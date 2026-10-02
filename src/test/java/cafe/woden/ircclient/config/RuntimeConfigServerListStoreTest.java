package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.server.RuntimeConfigServerListStore;
import cafe.woden.ircclient.config.yaml.RuntimeConfigDocumentStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;

class RuntimeConfigServerListStoreTest {

  @TempDir Path tempDir;

  @Test
  void seedsDefaultServersWhenServersKeyIsMissing() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigServerListStore store =
        serverListStore(
            cfg,
            IrcPropertiesTestFixtures.properties(
                IrcPropertiesTestFixtures.serverBuilder("libera")
                    .host("irc.libera.chat")
                    .autoJoin(List.of("#ircafe"))
                    .build()));

    store.ensureFileExistsWithServers();

    assertTrue(Files.exists(cfg));
    assertEquals(List.of("libera"), store.readServerIds());
    assertEquals(Map.of("libera", List.of("#ircafe")), store.readExplicitServerAutoJoinById());
  }

  @Test
  void readsOnlyExplicitAutoJoinEntries() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(
        cfg,
        """
        irc:
          servers:
            - id: libera
              autoJoin:
                - "#runtime"
            - id: oftc
              nick: test
        """);
    RuntimeConfigServerListStore store =
        serverListStore(cfg, IrcPropertiesTestFixtures.properties());

    assertEquals(Map.of("libera", List.of("#runtime")), store.readExplicitServerAutoJoinById());
  }

  @Test
  void certificateOptOutSurvivesSaveReloadAndDisabling() {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigServerListStore store =
        serverListStore(cfg, IrcPropertiesTestFixtures.properties());
    store.writeServers(
        List.of(
            IrcPropertiesTestFixtures.serverBuilder("znc").trustAllCertificates(true).build(),
            IrcPropertiesTestFixtures.server("libera")));

    IrcProperties saved = readProperties(cfg);
    assertTrue(saved.servers().getFirst().trustAllCertificates());
    assertFalse(saved.servers().get(1).trustAllCertificates());

    store.writeServers(List.of(IrcPropertiesTestFixtures.server("znc")));
    assertFalse(readProperties(cfg).servers().getFirst().trustAllCertificates());
  }

  private static IrcProperties readProperties(Path cfg) {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new FileSystemResource(cfg));
    return new Binder(new MapConfigurationPropertySource(yaml.getObject()))
        .bind("irc", IrcProperties.class)
        .orElseThrow(IllegalStateException::new);
  }

  @Test
  void writeServersReplacesConfiguredServerIds() {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigServerListStore store =
        serverListStore(cfg, IrcPropertiesTestFixtures.properties());

    store.writeServers(
        List.of(
            IrcPropertiesTestFixtures.server("libera"), IrcPropertiesTestFixtures.server("oftc")));

    assertEquals(List.of("libera", "oftc"), store.readServerIds());
  }

  private static RuntimeConfigServerListStore serverListStore(Path cfg, IrcProperties defaults) {
    return new RuntimeConfigServerListStore(cfg, new RuntimeConfigDocumentStore(cfg), defaults);
  }
}
