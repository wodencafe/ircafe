package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Router for backend-specific message mutation outbound command handlers. */
@Component
@ApplicationLayer
public final class MessageMutationOutboundCommandsRouter {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private final BackendExtensionCatalog backendExtensionCatalog;
  private final Map<String, MessageMutationOutboundCommands> handlers;
  private final MessageMutationOutboundCommands defaultCommands;

  @Autowired
  public MessageMutationOutboundCommandsRouter(BackendExtensionCatalog backendExtensionCatalog) {
    this.backendExtensionCatalog =
        Objects.requireNonNull(backendExtensionCatalog, "backendExtensionCatalog");
    this.handlers = Map.of();
    this.defaultCommands =
        backendExtensionCatalog.messageMutationCommandsFor(
            BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC));
  }

  public MessageMutationOutboundCommandsRouter(List<MessageMutationOutboundCommands> handlers) {
    this(indexHandlers(Objects.requireNonNull(handlers, "handlers")));
  }

  private MessageMutationOutboundCommandsRouter(
      Map<String, MessageMutationOutboundCommands> handlers) {
    this(null, handlers, handlers.get(BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC)));
  }

  private MessageMutationOutboundCommandsRouter(
      BackendExtensionCatalog backendExtensionCatalog,
      Map<String, MessageMutationOutboundCommands> handlers,
      MessageMutationOutboundCommands defaultCommands) {
    this.backendExtensionCatalog = backendExtensionCatalog;
    this.handlers = Map.copyOf(Objects.requireNonNull(handlers, "handlers"));
    MessageMutationOutboundCommands ircHandler =
        Objects.requireNonNull(defaultCommands, "defaultCommands");
    this.defaultCommands = ircHandler;
    if (ircHandler == null) {
      throw new IllegalStateException("Missing message mutation outbound handler for backend IRC");
    }
  }

  private static Map<String, MessageMutationOutboundCommands> indexHandlers(
      List<MessageMutationOutboundCommands> handlers) {
    LinkedHashMap<String, MessageMutationOutboundCommands> index = new LinkedHashMap<>();
    for (MessageMutationOutboundCommands handler : handlers) {
      if (handler == null) continue;
      String backendId = normalizeBackendId(handler.backendId());
      if (backendId.isEmpty()) continue;
      MessageMutationOutboundCommands previous = index.putIfAbsent(backendId, handler);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate message mutation outbound handler for backend " + backendId);
      }
    }
    if (!index.containsKey(BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC))) {
      throw new IllegalStateException("Missing message mutation outbound handler for backend IRC");
    }
    return Map.copyOf(index);
  }

  public MessageMutationOutboundCommands commandsFor(IrcProperties.Server.Backend backend) {
    return commandsFor(backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public MessageMutationOutboundCommands commandsFor(String backendId) {
    String id = normalizeBackendId(backendId);
    if (id.isEmpty()) return defaultCommands;
    if (backendExtensionCatalog != null) {
      return backendExtensionCatalog.messageMutationCommandsFor(id);
    }
    return handlers.getOrDefault(id, defaultCommands);
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(Locale.ROOT);
  }
}
