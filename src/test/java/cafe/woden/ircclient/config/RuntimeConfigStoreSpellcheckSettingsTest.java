package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreSpellcheckSettingsTest {

  @TempDir Path tempDir;

  @Test
  void spellcheckSettingsArePersistedUnderUiSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberSpellcheckEnabled(false);
    store.rememberSpellcheckUnderlineEnabled(false);
    store.rememberSpellcheckSuggestOnTabEnabled(false);
    store.rememberSpellcheckHoverSuggestionsEnabled(true);
    store.rememberSpellcheckCompletionPreset(" Conservative ");
    store.rememberSpellcheckLanguageTag("en_gb");
    store.rememberSpellcheckCustomMinPrefixCompletionTokenLength(1);
    store.rememberSpellcheckCustomMaxPrefixCompletionExtraChars(99);
    store.rememberSpellcheckCustomMaxPrefixLexiconCandidates(1);
    store.rememberSpellcheckCustomPrefixCompletionBonusScore(999);
    store.rememberSpellcheckCustomSourceOrderWeight(-5);
    store.rememberSpellcheckCustomDictionary(List.of(" alpha ", " ", "beta"));

    Map<String, Object> ui = RuntimeConfigYamlTestSupport.uiSection(cfg);
    assertEquals(false, ui.get("spellcheckEnabled"));
    assertEquals(false, ui.get("spellcheckUnderlineEnabled"));
    assertEquals(false, ui.get("spellcheckSuggestOnTabEnabled"));
    assertEquals(true, ui.get("spellcheckHoverSuggestionsEnabled"));
    assertEquals("conservative", ui.get("spellcheckCompletionPreset"));
    assertEquals("en-GB", ui.get("spellcheckLanguageTag"));
    assertEquals(2, ui.get("spellcheckCustomMinPrefixCompletionTokenLength"));
    assertEquals(24, ui.get("spellcheckCustomMaxPrefixCompletionExtraChars"));
    assertEquals(16, ui.get("spellcheckCustomMaxPrefixLexiconCandidates"));
    assertEquals(400, ui.get("spellcheckCustomPrefixCompletionBonusScore"));
    assertEquals(0, ui.get("spellcheckCustomSourceOrderWeight"));
    assertEquals(List.of("alpha", "beta"), ui.get("spellcheckCustomDictionary"));

    store.rememberSpellcheckCustomDictionary(List.of(" "));

    ui = RuntimeConfigYamlTestSupport.uiSection(cfg);
    assertFalse(ui.containsKey("spellcheckCustomDictionary"));
  }
}
