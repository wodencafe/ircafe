package cafe.woden.ircclient.irc.pircbotx.listener;

import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3OutboundCommandRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeTestFixtures.Runtime;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.PlaybackCursorProvider;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.util.function.Consumer;

/** Explicit listener-lifecycle composition for focused PircBotX tests. */
final class PircbotxListenerRuntimeTestFixtures {

  private PircbotxListenerRuntimeTestFixtures() {}

  static PircbotxRegistrationLifecycleHandler registrationLifecycle(
      String serverId,
      PircbotxConnectionState conn,
      PlaybackCursorProvider playbackCursorProvider,
      PircbotxBouncerDiscoveryCoordinator bouncerDiscovery,
      PircbotxServerResponseEmitter serverResponses,
      Consumer<ServerIrcEvent> emit,
      Runtime runtime) {
    return registrationLifecycle(
        serverId,
        conn,
        playbackCursorProvider,
        bouncerDiscovery,
        serverResponses,
        emit,
        runtime.catalogs().outboundCommands(),
        runtime);
  }

  static PircbotxRegistrationLifecycleHandler registrationLifecycle(
      String serverId,
      PircbotxConnectionState conn,
      PlaybackCursorProvider playbackCursorProvider,
      PircbotxBouncerDiscoveryCoordinator bouncerDiscovery,
      PircbotxServerResponseEmitter serverResponses,
      Consumer<ServerIrcEvent> emit,
      Ircv3OutboundCommandRuntimeCatalog outboundCommandRuntimeCatalog,
      Runtime runtime) {
    return new PircbotxRegistrationLifecycleHandler(
        serverId,
        conn,
        playbackCursorProvider,
        bouncerDiscovery,
        serverResponses,
        emit,
        outboundCommandRuntimeCatalog,
        runtime.zncPlayback());
  }

  static PircbotxIsupportObserver isupportObserver(
      String serverId,
      PircbotxConnectionState conn,
      ServerIsupportStatePort serverIsupportState,
      Consumer<ServerIrcEvent> emit,
      Consumer<String> sojuNetIdObserver,
      Runtime runtime) {
    return new PircbotxIsupportObserver(
        serverId,
        conn,
        serverIsupportState,
        emit,
        sojuNetIdObserver,
        runtime.isupport(),
        runtime.typing());
  }

  static PircbotxSaslFailureHandler saslFailures(
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> emit,
      boolean disconnectOnSaslFailure,
      Runtime runtime) {
    return new PircbotxSaslFailureHandler(
        serverId, conn, emit, disconnectOnSaslFailure, runtime.sasl());
  }
}
