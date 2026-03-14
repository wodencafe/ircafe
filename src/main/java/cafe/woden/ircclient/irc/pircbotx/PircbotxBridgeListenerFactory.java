package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Assembles per-connection bridge listeners from Spring-managed and runtime dependencies. */
@Component
@InfrastructureLayer
public class PircbotxBridgeListenerFactory {

  private final BouncerBackendRegistry bouncerBackends;
  private final BouncerDiscoveryEventPort bouncerDiscoveryEvents;
  private final PlaybackCursorProvider playbackCursorProvider;
  private final ServerIsupportStatePort serverIsupportState;
  private final boolean sojuDiscoveryEnabled;
  private final boolean zncDiscoveryEnabled;

  public PircbotxBridgeListenerFactory(
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents,
      PlaybackCursorProvider playbackCursorProvider,
      ServerIsupportStatePort serverIsupportState,
      SojuProperties sojuProps,
      ZncProperties zncProps) {
    this.bouncerBackends = Objects.requireNonNull(bouncerBackends, "bouncerBackends");
    this.bouncerDiscoveryEvents = bouncerDiscoveryEvents;
    this.playbackCursorProvider =
        Objects.requireNonNull(playbackCursorProvider, "playbackCursorProvider");
    this.serverIsupportState = Objects.requireNonNull(serverIsupportState, "serverIsupportState");
    this.sojuDiscoveryEnabled =
        Objects.requireNonNull(sojuProps, "sojuProps").discovery().enabled();
    this.zncDiscoveryEnabled = Objects.requireNonNull(zncProps, "zncProps").discovery().enabled();
  }

  PircbotxBridgeListener create(
      String serverId,
      PircbotxConnectionState conn,
      FlowableProcessor<ServerIrcEvent> bus,
      Consumer<PircbotxConnectionState> heartbeatStopper,
      BiConsumer<PircbotxConnectionState, String> reconnectScheduler,
      PircbotxBridgeListener.CtcpRequestHandler ctcpHandler,
      boolean disconnectOnSaslFailure) {
    return new PircbotxBridgeListener(
        serverId,
        conn,
        bus,
        heartbeatStopper,
        reconnectScheduler,
        ctcpHandler,
        disconnectOnSaslFailure,
        sojuDiscoveryEnabled,
        zncDiscoveryEnabled,
        bouncerBackends,
        bouncerDiscoveryEvents,
        playbackCursorProvider,
        serverIsupportState);
  }
}
