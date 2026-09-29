package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IrcReadMarkerPortAdapterTest {

  @Test
  void delegatesReadMarkerChecks() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.isReadMarkerAvailable("libera")).thenReturn(true);

    IrcReadMarkerPort port = new IrcReadMarkerPortAdapter(irc);

    assertTrue(port.isReadMarkerAvailable("libera"));
  }

  @Test
  void nullClientRetainsUnsupportedDefaults() {
    IrcReadMarkerPort port = new IrcReadMarkerPortAdapter(null);

    assertFalse(port.isReadMarkerAvailable("libera"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> port.sendReadMarker("libera", "#ircafe", Instant.now()).blockingAwait());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void sendsExactMarkerLazilyForLegacyAndNativeClients(boolean nativePort) {
    IrcClientService irc = nativePort ? mock(NativeClient.class) : mock(IrcClientService.class);
    Instant marker = Instant.parse("2026-09-27T00:00:00.123456789Z");
    AtomicInteger sends = new AtomicInteger();
    when(irc.sendReadMarker(" libera ", "#CaseSensitiveChannel", marker))
        .thenReturn(Completable.fromAction(sends::incrementAndGet));
    when(irc.isReadMarkerAvailable(" libera ")).thenReturn(true);
    IrcReadMarkerPort port = new IrcReadMarkerPortAdapter(irc);

    assertTrue(port.isReadMarkerAvailable(" libera "));
    Completable send = port.sendReadMarker(" libera ", "#CaseSensitiveChannel", marker);
    assertEquals(0, sends.get());
    send.test().assertComplete();
    assertEquals(1, sends.get());
    verify(irc).sendReadMarker(" libera ", "#CaseSensitiveChannel", marker);
  }

  @Test
  void sendFailureReachesSubscriber() {
    IrcClientService irc = mock(IrcClientService.class);
    IllegalStateException failure = new IllegalStateException("offline");
    when(irc.sendReadMarker("libera", "#ircafe", Instant.EPOCH))
        .thenReturn(Completable.error(failure));
    new IrcReadMarkerPortAdapter(irc)
        .sendReadMarker("libera", "#ircafe", Instant.EPOCH)
        .test()
        .assertError(failure);
  }

  private interface NativeClient extends IrcClientService, IrcReadMarkerPort {
    @Override
    boolean isReadMarkerAvailable(String serverId);

    @Override
    Completable sendReadMarker(String serverId, String target, Instant markerAt);
  }
}
