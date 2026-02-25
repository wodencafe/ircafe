package cafe.woden.ircclient.ui.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TagSpec;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TagSpecTest {

  @Test
  void supportsSpacedAndOperator() {
    TagSpec spec = TagSpec.parse("irc_in + irc_privmsg");
    assertTrue(spec.matches(Set.of("irc_in", "irc_privmsg")));
    assertFalse(spec.matches(Set.of("irc_in")));
  }

  @Test
  void supportsRegexLiteralWithPlusQuantifier() {
    TagSpec spec = TagSpec.parse("/^nick_.+$/");
    assertTrue(spec.matches(Set.of("nick_alice")));
    assertFalse(spec.matches(Set.of("irc_privmsg")));
  }

  @Test
  void supportsRegexLiteralCombinedWithAndClause() {
    TagSpec spec = TagSpec.parse("/^nick_.+$/+irc_in");
    assertTrue(spec.matches(Set.of("nick_alice", "irc_in")));
    assertFalse(spec.matches(Set.of("nick_alice")));
  }

  @Test
  void supportsRePrefixRegexWithPlusQuantifier() {
    TagSpec spec = TagSpec.parse("re:^nick_.+$");
    assertTrue(spec.matches(Set.of("nick_alice")));
    assertFalse(spec.matches(Set.of("nick")));
  }
}
