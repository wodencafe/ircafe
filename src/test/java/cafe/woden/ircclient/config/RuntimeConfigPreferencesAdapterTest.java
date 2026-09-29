package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RuntimeConfigPreferencesAdapterTest {

  @Test
  void exposesRuntimeConfigPath() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    Path path = Path.of("config", "ircafe.yml");
    when(runtimeConfig.runtimeConfigPath()).thenReturn(path);
    RuntimeConfigPreferencesAdapter adapter = new RuntimeConfigPreferencesAdapter(runtimeConfig);

    assertEquals(path, adapter.runtimeConfigPath());
  }

  @Test
  void delegatesMutationBatchAsOneAction() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    RuntimeConfigPreferencesAdapter adapter = new RuntimeConfigPreferencesAdapter(runtimeConfig);
    Runnable action = mock(Runnable.class);

    adapter.runMutationBatch(action);

    verify(runtimeConfig).runMutationBatch(action);
  }

  @Test
  void writesUiAndStartupSettings() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    RuntimeConfigPreferencesAdapter adapter = new RuntimeConfigPreferencesAdapter(runtimeConfig);

    adapter.rememberUiSettings("dark", "Inter", 15);
    adapter.rememberAutoConnectOnStart(false);

    verify(runtimeConfig).rememberUiSettings("dark", "Inter", 15);
    verify(runtimeConfig).rememberAutoConnectOnStart(false);
  }
}
