package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.irc.quassel.QuasselCoreAuthHandshake.CoreSetupRequiredException;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSessionEstablisherTest {
  private static final String PROBE_SOURCE =
      "quassel-probe protocol=datastream proto-features=0x0011 conn-features=0x03";
  private static final List<String> STAGES =
      List.of(
          "guard-before",
          "connect",
          "guard-after",
          "probe",
          "connected",
          "phase",
          "authenticate",
          "bootstrap");
  private final QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
  private final QuasselCoreHandshakeCoordinator handshakes =
      mock(QuasselCoreHandshakeCoordinator.class);
  private final QuasselCoreAuthHandshake authentication = mock(QuasselCoreAuthHandshake.class);
  private final QuasselCoreSessionBootstrap bootstrap = mock(QuasselCoreSessionBootstrap.class);
  private final QuasselCoreSessionLifecycle lifecycle = mock(QuasselCoreSessionLifecycle.class);
  private final QuasselCoreSetupCoordinator setups = mock(QuasselCoreSetupCoordinator.class);
  private final QuasselCoreSessionEventPublisher events =
      mock(QuasselCoreSessionEventPublisher.class);
  private final QuasselCoreSessionEstablisher establisher =
      new QuasselCoreSessionEstablisher(
          connector, handshakes, authentication, bootstrap, lifecycle, setups, events);
  private final IrcProperties.Server server =
      IrcPropertiesTestFixtures.serverBuilder("core").build();
  private final Socket socket = mock(Socket.class);
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "alice",
          "host",
          4242,
          (opened, operation) -> fail("establisher must delegate outbound requests"),
          ignored -> {},
          ignored -> {});
  private final QuasselCoreProtocolProbe.ProbeSelection probe =
      new QuasselCoreProtocolProbe.ProbeSelection(
          2, QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, 17, 3);
  private final QuasselCoreAuthHandshake.AuthResult auth =
      new QuasselCoreAuthHandshake.AuthResult("core", -1, List.of(), Map.of());
  private final List<String> trace = new ArrayList<>();

  @BeforeEach
  void configureStages() throws Exception {
    when(lifecycle.abortEstablishmentIfRequested(session, null))
        .thenAnswer(
            invocation -> {
              trace.add("guard-before");
              return false;
            });
    when(connector.connect(server))
        .thenAnswer(
            invocation -> {
              assertNull(session.socketRef.get());
              assertEquals(QuasselSessionPhase.TRANSPORT_CONNECTING, session.phase.get());
              trace.add("connect");
              return socket;
            });
    when(lifecycle.abortEstablishmentIfRequested(session, socket))
        .thenAnswer(
            invocation -> {
              trace.add("guard-after");
              return false;
            });
    when(handshakes.negotiateDatastream(socket))
        .thenAnswer(
            invocation -> {
              assertNull(session.socketRef.get());
              assertEquals(QuasselSessionPhase.TRANSPORT_CONNECTING, session.phase.get());
              trace.add("probe");
              return probe;
            });
    doAnswer(
            invocation -> {
              assertSame(socket, session.socketRef.get());
              assertEquals(QuasselSessionPhase.TRANSPORT_CONNECTED, session.phase.get());
              assertNull(session.probeSelection.get());
              trace.add("connected");
              return null;
            })
        .when(events)
        .emitConnected(session);
    doAnswer(
            invocation -> {
              assertSame(probe, session.probeSelection.get());
              assertEquals(QuasselSessionPhase.PROTOCOL_NEGOTIATED, session.phase.get());
              trace.add("phase");
              return null;
            })
        .when(events)
        .emitConnectionPhase(session, "protocol-negotiated", PROBE_SOURCE);
    when(authentication.authenticate(socket, server))
        .thenAnswer(
            invocation -> {
              assertSame(socket, session.socketRef.get());
              assertSame(probe, session.probeSelection.get());
              assertEquals(QuasselSessionPhase.AUTHENTICATING, session.phase.get());
              trace.add("authenticate");
              return auth;
            });
    doAnswer(
            invocation -> {
              assertEquals(QuasselSessionPhase.AUTHENTICATING, session.phase.get());
              trace.add("bootstrap");
              return null;
            })
        .when(bootstrap)
        .complete(session, auth);
    doAnswer(
            invocation -> {
              trace.add("failure");
              return null;
            })
        .when(lifecycle)
        .handleConnectFailure(same(session), nullable(Socket.class), any(Exception.class));
    doAnswer(
            invocation -> {
              trace.add("discard");
              return null;
            })
        .when(lifecycle)
        .discardFailedConnection(same(session), nullable(Socket.class));
    doAnswer(
            invocation -> {
              trace.add("setup");
              return null;
            })
        .when(setups)
        .reportRequired(same(session), any());
  }

  @Test
  void establishmentPreservesGuardNegotiationPublicationAuthenticationAndBootstrapOrder()
      throws Exception {
    establisher.establish(server, session);
    assertEquals(STAGES, trace);
    verify(bootstrap).complete(session, auth);
    verify(lifecycle).abortEstablishmentIfRequested(session, null);
    verify(lifecycle).abortEstablishmentIfRequested(session, socket);
    verifyNoMoreInteractions(lifecycle);
    verifyNoInteractions(setups);
  }

  @Test
  void preSocketCancellationStopsBeforeOpeningOrPublishing() {
    doReturn(true).when(lifecycle).abortEstablishmentIfRequested(session, null);
    establisher.establish(server, session);
    assertNull(session.socketRef.get());
    verify(lifecycle).abortEstablishmentIfRequested(session, null);
    verifyNoMoreInteractions(lifecycle);
    verifyNoInteractions(connector, handshakes, events, authentication, bootstrap, setups);
  }

  @Test
  void postSocketCancellationPassesOpenedSocketToTheGuardAndStopsBeforeNegotiation()
      throws Exception {
    doReturn(true).when(lifecycle).abortEstablishmentIfRequested(session, socket);
    establisher.establish(server, session);
    verify(connector).connect(server);
    verify(lifecycle).abortEstablishmentIfRequested(session, null);
    verify(lifecycle).abortEstablishmentIfRequested(session, socket);
    verifyNoMoreInteractions(lifecycle);
    assertNull(session.socketRef.get());
    verifyNoInteractions(handshakes, events, authentication, bootstrap, setups);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "guard-before",
        "connect",
        "guard-after",
        "probe",
        "connected",
        "phase",
        "authenticate",
        "bootstrap"
      })
  void stageFailureDelegatesTheOriginalExceptionAndActualOpenedSocketWithoutRunningLaterStages(
      String stage) throws Exception {
    var failure = new IllegalStateException("stage failed");
    switch (stage) {
      case "guard-before" ->
          doThrow(failure).when(lifecycle).abortEstablishmentIfRequested(session, null);
      case "connect" -> doThrow(failure).when(connector).connect(server);
      case "guard-after" ->
          doThrow(failure).when(lifecycle).abortEstablishmentIfRequested(session, socket);
      case "probe" -> doThrow(failure).when(handshakes).negotiateDatastream(socket);
      case "connected" -> doThrow(failure).when(events).emitConnected(session);
      case "phase" ->
          doThrow(failure)
              .when(events)
              .emitConnectionPhase(session, "protocol-negotiated", PROBE_SOURCE);
      case "authenticate" -> doThrow(failure).when(authentication).authenticate(socket, server);
      case "bootstrap" -> doThrow(failure).when(bootstrap).complete(session, auth);
      default -> throw new AssertionError(stage);
    }
    establisher.establish(server, session);
    int index = STAGES.indexOf(stage);
    var expected = new ArrayList<>(STAGES.subList(0, index));
    expected.add("failure");
    assertEquals(expected, trace);
    verify(lifecycle).handleConnectFailure(session, index <= 1 ? null : socket, failure);
    verify(lifecycle, never()).discardFailedConnection(any(), any());
    verifyNoInteractions(setups);
  }

  @ParameterizedTest
  @ValueSource(strings = {"connect", "authenticate", "bootstrap"})
  void setupRequirementIsReportedAfterCleanupWithoutGenericFailureHandling(String stage)
      throws Exception {
    var failure = new CoreSetupRequiredException("required", Map.of());
    if ("connect".equals(stage)) doThrow(failure).when(connector).connect(server);
    else if ("authenticate".equals(stage))
      doThrow(failure).when(authentication).authenticate(socket, server);
    else doThrow(failure).when(bootstrap).complete(session, auth);
    establisher.establish(server, session);
    verify(lifecycle).discardFailedConnection(session, "connect".equals(stage) ? null : socket);
    verify(setups).reportRequired(session, failure);
    assertEquals(List.of("discard", "setup"), trace.subList(trace.size() - 2, trace.size()));
    verify(lifecycle, never()).handleConnectFailure(any(), any(), any());
  }

  @Test
  void closeRequestedDuringSetupFailureCleanupSuppressesSetupReporting() throws Exception {
    var failure = new CoreSetupRequiredException("required", Map.of());
    doThrow(failure).when(authentication).authenticate(socket, server);
    doAnswer(
            invocation -> {
              session.closeRequested.set(true);
              return null;
            })
        .when(lifecycle)
        .discardFailedConnection(session, socket);
    establisher.establish(server, session);
    verify(lifecycle).discardFailedConnection(session, socket);
    verifyNoInteractions(setups);
    verify(lifecycle, never()).handleConnectFailure(any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"cleanup", "report"})
  void setupFailureHandlerExceptionsPropagateWithoutBeingRoutedToTheGenericHandler(String source)
      throws Exception {
    var required = new CoreSetupRequiredException("required", Map.of());
    doThrow(required).when(authentication).authenticate(socket, server);
    var failure = new IllegalStateException("handler failed");
    if ("cleanup".equals(source))
      doThrow(failure).when(lifecycle).discardFailedConnection(session, socket);
    else doThrow(failure).when(setups).reportRequired(session, required);
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> establisher.establish(server, session)));
    verify(lifecycle, never()).handleConnectFailure(any(), any(), any());
    if ("cleanup".equals(source)) verifyNoInteractions(setups);
  }

  @Test
  void transportIOExceptionAndFailureHandlerErrorsKeepTheirOriginalIdentity() throws Exception {
    var transport = new IOException("socket failed");
    doThrow(transport).when(connector).connect(server);
    var handling = new IllegalStateException("reporting failed");
    doThrow(handling).when(lifecycle).handleConnectFailure(session, null, transport);
    assertSame(
        handling,
        assertThrows(IllegalStateException.class, () -> establisher.establish(server, session)));
    verify(lifecycle).handleConnectFailure(session, null, transport);
  }
}
