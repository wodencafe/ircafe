package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcLagProbePort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IrcLagProbePortAdapterTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void delegatesLagProbeOperations(boolean nativePort) {
    IrcClientService irc = nativePort ? mock(NativeClient.class) : mock(IrcClientService.class);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));
    when(irc.requestLagProbe("libera")).thenReturn(Completable.complete());
    when(irc.isLagProbeReady("libera")).thenReturn(true);
    when(irc.lastMeasuredLagMs("libera")).thenReturn(OptionalLong.of(123L));

    IrcLagProbePort port = new IrcLagProbePortAdapter(irc);

    assertEquals("alice", port.currentNick("libera").orElse(""));
    port.requestLagProbe("libera").blockingAwait();
    assertTrue(port.isLagProbeReady("libera"));
    assertTrue(port.lastMeasuredLagMs("libera").isPresent());
    assertEquals(123L, port.lastMeasuredLagMs("libera").orElse(-1L));
  }

  @Test
  void rejectsMissingClient() {
    assertThrows(NullPointerException.class, () -> new IrcLagProbePortAdapter(null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankLegacyServerIdDoesNotReachTheClient(String serverId) {
    IrcClientService irc = mock(IrcClientService.class);
    assertTrue(new IrcLagProbePortAdapter(irc).currentNick(serverId).isEmpty());
    verifyNoInteractions(irc);
  }

  @Test
  void legacyNicknameLookupTrimsServerId() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.currentNick("libera")).thenReturn(Optional.of("alice"));
    assertEquals("alice", new IrcLagProbePortAdapter(irc).currentNick(" libera ").orElseThrow());
    verify(irc).currentNick("libera");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", " libera "})
  void nativePortOwnsServerIdNormalization(String serverId) {
    NativeClient irc = mock(NativeClient.class);
    when(irc.currentNick(serverId)).thenReturn(Optional.of("native"));
    assertEquals("native", new IrcLagProbePortAdapter(irc).currentNick(serverId).orElseThrow());
    verify(irc).currentNick(serverId);
  }

  @Test
  void readinessAndProbePolicyComeFromTheClientRatherThanTheNickname() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.currentNick("libera")).thenReturn(Optional.of("alice"));
    when(irc.isLagProbeReady("libera")).thenReturn(false);
    when(irc.shouldRequestLagProbe("libera")).thenReturn(false);
    IrcLagProbePort port = new IrcLagProbePortAdapter(irc);
    assertTrue(port.currentNick("libera").isPresent());
    assertFalse(port.isLagProbeReady("libera"));
    assertFalse(port.shouldRequestLagProbe("libera"));
  }

  @Test
  void probeFailureIsPropagatedToTheSubscriber() {
    IrcClientService irc = mock(IrcClientService.class);
    IllegalStateException failure = new IllegalStateException("offline");
    when(irc.requestLagProbe("libera")).thenReturn(Completable.error(failure));
    new IrcLagProbePortAdapter(irc).requestLagProbe("libera").test().assertError(failure);
  }

  private interface NativeClient extends IrcClientService, IrcLagProbePort {
    @Override
    Optional<String> currentNick(String serverId);

    @Override
    Completable requestLagProbe(String serverId);

    @Override
    boolean shouldRequestLagProbe(String serverId);

    @Override
    boolean isLagProbeReady(String serverId);

    @Override
    OptionalLong lastMeasuredLagMs(String serverId);
  }
}
