package cafe.woden.ircclient;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.util.VirtualThreads;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ApplicationShutdownCoordinator {
  private static final Logger log = LoggerFactory.getLogger(ApplicationShutdownCoordinator.class);

  // Hard-stop fallback to avoid hanging forever on shutdown races during startup/connect.
  private static final long SHUTDOWN_WATCHDOG_MS = 8000L;

  private final ConfigurableApplicationContext applicationContext;
  private final IrcClientService ircClientService;
  private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

  public ApplicationShutdownCoordinator(
      ConfigurableApplicationContext applicationContext, IrcClientService ircClientService) {
    this.applicationContext = applicationContext;
    this.ircClientService = ircClientService;
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
              "[ircafe] Shutdown watchdog fired after {}ms; forcing JVM halt.",
              SHUTDOWN_WATCHDOG_MS);
          Runtime.getRuntime().halt(1);
        });

    VirtualThreads.start(
        "ircafe-shutdown",
        () -> {
          int exitCode = 0;
          try {
            ircClientService.shutdownNow();
          } catch (Throwable t) {
            log.warn("[ircafe] Error while shutting down IRC client service", t);
            exitCode = 1;
          }

          try {
            if (isApplicationContextAlreadyClosed()) {
              log.debug(
                  "[ircafe] Spring context already closed before shutdown coordinator exit call.");
            } else {
              exitCode = SpringApplication.exit(applicationContext, () -> 0);
            }
          } catch (IllegalStateException ise) {
            if (isAlreadyClosedException(ise)) {
              // Benign shutdown race: another path already closed the context.
              log.debug("[ircafe] Spring context already closed during shutdown.", ise);
            } else {
              log.warn("[ircafe] Error while closing Spring context", ise);
              exitCode = 1;
            }
          } catch (Throwable t) {
            log.warn("[ircafe] Error while closing Spring context", t);
            exitCode = 1;
          }

          System.exit(exitCode);
        });
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
