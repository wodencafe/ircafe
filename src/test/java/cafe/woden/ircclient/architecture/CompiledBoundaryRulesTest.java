package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Compile fixtures outside the classpath so intentional violations cannot pollute Modulith scans.
 */
class CompiledBoundaryRulesTest {

  @TempDir Path temporaryDirectory;

  @ParameterizedTest
  @CsvSource({
    "import javax.swing.*;, JLabel, javax.swing.JLabel",
    "'', javax.swing.JLabel, javax.swing.JLabel",
    "import java.awt.*;, Color, java.awt.Color",
    "'', java.beans.PropertyChangeListener, java.beans.PropertyChangeListener",
    "'', javax.swing.JLabel[][], javax.swing.JLabel"
  })
  void pluginApiRejectsDesktopDependenciesRegardlessOfImportSyntax(
      String imports, String fieldType, String forbiddenType) throws IOException {
    JavaClasses fixture = compileFixture(imports, "public " + fieldType + " value;", Map.of());
    var result =
        CompiledBoundaryRules.pluginApiDependencies(CompiledSubprojects.names(fixture))
            .evaluate(fixture);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anySatisfy(detail -> assertThat(detail).contains(forbiddenType));
  }

  @Test
  void pluginApiAcceptsOwnedNestedTypesAndJdkGenerics() throws IOException {
    JavaClasses fixture =
        compileFixture(
            "",
            "public record Value(String text) {} public java.util.List<Value> values; "
                + "public Value[][] array; public int[] numbers; public enum Kind { ONE }",
            Map.of());

    assertThat(CompiledSubprojects.names(fixture))
        .contains("cafe.woden.ircclient.fixture.spi.Fixture$Value");
    CompiledBoundaryRules.pluginApiDependencies(CompiledSubprojects.names(fixture)).check(fixture);
  }

  @Test
  void pluginApiRejectsHostTypesEvenInSpiPackages() throws IOException {
    JavaClasses fixture = hostDependentFixture();
    var result =
        CompiledBoundaryRules.pluginApiDependencies(CompiledSubprojects.names(fixture))
            .evaluate(fixture);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anySatisfy(
            detail -> assertThat(detail).contains("cafe.woden.ircclient.host.spi.HostOnly"));
  }

  @Test
  void featuresRejectHostTypesEvenInSpiPackages() throws IOException {
    JavaClasses fixture = hostDependentFixture();
    var result =
        CompiledBoundaryRules.featureDependencies(CompiledSubprojects.names(fixture))
            .evaluate(fixture);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anySatisfy(
            detail -> assertThat(detail).contains("cafe.woden.ircclient.host.spi.HostOnly"));
  }

  @Test
  void featuresAcceptTypesOwnedByAnotherFeatureOrPluginApi() throws IOException {
    JavaClasses fixture =
        compileFixture(
            "",
            "public cafe.woden.ircclient.peer.Value.Nested value;",
            Map.of(
                "cafe/woden/ircclient/peer/Value.java",
                "package cafe.woden.ircclient.peer; public class Value { public record Nested(String text) {} }"));

    CompiledBoundaryRules.featureDependencies(
            Set.of(
                "cafe.woden.ircclient.fixture.spi.Fixture",
                "cafe.woden.ircclient.peer.Value",
                "cafe.woden.ircclient.peer.Value$Nested"))
        .check(fixture);
  }

  @Test
  void missingArtifactConfigurationFailsInsteadOfSilentlySkippingChecks() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> CompiledSubprojects.importProject("missing-fixture-project"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("run ./gradlew architectureTest");
  }

  @ParameterizedTest
  @CsvSource({
    "import org.pircbotx.*;, PircBotX",
    "'', org.pircbotx.PircBotX[][]",
    "import javax.swing.*;, JLabel",
    "'', java.awt.Color"
  })
  void protocolFeaturesRejectTransportAndDesktopDependencies(String imports, String fieldType)
      throws IOException {
    JavaClasses fixture =
        compileFixture(
            imports,
            "public " + fieldType + " value;",
            Map.of("org/pircbotx/PircBotX.java", "package org.pircbotx; public class PircBotX {}"));
    assertThat(
            CompiledBoundaryRules.transportIndependentFeatures().evaluate(fixture).hasViolation())
        .isTrue();
  }

  @Test
  void requestStoreRejectsDependenciesHiddenBehindWildcardImports() throws IOException {
    JavaClasses fixture =
        compileFixture(
            "import cafe.woden.ircclient.peer.*;",
            "public Value[] value;",
            Map.of(
                "cafe/woden/ircclient/peer/Value.java",
                "package cafe.woden.ircclient.peer; public class Value {}"));
    assertThat(
            CompiledBoundaryRules.jdkOnly("cafe.woden.ircclient.fixture.spi.Fixture")
                .evaluate(fixture)
                .hasViolation())
        .isTrue();
  }

  @Test
  void requestStoreAllowsItsOwnNestedTypesAndJdkCollections() throws IOException {
    JavaClasses fixture =
        compileFixture(
            "",
            "public record Entry(String key) {} "
                + "public java.util.List<Entry> entries; public long[] deadlines;",
            Map.of());
    CompiledBoundaryRules.jdkOnly("cafe.woden.ircclient.fixture.spi.Fixture").check(fixture);
    CompiledBoundaryRules.transportIndependentFeatures().check(fixture);
  }

  @Test
  void runtimeProviderBoundaryRejectsFeatureTypesInGenericsAndArrays() throws IOException {
    JavaClasses fixture =
        compileFixture(
            "",
            "public java.util.List<cafe.woden.ircclient.peer.Value> values; "
                + "public cafe.woden.ircclient.peer.Value[][] array;",
            Map.of(
                "cafe/woden/ircclient/peer/Value.java",
                "package cafe.woden.ircclient.peer; public class Value {}"));
    var result =
        CompiledBoundaryRules.mustNotBypassRuntimeProviders(
                JavaClass.Predicates.resideInAPackage("cafe.woden.ircclient.fixture.."),
                Set.of("cafe.woden.ircclient.peer.Value"))
            .evaluate(fixture);
    assertThat(result.getFailureReport().getDetails())
        .anySatisfy(detail -> assertThat(detail).contains("cafe.woden.ircclient.peer.Value"));
  }

  @Test
  void featurePackageBoundaryRejectsClassesInAnotherModulithModule() throws IOException {
    JavaClasses fixture = compileFixture("", "", Map.of());
    assertThat(
            CompiledBoundaryRules.featurePackages("cafe.woden.ircclient.app.commands..")
                .evaluate(fixture)
                .hasViolation())
        .isTrue();
    CompiledBoundaryRules.featurePackages("cafe.woden.ircclient.fixture..").check(fixture);
  }

  @Test
  void runtimeProviderBoundaryRejectsStaticCallsWithoutImportedTypes() throws IOException {
    JavaClasses fixture =
        compileFixture(
            "",
            "public String render() { return cafe.woden.ircclient.peer.Parser.parse(); }",
            Map.of(
                "cafe/woden/ircclient/peer/Parser.java",
                "package cafe.woden.ircclient.peer; public class Parser { public static String parse() { return \"\"; } }"));
    var result =
        CompiledBoundaryRules.mustNotBypassRuntimeProviders(
                JavaClass.Predicates.resideInAPackage("cafe.woden.ircclient.fixture.."),
                Set.of("cafe.woden.ircclient.peer.Parser"))
            .evaluate(fixture);
    assertThat(result.getFailureReport().getDetails())
        .anySatisfy(detail -> assertThat(detail).contains("calls method", "Parser.parse()"));
  }

  @Test
  void runtimeProviderBoundaryAllowsValuesOutsideItsImplementationSet() throws IOException {
    JavaClasses fixture =
        compileFixture(
            "",
            "public cafe.woden.ircclient.peer.Value value;",
            Map.of(
                "cafe/woden/ircclient/peer/Value.java",
                "package cafe.woden.ircclient.peer; public record Value(String text) {}"));
    CompiledBoundaryRules.mustNotBypassRuntimeProviders(
            JavaClass.Predicates.resideInAPackage("cafe.woden.ircclient.fixture.."),
            Set.of("cafe.woden.ircclient.peer.Parser"))
        .check(fixture);
  }

  private JavaClasses hostDependentFixture() throws IOException {
    return compileFixture(
        "",
        "public java.util.List<cafe.woden.ircclient.host.spi.HostOnly> values; "
            + "public cafe.woden.ircclient.host.spi.HostOnly[][] array;",
        Map.of(
            "cafe/woden/ircclient/host/spi/HostOnly.java",
            "package cafe.woden.ircclient.host.spi; public class HostOnly {}"));
  }

  private JavaClasses compileFixture(
      String imports, String members, Map<String, String> supportingSources) throws IOException {
    Map<String, String> sources = new LinkedHashMap<>(supportingSources);
    sources.put(
        "cafe/woden/ircclient/fixture/spi/Fixture.java",
        "package cafe.woden.ircclient.fixture.spi;\n"
            + imports
            + "\npublic class Fixture { "
            + members
            + " }");
    Path output = ArchitectureFixtureCompiler.compile(temporaryDirectory, sources);
    return new ClassFileImporter().importPath(output.resolve("cafe/woden/ircclient/fixture/spi"));
  }
}
