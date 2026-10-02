package cafe.woden.ircclient.config.runtime.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeConfigServerListCodecTest {

  @Test
  void serverMapsPersistCustomBackendAndOmitDefaultBackend() {
    List<Map<String, Object>> maps =
        RuntimeConfigServerListCodec.serverMaps(
            List.of(server("plugin-net", "plugin-backend"), server("libera", "irc")));

    assertEquals("plugin-backend", maps.getFirst().get("backend"));
    assertFalse(maps.get(1).containsKey("backend"));
  }

  @Test
  void serverMapsPersistCertificatePolicyForEachServer() {
    List<Map<String, Object>> maps =
        RuntimeConfigServerListCodec.serverMaps(
            List.of(
                IrcPropertiesTestFixtures.serverBuilder("znc").trustAllCertificates(true).build(),
                IrcPropertiesTestFixtures.server("libera")));

    assertEquals(true, maps.getFirst().get("trustAllCertificates"));
    assertEquals(false, maps.get(1).get("trustAllCertificates"));
  }

  @Test
  void readServerIdsNormalizesDedupesAndFallsBackToDefaults() {
    IrcProperties defaults =
        IrcPropertiesTestFixtures.properties(
            IrcPropertiesTestFixtures.server("libera"), IrcPropertiesTestFixtures.server("oftc"));

    assertEquals(
        List.of("runtime", "oftc"),
        RuntimeConfigServerListCodec.readServerIds(
            List.of(
                Map.of("id", " runtime "),
                Map.of("id", "RUNTIME"),
                Map.of("id", ""),
                Map.of("id", "oftc")),
            defaults));
    assertEquals(
        List.of("libera", "oftc"), RuntimeConfigServerListCodec.readServerIds(null, defaults));
  }

  @Test
  void readExplicitServerAutoJoinByIdKeepsOnlyExplicitServerLists() {
    assertEquals(
        Map.of("libera", List.of("#ircafe", "#java"), "empty", List.of()),
        RuntimeConfigServerListCodec.readExplicitServerAutoJoinById(
            List.of(
                Map.of("id", " libera ", "autoJoin", List.of(" #ircafe ", "", "#java")),
                Map.of("id", "oftc", "nick", "tester"),
                Map.of("id", "", "autoJoin", List.of("#ignored")),
                Map.of("id", "empty", "autoJoin", List.of("")))));
  }

  private static IrcProperties.Server server(String id, String backendId) {
    return IrcPropertiesTestFixtures.serverBuilder(id).backendId(backendId).build();
  }
}
