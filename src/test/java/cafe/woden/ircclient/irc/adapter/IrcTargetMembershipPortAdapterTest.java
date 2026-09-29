package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import io.reactivex.rxjava3.core.Completable;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IrcTargetMembershipPortAdapterTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void adapterDelegatesTargetMembershipOperations(boolean nativePort) {
    IrcClientService irc = nativePort ? mock(NativeClient.class) : mock(IrcClientService.class);
    when(irc.joinChannel("libera", "#ircafe")).thenReturn(Completable.complete());
    when(irc.partChannel("libera", "#ircafe")).thenReturn(Completable.complete());
    when(irc.partChannel("libera", "#ircafe", "bye")).thenReturn(Completable.complete());
    when(irc.requestNames("libera", "#ircafe")).thenReturn(Completable.complete());
    when(irc.sendRaw("libera", "DETACH #ircafe")).thenReturn(Completable.complete());
    when(irc.currentNick("libera")).thenReturn(Optional.of("alice"));

    IrcTargetMembershipPort port = new IrcTargetMembershipPortAdapter(irc);

    port.joinChannel("libera", "#ircafe").blockingAwait();
    port.partChannel("libera", "#ircafe").blockingAwait();
    port.partChannel("libera", "#ircafe", "bye").blockingAwait();
    port.requestNames("libera", "#ircafe").blockingAwait();
    port.sendRaw("libera", "DETACH #ircafe").blockingAwait();
    assertTrue(port.currentNick("libera").isPresent());
    assertEquals("alice", port.currentNick("libera").orElse(""));
  }

  @Test
  void rejectsMissingClient() {
    assertThrows(NullPointerException.class, () -> new IrcTargetMembershipPortAdapter(null));
  }

  @Test
  void legacyNicknameLookupTrimsServerAndSkipsBlankIds() {
    IrcClientService irc = mock(IrcClientService.class);
    IrcTargetMembershipPort port = new IrcTargetMembershipPortAdapter(irc);
    assertTrue(port.currentNick(null).isEmpty());
    assertTrue(port.currentNick("").isEmpty());
    assertTrue(port.currentNick("  ").isEmpty());
    verifyNoInteractions(irc);
    when(irc.currentNick("libera")).thenReturn(Optional.of("alice"));
    assertEquals(Optional.of("alice"), port.currentNick(" libera "));
    verify(irc).currentNick("libera");
    verifyNoMoreInteractions(irc);
  }

  @Test
  void nativeNicknameLookupPreservesServerId() {
    NativeClient irc = mock(NativeClient.class);
    IrcTargetMembershipPort port = new IrcTargetMembershipPortAdapter(irc);
    for (String serverId : new String[] {null, "", "  ", " libera "}) {
      when(irc.currentNick(serverId)).thenReturn(Optional.of("native"));
      assertEquals(Optional.of("native"), port.currentNick(serverId));
      verify(irc).currentNick(serverId);
    }
    verifyNoMoreInteractions(irc);
  }

  private interface NativeClient extends IrcClientService, IrcTargetMembershipPort {
    @Override
    Completable joinChannel(String serverId, String channel);

    @Override
    Completable partChannel(String serverId, String channel);

    @Override
    Completable partChannel(String serverId, String channel, String reason);

    @Override
    Completable requestNames(String serverId, String channel);

    @Override
    Completable sendRaw(String serverId, String line);

    @Override
    Optional<String> currentNick(String serverId);
  }
}
