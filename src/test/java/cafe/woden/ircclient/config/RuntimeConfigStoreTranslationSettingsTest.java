package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreTranslationSettingsTest {

  @TempDir Path tempDir;

  @Test
  void translationSettingsArePersistedUnderIrcClientSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberClientTranslation(
        new IrcProperties.Client.Translation(
            true,
            IrcProperties.Client.Translation.Mode.MANUAL,
            "deepl",
            "https://api.deepl.com/v2/translate",
            "api-key-123",
            "en",
            "es",
            false,
            false,
            List.of("en", "es"),
            IrcProperties.Client.Translation.DisplayMode.BELOW_ORIGINAL,
            12_000,
            700,
            3));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("translation:"));
    assertTrue(yaml.contains("enabled: true"));
    assertTrue(yaml.contains("mode: manual"));
    assertTrue(yaml.contains("backend: deepl"));
    assertTrue(yaml.contains("endpoint: https://api.deepl.com/v2/translate"));
    assertTrue(
        yaml.contains("apiKey: api-key-123")
            || yaml.contains("apiKey: 'api-key-123'")
            || yaml.contains("apiKey: \"api-key-123\""));
    assertTrue(yaml.contains("targetLanguage: es"));
    assertTrue(yaml.contains("translateUnknownMessages: false"));
    assertTrue(yaml.contains("detectAllLanguages: false"));
    assertTrue(yaml.contains("detectionLanguages:"));
    assertTrue(yaml.contains("- en"));
    assertTrue(yaml.contains("- es"));
    assertTrue(yaml.contains("requestTimeoutMs: 12000"));
    assertTrue(yaml.contains("maxConcurrentRequests: 3"));
  }

  @Test
  void blankOptionalTranslationFieldsAreRemovedWhenDisabled() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberClientTranslation(
        new IrcProperties.Client.Translation(
            true,
            IrcProperties.Client.Translation.Mode.AUTO,
            "libretranslate",
            "https://translate.example.test/translate",
            "api-key-123",
            "auto",
            "es",
            null,
            8_000,
            300,
            1));
    store.rememberClientTranslation(
        new IrcProperties.Client.Translation(
            false,
            IrcProperties.Client.Translation.Mode.AUTO,
            "",
            "",
            "",
            "auto",
            "",
            null,
            10_000,
            4_000,
            2));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("enabled: false"));
    assertTrue(yaml.contains("mode: auto"));
    assertFalse(yaml.contains("backend:"));
    assertFalse(yaml.contains("endpoint:"));
    assertFalse(yaml.contains("apiKey:"));
    assertFalse(yaml.contains("targetLanguage:"));
  }

  @Test
  void translationSettingsCanBeReboundFromRuntimeConfig() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberClientTranslation(
        new IrcProperties.Client.Translation(
            true,
            IrcProperties.Client.Translation.Mode.AUTO,
            "libretranslate",
            "https://translate.example.test/translate",
            "",
            "auto",
            "es",
            true,
            false,
            List.of("en", "es"),
            null,
            8_000,
            300,
            1));

    org.springframework.core.env.StandardEnvironment env =
        new org.springframework.core.env.StandardEnvironment();
    env.getPropertySources()
        .addFirst(
            new org.springframework.core.env.MapPropertySource(
                "test", flatten(RuntimeConfigYamlTestSupport.loadYaml(cfg))));
    IrcProperties rebound =
        org.springframework.boot.context.properties.bind.Binder.get(env)
            .bind("irc", IrcProperties.class)
            .orElseThrow(() -> new IllegalStateException("Expected IrcProperties binding"));
    IrcProperties.Client.Translation translation = rebound.client().translation();
    assertEquals(IrcProperties.Client.Translation.Mode.AUTO, translation.mode());
    assertEquals("libretranslate", translation.backendId());
    assertEquals("https://translate.example.test/translate", translation.endpoint());
    assertEquals("es", translation.targetLanguage());
    assertTrue(translation.translateUnknownMessages());
    assertFalse(translation.detectAllLanguages());
    assertEquals(List.of("en", "es"), translation.detectionLanguages());
  }

  private static java.util.Map<String, Object> flatten(java.util.Map<String, Object> yaml) {
    java.util.Map<String, Object> flattened = new java.util.LinkedHashMap<>();
    flattenInto("", yaml, flattened);
    return flattened;
  }

  @SuppressWarnings("unchecked")
  private static void flattenInto(
      String prefix, java.util.Map<String, Object> source, java.util.Map<String, Object> target) {
    for (java.util.Map.Entry<String, Object> entry : source.entrySet()) {
      String key = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
      Object value = entry.getValue();
      if (value instanceof java.util.Map<?, ?> map) {
        flattenInto(key, (java.util.Map<String, Object>) map, target);
      } else {
        target.put(key, value);
      }
    }
  }
}
