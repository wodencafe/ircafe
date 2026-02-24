package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class PircbotxBridgeListenerPrivateTargetTest {

  @Test
  void resolvesPrivateTargetFromChannelSourceWhenActionEventLacksRawLine() throws Exception {
    Method m = PircbotxBridgeListener.class.getDeclaredMethod("privmsgTargetFromEvent", Object.class);
    m.setAccessible(true);

    String target = (String) m.invoke(null, new FakeActionEvent("alice"));
    assertEquals("alice", target);
  }

  private static final class FakeActionEvent {
    private final String channelSource;

    private FakeActionEvent(String channelSource) {
      this.channelSource = channelSource;
    }

    @SuppressWarnings("unused")
    public String getChannelSource() {
      return channelSource;
    }
  }
}
