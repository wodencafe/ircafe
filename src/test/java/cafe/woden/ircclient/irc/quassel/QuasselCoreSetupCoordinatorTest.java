package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupRequest;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
          });
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
