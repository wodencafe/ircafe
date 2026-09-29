package cafe.woden.ircclient.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Reads main class outputs supplied by architectureTest, never test or dependency classes. */
final class CompiledSubprojects {

  private CompiledSubprojects() {}

  static Set<String> projectNames(String prefix) {
    String propertyPrefix = "ircafe.architecture.classes.";
    Set<String> projects =
        System.getProperties().stringPropertyNames().stream()
            .filter(name -> name.startsWith(propertyPrefix + prefix))
            .map(name -> name.substring(propertyPrefix.length()))
            .collect(Collectors.toCollection(java.util.TreeSet::new));
    if (projects.isEmpty()) {
      throw new AssertionError(
          "No compiled projects supplied for " + prefix + "; run ./gradlew architectureTest");
    }
    return projects;
  }

  static JavaClasses importProject(String projectName) {
    String locations = System.getProperty("ircafe.architecture.classes." + projectName, "");
    if (locations.isBlank()) {
      throw new AssertionError(
          "No compiled outputs supplied for " + projectName + "; run ./gradlew architectureTest");
    }
    var directories =
        Arrays.stream(locations.split(Pattern.quote(File.pathSeparator)))
            .map(Path::of)
            .filter(Files::isDirectory)
            .toList();
    JavaClasses classes = new ClassFileImporter().importPaths(directories);
    if (classes.size() == 0) {
      throw new AssertionError(
          "No main classes imported for " + projectName + " from " + locations);
    }
    return classes;
  }

  static Set<String> names(JavaClasses classes) {
    return classes.stream().map(JavaClass::getName).collect(Collectors.toUnmodifiableSet());
  }
}
