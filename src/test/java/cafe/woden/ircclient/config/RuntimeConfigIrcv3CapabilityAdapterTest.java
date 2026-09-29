package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeConfigIrcv3CapabilityAdapterTest {

  @Test
  void delegatesCapabilityReadsToRuntimeStore() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    Map<String, Boolean> persisted = Map.of("typing", Boolean.FALSE);
    when(runtimeConfig.readIrcv3Capabilities()).thenReturn(persisted);

    RuntimeConfigIrcv3CapabilityAdapter adapter =
        new RuntimeConfigIrcv3CapabilityAdapter(runtimeConfig);

    assertEquals(persisted, adapter.readIrcv3Capabilities());
  }

  @Test
  void delegatesCapabilityWritesToRuntimeStore() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    RuntimeConfigIrcv3CapabilityAdapter adapter =
        new RuntimeConfigIrcv3CapabilityAdapter(runtimeConfig);

    adapter.rememberIrcv3CapabilityEnabled("typing", false);

    verify(runtimeConfig).rememberIrcv3CapabilityEnabled("typing", false);
  }
}
