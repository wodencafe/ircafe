package cafe.woden.ircclient.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

/** Artifact ownership matters here: a root implementation can also live in an SPI package. */
final class CompiledBoundaryRules {

  private CompiledBoundaryRules() {}

  static ArchRule transportIndependentFeatures() {
    return noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.pircbotx..", "java.awt..", "javax.swing..")
        .because("feature policy must remain independent of transports and desktop widgets");
  }

  static ArchRule featurePackages(String... packages) {
    return classes()
        .should()
        .resideInAnyPackage(packages)
        .because("feature artifacts must stay within their existing Modulith boundaries");
  }

  static ArchRule jdkOnly(String className) {
    return classes()
        .that()
        .haveNameMatching(java.util.regex.Pattern.quote(className) + "(\\$.*)?")
        .should()
        .onlyDependOnClassesThat(
            new DescribedPredicate<>("own types or JDK types") {
              @Override
              public boolean test(JavaClass type) {
                JavaClass component = type.getBaseComponentType();
                String name = component.getName();
                return component.isPrimitive()
                    || name.startsWith("java.")
                    || name.equals(className)
                    || name.startsWith(className + "$");
              }
            });
  }

  static ArchRule mustNotBypassRuntimeProviders(
      DescribedPredicate<JavaClass> consumers, Set<String> featureTypes) {
    return noClasses()
        .that(consumers)
        .should()
        .dependOnClassesThat(
            new DescribedPredicate<>("feature implementation types") {
              @Override
              public boolean test(JavaClass type) {
                return featureTypes.contains(type.getBaseComponentType().getName());
              }
            })
        .because("runtime adapters must resolve protocol policy through installed providers");
  }

  static ArchRule pluginApiDependencies(Set<String> apiClassNames) {
    return classes()
        .should()
        .onlyDependOnClassesThat(
            new DescribedPredicate<>("plugin API classes or portable JDK types") {
              @Override
              public boolean test(JavaClass type) {
                JavaClass component = type.getBaseComponentType();
                String name = component.getName();
                return component.isPrimitive()
                    || apiClassNames.contains(name)
                    || (name.startsWith("java.")
                        && !name.startsWith("java.awt.")
                        && !name.startsWith("java.beans.")
                        && !name.startsWith("java.applet."))
                    || name.startsWith("javax.annotation.");
              }
            })
        .because("the published plugin API must remain independent of the host and desktop UI");
  }

  static ArchRule featureDependencies(Set<String> featureAndApiClassNames) {
    return classes()
        .should()
        .onlyDependOnClassesThat(
            new DescribedPredicate<>("feature/plugin API classes or external dependencies") {
              @Override
              public boolean test(JavaClass type) {
                String name = type.getBaseComponentType().getName();
                return !name.startsWith("cafe.woden.ircclient.")
                    || featureAndApiClassNames.contains(name);
              }
            })
        .because("features must not depend on host implementation classes, including host SPIs");
  }
}
