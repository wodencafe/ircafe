package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptAttrSupportTest {

  @Test
  void isConversationLineMatchesChatActionNoticeAndSpoilerKinds() {
    assertTrue(
        attrsWithKind("chat").map(ChatTranscriptAttrSupport::isConversationLine).orElseThrow());
    assertTrue(
        attrsWithKind("ACTION").map(ChatTranscriptAttrSupport::isConversationLine).orElseThrow());
    assertTrue(
        attrsWithKind("notice").map(ChatTranscriptAttrSupport::isConversationLine).orElseThrow());
    assertTrue(
        attrsWithKind("Spoiler").map(ChatTranscriptAttrSupport::isConversationLine).orElseThrow());
    assertFalse(
        attrsWithKind("status").map(ChatTranscriptAttrSupport::isConversationLine).orElseThrow());
    assertFalse(ChatTranscriptAttrSupport.isConversationLine(null));
  }

  @Test
  void lineEpochMsAcceptsNumbersAndStrings() {
    SimpleAttributeSet numberAttrs = new SimpleAttributeSet();
    numberAttrs.addAttribute(ChatStyles.ATTR_META_EPOCH_MS, 1234L);
    SimpleAttributeSet stringAttrs = new SimpleAttributeSet();
    stringAttrs.addAttribute(ChatStyles.ATTR_META_EPOCH_MS, " 5678 ");
    SimpleAttributeSet invalidAttrs = new SimpleAttributeSet();
    invalidAttrs.addAttribute(ChatStyles.ATTR_META_EPOCH_MS, "not-a-number");

    assertEquals(1234L, ChatTranscriptAttrSupport.lineEpochMs(numberAttrs));
    assertEquals(5678L, ChatTranscriptAttrSupport.lineEpochMs(stringAttrs));
    assertNull(ChatTranscriptAttrSupport.lineEpochMs(invalidAttrs));
    assertNull(ChatTranscriptAttrSupport.lineEpochMs(null));
  }

  @Test
  void logKindAndDirectionFallBackSafelyWhenAttributesAreMissingOrInvalid() {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_KIND, "action");
    attrs.addAttribute(ChatStyles.ATTR_META_DIRECTION, "out");
    SimpleAttributeSet invalidAttrs = new SimpleAttributeSet();
    invalidAttrs.addAttribute(ChatStyles.ATTR_META_KIND, "nope");
    invalidAttrs.addAttribute(ChatStyles.ATTR_META_DIRECTION, "sideways");

    assertEquals(LogKind.ACTION, ChatTranscriptAttrSupport.logKindFromAttrs(attrs));
    assertEquals(LogDirection.OUT, ChatTranscriptAttrSupport.logDirectionFromAttrs(attrs));
    assertEquals(LogKind.CHAT, ChatTranscriptAttrSupport.logKindFromAttrs(invalidAttrs));
    assertEquals(LogDirection.IN, ChatTranscriptAttrSupport.logDirectionFromAttrs(invalidAttrs));
    assertEquals(LogKind.CHAT, ChatTranscriptAttrSupport.logKindFromAttrs(null));
    assertEquals(LogDirection.IN, ChatTranscriptAttrSupport.logDirectionFromAttrs(null));
  }

  @Test
  void filterActionFromAttrHandlesCaseInsensitiveAndInvalidValues() {
    assertEquals(FilterAction.HIDE, ChatTranscriptAttrSupport.filterActionFromAttr("hide"));
    assertEquals(
        FilterAction.HIGHLIGHT, ChatTranscriptAttrSupport.filterActionFromAttr("HIGHLIGHT"));
    assertNull(ChatTranscriptAttrSupport.filterActionFromAttr("unknown"));
    assertNull(ChatTranscriptAttrSupport.filterActionFromAttr(" "));
    assertNull(ChatTranscriptAttrSupport.filterActionFromAttr(null));
  }

  private static java.util.Optional<SimpleAttributeSet> attrsWithKind(String kind) {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_KIND, kind);
    return java.util.Optional.of(attrs);
  }
}
