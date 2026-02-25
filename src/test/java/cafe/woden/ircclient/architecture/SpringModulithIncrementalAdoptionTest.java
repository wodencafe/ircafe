package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cafe.woden.ircclient.IrcSwingApp;
import cafe.woden.ircclient.app.JfrRuntimeEventsService;
import cafe.woden.ircclient.app.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.app.RuntimeJfrService;
import cafe.woden.ircclient.app.SpringRuntimeEventsService;
import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.MediatorControlPort;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.FilterCommand;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.app.outbound.LocalFilterCommandHandler;
import cafe.woden.ircclient.diagnostics.JfrSnapshotSummarizer;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.notifications.IrcEventNotificationService;
import cafe.woden.ircclient.perform.PerformOnConnectService;
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

    ApplicationModule performModule = moduleFor(modules, PerformOnConnectService.class);
    assertThat(performModule).isNotEqualTo(appModule);
    assertThat(performModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.perform");

    ApplicationModule diagnosticsSupportModule = moduleFor(modules, JfrSnapshotSummarizer.class);
    assertThat(diagnosticsSupportModule).isNotEqualTo(appModule);
    assertThat(diagnosticsSupportModule.getBasePackage().getName())
        .isEqualTo("cafe.woden.ircclient.diagnostics");

    ApplicationModule monitorModule = moduleFor(modules, MonitorListService.class);
    assertThat(monitorModule).isNotEqualTo(appModule);
    assertThat(monitorModule.getBasePackage().getName()).isEqualTo("cafe.woden.ircclient.monitor");

    ApplicationModule interceptorsModule = moduleFor(modules, InterceptorStore.class);
    assertThat(interceptorsModule).isNotEqualTo(appModule);
    assertThat(interceptorsModule.getBasePackage().getName())
        .isEqualTo("cafe.woden.ircclient.interceptors");

    ApplicationModule notificationsModule = moduleFor(modules, IrcEventNotificationService.class);
    assertThat(notificationsModule).isNotEqualTo(appModule);
    assertThat(notificationsModule.getBasePackage().getName())
        .isEqualTo("cafe.woden.ircclient.notifications");

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
        appModule,
        "api",
        UiPort.class,
        ActiveTargetPort.class,
        MediatorControlPort.class,
        InterceptorIngestPort.class,
        IrcEventNotifierPort.class,
        NotificationRuleMatcherPort.class,
        MonitorFallbackPort.class,
        MonitorRosterPort.class,
        TargetRef.class,
        TrayNotificationsPort.class);
    assertNamedInterfaceContains(
        appModule, "diagnostics", RuntimeJfrService.class, RuntimeDiagnosticEvent.class);
    assertNamedInterfaceContains(
        appModule, "commands", UserCommandAliasesBus.class, FilterCommand.class);
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
