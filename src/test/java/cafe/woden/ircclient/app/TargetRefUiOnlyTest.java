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

  @Test
  void interceptorTargetIsUiOnlyAndParsesId() {
    TargetRef ref = TargetRef.interceptor("srv", "abc-123");
    assertTrue(ref.isInterceptor());
    assertTrue(ref.isUiOnly());
    assertFalse(ref.isStatus());
    assertFalse(ref.isChannel());
    assertEquals("abc-123", ref.interceptorId());
    assertEquals(ref, new TargetRef("srv", TargetRef.INTERCEPTOR_PREFIX + "ABC-123"));
  }
}
