package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PircbotxBridgeListenerPrivateTargetTest {

  @Test
  void resolvesPrivateTargetFromChannelSourceWhenActionEventLacksRawLine() {
    String target = PircbotxEventAccessors.privmsgTargetFromEvent(new FakeActionEvent("alice"));
    assertEquals("alice", target);
  }

  @Test
  void senderNickFallsBackToNoticeRawPrefix() {
    String nick =
        PircbotxEventAccessors.senderNickFromEvent(
            new FakeRawNoticeEvent(
                ":alis!alis@services.libera.chat NOTICE wodencafe2 :End of output."));
    assertEquals("alis", nick);
  }

  @Test
  void senderNickDefaultsToServerWhenUnavailable() {
    String nick = PircbotxEventAccessors.senderNickFromEvent(new Object());
    assertEquals("server", nick);
  }

  @Test
  void senderNickPrefersRawPrefixOverGetSourceFallback() {
    String nick =
        PircbotxEventAccessors.senderNickFromEvent(
            new FakeRawNoticeWithSourceEvent(
                ":alis!alis@services.libera.chat NOTICE wodencafe2 :#test 12 :topic",
                "wodencafe2"));
    assertEquals("alis", nick);
  }

  @Test
  void modeDetailsPreferRawModeLinePayload() {
    String details =
        PircbotxEventAccessors.modeDetailsFromEvent(
            new FakeModeEvent(":nick!ident@host MODE #ircafe +ov alice bob"), "#ircafe");

    assertEquals("+ov alice bob", details);
  }

  @Test
  void parseChannelRedirectExtractsBothChannelsFromErrLinkchannel() {
    ParsedChannelRedirect parsed =
        PircbotxInboundLineParsers.parseChannelRedirect(
            ":irc.example.net 470 chris #old #new :Forwarding to another channel");
    assertNotNull(parsed);

    assertEquals("#old", parsed.fromChannel());
    assertEquals("#new", parsed.toChannel());
  }

  @Test
  void parseChannelRedirectReturnsNullWhenRedirectTargetMissing() {
    ParsedChannelRedirect parsed =
        PircbotxInboundLineParsers.parseChannelRedirect(
            ":irc.example.net 470 chris #old :Forwarding to another channel");
    assertNull(parsed);
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

  private static final class FakeModeEvent {
    private final String rawLine;

    private FakeModeEvent(String rawLine) {
      this.rawLine = rawLine;
    }

    @SuppressWarnings("unused")
    public String getRawLine() {
      return rawLine;
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
