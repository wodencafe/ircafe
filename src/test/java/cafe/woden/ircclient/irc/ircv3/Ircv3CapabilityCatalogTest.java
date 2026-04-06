package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class Ircv3CapabilityCatalogTest {

  @Test
  void requestableCapabilitiesIncludeModernIrcv3NegotiationSet() {
    List<String> caps = Ircv3CapabilityCatalog.requestableCapabilities();
    assertTrue(caps.contains("echo-message"));
    assertTrue(caps.contains("cap-notify"));
    assertTrue(caps.contains("invite-notify"));
    assertTrue(caps.contains("labeled-response"));
    assertTrue(caps.contains("standard-replies"));
    assertTrue(caps.contains("monitor"));
    assertTrue(caps.contains("extended-monitor"));
    assertTrue(caps.contains("setname"));
    assertTrue(caps.contains("chghost"));
    assertTrue(caps.contains("draft/message-redaction"));
    assertTrue(caps.contains("draft/read-marker"));
    assertTrue(caps.contains("draft/multiline"));
    assertTrue(caps.contains("draft/chathistory"));
    assertFalse(caps.contains("sts"));
    assertFalse(caps.contains("draft/channel-context"));
    assertFalse(caps.contains("draft/reply"));
    assertFalse(caps.contains("draft/react"));
    assertFalse(caps.contains("draft/unreact"));
    assertFalse(caps.contains("draft/message-edit"));
    assertFalse(caps.contains("message-edit"));
    assertFalse(caps.contains("message-redaction"));
    assertFalse(caps.contains("draft/typing"));
    assertFalse(caps.contains("typing"));
    assertFalse(caps.contains("read-marker"));
    assertFalse(caps.contains("multiline"));
    assertFalse(caps.contains("chathistory"));
  }
}
