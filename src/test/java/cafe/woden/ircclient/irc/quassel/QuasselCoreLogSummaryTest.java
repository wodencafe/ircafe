package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class QuasselCoreLogSummaryTest {
  @Test
  void nestedTypedPayloadsKeepSensitiveValuesRedacted() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("NetworkName", "example");
    payload.put(
        "ServerList",
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue(
                "Network::Server",
                Map.of("Host", "irc.example.net", "Password", "do-not-log-password"))));
    payload.put("AUTH_DATA", "do-not-log-auth");
    payload.put("Secret", "do-not-log-secret");
    payload.put("Token", "do-not-log-token");
    String summary = QuasselCoreLogSummary.summarizeNetworkInfoForLog(payload);
    assertTrue(summary.contains("NetworkName=\"example\""));
    assertTrue(summary.contains("Host=<depth-limit>"));
    assertTrue(summary.contains("Password=<redacted>"));
    assertFalse(summary.contains("do-not-log"));
  }

  @Test
  void binaryCodecsAreSummarizedByLength() {
    assertEquals(
        "{codec=byte[3]}",
        QuasselCoreLogSummary.summarizeNetworkInfoForLog(Map.of("codec", new byte[] {1, 2, 3})));
  }

  @Test
  void largeMapsAndListsStayTruncated() {
    Map<String, Object> map = new LinkedHashMap<>();
    IntStream.range(0, 33).forEach(i -> map.put("entry" + i, i));
    String summary = QuasselCoreLogSummary.summarizeNetworkInfoForLog(map);
    assertTrue(summary.contains("entry31=31"));
    assertFalse(summary.contains("entry32"));
    assertTrue(summary.endsWith(", ...}"));
    assertEquals(
        "{items=List[0, 1, 2, 3, 4, 5, 6, 7, ...]}",
        QuasselCoreLogSummary.summarizeNetworkInfoForLog(
            Map.of("items", IntStream.range(0, 9).boxed().toList())));
  }

  @Test
  void nestedPayloadsRespectExistingDepthLimit() {
    assertEquals(
        "{a={b={c={d=<depth-limit>}}}}",
        QuasselCoreLogSummary.summarizeNetworkInfoForLog(
            Map.of("a", Map.of("b", Map.of("c", Map.of("d", "hidden"))))));
  }

  @Test
  void absentAndEmptyPayloadsKeepEmptyMapRendering() {
    assertEquals("{}", QuasselCoreLogSummary.summarizeNetworkInfoForLog(null));
    assertEquals("{}", QuasselCoreLogSummary.summarizeNetworkInfoForLog(Map.of()));
  }
}
