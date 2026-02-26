package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreLaunchJvmSettingsTest {

  @TempDir Path tempDir;

  @Test
  void launchJvmSettingsRoundTripThroughRuntimeConfig() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberLaunchJvmJavaCommand("java21");
    store.rememberLaunchJvmXmsMiB(768);
    store.rememberLaunchJvmXmxMiB(4096);
    store.rememberLaunchJvmGc("zgc");
    store.rememberLaunchJvmArgs(List.of("-XX:+AlwaysPreTouch", "-Dsample=true"));

    assertEquals("java21", store.readLaunchJvmJavaCommand("java"));
    assertEquals(768, store.readLaunchJvmXmsMiB(0));
    assertEquals(4096, store.readLaunchJvmXmxMiB(0));
    assertEquals("zgc", store.readLaunchJvmGc(""));
    assertEquals(List.of("-XX:+AlwaysPreTouch", "-Dsample=true"), store.readLaunchJvmArgs(List.of()));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("launch"));
    assertTrue(yaml.contains("jvm"));
    assertTrue(yaml.contains("javaCommand: java21"));
    assertTrue(yaml.contains("xmsMiB: 768"));
    assertTrue(yaml.contains("xmxMiB: 4096"));
    assertTrue(yaml.contains("gc: zgc"));
    assertTrue(yaml.contains("-XX:+AlwaysPreTouch"));
  }

  @Test
  void defaultLikeLaunchJvmValuesAreCompactedOutOfConfig() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberLaunchJvmJavaCommand("java21");
    store.rememberLaunchJvmXmsMiB(512);
    store.rememberLaunchJvmXmxMiB(1024);
    store.rememberLaunchJvmGc("g1");
    store.rememberLaunchJvmArgs(List.of("-Dfoo=bar"));

    store.rememberLaunchJvmJavaCommand("java");
    store.rememberLaunchJvmXmsMiB(0);
    store.rememberLaunchJvmXmxMiB(0);
    store.rememberLaunchJvmGc("default");
    store.rememberLaunchJvmArgs(List.of());

    String yaml = Files.readString(cfg);
    assertFalse(yaml.contains("launch:"));
    assertFalse(yaml.contains("javaCommand:"));
    assertFalse(yaml.contains("xmsMiB:"));
    assertFalse(yaml.contains("xmxMiB:"));
    assertFalse(yaml.contains("gc:"));
    assertFalse(yaml.contains("args:"));
  }
}
