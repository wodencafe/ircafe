package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TargetRefUiOnlyTest {

  @Test
  void logViewerTargetIsUiOnly() {
    TargetRef ref = TargetRef.logViewer("srv");
    assertTrue(ref.isLogViewer());
    assertTrue(ref.isUiOnly());
    assertFalse(ref.isStatus());
    assertFalse(ref.isChannel());
    assertEquals(ref, new TargetRef("srv", TargetRef.LOG_VIEWER_TARGET));
  }
}
