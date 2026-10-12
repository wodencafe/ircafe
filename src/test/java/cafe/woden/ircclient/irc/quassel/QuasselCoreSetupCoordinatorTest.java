package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSetupCoordinatorTest {
  private final ServerCatalog catalog = mock(ServerCatalog.class);
  private final QuasselCoreHandshakeCoordinator handshakes =
      mock(QuasselCoreHandshakeCoordinator.class);
  private final IrcProperties.Server server =
      IrcPropertiesTestFixtures.serverBuilder("core").build();
  private final List<String> trace = new CopyOnWriteArrayList<>();
  private final List<ServerIrcEvent> events = new CopyOnWriteArrayList<>();
  private final AtomicReference<Consumer<ServerIrcEvent>> eventObserver =
      new AtomicReference<>(ignored -> {});
  private final QuasselCoreSessionEventPublisher sessionEvents =
      new QuasselCoreSessionEventPublisher(
          event -> {
            String kind =
                event.event() instanceof IrcEvent.ConnectionFeaturesUpdated
                    ? "phase"
                    : event.event() instanceof IrcEvent.Error ? "error" : "disconnected";
            trace.add(kind);
            events.add(event);
            eventObserver.get().accept(event);
          });
  private final Map<String, String> reasons = new ConcurrentHashMap<>();
  private final AtomicReference<Thread> worker = new AtomicReference<>();
  private final AtomicReference<Runnable> preparation = new AtomicReference<>(() -> {});
  private final QuasselCoreSetupCoordinator coordinator =
      new QuasselCoreSetupCoordinator(
          catalog,
          handshakes,
          serverId -> {
            trace.add("prepare:" + serverId);
            worker.set(Thread.currentThread());
            preparation.get().run();
          },
          (serverId, reason) -> {
            trace.add("reason:" + serverId);
            reasons.put(serverId, reason);
          },
          sessionEvents);
  private final QuasselCoreSetupRequest request =
      new QuasselCoreSetupRequest(
          " admin ", " secret ", "", "", Map.of(" host ", "db.example"), null);

  @Test
  void requiredPromptRecordsAvailabilityBeforePublishingOptionsAndCanBeQueriedByNormalizedId() {
    coordinator.rememberRequired(
        "core",
        " setup required ",
        Map.of("StorageBackends", List.of("PostgreSQL"), "Authenticators", List.of("LDAP")));
    assertTrue(coordinator.isPending(" core "));
    var prompt = coordinator.prompt(" core ").orElseThrow();
    assertEquals("core", prompt.serverId());
    assertEquals("setup required", prompt.detail());
    assertEquals(List.of("PostgreSQL"), prompt.storageBackends());
    assertEquals(List.of("LDAP"), prompt.authenticators());
    assertEquals(" setup required ", reasons.get("core"));
    assertEquals(List.of("reason:core"), trace);
    verifyNoInteractions(catalog, handshakes);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void blankIdsAreNotPendingAndSubmitFailsOnlyOnSubscription(String serverId) {
    pending();
    assertFalse(coordinator.isPending(serverId));
    assertTrue(coordinator.prompt(serverId).isEmpty());
    Completable submission = assertDoesNotThrow(() -> coordinator.submit(serverId, request));
    assertTrue(trace.isEmpty());
    submission
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && "server id is blank".equals(error.getMessage()));
    assertTrue(trace.isEmpty());
    verifyNoInteractions(catalog, handshakes);
    assertTrue(coordinator.isPending("core"));
  }

  @Test
  void missingPromptFailsBeforeCatalogLookupOrSessionPreparation() {
    coordinator
        .submit(" core ", request)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalStateException
                    && "Quassel Core setup is not pending for core".equals(error.getMessage()));
    assertTrue(trace.isEmpty());
    verifyNoInteractions(catalog, handshakes);
  }

  @Test
  void successfulSubmissionIsDeferredNormalizesValuesAndCompletesAfterHandshakeOnIo()
      throws Exception {
    pending();
    when(catalog.require("core"))
        .thenAnswer(
            invocation -> {
              trace.add("catalog");
              return server;
            });
    doAnswer(
            invocation -> {
              assertTrue(coordinator.isPending("core"));
              assertEquals("required", reasons.get("core"));
              assertEquals(worker.get(), Thread.currentThread());
              trace.add("handshake");
              return null;
            })
        .when(handshakes)
        .performSetup(eq(server), any());
    Completable submission = coordinator.submit(" core ", request);
    verifyNoInteractions(catalog, handshakes);
    assertTrue(trace.isEmpty());
    Thread caller = Thread.currentThread();
    complete(submission);
    assertNotSame(caller, worker.get());
    assertEquals(List.of("catalog", "prepare:core", "handshake", "reason:core"), trace);
    var normalized = org.mockito.ArgumentCaptor.forClass(QuasselCoreSetupRequest.class);
    verify(handshakes).performSetup(eq(server), normalized.capture());
    assertEquals("admin", normalized.getValue().adminUser());
    assertEquals(" secret ", normalized.getValue().adminPassword());
    assertEquals("PostgreSQL", normalized.getValue().storageBackend());
    assertEquals("LDAP", normalized.getValue().authenticator());
    assertEquals(Map.of("host", "db.example"), normalized.getValue().storageSetupData());
    assertEquals(Map.of(), normalized.getValue().authSetupData());
    assertFalse(coordinator.isPending("core"));
    assertTrue(coordinator.prompt("core").isEmpty());
    assertEquals(
        "Quassel Core setup completed. Reconnect to start a session.", reasons.get("core"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "user", "password"})
  void invalidRequestFailsBeforePreparingTheSessionAndRetainsThePrompt(String invalid) {
    pending();
    when(catalog.require("core")).thenReturn(server);
    QuasselCoreSetupRequest supplied =
        switch (invalid) {
          case "null" -> null;
          case "user" -> new QuasselCoreSetupRequest(" ", "secret", "", "", null, null);
          case "password" -> new QuasselCoreSetupRequest("admin", " ", "", "", null, null);
          default -> throw new AssertionError(invalid);
        };
    coordinator
        .submit("core", supplied)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                "null".equals(invalid)
                    ? error instanceof NullPointerException && "request".equals(error.getMessage())
                    : error instanceof IllegalArgumentException);
    verify(catalog).require("core");
    verifyNoInteractions(handshakes);
    assertTrue(trace.isEmpty());
    assertTrue(coordinator.isPending("core"));
    assertEquals("required", reasons.get("core"));
  }

  @Test
  void catalogFailurePrecedesRequestValidationAndKeepsSetupPending() {
    pending();
    var failure = new IllegalArgumentException("unknown server");
    when(catalog.require("core")).thenThrow(failure);
    coordinator.submit("core", null).test().awaitDone(5, TimeUnit.SECONDS).assertError(failure);
    assertTrue(coordinator.isPending("core"));
    assertTrue(trace.isEmpty());
    verifyNoInteractions(handshakes);
  }

  @Test
  void preparationFailureStopsBeforeHandshakeAndRetainsPendingState() {
    pending();
    when(catalog.require("core")).thenReturn(server);
    var failure = new IllegalStateException("session preparation failed");
    preparation.set(
        () -> {
          throw failure;
        });
    coordinator.submit("core", request).test().awaitDone(5, TimeUnit.SECONDS).assertError(failure);
    assertEquals(List.of("prepare:core"), trace);
    assertTrue(coordinator.isPending("core"));
    assertEquals("required", reasons.get("core"));
    verifyNoInteractions(handshakes);
  }

  @Test
  void handshakeFailureKeepsThePromptAndPreviousReasonAndAllowsAnExplicitRetry() throws Exception {
    pending();
    when(catalog.require("core")).thenReturn(server);
    var failure = new IOException("setup transport failed");
    doThrow(failure).doNothing().when(handshakes).performSetup(eq(server), any());
    Completable submission = coordinator.submit("core", request);
    var original = coordinator.prompt("core").orElseThrow();
    submission.test().awaitDone(5, TimeUnit.SECONDS).assertError(failure);
    assertSame(original, coordinator.prompt("core").orElseThrow());
    assertEquals("required", reasons.get("core"));
    verify(handshakes).performSetup(eq(server), any());
    complete(submission);
    verify(handshakes, times(2)).performSetup(eq(server), any());
    assertFalse(coordinator.isPending("core"));
    assertEquals(List.of("prepare:core", "prepare:core", "reason:core"), trace);
  }

  @Test
  void repeatedSubscriptionAfterSuccessDoesNotRepeatSetup() throws Exception {
    pending();
    when(catalog.require("core")).thenReturn(server);
    Completable submission = coordinator.submit("core", request);
    complete(submission);
    submission.test().awaitDone(5, TimeUnit.SECONDS).assertError(IllegalStateException.class);
    verify(catalog).require("core");
    verify(handshakes).performSetup(eq(server), any());
  }

  @Test
  void submissionReadsTheLatestPromptOptionsAtSubscriptionTime() throws Exception {
    pending();
    when(catalog.require("core")).thenReturn(server);
    Completable submission = coordinator.submit("core", request);
    coordinator.rememberRequired("core", "updated", Map.of());
    complete(submission);
    var normalized = org.mockito.ArgumentCaptor.forClass(QuasselCoreSetupRequest.class);
    verify(handshakes).performSetup(eq(server), normalized.capture());
    assertEquals("SQLite", normalized.getValue().storageBackend());
    assertEquals("Database", normalized.getValue().authenticator());
  }

  @Test
  void forgettingAndClearingPromptsAreIsolatedFromAvailabilityHistory() {
    pending();
    coordinator.rememberRequired("other", "other required", Map.of());
    coordinator.forget("core");
    assertFalse(coordinator.isPending("core"));
    assertTrue(coordinator.isPending("other"));
    assertEquals("required", reasons.get("core"));
    coordinator.clear();
    assertFalse(coordinator.isPending("other"));
    assertTrue(coordinator.prompt("other").isEmpty());
    assertEquals("other required", reasons.get("other"));
  }

  @Test
  void setupRequirementRecordsPromptBeforeDiagnosticsAndCancelsReadinessAfterError() {
    var session = reportingSession();
    var fallback = Disposable.fromRunnable(() -> trace.add("readiness"));
    session.readiness.fallbackTask.set(fallback);
    var fields =
        Map.<String, Object>of(
            "StorageBackends", List.of("PostgreSQL"), "Authenticators", List.of("LDAP"));
    var failure =
        new QuasselCoreAuthHandshake.CoreSetupRequiredException(" setup required ", fields);
    eventObserver.set(
        event -> {
          assertEquals("core", event.serverId());
          assertEquals("setup required", reasons.get("core"));
          var prompt = coordinator.prompt("core").orElseThrow();
          assertEquals(List.of("PostgreSQL"), prompt.storageBackends());
          assertEquals(List.of("LDAP"), prompt.authenticators());
          if (event.event() instanceof IrcEvent.Disconnected) {
            assertTrue(fallback.isDisposed());
            assertNull(session.readiness.fallbackTask.get());
          } else {
            assertFalse(fallback.isDisposed());
          }
        });
    coordinator.reportRequired(session, failure);
    assertEquals(List.of("reason:core", "phase", "error", "readiness", "disconnected"), trace);
    assertEquals(
        "quassel-phase=setup-required;detail=setup required",
        assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.get(0).event()).source());
    assertSame(failure, assertInstanceOf(IrcEvent.Error.class, events.get(1).event()).cause());
    assertEquals(
        "setup required",
        assertInstanceOf(IrcEvent.Disconnected.class, events.get(2).event()).reason());
    assertEquals(QuasselSessionPhase.AUTHENTICATING, session.phase.get());
    assertFalse(session.closeRequested.get());
    assertFalse(session.reconnectScheduled.get());
    assertTrue(session.disconnectedEmitted.get());
    verifyNoInteractions(catalog, handshakes);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", " setup required "})
  void setupRequirementKeepsExceptionMessageDefaultsAndClassFallback(String message) {
    var failure = new QuasselCoreAuthHandshake.CoreSetupRequiredException(message, null);
    String expected =
        message == null
            ? "Quassel Core setup is required"
            : message.isBlank() ? "CoreSetupRequiredException" : "setup required";
    coordinator.reportRequired(reportingSession(), failure);
    assertEquals(expected, reasons.get("core"));
    var prompt = coordinator.prompt("core").orElseThrow();
    assertEquals(expected, prompt.detail());
    assertEquals(List.of("SQLite"), prompt.storageBackends());
    assertEquals(List.of("Database"), prompt.authenticators());
    assertEquals(expected, assertInstanceOf(IrcEvent.Error.class, events.get(1).event()).message());
    assertSame(failure, assertInstanceOf(IrcEvent.Error.class, events.get(1).event()).cause());
    assertEquals(
        expected, assertInstanceOf(IrcEvent.Disconnected.class, events.get(2).event()).reason());
    verifyNoInteractions(catalog, handshakes);
  }

  @Test
  void repeatedRequirementUpdatesPromptAndDiagnosticsWhileDisconnectGuardStaysOnTheSession() {
    var session = reportingSession();
    coordinator.reportRequired(
        session, new QuasselCoreAuthHandshake.CoreSetupRequiredException("first", Map.of()));
    coordinator.reportRequired(
        session,
        new QuasselCoreAuthHandshake.CoreSetupRequiredException(
            "updated", Map.of("StorageBackends", List.of("PostgreSQL"))));
    assertEquals(5, events.size());
    assertEquals(
        1, events.stream().filter(event -> event.event() instanceof IrcEvent.Disconnected).count());
    assertEquals("updated", reasons.get("core"));
    assertEquals(List.of("PostgreSQL"), coordinator.prompt("core").orElseThrow().storageBackends());
    coordinator.reportRequired(
        reportingSession(),
        new QuasselCoreAuthHandshake.CoreSetupRequiredException("replacement", Map.of()));
    assertEquals(
        2, events.stream().filter(event -> event.event() instanceof IrcEvent.Disconnected).count());
    assertEquals("replacement", reasons.get("core"));
    verifyNoInteractions(catalog, handshakes);
  }

  @ParameterizedTest
  @ValueSource(strings = {"phase", "error"})
  void observerFailureRetainsPromptAndStopsBeforeLaterNotifications(String rejected) {
    var session = reportingSession();
    var fallback = Disposable.empty();
    session.readiness.fallbackTask.set(fallback);
    var observerFailure = new IllegalStateException("observer failed");
    eventObserver.set(
        event -> {
          if (("phase".equals(rejected)
                  && event.event() instanceof IrcEvent.ConnectionFeaturesUpdated)
              || ("error".equals(rejected) && event.event() instanceof IrcEvent.Error)) {
            throw observerFailure;
          }
        });
    assertSame(
        observerFailure,
        assertThrows(
            IllegalStateException.class,
            () ->
                coordinator.reportRequired(
                    session,
                    new QuasselCoreAuthHandshake.CoreSetupRequiredException(
                        "required", Map.of()))));
    assertEquals("required", reasons.get("core"));
    assertTrue(coordinator.isPending("core"));
    assertEquals("phase".equals(rejected) ? 1 : 2, events.size());
    assertFalse(session.disconnectedEmitted.get());
    assertFalse(fallback.isDisposed());
    verifyNoInteractions(catalog, handshakes);
    session.readiness.close();
  }

  private static QuasselCoreSession reportingSession() {
    var session =
        new QuasselCoreSession(
            "core",
            "alice",
            "host",
            4242,
            (socket, operation) -> fail("setup reporting must not send"),
            ignored -> {},
            ignored -> {});
    session.phase.set(QuasselSessionPhase.AUTHENTICATING);
    return session;
  }

  private void pending() {
    coordinator.rememberRequired(
        "core",
        "required",
        Map.of("StorageBackends", List.of("PostgreSQL"), "Authenticators", List.of("LDAP")));
    trace.clear();
  }

  private static void complete(Completable submission) {
    submission.test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors();
  }
}
