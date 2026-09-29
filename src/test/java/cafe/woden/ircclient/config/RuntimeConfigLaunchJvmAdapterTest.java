package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.api.LaunchJvmRuntimeConfigPort.LaunchJvmSnapshot;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeConfigLaunchJvmAdapterTest {

  @Test
  void readsLaunchJvmSnapshotFromRuntimeStore() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    when(runtimeConfig.readLaunchJvmJavaCommand("java")).thenReturn("java25");
    when(runtimeConfig.readLaunchJvmXmsMiB(0)).thenReturn(512);
    when(runtimeConfig.readLaunchJvmXmxMiB(0)).thenReturn(2048);
    when(runtimeConfig.readLaunchJvmGc("")).thenReturn("zgc");
    when(runtimeConfig.readLaunchJvmArgs(List.of())).thenReturn(List.of("-Dfoo=bar"));

    RuntimeConfigLaunchJvmAdapter adapter = new RuntimeConfigLaunchJvmAdapter(runtimeConfig);

    assertEquals(
        new LaunchJvmSnapshot("java25", 512, 2048, "zgc", List.of("-Dfoo=bar")),
        adapter.readLaunchJvmSettings());
  }

  @Test
  void writesLaunchJvmSnapshotToRuntimeStore() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    RuntimeConfigLaunchJvmAdapter adapter = new RuntimeConfigLaunchJvmAdapter(runtimeConfig);

    adapter.rememberLaunchJvmSettings(
        new LaunchJvmSnapshot("java25", 512, 2048, "zgc", List.of("-Dfoo=bar")));

    verify(runtimeConfig).rememberLaunchJvmJavaCommand("java25");
    verify(runtimeConfig).rememberLaunchJvmXmsMiB(512);
    verify(runtimeConfig).rememberLaunchJvmXmxMiB(2048);
    verify(runtimeConfig).rememberLaunchJvmGc("zgc");
    verify(runtimeConfig).rememberLaunchJvmArgs(List.of("-Dfoo=bar"));
  }
}
