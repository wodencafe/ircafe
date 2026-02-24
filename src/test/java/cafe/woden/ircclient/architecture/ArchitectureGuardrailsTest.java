package cafe.woden.ircclient.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(
    packages = "cafe.woden.ircclient",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureGuardrailsTest {

  @ArchTest
  static final ArchRule app_should_not_depend_on_ui_package_directly =
      FreezingArchRule.freeze(
          noClasses()
              .that()
              .resideInAPackage("cafe.woden.ircclient.app..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage("cafe.woden.ircclient.ui..")
              .because(
                  "application code should use app-level ports and abstractions, not concrete Swing/UI types"));

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
}
