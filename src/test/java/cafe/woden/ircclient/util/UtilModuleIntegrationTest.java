package cafe.woden.ircclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
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
class UtilModuleIntegrationTest {

  private final ApplicationContext applicationContext;
  private final VirtualThreadsLifecycle virtualThreadsLifecycle;
  private final RxVirtualSchedulersLifecycle rxVirtualSchedulersLifecycle;

  UtilModuleIntegrationTest(
      ApplicationContext applicationContext,
      VirtualThreadsLifecycle virtualThreadsLifecycle,
      RxVirtualSchedulersLifecycle rxVirtualSchedulersLifecycle) {
    this.applicationContext = applicationContext;
    this.virtualThreadsLifecycle = virtualThreadsLifecycle;
    this.rxVirtualSchedulersLifecycle = rxVirtualSchedulersLifecycle;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @AfterEach
  void cleanup() {
    VirtualThreads.shutdownTrackedExecutorsNow();
    RxVirtualSchedulers.shutdown();
  }

  @Test
  void exposesUtilityLifecycleBeans() {
    assertEquals(1, applicationContext.getBeansOfType(VirtualThreadsLifecycle.class).size());
    assertEquals(1, applicationContext.getBeansOfType(RxVirtualSchedulersLifecycle.class).size());
    assertNotNull(virtualThreadsLifecycle);
    assertNotNull(rxVirtualSchedulersLifecycle);
  }

  @Test
  void virtualThreadExecutorCanBeCreatedFromUtilityModule() {
    ExecutorService exec = VirtualThreads.newSingleThreadExecutor("util-module-test");
    assertNotNull(exec);
    assertFalse(exec.isShutdown());
  }
}
