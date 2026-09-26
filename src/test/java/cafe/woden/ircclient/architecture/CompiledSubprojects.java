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
