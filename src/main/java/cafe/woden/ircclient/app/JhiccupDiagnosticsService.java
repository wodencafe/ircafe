package cafe.woden.ircclient.app;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.util.VirtualThreads;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Optional external jHiccup process integration. */
@Component
public class JhiccupDiagnosticsService {
  private static final Logger log = LoggerFactory.getLogger(JhiccupDiagnosticsService.class);

  private final ApplicationDiagnosticsService diagnostics;
  private final UiProperties uiProps;
  private final RuntimeConfigStore runtimeConfig;

  private volatile Process process;
  private volatile boolean started = false;

  public JhiccupDiagnosticsService(
      ApplicationDiagnosticsService diagnostics,
      UiProperties uiProps,
      RuntimeConfigStore runtimeConfig) {
    this.diagnostics = diagnostics;
    this.uiProps = uiProps;
    this.runtimeConfig = runtimeConfig;
  }

  @PostConstruct
  void start() {
    UiProperties.Jhiccup cfg = resolveSettings();
    if (cfg == null || !Boolean.TRUE.equals(cfg.enabled())) {
      diagnostics.appendJhiccupStatus("Disabled by configuration.");
      return;
    }

    String jarPathText = Objects.toString(cfg.jarPath(), "").trim();
    if (jarPathText.isEmpty()) {
      diagnostics.appendJhiccupError(
          "Enabled but no jarPath configured. Set ircafe.ui.appDiagnostics.jhiccup.jarPath.");
      return;
    }

    Path jarPath = resolveJarPath(jarPathText);
    if (jarPath == null || !Files.isRegularFile(jarPath)) {
      diagnostics.appendJhiccupError("jHiccup jar not found: " + jarPathText);
      return;
    }

    List<String> cmd = buildCommand(cfg, jarPath);
    if (cmd.isEmpty()) {
      diagnostics.appendJhiccupError("Could not build jHiccup command.");
      return;
    }

    try {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      process = p;
      started = true;
      diagnostics.appendJhiccupStatus("Started: " + String.join(" ", cmd));
      long pid = safePid(p);
      if (pid > 0L) {
        diagnostics.appendJhiccupStatus("PID: " + pid);
      }

      VirtualThreads.start("ircafe-jhiccup-reader", () -> streamOutput(p));
      VirtualThreads.start("ircafe-jhiccup-waiter", () -> waitForExit(p));
    } catch (Exception e) {
      diagnostics.appendJhiccupError("Failed to start jHiccup: " + summarize(e));
      log.warn("[ircafe] failed to start jHiccup process", e);
    }
  }

  @PreDestroy
  void shutdown() {
    Process p = process;
    if (p == null) return;
    try {
      p.destroy();
      if (!p.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
        p.destroyForcibly();
      }
    } catch (Exception ignored) {
    }
  }

  private UiProperties.Jhiccup resolveSettings() {
    UiProperties.AppDiagnostics d = uiProps != null ? uiProps.appDiagnostics() : null;
    return d != null ? d.jhiccup() : null;
  }

  private Path resolveJarPath(String jarPathText) {
    try {
      Path candidate = Path.of(jarPathText);
      if (candidate.isAbsolute()) return candidate.normalize();
      Path runtimePath = runtimeConfig != null ? runtimeConfig.runtimeConfigPath() : null;
      Path base = runtimePath != null ? runtimePath.getParent() : null;
      if (base == null) return candidate.toAbsolutePath().normalize();
      return base.resolve(candidate).normalize();
    } catch (Exception e) {
      return null;
    }
  }

  private static List<String> buildCommand(UiProperties.Jhiccup cfg, Path jarPath) {
    String javaCmd = Objects.toString(cfg.javaCommand(), "").trim();
    if (javaCmd.isEmpty()) javaCmd = "java";
    List<String> out = new ArrayList<>();
    out.add(javaCmd);
    out.add("-jar");
    out.add(jarPath.toString());
    if (cfg.args() != null && !cfg.args().isEmpty()) {
      out.addAll(cfg.args());
    }
    return out;
  }

  private void streamOutput(Process p) {
    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) {
        String msg = sanitizeLine(line);
        if (msg.isEmpty()) continue;
        if (looksLikeError(msg)) {
          diagnostics.appendJhiccupError(msg);
        } else {
          diagnostics.appendJhiccupStatus(msg);
        }
      }
    } catch (Exception e) {
      if (started) {
        diagnostics.appendJhiccupError("Output stream ended with error: " + summarize(e));
      }
    }
  }

  private void waitForExit(Process p) {
    try {
      int code = p.waitFor();
      if (code == 0) {
        diagnostics.appendJhiccupStatus("jHiccup exited normally (code 0).");
      } else {
        diagnostics.appendJhiccupError("jHiccup exited with code " + code + ".");
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      diagnostics.appendJhiccupStatus("jHiccup wait interrupted.");
    } catch (Exception e) {
      diagnostics.appendJhiccupError("Failed waiting for jHiccup exit: " + summarize(e));
    } finally {
      if (process == p) {
        process = null;
      }
      started = false;
    }
  }

  private static boolean looksLikeError(String msg) {
    String s = Objects.toString(msg, "").toLowerCase(Locale.ROOT);
    return s.contains("error")
        || s.contains("exception")
        || s.contains("failed")
        || s.contains("unable")
        || s.contains("fatal");
  }

  private static String sanitizeLine(String line) {
    String s = Objects.toString(line, "").trim();
    if (s.isEmpty()) return "";
    // Keep diagnostics lines bounded so one malformed line doesn't spam huge blobs.
    if (s.length() > 600) {
      return s.substring(0, 600) + "...";
    }
    return s;
  }

  private static long safePid(Process p) {
    if (p == null) return -1L;
    try {
      return p.pid();
    } catch (Throwable ignored) {
      return -1L;
    }
  }

  private static String summarize(Throwable t) {
    if (t == null) return "(null throwable)";
    Throwable root = t;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String type = root.getClass().getSimpleName();
    if (type == null || type.isBlank()) {
      type = root.getClass().getName();
    }
    String msg = Objects.toString(root.getMessage(), "").trim();
    return msg.isEmpty() ? type : (type + ": " + msg);
  }
}
