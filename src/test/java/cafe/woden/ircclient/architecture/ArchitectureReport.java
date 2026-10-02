package cafe.woden.ircclient.architecture;

import cafe.woden.ircclient.IrcSwingApp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.modulith.core.ApplicationModules;

/** Offline, opt-in architecture advice. Does not start Spring or instantiate SPI providers. */
public final class ArchitectureReport {
  private ArchitectureReport() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2)
      throw new IllegalArgumentException("Expected repository and output directory");
    Path repository = Path.of(args[0]);
    Path output = Path.of(args[1]);
    int fanOut = positiveProperty("ircafe.advisor.fanOut", 25);
    int constructorSize = positiveProperty("ircafe.advisor.constructorSize", 10);
    List<Project> projects = projectsFromProperties();
    Map<String, Project> owners = classOwners(projects);
    var classes =
        new ClassFileImporter()
            .importPaths(
                projects.stream()
                    .flatMap(p -> p.classes().stream())
                    .filter(Files::isDirectory)
                    .toList());
    if (classes.size() == 0) throw new IllegalStateException("No production classes imported");
    System.out.println(
        "Analyzing " + classes.size() + " production classes in " + projects.size() + " projects");
    var analyzer = new ArchitectureAdvisor(classes, owners, repository, fanOut, constructorSize);
    var modules = analyzer.analyzeModules(ApplicationModules.of(IrcSwingApp.class));
    analyzer.analyzeDependencies(modules);
    analyzer.analyzeServices(projects);
    analyzer.analyzeGuardrails();
    List<Finding> findings = analyzer.findings();
    String baseline = System.getProperty("ircafe.advisor.baseline", "");
    if (!baseline.isBlank()
        && Path.of(baseline)
            .toAbsolutePath()
            .normalize()
            .equals(output.resolve("architecture.json").toAbsolutePath().normalize())) {
      throw new IllegalArgumentException("Save the baseline outside the report output directory");
    }
    Delta delta = compare(findings, baseline.isBlank() ? null : Path.of(baseline));
    var report =
        new Report(
            1,
            classes.size(),
            projects.size(),
            Map.of("fanOut", fanOut, "constructorSize", constructorSize),
            findings,
            delta);
    Files.createDirectories(output);
    new ObjectMapper()
        .writerWithDefaultPrettyPrinter()
        .writeValue(output.resolve("architecture.json").toFile(), report);
    Files.writeString(output.resolve("architecture.md"), markdown(report));
    System.out.println("Architecture report: " + output.resolve("architecture.md"));
    System.out.println(
        findings.size()
            + " findings; "
            + findings.stream().filter(f -> f.kind().equals("violation")).count()
            + " violations");
    findings.stream()
        .limit(10)
        .forEach(f -> System.out.println("  " + f.priority() + " " + f.rule() + " " + f.subject()));
  }

  private static int positiveProperty(String name, int fallback) {
    int value = Integer.parseInt(System.getProperty(name, Integer.toString(fallback)));
    if (value < 1) throw new IllegalArgumentException(name + " must be positive");
    return value;
  }

  private static List<Project> projectsFromProperties() {
    String prefix = "ircafe.advisor.classes.";
    List<Project> projects =
        System.getProperties().stringPropertyNames().stream()
            .filter(key -> key.startsWith(prefix))
            .sorted()
            .map(
                key -> {
                  String name = key.substring(prefix.length());
                  return new Project(
                      name,
                      paths(key),
                      paths("ircafe.advisor.sources." + name),
                      paths("ircafe.advisor.resources." + name));
                })
            .toList();
    if (projects.isEmpty()) throw new IllegalStateException("Run ./gradlew architectureReport");
    return projects;
  }

  private static List<Path> paths(String property) {
    return Arrays.stream(System.getProperty(property, "").split(Pattern.quote(File.pathSeparator)))
        .filter(s -> !s.isBlank())
        .map(Path::of)
        .toList();
  }

  static Map<String, Project> classOwners(List<Project> projects) throws IOException {
    Map<String, Project> owners = new TreeMap<>();
    for (Project project : projects) {
      for (Path directory : project.classes()) {
        if (!Files.isDirectory(directory)) continue;
        try (var files = Files.walk(directory)) {
          for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
            String relative =
                directory.relativize(file).toString().replace(File.separatorChar, '.');
            String name = relative.substring(0, relative.length() - 6);
            Project previous = owners.putIfAbsent(name, project);
            if (previous != null)
              throw new IllegalStateException(
                  "Duplicate class " + name + " in " + previous.name() + " and " + project.name());
          }
        }
      }
    }
    return owners;
  }

  static String location(Path repository, Project project, JavaClass type) {
    if (project == null) return "";
    String filename =
        type.getSource()
            .flatMap(s -> s.getFileName())
            .orElse(type.getSimpleName().split("\\$")[0] + ".java");
    for (Path root : project.sources()) {
      Path file = root.resolve(type.getPackageName().replace('.', '/')).resolve(filename);
      if (Files.isRegularFile(file)) return relative(repository, file);
    }
    return "";
  }

  static String relative(Path repository, Path file) {
    return repository
        .toAbsolutePath()
        .normalize()
        .relativize(file.toAbsolutePath().normalize())
        .toString()
        .replace(File.separatorChar, '/');
  }

  static Delta compare(List<Finding> findings, Path baseline) throws IOException {
    if (baseline == null) return new Delta(false, List.of(), List.of());
    var json = new ObjectMapper().readTree(baseline.toFile());
    if (json == null
        || json.path("schemaVersion").asInt() != 1
        || !json.path("findings").isArray()) {
      throw new IllegalArgumentException("Baseline must be a schemaVersion 1 architecture report");
    }
    Set<String> before = new TreeSet<>();
    for (var finding : json.path("findings")) {
      if (!finding.path("id").isTextual() || finding.path("id").asText().isBlank()) {
        throw new IllegalArgumentException("Baseline finding has no id");
      }
      before.add(finding.path("id").asText());
    }
    Set<String> after =
        findings.stream().map(Finding::id).collect(Collectors.toCollection(TreeSet::new));
    return new Delta(
        true,
        after.stream().filter(id -> !before.contains(id)).toList(),
        before.stream().filter(id -> !after.contains(id)).toList());
  }

  static String markdown(Report report) {
    StringBuilder text = new StringBuilder("# Architecture refactoring report\n\n");
    text.append(report.classCount())
        .append(" production classes across ")
        .append(report.projectCount())
        .append(" projects.\n\n")
        .append(
            "Advisory only. Candidates require design review; violations are detected by explicit rules. ")
        .append("Run `./gradlew architectureTest` for the complete enforcement suite.\n\n")
        .append("Thresholds: ")
        .append(new TreeMap<>(report.thresholds()))
        .append(". ")
        .append("P1 = violation, P2 = boundary opportunity, P3 = structural heuristic. ")
        .append("Within a priority, higher evidence counts sort first.\n\n");
    if (report.delta().compared()) {
      text.append("Baseline: ")
          .append(report.delta().added().size())
          .append(" new, ")
          .append(report.delta().resolved().size())
          .append(" resolved findings. ")
          .append("Comparison uses rule + subject identity, not evidence changes.\n\n");
    }
    if (report.findings().isEmpty()) text.append("No findings at the configured thresholds.\n");
    else {
      text.append("| Rule | Findings |\n| --- | ---: |\n");
      report.findings().stream()
          .collect(Collectors.groupingBy(Finding::rule, TreeMap::new, Collectors.counting()))
          .forEach(
              (rule, count) ->
                  text.append("| ").append(rule).append(" | ").append(count).append(" |\n"));
      text.append('\n');
    }
    for (Finding finding : report.findings()) {
      text.append("## ")
          .append(finding.priority())
          .append(" ")
          .append(finding.rule())
          .append(" — ")
          .append(finding.subject())
          .append("\n\n")
          .append("Kind: ")
          .append(finding.kind())
          .append("; evidence count: ")
          .append(finding.evidenceCount())
          .append(".\n\n");
      if (!finding.location().isEmpty())
        text.append("Source: `").append(finding.location()).append("`\n\n");
      text.append(finding.recommendation()).append("\n\n");
      finding.evidence().forEach(e -> text.append("- ").append(e.replace("\n", " ")).append('\n'));
      if (finding.evidenceCount() > finding.evidence().size()) {
        text.append("- … ")
            .append(finding.evidenceCount() - finding.evidence().size())
            .append(" further evidence items omitted.\n");
      }
      text.append('\n');
    }
    if (!report.delta().resolved().isEmpty()) {
      text.append("## Resolved finding IDs\n\n");
      report.delta().resolved().forEach(id -> text.append("- `").append(id).append("`\n"));
    }
    return text.toString();
  }

  record Project(String name, List<Path> classes, List<Path> sources, List<Path> resources) {}

  public record Finding(
      String id,
      String rule,
      String kind,
      String priority,
      String subject,
      String location,
      String recommendation,
      int evidenceCount,
      List<String> evidence) {
    static final Comparator<Finding> ORDER =
        Comparator.comparing(Finding::priority)
            .thenComparing(Comparator.comparingInt(Finding::evidenceCount).reversed())
            .thenComparing(Finding::id);
  }

  public record Delta(boolean compared, List<String> added, List<String> resolved) {}

  public record Report(
      int schemaVersion,
      int classCount,
      int projectCount,
      Map<String, Integer> thresholds,
      List<Finding> findings,
      Delta delta) {
    public Report {
      thresholds = java.util.Collections.unmodifiableMap(new TreeMap<>(thresholds));
      findings = List.copyOf(findings);
    }
  }
}
