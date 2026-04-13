package cafe.woden.ircclient.util;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class CompiledPluginJarSupport {

  private CompiledPluginJarSupport() {}

  public static Map<String, String> compatibleManifest(String pluginId, String pluginVersion) {
    LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
    attributes.put(PluginServiceLoaderSupport.PLUGIN_ID_ATTRIBUTE, pluginId);
    attributes.put(PluginServiceLoaderSupport.PLUGIN_VERSION_ATTRIBUTE, pluginVersion);
    attributes.put(
        PluginServiceLoaderSupport.PLUGIN_API_VERSION_ATTRIBUTE,
        Integer.toString(PluginServiceLoaderSupport.SUPPORTED_PLUGIN_API_VERSION));
    return Map.copyOf(attributes);
  }

  public static Path writePluginJar(
      Path jarPath,
      String providerClassName,
      String providerSource,
      String serviceTypeName,
      Map<String, String> manifestAttributes)
      throws IOException {
    Path normalizedJarPath = jarPath.toAbsolutePath().normalize();
    Path baseDirectory =
        Files.createDirectories(
            Objects.requireNonNull(normalizedJarPath.getParent(), "jarPath.parent"));
    Path sourceRoot = Files.createDirectories(baseDirectory.resolve("src"));
    Path classesRoot = Files.createDirectories(baseDirectory.resolve("classes"));

    Path sourcePath = sourceRoot.resolve(providerClassName.replace('.', '/') + ".java");
    Files.createDirectories(Objects.requireNonNull(sourcePath.getParent()));
    Files.writeString(sourcePath, providerSource, StandardCharsets.UTF_8);

    compileSource(sourcePath, classesRoot);

    Manifest manifest = new Manifest();
    Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    for (Map.Entry<String, String> entry :
        Objects.requireNonNullElse(manifestAttributes, Map.<String, String>of()).entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      attributes.putValue(entry.getKey(), entry.getValue());
    }

    try (JarOutputStream out =
        new JarOutputStream(Files.newOutputStream(normalizedJarPath), manifest)) {
      out.putNextEntry(new JarEntry("META-INF/services/" + serviceTypeName));
      out.write((providerClassName + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
      try (var stream = Files.walk(classesRoot)) {
        stream
            .filter(Files::isRegularFile)
            .forEach(compiledClass -> writeCompiledClassEntry(out, classesRoot, compiledClass));
      }
    }

    return normalizedJarPath;
  }

  private static void compileSource(Path sourcePath, Path classesRoot) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("[ircafe] JDK compiler is not available for plugin tests");
    }

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    StringWriter compilerOutput = new StringWriter();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromPaths(List.of(sourcePath));
      List<String> options =
          List.of(
              "--release",
              "25",
              "-classpath",
              System.getProperty("java.class.path"),
              "-d",
              classesRoot.toString());
      Boolean success =
          compiler
              .getTask(compilerOutput, fileManager, diagnostics, options, null, compilationUnits)
              .call();
      if (Boolean.TRUE.equals(success)) {
        return;
      }
    }

    StringBuilder message = new StringBuilder("[ircafe] failed to compile test plugin source");
    if (compilerOutput.getBuffer().length() > 0) {
      message.append(": ").append(compilerOutput);
    }
    diagnostics
        .getDiagnostics()
        .forEach(diagnostic -> message.append(System.lineSeparator()).append(diagnostic));
    throw new IllegalStateException(message.toString());
  }

  private static void writeCompiledClassEntry(
      JarOutputStream out, Path classesRoot, Path compiledClassPath) {
    Path relativePath = classesRoot.relativize(compiledClassPath);
    try {
      out.putNextEntry(new JarEntry(relativePath.toString().replace('\\', '/')));
      out.write(Files.readAllBytes(compiledClassPath));
      out.closeEntry();
    } catch (IOException e) {
      throw new IllegalStateException(
          "[ircafe] failed to write compiled plugin class " + compiledClassPath, e);
    }
  }
}
