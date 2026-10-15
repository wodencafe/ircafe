package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Creates independent Core sessions with configured defaults and session-scoped observations. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreSessionFactory {
  @NonNull private final QuasselCoreSignalProxySender sender;
  @NonNull private final Consumer<String> identityObserved;
  @NonNull private final Consumer<ServerIrcEvent> events;

  QuasselCoreSession create(String serverId, IrcProperties.Server server) {
    String nick = Objects.toString(server.nick(), "").trim();
    if (nick.isEmpty()) nick = "quassel-user";
    return new QuasselCoreSession(
        serverId,
        nick,
        server.host(),
        server.port(),
        sender,
        identityObserved,
        event -> events.accept(new ServerIrcEvent(serverId, event)));
  }
}
