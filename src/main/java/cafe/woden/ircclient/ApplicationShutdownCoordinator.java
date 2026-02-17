package cafe.woden.ircclient;

import cafe.woden.ircclient.irc.IrcClientService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
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
      ConfigurableApplicationContext applicationContext,
      IrcClientService ircClientService
  ) {
    this.applicationContext = applicationContext;
    this.ircClientService = ircClientService;
  }

  public void shutdown() {
    if (!shutdownStarted.compareAndSet(false, true)) {
      return;
    }

    Thread watchdog = new Thread(() -> {
      try {
        Thread.sleep(SHUTDOWN_WATCHDOG_MS);
      } catch (InterruptedException ignored) {
        return;
      }
      log.error("[ircafe] Shutdown watchdog fired after {}ms; forcing JVM halt.", SHUTDOWN_WATCHDOG_MS);
      Runtime.getRuntime().halt(1);
    }, "ircafe-shutdown-watchdog");
    watchdog.setDaemon(true);
    watchdog.start();

    Thread shutdownThread = new Thread(() -> {
      int exitCode = 0;
      try {
        ircClientService.shutdownNow();
      } catch (Throwable t) {
        log.warn("[ircafe] Error while shutting down IRC client service", t);
        exitCode = 1;
      }

      try {
        exitCode = SpringApplication.exit(applicationContext, () -> 0);
      } catch (Throwable t) {
        log.warn("[ircafe] Error while closing Spring context", t);
        exitCode = 1;
      }

      System.exit(exitCode);
    }, "ircafe-shutdown");
    shutdownThread.setDaemon(false);
    shutdownThread.start();
  }
}
