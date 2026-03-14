package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.support.Ircv3MultilineAccumulator;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
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
    conn.lastInboundMs.set(System.currentTimeMillis());
    conn.localTimeoutEmitted.set(false);
  }

  void onConnect(ConnectEvent event) {
    recordInboundActivity();
    PircBotX bot = event.getBot();
    conn.reconnectAttempts.set(0);
    conn.manualDisconnect.set(false);
    serverResponses.clear();

    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.Connected(
                Instant.now(), bot.getServerHostname(), bot.getServerPort(), bot.getNick())));
  }

  void onDisconnect(DisconnectEvent event) {
    serverResponses.clear();
    String override = conn.disconnectReasonOverride.getAndSet(null);
    Exception ex = event.getDisconnectException();
    String reason =
        (override != null && !override.isBlank())
            ? override
            : (ex != null && ex.getMessage() != null) ? ex.getMessage() : "Disconnected";
    if (conn.botRef.compareAndSet(event.getBot(), null)) {
      heartbeatStopper.accept(conn);
    }
    bouncerDiscovery.onDisconnect();
    multilineAccumulator.clear();
    chatHistoryBatches.clear();

    emit.accept(new ServerIrcEvent(serverId, new IrcEvent.Disconnected(Instant.now(), reason)));
    boolean suppressReconnect = conn.suppressAutoReconnectOnce.getAndSet(false);
    if (!conn.manualDisconnect.get() && !suppressReconnect) {
      reconnectScheduler.accept(conn, reason);
    }
  }
}
