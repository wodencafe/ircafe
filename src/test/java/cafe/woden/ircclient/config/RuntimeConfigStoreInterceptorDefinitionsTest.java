package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.InterceptorRule;
import cafe.woden.ircclient.model.InterceptorRuleMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreInterceptorDefinitionsTest {

  @TempDir Path tempDir;

  @Test
  void interceptorDefinitionsRoundTripRuntimeConfig() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    InterceptorDefinition def =
        new InterceptorDefinition(
            "id-1",
            "Bad words",
            true,
            "",
            InterceptorRuleMode.GLOB,
            "#general,#help",
            InterceptorRuleMode.GLOB,
            "#staff",
            true,
            true,
            true,
            "NOTIF_3",
            true,
            "sounds/custom.mp3",
            true,
            "/tmp/ircafe-hook.sh",
            "--arg value",
            "/tmp",
            List.of(
                new InterceptorRule(
                    true,
                    "Swear",
                    "message,action",
                    InterceptorRuleMode.REGEX,
                    "(damn|heck)",
                    InterceptorRuleMode.GLOB,
                    "bad*",
                    InterceptorRuleMode.GLOB,
                    "*!*@*")));

    store.rememberInterceptorDefinitions(Map.of("libera", List.of(def)));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("interceptors"));
    assertTrue(yaml.contains("channelIncludeMode: GLOB"));
    assertTrue(yaml.contains("actionStatusBarEnabled: true"));
    assertTrue(yaml.contains("actionToastEnabled: true"));
    assertTrue(
        yaml.contains("actionScriptPath: /tmp/ircafe-hook.sh")
            || yaml.contains("actionScriptPath: '/tmp/ircafe-hook.sh'")
            || yaml.contains("actionScriptPath: \"/tmp/ircafe-hook.sh\""));
    assertTrue(yaml.contains("hostmaskPattern"));

    Map<String, List<InterceptorDefinition>> roundTrip = store.readInterceptorDefinitions();
    assertFalse(roundTrip.isEmpty());
    assertTrue(roundTrip.containsKey("libera"));
    InterceptorDefinition saved = roundTrip.get("libera").getFirst();
    assertEquals("id-1", saved.id());
    assertEquals("Bad words", saved.name());
    assertEquals("", saved.scopeServerId());
    assertEquals("#general,#help", saved.channelIncludes());
    assertEquals("#staff", saved.channelExcludes());
    assertTrue(saved.actionSoundEnabled());
    assertTrue(saved.actionStatusBarEnabled());
    assertTrue(saved.actionToastEnabled());
    assertTrue(saved.actionScriptEnabled());
    assertEquals("message,action", saved.rules().getFirst().eventTypesCsv());
    assertEquals("(damn|heck)", saved.rules().getFirst().messagePattern());
    assertEquals("bad*", saved.rules().getFirst().nickPattern());
    assertEquals("*!*@*", saved.rules().getFirst().hostmaskPattern());
  }
}
