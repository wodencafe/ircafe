package cafe.woden.ircclient.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ReleaseWorkflowTest {

  @Test
  void taggedCommitsRunEveryCiCheckBeforePublishing() throws IOException {
    Map<?, ?> verification = (Map<?, ?>) jobs().get("verify");
    assertEquals("read", ((Map<?, ?>) verification.get("permissions")).get("contents"));
    assertNull(verification.get("if"), "manual branch builds must not inherit a skipped job");
    assertNotEquals(Boolean.TRUE, verification.get("continue-on-error"));

    Map<?, ?> check =
        steps(verification).stream()
            .map(step -> (Map<?, ?>) step)
            .filter(step -> step.get("run") instanceof String run && run.contains("./gradlew"))
            .findFirst()
            .orElseThrow();
    assertEquals("github.ref_type == 'tag'", check.get("if"));
    assertNotEquals(Boolean.TRUE, check.get("continue-on-error"));
    String command = (String) check.get("run");
    assertTrue(command.contains("set -euo pipefail"));
    List<String> arguments = Arrays.asList(command.split("\\s+"));
    assertTrue(
        arguments.containsAll(
            List.of("lint", "test", "integrationTest", "architectureTest", "functionalTest")),
        "release verification must cover all five CI suites");
    assertFalse(arguments.contains("-x"));
    assertFalse(command.contains("--exclude-task"));
    assertTrue(command.contains("-Pversion=\"${GITHUB_REF_NAME#v}\""));
  }

  @Test
  void everyReleasePublisherRequiresSuccessfulVerification() throws IOException {
    Map<?, ?> jobs = jobs();
    int publishers = 0;
    for (var entry : jobs.entrySet()) {
      String jobId = (String) entry.getKey();
      Map<?, ?> job = (Map<?, ?>) entry.getValue();
      for (Object value : steps(job)) {
        Map<?, ?> step = (Map<?, ?>) value;
        if (step.get("uses") instanceof String action
            && action.startsWith("softprops/action-gh-release@")) {
          publishers++;
          assertEquals("startsWith(github.ref, 'refs/tags/')", step.get("if"));
          assertTrue(
              requiresVerification(jobId, jobs, new HashSet<>()),
              jobId + " must wait for verification before publishing");
        }
      }
    }
    assertTrue(publishers > 0, "the release publishing paths must be checked");
  }

  @Test
  void verificationAndPackagingCheckOutTheSameImmutableCommit() throws IOException {
    for (Object value : jobs().values()) {
      for (Object stepValue : steps((Map<?, ?>) value)) {
        Map<?, ?> step = (Map<?, ?>) stepValue;
        if (step.get("uses") instanceof String action && action.startsWith("actions/checkout@")) {
          assertEquals("${{ github.sha }}", ((Map<?, ?>) step.get("with")).get("ref"));
        }
      }
    }
  }

  private static boolean requiresVerification(String jobId, Map<?, ?> jobs, Set<String> visited) {
    if (!visited.add(jobId)) return false;
    Map<?, ?> job = (Map<?, ?>) jobs.get(jobId);
    assertNull(job.get("if"), jobId + " must use the default successful-dependencies condition");
    assertNotEquals(Boolean.TRUE, job.get("continue-on-error"));
    if (jobId.equals("verify")) return true;
    Object needs = job.get("needs");
    List<?> dependencies = needs instanceof String name ? List.of(name) : (List<?>) needs;
    return dependencies != null
        && dependencies.stream()
            .anyMatch(dependency -> requiresVerification((String) dependency, jobs, visited));
  }

  private static List<?> steps(Map<?, ?> job) {
    return (List<?>) job.get("steps");
  }

  private static Map<?, ?> jobs() throws IOException {
    Map<?, ?> workflow =
        new Yaml().load(Files.readString(Path.of(".github/workflows/release-jpackage.yml")));
    return (Map<?, ?>) workflow.get("jobs");
  }
}
