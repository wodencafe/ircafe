package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.settings.SpellcheckSettings;
import java.util.List;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class MessageInputSpellcheckSupportTest {

  @Test
  void suppressesSuggestionsForKnownNick() {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());
    support.setNickWhitelist(List.of("foobarNick"));

    List<String> suggestions = support.suggestWords("foobarNick", 5);

    assertTrue(suggestions.isEmpty(), "known nick should not be treated as misspelled");
  }

  @Test
  void skipsSuggestionsForNonWordTokens() {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());

    assertTrue(support.suggestWords("https://example.com", 5).isEmpty());
    assertTrue(support.suggestWords("#ircafe", 5).isEmpty());
  }
}
