package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.irc.quassel.QuasselCoreAuthHandshake.CoreSetupRequest;
import cafe.woden.ircclient.irc.quassel.QuasselCoreProtocolProbe.ProbeSelection;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupRequest;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreHandshakeCoordinatorTest {
  private final QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
  private final QuasselCoreProtocolProbe probe = mock(QuasselCoreProtocolProbe.class);
  private final QuasselCoreAuthHandshake auth = mock(QuasselCoreAuthHandshake.class);
  private final Socket socket = mock(Socket.class);
  private final QuasselCoreHandshakeCoordinator coordinator =
      new QuasselCoreHandshakeCoordinator(connector, probe, auth);
  private final IrcProperties.Server server =
      IrcPropertiesTestFixtures.serverBuilder("core")
          .host("core.example")
          .port(4242)
          .backend(IrcProperties.Server.Backend.QUASSEL_CORE)
          .build();
  private final ProbeSelection datastream = new ProbeSelection(0x03001102, 2, 0x0011, 3);
  private final QuasselCoreSetupRequest request =
      new QuasselCoreSetupRequest(
          "admin",
          " password with spaces ",
          "PostgreSQL",
          "LDAP",
          Map.of("Host", "db.example", "Port", 5432),
          Map.of("Url", "ldaps://directory.example"));

  @BeforeEach
  void prepareTransport() throws Exception {
    when(connector.connect(server)).thenReturn(socket);
    when(probe.negotiate(socket)).thenReturn(datastream);
  }

  @Test
  void datastreamNegotiationReturnsTheExactSelectionAndLeavesTheCallerSocketOpen()
      throws Exception {
    assertSame(datastream, coordinator.negotiateDatastream(socket));
    verify(probe).negotiate(socket);
    verifyNoMoreInteractions(probe);
    verifyNoInteractions(socket, connector, auth);
  }

  @ParameterizedTest
  @CsvSource({"1, legacy", "0, unknown(0)", "255, unknown(255)"})
  void unsupportedNegotiationReportsTheProtocolWithoutClosingTheCallerSocket(
      int protocol, String label) throws Exception {
    when(probe.negotiate(socket)).thenReturn(new ProbeSelection(protocol, protocol, 0, 0));
    var error =
        assertThrows(IllegalStateException.class, () -> coordinator.negotiateDatastream(socket));
    assertEquals("Quassel core selected unsupported protocol " + label, error.getMessage());
    verify(probe).negotiate(socket);
    verifyNoInteractions(socket, connector, auth);
  }

  @Test
  void negotiationFailurePropagatesWithoutClosingTheCallerSocket() throws Exception {
    var error = new IOException("probe failed");
    when(probe.negotiate(socket)).thenThrow(error);
    assertSame(
        error, assertThrows(IOException.class, () -> coordinator.negotiateDatastream(socket)));
    verifyNoInteractions(socket, connector, auth);
  }

  @Test
  void setupConnectsNegotiatesSendsEverySetupFieldAndClosesItsSocketInOrder() throws Exception {
    coordinator.performSetup(server, request);
    var setup = org.mockito.ArgumentCaptor.forClass(CoreSetupRequest.class);
    var order = inOrder(connector, probe, auth, socket);
    order.verify(connector).connect(server);
    order.verify(probe).negotiate(socket);
    order.verify(auth).performCoreSetup(eq(socket), setup.capture());
    order.verify(socket).close();
    order.verifyNoMoreInteractions();
    assertEquals("admin", setup.getValue().adminUser());
    assertEquals(" password with spaces ", setup.getValue().adminPassword());
    assertEquals("PostgreSQL", setup.getValue().storageBackend());
    assertEquals("LDAP", setup.getValue().authenticator());
    assertEquals(Map.of("Host", "db.example", "Port", 5432), setup.getValue().storageSetupData());
    assertEquals(Map.of("Url", "ldaps://directory.example"), setup.getValue().authSetupData());
  }

  @ParameterizedTest
  @ValueSource(strings = {"connect", "probe", "setup"})
  void setupFailureStopsTheWorkflowAndClosesOnlyAnOpenedSocketWithoutRetry(String stage)
      throws Exception {
    var error = new IOException(stage + " failed");
    switch (stage) {
      case "connect" -> when(connector.connect(server)).thenThrow(error);
      case "probe" -> when(probe.negotiate(socket)).thenThrow(error);
      case "setup" -> doThrow(error).when(auth).performCoreSetup(eq(socket), any());
      default -> throw new AssertionError(stage);
    }
    assertSame(
        error, assertThrows(IOException.class, () -> coordinator.performSetup(server, request)));
    var order = inOrder(connector, probe, auth, socket);
    order.verify(connector).connect(server);
    if (!"connect".equals(stage)) {
      order.verify(probe).negotiate(socket);
      if ("setup".equals(stage)) order.verify(auth).performCoreSetup(eq(socket), any());
      order.verify(socket).close();
    }
    order.verifyNoMoreInteractions();
  }

  @ParameterizedTest
  @CsvSource({"1, legacy", "255, unknown(255)"})
  void unsupportedSetupProtocolClosesTheTemporarySocketWithoutSubmittingSetup(
      int protocol, String label) throws Exception {
    when(probe.negotiate(socket)).thenReturn(new ProbeSelection(protocol, protocol, 0, 0));
    var error =
        assertThrows(IllegalStateException.class, () -> coordinator.performSetup(server, request));
    assertEquals("Quassel core selected unsupported protocol " + label, error.getMessage());
    var order = inOrder(connector, probe, socket);
    order.verify(connector).connect(server);
    order.verify(probe).negotiate(socket);
    order.verify(socket).close();
    order.verifyNoMoreInteractions();
    verifyNoInteractions(auth);
  }

  @Test
  void failureClosingAfterSuccessfulSetupPropagatesWithoutRetry() throws Exception {
    var error = new IOException("close failed");
    doThrow(error).when(socket).close();
    assertSame(
        error, assertThrows(IOException.class, () -> coordinator.performSetup(server, request)));
    verify(connector).connect(server);
    verify(probe).negotiate(socket);
    verify(auth).performCoreSetup(eq(socket), any());
    verify(socket).close();
    verifyNoMoreInteractions(connector, probe, auth, socket);
    assertEquals(0, error.getSuppressed().length);
  }

  @ParameterizedTest
  @ValueSource(strings = {"probe", "setup"})
  void closeFailureIsSuppressedBehindTheOriginalHandshakeFailure(String stage) throws Exception {
    var error = new IOException(stage + " failed");
    var closeError = new IOException("close failed");
    if ("probe".equals(stage)) {
      when(probe.negotiate(socket)).thenThrow(error);
    } else {
      doThrow(error).when(auth).performCoreSetup(eq(socket), any());
    }
    doThrow(closeError).when(socket).close();
    assertSame(
        error, assertThrows(IOException.class, () -> coordinator.performSetup(server, request)));
    assertArrayEquals(new Throwable[] {closeError}, error.getSuppressed());
    verify(socket).close();
  }

  @Test
  void runtimeSetupFailureAlsoClosesTheTemporarySocket() throws Exception {
    var error = new IllegalStateException("setup rejected");
    doThrow(error).when(auth).performCoreSetup(eq(socket), any());
    assertSame(
        error,
        assertThrows(IllegalStateException.class, () -> coordinator.performSetup(server, request)));
    verify(socket).close();
    verify(auth).performCoreSetup(eq(socket), any());
  }

  @Test
  void repeatedSetupsOpenAndCloseSeparateSockets() throws Exception {
    var secondSocket = mock(Socket.class);
    when(connector.connect(server)).thenReturn(socket, secondSocket);
    when(probe.negotiate(secondSocket)).thenReturn(datastream);
    coordinator.performSetup(server, request);
    coordinator.performSetup(server, request);
    var order = inOrder(connector, probe, auth, socket, secondSocket);
    order.verify(connector).connect(server);
    order.verify(probe).negotiate(socket);
    order.verify(auth).performCoreSetup(eq(socket), any());
    order.verify(socket).close();
    order.verify(connector).connect(server);
    order.verify(probe).negotiate(secondSocket);
    order.verify(auth).performCoreSetup(eq(secondSocket), any());
    order.verify(secondSocket).close();
    order.verifyNoMoreInteractions();
  }
}
