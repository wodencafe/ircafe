package cafe.woden.ircclient.ui.chat.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatViewPanelPrefillDraftTest {

  @Test
  void buildReplyPrefillDraftEscapesMessageIdForIrcv3Tags() {
    String draft = Exposed.reply("#ircafe", "abc 123;xyz\\tail");
    assertEquals("/quote @+draft/reply=abc\\s123\\:xyz\\\\tail PRIVMSG #ircafe :", draft);
  }

  @Test
  void buildReactPrefillDraftUsesDefaultReactionAndReplyTag() {
    String draft = Exposed.react("#ircafe", "msgid-42");
    assertEquals("/quote @+draft/react=:+1:;+draft/reply=msgid-42 TAGMSG #ircafe", draft);
  }

  @Test
  void prefillDraftBuildersReturnEmptyForBlankInputs() {
    assertEquals("", Exposed.reply("", "msgid-1"));
    assertEquals("", Exposed.reply("#ircafe", ""));
    assertEquals("", Exposed.react("", "msgid-1"));
    assertEquals("", Exposed.react("#ircafe", ""));
  }

  @Test
  void buildChatHistoryLatestCommandUsesWildcardSelector() {
    assertEquals("/chathistory latest *", Exposed.latestHistory());
  }

  @Test
  void buildChatHistoryAroundByMsgIdCommandBuildsSelectorToken() {
    assertEquals("/chathistory around msgid=abc123", Exposed.aroundHistory("abc123"));
  }

  @Test
  void buildChatHistoryAroundByMsgIdCommandRejectsBlankOrWhitespaceIds() {
    assertEquals("", Exposed.aroundHistory(""));
    assertEquals("", Exposed.aroundHistory("has space"));
  }

  private static final class Exposed extends ChatViewPanel {
    private Exposed() {
      super(null);
    }

    private static String reply(String target, String messageId) {
      return buildReplyPrefillDraft(target, messageId);
    }

    private static String react(String target, String messageId) {
      return buildReactPrefillDraft(target, messageId);
    }

    private static String latestHistory() {
      return buildChatHistoryLatestCommand();
    }

    private static String aroundHistory(String messageId) {
      return buildChatHistoryAroundByMsgIdCommand(messageId);
    }

    @Override
    protected boolean isFollowTail() {
      return false;
    }

    @Override
    protected void setFollowTail(boolean followTail) {}

    @Override
    protected int getSavedScrollValue() {
      return 0;
    }

    @Override
    protected void setSavedScrollValue(int value) {}
  }
}
