package cafe.woden.ircclient.irc;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.pircbotx.UserHostmask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputParser hook for a few low-cost IRCv3 capabilities (away-notify, account-notify, extended-join, account-tag).
 *
 * <p>Away-notify arrives as raw lines like:
 * <ul>
 *   <li><code>:nick!user@host AWAY :Gone away for now</code></li>
 *   <li><code>:nick!user@host AWAY</code></li>
 * </ul>
 */
final class PircbotxAwayNotifyInputParser extends InputParser {

  private static final Logger log = LoggerFactory.getLogger(PircbotxAwayNotifyInputParser.class);

  private final String serverId;
  private final Consumer<ServerIrcEvent> sink;

  private final PircbotxConnectionState conn;

  // Deduplicate high-frequency account-tag observations (which can appear on every PRIVMSG).
  private final Map<String, String> lastAccountTagByNickLower = new HashMap<>();

  PircbotxAwayNotifyInputParser(PircBotX bot, String serverId, PircbotxConnectionState conn, Consumer<ServerIrcEvent> sink) {
    super(bot);
    this.serverId = serverId;
    this.sink = Objects.requireNonNull(sink, "sink");
    this.conn = Objects.requireNonNull(conn, "conn");
  }

  @Override
  public void processCommand(
      String target,
      UserHostmask source,
      String command,
      String line,
      List<String> parsedLine,
      ImmutableMap<String, String> tags
  ) throws IOException {
    // Preserve default behavior first (this keeps User.isAway()/getAwayMessage() accurate).
    super.processCommand(target, source, command, line, parsedLine, tags);
    if (command == null) return;

    // Detect CAP ACKs for bouncer-related capabilities (ZNC playback, soju chathistory, soju bouncer networks, IRCv3 batch).
    if ("CAP".equalsIgnoreCase(command) && parsedLine != null && parsedLine.size() >= 2) {
      String sub = parsedLine.get(1);
      if (sub != null && "ACK".equalsIgnoreCase(sub) && parsedLine.size() >= 3) {
        String capList = parsedLine.get(2);
        if (capList != null && capList.startsWith(":")) capList = capList.substring(1);
        if (capList != null) {
          for (String cap : capList.split("\s+")) {
            if (cap == null) continue;
            String c = cap.trim();
            if (c.isEmpty()) continue;

            if ("znc.in/playback".equalsIgnoreCase(c)) {
              if (conn.zncPlaybackCapAcked.compareAndSet(false, true)) {
                log.info("[{}] CAP ACK: znc.in/playback enabled", serverId);
              }
            } else if ("batch".equalsIgnoreCase(c)) {
              if (conn.batchCapAcked.compareAndSet(false, true)) {
                log.info("[{}] CAP ACK: batch enabled", serverId);
              }
            } else if ("draft/chathistory".equalsIgnoreCase(c)) {
              if (conn.chatHistoryCapAcked.compareAndSet(false, true)) {
                log.info("[{}] CAP ACK: draft/chathistory enabled", serverId);
              }
            } else if ("soju.im/bouncer-networks".equalsIgnoreCase(c)) {
              if (conn.sojuBouncerNetworksCapAcked.compareAndSet(false, true)) {
                log.info("[{}] CAP ACK: soju.im/bouncer-networks enabled", serverId);
              }
            } else if ("server-time".equalsIgnoreCase(c)) {
              if (conn.serverTimeCapAcked.compareAndSet(false, true)) {
                log.info("[{}] CAP ACK: server-time enabled", serverId);
              }
            }
          }
        }
      }
      return;
    }

    if (source == null) return;
    String nick = Objects.toString(source.getNick(), "").trim();
    if (nick.isEmpty()) return;

    // IRCv3 account-tag: message tags include @account=<name> (or @account=* / @account=0).
    // This can arrive on many commands (PRIVMSG/NOTICE/etc), so we treat it as a best-effort signal
    // for account state and deduplicate per-nick to avoid excessive downstream work.
    if (tags != null) {
      String tagged = tags.get("account");
      if (tagged != null) {
        String account = tagged.trim();
        String key = nick.toLowerCase(Locale.ROOT);
        String prev = lastAccountTagByNickLower.put(key, account);
        if (!java.util.Objects.equals(prev, account)) {
          IrcEvent.AccountState st;
          String name = null;
          if (account.isEmpty() || "*".equals(account) || "0".equals(account)) {
            st = IrcEvent.AccountState.LOGGED_OUT;
          } else {
            st = IrcEvent.AccountState.LOGGED_IN;
            name = account;
          }
          // Keep this at TRACE: account-tag can be very chatty.
          log.trace("[{}] account-tag observed via tags: nick={} cmd={} target={} state={} account={}",
              serverId, nick, command, target, st, name);
          sink.accept(new ServerIrcEvent(serverId,
              new IrcEvent.UserAccountStateObserved(Instant.now(), nick, st, name)));
        }
      }
    }

    // The remaining handlers are specific to a small set of IRCv3 capabilities that arrive as
    // distinct commands. (account-tag above can arrive on many commands and is handled first.)
    boolean isAway = "AWAY".equalsIgnoreCase(command);
    boolean isAccount = "ACCOUNT".equalsIgnoreCase(command);
    boolean isJoin = "JOIN".equalsIgnoreCase(command);
    if (!isAway && !isAccount && !isJoin) return;

    // Opportunistic hostmask capture: away-notify + account-notify lines include a full prefix
    // (nick!user@host), which we can use to enrich the roster hostmask cache.
    String observedHostmask = null;
    if (line != null && !line.isBlank()) {
      String norm = line;
      // Strip IRCv3 message tags if present (e.g. "@time=...;... :nick!user@host AWAY ...")
      if (norm.startsWith("@")) {
        int sp = norm.indexOf(' ');
        if (sp > 0 && sp < norm.length() - 1) norm = norm.substring(sp + 1);
      }
      if (norm.startsWith(":")) {
        int sp = norm.indexOf(' ');
        if (sp > 1) {
          observedHostmask = norm.substring(1, sp).trim();
        }
      }
    }

    Instant now = Instant.now();
    // Hostmask capture is most valuable for passive state updates (away/account-notify), since
    // JOIN/PART/QUIT already produce hostmask observations via the bridge listener.
    if ((isAway || isAccount) && PircbotxUtil.isUsefulHostmask(observedHostmask)) {
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserHostmaskObserved(now, "", nick, observedHostmask)));
    }

    if (isAway) {
      // AWAY with a parameter means "now away". AWAY with no parameter means "back".
      // NOTE: In this callback, `command` is already the IRC command and `parsedLine` is the
      // *parameter* list (e.g. [":Gone away"]), not the entire tokenized line.
      boolean nowAway = (parsedLine != null && !parsedLine.isEmpty());
      IrcEvent.AwayState state = nowAway ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE;

      String msg = null;
      if (nowAway && parsedLine != null && !parsedLine.isEmpty()) {
        // For AWAY, the only parameter is the away message (if present).
        msg = parsedLine.get(0);
        if (msg != null && msg.startsWith(":")) msg = msg.substring(1);
      }

      // Per-event away-notify logs can get noisy; keep at DEBUG.
      log.debug("[{}] away-notify observed via InputParser: nick={} state={} msg={} params={} raw={}",
          serverId, nick, state, msg, parsedLine, line);

      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserAwayStateObserved(now, nick, state, msg)));
      return;
    }

    if (isAccount) {
      // IRCv3 account-notify:
      //  - ACCOUNT <account>  (logged in)
      //  - ACCOUNT *          (logged out)
      String account = null;
      if (parsedLine != null && !parsedLine.isEmpty()) {
        account = parsedLine.get(0);
        if (account != null && account.startsWith(":")) account = account.substring(1);
      }

      account = (account == null) ? null : account.trim();
      IrcEvent.AccountState st;
      if (account == null || account.isEmpty() || "*".equals(account) || "0".equals(account)) {
        st = IrcEvent.AccountState.LOGGED_OUT;
        account = null;
      } else {
        st = IrcEvent.AccountState.LOGGED_IN;
      }

      log.debug("[{}] account-notify observed via InputParser: nick={} state={} account={} params={} raw={}",
          serverId, nick, st, account, parsedLine, line);

      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserAccountStateObserved(now, nick, st, account)));
      return;
    }

    // IRCv3 extended-join:
    //  :nick!user@host JOIN #channel <account> :<realname>
    // Account may be "*" (or "0") to indicate logged out.
    if (!isJoin) return;

    if (parsedLine == null || parsedLine.size() < 2) {
      // Not an extended-join payload (server may not support it or didn't accept CAP).
      return;
    }

    String channel = parsedLine.get(0);
    String account = parsedLine.get(1);
    if (account != null && account.startsWith(":")) account = account.substring(1);
    account = (account == null) ? null : account.trim();

    IrcEvent.AccountState st;
    if (account == null || account.isEmpty() || "*".equals(account) || "0".equals(account)) {
      st = IrcEvent.AccountState.LOGGED_OUT;
      account = null;
    } else {
      st = IrcEvent.AccountState.LOGGED_IN;
    }

    log.debug("[{}] extended-join observed via InputParser: nick={} channel={} state={} account={} params={} raw={}",
        serverId, nick, channel, st, account, parsedLine, line);

    // We treat this as another best-effort signal for account state.
    sink.accept(new ServerIrcEvent(serverId,
        new IrcEvent.UserAccountStateObserved(now, nick, st, account)));
  }
}
