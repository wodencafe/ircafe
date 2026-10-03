package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.irc.quassel.QuasselCoreFeatureStateParser.MonitorSupportState;
import cafe.woden.ircclient.irc.quassel.QuasselCoreFeatureStateParser.MultilineLimitState;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuasselCoreFeatureStateParserTest {
  @Test
  void decodesCapabilitiesFromByteArraysListsAndEnabledMapEntries() {
    Map<String, Object> entries = new LinkedHashMap<>();
    entries.put("+MULTILINE=max-lines=4", true);
    entries.put("draft/read-marker", "off");
    entries.put("echo-message", 0);
    entries.put("typing", "false");
    entries.put("labeled-response", "");
    Map<String, Object> state =
        Map.of(
            "CAPSENABLED",
            List.of("message-tags,standard-replies".getBytes(StandardCharsets.UTF_8), entries));
    assertEquals(
        Set.of("message-tags", "standard-replies", "multiline", "labeled-response"),
        QuasselCoreFeatureStateParser.extractCapabilityTokens(state, "capsEnabled"));
  }

  @Test
  void decodesMultilineLimitsFromStructuredAndTextPayloads() {
    Map<String, Object> state =
        Map.of(
            "capsEnabled", Map.of("draft/multiline", Map.of("max_bytes", 4096, "max-lines", "4")));
    assertEquals(
        new MultilineLimitState(4096, 4),
        QuasselCoreFeatureStateParser.extractMultilineLimitsFromStateMap(state));
    assertEquals(
        new MultilineLimitState(2048, 3),
        QuasselCoreFeatureStateParser.multilineLimitsFromToken(
            ":multiline=max-bytes=2048,max-lines=3"));
    assertNull(QuasselCoreFeatureStateParser.multilineLimitsFromToken("-multiline=max-lines=4"));
    assertNull(
        QuasselCoreFeatureStateParser.extractMultilineLimitsFromStateMap(
            Map.of("caps", List.of("message-tags"))));
  }

  @Test
  void laterMultilineObservationsPreserveExistingPrecedence() {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("capsEnabled", List.of("multiline=max-bytes=4096,max-lines=4"));
    state.put("availableCaps", "draft/multiline=max-lines=8".getBytes(StandardCharsets.UTF_8));
    assertEquals(
        new MultilineLimitState(4096, 8),
        QuasselCoreFeatureStateParser.extractMultilineLimitsFromStateMap(state));
  }

  @Test
  void decodesMonitorSupportFromNestedIsupportAndStructuredFields() {
    assertEquals(
        new MonitorSupportState(true, 250),
        QuasselCoreFeatureStateParser.extractMonitorSupportFromStateMap(
            Map.of(
                "isupport", List.of("CHANTYPES=# MONITOR=250;".getBytes(StandardCharsets.UTF_8)))));
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("supportsMonitor", true);
    state.put("monitorLimit", 100);
    assertEquals(
        new MonitorSupportState(true, 100),
        QuasselCoreFeatureStateParser.extractMonitorSupportFromStateMap(state));
    assertNull(
        QuasselCoreFeatureStateParser.extractMonitorSupportFromStateMap(
            Map.of("networkName", "libera")));
    assertNull(QuasselCoreFeatureStateParser.extractMonitorSupportFromStateMap(Map.of()));
  }
}
