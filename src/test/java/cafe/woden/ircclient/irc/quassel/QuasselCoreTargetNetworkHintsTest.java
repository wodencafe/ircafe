package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreTargetNetworkHintsTest {
  private final QuasselCoreTargetNetworkHints hints = new QuasselCoreTargetNetworkHints();
  private static final IntSupplier UNEXPECTED_DEFAULT =
      () -> {
        throw new AssertionError("Default network should not be resolved");
      };

  @Test
  void observationsShareCaseInsensitiveBaseNamesAndReplacePreviousRouting() {
    hints.observe(" #Room{net:ONE} ", 1);
    hints.observe("other", 3);
    assertEquals(1, hints.networkIdForTarget("#room"));
    hints.observe("#ROOM{net:two}", 2);
    assertEquals(2, hints.networkIdForTarget("#room{net:one}"));
    assertEquals(3, hints.networkIdForTarget("OTHER"));
    assertEquals(-1, hints.networkIdForTarget("unknown"));
  }

  @ParameterizedTest
  @CsvSource({"2,1,1", "2,0,0", "2,-1,2", "0,-1,0"})
  void snapshotSeedsPreferTheDefaultNetworkWhenItIsKnown(
      int observed, int preferredDefault, int expected) {
    hints.seed("#room", observed, () -> preferredDefault);
    assertEquals(expected, hints.networkIdForTarget("#ROOM"));
  }

  @Test
  void laterSnapshotSeedsCannotOverrideAnObservedHint() {
    hints.seed("#room", 2, () -> 1);
    hints.observe("#room{net:two}", 2);
    hints.seed("#ROOM", 1, () -> 1);
    hints.seed("#room{net:three}", 3, () -> 3);
    assertEquals(2, hints.networkIdForTarget("#room"));
  }

  @Test
  void firstSnapshotSeedIsRetainedUntilAnObservationOverridesIt() {
    hints.seed("#room", 2, () -> 1);
    hints.seed("#ROOM", 3, () -> 3);
    assertEquals(1, hints.networkIdForTarget("#room"));
    hints.observe("#room", 3);
    assertEquals(3, hints.networkIdForTarget("#room"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void blankTargetsDoNotResolveTheDefaultOrConsumeCapacity(String target) {
    var limited = new QuasselCoreTargetNetworkHints(1);
    limited.observe("#room", 2);
    limited.observe(target, 3);
    limited.seed(target, 3, UNEXPECTED_DEFAULT);
    assertEquals(-1, limited.networkIdForTarget(target));
    assertEquals(2, limited.networkIdForTarget("#room"));
  }

  @Test
  void unknownObservedNetworksDoNotOverwriteHintsOrResolveTheDefault() {
    hints.observe("#room", 2);
    hints.observe("#ROOM", -1);
    hints.seed("#room", -1, UNEXPECTED_DEFAULT);
    assertEquals(2, hints.networkIdForTarget("#room"));
  }

  @Test
  void forgettingANetworkRemovesAllItsHintsAndKeepsReassignedTargets() {
    hints.observe("#one", 1_000);
    hints.observe("#two", 1_000);
    hints.observe("#other", 2_000);
    hints.observe("#two", 2_000);
    hints.forgetNetwork(1_000);
    hints.forgetNetwork(1_000);
    assertEquals(-1, hints.networkIdForTarget("#one"));
    assertEquals(2_000, hints.networkIdForTarget("#two"));
    assertEquals(2_000, hints.networkIdForTarget("#other"));
    hints.seed("#one", 3, () -> 3);
    assertEquals(3, hints.networkIdForTarget("#one"));
  }

  @Test
  void clearingTheSessionAllowsFreshDefaultSeeds() {
    hints.observe("#one", 1);
    hints.observe("#two", 2);
    hints.clear();
    hints.clear();
    assertEquals(-1, hints.networkIdForTarget("#one"));
    assertEquals(-1, hints.networkIdForTarget("#two"));
    hints.seed("#one", 2, () -> 1);
    assertEquals(1, hints.networkIdForTarget("#one"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void bothObservationAndSnapshotInsertionRemainBounded(boolean observed) {
    var limited = new QuasselCoreTargetNetworkHints(3);
    for (int id = 0; id < 100; id++) {
      if (observed) limited.observe("#room" + id, id);
      else limited.seed("#room" + id, id, () -> -1);
    }
    int retained = 0;
    for (int id = 0; id < 100; id++) {
      int networkId = limited.networkIdForTarget("#room" + id);
      if (networkId >= 0) {
        assertEquals(id, networkId);
        retained++;
      }
    }
    assertEquals(3, retained);
  }

  @Test
  void defaultCapacityRemains4096Targets() {
    for (int id = 0; id < 5_000; id++) hints.observe("#room" + id, id);
    int retained = 0;
    for (int id = 0; id < 5_000; id++) {
      if (hints.networkIdForTarget("#room" + id) >= 0) retained++;
    }
    assertEquals(4_096, retained);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void capacityMustBePositive(int maxSize) {
    assertThrows(IllegalArgumentException.class, () -> new QuasselCoreTargetNetworkHints(maxSize));
  }
}
