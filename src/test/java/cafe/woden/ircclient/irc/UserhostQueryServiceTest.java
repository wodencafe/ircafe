package cafe.woden.ircclient.irc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class UserhostQueryServiceTest {

  @Test
  void enqueueDeduplicatesNicksAndSendsSingleUserhostBatch() throws Exception {
    Fixture fixture = fixtureWithSettings(settings(true));
    try {
      fixture.service.enqueue("libera", List.of("alice", "alice", "bob"));

      invokeTickAll(fixture.service);
      invokeTickAll(fixture.service);

      verify(fixture.irc, times(1)).sendRaw("libera", "USERHOST alice bob");
    } finally {
      fixture.service.shutdown();
    }
  }

  @Test
  void nickCooldownPreventsImmediateRepeatLookups() throws Exception {
    Fixture fixture = fixtureWithSettings(settings(true));
    try {
      fixture.service.enqueue("libera", List.of("alice"));
      invokeTickAll(fixture.service);

      fixture.service.enqueue("libera", List.of("alice"));
      invokeTickAll(fixture.service);

      verify(fixture.irc, times(1)).sendRaw("libera", "USERHOST alice");
    } finally {
      fixture.service.shutdown();
    }
  }

  @Test
  void disabledDiscoverySkipsSending() throws Exception {
    Fixture fixture = fixtureWithSettings(settings(false));
    try {
      fixture.service.enqueue("libera", List.of("alice"));
      invokeTickAll(fixture.service);

      verify(fixture.irc, never()).sendRaw(anyString(), anyString());
    } finally {
      fixture.service.shutdown();
    }
  }

  private static Fixture fixtureWithSettings(IrcRuntimeSettings settings) {
    IrcClientService irc = mock(IrcClientService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<IrcRuntimeSettingsProvider> settingsProvider = mock(ObjectProvider.class);
    ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
    @SuppressWarnings("unchecked")
    ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);

    when(irc.sendRaw(anyString(), anyString())).thenReturn(Completable.complete());
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

    return new Fixture(new UserhostQueryService(irc, settingsProvider, exec), irc);
  }

  private static IrcRuntimeSettings settings(boolean enabled) {
    return new IrcRuntimeSettings(
        enabled, 1, 10, 30, 5, false, 15, 3, 60, 5, false, 45, 120, false, 300, 2);
  }

  private static void invokeTickAll(UserhostQueryService service) throws Exception {
    Method method = UserhostQueryService.class.getDeclaredMethod("tickAll");
    method.setAccessible(true);
    method.invoke(service);
  }

  private record Fixture(UserhostQueryService service, IrcClientService irc) {}
}
