package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MessageInputPanelDraftNormalizationTest {

  @Test
  void stripsReplyTagWhenReplyCapabilityIsDisabled() {
    String before = "/quote @+draft/reply=abc123 PRIVMSG #ircafe :hello there";
    String after = MessageInputPanel.normalizeIrcv3DraftForCapabilities(before, false, true);
    assertEquals("/quote PRIVMSG #ircafe :hello there", after);
  }

  @Test
  void keepsOtherTagsWhenRemovingReplyTag() {
    String before = "/quote @label=req42;+draft/reply=abc123 PRIVMSG #ircafe :hello";
    String after = MessageInputPanel.normalizeIrcv3DraftForCapabilities(before, false, true);
    assertEquals("/quote @label=req42 PRIVMSG #ircafe :hello", after);
  }

  @Test
  void clearsReactDraftWhenReactCapabilityIsDisabled() {
    String before = "/quote @+draft/react=:+1:;+draft/reply=abc TAGMSG #ircafe";
    String after = MessageInputPanel.normalizeIrcv3DraftForCapabilities(before, true, false);
    assertEquals("", after);
  }

  @Test
  void clearsReactDraftWhenReplyCapabilityIsDisabled() {
    String before = "/quote @+draft/react=:+1:;+draft/reply=abc TAGMSG #ircafe";
    String after = MessageInputPanel.normalizeIrcv3DraftForCapabilities(before, false, true);
    assertEquals("", after);
  }

  @Test
  void leavesUnrelatedDraftUnchanged() {
    String before = "/me waves";
    String after = MessageInputPanel.normalizeIrcv3DraftForCapabilities(before, false, false);
    assertEquals(before, after);
  }
}
