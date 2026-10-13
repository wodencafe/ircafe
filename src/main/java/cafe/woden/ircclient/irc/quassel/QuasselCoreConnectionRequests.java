package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Validates connection requests and atomically registers sessions before admitting establishment.
 * The service supplies session construction and IO scheduling; this collaborator owns no workers.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreConnectionRequests {
  @NonNull private final ServerCatalog serverCatalog;
  @NonNull private final Map<String, QuasselCoreSession> sessions;
  @NonNull private final BiConsumer<String, String> rememberAvailability;
  @NonNull private final BooleanSupplier shuttingDown;
  @NonNull private final QuasselCoreReconnectCoordinator reconnects;

  @NonNull
  private final BiFunction<String, IrcProperties.Server, QuasselCoreSession> sessionFactory;

  @NonNull private final BiConsumer<IrcProperties.Server, QuasselCoreSession> startEstablishment;

  void connect(String serverId, boolean resetReconnectAttempts) {
    if (shuttingDown.getAsBoolean()) return;

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      throw new IllegalArgumentException(SERVER_ID_BLANK);
    }
    reconnects.cancel(sid, false);
    if (resetReconnectAttempts) {
      reconnects.reset(sid);
    }
    rememberAvailability.accept(sid, "Quassel transport is connecting");

    IrcProperties.Server server = serverCatalog.require(sid);
    QuasselCoreSession existing = sessions.get(sid);
    if (existing != null) return;

    QuasselCoreSession next = sessionFactory.apply(sid, server);
    QuasselCoreSession previous = sessions.putIfAbsent(sid, next);
    if (previous != null) return;

    startEstablishment.accept(server, next);
  }
}
