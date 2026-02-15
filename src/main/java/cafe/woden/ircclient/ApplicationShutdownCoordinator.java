package cafe.woden.ircclient;

import cafe.woden.ircclient.irc.IrcClientService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ApplicationShutdownCoordinator {
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
    ircClientService.shutdownNow();
    int exitCode = SpringApplication.exit(applicationContext, () -> 0);
    System.exit(exitCode);
  }
}
