package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Decorator that adds lightweight logging around outbound command dispatch.
 */
@Component
@Primary
public class ObservedOutboundCommandDispatcher extends OutboundCommandDispatcherDecorator {

  private static final Logger log = LoggerFactory.getLogger(ObservedOutboundCommandDispatcher.class);

  public ObservedOutboundCommandDispatcher(
      @Qualifier("defaultOutboundCommandDispatcher") OutboundCommandDispatcher delegate
  ) {
    super(delegate);
  }

  @Override
  public void dispatch(CompositeDisposable disposables, ParsedInput input) {
    if (input == null) return;

    String command = commandKey(input);
    long startedNanos = System.nanoTime();
    boolean failed = false;

    try {
      delegate.dispatch(disposables, input);
    } catch (RuntimeException e) {
      failed = true;
      throw e;
    } finally {
      long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;

      if (failed) {
        log.warn("[outbound] command={} failed elapsedMs={}", command, elapsedMs);
      } else if (log.isDebugEnabled()) {
        log.debug("[outbound] command={} elapsedMs={}", command, elapsedMs);
      }
    }
  }

  private static String commandKey(ParsedInput input) {
    String simple = input.getClass().getSimpleName();
    if (simple == null || simple.isBlank()) return "unknown";
    return simple.toLowerCase(Locale.ROOT);
  }
}
