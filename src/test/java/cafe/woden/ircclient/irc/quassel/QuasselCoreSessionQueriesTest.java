package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSessionQueriesTest {
  private final Map<String, QuasselCoreSession> sessions = new HashMap<>();
  private final Map<String, String> reasons = new HashMap<>();
  private final List<String> lookups = new ArrayList<>();
  private final List<IrcEvent> events = new ArrayList<>();
  private final QuasselCoreSession session = session("alice");
  private final QuasselCoreSessionQueries queries =
      new QuasselCoreSessionQueries(
          serverId -> {
            lookups.add("session:" + serverId);
            return sessions.get(serverId);
          },
          serverId -> {
            lookups.add("reason:" + serverId);
            return reasons.get(serverId);
          });

  @ParameterizedTest
  @CsvSource({
    "TRANSPORT_CONNECTING, remembered",
    "TRANSPORT_CONNECTED, 'Quassel transport connected, but protocol negotiation is incomplete'",
    "PROTOCOL_NEGOTIATED, 'Quassel protocol negotiated, but login/session handshake is not complete'",
    "AUTHENTICATING, 'Quassel protocol negotiated, but login/session handshake is not complete'",
    "SESSION_ESTABLISHED, ''"
  })
  void availabilityReasonPreservesPhasePrecedenceIndependentlyOfSocketPresence(
      QuasselSessionPhase phase, String expected) {
    sessions.put("core", session);
    reasons.put("core", " remembered ");
    session.phase.set(phase);
    assertEquals(expected, queries.availabilityReason(" core "));
    session.socketRef.set(null);
    assertEquals(expected, queries.availabilityReason("core"));
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "TRANSPORT_CONNECTING, true, false",
    "TRANSPORT_CONNECTED, true, false",
    "PROTOCOL_NEGOTIATED, true, false",
    "AUTHENTICATING, true, false",
    "SESSION_ESTABLISHED, true, true",
    "TRANSPORT_CONNECTING, false, false",
    "TRANSPORT_CONNECTED, false, false",
    "PROTOCOL_NEGOTIATED, false, false",
    "AUTHENTICATING, false, false",
    "SESSION_ESTABLISHED, false, false"
  })
  void establishedSessionQueriesRequireBothPhaseAndSocket(
      QuasselSessionPhase phase, boolean socketPresent, boolean established) {
    sessions.put("core", session);
    session.phase.set(phase);
    if (!socketPresent) session.socketRef.set(null);
    assertEquals(established, queries.hasEstablished(" core "));
    assertEquals(established, queries.isEstablished(" core "));
    if (established) assertSame(session, queries.findEstablished(" core "));
    else assertNull(queries.findEstablished(" core "));
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void absentOrBlankRememberedReasonUsesTheBackendDefault(String remembered) {
    reasons.put("core", remembered);
    assertEquals("Quassel Core backend is not connected", queries.availabilityReason(" core "));
    sessions.put("core", session);
    assertEquals("Quassel Core backend is not connected", queries.availabilityReason("core"));
  }

  @Test
  void rememberedAvailabilityReadsCurrentValuesAndTrimsThemWithoutChangingTheMap() {
    reasons.put("core", " initial reason ");
    assertEquals("initial reason", queries.availabilityReason("core"));
    assertEquals(" initial reason ", reasons.get("core"));
    reasons.put("core", " new reason ");
    assertEquals("new reason", queries.availabilityReason("core"));
    reasons.remove("core");
    assertEquals("Quassel Core backend is not connected", queries.availabilityReason("core"));
    assertTrue(reasons.isEmpty());
  }

  @Test
  void missingSessionProducesEmptyNickAndNoEstablishedSession() {
    assertEquals(Optional.empty(), queries.currentNick("core"));
    assertNull(queries.findEstablished("core"));
    assertFalse(queries.isEstablished("core"));
    assertFalse(queries.hasEstablished("core"));
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "TRANSPORT_CONNECTING",
    "TRANSPORT_CONNECTED",
    "PROTOCOL_NEGOTIATED",
    "AUTHENTICATING",
    "SESSION_ESTABLISHED"
  })
  void currentNickUsesTheSocketGateWithoutRequiringAnEstablishedPhase(QuasselSessionPhase phase) {
    sessions.put("core", session);
    session.phase.set(phase);
    assertEquals(Optional.of("alice"), queries.currentNick(" core "));
    session.socketRef.set(null);
    assertEquals(Optional.empty(), queries.currentNick("core"));
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void emptyNickIsNotExposedAsAnOptionalValue(String nick) {
    sessions.put("core", session(nick));
    assertEquals(Optional.empty(), queries.currentNick("core"));
  }

  @Test
  void nickQueriesFollowPrimaryNetworkObservationsWithoutPublishingEvents() {
    sessions.put("core", session);
    session.authResult.set(
        new QuasselCoreAuthHandshake.AuthResult("core", 7, List.of(7, 8), Map.of()));
    session.networks.observe(7, "Home");
    session.networks.observe(8, "Work");
    session.nicks.observe(8, "secondary", Instant.ofEpochSecond(10));
    assertEquals(Optional.of("alice"), queries.currentNick("core"));
    session.nicks.observe(7, "renamed", Instant.ofEpochSecond(10));
    events.clear();
    assertEquals(Optional.of("renamed"), queries.currentNick("core"));
    assertEquals(Optional.of("renamed"), queries.currentNick("core"));
    assertTrue(events.isEmpty());
  }

  @Test
  void sessionReplacementAndRemovalAreVisibleImmediatelyWithoutRememberedSnapshots() {
    sessions.put("core", session);
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    assertSame(session, queries.findEstablished("core"));
    assertEquals("", queries.availabilityReason("core"));
    var replacement = session("replacement");
    sessions.put("core", replacement);
    reasons.put("core", "reconnecting");
    assertNull(queries.findEstablished("core"));
    assertEquals(Optional.of("replacement"), queries.currentNick("core"));
    assertEquals("reconnecting", queries.availabilityReason("core"));
    replacement.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    assertSame(replacement, queries.findEstablished("core"));
    sessions.remove("core");
    assertNull(queries.findEstablished("core"));
    assertEquals(Optional.empty(), queries.currentNick("core"));
    assertEquals("reconnecting", queries.availabilityReason("core"));
  }

  @Test
  void queriesKeepDifferentServersIsolated() {
    sessions.put("one", session);
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    var other = session("other");
    sessions.put("two", other);
    reasons.put("two", "waiting");
    assertSame(session, queries.findEstablished("one"));
    assertNull(queries.findEstablished("two"));
    assertEquals(Optional.of("alice"), queries.currentNick("one"));
    assertEquals(Optional.of("other"), queries.currentNick("two"));
    assertEquals("", queries.availabilityReason("one"));
    assertEquals("waiting", queries.availabilityReason("two"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void explicitEstablishedControlQueryRejectsBlankIdsBeforeLookingUpEvenAnEmptyKey(
      String serverId) {
    sessions.put("", session);
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    assertFalse(queries.hasEstablished(serverId));
    assertTrue(lookups.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void otherQueriesPreserveTheirNormalizedEmptyKeyLookup(String serverId) {
    sessions.put("", session);
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    assertSame(session, queries.findEstablished(serverId));
    assertTrue(queries.isEstablished(serverId));
    assertEquals(Optional.of("alice"), queries.currentNick(serverId));
    assertEquals("", queries.availabilityReason(serverId));
    assertEquals(List.of("session:", "session:", "session:", "session:"), lookups);
  }

  private QuasselCoreSession session(String nick) {
    var session =
        new QuasselCoreSession(
            "core",
            nick,
            "core.example",
            4242,
            (socket, operation) -> fail("Queries must not write to the transport"),
            ignored -> fail("Queries must not publish identities"),
            events::add);
    session.socketRef.set(new Socket());
    return session;
  }
}
