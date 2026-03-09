package cafe.woden.ircclient.app;

import cafe.woden.ircclient.irc.IrcShutdownPort;
import cafe.woden.ircclient.util.VirtualThreads;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ApplicationLayer
public class ApplicationShutdownCoordinator {
  private static final Logger log = LoggerFactory.getLogger(ApplicationShutdownCoordinator.class);

  // Hard-stop fallback to avoid hanging forever on shutdown races during startup/connect.
  private static final long SHUTDOWN_WATCHDOG_MS = 8000L;
  private static final long SHUTDOWN_IRC_PHASE_TIMEOUT_MS = 3000L;
  private static final long SHUTDOWN_SPRING_PHASE_TIMEOUT_MS = 3000L;

  private final ConfigurableApplicationContext applicationContext;
  private final IrcShutdownPort ircShutdownPort;
  private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

  public ApplicationShutdownCoordinator(
      ConfigurableApplicationContext applicationContext,
      @Qualifier("ircShutdownPort") IrcShutdownPort ircShutdownPort) {
    this.applicationContext = applicationContext;
    this.ircShutdownPort = ircShutdownPort;
  }

  public void shutdown() {
    if (!shutdownStarted.compareAndSet(false, true)) {
      return;
    }

    VirtualThreads.start(
        "ircafe-shutdown-watchdog",
        () -> {
          try {
            Thread.sleep(SHUTDOWN_WATCHDOG_MS);
          } catch (InterruptedException ignored) {
            return;
          }
          log.error(
              "[ircafe] Shutdown watchdog fired after {}ms; forcing JVM halt(0).",
              SHUTDOWN_WATCHDOG_MS);
          Runtime.getRuntime().halt(0);
        });

    VirtualThreads.start(
        "ircafe-shutdown",
        () -> {
          int exitCode = 0;
          log.info("[ircafe] shutdown started: coordinator thread active");

          runPhaseWithTimeout(
              "irc-client-shutdown",
              SHUTDOWN_IRC_PHASE_TIMEOUT_MS,
              this::shutdownIrcClientBestEffort);

          runPhaseWithTimeout(
              "spring-context-close",
              SHUTDOWN_SPRING_PHASE_TIMEOUT_MS,
              this::closeSpringContextBestEffort);

          log.info("[ircafe] shutdown complete; terminating JVM with code {}", exitCode);
          Runtime.getRuntime().halt(exitCode);
        });
  }

  private void shutdownIrcClientBestEffort() {
    try {
      ircShutdownPort.shutdownNow();
    } catch (Throwable t) {
      log.warn("[ircafe] Error while shutting down IRC client service", t);
    }
  }

  private void closeSpringContextBestEffort() {
    try {
      if (isApplicationContextAlreadyClosed()) {
        log.debug("[ircafe] Spring context already closed before shutdown exit call.");
      } else {
        int springExitCode = SpringApplication.exit(applicationContext, () -> 0);
        if (springExitCode != 0) {
          log.debug(
              "[ircafe] Spring exit code {} ignored for normal desktop shutdown.", springExitCode);
        }
      }
    } catch (IllegalStateException ise) {
      if (isAlreadyClosedException(ise)) {
        // Benign shutdown race: another path already closed the context.
        log.debug("[ircafe] Spring context already closed during shutdown.", ise);
      } else {
        log.warn("[ircafe] Error while closing Spring context", ise);
      }
    } catch (Throwable t) {
      log.warn("[ircafe] Error while closing Spring context", t);
    }
  }

  private void runPhaseWithTimeout(String phase, long timeoutMs, Runnable action) {
    String token = sanitizePhaseToken(phase);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread worker =
        VirtualThreads.start(
            "ircafe-shutdown-" + token,
            () -> {
              try {
                action.run();
              } catch (Throwable t) {
                failure.set(t);
              }
            });

    try {
      worker.join(timeoutMs);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      log.warn("[ircafe] Shutdown phase '{}' interrupted while waiting.", phase, ie);
      return;
    }

    if (worker.isAlive()) {
      log.error(
          "[ircafe] Shutdown phase '{}' exceeded {}ms; continuing shutdown.", phase, timeoutMs);
      return;
    }

    Throwable thrown = failure.get();
    if (thrown != null) {
      log.warn("[ircafe] Shutdown phase '{}' failed.", phase, thrown);
      return;
    }

    log.info("[ircafe] Shutdown phase '{}' completed.", phase);
  }

  private static String sanitizePhaseToken(String phase) {
    String input = phase == null ? "" : phase.trim().toLowerCase(Locale.ROOT);
    if (input.isEmpty()) return "phase";
    String token = input.replaceAll("[^a-z0-9]+", "-");
    token = token.replaceAll("^-+", "").replaceAll("-+$", "");
    return token.isEmpty() ? "phase" : token;
  }

  private boolean isApplicationContextAlreadyClosed() {
    try {
      if (applicationContext instanceof AbstractApplicationContext ac) {
        return !ac.isActive();
      }
    } catch (Throwable ignored) {
    }
    return false;
  }

  private static boolean isAlreadyClosedException(IllegalStateException ex) {
    String msg = ex == null ? "" : String.valueOf(ex.getMessage());
    msg = msg.toLowerCase(Locale.ROOT);
    return msg.contains("has been closed already")
        || msg.contains("beanfactory not initialized or already closed");
  }
}
