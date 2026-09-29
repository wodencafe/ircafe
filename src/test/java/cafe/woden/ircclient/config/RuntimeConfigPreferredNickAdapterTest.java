package cafe.woden.ircclient.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.junit.jupiter.api.Test;

class RuntimeConfigPreferredNickAdapterTest {

  @Test
  void delegatesPreferredNickPersistence() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    RuntimeConfigPreferredNickAdapter adapter =
        new RuntimeConfigPreferredNickAdapter(runtimeConfig);

    adapter.rememberNick("libera", "alice");

    verify(runtimeConfig).rememberNick("libera", "alice");
  }
}
