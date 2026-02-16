package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxBotFactoryCapabilitiesTest {

  @SuppressWarnings("unchecked")
  @Test
  void baseCapabilitiesIncludeModernIrcv3NegotiationSet() throws Exception {
    Field f = PircbotxBotFactory.class.getDeclaredField("BASE_CAPABILITIES");
    f.setAccessible(true);

    List<String> caps = (List<String>) f.get(null);
    assertTrue(caps.contains("echo-message"));
    assertTrue(caps.contains("cap-notify"));
    assertTrue(caps.contains("labeled-response"));
    assertTrue(caps.contains("standard-replies"));
    assertTrue(caps.contains("setname"));
    assertTrue(caps.contains("chghost"));
    assertTrue(caps.contains("draft/reply"));
    assertTrue(caps.contains("draft/react"));
    assertTrue(caps.contains("typing"));
    assertTrue(caps.contains("read-marker"));
    assertTrue(caps.contains("chathistory"));
    assertTrue(caps.contains("draft/chathistory"));
  }
}
