package cafe.woden.ircclient.ignore.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IgnoreMaskNormalizerTest {

  @Test
  void normalizeReturnsEmptyForBlankInput() {
    assertEquals("", IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("  "));
  }

  @Test
  void normalizePreservesFullHostmaskPattern() {
    assertEquals(
        "nick!ident@host", IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("nick!ident@host"));
  }

  @Test
  void normalizePrefixesUserAtHostWithNickWildcard() {
    assertEquals("*!ident@host", IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("ident@host"));
    assertEquals(
        "*!ident@host", IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("*!ident@host"));
  }

  @Test
  void normalizeHandlesBangPrefixedAtHostInput() {
    assertEquals("!ident@host", IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("!ident@host"));
  }

  @Test
  void normalizeTreatsHostLikeInputAsWildcardHostmask() {
    assertEquals("*!*@bad.host", IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("bad.host"));
    assertEquals(
        "*!*@2001:db8::1", IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("2001:db8::1"));
    assertEquals("*!*@gateway/", IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask("gateway/"));
  }

  @Test
  void normalizeTreatsRemainingInputAsNickAndStripsWhitespace() {
    assertEquals("BadNick!*@*", IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(" Bad Nick "));
  }
}
