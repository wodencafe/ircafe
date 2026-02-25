package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cafe.woden.ircclient.IrcSwingApp;
import cafe.woden.ircclient.app.JfrRuntimeEventsService;
import cafe.woden.ircclient.app.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.app.RuntimeJfrService;
import cafe.woden.ircclient.app.SpringRuntimeEventsService;
import cafe.woden.ircclient.app.commands.FilterCommand;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.app.interceptors.InterceptorStore;
import cafe.woden.ircclient.app.monitor.MonitorListService;
import cafe.woden.ircclient.app.notifications.IrcEventNotificationRulesBus;
import cafe.woden.ircclient.app.outbound.LocalFilterCommandHandler;
import cafe.woden.ircclient.ui.application.RuntimeEventsPanel;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;

class SpringModulithIncrementalAdoptionTest {

  @Test
  void applicationModulesCanBeDiscovered() {
    assertThatCode(() -> ApplicationModules.of(IrcSwingApp.class)).doesNotThrowAnyException();
  }

  @Test
  void runtimeDiagnosticsTypesResolveToExpectedModules() {
    ApplicationModules modules = ApplicationModules.of(IrcSwingApp.class);

    ApplicationModule appModule = moduleFor(modules, RuntimeJfrService.class);
    assertThat(moduleFor(modules, JfrRuntimeEventsService.class)).isEqualTo(appModule);
    assertThat(moduleFor(modules, SpringRuntimeEventsService.class)).isEqualTo(appModule);
    assertThat(appModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.app");
    assertAppNamedInterfaces(appModule);

    ApplicationModule uiModule = moduleFor(modules, RuntimeEventsPanel.class);
    assertThat(uiModule).isNotEqualTo(appModule);
    assertThat(uiModule.getBasePackage().getName()).startsWith("cafe.woden.ircclient.ui");
  }

  @Test
  void moduleVerificationPassesWithCurrentBoundaries() {
    ApplicationModules.of(IrcSwingApp.class).verify();
  }

  private static ApplicationModule moduleFor(ApplicationModules modules, Class<?> type) {
    return modules
        .getModuleByType(type)
        .orElseThrow(() -> new AssertionError("No module discovered for type " + type.getName()));
  }

  private static void assertAppNamedInterfaces(ApplicationModule appModule) {
    assertNamedInterfaceContains(
        appModule, "diagnostics", RuntimeJfrService.class, RuntimeDiagnosticEvent.class);
    assertNamedInterfaceContains(
        appModule, "commands", UserCommandAliasesBus.class, FilterCommand.class);
    assertNamedInterfaceContains(appModule, "interceptors", InterceptorStore.class);
    assertNamedInterfaceContains(appModule, "monitor", MonitorListService.class);
    assertNamedInterfaceContains(appModule, "notifications", IrcEventNotificationRulesBus.class);
    assertNamedInterfaceContains(appModule, "outbound", LocalFilterCommandHandler.class);
  }

  private static void assertNamedInterfaceContains(
      ApplicationModule appModule, String namedInterface, Class<?>... requiredTypes) {
    NamedInterface diagnosticsInterface =
        appModule
            .getNamedInterfaces()
            .getByName(namedInterface)
            .orElseThrow(
                () -> new AssertionError("Missing app::" + namedInterface + " named interface."));
    for (Class<?> type : requiredTypes) {
      assertThat(diagnosticsInterface.contains(type))
          .as("app::" + namedInterface + " should contain " + type.getSimpleName())
          .isTrue();
    }
  }
}
