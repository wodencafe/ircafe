package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emits structured WHO/WHOX/WHOIS events from numeric IRC lines. */
final class PircbotxWhoEventEmitter {
  private static final Logger log = LoggerFactory.getLogger(PircbotxWhoEventEmitter.class);
  private static final String IRCAFE_WHOX_TOKEN = "1";

  private final String serverId;
  private final PircbotxConnectionState conn;
  private final Consumer<ServerIrcEvent> emit;

  PircbotxWhoEventEmitter(
      String serverId, PircbotxConnectionState conn, Consumer<ServerIrcEvent> emit) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.emit = Objects.requireNonNull(emit, "emit");
  }

  void maybeEmitLine(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return;
    emitRpl302Userhost(rawLine);
    emitRpl301WhoisAway(rawLine);
    emitRpl330WhoisAccount(rawLine);
    emitRpl318EndOfWhois(rawLine);
    emitRpl311Or314Hostmask(rawLine);
    emitRpl352WhoReply(rawLine);
    emitRpl354Whox(rawLine);
  }

  boolean maybeEmitNumeric(int code, String line) {
    switch (code) {
      case 302:
        emitRpl302Userhost(line);
        return true;
      case 352:
        emitRpl352WhoReply(line);
        return true;
      case 354:
        emitRpl354Whox(line);
        return true;
      case 330:
        emitRpl330WhoisAccount(line);
        return true;
      case 301:
        emitRpl301WhoisAway(line);
        return true;
      case 318:
        emitRpl318EndOfWhois(line);
        return true;
      default:
        return false;
    }
  }

  private void emitRpl302Userhost(String line) {
    List<PircbotxWhoUserhostParsers.UserhostEntry> entries =
        PircbotxWhoUserhostParsers.parseRpl302Userhost(line);
    if (entries == null || entries.isEmpty()) return;
    Instant now = Instant.now();
    for (PircbotxWhoUserhostParsers.UserhostEntry entry : entries) {
      if (entry == null) continue;
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserHostmaskObserved(now, "", entry.nick(), entry.hostmask())));
      IrcEvent.AwayState as =
          (entry.awayState() == null) ? IrcEvent.AwayState.UNKNOWN : entry.awayState();
      if (as == IrcEvent.AwayState.AWAY || as == IrcEvent.AwayState.HERE) {
        emit.accept(
            new ServerIrcEvent(
                serverId, new IrcEvent.UserAwayStateObserved(now, entry.nick(), as)));
      }
    }
  }

  private void emitRpl301WhoisAway(String line) {
    PircbotxWhoisParsers.ParsedWhoisAway whoisAway =
        PircbotxWhoisParsers.parseRpl301WhoisAway(line);
    if (whoisAway == null || whoisAway.nick() == null || whoisAway.nick().isBlank()) return;
    String nick = whoisAway.nick().trim();
    String key = nick.toLowerCase(Locale.ROOT);
    conn.whoisSawAwayByNickLower.computeIfPresent(key, (ignored, prior) -> Boolean.TRUE);
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.UserAwayStateObserved(
                Instant.now(), nick, IrcEvent.AwayState.AWAY, whoisAway.message())));
  }

  private void emitRpl330WhoisAccount(String line) {
    PircbotxWhoisParsers.ParsedWhoisAccount whoisAcct =
        PircbotxWhoisParsers.parseRpl330WhoisAccount(line);
    if (whoisAcct == null || whoisAcct.nick() == null || whoisAcct.nick().isBlank()) return;
    String nick = whoisAcct.nick().trim();
    String key = nick.toLowerCase(Locale.ROOT);
    conn.whoisSawAccountByNickLower.computeIfPresent(key, (ignored, prior) -> Boolean.TRUE);
    conn.whoisAccountNumericSupported.set(true);
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.UserAccountStateObserved(
                Instant.now(), nick, IrcEvent.AccountState.LOGGED_IN, whoisAcct.account())));
  }

  private void emitRpl318EndOfWhois(String line) {
    String nick = PircbotxWhoisParsers.parseRpl318EndOfWhoisNick(line);
    if (nick == null || nick.isBlank()) return;
    nick = nick.trim();
    String key = nick.toLowerCase(Locale.ROOT);

    Boolean sawAway = conn.whoisSawAwayByNickLower.remove(key);
    if (sawAway != null && !sawAway.booleanValue()) {
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserAwayStateObserved(Instant.now(), nick, IrcEvent.AwayState.HERE)));
    }

    Boolean sawAcct = conn.whoisSawAccountByNickLower.remove(key);
    if (sawAcct != null && !sawAcct.booleanValue() && conn.whoisAccountNumericSupported.get()) {
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserAccountStateObserved(
                  Instant.now(), nick, IrcEvent.AccountState.LOGGED_OUT)));
    }
    if (sawAway != null || sawAcct != null) {
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.WhoisProbeCompleted(
                  Instant.now(),
                  nick,
                  sawAway != null && sawAway.booleanValue(),
                  sawAcct != null && sawAcct.booleanValue(),
                  conn.whoisAccountNumericSupported.get())));
    }
  }

  private void emitRpl311Or314Hostmask(String line) {
    PircbotxWhoisParsers.ParsedWhoisUser whoisUser =
        PircbotxWhoisParsers.parseRpl311WhoisUser(line);
    if (whoisUser == null) whoisUser = PircbotxWhoisParsers.parseRpl314WhowasUser(line);
    if (whoisUser == null
        || whoisUser.nick().isBlank()
        || whoisUser.user().isBlank()
        || whoisUser.host().isBlank()) {
      return;
    }
    String hostmask = whoisUser.nick() + "!" + whoisUser.user() + "@" + whoisUser.host();
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.UserHostmaskObserved(Instant.now(), "", whoisUser.nick(), hostmask)));
  }

  private void emitRpl352WhoReply(String line) {
    PircbotxWhoUserhostParsers.ParsedWhoReply whoReply =
        PircbotxWhoUserhostParsers.parseRpl352WhoReply(line);
    if (whoReply == null
        || whoReply.channel().isBlank()
        || whoReply.nick().isBlank()
        || whoReply.user().isBlank()
        || whoReply.host().isBlank()) {
      return;
    }

    Instant now = Instant.now();
    String hostmask = whoReply.nick() + "!" + whoReply.user() + "@" + whoReply.host();
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.UserHostmaskObserved(now, whoReply.channel(), whoReply.nick(), hostmask)));
    emitAwayStateFromFlags(now, whoReply.nick(), whoReply.flags());
  }

  private void emitRpl354Whox(String line) {
    PircbotxWhoUserhostParsers.ParsedWhoxTcuhnaf strict =
        PircbotxWhoUserhostParsers.parseRpl354WhoxTcuhnaf(line, IRCAFE_WHOX_TOKEN);
    if (strict != null) {
      if (conn.whoxSchemaCompatibleEmitted.compareAndSet(false, true)) {
        conn.whoxSchemaCompatible.set(true);
        emit.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.WhoxSchemaCompatibleObserved(Instant.now(), true, "strict-parse-ok")));
      }
      Instant now = Instant.now();
      String hostmask = strict.nick() + "!" + strict.user() + "@" + strict.host();
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserHostmaskObserved(now, strict.channel(), strict.nick(), hostmask)));
      emitAwayStateFromFlags(now, strict.nick(), strict.flags());

      IrcEvent.AccountState accountState = accountState(strict.account());
      if (accountState != IrcEvent.AccountState.UNKNOWN) {
        emit.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.UserAccountStateObserved(
                    now, strict.nick(), accountState, strict.account())));
      }
      return;
    }

    if (PircbotxWhoUserhostParsers.seemsRpl354WhoxWithToken(line, IRCAFE_WHOX_TOKEN)
        && conn.whoxSchemaIncompatibleEmitted.compareAndSet(false, true)) {
      conn.whoxSchemaCompatible.set(false);
      log.debug(
          "[{}] WHOX schema mismatch: strict parse failed for token {}: {}",
          serverId,
          IRCAFE_WHOX_TOKEN,
          line);
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.WhoxSchemaCompatibleObserved(
                  Instant.now(), false, "strict-parse-failed")));
    }

    PircbotxWhoUserhostParsers.ParsedWhoxReply whox =
        PircbotxWhoUserhostParsers.parseRpl354WhoxReply(line);
    if (whox == null || whox.nick().isBlank() || whox.user().isBlank() || whox.host().isBlank()) {
      return;
    }
    String hostmask = whox.nick() + "!" + whox.user() + "@" + whox.host();
    String channel = (whox.channel() == null) ? "" : whox.channel();
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.UserHostmaskObserved(Instant.now(), channel, whox.nick(), hostmask)));
  }

  private void emitAwayStateFromFlags(Instant at, String nick, String flags) {
    IrcEvent.AwayState as = awayState(flags);
    if (as == IrcEvent.AwayState.AWAY || as == IrcEvent.AwayState.HERE) {
      emit.accept(new ServerIrcEvent(serverId, new IrcEvent.UserAwayStateObserved(at, nick, as)));
    }
  }

  private static IrcEvent.AwayState awayState(String flags) {
    if (flags == null) return IrcEvent.AwayState.UNKNOWN;
    if (flags.indexOf('G') >= 0) return IrcEvent.AwayState.AWAY;
    if (flags.indexOf('H') >= 0) return IrcEvent.AwayState.HERE;
    return IrcEvent.AwayState.UNKNOWN;
  }

  private static IrcEvent.AccountState accountState(String acct) {
    if (acct == null) return IrcEvent.AccountState.UNKNOWN;
    if ("*".equals(acct) || "0".equals(acct)) return IrcEvent.AccountState.LOGGED_OUT;
    return IrcEvent.AccountState.LOGGED_IN;
  }
}
