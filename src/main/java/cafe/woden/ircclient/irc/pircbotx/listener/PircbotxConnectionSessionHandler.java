package cafe.woden.ircclient.irc.pircbotx.listener;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.Ircv3MultilineAccumulator;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;

/** Owns connection-scoped lifecycle transitions and inbound activity bookkeeping. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PircbotxConnectionSessionHandler {

  private static final String DEFAULT_DISCONNECT_REASON = "Disconnected";
  private static final int MAX_CAUSE_DEPTH = 12;

  @NonNull private final String serverId;
  @NonNull private final PircbotxConnectionState conn;
  @NonNull private final Consumer<PircbotxConnectionState> heartbeatStopper;
  @NonNull private final BiConsumer<PircbotxConnectionState, String> reconnectScheduler;
  @NonNull private final PircbotxBouncerDiscoveryCoordinator bouncerDiscovery;
  @NonNull private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  @NonNull private final PircbotxServerResponseEmitter serverResponses;
  @NonNull private final Ircv3MultilineAccumulator multilineAccumulator;
  @NonNull private final Consumer<ServerIrcEvent> emit;

  void recordInboundActivity() {
    conn.recordInboundActivity(System.currentTimeMillis());
  }

  void onConnect(ConnectEvent event) {
    recordInboundActivity();
    PircBotX bot = event.getBot();
    conn.resetReconnectAttempts();
    conn.clearManualDisconnect();
    serverResponses.clear();

    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.Connected(
                Instant.now(), bot.getServerHostname(), bot.getServerPort(), bot.getNick())));
  }

  void onDisconnect(DisconnectEvent event) {
    serverResponses.clear();
    String override = conn.takeDisconnectReasonOverride();
    Exception ex = event.getDisconnectException();
    String reason = disconnectReason(override, ex);
    if (conn.clearBotIf(event.getBot())) {
      heartbeatStopper.accept(conn);
    }
    bouncerDiscovery.onDisconnect();
    multilineAccumulator.clear();
    chatHistoryBatches.clear();

    emit.accept(new ServerIrcEvent(serverId, new IrcEvent.Disconnected(Instant.now(), reason)));
    boolean suppressReconnect = conn.consumeSuppressAutoReconnectOnce();
    if (!conn.manualDisconnectRequested() && !suppressReconnect) {
      reconnectScheduler.accept(conn, reason);
    }
  }

  private static String disconnectReason(String override, Exception ex) {
    String overrideText = normalizeReason(override);
    if (!overrideText.isEmpty()) return overrideText;
    String extracted = extractReason(ex);
    return extracted.isEmpty() ? DEFAULT_DISCONNECT_REASON : extracted;
  }

  private static String extractReason(Throwable throwable) {
    String fallback = "";
    Throwable current = throwable;
    int depth = 0;
    while (current != null && depth++ < MAX_CAUSE_DEPTH) {
      String message = normalizeReason(current.getMessage());
      if (!message.isEmpty()) {
        if (isPlaceholderDisconnectMessage(message)) {
          if (fallback.isEmpty()) fallback = message;
        } else {
          return message;
        }
      }
      Throwable cause = current.getCause();
      if (cause == current) break;
      current = cause;
    }
    return fallback;
  }

  private static boolean isPlaceholderDisconnectMessage(String message) {
    String value = normalizeReason(message).toLowerCase(Locale.ROOT);
    return value.isEmpty()
        || "disconnected".equals(value)
        || "socket closed".equals(value)
        || "socket is closed".equals(value)
        || "exception encountered during connect".equals(value)
        || value.startsWith("failed to connect to irc server(s) after ");
  }

  private static String normalizeReason(String reason) {
    return Objects.toString(reason, "").trim();
  }
}
