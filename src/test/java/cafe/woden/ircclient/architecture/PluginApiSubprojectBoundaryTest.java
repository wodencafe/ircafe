package cafe.woden.ircclient.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

class PluginApiSubprojectBoundaryTest {

  private static final JavaClasses PLUGIN_API =
      CompiledSubprojects.importProject("ircafe-plugin-api");

  @Test
  void pluginApiClassesDependOnlyOnPluginPortableTypes() {
    CompiledBoundaryRules.pluginApiDependencies(CompiledSubprojects.names(PLUGIN_API))
        .check(PLUGIN_API);
  }

  @Test
  void pluginApiClassesStayInSpiPackages() {
    classes().should().resideInAPackage("cafe.woden.ircclient..spi..").check(PLUGIN_API);
  }
}
