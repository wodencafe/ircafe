package cafe.woden.ircclient.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import cafe.woden.ircclient.irc.PircbotxIrcClientService;
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
              "cafe.woden.ircclient.app.state..",
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
              "cafe.woden.ircclient.app.state..",
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
          .resideInAPackage("cafe.woden.ircclient.app.state..")
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
              "cafe.woden.ircclient.app.state..",
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
              "cafe.woden.ircclient.app.state..",
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
}
