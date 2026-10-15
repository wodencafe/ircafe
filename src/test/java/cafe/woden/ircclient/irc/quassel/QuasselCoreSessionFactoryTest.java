package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import io.reactivex.rxjava3.disposables.Disposable;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuasselCoreSessionFactoryTest {
  private final QuasselCoreSignalProxySender sender = mock(QuasselCoreSignalProxySender.class);
  private final List<String> identities = new ArrayList<>();
  private final List<ServerIrcEvent> events = new ArrayList<>();
  private final QuasselCoreSessionFactory factory =
      new QuasselCoreSessionFactory(sender, identities::add, events::add);

  @ParameterizedTest
  @CsvSource({", quassel-user", "'', quassel-user", "'   ', quassel-user", "' Alice ', Alice"})
  void configuredMetadataAndNickDefaultsAreAppliedWithoutOpeningOrPublishing(
      String configuredNick, String expectedNick) {
    var session = factory.create("runtime", server(configuredNick));
    assertEquals("runtime", session.serverId);
    assertEquals(expectedNick, session.initialNick);
    assertEquals(expectedNick, session.nicks.current());
    assertEquals("core.example", session.connectedHost);
    assertEquals(4242, session.connectedPort);
    assertEquals(QuasselSessionPhase.TRANSPORT_CONNECTING, session.phase.get());
    assertNull(session.socketRef.get());
    assertNull(session.readLoopTask.get());
    assertNull(session.readiness.fallbackTask.get());
    assertTrue(identities.isEmpty());
    assertTrue(events.isEmpty());
    verifyNoInteractions(sender);
  }

  @Test
  void callbacksKeepTheOriginatingSessionIdWhenTheFactoryIsSharedAcrossServers() {
    var configured = server("alice");
    var first = factory.create("first", configured);
    var second = factory.create("second", configured);
    Instant at = Instant.ofEpochSecond(1_700_000_000L);
    first.identities.observe(7, "First identity");
    second.identities.observe(9, "Second identity");
    first.identities.observe(8, "Another first identity");
    second.nicks.observe(-1, "SecondNick", at);
    first.nicks.observe(-1, "FirstNick", at);
    assertEquals(List.of("first", "second", "first"), identities);
    assertEquals(
        List.of(
            new ServerIrcEvent("second", new IrcEvent.NickChanged(at, "alice", "SecondNick")),
            new ServerIrcEvent("first", new IrcEvent.NickChanged(at, "alice", "FirstNick"))),
        events);
    assertFalse(first.identities.isKnown(9));
    assertFalse(second.identities.isKnown(7));
    verifyNoInteractions(sender);
  }

  @Test
  void replacementDoesNotReuseSessionStateOrTaskHandlesAndKeepsItsOwnWriteBinding()
      throws Exception {
    var configured = server("alice");
    var first = factory.create("core", configured);
    Socket oldSocket = mock(Socket.class);
    first.socketRef.set(oldSocket);
    first.buffers.merge(new QuasselCoreDatastreamCodec.BufferInfoValue(11, 7, 2, -1, "#room"));
    first.history.observe("#room", 101, Instant.ofEpochSecond(10));
    first.readLoopTask.set(Disposable.empty());
    first.closeRequested.set(true);

    var replacement = factory.create("core", configured);
    assertNotSame(first, replacement);
    assertTrue(replacement.buffers.values().isEmpty());
    assertEquals(-1, replacement.history.timestampForMsgId("#room", 101));
    assertNull(replacement.socketRef.get());
    assertNull(replacement.readLoopTask.get());
    assertFalse(replacement.closeRequested.get());
    assertNotSame(first.outbound, replacement.outbound);
    Socket newSocket = mock(Socket.class);
    replacement.socketRef.set(newSocket);
    replacement.networkCommands.syncNetwork(9, "requestConnect");
    verify(sender).send(same(newSocket), any());
    assertSame(oldSocket, first.socketRef.get());
    assertNotNull(first.buffers.get(11));
    verifyNoMoreInteractions(sender);
    verifyNoInteractions(oldSocket, newSocket);
    assertTrue(events.isEmpty());
    assertTrue(identities.isEmpty());
  }

  private static IrcProperties.Server server(String nick) {
    return IrcPropertiesTestFixtures.serverBuilder("configured")
        .host(" core.example ")
        .port(4242)
        .nick(nick)
        .build();
  }
}
