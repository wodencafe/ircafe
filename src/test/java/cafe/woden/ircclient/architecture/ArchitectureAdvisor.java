package cafe.woden.ircclient.architecture;

import cafe.woden.ircclient.architecture.ArchitectureReport.Finding;
import cafe.woden.ircclient.architecture.ArchitectureReport.Project;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jmolecules.architecture.hexagonal.Port;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.stereotype.Component;

/** Deterministic analysis of production bytecode; suggestions deliberately do not edit sources. */
final class ArchitectureAdvisor {
  private final JavaClasses classes;
  private final Map<String, Project> owners;
  private final Path repository;
  private final int fanOut;
  private final int constructorSize;
  private final Map<String, Finding> findings = new TreeMap<>();

  ArchitectureAdvisor(
      JavaClasses classes,
      Map<String, Project> owners,
      Path repository,
      int fanOut,
      int constructorSize) {
    this.classes = classes;
    this.owners = owners;
    this.repository = repository;
    this.fanOut = fanOut;
    this.constructorSize = constructorSize;
  }

  List<Finding> findings() {
    return findings.values().stream().sorted(Finding.ORDER).toList();
  }

  Map<String, String> analyzeModules(ApplicationModules modules) {
    Map<String, String> moduleNames = new HashMap<>();
    for (JavaClass type : classes) {
      modules
          .getModuleByType(type.getName())
          .ifPresent(module -> moduleNames.put(type.getName(), module.getIdentifier().toString()));
    }
    add(
        "MODULITH_VIOLATION",
        "violation",
        "P1",
        "application modules",
        "",
        "Repair the reported module dependency or cycle, then run architectureTest.",
        modules.detectViolations().getMessages());
    for (var module : modules) {
      String name = module.getIdentifier().toString();
      String packageName = module.getBasePackage().getName();
      String location =
          classes.contain(packageName + ".package-info")
              ? location(classes.get(packageName + ".package-info"))
              : "";
      List<String> dependencies =
          module.getDirectDependencies(modules).stream()
              .map(d -> d.getTargetModule().getIdentifier().toString())
              .distinct()
              .sorted()
              .toList();
      if (module.isOpen()) {
        add(
            "MODULITH_OPEN_MODULE",
            "candidate",
            "P2",
            name,
            location,
            "Review cross-module callers, expose a minimal named interface, and close this module in a tested slice.",
            List.of(
                "Open module: " + packageName,
                "Named interfaces: "
                    + module.getNamedInterfaces().stream()
                        .map(i -> i.getName())
                        .sorted()
                        .collect(Collectors.joining(", "))));
      }
      var declaration = module.getBasePackage().getAnnotation(ApplicationModule.class);
      boolean unrestricted =
          declaration.isEmpty()
              || Arrays.asList(declaration.get().allowedDependencies())
                  .contains(ApplicationModule.OPEN_TOKEN);
      if (unrestricted) {
        add(
            "MODULITH_UNRESTRICTED_DEPENDENCIES",
            "candidate",
            "P2",
            name,
            location,
            "Declare allowedDependencies after reviewing actual usage, including named interfaces; do not copy module names blindly.",
            List.of(
                "Dependencies are not explicitly restricted.",
                "Observed target modules: " + dependencies));
      }
    }
    return moduleNames;
  }

  void analyzeDependencies(Map<String, String> modules) {
    for (JavaClass type : classes) {
      if (!type.isTopLevelClass()
          || type.isAnnotation()
          || type.getSimpleName().equals("package-info")) continue;
      List<Dependency> dependencies =
          type.getDirectDependenciesFromSelf().stream()
              .filter(d -> owners.containsKey(d.getTargetClass().getBaseComponentType().getName()))
              .filter(d -> !d.getTargetClass().getName().equals(type.getName()))
              .filter(d -> !d.getTargetClass().getName().startsWith(type.getName() + "$"))
              .sorted(java.util.Comparator.comparing(Dependency::getDescription))
              .toList();
      List<String> targets =
          dependencies.stream()
              .map(d -> d.getTargetClass().getBaseComponentType().getName())
              .distinct()
              .sorted()
              .toList();
      if (targets.size() >= fanOut) {
        add(
            "CLASS_FAN_OUT",
            "candidate",
            "P3",
            type.getName(),
            location(type),
            "Review cohesive responsibilities for extraction behind existing ports. Count includes signatures and annotations; high fan-out alone is not a defect.",
            targets);
      }
      if (isComponent(type)) {
        type.getConstructors().stream()
            .filter(c -> c.getRawParameterTypes().size() >= constructorSize)
            .sorted(
                Comparator.<JavaConstructor>comparingInt(c -> c.getRawParameterTypes().size())
                    .reversed()
                    .thenComparing(JavaConstructor::getFullName))
            .findFirst()
            .ifPresent(
                c ->
                    add(
                        "LARGE_COMPONENT_CONSTRUCTOR",
                        "candidate",
                        "P3",
                        type.getName(),
                        location(type),
                        "Largest constructor has "
                            + c.getRawParameterTypes().size()
                            + " parameters. Review whether this component coordinates too many responsibilities; prefer a cohesive collaborator over a parameter-holder bean.",
                        IntStream.range(0, c.getRawParameterTypes().size())
                            .mapToObj(
                                i ->
                                    "Parameter "
                                        + (i + 1)
                                        + ": "
                                        + c.getParameterTypes().get(i).getName())
                            .toList()));
      }
      if (type.isInterface()
          && (type.getSimpleName().endsWith("Port")
              || Arrays.asList(type.getPackageName().split("\\.")).contains("port"))
          && !hasRole(type, Port.class)) {
        add(
            "JMOLECULES_PORT_ROLE",
            "candidate",
            "P3",
            type.getName(),
            location(type),
            "If this interface is an architectural port, declare its primary/secondary role and enforce its direction. Naming alone cannot determine the role.",
            List.of(
                "Port-shaped interface has no type, meta-annotation, or inherited package @Port role."));
      }
      Map<String, List<String>> concreteTargets = new TreeMap<>();
      for (Dependency dependency : dependencies) {
        JavaClass target = dependency.getTargetClass().getBaseComponentType();
        String fromModule = modules.get(type.getName());
        String toModule = modules.get(target.getName());
        if (fromModule != null
            && toModule != null
            && !fromModule.equals(toModule)
            && isComponent(target)
            && !target.isInterface()) {
          concreteTargets
              .computeIfAbsent(target.getName(), ignored -> new ArrayList<>())
              .add(dependency.getDescription());
        }
      }
      concreteTargets.forEach(
          (target, evidence) ->
              add(
                  "CROSS_MODULE_CONCRETE_COMPONENT",
                  "candidate",
                  "P2",
                  type.getName() + " -> " + target,
                  location(type),
                  "Consider depending on an existing port or a narrow named interface. Exported concrete services can be intentional; review before introducing an abstraction.",
                  evidence));
    }
  }

  void analyzeServices(List<Project> projects) throws IOException {
    Map<String, List<String>> providerContracts = new TreeMap<>();
    for (Project project : projects) {
      for (Path root : project.resources()) {
        Path services = root.resolve("META-INF/services");
        if (!Files.isDirectory(services)) continue;
        try (var files = Files.list(services)) {
          for (Path descriptor : files.filter(Files::isRegularFile).sorted().toList()) {
            String contract = descriptor.getFileName().toString();
            int lineNumber = 0;
            for (String line : Files.readAllLines(descriptor)) {
              lineNumber++;
              String provider = line.split("#", 2)[0].strip();
              if (provider.isEmpty()) continue;
              String location =
                  ArchitectureReport.relative(repository, descriptor) + ":" + lineNumber;
              providerContracts
                  .computeIfAbsent(provider, ignored -> new ArrayList<>())
                  .add(contract);
              if (!classes.contain(provider)) {
                add(
                    "SPI_PROVIDER_UNRESOLVED",
                    "candidate",
                    "P2",
                    contract + " -> " + provider,
                    location,
                    "Confirm that the descriptor names a packaged provider. External dependency providers are outside this production-class scan.",
                    List.of("Provider not found in analyzed project outputs: " + provider));
              } else if (classes.contain(contract)
                  && !classes.get(provider).isAssignableTo(contract)) {
                add(
                    "SPI_PROVIDER_CONTRACT",
                    "violation",
                    "P1",
                    contract + " -> " + provider,
                    location,
                    "Make the classpath ServiceLoader provider implement its declared service contract.",
                    List.of(provider + " is not assignable to " + contract));
              }
            }
          }
        }
      }
    }
    for (JavaClass type : classes) {
      Map<String, List<String>> leakedProviders = new TreeMap<>();
      for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
        String target = dependency.getTargetClass().getBaseComponentType().getName();
        if (!providerContracts.containsKey(target) || !owners.containsKey(target)) continue;
        if (Objects.equals(owners.get(type.getName()), owners.get(target))) continue;
        leakedProviders
            .computeIfAbsent(target, ignored -> new ArrayList<>())
            .add(dependency.getDescription());
      }
      leakedProviders.forEach(
          (target, evidence) ->
              add(
                  "SPI_IMPLEMENTATION_COUPLING",
                  "candidate",
                  "P2",
                  type.getName() + " -> " + target,
                  location(type),
                  "Depend on the service contract instead of another artifact's provider implementation. Contracts: "
                      + new TreeSet<>(providerContracts.get(target)),
                  evidence));
    }
  }

  void analyzeGuardrails() throws IllegalAccessException {
    // Reuse the project's dependency-inverted jMolecules rules, not incompatible stock layering.
    for (Class<?> rules :
        List.of(ArchitectureGuardrailsTest.class, JmoleculesDependencyRulesTest.class)) {
      for (var field : rules.getDeclaredFields()) {
        if (!field.isAnnotationPresent(ArchTest.class)
            || !Modifier.isStatic(field.getModifiers())
            || !ArchRule.class.isAssignableFrom(field.getType())) continue;
        ArchRule rule = (ArchRule) field.get(null);
        add(
            "ARCHUNIT_VIOLATION",
            "violation",
            "P1",
            rules.getSimpleName() + "." + field.getName(),
            "src/test/java/cafe/woden/ircclient/architecture/" + rules.getSimpleName() + ".java",
            rule.getDescription(),
            rule.evaluate(classes).getFailureReport().getDetails());
      }
    }
  }

  private static boolean isComponent(JavaClass type) {
    return type.isAnnotatedWith(Component.class) || type.isMetaAnnotatedWith(Component.class);
  }

  static boolean hasRole(JavaClass type, Class<? extends Annotation> role) {
    JavaClass component = type.getBaseComponentType();
    if (component.isAnnotatedWith(role) || component.isMetaAnnotatedWith(role)) return true;
    for (var pkg = component.getPackage(); pkg != null; pkg = pkg.getParent().orElse(null)) {
      if (pkg.isAnnotatedWith(role) || pkg.isMetaAnnotatedWith(role)) return true;
    }
    return false;
  }

  private String location(JavaClass type) {
    return ArchitectureReport.location(repository, owners.get(type.getName()), type);
  }

  private void add(
      String rule,
      String kind,
      String priority,
      String subject,
      String location,
      String recommendation,
      List<String> evidence) {
    if (evidence.isEmpty()) return;
    List<String> unique = evidence.stream().distinct().sorted().toList();
    String id = rule + ":" + subject;
    findings.put(
        id,
        new Finding(
            id,
            rule,
            kind,
            priority,
            subject,
            location,
            recommendation,
            unique.size(),
            unique.stream().limit(20).toList()));
  }
}
