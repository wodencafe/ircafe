package cafe.woden.ircclient.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
class ModelModuleIntegrationTest {

  private final ApplicationContext applicationContext;
  private final AwayStatusStore awayStatusStore;

  ModelModuleIntegrationTest(ApplicationContext applicationContext, AwayStatusStore awayStatusStore) {
    this.applicationContext = applicationContext;
    this.awayStatusStore = awayStatusStore;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @Test
  void exposesModelBeans() {
    assertEquals(1, applicationContext.getBeansOfType(AwayStatusStore.class).size());
    assertNotNull(awayStatusStore);
  }

  @Test
  void awayStatusStoreSupportsBasicPutGetAndClearFlow() {
    String serverId = "libera";
    String nick = "Alice";
    Instant at = Instant.now();

    assertFalse(awayStatusStore.isAway(serverId, nick));

    boolean changed = awayStatusStore.put(serverId, nick, true, "brb", at);
    assertTrue(changed);
    assertTrue(awayStatusStore.isAway(serverId, nick));
    assertTrue(awayStatusStore.get(serverId, nick).isPresent());
    assertEquals("brb", awayStatusStore.get(serverId, nick).orElseThrow().message());

    assertTrue(awayStatusStore.clear(serverId, nick));
    assertFalse(awayStatusStore.get(serverId, nick).isPresent());
  }
}
