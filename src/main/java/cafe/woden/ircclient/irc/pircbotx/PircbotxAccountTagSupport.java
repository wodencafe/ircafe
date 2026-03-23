package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Observes IRCv3 account-tag state changes while suppressing duplicate per-nick emissions. */
final class PircbotxAccountTagSupport {

  private static final Logger log = LoggerFactory.getLogger(PircbotxAccountTagSupport.class);
  private static final int MAX_ACCOUNT_TAG_KEYS = 8_192;

  private final String serverId;
  private final Consumer<ServerIrcEvent> sink;

  // Deduplicate high-frequency account-tag observations (which can appear on every PRIVMSG).
  private final Map<String, String> lastAccountTagByNickLower =
      Collections.synchronizedMap(
          new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
              return size() > MAX_ACCOUNT_TAG_KEYS;
            }
          });

  PircbotxAccountTagSupport(String serverId, Consumer<ServerIrcEvent> sink) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  void observe(
      Instant at, String nick, String command, String target, ImmutableMap<String, String> tags) {
    if (tags == null) return;

    String tagged = tags.get("account");
    if (tagged == null) return;

    String account = tagged.trim();
    String key = nick.toLowerCase(Locale.ROOT);
    String previous = lastAccountTagByNickLower.put(key, account);
    if (Objects.equals(previous, account)) return;

    IrcEvent.AccountState state;
    String accountName = null;
    if (account.isEmpty() || "*".equals(account) || "0".equals(account)) {
      state = IrcEvent.AccountState.LOGGED_OUT;
    } else {
      state = IrcEvent.AccountState.LOGGED_IN;
      accountName = account;
    }

    // Keep this at TRACE: account-tag can be very chatty.
    log.trace(
        "[{}] account-tag observed via tags: nick={} cmd={} target={} state={} account={}",
        serverId,
        nick,
        command,
        target,
        state,
        accountName);
    sink.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.UserAccountStateObserved(at, nick, state, accountName)));
  }
}
