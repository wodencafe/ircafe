package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class ConfigModuleIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  private final RuntimeConfigStore runtimeConfigStore;
  private final IrcProperties ircProperties;
  private final ServerRegistry serverRegistry;
  private final ServerCatalog serverCatalog;

  ConfigModuleIntegrationTest(
      RuntimeConfigStore runtimeConfigStore,
      IrcProperties ircProperties,
      ServerRegistry serverRegistry,
      ServerCatalog serverCatalog) {
    this.runtimeConfigStore = runtimeConfigStore;
    this.ircProperties = ircProperties;
    this.serverRegistry = serverRegistry;
    this.serverCatalog = serverCatalog;
  }

  @Test
  void bootstrapsRuntimeConfigInTestScopedLocation() {
    Path runtimeConfigPath = runtimeConfigStore.runtimeConfigPath().toAbsolutePath().normalize();

    assertTrue(Files.exists(runtimeConfigPath), "runtime config file should be created");
    assertTrue(
        runtimeConfigPath.toString().contains("build/tmp/modulith-tests"),
        "runtime config should stay under build/tmp for tests");
    assertFalse(
        runtimeConfigStore.runtimeConfigFileExistedOnStartup(),
        "test runtime config should be created by the store");
  }

  @Test
  void exposesConfiguredServersThroughRegistryAndCatalog() {
    assertNotNull(ircProperties.servers(), "server config should be bound");
    assertFalse(ircProperties.servers().isEmpty(), "application defaults should provide a server");

    String firstServerId = ircProperties.servers().getFirst().id();
    assertTrue(serverRegistry.containsId(firstServerId), "registry should contain bound server");
    assertTrue(serverCatalog.containsId(firstServerId), "catalog should contain bound server");
  }
}
