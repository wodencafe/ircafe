package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.filter.FilterEngine;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.shell.MainFrame;
import cafe.woden.ircclient.ui.terminal.TerminalDockable;
import cafe.woden.ircclient.ui.tray.TrayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "spring.main.lazy-initialization=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.ui.appDiagnostics.assertjSwing.enabled=false",
      "ircafe.ui.appDiagnostics.assertjSwing.edtFreezeWatchdogEnabled=false",
      "ircafe.ui.appDiagnostics.jhiccup.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
class UiModuleIntegrationTest {

  private final ConfigurableApplicationContext applicationContext;

  UiModuleIntegrationTest(ConfigurableApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @Test
  void exposesCoreUiBeanDefinitionsWithoutEagerInitialization() {
    ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();

    assertEquals(1, beanFactory.getBeanNamesForType(SwingUiPort.class, true, false).length);
    assertEquals(1, beanFactory.getBeanNamesForType(SwingUiEventAdapter.class, true, false).length);
    assertEquals(1, beanFactory.getBeanNamesForType(MainFrame.class, true, false).length);
    assertEquals(1, beanFactory.getBeanNamesForType(ChatDockManager.class, true, false).length);
    assertEquals(1, beanFactory.getBeanNamesForType(FilterEngine.class, true, false).length);
    assertEquals(1, beanFactory.getBeanNamesForType(ThemeManager.class, true, false).length);
    assertEquals(1, beanFactory.getBeanNamesForType(TerminalDockable.class, true, false).length);
    assertEquals(1, beanFactory.getBeanNamesForType(TrayService.class, true, false).length);
  }

  @Test
  void uiModulePublishesExpectedBeanDefinitions() {
    String[] uiPortBeans = applicationContext.getBeanNamesForType(SwingUiPort.class, true, false);
    assertEquals("swingUiPort", uiPortBeans[0]);

    String[] uiEventPortBeans =
        applicationContext.getBeanNamesForType(SwingUiEventAdapter.class, true, false);
    assertEquals("swingUiEventPort", uiEventPortBeans[0]);

    String[] mainFrameBeans = applicationContext.getBeanNamesForType(MainFrame.class, true, false);
    assertTrue(mainFrameBeans.length == 1 && !mainFrameBeans[0].isBlank());
  }
}
