package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Routes parsed backend-specific command names to backend command handlers. */
@Component
final class BackendNamedOutboundCommandRouter {

  private final Map<String, BackendNamedOutboundCommandHandler> handlersByCommandName;
  private final TargetCoordinator targetCoordinator;
  private final UiPort ui;

  BackendNamedOutboundCommandRouter(
      List<BackendNamedOutboundCommandHandler> handlers,
      TargetCoordinator targetCoordinator,
      UiPort ui) {
    List<BackendNamedOutboundCommandHandler> safeHandlers =
        List.copyOf(Objects.requireNonNull(handlers, "handlers"));
    this.handlersByCommandName = indexHandlersByCommandName(safeHandlers);
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.ui = Objects.requireNonNull(ui, "ui");
  }

  void handle(CompositeDisposable disposables, ParsedInput.BackendNamed command) {
    String name = normalizeCommandName(command.command());
    BackendNamedOutboundCommandHandler handler = handlersByCommandName.get(name);
    if (handler != null) {
      handler.handle(disposables, command);
      return;
    }
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef out = active != null ? active : targetCoordinator.safeStatusTarget();
    ui.appendStatus(out, "(system)", "Unknown command: /" + name);
  }

  private static Map<String, BackendNamedOutboundCommandHandler> indexHandlersByCommandName(
      List<BackendNamedOutboundCommandHandler> handlers) {
    LinkedHashMap<String, BackendNamedOutboundCommandHandler> index = new LinkedHashMap<>();
    for (BackendNamedOutboundCommandHandler handler : handlers) {
      Set<String> commandNames =
          Objects.requireNonNullElse(handler.supportedCommandNames(), Set.<String>of());
      for (String commandName : commandNames) {
        String key = normalizeCommandName(commandName);
        BackendNamedOutboundCommandHandler previous = index.putIfAbsent(key, handler);
        if (previous != null && previous != handler) {
          throw new IllegalStateException(
              "Duplicate backend named outbound command handler for '" + key + "'");
        }
      }
    }
    return Map.copyOf(index);
  }

  private static String normalizeCommandName(String commandName) {
    String name = Objects.toString(commandName, "").trim().toLowerCase(Locale.ROOT);
    if (name.startsWith("/")) name = name.substring(1).trim();
    return name;
  }
}
