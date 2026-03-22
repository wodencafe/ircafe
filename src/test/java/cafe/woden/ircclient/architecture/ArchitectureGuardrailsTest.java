package cafe.woden.ircclient.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import cafe.woden.ircclient.bouncer.BouncerBackendDiscoveryHandler;
import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.pircbotx.PircbotxIrcClientService;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "cafe.woden.ircclient",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureGuardrailsTest {

  private static final DescribedPredicate<JavaClass> IGNORE_INTERNAL_CLASSES =
      new DescribedPredicate<>("ignore internal classes (outside ignore::api)") {
        @Override
        public boolean test(JavaClass input) {
          String pkg = input.getPackageName();
          if (!pkg.startsWith("cafe.woden.ircclient.ignore")) return false;
          return !pkg.startsWith("cafe.woden.ircclient.ignore.api");
        }
      };

  private static final DescribedPredicate<JavaClass> STATE_INTERNAL_CLASSES =
      new DescribedPredicate<>("state internal classes (outside state::api)") {
        @Override
        public boolean test(JavaClass input) {
          String pkg = input.getPackageName();
          if (!pkg.startsWith("cafe.woden.ircclient.state")) return false;
          return !pkg.startsWith("cafe.woden.ircclient.state.api");
        }
      };

  private static final DescribedPredicate<JavaClass> RUNTIME_CONFIG_STORE_TYPES =
      new DescribedPredicate<>("RuntimeConfigStore types") {
        @Override
        public boolean test(JavaClass input) {
          String name = input.getName();
          return name.equals("cafe.woden.ircclient.config.RuntimeConfigStore")
              || name.startsWith("cafe.woden.ircclient.config.RuntimeConfigStore$");
        }
      };

  private static final DescribedPredicate<JavaClass> BOUNCER_INTERNAL_TYPES =
      new DescribedPredicate<>("bouncer internal types") {
        @Override
        public boolean test(JavaClass input) {
          String name = input.getName();
          return name.equals("cafe.woden.ircclient.bouncer.AbstractBouncerAutoConnectStore")
              || name.startsWith("cafe.woden.ircclient.bouncer.AbstractBouncerAutoConnectStore$")
              || name.equals("cafe.woden.ircclient.bouncer.BouncerNetworkDiscoveryOrchestrator")
              || name.startsWith(
                  "cafe.woden.ircclient.bouncer.BouncerNetworkDiscoveryOrchestrator$")
              || name.equals("cafe.woden.ircclient.bouncer.ResolvedBouncerNetwork")
              || name.startsWith("cafe.woden.ircclient.bouncer.ResolvedBouncerNetwork$");
        }
      };

  private static final DescribedPredicate<JavaClass> IRC_PROTOCOL_PARSER_TYPES =
      new DescribedPredicate<>("irc protocol parser types") {
        @Override
        public boolean test(JavaClass input) {
          String name = input.getName();
          return name.equals("cafe.woden.ircclient.irc.pircbotx.parse.PircbotxZncParsers")
              || name.startsWith("cafe.woden.ircclient.irc.pircbotx.parse.PircbotxZncParsers$")
              || name.equals("cafe.woden.ircclient.irc.soju.PircbotxSojuParsers")
              || name.startsWith("cafe.woden.ircclient.irc.soju.PircbotxSojuParsers$");
        }
      };

  @ArchTest
  static final ArchRule app_should_not_depend_on_ui_package_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.ui..")
          .because(
              "application code should use app-level ports and abstractions, not concrete Swing/UI types");

  @ArchTest
  static final ArchRule app_should_not_depend_on_logging_package_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.logging..")
          .because("application code should use app-owned ports, not logging module internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_interceptors_module_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.interceptors..")
          .because(
              "application code should depend on app::api interceptor ports, not interceptor module internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_notifications_module_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.notifications..")
          .because(
              "application code should depend on app::api notification ports, not notification module internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_ignore_module_internals_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat(IGNORE_INTERNAL_CLASSES)
          .because("application code should only depend on ignore::api, not ignore internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_state_module_internals_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat(STATE_INTERNAL_CLASSES)
          .because("application code should only depend on state::api, not state internals");

  @ArchTest
  static final ArchRule non_ui_modules_should_not_depend_on_ignore_module_internals =
      noClasses()
          .that()
          .resideOutsideOfPackages("cafe.woden.ircclient.ignore..", "cafe.woden.ircclient.ui..")
          .should()
          .dependOnClassesThat(IGNORE_INTERNAL_CLASSES)
          .because(
              "non-UI modules should depend only on ignore::api and remain decoupled from ignore internals");

  @ArchTest
  static final ArchRule ignore_api_should_remain_dependency_light =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ignore.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.state..",
              "cafe.woden.ircclient.irc..",
              "cafe.woden.ircclient.logging..")
          .because("ignore::api should stay stable and free from app, UI, and transport internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_diagnostics_module_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.diagnostics..")
          .because(
              "application code should not couple directly to diagnostics internals and should use app-level seams");

  @ArchTest
  static final ArchRule logging_should_not_depend_on_ui_package_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.logging..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.ui..")
          .because("logging should stay UI-agnostic and communicate through app/logging ports");

  @ArchTest
  static final ArchRule logging_should_not_depend_on_app_internal_or_feature_modules =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.logging..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.state..",
              "cafe.woden.ircclient.app.util..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..",
              "cafe.woden.ircclient.perform..",
              "cafe.woden.ircclient.diagnostics..")
          .because(
              "logging module should expose adapters through app::api and avoid coupling to app internals or peer feature modules");

  @ArchTest
  static final ArchRule app_should_not_depend_on_pircbotx_service_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .areAssignableTo(PircbotxIrcClientService.class)
          .because(
              "application code should depend on IrcClientService, not transport-specific adapters");

  @ArchTest
  static final ArchRule non_irc_modules_should_not_depend_on_matrix_transport_internals =
      noClasses()
          .that()
          .resideOutsideOfPackage("cafe.woden.ircclient.irc..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.irc.matrix..")
          .because(
              "matrix transport internals should stay behind the irc module boundary and be accessed via irc ports");

  @ArchTest
  static final ArchRule non_app_modules_should_not_depend_on_app_core_directly =
      noClasses()
          .that()
          .resideOutsideOfPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.app.core..")
          .because("app.core is internal orchestration and should only be used from within app.");

  @ArchTest
  static final ArchRule perform_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.perform..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..")
          .because(
              "perform module should integrate through app api/commands ports and remain decoupled from UI and app internals");

  @ArchTest
  static final ArchRule monitor_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.monitor..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..")
          .because(
              "monitor module should integrate via app::api plus config/irc without coupling to UI or app internals");

  @ArchTest
  static final ArchRule interceptors_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.interceptors..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..")
          .because(
              "interceptor module should integrate via app::api and remain decoupled from app internals and UI/logging");

  @ArchTest
  static final ArchRule notifications_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.notifications..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.interceptors..")
          .because(
              "notification module should integrate via app::api and remain decoupled from UI and app internals");

  @ArchTest
  static final ArchRule state_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.state..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..")
          .because(
              "state module should remain a reusable correlation/state holder and integrate through app::api plus config");

  @ArchTest
  static final ArchRule state_api_should_not_depend_on_state_implementations =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.state.api..")
          .should()
          .dependOnClassesThat(STATE_INTERNAL_CLASSES)
          .because(
              "state::api should stay implementation-agnostic and independent from state internals");

  @ArchTest
  static final ArchRule app_outbound_should_not_depend_on_config_module_internals_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app.outbound..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "app outbound flows should depend on config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule app_commands_should_not_depend_on_config_module_internals_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app.commands..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "app command flows should depend on config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule only_quassel_outbound_service_should_depend_on_quassel_core_control_port =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app.outbound..")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.QuasselOutboundCommandService")
          .should()
          .dependOnClassesThat()
          .areAssignableTo(QuasselCoreControlPort.class)
          .because(
              "backend-specific Quassel transport control should stay isolated in QuasselOutboundCommandService");

  @ArchTest
  static final ArchRule only_matrix_upload_services_should_depend_on_upload_translation_handlers =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app.outbound..")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.MatrixOutboundCommandService")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.BackendUploadCommandRegistry")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.MatrixUploadCommandTranslationHandler")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.UploadCommandTranslationHandler")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.UploadCommandTranslationHandler")
          .because(
              "semantic /upload backend translation should stay behind dedicated matrix upload services");

  @ArchTest
  static final ArchRule
      only_matrix_upload_services_should_depend_on_matrix_outbound_command_support =
          noClasses()
              .that()
              .resideInAPackage("cafe.woden.ircclient.app.outbound..")
              .and()
              .doNotHaveFullyQualifiedName(
                  "cafe.woden.ircclient.app.outbound.backend.MatrixOutboundCommandSupport")
              .and()
              .doNotHaveFullyQualifiedName(
                  "cafe.woden.ircclient.app.outbound.backend.MatrixOutboundCommandService")
              .and()
              .doNotHaveFullyQualifiedName(
                  "cafe.woden.ircclient.app.outbound.backend.MatrixUploadCommandTranslationHandler")
              .should()
              .dependOnClassesThat()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.app.outbound.backend.MatrixOutboundCommandSupport")
              .because(
                  "matrix upload payload shaping should remain isolated to dedicated matrix outbound services");

  @ArchTest
  static final ArchRule ui_should_only_access_backend_mode_port_through_ui_backend_profile_types =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui..")
          .and()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.ui.backend.BackendUiProfile")
          .and()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.ui.backend.BackendUiContext")
          .and()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.ui.backend.BackendUiProfileProvider")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("cafe.woden.ircclient.irc.backend.IrcBackendModePort")
          .because(
              "backend mode checks in UI should stay centralized behind backend-ui profile/context services");

  @ArchTest
  static final ArchRule state_should_not_depend_on_config_module_internals_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.state..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because("state should depend on config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule connection_coordinator_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .haveFullyQualifiedName("cafe.woden.ircclient.app.core.ConnectionCoordinator")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ConnectionCoordinator should depend on config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule app_core_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app.core..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because("app.core should depend on config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ircv3_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.ircv3..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ircv3 support should persist policy state via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule bouncer_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.bouncer..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "bouncer discovery support should persist settings via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule soju_support_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.soju..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "soju discovery support should persist settings via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule znc_support_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.znc..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "znc discovery support should persist settings via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule pircbotx_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.pircbotx..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "pircbotx transport support should read runtime settings via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule monitor_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.monitor..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "monitor support should persist roster state via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ignore_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ignore..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ignore support should persist rule state via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule diagnostics_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.diagnostics..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "diagnostics support should persist/export config state via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule interceptors_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.interceptors..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "interceptor support should resolve runtime paths via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule notify_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.notify..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "notification support should resolve runtime paths via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule logging_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.logging..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "logging support should resolve runtime paths via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ui_shell_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.shell..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ui.shell should persist UI chrome state via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ui_tray_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.tray..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ui.tray should persist tray state via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ui_servers_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.servers..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ui.servers should persist startup auto-connect state via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ui_filter_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.filter..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ui.filter should persist local transcript filter settings via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ui_nickcolors_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.nickcolors..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ui.nickcolors should persist per-nick override settings via config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ui_settings_bus_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .haveFullyQualifiedName("cafe.woden.ircclient.ui.settings.UiSettingsBus")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "UiSettingsBus should derive persisted UI defaults through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule theme_selection_dialog_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .haveFullyQualifiedName("cafe.woden.ircclient.ui.settings.ThemeSelectionDialog")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ThemeSelectionDialog should persist theme settings through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      chat_history_transcript_port_adapter_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.ui.chat.ChatHistoryTranscriptPortAdapter")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "ChatHistoryTranscriptPortAdapter should resolve persisted chat history UI settings through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ui_chat_embed_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.chat.embed..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "ui.chat.embed should resolve embed policy state through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      embed_load_policy_dialog_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName("cafe.woden.ircclient.ui.settings.EmbedLoadPolicyDialog")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "EmbedLoadPolicyDialog should edit embed policy state through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      server_tree_startup_selection_restorer_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.ui.servertree.policy.ServerTreeStartupSelectionRestorer")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "ServerTreeStartupSelectionRestorer should read remembered UI selection through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      server_tree_built_in_visibility_coordinator_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilityCoordinator")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "ServerTreeBuiltInVisibilityCoordinator should persist built-in visibility through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      server_tree_settings_synchronizer_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.ui.servertree.state.ServerTreeSettingsSynchronizer")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "ServerTreeSettingsSynchronizer should resolve persisted UI settings through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      server_tree_network_info_dialog_builder_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.ui.servertree.view.ServerTreeNetworkInfoDialogBuilder")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "ServerTreeNetworkInfoDialogBuilder should resolve requested capability settings through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      server_tree_target_removal_state_coordinator_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "ServerTreeTargetRemovalStateCoordinator should remove persisted target state through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      server_tree_channel_state_coordinator_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "ServerTreeChannelStateCoordinator should persist channel state through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      server_tree_view_interaction_collaborators_factory_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.ui.servertree.composition.ServerTreeViewInteractionCollaboratorsFactory")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "ServerTreeViewInteractionCollaboratorsFactory should resolve server auto-connect state through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule
      server_tree_state_interaction_collaborators_factory_should_not_depend_on_runtime_config_store_directly =
          noClasses()
              .that()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaboratorsFactory")
              .should()
              .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
              .because(
                  "ServerTreeStateInteractionCollaboratorsFactory should wire server-tree collaborators through config::api ports, not RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule server_tree_types_should_not_depend_on_runtime_config_store_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.servertree..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because(
              "server-tree collaborators should resolve persisted state through config::api ports instead of RuntimeConfigStore directly");

  @ArchTest
  static final ArchRule ui_ignore_should_not_depend_on_app_internal_or_irc_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.ignore..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.state..",
              "cafe.woden.ircclient.irc..")
          .because(
              "ignore UI components should stay presentation-focused and avoid coupling to app internals or IRC transport details");

  @ArchTest
  static final ArchRule dcc_should_not_depend_on_ui_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.dcc..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.app..",
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..",
              "cafe.woden.ircclient.perform..")
          .because(
              "dcc transfer state should remain shared domain/application state and avoid app-internal or UI coupling");

  @ArchTest
  static final ArchRule diagnostics_should_not_depend_on_app_ui_or_logging_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.diagnostics..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.app",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.state..",
              "cafe.woden.ircclient.app.util..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..",
              "cafe.woden.ircclient.perform..",
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..")
          .because(
              "diagnostics support should stay independent from app internals while integrating only via app::api plus config/model/util/notify seams");

  @ArchTest
  static final ArchRule bouncer_should_not_depend_on_irc_package_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.bouncer..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.irc..")
          .because(
              "bouncer support should remain transport-agnostic and use BouncerConnectionPort for connect operations");

  @ArchTest
  static final ArchRule only_irc_module_should_implement_bouncer_connection_port =
      noClasses()
          .that()
          .resideOutsideOfPackage("cafe.woden.ircclient.irc..")
          .should()
          .implement(BouncerConnectionPort.class)
          .because(
              "BouncerConnectionPort adapters should stay in the IRC transport module to keep infrastructure ownership explicit");

  @ArchTest
  static final ArchRule only_bouncer_module_should_implement_bouncer_discovery_event_port =
      noClasses()
          .that()
          .resideOutsideOfPackage("cafe.woden.ircclient.bouncer..")
          .should()
          .implement(BouncerDiscoveryEventPort.class)
          .because(
              "BouncerDiscoveryEventPort dispatch should stay in bouncer module so backend routing is centralized");

  @ArchTest
  static final ArchRule only_bouncer_or_irc_modules_should_implement_bouncer_backend_handler =
      noClasses()
          .that()
          .resideOutsideOfPackages("cafe.woden.ircclient.bouncer..", "cafe.woden.ircclient.irc..")
          .should()
          .implement(BouncerBackendDiscoveryHandler.class)
          .because(
              "backend discovery handlers should live in bouncer or irc modules, not app/ui/features");

  @ArchTest
  static final ArchRule only_bouncer_or_irc_modules_should_implement_mapping_strategy =
      noClasses()
          .that()
          .resideOutsideOfPackages("cafe.woden.ircclient.bouncer..", "cafe.woden.ircclient.irc..")
          .should()
          .implement(BouncerNetworkMappingStrategy.class)
          .because(
              "bouncer network mapping strategies belong to bouncer core or irc backend adapters");

  @ArchTest
  static final ArchRule bouncer_should_not_depend_on_irc_protocol_parsers =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.bouncer..")
          .should()
          .dependOnClassesThat(IRC_PROTOCOL_PARSER_TYPES)
          .because(
              "bouncer core should stay parser-agnostic and receive normalized discovery events only");

  @ArchTest
  static final ArchRule only_bouncer_and_backend_adapters_should_depend_on_bouncer_internal_types =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "cafe.woden.ircclient.bouncer..",
              "cafe.woden.ircclient.irc.soju..",
              "cafe.woden.ircclient.irc.znc..")
          .should()
          .dependOnClassesThat(BOUNCER_INTERNAL_TYPES)
          .because(
              "bouncer internals should remain implementation details used only by bouncer core and backend-specific IRC adapters");

  @ArchTest
  static final ArchRule only_virtual_threads_factory_should_depend_on_executors =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.util.VirtualThreads")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.concurrent.Executors")
          .because(
              "raw Executors factories should stay centralized in VirtualThreads so executor ownership, naming, and shutdown policies remain consistent");

  @ArchTest
  static final ArchRule only_virtual_threads_factory_should_call_thread_of_virtual =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.util.VirtualThreads")
          .should()
          .callMethod(Thread.class, "ofVirtual")
          .because(
              "virtual-thread creation should stay centralized in VirtualThreads to keep thread naming and policy consistent");

  @ArchTest
  static final ArchRule app_should_not_construct_threads_directly =
      noClasses()
          .should()
          .callConstructorWhere(target(owner(assignableTo(Thread.class))))
          .because(
              "direct new Thread(...) creation should be avoided in favor of VirtualThreads helpers");

  @ArchTest
  static final ArchRule app_should_not_use_rxjava_default_io_scheduler =
      noClasses()
          .should()
          .callMethod(io.reactivex.rxjava3.schedulers.Schedulers.class, "io")
          .because(
              "RxJava Schedulers.io() uses the default platform-thread pool; use RxVirtualSchedulers.io()");

  @ArchTest
  static final ArchRule app_should_not_use_completable_future_common_pool_overloads =
      noClasses()
          .should()
          .callMethod(
              java.util.concurrent.CompletableFuture.class,
              "supplyAsync",
              java.util.function.Supplier.class)
          .orShould()
          .callMethod(java.util.concurrent.CompletableFuture.class, "runAsync", Runnable.class)
          .because(
              "CompletableFuture common-pool overloads bypass virtual-thread executors; pass an explicit VirtualThreads-backed executor");

  @ArchTest
  static final ArchRule
      non_ui_packages_should_not_depend_on_ui_input_servertree_or_coordinator_subpackages =
          noClasses()
              .that()
              .resideOutsideOfPackage("cafe.woden.ircclient.ui..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "cafe.woden.ircclient.ui.input..",
                  "cafe.woden.ircclient.ui.servertree..",
                  "cafe.woden.ircclient.ui.coordinator..",
                  "cafe.woden.ircclient.ui.bus..",
                  "cafe.woden.ircclient.ui.controls..")
              .because(
                  "ui internals (input, server-tree, coordinator, bus, controls) should remain behind the top-level ui adapter boundary");

  @ArchTest
  static final ArchRule ui_input_should_not_depend_on_servertree_subpackage =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.input..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.ui.servertree..")
          .because(
              "message-input internals and server-tree internals should stay decoupled and coordinate via higher-level UI services");

  @ArchTest
  static final ArchRule ui_servertree_should_not_depend_on_input_subpackage =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.servertree..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.ui.input..")
          .because(
              "server-tree internals should not depend on message-input internals; interactions belong in UI coordinators");
}
