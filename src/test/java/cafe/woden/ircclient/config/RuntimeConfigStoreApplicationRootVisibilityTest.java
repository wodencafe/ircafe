package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreApplicationRootVisibilityTest {

  @TempDir Path tempDir;

  @Test
  void applicationRootVisibilityRoundTripsUnderServerTreeUiSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    assertTrue(store.readApplicationRootVisible(true));

    store.rememberApplicationRootVisible(false);

    assertFalse(store.readApplicationRootVisible(true));
    Map<String, Object> serverTree = RuntimeConfigYamlTestSupport.uiSection(cfg, "serverTree");
    assertFalse((Boolean) serverTree.get("applicationRootVisible"));

    store.rememberApplicationRootVisible(true);

    assertTrue(store.readApplicationRootVisible(false));
  }
}
