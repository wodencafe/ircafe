package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cafe.woden.ircclient.architecture.ArchitectureReport.Finding;
import cafe.woden.ircclient.architecture.ArchitectureReport.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.Port;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.stereotype.Component;

class ArchitectureAdvisorTest {
  @TempDir Path temporary;

  @Test
  void countsDistinctProductionDependenciesAndUsesModuleOwnership() throws Exception {
    var sources =
        Map.of(
            "example/left/Caller.java",
                """
          package example.left;
          @org.springframework.stereotype.Component
          public class Caller {
            example.right.Service service;
            example.right.Contract contract;
            java.time.Instant external;
            public Caller(example.right.Service service, example.right.Contract contract) {}
            public void call() { service.run(); service.run(); }
          }
          """,
            "example/right/Service.java",
                """
          package example.right;
          @org.springframework.stereotype.Component
          public class Service { public void run() {} }
          """,
            "example/right/Contract.java", "package example.right; public interface Contract {}",
            "example/right/Local.java",
                """
          package example.right;
          public class Local { Service service; }
          """);
    Path output = ArchitectureFixtureCompiler.compile(temporary, sources, Component.class);
    var project = project("root", output);
    var classes = new ClassFileImporter().importPath(output);
    var advisor =
        new ArchitectureAdvisor(
            classes, ArchitectureReport.classOwners(List.of(project)), temporary, 2, 2);
    advisor.analyzeDependencies(
        Map.of(
            "example.left.Caller",
            "left",
            "example.right.Service",
            "right",
            "example.right.Contract",
            "right",
            "example.right.Local",
            "right"));

    var fanOut = findings(advisor, "CLASS_FAN_OUT");
    assertThat(fanOut)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.evidenceCount()).isEqualTo(2);
              assertThat(f.evidence())
                  .containsExactly("example.right.Contract", "example.right.Service");
              assertThat(f.location()).isEqualTo("sources/example/left/Caller.java");
            });
    assertThat(findings(advisor, "LARGE_COMPONENT_CONSTRUCTOR")).hasSize(1);
    assertThat(findings(advisor, "CROSS_MODULE_CONCRETE_COMPONENT"))
        .singleElement()
        .satisfies(
            f -> assertThat(f.subject()).isEqualTo("example.left.Caller -> example.right.Service"));
  }

  @Test
  void constructorsAreGroupedByClassAndRepeatedParameterTypesStillCount() throws Exception {
    Path output =
        ArchitectureFixtureCompiler.compile(
            temporary,
            Map.of(
                "example/Service.java",
                """
          package example;
          @org.springframework.stereotype.Component
          public class Service {
            public Service(String first, String second) {}
            public Service(String first, String second, String third) {}
          }
          """),
            Component.class);
    var project = project("root", output);
    var advisor =
        new ArchitectureAdvisor(
            new ClassFileImporter().importPath(output),
            ArchitectureReport.classOwners(List.of(project)),
            temporary,
            25,
            2);
    advisor.analyzeDependencies(Map.of());
    assertThat(findings(advisor, "LARGE_COMPONENT_CONSTRUCTOR"))
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.subject()).isEqualTo("example.Service");
              assertThat(f.evidenceCount()).isEqualTo(3);
              assertThat(f.recommendation()).contains("3 parameters");
            });
  }

  @Test
  void portRoleHintsRespectMetaAnnotationsAndAncestorPackages() throws Exception {
    var sources =
        Map.of(
            "example/plain/MissingPort.java",
                "package example.plain; public interface MissingPort {}",
            "example/plain/MarkedPort.java",
                """
          package example.plain;
          @org.jmolecules.architecture.hexagonal.SecondaryPort
          public interface MarkedPort {}
          """,
            "example/scoped/package-info.java",
                """
          @org.jmolecules.architecture.hexagonal.SecondaryPort
          package example.scoped;
          """,
            "example/scoped/child/InheritedPort.java",
                "package example.scoped.child; public interface InheritedPort {}",
            "example/plain/Ordinary.java", "package example.plain; public interface Ordinary {}");
    Path output =
        ArchitectureFixtureCompiler.compile(temporary, sources, Port.class, SecondaryPort.class);
    var project = project("root", output);
    var advisor =
        new ArchitectureAdvisor(
            new ClassFileImporter().importPath(output),
            ArchitectureReport.classOwners(List.of(project)),
            temporary,
            25,
            10);
    advisor.analyzeDependencies(Map.of());
    assertThat(findings(advisor, "JMOLECULES_PORT_ROLE"))
        .singleElement()
        .satisfies(f -> assertThat(f.subject()).isEqualTo("example.plain.MissingPort"));
  }

  @Test
  void serviceDescriptorsSupportCommentsAndDetectProviderCouplingAndBadContracts()
      throws Exception {
    var sources =
        Map.of(
            "example/api/Service.java", "package example.api; public interface Service {}",
            "example/impl/Provider.java",
                """
          package example.impl;
          public class Provider implements example.api.Service {}
          """,
            "example/impl/Wrong.java", "package example.impl; public class Wrong {}",
            "example/app/Caller.java",
                "package example.app; public class Caller { example.impl.Provider provider; }",
            "example/app/Good.java",
                "package example.app; public class Good { example.api.Service service; }");
    Path output = ArchitectureFixtureCompiler.compile(temporary, sources);
    var app = project("root", output);
    var provider =
        new Project("provider", List.of(), List.of(temporary.resolve("sources")), app.resources());
    Map<String, Project> owners = new HashMap<>(ArchitectureReport.classOwners(List.of(app)));
    owners.put("example.impl.Provider", provider);
    owners.put("example.impl.Wrong", provider);
    Path descriptors = Files.createDirectories(temporary.resolve("resources/META-INF/services"));
    Files.writeString(
        descriptors.resolve("example.api.Service"),
        """
        # providers
        example.impl.Provider # valid

        example.impl.Wrong
        example.impl.Missing
        """);
    var advisor =
        new ArchitectureAdvisor(
            new ClassFileImporter().importPath(output), owners, temporary, 25, 10);
    advisor.analyzeServices(List.of(provider));
    assertThat(findings(advisor, "SPI_IMPLEMENTATION_COUPLING"))
        .singleElement()
        .satisfies(
            f -> assertThat(f.subject()).isEqualTo("example.app.Caller -> example.impl.Provider"));
    assertThat(findings(advisor, "SPI_PROVIDER_CONTRACT"))
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.kind()).isEqualTo("violation");
              assertThat(f.location()).endsWith("example.api.Service:4");
            });
    assertThat(findings(advisor, "SPI_PROVIDER_UNRESOLVED"))
        .singleElement()
        .satisfies(f -> assertThat(f.kind()).isEqualTo("candidate"));
  }

  @Test
  void duplicateProductionClassesFailInsteadOfSilentlyMisattributingOwnership() throws Exception {
    Path output =
        ArchitectureFixtureCompiler.compile(
            temporary, Map.of("example/Thing.java", "package example; public class Thing {}"));
    assertThatThrownBy(
            () ->
                ArchitectureReport.classOwners(
                    List.of(project("one", output), project("two", output))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate class example.Thing");
  }

  @Test
  void reportComparisonUsesStableIdentityAndRejectsInvalidBaselines() throws Exception {
    Finding before = finding("old", "before");
    Finding retained = finding("retained", "before");
    Path baseline = temporary.resolve("baseline.json");
    var mapper = new ObjectMapper();
    mapper.writeValue(baseline.toFile(), report(List.of(before, retained)));
    var delta =
        ArchitectureReport.compare(
            List.of(finding("new", "after"), finding("retained", "after")), baseline);
    assertThat(delta.added()).containsExactly("rule:new");
    assertThat(delta.resolved()).containsExactly("rule:old");
    assertThat(ArchitectureReport.compare(List.of(), null).compared()).isFalse();
    Files.writeString(baseline, "{\"schemaVersion\":99,\"findings\":[]}");
    assertThatThrownBy(() -> ArchitectureReport.compare(List.of(), baseline))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("schemaVersion 1");
  }

  @Test
  void markdownExplainsHeuristicsAndIncludesSourceAndRecommendation() {
    String markdown = ArchitectureReport.markdown(report(List.of(finding("subject", "evidence"))));
    assertThat(markdown)
        .contains(
            "Advisory only",
            "src/Example.java",
            "Review responsibility",
            "evidence",
            "architectureTest");
  }

  private Project project(String name, Path output) {
    return new Project(
        name,
        List.of(output),
        List.of(temporary.resolve("sources")),
        List.of(temporary.resolve("resources")));
  }

  private static List<Finding> findings(ArchitectureAdvisor advisor, String rule) {
    return advisor.findings().stream().filter(f -> f.rule().equals(rule)).toList();
  }

  private static Finding finding(String subject, String evidence) {
    return new Finding(
        "rule:" + subject,
        "rule",
        "candidate",
        "P3",
        subject,
        "src/Example.java",
        "Review responsibility",
        1,
        List.of(evidence));
  }

  private static ArchitectureReport.Report report(List<Finding> findings) {
    return new ArchitectureReport.Report(
        1,
        1,
        1,
        Map.of("fanOut", 25),
        findings,
        new ArchitectureReport.Delta(false, List.of(), List.of()));
  }
}
