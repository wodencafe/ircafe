package cafe.woden.ircclient.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "cafe.woden.ircclient",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureGuardrailsTest {

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
  static final ArchRule logging_should_not_depend_on_ui_package_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.logging..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.ui..")
          .because("logging should stay UI-agnostic and communicate through app/logging ports");

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
          .resideOutsideOfPackages("cafe.woden.ircclient.app..", "cafe.woden.ircclient")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.app.core..")
          .because("app.core is internal orchestration and should only be used from within app.");
}
