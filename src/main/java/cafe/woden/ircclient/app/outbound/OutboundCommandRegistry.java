package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Mutable outbound command registration map with typed handler binding. */
final class OutboundCommandRegistry {

  @FunctionalInterface
  interface Handler<T extends ParsedInput> {
    void handle(CompositeDisposable disposables, T input);
  }

  private final LinkedHashMap<Class<? extends ParsedInput>, Handler<? extends ParsedInput>>
      handlers = new LinkedHashMap<>();

  <T extends ParsedInput> void register(Class<T> commandType, Handler<T> handler) {
    Class<T> type = Objects.requireNonNull(commandType, "commandType");
    Handler<T> typedHandler = Objects.requireNonNull(handler, "handler");
    Handler<? extends ParsedInput> previous = handlers.putIfAbsent(type, typedHandler);
    if (previous != null) {
      throw new IllegalStateException(
          "Duplicate outbound command handler registered for " + type.getName());
    }
  }

  Map<Class<? extends ParsedInput>, Handler<? extends ParsedInput>> snapshot() {
    return Map.copyOf(handlers);
  }
}
