package cafe.woden.ircclient.irc.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import cafe.woden.ircclient.irc.DisconnectRequestSource;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcDisconnectWithSourcePort;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Test;

class IrcConnectionLifecyclePortTest {

  @Test
  void fromIrcClientDelegatesLifecycleOperations() {
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.connect("libera")).thenReturn(Completable.complete());
    when(irc.disconnect("libera")).thenReturn(Completable.complete());
    when(irc.disconnect("libera", "bye")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(java.util.Optional.of("alice"));

    IrcConnectionLifecyclePort port = IrcConnectionLifecyclePort.from(irc);

    port.connect("libera").blockingAwait();
    port.disconnect("libera").blockingAwait();
    port.disconnect("libera", "bye").blockingAwait();
    assertTrue(port.currentNick("libera").isPresent());
    assertEquals("alice", port.currentNick("libera").orElse(""));
  }

  @Test
  void fromIrcClientDelegatesSourceAwareDisconnectWhenAvailable() {
    IrcClientService irc =
        mock(
            IrcClientService.class,
            withSettings().extraInterfaces(IrcDisconnectWithSourcePort.class));
    IrcDisconnectWithSourcePort sourceAware = (IrcDisconnectWithSourcePort) irc;
    when(sourceAware.disconnect("libera", "bye", DisconnectRequestSource.RECONNECT))
        .thenReturn(Completable.complete());

    IrcConnectionLifecyclePort port = IrcConnectionLifecyclePort.from(irc);

    port.disconnect("libera", "bye", DisconnectRequestSource.RECONNECT).blockingAwait();

    verify(sourceAware).disconnect("libera", "bye", DisconnectRequestSource.RECONNECT);
  }
}
