package cafe.woden.ircclient.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.annotation.Annotation;
import org.jmolecules.architecture.hexagonal.Adapter;
import org.jmolecules.architecture.hexagonal.Application;
import org.jmolecules.architecture.hexagonal.Port;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.jmolecules.architecture.layered.DomainLayer;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.jmolecules.architecture.layered.InterfaceLayer;

/**
 * Enforces the roles already adopted by the application, including package and meta annotations.
 *
 * <p>Keep these rules explicit: adding jmolecules-archunit to the shared test classpath also
 * enables Modulith's automatic stock layering checks, whose infrastructure-below-domain direction
 * conflicts with our dependency-inverted adapters.
 */
@AnalyzeClasses(
    packages = "cafe.woden.ircclient",
    importOptions = ImportOption.DoNotIncludeTests.class)
class JmoleculesDependencyRulesTest {

  // The stock layered rule places infrastructure below domain; our domain must stay independent.
  @ArchTest
  static final ArchRule domain_types_must_not_depend_on_outer_layers =
      noClasses()
          .that(hasRole(DomainLayer.class))
          .should()
          .dependOnClassesThat(
              hasRole(ApplicationLayer.class)
                  .or(hasRole(InterfaceLayer.class))
                  .or(hasRole(InfrastructureLayer.class)))
          .because("domain types must remain independent of application, UI, and infrastructure");

  @ArchTest
  static final ArchRule ports_and_applications_must_not_depend_on_adapters =
      noClasses()
          .that(hasRole(Port.class).or(hasRole(Application.class)))
          .should()
          .dependOnClassesThat(hasRole(Adapter.class))
          .because("application and port contracts must not know their concrete adapters");

  @ArchTest
  static final ArchRule irc_secondary_adapters_must_not_depend_on_primary_roles =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.adapter..")
          .and(hasRole(SecondaryAdapter.class))
          .should()
          .dependOnClassesThat(hasRole(PrimaryAdapter.class).or(hasRole(PrimaryPort.class)))
          .because("outbound transport adapters must not depend on inbound UI or control roles");

  @ArchTest
  static final ArchRule irc_port_interfaces_must_declare_their_secondary_role =
      classes()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.port..")
          .and()
          .areInterfaces()
          .and()
          .doNotHaveSimpleName("package-info")
          .should()
          .beAnnotatedWith(SecondaryPort.class);

  @ArchTest
  static final ArchRule irc_adapter_components_must_declare_their_secondary_role =
      classes()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.adapter..")
          .and(hasRole(org.springframework.stereotype.Component.class))
          .should()
          .beAnnotatedWith(SecondaryAdapter.class);

  // Include package roles and their descendants so moving a type into a subpackage cannot evade it.
  private static DescribedPredicate<JavaClass> hasRole(Class<? extends Annotation> role) {
    return new DescribedPredicate<>(
        "carrying @" + role.getSimpleName() + " on its type or package") {
      @Override
      public boolean test(JavaClass type) {
        JavaClass component = type.getBaseComponentType();
        if (component.isAnnotatedWith(role) || component.isMetaAnnotatedWith(role)) return true;
        for (var pkg = component.getPackage(); pkg != null; pkg = pkg.getParent().orElse(null)) {
          if (pkg.isAnnotatedWith(role) || pkg.isMetaAnnotatedWith(role)) return true;
        }
        return false;
      }
    };
  }
}
