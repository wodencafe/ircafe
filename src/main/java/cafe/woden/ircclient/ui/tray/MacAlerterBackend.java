package cafe.woden.ircclient.ui.tray;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.SerialDisposable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bundled macOS notification helper, owned and closed by {@link TrayNotificationService}. */
final class MacAlerterBackend implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(MacAlerterBackend.class);
  private static final String BUNDLE_ID = "cafe.woden.ircafe";
  private static final String OPEN_ACTION = "Open IRCafe";
  private static final int TIMEOUT_SECONDS = 5;
  private static final int FORCE_CLOSE_SECONDS = TIMEOUT_SECONDS + 2;
  private static final int MAX_ACTIVE_PROCESSES = 10;
  private static final int MAX_OUTPUT_BYTES = 1024;

  private final Scheduler timerScheduler;
  private final Scheduler ioScheduler;
  private final Supplier<String> launcherPath;
  private final ProcessStarter processStarter;
  private final Map<Process, SerialDisposable> activeProcesses = new ConcurrentHashMap<>();
  private volatile boolean closed;

  MacAlerterBackend(Scheduler timerScheduler, Scheduler ioScheduler) {
    this(
        timerScheduler,
        ioScheduler,
        () -> System.getProperty("jpackage.app-path"),
        ProcessBuilder::start);
  }

  MacAlerterBackend(
      Scheduler timerScheduler,
      Scheduler ioScheduler,
      Supplier<String> launcherPath,
      ProcessStarter processStarter) {
    this.timerScheduler = Objects.requireNonNull(timerScheduler, "timerScheduler");
    this.ioScheduler = Objects.requireNonNull(ioScheduler, "ioScheduler");
    this.launcherPath = Objects.requireNonNull(launcherPath, "launcherPath");
    this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
  }

  /**
   * Called off the EDT. False requests immediate fallback; later process failures invoke {@code
   * onFailure} on the I/O scheduler. Clicks are dispatched on the EDT.
   */
  boolean tryNotify(String title, String body, Runnable onClick, Runnable onFailure) {
    Objects.requireNonNull(onFailure, "onFailure");
    try {
      String appPath = launcherPath.get();
      if (appPath == null || appPath.isBlank()) return false;
      File macOsDir = new File(appPath).getParentFile();
      if (macOsDir == null) return false;
      File resourcesDir = new File(macOsDir, "../Resources").getCanonicalFile();
      File executable = new File(resourcesDir, "alerter");
      if (!executable.isFile()) return false;

      ProcessBuilder builder =
          new ProcessBuilder(command(executable, title, body, onClick != null));
      // A diagnostic must not become part of the action token or fill an undrained stderr pipe.
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      synchronized (activeProcesses) {
        if (closed) return true;
        if (activeProcesses.size() >= MAX_ACTIVE_PROCESSES) {
          log.debug("[ircafe] macOS notification helper limit reached");
          return false;
        }
        track(processStarter.start(builder), onClick, onFailure);
      }
      return true;
    } catch (Exception e) {
      log.debug("[ircafe] could not start macOS notification helper", e);
      return false;
    }
  }

  private static List<String> command(
      File executable, String title, String body, boolean clickable) {
    List<String> command = new ArrayList<>();
    command.add(executable.getAbsolutePath());
    // Attached option values preserve IRC text beginning with '-' in Swift ArgumentParser.
    command.add("--title=" + sanitizeText(title));
    command.add("--message=" + sanitizeText(body));
    command.add("--sender=" + BUNDLE_ID);
    command.add("--group=" + BUNDLE_ID);
    command.add("--timeout=" + TIMEOUT_SECONDS);
    if (clickable) command.add("--actions=" + OPEN_ACTION);
    return command;
  }

  private void track(Process process, Runnable onClick, Runnable onFailure) {
    SerialDisposable timeout = new SerialDisposable();
    activeProcesses.put(process, timeout);
    timeout.set(
        timerScheduler.scheduleDirect(
            () -> forceClose(process), FORCE_CLOSE_SECONDS, TimeUnit.SECONDS));
    ioScheduler.scheduleDirect(() -> awaitResult(process, timeout, onClick, onFailure));
  }

  private void awaitResult(
      Process process, SerialDisposable timeout, Runnable onClick, Runnable onFailure) {
    // Drain stdout while running; waiting for exit first can deadlock on a full pipe.
    String result = readOutput(process);
    int exitCode;
    try {
      exitCode = process.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      forceClose(process);
      return;
    }
    if (activeProcesses.remove(process) == null) return;
    timeout.dispose();
    if (closed) return;
    if (exitCode != 0) {
      log.debug("[ircafe] macOS notification helper failed with exit code {}", exitCode);
      onFailure.run();
    } else if (onClick != null
        && (OPEN_ACTION.equals(result) || "@CONTENTCLICKED".equals(result))) {
      SwingUtilities.invokeLater(
          () -> {
            if (!closed) onClick.run();
          });
    }
  }

  private static String readOutput(Process process) {
    try (var input = process.getInputStream()) {
      byte[] output = input.readNBytes(MAX_OUTPUT_BYTES);
      input.transferTo(OutputStream.nullOutputStream());
      return new String(output, StandardCharsets.UTF_8).trim();
    } catch (IOException e) {
      log.debug("[ircafe] could not read macOS notification result", e);
      return "";
    }
  }

  private static String sanitizeText(String value) {
    return Objects.toString(value, "").replace('\n', ' ').replace('\r', ' ').trim();
  }

  private void forceClose(Process process) {
    SerialDisposable timeout = activeProcesses.remove(process);
    if (timeout == null) return;
    timeout.dispose();
    log.debug("[ircafe] stopping macOS notification helper");
    process.destroyForcibly();
  }

  @Override
  public void close() {
    synchronized (activeProcesses) {
      closed = true;
      for (Process process : List.copyOf(activeProcesses.keySet())) {
        forceClose(process);
      }
    }
  }

  @FunctionalInterface
  interface ProcessStarter {
    Process start(ProcessBuilder builder) throws IOException;
  }
}
