package cafe.woden.ircclient.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.NegotiatedModeSemantics;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModeVocabularyParserTest {

  @Test
  void parsesExplicitPrefixAndChanmodesTokens() {
    ModeVocabulary vocabulary =
        ModeVocabularyParser.parse(
            Map.of(
                "PREFIX", "(ov)@+",
                "CHANMODES", "qbeI,k,l,imnst",
                "CHANTYPES", "#!",
                "STATUSMSG", "@",
                "CASEMAPPING", "ascii"));

    assertTrue(vocabulary.hasExplicitStatusMode('o'));
    assertTrue(vocabulary.hasExplicitStatusMode('v'));
    assertFalse(vocabulary.isStatusMode('h'));
    assertTrue(vocabulary.hasExplicitListMode('q'));
    assertTrue(vocabulary.isListMode('q'));
    assertFalse(vocabulary.isStatusMode('q'));
    assertTrue(vocabulary.takesArgument('q', true));
    assertTrue(vocabulary.takesArgument('q', false));
    assertEquals(0, vocabulary.prefixRank("@"));
    assertEquals(1, vocabulary.prefixRank("+"));
    assertEquals(99, vocabulary.prefixRank("~"));
    assertEquals("#!", vocabulary.channelTypes());
    assertEquals("@", vocabulary.statusMessagePrefixes());
    assertEquals("ascii", vocabulary.caseMapping());
  }

  @Test
  void fallsBackToConservativeDefaultsWhenTokensAreMissing() {
    ModeVocabulary vocabulary = ModeVocabularyParser.parse(Map.of());

    assertTrue(vocabulary.isStatusMode('o'));
    assertTrue(vocabulary.isListMode('b'));
    assertTrue(vocabulary.takesArgument('l', true));
    assertFalse(vocabulary.takesArgument('l', false));
    assertTrue(NegotiatedModeSemantics.isStatusMode(vocabulary, 'q', "alice"));
    assertTrue(NegotiatedModeSemantics.isListMode(vocabulary, 'q', "*!*@example"));
  }

  @Test
  void respectsAlternateExceptionAndInviteModes() {
    ModeVocabulary vocabulary =
        ModeVocabularyParser.parse(
            Map.of("CHANMODES", "beZ,k,l,imnst", "EXCEPTS", "Z", "INVEX", "X"));

    assertTrue(vocabulary.isExceptsMode('Z'));
    assertTrue(vocabulary.isInvexMode('X'));
    assertTrue(vocabulary.isListMode('Z'));
    assertTrue(vocabulary.isListMode('X'));
    assertTrue(vocabulary.takesArgument('Z', true));
    assertTrue(vocabulary.takesArgument('X', false));
  }
}
