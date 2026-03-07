package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Router for backend-specific message mutation outbound command handlers. */
@Component
final class MessageMutationOutboundCommandsRouter {

  private final Map<IrcProperties.Server.Backend, MessageMutationOutboundCommands> handlers;
  private final MessageMutationOutboundCommands defaultCommands;

  MessageMutationOutboundCommandsRouter(List<MessageMutationOutboundCommands> handlers) {
    LinkedHashMap<IrcProperties.Server.Backend, MessageMutationOutboundCommands> index =
        new LinkedHashMap<>();
    for (MessageMutationOutboundCommands handler : Objects.requireNonNull(handlers, "handlers")) {
      if (handler == null) continue;
      IrcProperties.Server.Backend backend = handler.backend();
      if (backend == null) continue;
      MessageMutationOutboundCommands previous = index.putIfAbsent(backend, handler);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate message mutation outbound handler for backend " + backend);
      }
    }
    this.handlers = Map.copyOf(index);
    MessageMutationOutboundCommands ircHandler = this.handlers.get(IrcProperties.Server.Backend.IRC);
    if (ircHandler == null) {
      throw new IllegalStateException("Missing message mutation outbound handler for backend IRC");
    }
    this.defaultCommands = ircHandler;
  }

  MessageMutationOutboundCommands commandsFor(IrcProperties.Server.Backend backend) {
    if (backend == null) return defaultCommands;
    return handlers.getOrDefault(backend, defaultCommands);
  }
}
