package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
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

  @Test
  void monitorGroupTargetIsUiOnly() {
    TargetRef ref = TargetRef.monitorGroup("srv");
    assertTrue(ref.isMonitorGroup());
    assertTrue(ref.isUiOnly());
    assertFalse(ref.isStatus());
    assertFalse(ref.isChannel());
    assertEquals(ref, new TargetRef("srv", TargetRef.MONITOR_GROUP_TARGET));
  }

  @Test
  void weechatFiltersTargetIsUiOnly() {
    TargetRef ref = TargetRef.weechatFilters("srv");
    assertTrue(ref.isWeechatFilters());
    assertTrue(ref.isUiOnly());
    assertFalse(ref.isStatus());
    assertFalse(ref.isChannel());
    assertEquals(ref, new TargetRef("srv", TargetRef.WEECHAT_FILTERS_TARGET));
  }

  @Test
  void ignoresTargetIsUiOnly() {
    TargetRef ref = TargetRef.ignores("srv");
    assertTrue(ref.isIgnores());
    assertTrue(ref.isUiOnly());
    assertFalse(ref.isStatus());
    assertFalse(ref.isChannel());
    assertEquals(ref, new TargetRef("srv", TargetRef.IGNORES_TARGET));
  }

  @Test
  void applicationTerminalTargetIsUiOnly() {
    TargetRef ref = TargetRef.applicationTerminal();
    assertTrue(ref.isApplicationTerminal());
    assertTrue(ref.isApplicationUi());
    assertTrue(ref.isUiOnly());
    assertFalse(ref.isStatus());
    assertFalse(ref.isChannel());
    assertEquals(
        ref, new TargetRef(TargetRef.APPLICATION_SERVER_ID, TargetRef.APPLICATION_TERMINAL_TARGET));
  }

  @Test
  void applicationJfrTargetIsUiOnly() {
    TargetRef ref = TargetRef.applicationJfr();
    assertTrue(ref.isApplicationJfr());
    assertTrue(ref.isApplicationUi());
    assertTrue(ref.isUiOnly());
    assertFalse(ref.isStatus());
    assertFalse(ref.isChannel());
    assertEquals(
        ref, new TargetRef(TargetRef.APPLICATION_SERVER_ID, TargetRef.APPLICATION_JFR_TARGET));
  }

  @Test
  void applicationSpringTargetIsUiOnly() {
    TargetRef ref = TargetRef.applicationSpring();
    assertTrue(ref.isApplicationSpring());
    assertTrue(ref.isApplicationUi());
    assertTrue(ref.isUiOnly());
    assertFalse(ref.isStatus());
    assertFalse(ref.isChannel());
    assertEquals(
        ref, new TargetRef(TargetRef.APPLICATION_SERVER_ID, TargetRef.APPLICATION_SPRING_TARGET));
  }

  @Test
  void applicationInboundDedupTargetIsUiOnly() {
    TargetRef ref = TargetRef.applicationInboundDedup();
    assertTrue(ref.isApplicationInboundDedup());
    assertTrue(ref.isApplicationUi());
    assertTrue(ref.isUiOnly());
    assertFalse(ref.isStatus());
    assertFalse(ref.isChannel());
    assertEquals(
        ref,
        new TargetRef(TargetRef.APPLICATION_SERVER_ID, TargetRef.APPLICATION_INBOUND_DEDUP_TARGET));
  }
}
