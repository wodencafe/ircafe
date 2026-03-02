package cafe.woden.ircclient.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateNotifierServiceTest {

  @Test
  void compareVersionsTreatsNewerReleaseAsGreater() {
    assertTrue(UpdateNotifierService.compareVersionsForTest("v1.2.4", "1.2.3") > 0);
  }

  @Test
  void compareVersionsTreatsReleaseAsNewerThanSnapshotAtSameBase() {
    assertTrue(
        UpdateNotifierService.compareVersionsForTest("1.2.3", "1.2.3-SNAPSHOT+master.12") > 0);
  }

  @Test
  void compareVersionsHandlesPrefixedAndSuffixVersions() {
    assertEquals(0, UpdateNotifierService.compareVersionsForTest("v2.0.0", "2.0.0+local"));
  }

  @Test
  void compareVersionsReturnsZeroWhenParsingFails() {
    assertEquals(0, UpdateNotifierService.compareVersionsForTest("latest", "dev-build"));
  }
}
