package cafe.woden.ircclient.ui.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the actual Dorkbox methods on the resolved (and packaged) Gradle classpath. */
class DorkboxOsProcessTest {
  @TempDir Path tempDir;

  @Test
  void readsStdoutLargerThanAPipeWithoutWaitingForTheProcessTimeout() throws Exception {
    assertEquals("x".repeat(256 * 1024), invoke("execute", "stdout", 30));
  }

  @Test
  void drainsStderrWithoutIncludingItInDetectionOutput() throws Exception {
    assertEquals("done", invoke("execute", "stderr", 30));
  }

  @Test
  void statusCommandsCannotBlockOnEitherOutputPipe() throws Exception {
    assertEquals(true, invoke("executeStatus", "both", 30));
    assertEquals(false, invoke("executeStatus", "failure", 30));
  }

  @Test
  void captureRejectsOversizedOutput() {
    ExecutionException failure =
        assertThrows(ExecutionException.class, () -> invoke("execute", "oversized", 30));
    IOException cause = assertInstanceOf(IOException.class, failure.getCause());
    assertEquals("Desktop detection output exceeds 1 MiB", cause.getMessage());
  }

  @Test
  void captureTimeoutKillsTheChild() throws Exception {
    verifyTimeout("execute");
  }

  @Test
  void statusTimeoutKillsTheChild() throws Exception {
    verifyTimeout("executeStatus");
  }

  @Test
  void preservesTheKdeApiUsedBySystemTray() throws Exception {
    assertEquals(
        double.class,
        Class.forName("dorkbox.os.OS$DesktopEnv", false, getClass().getClassLoader())
            .getMethod("getPlasmaVersion")
            .getReturnType());
  }

  private void verifyTimeout(String method) throws Exception {
    ExecutionException failure =
        assertThrows(ExecutionException.class, () -> invoke(method, "sleep", 1));
    IOException cause = assertInstanceOf(IOException.class, failure.getCause());
    assertEquals("Desktop detection command timed out", cause.getMessage());
    long pid = Long.parseLong(Files.readString(tempDir.resolve("child.pid")));
    assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
  }

  private Object invoke(String methodName, String mode, long timeoutSeconds) throws Exception {
    Class<?> os = Class.forName("dorkbox.os.OS");
    Method method = os.getDeclaredMethod(methodName, String[].class, long.class);
    method.setAccessible(true);
    Object instance = os.getField("INSTANCE").get(null);
    Path pidFile = tempDir.resolve("child.pid");
    String java =
        Path.of(System.getProperty("java.home"), "bin", "java").toAbsolutePath().toString();
    String classes =
        Path.of(OutputProducer.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString();
    String[] command = {
      java, "-cp", classes, OutputProducer.class.getName(), mode, pidFile.toString()
    };
    Set<Path> capturesBefore = captureFiles();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var result =
          executor.submit(
              () -> {
                try {
                  return method.invoke(instance, command, timeoutSeconds);
                } catch (InvocationTargetException ex) {
                  if (ex.getCause() instanceof Exception cause) throw cause;
                  throw ex;
                }
              });
      boolean completed = false;
      try {
        // Old OS 1.8 waits 30s on full pipes. Fail promptly, then clean up its child too.
        Object output = result.get(10, TimeUnit.SECONDS);
        completed = true;
        return output;
      } catch (ExecutionException ex) {
        completed = true;
        throw ex;
      } finally {
        result.cancel(true);
        if (Files.exists(pidFile)) {
          long pid = Long.parseLong(Files.readString(pidFile));
          var child = ProcessHandle.of(pid);
          boolean leaked = completed && child.map(ProcessHandle::isAlive).orElse(false);
          child.ifPresent(ProcessHandle::destroyForcibly);
          assertFalse(leaked, "Desktop detection left its subprocess running");
        }
        if (completed) {
          assertEquals(
              capturesBefore, captureFiles(), "Desktop detection left a capture file behind");
        }
      }
    }
  }

  private Set<Path> captureFiles() throws IOException {
    try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      return files
          .filter(path -> path.getFileName().toString().startsWith("ircafe-os-"))
          .collect(Collectors.toSet());
    }
  }

  /** Separate JVM producing enough output to fill any ordinary subprocess pipe. */
  public static class OutputProducer {
    public static void main(String[] args) throws Exception {
      Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
      String output = "x".repeat(256 * 1024);
      switch (args[0]) {
        case "stdout" -> System.out.print(output);
        case "stderr" -> {
          System.err.print(output);
          System.out.print("done");
        }
        case "both" -> {
          System.out.print(output);
          System.err.print(output);
        }
        case "oversized" -> System.out.print(output.repeat(8));
        case "failure" -> System.exit(7);
        case "sleep" -> Thread.sleep(60_000);
        default -> throw new IllegalArgumentException(args[0]);
      }
    }
  }
}
