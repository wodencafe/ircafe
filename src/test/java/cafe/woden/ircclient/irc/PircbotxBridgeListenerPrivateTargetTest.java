package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class PircbotxBridgeListenerPrivateTargetTest {

  @Test
  void resolvesPrivateTargetFromChannelSourceWhenActionEventLacksRawLine() throws Exception {
    Method m =
        PircbotxBridgeListener.class.getDeclaredMethod("privmsgTargetFromEvent", Object.class);
    m.setAccessible(true);

    String target = (String) m.invoke(null, new FakeActionEvent("alice"));
    assertEquals("alice", target);
  }

  @Test
  void senderNickFallsBackToNoticeRawPrefix() throws Exception {
    Method m =
        PircbotxBridgeListener.class.getDeclaredMethod("senderNickFromEvent", Object.class);
    m.setAccessible(true);

    String nick =
        (String)
            m.invoke(
                null,
                new FakeRawNoticeEvent(
                    ":alis!alis@services.libera.chat NOTICE wodencafe2 :End of output."));
    assertEquals("alis", nick);
  }

  @Test
  void senderNickDefaultsToServerWhenUnavailable() throws Exception {
    Method m =
        PircbotxBridgeListener.class.getDeclaredMethod("senderNickFromEvent", Object.class);
    m.setAccessible(true);

    String nick = (String) m.invoke(null, new Object());
    assertEquals("server", nick);
  }

  @Test
  void senderNickPrefersRawPrefixOverGetSourceFallback() throws Exception {
    Method m =
        PircbotxBridgeListener.class.getDeclaredMethod("senderNickFromEvent", Object.class);
    m.setAccessible(true);

    String nick =
        (String)
            m.invoke(
                null,
                new FakeRawNoticeWithSourceEvent(
                    ":alis!alis@services.libera.chat NOTICE wodencafe2 :#test 12 :topic",
                    "wodencafe2"));
    assertEquals("alis", nick);
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

  private static final class FakeRawNoticeEvent {
    private final String rawLine;

    private FakeRawNoticeEvent(String rawLine) {
      this.rawLine = rawLine;
    }

    @SuppressWarnings("unused")
    public String getRawLine() {
      return rawLine;
    }
  }

  private static final class FakeRawNoticeWithSourceEvent {
    private final String rawLine;
    private final FakeSource source;

    private FakeRawNoticeWithSourceEvent(String rawLine, String sourceNick) {
      this.rawLine = rawLine;
      this.source = new FakeSource(sourceNick);
    }

    @SuppressWarnings("unused")
    public String getRawLine() {
      return rawLine;
    }

    @SuppressWarnings("unused")
    public Object getSource() {
      return source;
    }
  }

  private static final class FakeSource {
    private final String nick;

    private FakeSource(String nick) {
      this.nick = nick;
    }

    @SuppressWarnings("unused")
    public String getNick() {
      return nick;
    }
  }
}
