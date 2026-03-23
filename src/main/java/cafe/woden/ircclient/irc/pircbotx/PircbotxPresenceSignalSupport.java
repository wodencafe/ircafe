package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses away/account/join presence signals emitted as IRCv3 command events. */
final class PircbotxPresenceSignalSupport {

  private static final Logger log = LoggerFactory.getLogger(PircbotxPresenceSignalSupport.class);

  private final String serverId;
  private final Consumer<ServerIrcEvent> sink;

  PircbotxPresenceSignalSupport(String serverId, Consumer<ServerIrcEvent> sink) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  boolean observes(String command) {
    if (command == null || command.isBlank()) return false;
    String normalized = command.trim().toUpperCase(Locale.ROOT);
    return "AWAY".equals(normalized) || "ACCOUNT".equals(normalized) || "JOIN".equals(normalized);
  }

  void observe(Instant at, String nick, String command, String rawLine, List<String> parsedLine) {
    boolean isAway = "AWAY".equalsIgnoreCase(command);
    boolean isAccount = "ACCOUNT".equalsIgnoreCase(command);
    boolean isJoin = "JOIN".equalsIgnoreCase(command);
    if (!isAway && !isAccount && !isJoin) return;

    String observedHostmask = observedHostmask(rawLine);
    if ((isAway || isAccount) && PircbotxUtil.isUsefulHostmask(observedHostmask)) {
      sink.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.UserHostmaskObserved(at, "", nick, observedHostmask)));
    }

    if (isAway) {
      boolean nowAway = parsedLine != null && !parsedLine.isEmpty();
      IrcEvent.AwayState state = nowAway ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE;

      String message = null;
      if (nowAway && parsedLine != null && !parsedLine.isEmpty()) {
        message = stripLeadingColon(parsedLine.get(0));
      }

      log.debug(
          "[{}] away-notify observed via InputParser: nick={} state={} msg={} params={} raw={}",
          serverId,
          nick,
          state,
          message,
          parsedLine,
          rawLine);

      sink.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.UserAwayStateObserved(at, nick, state, message)));
      return;
    }

    if (isAccount) {
      String account =
          parsedLine == null || parsedLine.isEmpty() ? null : stripLeadingColon(parsedLine.get(0));
      IrcEvent.AccountState state = toAccountState(account);
      if (state == IrcEvent.AccountState.LOGGED_OUT) {
        account = null;
      } else if (account != null) {
        account = account.trim();
      }

      log.debug(
          "[{}] account-notify observed via InputParser: nick={} state={} account={} params={} raw={}",
          serverId,
          nick,
          state,
          account,
          parsedLine,
          rawLine);

      sink.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.UserAccountStateObserved(at, nick, state, account)));
      return;
    }

    if (parsedLine == null || parsedLine.size() < 2) {
      // Not an extended-join payload (server may not support it or didn't accept CAP).
      return;
    }

    String channel = parsedLine.get(0);
    String account = stripLeadingColon(parsedLine.get(1)).trim();
    String realName = null;
    if (parsedLine.size() >= 3) {
      realName = stripLeadingColon(parsedLine.get(2)).trim();
      if (realName.isEmpty()) realName = null;
    }

    IrcEvent.AccountState state = toAccountState(account);
    if (state == IrcEvent.AccountState.LOGGED_OUT) {
      account = null;
    }

    log.debug(
        "[{}] extended-join observed via InputParser: nick={} channel={} state={} account={} realName={} params={} raw={}",
        serverId,
        nick,
        channel,
        state,
        account,
        realName,
        parsedLine,
        rawLine);

    sink.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.UserAccountStateObserved(at, nick, state, account)));
    if (realName != null) {
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserSetNameObserved(
                  at, nick, realName, IrcEvent.UserSetNameObserved.Source.EXTENDED_JOIN)));
    }
  }

  private static String observedHostmask(String rawLine) {
    String line = Objects.toString(rawLine, "");
    if (line.isBlank()) return null;
    String normalized = line;
    if (normalized.startsWith("@")) {
      int firstSpace = normalized.indexOf(' ');
      if (firstSpace > 0 && firstSpace < normalized.length() - 1) {
        normalized = normalized.substring(firstSpace + 1);
      }
    }
    if (!normalized.startsWith(":")) return null;
    int firstSpace = normalized.indexOf(' ');
    if (firstSpace <= 1) return null;
    return normalized.substring(1, firstSpace).trim();
  }

  private static IrcEvent.AccountState toAccountState(String accountRaw) {
    String account = accountRaw == null ? "" : accountRaw.trim();
    if (account.isEmpty() || "*".equals(account) || "0".equals(account)) {
      return IrcEvent.AccountState.LOGGED_OUT;
    }
    return IrcEvent.AccountState.LOGGED_IN;
  }

  private static String stripLeadingColon(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.startsWith(":")) value = value.substring(1).trim();
    return value;
  }
}
