package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreNickStateTest {
  private static final Instant AT = Instant.parse("2026-10-10T12:00:00Z");
  private final AtomicInteger primary = new AtomicInteger(1);
  private final List<IrcEvent> events = new ArrayList<>();
  private final QuasselCoreNickState nicks =
      new QuasselCoreNickState("alice", primary::get, events::add);

  @Test
  void secondaryObservationKeepsPrimaryFallbackAndDoesNotPublishGlobalChange() {
    nicks.observe(2, " workNick ", AT);
    assertEquals("workNick", nicks.forNetwork(2));
    assertEquals("alice", nicks.current());
    assertEquals("alice", nicks.forNetwork(99));
    assertEquals("alice", nicks.forNetwork(-1));
    assertEquals("alice", nicks.lastObservedNick());
    assertTrue(events.isEmpty());
  }

  @Test
  void primarySelectionUsesLiveNetworkCatalogWithoutOverwritingFallback() {
    nicks.observe(1, "homeNick", AT);
    nicks.observe(2, "workNick", AT);
    primary.set(2);
    assertEquals("workNick", nicks.current());
    assertEquals("workNick", nicks.forNetwork(99));
    assertEquals("homeNick", nicks.forNetwork(1));
    assertEquals("homeNick", nicks.lastObservedNick());
    primary.set(-1);
    assertEquals("homeNick", nicks.current());
    assertEquals(List.of(new IrcEvent.NickChanged(AT, "alice", "homeNick")), events);
  }

  @ParameterizedTest
  @CsvSource({"1,1", "-1,2", "1,-1", "0,0"})
  void globalObservationPublishesOriginalTimestampAfterStateHasChanged(int preferred, int network) {
    primary.set(preferred);
    var ref = new AtomicReference<QuasselCoreNickState>();
    var state =
        new QuasselCoreNickState(
            " alice ",
            primary::get,
            event -> {
              assertEquals("next", ref.get().current());
              assertEquals("next", ref.get().forNetwork(network));
              assertEquals("next", ref.get().lastObservedNick());
              events.add(event);
            });
    ref.set(state);
    state.observe(network, " next ", AT);
    assertEquals(List.of(new IrcEvent.NickChanged(AT, "alice", "next")), events);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankObservationsAndSelfChecksDoNotConsultNetworkCatalog(String blank) {
    var state = new QuasselCoreNickState("alice", () -> fail("blank nick"), events::add);
    state.observe(1, blank, AT);
    assertFalse(state.isSelf(blank, 1));
    assertEquals("alice", state.lastObservedNick());
    assertTrue(events.isEmpty());
  }

  @Test
  void caseOnlyAndRepeatedObservationsUpdateSpellingWithoutGlobalEvents() {
    nicks.observe(1, "ALICE", AT);
    nicks.observe(1, "ALICE", AT);
    assertEquals("ALICE", nicks.current());
    assertEquals("ALICE", nicks.lastObservedNick());
    assertTrue(events.isEmpty());
    nicks.observe(1, "next", AT);
    nicks.observe(1, "NEXT", AT.plusSeconds(1));
    assertEquals("NEXT", nicks.current());
    assertEquals(List.of(new IrcEvent.NickChanged(AT, "ALICE", "next")), events);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void firstNickObservationWithEmptyFallbackDoesNotPublishAChange(String initial) {
    var state = new QuasselCoreNickState(initial, primary::get, events::add);
    assertEquals("", state.current());
    state.observe(1, "first", AT);
    assertEquals("first", state.current());
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "workNick,2,true",
    "WORKNICK,2,true",
    "alice,2,true",
    "ALICE,99,true",
    "alice,-1,true",
    "workNick,1,false",
    "other,2,false"
  })
  void selfDetectionAcceptsNetworkNickOrPrimaryFallback(String nick, int network, boolean self) {
    nicks.observe(2, "workNick", AT);
    assertEquals(self, nicks.isSelf(" " + nick + " ", network));
  }

  @Test
  void primarySeedRestoresConfiguredNickWithoutChangingFallbackOrPublishing() {
    nicks.observe(-1, "fallback", AT);
    events.clear();
    nicks.seedPrimaryNetwork(1);
    assertEquals("alice", nicks.current());
    assertEquals("fallback", nicks.lastObservedNick());
    nicks.seedPrimaryNetwork(-1);
    assertEquals("alice", nicks.current());
    assertTrue(events.isEmpty());
  }

  @Test
  void forgettingAndClearingObservationsKeepFallbackWithoutPublishing() {
    nicks.observe(1, "homeNick", AT);
    nicks.observe(2, "workNick", AT);
    nicks.observe(-1, "fallback", AT);
    events.clear();
    nicks.forgetNetwork(1);
    nicks.forgetNetwork(1);
    assertEquals("fallback", nicks.current());
    assertEquals("workNick", nicks.forNetwork(2));
    nicks.clear();
    nicks.clear();
    assertEquals("fallback", nicks.forNetwork(2));
    assertEquals("fallback", nicks.lastObservedNick());
    assertTrue(events.isEmpty());
  }

  @Test
  void observationsStayBoundedAcrossManyNetworks() {
    primary.set(0);
    for (int id = 1; id <= 1024; id++) nicks.observe(id, "nick" + id, AT);
    int retained = 0;
    for (int id = 1; id <= 1024; id++) {
      String actual = nicks.forNetwork(id);
      if (actual.equals("nick" + id)) retained++;
      else assertEquals("alice", actual);
    }
    assertEquals(256, retained);
    assertTrue(events.isEmpty());
    nicks.clear();
    for (int id = 1; id <= 1024; id++) assertEquals("alice", nicks.forNetwork(id));
  }

  @Test
  void observerFailurePropagatesAfterNickUpdateAndDoesNotRepeatSameChange() {
    var failure = new IllegalStateException("observer failed");
    var state =
        new QuasselCoreNickState(
            "alice",
            primary::get,
            event -> {
              throw failure;
            });
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> state.observe(1, "next", AT)));
    assertEquals("next", state.current());
    assertEquals("next", state.lastObservedNick());
    assertDoesNotThrow(() -> state.observe(1, "next", AT));
  }

  @Test
  void connectedNickRetainsOriginalFallbackSpellingBeforeAnyObservation() {
    var state = new QuasselCoreNickState(" alice ", primary::get, events::add);
    assertEquals(" alice ", state.lastObservedNick());
    assertEquals("alice", state.current());
  }
}
