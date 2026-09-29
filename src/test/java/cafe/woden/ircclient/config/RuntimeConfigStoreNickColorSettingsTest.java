package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreNickColorSettingsTest {

  @TempDir Path tempDir;

  @Test
  void nickColorSettingsAndOverridesArePersistedUnderUiSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    Map<String, String> overrides = new LinkedHashMap<>();
    overrides.put(" alice ", " #123456 ");
    overrides.put(" ", "#ffffff");
    overrides.put("bob", " ");

    store.rememberNickColoringEnabled(false);
    store.rememberNickColorMinContrast(-1);
    store.rememberNickColorOverrides(overrides);

    Map<String, Object> ui = RuntimeConfigYamlTestSupport.uiSection(cfg);
    assertEquals(Boolean.FALSE, ui.get("nickColoringEnabled"));
    assertEquals(3.0, ((Number) ui.get("nickColorMinContrast")).doubleValue());

    Map<?, ?> persistedOverrides = (Map<?, ?>) ui.get("nickColorOverrides");
    assertEquals(1, persistedOverrides.size());
    assertEquals("#123456", persistedOverrides.get("alice"));

    store.rememberNickColorOverrides(Map.of());

    ui = RuntimeConfigYamlTestSupport.uiSection(cfg);
    assertFalse(ui.containsKey("nickColorOverrides"));
  }
}
