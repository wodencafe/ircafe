package cafe.woden.ircclient.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class GradleCacheWorkflowTest {

  @Test
  void gradleJobsHaveOneCacheManager() throws IOException {
    try (var paths = Files.list(Path.of(".github/workflows"))) {
      for (Path path : paths.filter(file -> file.toString().endsWith(".yml")).toList()) {
        for (Object value : jobs(workflow(path)).values()) {
          List<Map<?, ?>> steps = steps((Map<?, ?>) value);
          boolean usesGradle =
              steps.stream()
                  .anyMatch(step -> action(step).startsWith("gradle/actions/setup-gradle@"));
          if (!usesGradle) continue;
          assertEquals(
              1,
              steps.stream()
                  .filter(step -> action(step).startsWith("gradle/actions/setup-gradle@"))
                  .count(),
              path + " must have one Gradle cache manager per job");
          for (Map<?, ?> step : steps) {
            if (action(step).startsWith("actions/setup-java@")) {
              Map<?, ?> options = (Map<?, ?>) step.get("with");
              assertFalse(
                  options != null && "gradle".equals(options.get("cache")),
                  path + " must not combine setup-java and setup-gradle caches");
            }
          }
        }
      }
    }
  }

  @Test
  void releaseAndManualBuildsOnlyReadSharedCaches() throws IOException {
    for (String name :
        List.of("release-jpackage.yml", "manual-test-build.yml", "pr-spotless-autofix.yml")) {
      for (Object value : jobs(workflow(Path.of(".github/workflows", name))).values()) {
        for (Map<?, ?> step : steps((Map<?, ?>) value)) {
          if (action(step).startsWith("gradle/actions/setup-gradle@")) {
            assertEquals(Boolean.TRUE, ((Map<?, ?>) step.get("with")).get("cache-read-only"));
          }
        }
      }
    }
  }

  @Test
  void cacheWarmupRunsOnDefaultBranchAndCoversEveryReleasePlatform() throws IOException {
    Map<?, ?> warmup = workflow(Path.of(".github/workflows/gradle-cache-warmup.yml"));
    // SnakeYAML's YAML 1.1 resolver treats an unquoted "on" key as a boolean.
    Map<?, ?> triggers = (Map<?, ?>) warmup.get(warmup.containsKey("on") ? "on" : Boolean.TRUE);
    assertEquals(List.of("master"), ((Map<?, ?>) triggers.get("push")).get("branches"));
    assertFalse(((List<?>) triggers.get("schedule")).isEmpty());
    assertTrue(triggers.containsKey("workflow_dispatch"));
    Map<?, ?> job = (Map<?, ?>) jobs(warmup).get("warm-cache");
    assertEquals(
        "github.ref == format('refs/heads/{0}', github.event.repository.default_branch)",
        job.get("if"));
    Map<?, ?> release = workflow(Path.of(".github/workflows/release-jpackage.yml"));
    assertEquals(platforms((Map<?, ?>) jobs(release).get("package")), platforms(job));

    Map<?, ?> gradle =
        steps(job).stream()
            .filter(step -> action(step).startsWith("gradle/actions/setup-gradle@"))
            .findFirst()
            .orElseThrow();
    assertEquals(Boolean.FALSE, ((Map<?, ?>) gradle.get("with")).get("cache-read-only"));
  }

  @Test
  void warmupResolvesBuildAndTestInputsWithoutPublishing() throws IOException {
    Map<?, ?> warmup = workflow(Path.of(".github/workflows/gradle-cache-warmup.yml"));
    assertEquals("read", ((Map<?, ?>) warmup.get("permissions")).get("contents"));
    List<Map<?, ?>> steps = steps((Map<?, ?>) jobs(warmup).get("warm-cache"));
    Map<?, ?> build =
        steps.stream()
            .filter(step -> step.get("run") instanceof String run && run.contains("./gradlew"))
            .findFirst()
            .orElseThrow();
    Set<String> arguments =
        Arrays.stream(((String) build.get("run")).split("\\s+")).collect(Collectors.toSet());
    assertTrue(arguments.containsAll(Set.of(":bootJar", ":testClasses", ":functionalTestClasses")));
    assertFalse(arguments.contains("--dry-run"));
    assertFalse(arguments.contains("--offline"), "warmup must be able to populate a cold cache");
    for (Map<?, ?> step : steps) {
      assertFalse(action(step).startsWith("softprops/action-gh-release@"));
      assertFalse(action(step).startsWith("actions/upload-artifact@"));
    }
  }

  private static Set<String> platforms(Map<?, ?> job) {
    Map<?, ?> matrix = (Map<?, ?>) ((Map<?, ?>) job.get("strategy")).get("matrix");
    return ((List<?>) matrix.get("include"))
        .stream().map(value -> (String) ((Map<?, ?>) value).get("os")).collect(Collectors.toSet());
  }

  private static String action(Map<?, ?> step) {
    return step.get("uses") instanceof String uses ? uses : "";
  }

  private static List<Map<?, ?>> steps(Map<?, ?> job) {
    return ((List<?>) job.get("steps"))
        .stream().<Map<?, ?>>map(value -> (Map<?, ?>) value).toList();
  }

  private static Map<?, ?> jobs(Map<?, ?> workflow) {
    return (Map<?, ?>) workflow.get("jobs");
  }

  private static Map<?, ?> workflow(Path path) throws IOException {
    Map<?, ?> workflow = new Yaml().load(Files.readString(path));
    assertNotNull(workflow, path + " must define a workflow");
    return workflow;
  }
}
