package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Ircv3MultilineSupportTest {

  @Test
  void recognizesFinalAndDraftMultilineCapabilities() {
    assertTrue(Ircv3MultilineSupport.isMultilineCapability("multiline"));
    assertTrue(Ircv3MultilineSupport.isMultilineCapability("draft/multiline"));
    assertFalse(Ircv3MultilineSupport.isMultilineCapability("message-tags"));
    assertFalse(Ircv3MultilineSupport.isDraftMultilineCapability("multiline"));
    assertTrue(Ircv3MultilineSupport.isDraftMultilineCapability("draft/multiline"));
  }

  @Test
  void recognizesConcatTagsWithRawPrefixes() {
    assertTrue(Ircv3MultilineSupport.isMultilineConcatTag("multiline-concat"));
    assertTrue(Ircv3MultilineSupport.isMultilineConcatTag("draft/multiline-concat"));
    assertTrue(Ircv3MultilineSupport.isMultilineConcatTag("+draft/multiline-concat"));
    assertTrue(Ircv3MultilineSupport.isMultilineConcatTag("@+multiline-concat"));
    assertFalse(Ircv3MultilineSupport.isMultilineConcatTag("reply"));
  }

  @Test
  void resolvesNegotiatedFinalAndDraftTokens() {
    assertEquals("multiline", Ircv3MultilineSupport.negotiatedBatchType(true, true));
    assertEquals("multiline-concat", Ircv3MultilineSupport.negotiatedConcatTag(true, true));
    assertEquals("draft/multiline", Ircv3MultilineSupport.negotiatedBatchType(false, true));
    assertEquals("draft/multiline-concat", Ircv3MultilineSupport.negotiatedConcatTag(false, true));
    assertEquals("", Ircv3MultilineSupport.negotiatedBatchType(false, false));
    assertEquals("", Ircv3MultilineSupport.negotiatedConcatTag(false, false));
  }

  @Test
  void parsesNamedMultilineLimitParameters() {
    Ircv3MultilineSupport.LimitParams parsed =
        Ircv3MultilineSupport.parseLimitParams("max-bytes=4096,max-lines=4");

    assertEquals(4096L, parsed.maxBytes());
    assertEquals(4L, parsed.maxLines());
  }
}
