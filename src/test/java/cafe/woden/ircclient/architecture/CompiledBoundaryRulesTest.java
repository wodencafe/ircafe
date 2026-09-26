package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThat;

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
