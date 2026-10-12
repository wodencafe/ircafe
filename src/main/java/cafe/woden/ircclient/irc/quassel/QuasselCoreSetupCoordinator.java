package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.backend.IrcBackendValidationMessages.SERVER_ID_BLANK;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreSessionLifecycle.renderThrowableMessage;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupPrompt;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupRequest;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Owns pending Core setup prompts, reports setup requirements, and coordinates submission after
 * service-owned session teardown.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreSetupCoordinator {
  private static final String PHASE_SETUP_REQUIRED = "setup-required";
  private final Map<String, QuasselCoreSetupPrompt> pendingPrompts = new ConcurrentHashMap<>();
  @NonNull private final ServerCatalog serverCatalog;
  @NonNull private final QuasselCoreHandshakeCoordinator handshakes;
  @NonNull private final Consumer<String> beforeSetup;
  @NonNull private final BiConsumer<String, String> availabilityReason;
  @NonNull private final QuasselCoreSessionEventPublisher sessionEvents;

  boolean isPending(String serverId) {
    String sid = normalizeServerId(serverId);
    return !sid.isEmpty() && pendingPrompts.containsKey(sid);
  }

  Optional<QuasselCoreSetupPrompt> prompt(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return Optional.empty();
    return Optional.ofNullable(pendingPrompts.get(sid));
  }

  Completable submit(String serverId, QuasselCoreSetupRequest request) {
    return Completable.fromAction(
            () -> {
              String sid = normalizeServerId(serverId);
              if (sid.isEmpty()) {
                throw new IllegalArgumentException(SERVER_ID_BLANK);
              }
              QuasselCoreSetupPrompt prompt = pendingPrompts.get(sid);
              if (prompt == null) {
                throw new IllegalStateException("Quassel Core setup is not pending for " + sid);
              }

              IrcProperties.Server server = serverCatalog.require(sid);
              QuasselCoreSetupRequest req =
                  QuasselCoreSetupSupport.normalizeSetupRequest(
                      prompt, Objects.requireNonNull(request, "request"));

              beforeSetup.accept(sid);

              handshakes.performSetup(server, req);

              pendingPrompts.remove(sid);
              availabilityReason.accept(
                  sid, "Quassel Core setup completed. Reconnect to start a session.");
            })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  void rememberRequired(String serverId, String reason, Map<String, Object> setupFields) {
    availabilityReason.accept(serverId, reason);
    pendingPrompts.put(
        serverId, QuasselCoreSetupSupport.buildSetupPrompt(serverId, reason, setupFields));
  }

  void reportRequired(
      QuasselCoreSession session, QuasselCoreAuthHandshake.CoreSetupRequiredException failure) {
    String sid = session.serverId;
    String detail = renderThrowableMessage(failure);
    String reason = detail.isEmpty() ? "Quassel Core setup is required before login" : detail;
    rememberRequired(sid, reason, failure.setupFields());
    sessionEvents.emitConnectionPhase(session, PHASE_SETUP_REQUIRED, reason);
    sessionEvents.emitError(session, reason, failure);
    sessionEvents.emitDisconnectedOnce(session, reason);
  }

  void forget(String serverId) {
    pendingPrompts.remove(serverId);
  }

  void clear() {
    pendingPrompts.clear();
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
