package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import cafe.woden.ircclient.irc.DisconnectRequestSource;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcDisconnectWithSourcePort;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IrcConnectionLifecyclePortAdapterTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void delegatesLifecycleOperations(boolean nativeLifecycleClient) {
    IrcClientService irc =
        nativeLifecycleClient ? mock(LifecycleClient.class) : mock(IrcClientService.class);
    when(irc.connect("libera")).thenReturn(Completable.complete());
    when(irc.disconnect("libera")).thenReturn(Completable.complete());
    when(irc.disconnect("libera", "bye")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));

    IrcConnectionLifecyclePort port = new IrcConnectionLifecyclePortAdapter(irc);

    port.connect("libera").blockingAwait();
    port.disconnect("libera").blockingAwait();
    port.disconnect("libera", "bye").blockingAwait();
    assertTrue(port.currentNick("libera").isPresent());
    assertEquals("alice", port.currentNick("libera").orElse(""));
  }

  @Test
  void delegatesSourceAwareDisconnectWhenAvailable() {
    IrcClientService irc =
        mock(
            IrcClientService.class,
            withSettings().extraInterfaces(IrcDisconnectWithSourcePort.class));
    IrcDisconnectWithSourcePort sourceAware = (IrcDisconnectWithSourcePort) irc;
    when(sourceAware.disconnect("libera", "bye", DisconnectRequestSource.RECONNECT))
        .thenReturn(Completable.complete());

    IrcConnectionLifecyclePort port = new IrcConnectionLifecyclePortAdapter(irc);

    port.disconnect("libera", "bye", DisconnectRequestSource.RECONNECT).blockingAwait();

    verify(sourceAware).disconnect("libera", "bye", DisconnectRequestSource.RECONNECT);
  }

  @Test
  void rejectsMissingClient() {
    assertThrows(NullPointerException.class, () -> new IrcConnectionLifecyclePortAdapter(null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankServerIdDoesNotQueryTheClient(String serverId) {
    IrcClientService irc = mock(IrcClientService.class);

    assertTrue(new IrcConnectionLifecyclePortAdapter(irc).currentNick(serverId).isEmpty());

    verifyNoInteractions(irc);
  }

  @Test
  void nicknameLookupTrimsTheServerIdEvenForLifecycleClients() {
    IrcClientService irc = mock(LifecycleClient.class);
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));

    assertEquals(
        "alice", new IrcConnectionLifecyclePortAdapter(irc).currentNick(" libera ").orElseThrow());

    verify(irc).currentNick("libera");
  }

  @Test
  void sourceAwareClientReceivesUnknownForNullSource() {
    IrcClientService irc =
        mock(
            IrcClientService.class,
            withSettings().extraInterfaces(IrcDisconnectWithSourcePort.class));
    IrcDisconnectWithSourcePort sourceAware = (IrcDisconnectWithSourcePort) irc;
    when(sourceAware.disconnect("libera", "bye", DisconnectRequestSource.UNKNOWN))
        .thenReturn(Completable.complete());

    new IrcConnectionLifecyclePortAdapter(irc)
        .disconnect("libera", "bye", null)
        .test()
        .assertComplete();

    verify(sourceAware).disconnect("libera", "bye", DisconnectRequestSource.UNKNOWN);
    verify(irc, never()).disconnect("libera", "bye");
  }

  @Test
  void legacyClientFallsBackToDisconnectWithReason() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.disconnect("libera", "bye")).thenReturn(Completable.complete());

    new IrcConnectionLifecyclePortAdapter(irc)
        .disconnect("libera", "bye", DisconnectRequestSource.RECONNECT)
        .test()
        .assertComplete();

    verify(irc).disconnect("libera", "bye");
  }

  @Test
  void nativeLifecycleClientReceivesNullSourceUnchanged() {
    IrcClientService irc = mock(LifecycleClient.class);
    IrcConnectionLifecyclePort lifecycle = (IrcConnectionLifecyclePort) irc;
    when(lifecycle.disconnect("libera", "bye", null)).thenReturn(Completable.complete());

    new IrcConnectionLifecyclePortAdapter(irc)
        .disconnect("libera", "bye", null)
        .test()
        .assertComplete();

    verify(lifecycle).disconnect("libera", "bye", null);
    verify(irc, never()).disconnect("libera", "bye");
  }

  @Test
  void preservesLazyDisconnectFailure() {
    IrcClientService irc = mock(IrcClientService.class);
    IllegalStateException failure = new IllegalStateException("disconnected");
    when(irc.disconnect("libera", "bye")).thenReturn(Completable.error(failure));

    new IrcConnectionLifecyclePortAdapter(irc)
        .disconnect("libera", "bye", DisconnectRequestSource.RECONNECT)
        .test()
        .assertError(failure);
  }

  // Resolve the overlapping interface defaults exactly as a real combined client must.
  private interface LifecycleClient
      extends IrcClientService, IrcConnectionLifecyclePort, IrcDisconnectWithSourcePort {
    @Override
    Completable connect(String serverId);

    @Override
    Completable disconnect(String serverId);

    @Override
    Completable disconnect(String serverId, String reason);

    @Override
    Completable disconnect(String serverId, String reason, DisconnectRequestSource source);

    @Override
    java.util.Optional<String> currentNick(String serverId);
  }
}
