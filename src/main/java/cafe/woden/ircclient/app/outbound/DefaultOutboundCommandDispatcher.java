package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Default outbound dispatcher backed by the concrete outbound command services. */
@Component("defaultOutboundCommandDispatcher")
public class DefaultOutboundCommandDispatcher implements OutboundCommandDispatcher {

  private final List<OutboundCommandRegistrar> outboundCommandRegistrars;
  private final Map<
          Class<? extends ParsedInput>, OutboundCommandRegistry.Handler<? extends ParsedInput>>
      handlers;

  public DefaultOutboundCommandDispatcher(
      List<OutboundCommandRegistrar> outboundCommandRegistrars) {
    this.outboundCommandRegistrars = List.copyOf(outboundCommandRegistrars);
    this.handlers = buildHandlers();
  }

  @Override
  public void dispatch(CompositeDisposable disposables, ParsedInput in) {
    if (in == null) return;

    OutboundCommandRegistry.Handler<? extends ParsedInput> handler = handlers.get(in.getClass());
    if (handler == null) {
      throw new IllegalStateException(
          "No outbound command handler registered for " + in.getClass().getName());
    }
    dispatchTo(handler, disposables, in);
  }

  private Map<Class<? extends ParsedInput>, OutboundCommandRegistry.Handler<? extends ParsedInput>>
      buildHandlers() {
    OutboundCommandRegistry registry = new OutboundCommandRegistry();
    for (OutboundCommandRegistrar registrar : outboundCommandRegistrars) {
      registrar.registerCommands(registry);
    }

    Map<Class<? extends ParsedInput>, OutboundCommandRegistry.Handler<? extends ParsedInput>> map =
        registry.snapshot();
    validateHandlerCoverage(map);
    return map;
  }

  private void validateHandlerCoverage(
      Map<Class<? extends ParsedInput>, OutboundCommandRegistry.Handler<? extends ParsedInput>>
          map) {
    Class<?>[] permitted = ParsedInput.class.getPermittedSubclasses();
    if (permitted == null || permitted.length == 0) {
      return;
    }

    LinkedHashSet<Class<?>> expected = new LinkedHashSet<>(Arrays.asList(permitted));
    LinkedHashSet<Class<?>> registered = new LinkedHashSet<>(map.keySet());

    LinkedHashSet<Class<?>> missing = new LinkedHashSet<>(expected);
    missing.removeAll(registered);

    LinkedHashSet<Class<?>> extra = new LinkedHashSet<>(registered);
    extra.removeAll(expected);

    if (!missing.isEmpty() || !extra.isEmpty()) {
      throw new IllegalStateException(
          "Outbound command handler registry mismatch. missing=" + missing + ", extra=" + extra);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends ParsedInput> void dispatchTo(
      OutboundCommandRegistry.Handler<? extends ParsedInput> handler,
      CompositeDisposable disposables,
      ParsedInput input) {
    OutboundCommandRegistry.Handler<T> typedHandler = (OutboundCommandRegistry.Handler<T>) handler;
    typedHandler.handle(disposables, (T) input);
  }
}
