package cafe.woden.ircclient.ui.terminal;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Mirrors System.out/System.err to an in-memory buffer and to subscribers.
 *
 * <p>This installs a "tee" PrintStream that still writes to the original console stream,
 * but also records the output for the in-app terminal dock.
 */
@Component
@Lazy(false)
public class ConsoleTeeService {

  @PostConstruct
  public void install() {
    ConsoleTeeHub.install();
  }

  @PreDestroy
  public void restore() {
    ConsoleTeeHub.restore();
  }

  /** Current buffered terminal content (best-effort snapshot). */
  public String snapshot() {
    return ConsoleTeeHub.snapshot();
  }

  /** Subscribe to future output. */
  public AutoCloseable addListener(Consumer<String> listener) {
    Objects.requireNonNull(listener, "listener");
    return ConsoleTeeHub.addListener(listener);
  }
}
