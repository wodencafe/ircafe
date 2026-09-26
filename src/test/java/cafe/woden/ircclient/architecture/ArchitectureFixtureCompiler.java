package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;

/** Compiles isolated fixtures without putting intentional violations on the test classpath. */
final class ArchitectureFixtureCompiler {

  private ArchitectureFixtureCompiler() {}

  static Path compile(Path directory, Map<String, String> sources, Class<?>... dependencies)
      throws IOException {
    Path sourceDirectory = Files.createDirectories(directory.resolve("sources"));
    Path output = Files.createDirectories(directory.resolve("classes"));
    List<Path> files = new ArrayList<>();
    for (var source : sources.entrySet()) {
      Path file = sourceDirectory.resolve(source.getKey());
      Files.createDirectories(file.getParent());
      Files.writeString(file, source.getValue());
      files.add(file);
    }

    var compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("architecture fixtures require the configured JDK").isNotNull();
    List<String> options = new ArrayList<>(List.of("-proc:none", "-d", output.toString()));
    if (dependencies.length > 0) {
      options.addAll(
          List.of(
              "-classpath",
              Arrays.stream(dependencies)
                  .map(ArchitectureFixtureCompiler::classLocation)
                  .distinct()
                  .collect(Collectors.joining(File.pathSeparator))));
    }
    StringWriter diagnostics = new StringWriter();
    try (var fileManager = compiler.getStandardFileManager(null, null, null)) {
      boolean compiled =
          compiler
              .getTask(
                  diagnostics,
                  fileManager,
                  null,
                  options,
                  null,
                  fileManager.getJavaFileObjectsFromPaths(files))
              .call();
      assertThat(compiled).as(diagnostics.toString()).isTrue();
    }
    return output;
  }

  private static String classLocation(Class<?> type) {
    try {
      return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    } catch (URISyntaxException e) {
      throw new AssertionError("Cannot locate fixture dependency " + type.getName(), e);
    }
  }
}
