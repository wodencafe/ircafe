package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreApplicationJfrEnabledTest {

  @TempDir Path tempDir;

  @Test
  void applicationJfrEnabledRoundTripsFromRuntimeConfig() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    assertTrue(store.readApplicationJfrEnabled(true));

    store.rememberApplicationJfrEnabled(false);
    assertFalse(store.readApplicationJfrEnabled(true));

    store.rememberApplicationJfrEnabled(true);
    assertTrue(store.readApplicationJfrEnabled(false));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("appDiagnostics"));
    assertTrue(yaml.contains("jfr"));
    assertTrue(yaml.contains("enabled"));
  }
}
