package cafe.woden.ircclient.bouncer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
class BouncerModuleIntegrationTest {

  @MockitoBean BouncerConnectionPort bouncerConnectionPort;

  private final ApplicationContext applicationContext;
  private final BouncerBackendRegistry bouncerBackendRegistry;
  private final BouncerDiscoveryEventPort bouncerDiscoveryEventPort;
  private final GenericBouncerAutoConnectStore genericBouncerAutoConnectStore;
  private final GenericBouncerEphemeralNetworkImporter genericBouncerEphemeralNetworkImporter;

  BouncerModuleIntegrationTest(
      ApplicationContext applicationContext,
      BouncerBackendRegistry bouncerBackendRegistry,
      BouncerDiscoveryEventPort bouncerDiscoveryEventPort,
      GenericBouncerAutoConnectStore genericBouncerAutoConnectStore,
      GenericBouncerEphemeralNetworkImporter genericBouncerEphemeralNetworkImporter) {
    this.applicationContext = applicationContext;
    this.bouncerBackendRegistry = bouncerBackendRegistry;
    this.bouncerDiscoveryEventPort = bouncerDiscoveryEventPort;
    this.genericBouncerAutoConnectStore = genericBouncerAutoConnectStore;
    this.genericBouncerEphemeralNetworkImporter = genericBouncerEphemeralNetworkImporter;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @Test
  void exposesBouncerModuleBeans() {
    assertEquals(1, applicationContext.getBeansOfType(BouncerBackendRegistry.class).size());
    assertEquals(1, applicationContext.getBeansOfType(BouncerDiscoveryEventPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(GenericBouncerAutoConnectStore.class).size());
    assertEquals(
        1, applicationContext.getBeansOfType(GenericBouncerEphemeralNetworkImporter.class).size());
    assertNotNull(bouncerBackendRegistry);
    assertNotNull(bouncerDiscoveryEventPort);
    assertNotNull(genericBouncerAutoConnectStore);
    assertNotNull(genericBouncerEphemeralNetworkImporter);
  }

  @Test
  void backendRegistryContainsGenericBackendDescriptor() {
    assertTrue(bouncerBackendRegistry.backendIds().contains("generic"));
    assertTrue(bouncerBackendRegistry.find("GENERIC").isPresent());
  }

  @Test
  void unknownBouncerOriginDiscoveryDoesNotTriggerConnectionRequests() {
    bouncerDiscoveryEventPort.onNetworkDiscovered(
        new BouncerDiscoveredNetwork(
            "generic", "unknown-origin", "network-1", "Network 1", "Network 1", Map.of()));

    verifyNoInteractions(bouncerConnectionPort);
  }
}
