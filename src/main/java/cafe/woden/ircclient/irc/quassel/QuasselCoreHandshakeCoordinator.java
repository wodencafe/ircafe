package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupRequest;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

/**
 * Enforces the supported protocol for connection and setup handshakes. Owns the temporary setup
 * socket; negotiation leaves the caller's session socket open.
 */
final class QuasselCoreHandshakeCoordinator {
  private final QuasselCoreSocketConnector socketConnector;
  private final QuasselCoreProtocolProbe protocolProbe;
  private final QuasselCoreAuthHandshake authHandshake;

  QuasselCoreHandshakeCoordinator(
      QuasselCoreSocketConnector socketConnector,
      QuasselCoreProtocolProbe protocolProbe,
      QuasselCoreAuthHandshake authHandshake) {
    this.socketConnector = Objects.requireNonNull(socketConnector, "socketConnector");
    this.protocolProbe = Objects.requireNonNull(protocolProbe, "protocolProbe");
    this.authHandshake = Objects.requireNonNull(authHandshake, "authHandshake");
  }

  QuasselCoreProtocolProbe.ProbeSelection negotiateDatastream(Socket socket) throws IOException {
    QuasselCoreProtocolProbe.ProbeSelection probe = protocolProbe.negotiate(socket);
    if (probe.protocolType() != QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM) {
      throw new IllegalStateException(
          "Quassel core selected unsupported protocol "
              + QuasselCoreProtocolProbe.protocolLabel(probe.protocolType()));
    }
    return probe;
  }

  void performSetup(IrcProperties.Server server, QuasselCoreSetupRequest request)
      throws IOException {
    try (Socket socket = socketConnector.connect(server)) {
      negotiateDatastream(socket);
      authHandshake.performCoreSetup(
          socket,
          new QuasselCoreAuthHandshake.CoreSetupRequest(
              request.adminUser(),
              request.adminPassword(),
              request.storageBackend(),
              request.authenticator(),
              request.storageSetupData(),
              request.authSetupData()));
    }
  }
}
