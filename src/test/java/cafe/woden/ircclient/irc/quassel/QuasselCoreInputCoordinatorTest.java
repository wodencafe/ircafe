package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreInputCoordinatorTest {
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private final List<String> trace = new ArrayList<>();
  private IOException writeFailure;
  private RuntimeException observationFailure;
  private final QuasselCoreSession session = session("core");
  private final QuasselCoreTargetResolver targets =
      new QuasselCoreTargetResolver(
          new QuasselCoreTargetResolver.SessionPort() {
            @Override
            public void observeNetwork(QuasselCoreSession observed, int networkId) {
              fail("Outbound buffer lookup must not observe a network");
            }

            @Override
            public boolean isSelfNick(QuasselCoreSession observed, String nick, int networkId) {
              fail("Outbound routing must not inspect the sender nick");
              return false;
            }
          });
  private final QuasselCoreInputCoordinator inputs =
      new QuasselCoreInputCoordinator(
          targets,
          (observed, target, networkId) -> {
            trace.add("hint:" + observed.serverId + ":" + target + ":" + networkId);
            if (observationFailure != null) throw observationFailure;
            observed.targetNetworkHints.observe(target, networkId);
          });

  @ParameterizedTest
  @CsvSource({
    "#room{net:HOME}, 2, 11, 7, #room",
    "#room{net:WORK}, 2, 22, 8, #room",
    "alice{net:work}, 4, 33, 8, alice",
    "#new{net:work}, 2, -1, 8, #new",
    "bob{net:work}, 4, -1, 8, bob",
    "'', 1, -1, 7, ''"
  })
  void normalInputRoutesAndObservesBeforeWritingWithoutChangingText(
      String target, int type, int id, int network, String base) throws Exception {
    twoNetworks();
    String text = "  message with  spacing  ";
    inputs.sendInput(session, "send message", type, target, text);
    assertWrite(buffer(id, network, type, base), text);
    assertEquals(List.of("hint:core:" + base + ":" + network, "write:core"), trace);
  }

  @ParameterizedTest
  @CsvSource({
    "PRIVMSG, #room{net:work}, 22, 2, #room",
    "notice, #room{net:work}, 22, 2, #room",
    "TAGMSG, #room{net:work}, 22, 2, #room",
    "MARKREAD, #room{net:work}, 22, 2, #room",
    "REDACT, #room{net:work}, 22, 2, #room",
    "privmsg, alice{net:work}, 33, 4, alice",
    "NOTICE, alice{net:work}, 33, 4, alice",
    "tagmsg, alice{net:work}, 33, 4, alice",
    "markread, alice{net:work}, 33, 4, alice",
    "redact, alice{net:work}, 33, 4, alice"
  })
  void rawRoutingRemovesOnlyTheNetworkQualifierAndPreservesTheEnvelope(
      String command, String target, int id, int type, String base) throws Exception {
    twoNetworks();
    String prefix = "@+typing=active;label=msg42 :me!user@host " + command + "   ";
    String suffix = " :text with  two spaces";
    inputs.sendRaw(session, "send raw", prefix + target + suffix);
    assertWrite(buffer(id, 8, type, base), "/QUOTE " + prefix + base + suffix);
    assertEquals(List.of("hint:core:" + base + ":8", "write:core"), trace);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "WHOIS alice",
        "CAP LS 302",
        "JOIN #room{net:work}",
        "PRIVMSG",
        "PRIVMSG :trailing",
        "@label=one",
        ":server"
      })
  void commandsWithoutARoutedTargetUseStatusWithoutObservingATarget(String raw) throws Exception {
    twoNetworks();
    inputs.sendRaw(session, "send raw", raw);
    assertWrite(buffer(-1, 7, 1, ""), "/QUOTE " + raw);
    assertEquals(List.of("write:core"), trace);
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget("#room"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingNetworkFailsBeforeObservationsAndWritesWithOperationContext(boolean raw) {
    BackendNotAvailableException failure =
        assertThrows(BackendNotAvailableException.class, () -> send(raw, session));
    assertEquals("quassel-core", failure.backendId());
    assertEquals("core", failure.serverId());
    assertEquals("send test input", failure.operation());
    assertEquals("no active Quassel network is available yet", failure.detail());
    assertTrue(trace.isEmpty());
    verifyNoInteractions(codec);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingSessionFailsWithoutObservationsOrWrites(boolean raw) {
    assertEquals(
        "Quassel session is missing",
        assertThrows(IllegalStateException.class, () -> send(raw, null)).getMessage());
    assertTrue(trace.isEmpty());
    verifyNoInteractions(codec);
  }

  @ParameterizedTest
  @ValueSource(strings = {"auth", "buffer", "metadata"})
  void discoversAnActiveNetworkFromEachLiveCatalogIncludingNetworkZero(String source)
      throws Exception {
    switch (source) {
      case "auth" ->
          session.authResult.set(
              new QuasselCoreAuthHandshake.AuthResult("core", 0, List.of(0), Map.of()));
      case "buffer" -> session.buffers.merge(buffer(1, 0, 1, "status"));
      case "metadata" -> session.networks.observe(0, "Zero");
      default -> fail("Unknown fixture source");
    }
    inputs.sendRaw(session, "send raw", "WHOIS alice");
    assertWrite(buffer(-1, 0, 1, ""), "/QUOTE WHOIS alice");
    assertEquals(List.of("write:core"), trace);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void closedSocketFailureOccursAfterTheRoutingHint(boolean raw) {
    twoNetworks();
    session.socketRef.set(null);
    assertEquals(
        "Quassel socket is closed",
        assertThrows(IllegalStateException.class, () -> send(raw, session)).getMessage());
    assertEquals(List.of("hint:core:#room:8"), trace);
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#room"));
    verifyNoInteractions(codec);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void transportFailurePropagatesAndKeepsTheObservedRoutingHint(boolean raw) {
    twoNetworks();
    writeFailure = new IOException("write failed");
    assertSame(writeFailure, assertThrows(IOException.class, () -> send(raw, session)));
    assertEquals(List.of("hint:core:#room:8", "write:core"), trace);
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#room"));
    verifyNoInteractions(codec);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void observationFailurePreventsTheWrite(boolean raw) {
    twoNetworks();
    observationFailure = new IllegalStateException("observer failed");
    assertSame(
        observationFailure, assertThrows(IllegalStateException.class, () -> send(raw, session)));
    assertEquals(List.of("hint:core:#room:8"), trace);
    verifyNoInteractions(codec);
  }

  @Test
  void routedSendUpdatesTheNetworkChoiceForSubsequentUnqualifiedInput() throws Exception {
    twoNetworks();
    inputs.sendRaw(session, "send raw", "PRIVMSG #room{net:work} :first");
    inputs.sendInput(session, "send message", 2, "#room", "second");
    assertWrite(buffer(22, 8, 2, "#room"), "/QUOTE PRIVMSG #room :first");
    assertWrite(buffer(22, 8, 2, "#room"), "second");
    assertEquals(
        List.of("hint:core:#room:8", "write:core", "hint:core:#room:8", "write:core"), trace);
  }

  @Test
  void networkRenameRemovalAndExhaustionAreReadOnEverySend() throws Exception {
    twoNetworks();
    session.networks.observe(8, "New Work");
    inputs.sendRaw(session, "send raw", "PRIVMSG #room{net:new-work} :renamed");
    assertWrite(buffer(22, 8, 2, "#room"), "/QUOTE PRIVMSG #room :renamed");
    session.networks.forget(8);
    session.buffers.forgetNetwork(8, ignored -> {});
    session.targetNetworkHints.forgetNetwork(8);
    inputs.sendInput(session, "send message", 2, "#room", "remaining");
    assertWrite(buffer(11, 7, 2, "#room"), "remaining");
    session.networks.forget(7);
    session.buffers.forgetNetwork(7, ignored -> {});
    session.targetNetworkHints.forgetNetwork(7);
    trace.clear();
    assertThrows(BackendNotAvailableException.class, () -> send(true, session));
    assertTrue(trace.isEmpty());
  }

  @Test
  void oneCoordinatorUsesEachSessionsCurrentSocketAndNetwork() throws Exception {
    twoNetworks();
    QuasselCoreSession other = session("other");
    other.networks.observe(0, "Other");
    inputs.sendInput(session, "send message", 2, "#new{net:work}", "first session");
    inputs.sendInput(other, "send message", 2, "#new", "second session");
    assertWrite(buffer(-1, 8, 2, "#new"), "first session");
    assertWrite(buffer(-1, 0, 2, "#new"), "second session");
    assertEquals(
        List.of("hint:core:#new:8", "write:core", "hint:other:#new:0", "write:other"), trace);
  }

  private void send(boolean raw, QuasselCoreSession observed) throws IOException {
    if (raw) inputs.sendRaw(observed, "send test input", "PRIVMSG #room{net:work} :hello");
    else inputs.sendInput(observed, "send test input", 2, "#room{net:work}", "hello");
  }

  private QuasselCoreSession session(String serverId) {
    Socket socket = new Socket();
    QuasselCoreSession created =
        new QuasselCoreSession(
            serverId,
            "me",
            "host",
            4242,
            (captured, operation) -> {
              assertSame(socket, captured);
              trace.add("write:" + serverId);
              if (writeFailure != null) throw writeFailure;
              operation.write(codec, output);
            },
            sid -> {},
            event -> {});
    created.socketRef.set(socket);
    return created;
  }

  private void twoNetworks() {
    session.authResult.set(
        new QuasselCoreAuthHandshake.AuthResult("core", 7, List.of(7, 8), Map.of()));
    session.networks.observe(7, "Home");
    session.networks.observe(8, "Work");
    session.buffers.merge(buffer(11, 7, 2, "#room"));
    session.buffers.merge(buffer(22, 8, 2, "#room"));
    session.buffers.merge(buffer(33, 8, 4, "alice"));
  }

  private void assertWrite(BufferInfoValue buffer, String input) throws IOException {
    verify(codec)
        .writeSignalProxyRpcCall(output, "2sendInput(BufferInfo,QString)", List.of(buffer, input));
  }

  private static BufferInfoValue buffer(int id, int network, int type, String name) {
    return new BufferInfoValue(id, network, type, -1, name);
  }
}
