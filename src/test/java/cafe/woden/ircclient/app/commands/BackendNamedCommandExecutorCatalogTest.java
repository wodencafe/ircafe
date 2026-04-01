package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackendNamedCommandExecutorCatalogTest {

  private static final BackendNamedCommandExecutionContext TEST_CONTEXT =
      new BackendNamedCommandExecutionContext() {
        private final TargetRef statusTarget = new TargetRef("test", "status");

        @Override
        public TargetRef activeTarget() {
          return null;
        }

        @Override
        public TargetRef safeStatusTarget() {
          return statusTarget;
        }

        @Override
        public boolean isConnected(String serverId) {
          return true;
        }

        @Override
        public void appendStatus(TargetRef target, String prefix, String message) {}

        @Override
        public void appendError(TargetRef target, String prefix, String message) {}

        @Override
        public void ensureTargetExists(TargetRef target) {}

        @Override
        public void selectTarget(TargetRef target) {}

        @Override
        public Completable sendRaw(String serverId, String line) {
          return Completable.complete();
        }
      };

  @TempDir Path tempDir;

  @Test
  void loadsExecutionProvidersFromPluginDirectoryJar() throws Exception {
    Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
    writePluginJar(pluginDir.resolve("backendexec.jar"));

    BackendNamedCommandExecutorCatalog catalog =
        BackendNamedCommandExecutorCatalog.installed(
            pluginDir, BackendNamedCommandExecutorCatalogTest.class.getClassLoader());
    CompositeDisposable disposables = new CompositeDisposable();
    try {
      assertTrue(
          catalog.handle(
              TEST_CONTEXT, disposables, new ParsedInput.BackendNamed("backendexec", "hello")));
    } finally {
      disposables.dispose();
      catalog.shutdown();
    }
  }

  @Test
  void loadsExecutionProvidersFromPluginsNextToRuntimeConfig() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    writePluginJar(pluginDir.resolve("backendexec.jar"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    BackendNamedCommandExecutorCatalog catalog =
        new BackendNamedCommandExecutorCatalog(runtimeConfigPathPort, java.util.List.of());
    CompositeDisposable disposables = new CompositeDisposable();
    try {
      assertTrue(
          catalog.handle(
              TEST_CONTEXT, disposables, new ParsedInput.BackendNamed("backendexec", "hello")));
    } finally {
      disposables.dispose();
      catalog.shutdown();
    }
  }

  @Test
  void duplicateExecutionCommandRegistrationsFailFast() {
    BackendNamedCommandExecutor first =
        new BackendNamedCommandExecutor() {
          @Override
          public Set<String> handledCommandNames() {
            return Set.of("backendexec");
          }

          @Override
          public boolean handle(
              BackendNamedCommandExecutionContext context,
              io.reactivex.rxjava3.disposables.CompositeDisposable disposables,
              ParsedInput.BackendNamed command) {
            return false;
          }
        };
    BackendNamedCommandExecutor second =
        new BackendNamedCommandExecutor() {
          @Override
          public Set<String> handledCommandNames() {
            return Set.of("backendexec");
          }

          @Override
          public boolean handle(
              BackendNamedCommandExecutionContext context,
              io.reactivex.rxjava3.disposables.CompositeDisposable disposables,
              ParsedInput.BackendNamed command) {
            return false;
          }
        };

    assertThrows(
        IllegalStateException.class,
        () -> BackendNamedCommandExecutorCatalog.fromExecutors(java.util.List.of(first, second)));
  }

  private static void writePluginJar(Path jarPath) throws IOException {
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath))) {
      out.putNextEntry(
          new JarEntry("META-INF/services/" + BackendNamedCommandExecutor.class.getName()));
      out.write(
          (PluginProvidedBackendNamedCommandExecutor.class.getName() + System.lineSeparator())
              .getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  public static final class PluginProvidedBackendNamedCommandExecutor
      implements BackendNamedCommandExecutor {

    @Override
    public Set<String> handledCommandNames() {
      return Set.of("backendexec");
    }

    @Override
    public boolean handle(
        BackendNamedCommandExecutionContext context,
        CompositeDisposable disposables,
        ParsedInput.BackendNamed command) {
      return command != null && "backendexec".equals(command.command());
    }
  }
}
