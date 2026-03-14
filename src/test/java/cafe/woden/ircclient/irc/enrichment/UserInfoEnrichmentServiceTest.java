package cafe.woden.ircclient.irc.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.IrcRuntimeSettings;
import cafe.woden.ircclient.irc.IrcRuntimeSettingsProvider;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class UserInfoEnrichmentServiceTest {

  @Test
  void enqueueWhoisIsIgnoredWhenWhoisFallbackIsDisabled() {
    Fixture fixture = fixtureWithSettings(settings(true, false));
    try {
      fixture.service.enqueueWhois("libera", List.of("alice"));

      verify(fixture.planner, never()).enqueueWhois(anyString(), any());
    } finally {
      fixture.service.shutdown();
    }
  }

  @Test
  void whoxSupportAndSchemaEventsControlChannelScanEligibility() {
    Fixture fixture = fixtureWithSettings(settings(true, true));
    try {
      assertFalse(fixture.service.shouldUseWhoxForChannelScan("libera"));

      fixture.events.onNext(
          new ServerIrcEvent(
              "libera",
              new IrcEvent.WhoxSupportObserved(Instant.parse("2026-02-26T00:00:00Z"), true)));
      assertTrue(fixture.service.shouldUseWhoxForChannelScan("libera"));

      fixture.events.onNext(
          new ServerIrcEvent(
              "libera",
              new IrcEvent.WhoxSchemaCompatibleObserved(
                  Instant.parse("2026-02-26T00:00:01Z"), false, "mismatch")));
      assertFalse(fixture.service.shouldUseWhoxForChannelScan("libera"));

      fixture.events.onNext(
          new ServerIrcEvent(
              "libera",
              new IrcEvent.WhoxSchemaCompatibleObserved(
                  Instant.parse("2026-02-26T00:00:02Z"), true, "ok")));
      assertTrue(fixture.service.shouldUseWhoxForChannelScan("libera"));
    } finally {
      fixture.service.shutdown();
    }
  }

  @Test
  void noteUserActivityStoresCaseInsensitiveTimestampsAndClearServerResetsState() {
    Fixture fixture = fixtureWithSettings(settings(true, true));
    try {
      Instant at = Instant.parse("2026-02-26T00:10:00Z");
      fixture.service.noteUserActivity("libera", "Alice", at);

      assertEquals(at, fixture.service.lastActiveAt("libera", "alice"));

      fixture.service.clearServer("libera");

      assertNull(fixture.service.lastActiveAt("libera", "alice"));
      assertFalse(fixture.service.shouldUseWhoxForChannelScan("libera"));
      verify(fixture.planner).clearServer("libera");
    } finally {
      fixture.service.shutdown();
    }
  }

  @Test
  void backendUnavailableSkipsScheduledEnrichmentPolling() throws Exception {
    Fixture fixture = fixtureWithSettings(settings(true, true));
    try {
      when(fixture.irc.backendAvailabilityReason("libera"))
          .thenReturn("Quassel Core backend is not implemented yet");

      invokeOnEvent(
          fixture.service,
          new ServerIrcEvent(
              "libera",
              new IrcEvent.Connected(Instant.parse("2026-03-03T01:00:00Z"), "irc", 6697, "me")));
      invokeTick(fixture.service);

      verify(fixture.planner, never()).pollNext(anyString(), any(), any());
    } finally {
      fixture.service.shutdown();
    }
  }

  private static Fixture fixtureWithSettings(IrcRuntimeSettings settings) {
    IrcBackendClientService irc = mock(IrcBackendClientService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<IrcRuntimeSettingsProvider> settingsProvider = mock(ObjectProvider.class);
    UserInfoEnrichmentPlanner planner = mock(UserInfoEnrichmentPlanner.class);
    ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
    @SuppressWarnings("unchecked")
    ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
    PublishProcessor<ServerIrcEvent> events = PublishProcessor.create();

    when(irc.events()).thenReturn(events);
    when(irc.currentNick(anyString())).thenReturn(Optional.of("me"));
    when(settingsProvider.getIfAvailable())
        .thenReturn(
            new IrcRuntimeSettingsProvider() {
              @Override
              public IrcRuntimeSettings current() {
                return settings;
              }
            });
    when(exec.isShutdown()).thenReturn(false);
    doReturn(scheduled)
        .when(exec)
        .schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));

    UserInfoEnrichmentService service =
        new UserInfoEnrichmentService(irc, irc, settingsProvider, planner, exec);
    return new Fixture(service, irc, planner, events);
  }

  private static IrcRuntimeSettings settings(boolean enrichmentEnabled, boolean whoisFallback) {
    return new IrcRuntimeSettings(
        true, 7, 6, 30, 5, enrichmentEnabled, 15, 3, 60, 5, whoisFallback, 45, 120, false, 300, 2);
  }

  private record Fixture(
      UserInfoEnrichmentService service,
      IrcBackendClientService irc,
      UserInfoEnrichmentPlanner planner,
      PublishProcessor<ServerIrcEvent> events) {}

  private static void invokeOnEvent(UserInfoEnrichmentService service, ServerIrcEvent event)
      throws Exception {
    Method onEvent =
        UserInfoEnrichmentService.class.getDeclaredMethod("onEvent", ServerIrcEvent.class);
    onEvent.setAccessible(true);
    onEvent.invoke(service, event);
  }

  private static void invokeTick(UserInfoEnrichmentService service) throws Exception {
    Method tick = UserInfoEnrichmentService.class.getDeclaredMethod("tick");
    tick.setAccessible(true);
    tick.invoke(service);
  }
}
