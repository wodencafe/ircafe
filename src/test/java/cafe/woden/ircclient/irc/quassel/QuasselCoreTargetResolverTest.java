package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.parseQualifiedTarget;
import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreTargetResolverTest {
  private final List<String> trace = new ArrayList<>();
  private boolean observeMissingNetwork = true;
  private RuntimeException observationFailure;
  private final QuasselCoreSession session = session("core");
  private final QuasselCoreTargetResolver resolver =
      new QuasselCoreTargetResolver(
          new QuasselCoreTargetResolver.SessionPort() {
            @Override
            public void observeNetwork(QuasselCoreSession observed, int networkId) {
              assertSame(session, observed);
              trace.add("observe:" + networkId);
              if (observationFailure != null) throw observationFailure;
              if (observeMissingNetwork) observed.networks.observe(networkId, "");
            }

            @Override
            public boolean isSelfNick(QuasselCoreSession observed, String nick, int networkId) {
              assertSame(session, observed);
              trace.add("self:" + networkId + ":" + nick);
              return nick.equalsIgnoreCase("me");
            }
          });

  private static QuasselCoreSession session(String serverId) {
    return new QuasselCoreSession(
        serverId,
        "me",
        "host",
        4242,
        (socket, write) -> fail("Resolution must not write"),
        sid -> {},
        event -> {});
  }

  private void twoNetworks() {
    session.authResult.set(
        new QuasselCoreAuthHandshake.AuthResult("core", 7, List.of(7, 8), Map.of()));
    session.networks.observe(7, "Home");
    session.networks.observe(8, "Work");
  }

  private static BufferInfoValue buffer(int id, int network, int type, String name) {
    return new BufferInfoValue(id, network, type, -1, name);
  }

  @ParameterizedTest
  @CsvSource({"#room, 8", "#ROOM{net:HOME}, 7", "#room{net:WORK}, 8", "#room{net:unknown}, 8"})
  void explicitNetworkWinsOverObservedHintWhileUnknownTokenKeepsHintFallback(
      String target, int expectedNetwork) {
    twoNetworks();
    var home = buffer(11, 7, 2, "#room");
    var work = buffer(22, 8, 2, "#room");
    session.buffers.merge(home);
    session.buffers.merge(work);
    session.targetNetworkHints.observe("#room", 8);
    var expected = expectedNetwork == 7 ? home : work;
    assertSame(expected, resolver.outboundBuffer(session, 2, parseQualifiedTarget(target)));
    assertSame(
        expected, resolver.historyBuffer(session, "core", "history", parseQualifiedTarget(target)));
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#room"));
    assertTrue(trace.isEmpty(), "Buffer lookup must not emit observations");
  }

  @ParameterizedTest
  @CsvSource({"#new, 7", "#new{net:work}, 8", "#new{net:unknown}, 7", "alice{net:work}, 8"})
  void unknownOutboundTargetUsesSyntheticBufferButHistoryFailsWithContext(
      String target, int expectedNetwork) {
    twoNetworks();
    var parsed = parseQualifiedTarget(target);
    int type = parsed.baseTarget().startsWith("#") ? 2 : 4;
    assertEquals(
        buffer(-1, expectedNetwork, type, parsed.baseTarget()),
        resolver.outboundBuffer(session, type, parsed));
    var error =
        assertThrows(
            BackendNotAvailableException.class,
            () -> resolver.historyBuffer(session, "core", "bounded history", parsed));
    assertEquals("quassel-core", error.backendId());
    assertEquals("core", error.serverId());
    assertEquals("bounded history", error.operation());
    assertEquals("target buffer '" + parsed.baseTarget() + "' is not known yet", error.detail());
    assertTrue(session.buffers.values().isEmpty());
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget(target));
    assertTrue(trace.isEmpty());
  }

  @Test
  void preferredNetworkAnyTypeStillWinsOverOtherNetworkMatchingType() {
    twoNetworks();
    var homeChannel = buffer(11, 7, 2, "#room");
    var workQuery = buffer(22, 8, 4, "#room");
    session.buffers.merge(homeChannel);
    session.buffers.merge(workQuery);
    var target = parseQualifiedTarget("#room{net:work}");
    assertSame(workQuery, resolver.outboundBuffer(session, 2, target));
    assertSame(workQuery, resolver.historyBuffer(session, "core", "history", target));
  }

  @Test
  void missingPreferredBufferRetainsExistingCrossNetworkFallback() {
    twoNetworks();
    var home = buffer(11, 7, 2, "#room");
    session.buffers.merge(home);
    var target = parseQualifiedTarget("#room{net:work}");
    assertSame(home, resolver.outboundBuffer(session, 2, target));
    assertSame(home, resolver.historyBuffer(session, "core", "history", target));
  }

  @Test
  void statusRequestsKeepSyntheticEmptyNameOnPrimaryNetwork() {
    twoNetworks();
    session.buffers.merge(buffer(1, 8, 1, "status"));
    var expected = buffer(-1, 7, 1, "");
    assertEquals(expected, resolver.outboundBuffer(session, 1, null));
    assertEquals(expected, resolver.outboundBuffer(session, 1, parseQualifiedTarget("")));
    assertEquals(buffer(-1, -1, 1, ""), resolver.outboundBuffer(null, 1, null));
  }

  @Test
  void historyRejectsNullTargetBeforeConsultingSession() {
    assertEquals(
        "target is blank",
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.historyBuffer(null, "core", "history", null))
            .getMessage());
  }

  @Test
  void subsequentLookupsUseRenamedNetworksChangedHintsAndRemovedBuffers() {
    twoNetworks();
    var home = buffer(11, 7, 2, "#room");
    var work = buffer(22, 8, 2, "#room");
    session.buffers.merge(home);
    session.buffers.merge(work);
    var plain = parseQualifiedTarget("#room");
    assertSame(home, resolver.outboundBuffer(session, 2, plain));
    session.targetNetworkHints.observe("#room", 8);
    assertSame(work, resolver.outboundBuffer(session, 2, plain));
    session.networks.observe(8, "New Work");
    assertEquals("#room{net:new-work}", resolver.targetForBuffer(session, work, ""));
    session.buffers.remove(22);
    assertSame(home, resolver.outboundBuffer(session, 2, plain));
    session.buffers.remove(11);
    session.networks.forget(8);
    session.targetNetworkHints.forgetNetwork(8);
    assertEquals(buffer(-1, 7, 2, "#room"), resolver.outboundBuffer(session, 2, plain));
    assertEquals("#room", resolver.qualifyTarget(session, "#room", 7));
  }

  @Test
  void sharedResolverDoesNotReuseAnotherSessionsNetworkSelection() {
    twoNetworks();
    var other = session("other");
    other.networks.observe(0, "Zero");
    var target = parseQualifiedTarget("#new");
    assertEquals(7, resolver.outboundBuffer(session, 2, target).networkId());
    assertEquals(0, resolver.outboundBuffer(other, 2, target).networkId());
    assertEquals(-1, resolver.outboundBuffer(session("empty"), 2, target).networkId());
    assertTrue(trace.isEmpty());
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "1|anything|sender|status",
        "2|#room|sender|#room{net:work}",
        "4|alice|sender|alice{net:work}",
        "4| |sender|sender{net:work}",
        "2| |sender|",
        "0|untyped|sender|untyped",
        "3|#room|sender|#room{net:work}",
        "5| |sender|sender{net:work}"
      },
      delimiter = '|',
      nullValues = "",
      emptyValue = "")
  void bufferTargetsPreserveTypePriorityQueryFallbackAndStatusCanonicalization(
      int type, String name, String sender, String expected) {
    twoNetworks();
    assertEquals(
        expected == null ? "" : expected,
        resolver.targetForBuffer(session, buffer(22, 8, type, name), sender));
    assertTrue(trace.isEmpty());
  }

  @Test
  void nullBufferDoesNotUseSenderFallback() {
    assertEquals("", resolver.targetForBuffer(session, null, "sender"));
  }

  @Test
  void qualificationObservesMissingTokenOnceBeforeReturningItsValue() {
    twoNetworks();
    assertEquals("#room{net:network-9}", resolver.qualifyTarget(session, " #room ", 9));
    assertEquals(List.of("observe:9"), trace);
    assertEquals("#room{net:network-9}", resolver.qualifyTarget(session, "#room", 9));
    assertEquals(1, trace.size());
  }

  @Test
  void missingTokenObservationFailurePropagatesWithoutInventingAToken() {
    twoNetworks();
    observationFailure = new IllegalStateException("observer failed");
    assertSame(
        observationFailure,
        assertThrows(
            IllegalStateException.class, () -> resolver.qualifyTarget(session, "#room", 9)));
    assertEquals("", session.networks.token(9));
  }

  @Test
  void absentTokenAfterObservationLeavesTargetUnqualified() {
    twoNetworks();
    observeMissingNetwork = false;
    assertEquals("#room", resolver.qualifyTarget(session, "#room", 9));
    assertEquals(List.of("observe:9"), trace);
  }

  @Test
  void blankNegativeNetworkAndSingleNetworkTargetsDoNotObserveMissingTokens() {
    assertEquals("", resolver.qualifyTarget(session, null, 9));
    assertEquals("#room", resolver.qualifyTarget(null, " #room ", 9));
    assertEquals("#room", resolver.qualifyTarget(session, "#room", -1));
    session.networks.observe(0, "Zero");
    assertEquals("#room", resolver.qualifyTarget(session, "#room", 9));
    assertTrue(trace.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", ":", ": "})
  void blankSignalTargetsUseTrimmedFallbackWithoutSelfNickLookup(String raw) {
    assertEquals("fallback", resolver.signalTarget(session, "sender", " fallback ", 8, raw));
    assertTrue(trace.isEmpty());
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "me|alice|alice{net:work}|me",
        ":ME| bob |bob{net:work}|ME",
        "me| |me{net:work}|me",
        "#room|alice|#room{net:work}|#room",
        "me{net:HOME}|alice|me{net:HOME}|me",
        "#room{net:unknown}|alice|#room{net:unknown}|#room"
      },
      delimiter = '|')
  void signalTargetsMapSelfToSenderButPreserveExplicitQualifiedTarget(
      String raw, String sender, String expected, String lookupNick) {
    twoNetworks();
    assertEquals(expected, resolver.signalTarget(session, sender, "fallback", 8, raw));
    assertEquals(List.of("self:8:" + lookupNick), trace);
  }
}
