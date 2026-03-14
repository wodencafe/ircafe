package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.irc.soju.PircbotxSojuParsers;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies RPL_ISUPPORT tokens and emits derived connection-feature observations. */
final class PircbotxIsupportObserver {
  private static final Logger log = LoggerFactory.getLogger(PircbotxIsupportObserver.class);

  private final String serverId;
  private final PircbotxConnectionState conn;
  private final ServerIsupportStatePort serverIsupportState;
  private final Consumer<ServerIrcEvent> emit;
  private final Consumer<String> sojuNetIdObserver;

  PircbotxIsupportObserver(
      String serverId,
      PircbotxConnectionState conn,
      ServerIsupportStatePort serverIsupportState,
      Consumer<ServerIrcEvent> emit,
      Consumer<String> sojuNetIdObserver) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.serverIsupportState = Objects.requireNonNull(serverIsupportState, "serverIsupportState");
    this.emit = Objects.requireNonNull(emit, "emit");
    this.sojuNetIdObserver = Objects.requireNonNull(sojuNetIdObserver, "sojuNetIdObserver");
  }

  void observe(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return;
    applyIsupportTokens(rawLine);
    sojuNetIdObserver.accept(PircbotxSojuParsers.parseRpl005BouncerNetId(rawLine));

    if (PircbotxWhoUserhostParsers.parseRpl005IsupportHasWhox(rawLine)) {
      emit.accept(
          new ServerIrcEvent(serverId, new IrcEvent.WhoxSupportObserved(Instant.now(), true)));
    }

    maybeApplyMonitorIsupport(rawLine);
    maybeApplyTypingClientTagPolicy(rawLine);
  }

  private void maybeApplyMonitorIsupport(String rawLine) {
    PircbotxMonitorParsers.ParsedMonitorSupport monitor =
        PircbotxMonitorParsers.parseRpl005MonitorSupport(rawLine);
    if (monitor == null) return;

    boolean prevSupported = conn.monitorSupported.getAndSet(monitor.supported());
    long prevLimit = conn.monitorMaxTargets.getAndSet(Math.max(0L, monitor.limit()));
    if (prevSupported != monitor.supported() || prevLimit != Math.max(0L, monitor.limit())) {
      log.info(
          "[{}] monitor support changed: supported={} max-targets={}",
          serverId,
          monitor.supported(),
          Math.max(0, monitor.limit()));
    }
  }

  private void maybeApplyTypingClientTagPolicy(String rawLine) {
    String clientTagDeny = PircbotxClientTagParsers.parseRpl005ClientTagDenyValue(rawLine);
    if (clientTagDeny == null) return;

    boolean allowed = PircbotxClientTagParsers.isClientOnlyTagAllowed(clientTagDeny, "typing");
    conn.typingClientTagPolicyKnown.set(true);
    boolean prev = conn.typingClientTagAllowed.getAndSet(allowed);
    if (prev != allowed) {
      log.info(
          "[{}] CLIENTTAGDENY -> typing allowed={} (raw={})", serverId, allowed, clientTagDeny);
    }
  }

  private void applyIsupportTokens(String rawLine) {
    ParsedIrcLine parsed = PircbotxInboundLineParsers.parseIrcLine(rawLine);
    if (parsed == null || !"005".equals(parsed.command())) return;

    List<String> params = parsed.params();
    int start = params.size() >= 1 ? 1 : 0;
    for (int i = start; i < params.size(); i++) {
      String token = Objects.toString(params.get(i), "").trim();
      if (token.isEmpty()) continue;
      if (token.startsWith("-") && token.length() > 1) {
        serverIsupportState.applyIsupportToken(serverId, token.substring(1), null);
        continue;
      }

      int eq = token.indexOf('=');
      if (eq >= 0) {
        String key = token.substring(0, eq).trim();
        String value = token.substring(eq + 1).trim();
        if (!key.isEmpty()) {
          serverIsupportState.applyIsupportToken(serverId, key, value);
        }
        continue;
      }

      serverIsupportState.applyIsupportToken(serverId, token, "");
    }
  }
}
