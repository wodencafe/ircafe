package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.parseQualifiedTarget;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.routeOutboundRawLine;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.OutboundRawRoute;
import cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.QualifiedTarget;
import java.io.IOException;
import java.util.Objects;

/**
 * Coordinates buffer routing, target observations, and input writes against the current session.
 */
final class QuasselCoreInputCoordinator {
  private static final int BUFFER_STATUS = 0x01;

  @FunctionalInterface
  interface SessionPort {
    void observeTargetNetwork(QuasselCoreSession session, String target, int networkId);
  }

  private final QuasselCoreTargetResolver targets;
  private final SessionPort port;

  QuasselCoreInputCoordinator(QuasselCoreTargetResolver targets, SessionPort port) {
    this.targets = Objects.requireNonNull(targets, "targets");
    this.port = Objects.requireNonNull(port, "port");
  }

  void sendInput(
      QuasselCoreSession session, String operation, int typeBits, String bufferName, String input)
      throws IOException {
    requireNetwork(session, operation);
    QualifiedTarget requested = parseQualifiedTarget(bufferName);
    BufferInfoValue buffer = targets.outboundBuffer(session, typeBits, requested);
    port.observeTargetNetwork(session, requested.baseTarget(), buffer.networkId());
    session.bufferCommands.sendInput(buffer, input);
  }

  void sendRaw(QuasselCoreSession session, String operation, String rawLine) throws IOException {
    requireNetwork(session, operation);
    OutboundRawRoute route = routeOutboundRawLine(rawLine);
    BufferInfoValue buffer;
    if (route.requestedTarget() == null) {
      buffer = targets.outboundBuffer(session, BUFFER_STATUS, parseQualifiedTarget(""));
    } else {
      buffer = targets.outboundBuffer(session, route.targetTypeBitsHint(), route.requestedTarget());
      port.observeTargetNetwork(session, route.requestedTarget().baseTarget(), buffer.networkId());
    }
    session.bufferCommands.sendInput(buffer, "/QUOTE " + route.rewrittenRawLine());
  }

  private static void requireNetwork(QuasselCoreSession session, String operation) {
    if (session == null) {
      throw new IllegalStateException("Quassel session is missing");
    }
    if (session.networks.firstKnownNetworkId(session.authResult.get(), session.buffers.values())
        < 0) {
      throw new BackendNotAvailableException(
          IrcProperties.Server.Backend.QUASSEL_CORE,
          operation,
          session.serverId,
          "no active Quassel network is available yet");
    }
  }
}
