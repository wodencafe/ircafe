package cafe.woden.ircclient.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.properties.PushyProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.junit.jupiter.api.Test;

class RuntimeConfigPushyAdapterTest {

  @Test
  void delegatesPushyPersistence() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    RuntimeConfigPushyAdapter adapter = new RuntimeConfigPushyAdapter(runtimeConfig);
    PushyProperties settings =
        new PushyProperties(
            true, "https://push.example/push", "secret", "device-token", null, "IRCafe", 5, 8);

    adapter.rememberPushySettings(settings);

    verify(runtimeConfig).rememberPushySettings(settings);
  }
}
