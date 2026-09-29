package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreEmbedCardStyleTest {

  @TempDir Path tempDir;

  @Test
  void rememberEmbedCardStylePersistsTokenUnderUiSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberEmbedCardStyle("glassy");

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("ui:"));
    assertTrue(yaml.contains("embedCardStyle: glassy"));
  }

  @Test
  void rememberEmbedPreviewSettingsPersistUnderUiSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberImageEmbedsEnabled(false);
    store.rememberImageEmbedsCollapsedByDefault(true);
    store.rememberImageEmbedsMaxWidthPx(-1);
    store.rememberImageEmbedsMaxHeightPx(480);
    store.rememberImageEmbedsAnimateGifs(false);
    store.rememberLinkPreviewsEnabled(false);
    store.rememberLinkPreviewsCollapsedByDefault(true);

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("ui:"));
    assertTrue(yaml.contains("imageEmbedsEnabled: false"));
    assertTrue(yaml.contains("imageEmbedsCollapsedByDefault: true"));
    assertTrue(yaml.contains("imageEmbedsMaxWidthPx: 0"));
    assertTrue(yaml.contains("imageEmbedsMaxHeightPx: 480"));
    assertTrue(yaml.contains("imageEmbedsAnimateGifs: false"));
    assertTrue(yaml.contains("linkPreviewsEnabled: false"));
    assertTrue(yaml.contains("linkPreviewsCollapsedByDefault: true"));
  }

  @Test
  void blankEmbedCardStyleFallsBackToDefaultToken() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberEmbedCardStyle("   ");

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("embedCardStyle: default"));
  }
}
