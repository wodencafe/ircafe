package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NotificationBackendModeTest {

  @Test
  void fromTokenDefaultsToAutoForNullEmptyAndUnknown() {
    assertEquals(NotificationBackendMode.AUTO, NotificationBackendMode.fromToken(null));
    assertEquals(NotificationBackendMode.AUTO, NotificationBackendMode.fromToken(""));
    assertEquals(NotificationBackendMode.AUTO, NotificationBackendMode.fromToken("   "));
    assertEquals(NotificationBackendMode.AUTO, NotificationBackendMode.fromToken("unknown"));
  }

  @Test
  void fromTokenParsesSupportedAliases() {
    assertEquals(NotificationBackendMode.AUTO, NotificationBackendMode.fromToken("auto"));
    assertEquals(NotificationBackendMode.NATIVE_ONLY, NotificationBackendMode.fromToken("native"));
    assertEquals(
        NotificationBackendMode.NATIVE_ONLY, NotificationBackendMode.fromToken("native-only"));
    assertEquals(
        NotificationBackendMode.TWO_SLICES_ONLY, NotificationBackendMode.fromToken("two-slices"));
    assertEquals(
        NotificationBackendMode.TWO_SLICES_ONLY, NotificationBackendMode.fromToken("two_slices"));
    assertEquals(
        NotificationBackendMode.TWO_SLICES_ONLY, NotificationBackendMode.fromToken("twoslices"));
    assertEquals(
        NotificationBackendMode.TWO_SLICES_ONLY,
        NotificationBackendMode.fromToken("two-slices-only"));
  }
}
