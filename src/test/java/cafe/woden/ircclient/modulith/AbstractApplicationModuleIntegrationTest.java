package cafe.woden.ircclient.modulith;

import org.springframework.boot.ApplicationRunner;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Shared headless environment and isolated runtime files; dependency doubles belong to each module
 * test.
 */
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.ui.appDiagnostics.assertjSwing.enabled=false",
      "ircafe.ui.appDiagnostics.assertjSwing.edtFreezeWatchdogEnabled=false",
      "ircafe.ui.appDiagnostics.jhiccup.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
public abstract class AbstractApplicationModuleIntegrationTest {

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }
}
