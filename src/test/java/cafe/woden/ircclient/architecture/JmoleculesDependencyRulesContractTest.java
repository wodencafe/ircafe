package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.DomainLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JmoleculesDependencyRulesContractTest {

  @TempDir Path temporaryDirectory;

  @ParameterizedTest
  @ValueSource(strings = {"ApplicationLayer", "InfrastructureLayer", "InterfaceLayer"})
  void domainTypesCannotReferToOuterLayers(String outerRole) throws IOException {
    JavaClasses fixture =
        compile(
            Map.of(
                "example/Value.java",
                """
                package example;
                @org.jmolecules.architecture.layered.DomainLayer
                public class Value { public java.util.List<Outer> values; public Outer[][] array; }
                """,
                "example/Outer.java",
                """
                package example;
                @org.jmolecules.architecture.layered.%s
                public class Outer {}
                """
                    .formatted(outerRole)));

    var result =
        JmoleculesDependencyRulesTest.domain_types_must_not_depend_on_outer_layers.evaluate(
            fixture);
    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anySatisfy(detail -> assertThat(detail).contains("example.Value", "example.Outer"));
  }

  @Test
  void domainPackageRolesProtectUnannotatedTypesInSubpackages() throws IOException {
    JavaClasses fixture =
        compile(
            Map.of(
                "example/domain/package-info.java",
                "@org.jmolecules.architecture.layered.DomainLayer package example.domain;",
                "example/domain/nested/Value.java",
                "package example.domain.nested; public class Value { public example.storage.Store store; }",
                "example/storage/package-info.java",
                "@org.jmolecules.architecture.layered.InfrastructureLayer package example.storage;",
                "example/storage/Store.java",
                "package example.storage; public class Store {}"));

    assertThatThrownBy(
            () ->
                JmoleculesDependencyRulesTest.domain_types_must_not_depend_on_outer_layers.check(
                    fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("example.domain.nested.Value")
        .hasMessageContaining("example.storage.Store");
  }

  @Test
  void domainTypesMayUseOtherDomainValuesAndJdkTypes() throws IOException {
    JavaClasses fixture =
        compile(
            Map.of(
                "example/package-info.java",
                "@org.jmolecules.architecture.layered.DomainLayer package example;",
                "example/Value.java",
                """
                package example;
                public record Value(java.util.List<Part> parts) { public record Part(String text) {} }
                """));

    JmoleculesDependencyRulesTest.domain_types_must_not_depend_on_outer_layers.check(fixture);
  }

  @Test
  void applicationAndAdapterCanDependOnTheirPort() throws IOException {
    JavaClasses fixture =
        compile(
            Map.of(
                "cafe/woden/ircclient/irc/port/fixture/package-info.java",
                "@Deprecated package cafe.woden.ircclient.irc.port.fixture;",
                "cafe/woden/ircclient/irc/port/fixture/CommandPort.java",
                """
                package cafe.woden.ircclient.irc.port.fixture;
                @org.jmolecules.architecture.hexagonal.SecondaryPort
                public interface CommandPort { void send(); }
                """,
                "cafe/woden/ircclient/irc/port/fixture/UseCase.java",
                """
                package cafe.woden.ircclient.irc.port.fixture;
                @org.jmolecules.architecture.hexagonal.Application
                public class UseCase {
                  private final CommandPort port;
                  public UseCase(CommandPort port) { this.port = port; }
                  public void execute() { port.send(); }
                }
                """,
                "cafe/woden/ircclient/irc/adapter/fixture/NetworkAdapter.java",
                """
                package cafe.woden.ircclient.irc.adapter.fixture;
                @org.jmolecules.architecture.hexagonal.SecondaryAdapter
                public class NetworkAdapter implements cafe.woden.ircclient.irc.port.fixture.CommandPort {
                  public void send() {}
                }
                """));

    assertThatCode(
            () ->
                JmoleculesDependencyRulesTest.ports_and_applications_must_not_depend_on_adapters
                    .check(fixture))
        .doesNotThrowAnyException();
    JmoleculesDependencyRulesTest.irc_secondary_adapters_must_not_depend_on_primary_roles.check(
        fixture);
    JmoleculesDependencyRulesTest.irc_port_interfaces_must_declare_their_secondary_role.check(
        fixture);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PrimaryPort", "PrimaryAdapter"})
  void outboundIrcAdaptersCannotReachIntoInboundRoles(String role) throws IOException {
    JavaClasses fixture =
        compile(
            Map.of(
                "cafe/woden/ircclient/irc/adapter/fixture/NetworkAdapter.java",
                """
                package cafe.woden.ircclient.irc.adapter.fixture;
                @org.jmolecules.architecture.hexagonal.SecondaryAdapter
                public class NetworkAdapter { public example.Inbound inbound; }
                """,
                "example/Inbound.java",
                """
                package example;
                @org.jmolecules.architecture.hexagonal.%s
                public class Inbound {}
                """
                    .formatted(role)));

    assertThatThrownBy(
            () ->
                JmoleculesDependencyRulesTest
                    .irc_secondary_adapters_must_not_depend_on_primary_roles
                    .check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("NetworkAdapter")
        .hasMessageContaining("example.Inbound");
  }

  @Test
  void newIrcPortCannotEvadeDirectionChecksByOmittingItsRole() throws IOException {
    JavaClasses fixture =
        compile(
            Map.of(
                "cafe/woden/ircclient/irc/port/NewPort.java",
                "package cafe.woden.ircclient.irc.port; public interface NewPort {}"));

    assertThatThrownBy(
            () ->
                JmoleculesDependencyRulesTest.irc_port_interfaces_must_declare_their_secondary_role
                    .check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("NewPort");
  }

  @Test
  void newIrcAdapterCannotEvadeDirectionChecksUsingAMetaAnnotatedSpringStereotype()
      throws IOException {
    JavaClasses fixture =
        compile(
            Map.of(
                "cafe/woden/ircclient/irc/adapter/NewAdapter.java",
                """
                package cafe.woden.ircclient.irc.adapter;
                @org.springframework.stereotype.Service
                public class NewAdapter {}
                """));

    assertThatThrownBy(
            () ->
                JmoleculesDependencyRulesTest
                    .irc_adapter_components_must_declare_their_secondary_role
                    .check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("NewAdapter");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PrimaryPort", "SecondaryPort", "Application"})
  void portsAndApplicationCannotReferenceAdaptersInOtherPackages(String role) throws IOException {
    JavaClasses fixture =
        compile(
            Map.of(
                "cafe/woden/ircclient/irc/port/fixture/LeakingBoundary.java",
                """
                package cafe.woden.ircclient.irc.port.fixture;
                @org.jmolecules.architecture.hexagonal.%s
                public interface LeakingBoundary {
                  cafe.woden.ircclient.irc.backend.fixture.NetworkAdapter adapter();
                }
                """
                    .formatted(role),
                "cafe/woden/ircclient/irc/backend/fixture/NetworkAdapter.java",
                """
                package cafe.woden.ircclient.irc.backend.fixture;
                @org.jmolecules.architecture.hexagonal.SecondaryAdapter
                public class NetworkAdapter {}
                """));

    assertThatThrownBy(
            () ->
                JmoleculesDependencyRulesTest.ports_and_applications_must_not_depend_on_adapters
                    .check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("LeakingBoundary")
        .hasMessageContaining("NetworkAdapter");
  }

  @Test
  void hexagonalPackageRolesApplyToSubpackages() throws IOException {
    JavaClasses fixture =
        compile(
            Map.of(
                "cafe/woden/ircclient/irc/port/fixture/package-info.java",
                """
                @org.jmolecules.architecture.hexagonal.SecondaryPort
                package cafe.woden.ircclient.irc.port.fixture;
                """,
                "cafe/woden/ircclient/irc/port/fixture/nested/LeakingBoundary.java",
                """
                package cafe.woden.ircclient.irc.port.fixture.nested;
                public interface LeakingBoundary {
                  cafe.woden.ircclient.irc.adapter.fixture.NetworkAdapter adapter();
                }
                """,
                "cafe/woden/ircclient/irc/adapter/fixture/NetworkAdapter.java",
                """
                package cafe.woden.ircclient.irc.adapter.fixture;
                @org.jmolecules.architecture.hexagonal.SecondaryAdapter
                public class NetworkAdapter {}
                """));

    assertThatThrownBy(
            () ->
                JmoleculesDependencyRulesTest.ports_and_applications_must_not_depend_on_adapters
                    .check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("LeakingBoundary")
        .hasMessageContaining("NetworkAdapter");
  }

  private JavaClasses compile(Map<String, String> sources) throws IOException {
    return new ClassFileImporter()
        .importPath(
            ArchitectureFixtureCompiler.compile(
                temporaryDirectory,
                sources,
                DomainLayer.class,
                SecondaryPort.class,
                org.springframework.stereotype.Component.class));
  }
}
