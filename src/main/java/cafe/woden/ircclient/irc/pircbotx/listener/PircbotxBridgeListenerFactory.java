package cafe.woden.ircclient.irc.pircbotx.listener;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.config.properties.ZncProperties;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.NonNull;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.pircbotx.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Assembles per-connection bridge listeners from Spring-managed and runtime dependencies. */
@Component
@InfrastructureLayer
public class PircbotxBridgeListenerFactory {

  @NonNull private final BouncerBackendRegistry bouncerBackends;
  private final BouncerDiscoveryEventPort bouncerDiscoveryEvents;
  @NonNull private final PlaybackCursorProvider playbackCursorProvider;
  @NonNull private final ServerIsupportStatePort serverIsupportState;
  @NonNull private final Ircv3InboundCommandSignalRuntimeCatalog inboundCommandRuntimeCatalog;
  @NonNull private final Ircv3InboundTagSignalRuntimeCatalog inboundTagRuntimeCatalog;
  @NonNull private final Ircv3OutboundCommandRuntimeCatalog outboundCommandRuntimeCatalog;
  @NonNull private final Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport;
  @NonNull private final Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport;
  @NonNull private final Ircv3ZncPlaybackRuntimeSupport zncPlaybackRuntimeSupport;
  @NonNull private final Ircv3IsupportRuntimeSupport isupportRuntimeSupport;
  @NonNull private final Ircv3TypingRuntimeSupport typingRuntimeSupport;
  @NonNull private final Ircv3SaslRuntimeSupport saslRuntimeSupport;
  private final boolean sojuDiscoveryEnabled;
  private final boolean zncDiscoveryEnabled;

  @Autowired
  public PircbotxBridgeListenerFactory(
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents,
      PlaybackCursorProvider playbackCursorProvider,
      ServerIsupportStatePort serverIsupportState,
      SojuProperties sojuProps,
      ZncProperties zncProps,
      Ircv3RuntimeCatalogs catalogs,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport,
      Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport) {
    this(
        bouncerBackends,
        bouncerDiscoveryEvents,
        playbackCursorProvider,
        serverIsupportState,
        sojuProps,
        zncProps,
        runtimeComposition(
            Objects.requireNonNull(catalogs, "catalogs"),
            serverTimeRuntimeSupport,
            messageTagsRuntimeSupport));
  }

  public PircbotxBridgeListenerFactory(
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents,
      PlaybackCursorProvider playbackCursorProvider,
      ServerIsupportStatePort serverIsupportState,
      SojuProperties sojuProps,
      ZncProperties zncProps,
      Ircv3InboundCommandSignalRuntimeCatalog inboundCommandRuntimeCatalog,
      Ircv3InboundTagSignalRuntimeCatalog inboundTagRuntimeCatalog,
      Ircv3OutboundCommandRuntimeCatalog outboundCommandRuntimeCatalog,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport,
      Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport) {
    this(
        bouncerBackends,
        bouncerDiscoveryEvents,
        playbackCursorProvider,
        serverIsupportState,
        sojuProps,
        zncProps,
        runtimeComposition(
            inboundCommandRuntimeCatalog,
            inboundTagRuntimeCatalog,
            outboundCommandRuntimeCatalog,
            serverTimeRuntimeSupport,
            messageTagsRuntimeSupport));
  }

  private PircbotxBridgeListenerFactory(
      BouncerBackendRegistry bouncerBackends,
      BouncerDiscoveryEventPort bouncerDiscoveryEvents,
      PlaybackCursorProvider playbackCursorProvider,
      ServerIsupportStatePort serverIsupportState,
      SojuProperties sojuProps,
      ZncProperties zncProps,
      RuntimeComposition runtime) {
    RuntimeComposition requiredRuntime = Objects.requireNonNull(runtime, "runtime");
    this.bouncerBackends = Objects.requireNonNull(bouncerBackends, "bouncerBackends");
    this.bouncerDiscoveryEvents = bouncerDiscoveryEvents;
    this.playbackCursorProvider =
        Objects.requireNonNull(playbackCursorProvider, "playbackCursorProvider");
    this.serverIsupportState = Objects.requireNonNull(serverIsupportState, "serverIsupportState");
    this.inboundCommandRuntimeCatalog = requiredRuntime.inboundCommands();
    this.inboundTagRuntimeCatalog = requiredRuntime.inboundTags();
    this.outboundCommandRuntimeCatalog = requiredRuntime.outboundCommands();
    this.serverTimeRuntimeSupport = requiredRuntime.serverTime();
    this.messageTagsRuntimeSupport = requiredRuntime.messageTags();
    this.zncPlaybackRuntimeSupport = requiredRuntime.zncPlayback();
    this.isupportRuntimeSupport = requiredRuntime.isupport();
    this.typingRuntimeSupport = requiredRuntime.typing();
    this.saslRuntimeSupport = requiredRuntime.sasl();
    this.sojuDiscoveryEnabled =
        Objects.requireNonNull(sojuProps, "sojuProps").discovery().enabled();
    this.zncDiscoveryEnabled = Objects.requireNonNull(zncProps, "zncProps").discovery().enabled();
  }

  public ListenerAdapter create(
      String serverId,
      PircbotxConnectionState conn,
      FlowableProcessor<ServerIrcEvent> bus,
      Consumer<PircbotxConnectionState> heartbeatStopper,
      BiConsumer<PircbotxConnectionState, String> reconnectScheduler,
      PircbotxCtcpRequestHandler ctcpHandler,
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
        serverIsupportState,
        inboundCommandRuntimeCatalog,
        inboundTagRuntimeCatalog,
        outboundCommandRuntimeCatalog,
        serverTimeRuntimeSupport,
        messageTagsRuntimeSupport,
        zncPlaybackRuntimeSupport,
        isupportRuntimeSupport,
        typingRuntimeSupport,
        saslRuntimeSupport);
  }

  private static RuntimeComposition runtimeComposition(
      Ircv3RuntimeCatalogs catalogs,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport,
      Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport) {
    return runtimeComposition(
        catalogs.inboundCommands(),
        catalogs.inboundTags(),
        catalogs.outboundCommands(),
        serverTimeRuntimeSupport,
        messageTagsRuntimeSupport);
  }

  private static RuntimeComposition runtimeComposition(
      Ircv3InboundCommandSignalRuntimeCatalog inboundCommandRuntimeCatalog,
      Ircv3InboundTagSignalRuntimeCatalog inboundTagRuntimeCatalog,
      Ircv3OutboundCommandRuntimeCatalog outboundCommandRuntimeCatalog,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport,
      Ircv3MessageTagsRuntimeSupport messageTagsRuntimeSupport) {
    Ircv3InboundCommandSignalRuntimeCatalog inboundCommands =
        Objects.requireNonNull(inboundCommandRuntimeCatalog, "inboundCommandRuntimeCatalog");
    Ircv3InboundTagSignalRuntimeCatalog inboundTags =
        Objects.requireNonNull(inboundTagRuntimeCatalog, "inboundTagRuntimeCatalog");
    Ircv3OutboundCommandRuntimeCatalog outboundCommands =
        Objects.requireNonNull(outboundCommandRuntimeCatalog, "outboundCommandRuntimeCatalog");
    return new RuntimeComposition(
        inboundCommands,
        inboundTags,
        outboundCommands,
        Objects.requireNonNull(serverTimeRuntimeSupport, "serverTimeRuntimeSupport"),
        Objects.requireNonNull(messageTagsRuntimeSupport, "messageTagsRuntimeSupport"),
        new Ircv3ZncPlaybackRuntimeSupport(inboundCommands, inboundTags),
        new Ircv3IsupportRuntimeSupport(inboundCommands),
        new Ircv3TypingRuntimeSupport(outboundCommands, inboundTags, inboundCommands),
        new Ircv3SaslRuntimeSupport(inboundCommands));
  }

  private record RuntimeComposition(
      Ircv3InboundCommandSignalRuntimeCatalog inboundCommands,
      Ircv3InboundTagSignalRuntimeCatalog inboundTags,
      Ircv3OutboundCommandRuntimeCatalog outboundCommands,
      Ircv3ServerTimeRuntimeSupport serverTime,
      Ircv3MessageTagsRuntimeSupport messageTags,
      Ircv3ZncPlaybackRuntimeSupport zncPlayback,
      Ircv3IsupportRuntimeSupport isupport,
      Ircv3TypingRuntimeSupport typing,
      Ircv3SaslRuntimeSupport sasl) {

    private RuntimeComposition {
      Objects.requireNonNull(inboundCommands, "inboundCommands");
      Objects.requireNonNull(inboundTags, "inboundTags");
      Objects.requireNonNull(outboundCommands, "outboundCommands");
      Objects.requireNonNull(serverTime, "serverTime");
      Objects.requireNonNull(messageTags, "messageTags");
      Objects.requireNonNull(zncPlayback, "zncPlayback");
      Objects.requireNonNull(isupport, "isupport");
      Objects.requireNonNull(typing, "typing");
      Objects.requireNonNull(sasl, "sasl");
    }
  }
}
