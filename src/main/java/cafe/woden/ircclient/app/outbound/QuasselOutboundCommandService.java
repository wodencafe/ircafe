package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.QuasselNetworkManagerAction;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles Quassel-specific outbound commands and manager workflows. */
@Component
@ApplicationLayer
final class QuasselOutboundCommandService implements OutboundHelpContributor {
  private static final Logger log = LoggerFactory.getLogger(QuasselOutboundCommandService.class);
  private static final long NETWORK_OBSERVE_TIMEOUT_MS = 3_000L;

  private final QuasselCoreControlPort quasselControl;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final QuasselOutboundCommandSupport quasselCommandSupport;
  private final Map<String, QuasselNetworkVerbHandler> quasselNetworkVerbHandlers;

  QuasselOutboundCommandService(
      @Qualifier("ircClientService") QuasselCoreControlPort quasselControl,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      QuasselOutboundCommandSupport quasselCommandSupport) {
    this.quasselControl = Objects.requireNonNull(quasselControl, "quasselControl");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.quasselCommandSupport =
        Objects.requireNonNull(quasselCommandSupport, "quasselCommandSupport");
    this.quasselNetworkVerbHandlers = buildQuasselNetworkVerbHandlers();
  }

  @Override
  public void appendGeneralHelp(TargetRef out) {
    ui.appendStatus(
        out, "(help)", "/quasselsetup [serverId] (complete pending Quassel Core setup)");
    ui.appendStatus(
        out,
        "(help)",
        "/quasselnet [serverId] list|connect|disconnect|remove|add|edit ... (manage Quassel networks)");
  }

  public void handleQuasselSetup(CompositeDisposable disposables, String serverId) {
    String sid = normalizeConnectionTargetServerId(serverId);
    TargetRef safe = targetCoordinator.safeStatusTarget();
    if (sid.isBlank()) {
      ui.appendStatus(
          safe, "(qsetup)", "Usage: /quasselsetup [serverId] (or run it from that server tab)");
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    if (!ensureQuasselServerBackend(sid, status, "(qsetup)")) {
      return;
    }
    if (!quasselControl.isQuasselCoreSetupPending(sid)) {
      if (!connectionCoordinator.isConnected(sid)) {
        ui.appendStatus(
            status,
            "(qsetup)",
            "No pending Quassel setup is cached yet. Connecting to check setup state…");
        ui.appendStatus(
            status,
            "(qsetup)",
            "If connect fails with ClientLogin rejected, the core is likely already configured. "
                + "Update this server's Login and Server Password, then reconnect.");
        connectionCoordinator.connectOne(sid);
        return;
      }
      ui.appendStatus(status, "(qsetup)", "No pending Quassel Core setup for this server.");
      return;
    }

    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        quasselControl
            .quasselCoreSetupPrompt(sid)
            .orElse(
                new QuasselCoreControlPort.QuasselCoreSetupPrompt(
                    sid, "", List.of(), List.of(), Map.of()));

    Optional<QuasselCoreControlPort.QuasselCoreSetupRequest> maybeRequest =
        ui.promptQuasselCoreSetup(sid, prompt);
    if (maybeRequest.isEmpty()) {
      ui.appendStatus(status, "(qsetup)", "Quassel Core setup canceled.");
      return;
    }
    QuasselCoreControlPort.QuasselCoreSetupRequest request = maybeRequest.orElseThrow();

    disposables.add(
        quasselControl
            .submitQuasselCoreSetup(sid, request)
            .subscribe(
                () -> {
                  connectionCoordinator.syncQuasselSetupCredentials(sid, request);
                  connectionCoordinator.markQuasselSetupSubmitted(sid);
                  ui.appendStatus(
                      status, "(qsetup)", "Quassel Core setup submitted. Reconnecting…");
                  connectionCoordinator.connectOne(sid);
                },
                err -> ui.appendError(status, "(qsetup-error)", String.valueOf(err))));
  }

  public void handleQuasselNetwork(CompositeDisposable disposables, String args) {
    List<String> tokens = tokenizeWhitespaceArgs(args);
    TargetRef safe = targetCoordinator.safeStatusTarget();
    if (tokens.isEmpty()) {
      ui.appendStatus(safe, "(qnet)", quasselNetworkUsage());
      return;
    }

    int cursor = 0;
    String serverHint = "";
    String first = tokens.get(0).toLowerCase(Locale.ROOT);
    if (!isQuasselNetworkVerb(first)) {
      serverHint = tokens.get(0);
      cursor = 1;
      if (cursor >= tokens.size()) {
        ui.appendStatus(safe, "(qnet)", quasselNetworkUsage());
        return;
      }
    }

    String verb = tokens.get(cursor).toLowerCase(Locale.ROOT);
    cursor++;

    String sid =
        serverHint.isBlank()
            ? normalizeConnectionTargetServerId("")
            : normalizeConnectionTargetServerId(serverHint);
    if (sid.isBlank()) {
      ui.appendStatus(safe, "(qnet)", quasselNetworkUsage());
      return;
    }
    TargetRef status = new TargetRef(sid, "status");
    if (!ensureQuasselServerBackend(sid, status, "(qnet)")) {
      return;
    }

    QuasselNetworkVerbHandler handler = quasselNetworkVerbHandlers.get(verb);
    if (handler == null) {
      ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
      return;
    }
    handler.handle(disposables, sid, status, tokens, cursor, verb);
  }

  public void handleQuasselNetworkManager(CompositeDisposable disposables, String args) {
    List<String> tokens = tokenizeWhitespaceArgs(args);
    String serverHint = tokens.isEmpty() ? "" : tokens.get(0);
    String sid = normalizeConnectionTargetServerId(serverHint);
    TargetRef safe = targetCoordinator.safeStatusTarget();
    if (sid.isBlank()) {
      ui.appendStatus(
          safe, "(qnet-ui)", "Select a Quassel server first, then open Quassel Network Manager.");
      return;
    }
    TargetRef status = new TargetRef(sid, "status");
    if (!ensureQuasselServerBackend(sid, status, "(qnet-ui)")) {
      return;
    }
    boolean connectedState = connectionCoordinator.isConnected(sid);
    boolean establishedSession = quasselControl.hasEstablishedQuasselCoreSession(sid);
    appendQnetDebug(
        status,
        "handleQuasselNetworkManager: args='"
            + Objects.toString(args, "")
            + "', sid='"
            + sid
            + "', connectedState="
            + connectedState
            + ", establishedSession="
            + establishedSession);
    boolean managerSessionAvailable = connectedState || establishedSession;
    if (!managerSessionAvailable) {
      ui.appendStatus(
          status,
          "(qnet-ui)",
          "Quassel Network Manager requires an active core session. Connecting now; it will open automatically once connected.");
      ui.appendStatus(status, "(qnet-ui)", "Queued Quassel Network Manager to open on sync-ready.");
      connectionCoordinator.queueOpenQuasselNetworkManagerOnSyncReady(sid);
      connectionCoordinator.connectOne(sid);
      return;
    }

    Optional<QuasselNetworkManagerAction> directAction =
        parseDirectQuasselNetworkManagerAction(tokens);
    if (directAction.isEmpty()) {
      openQuasselNetworkManagerPrompt(disposables, sid, status);
      return;
    }
    processQuasselNetworkManagerAction(disposables, sid, status, directAction.orElseThrow(), false);
  }

  private void openQuasselNetworkManagerPrompt(
      CompositeDisposable disposables, String serverId, TargetRef status) {
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
        quasselControl.quasselCoreNetworks(serverId);
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> safeNetworks =
        networks == null ? List.of() : List.copyOf(networks);
    appendQnetDebug(
        status,
        "Opening manager for server="
            + serverId
            + ", networks="
            + safeNetworks.size()
            + ": "
            + summarizeNetworkSnapshot(safeNetworks));
    try {
      ui.syncQuasselNetworks(serverId, safeNetworks);
    } catch (RuntimeException syncError) {
      log.error(
          "Quassel Network Manager pre-sync failed: serverId={}, networks={}",
          serverId,
          summarizeNetworkSnapshot(safeNetworks),
          syncError);
      appendQnetDebug(
          status,
          "Manager pre-sync failed (continuing to open dialog): "
              + syncError.getClass().getSimpleName()
              + ": "
              + String.valueOf(syncError.getMessage()));
    }
    Optional<QuasselNetworkManagerAction> maybeAction;
    try {
      maybeAction = ui.promptQuasselNetworkManagerAction(serverId, safeNetworks);
    } catch (RuntimeException promptError) {
      log.error(
          "Quassel Network Manager prompt failed: serverId={}, networks={}",
          serverId,
          summarizeNetworkSnapshot(safeNetworks),
          promptError);
      ui.appendError(status, "(qnet-ui-error)", String.valueOf(promptError));
      return;
    }
    if (maybeAction.isEmpty()) {
      appendQnetDebug(status, "Quassel Network Manager prompt returned empty (closed/canceled).");
      return;
    }
    QuasselNetworkManagerAction action = maybeAction.orElseThrow();
    processQuasselNetworkManagerAction(disposables, serverId, status, action, true);
  }

  private void processQuasselNetworkManagerAction(
      CompositeDisposable disposables,
      String serverId,
      TargetRef status,
      QuasselNetworkManagerAction action,
      boolean reopenPrompt) {
    if (action == null || action.operation() == null) {
      maybeReopenQuasselNetworkManagerPrompt(disposables, serverId, status, reopenPrompt);
      return;
    }

    switch (action.operation()) {
      case REFRESH -> maybeReopenQuasselNetworkManagerPrompt(disposables, serverId, status, true);
      case CONNECT -> {
        String network = Objects.toString(action.networkIdOrName(), "").trim();
        if (network.isEmpty()) {
          ui.appendStatus(status, "(qnet-ui)", "Select a network first.");
          maybeReopenQuasselNetworkManagerPrompt(disposables, serverId, status, reopenPrompt);
          return;
        }
        disposables.add(
            quasselControl
                .quasselCoreConnectNetwork(serverId, network)
                .subscribe(
                    () -> {
                      ui.appendStatus(
                          status,
                          "(qnet-ui)",
                          "Requested connect for Quassel network '" + network + "'.");
                      syncQuasselNetworksToUi(serverId);
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    },
                    err -> {
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    }));
      }
      case DISCONNECT -> {
        String network = Objects.toString(action.networkIdOrName(), "").trim();
        if (network.isEmpty()) {
          ui.appendStatus(status, "(qnet-ui)", "Select a network first.");
          maybeReopenQuasselNetworkManagerPrompt(disposables, serverId, status, reopenPrompt);
          return;
        }
        disposables.add(
            quasselControl
                .quasselCoreDisconnectNetwork(serverId, network)
                .subscribe(
                    () -> {
                      ui.appendStatus(
                          status,
                          "(qnet-ui)",
                          "Requested disconnect for Quassel network '" + network + "'.");
                      syncQuasselNetworksToUi(serverId);
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    },
                    err -> {
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    }));
      }
      case REMOVE -> {
        String network = Objects.toString(action.networkIdOrName(), "").trim();
        if (network.isEmpty()) {
          ui.appendStatus(status, "(qnet-ui)", "Select a network first.");
          maybeReopenQuasselNetworkManagerPrompt(disposables, serverId, status, reopenPrompt);
          return;
        }
        Integer targetNetworkId = resolveKnownNetworkId(serverId, network);
        disposables.add(
            quasselControl
                .quasselCoreRemoveNetwork(serverId, network)
                .andThen(
                    awaitQuasselNetworkRemovedByEvents(
                        serverId, status, targetNetworkId, network, NETWORK_OBSERVE_TIMEOUT_MS))
                .subscribe(
                    observed -> {
                      ui.appendStatus(
                          status,
                          "(qnet-ui)",
                          "Requested removal of Quassel network '" + network + "'.");
                      if (!observed) {
                        ui.appendStatus(
                            status,
                            "(qnet-ui)",
                            "Removal request sent, but core has not reported the updated network list yet.");
                      }
                      syncQuasselNetworksToUi(serverId);
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    },
                    err -> {
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    }));
      }
      case ADD -> {
        QuasselCoreControlPort.QuasselCoreNetworkCreateRequest request = action.createRequest();
        if (request == null) {
          appendQnetDebug(status, "ADD action had null create request; reopening prompt.");
          maybeReopenQuasselNetworkManagerPrompt(disposables, serverId, status, true);
          return;
        }
        appendQnetDebug(
            status,
            "Submitting ADD via manager: name="
                + request.networkName()
                + ", host="
                + request.serverHost()
                + ", port="
                + request.serverPort()
                + ", tls="
                + request.useTls());
        Set<Integer> baselineNetworkIds =
            snapshotNetworkIds(quasselControl.quasselCoreNetworks(serverId));
        disposables.add(
            quasselControl
                .quasselCoreCreateNetwork(serverId, request)
                .andThen(
                    awaitQuasselNetworkObservedByEvents(
                        serverId,
                        status,
                        request.networkName(),
                        baselineNetworkIds,
                        NETWORK_OBSERVE_TIMEOUT_MS))
                .subscribe(
                    observed -> {
                      ui.appendStatus(
                          status,
                          "(qnet-ui)",
                          "Requested Quassel network create: "
                              + request.networkName()
                              + " -> "
                              + request.serverHost()
                              + ":"
                              + request.serverPort()
                              + (request.useTls() ? " (tls)" : " (plain)"));
                      if (!observed) {
                        ui.appendStatus(
                            status,
                            "(qnet-ui)",
                            "Create request sent, but core has not reported that network yet. "
                                + "Use Refresh in Network Manager or reconnect this Quassel server.");
                      }
                      syncQuasselNetworksToUi(serverId);
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    },
                    err -> {
                      appendQnetDebug(
                          status,
                          "ADD via manager failed: "
                              + err.getClass().getSimpleName()
                              + ": "
                              + String.valueOf(err.getMessage()));
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    }));
      }
      case EDIT -> {
        String network = Objects.toString(action.networkIdOrName(), "").trim();
        QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest request = action.updateRequest();
        if (network.isEmpty() || request == null) {
          ui.appendStatus(status, "(qnet-ui)", "Select a network first.");
          maybeReopenQuasselNetworkManagerPrompt(disposables, serverId, status, reopenPrompt);
          return;
        }
        disposables.add(
            quasselControl
                .quasselCoreUpdateNetwork(serverId, network, request)
                .subscribe(
                    () -> {
                      ui.appendStatus(
                          status,
                          "(qnet-ui)",
                          "Requested update for Quassel network '"
                              + network
                              + "': "
                              + request.serverHost()
                              + ":"
                              + request.serverPort()
                              + (request.useTls() ? " (tls)" : " (plain)"));
                      syncQuasselNetworksToUi(serverId);
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    },
                    err -> {
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      maybeReopenQuasselNetworkManagerPrompt(
                          disposables, serverId, status, reopenPrompt);
                    }));
      }
    }
  }

  private Optional<QuasselNetworkManagerAction> parseDirectQuasselNetworkManagerAction(
      List<String> tokens) {
    if (tokens == null || tokens.size() < 2) return Optional.empty();
    String verb = Objects.toString(tokens.get(1), "").trim().toLowerCase(Locale.ROOT);
    if (verb.isEmpty()) return Optional.empty();

    if ("add".equals(verb)) {
      return Optional.of(QuasselNetworkManagerAction.add(null));
    }

    if (("connect".equals(verb) || "disconnect".equals(verb) || "remove".equals(verb))
        && tokens.size() >= 3) {
      String network = Objects.toString(tokens.get(2), "").trim();
      if (network.isEmpty()) return Optional.empty();
      return switch (verb) {
        case "connect" -> Optional.of(QuasselNetworkManagerAction.connect(network));
        case "disconnect" -> Optional.of(QuasselNetworkManagerAction.disconnect(network));
        case "remove" -> Optional.of(QuasselNetworkManagerAction.remove(network));
        default -> Optional.empty();
      };
    }

    return Optional.empty();
  }

  private void maybeReopenQuasselNetworkManagerPrompt(
      CompositeDisposable disposables, String serverId, TargetRef status, boolean reopenPrompt) {
    if (!reopenPrompt) return;
    openQuasselNetworkManagerPrompt(disposables, serverId, status);
  }

  private void syncQuasselNetworksToUi(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
        quasselControl.quasselCoreNetworks(sid);
    ui.syncQuasselNetworks(sid, networks == null ? List.of() : List.copyOf(networks));
  }

  private Single<Boolean> awaitQuasselNetworkObservedByEvents(
      String serverId,
      TargetRef status,
      String expectedNetworkName,
      Set<Integer> baselineNetworkIds,
      long timeoutMs) {
    String sid = Objects.toString(serverId, "").trim();
    String wanted = Objects.toString(expectedNetworkName, "").trim();
    if (sid.isEmpty() || timeoutMs <= 0L) return Single.just(false);
    Set<Integer> baselineIds =
        baselineNetworkIds == null ? Set.of() : Set.copyOf(baselineNetworkIds);

    appendQnetDebug(
        status,
        "Awaiting network observation after create via events: server="
            + sid
            + ", expected='"
            + wanted
            + "', timeoutMs="
            + timeoutMs
            + ", baselineIds="
            + baselineIds);

    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> initialSnapshot =
        quasselControl.quasselCoreNetworks(sid);
    if (isObservedNetworkSnapshot(initialSnapshot, wanted, baselineIds)) {
      return Single.just(true);
    }

    var events = quasselControl.quasselCoreNetworkEvents();
    if (events == null) {
      appendQnetDebug(status, "Quassel network event stream unavailable; skipping event wait.");
      return Single.just(false);
    }

    return events
        .filter(event -> sid.equals(Objects.toString(event.serverId(), "").trim()))
        .map(QuasselCoreControlPort.QuasselCoreNetworkSnapshotEvent::networks)
        .filter(networks -> isObservedNetworkSnapshot(networks, wanted, baselineIds))
        .firstElement()
        .timeout(timeoutMs, TimeUnit.MILLISECONDS)
        .map(ignored -> true)
        .onErrorReturnItem(false)
        .defaultIfEmpty(false)
        .doOnSuccess(
            observed -> {
              if (observed) return;
              List<QuasselCoreControlPort.QuasselCoreNetworkSummary> finalSnapshot =
                  quasselControl.quasselCoreNetworks(sid);
              appendQnetDebug(
                  status,
                  "Network not observed after create; final snapshot: "
                      + summarizeNetworkSnapshot(finalSnapshot));
            });
  }

  private Single<Boolean> awaitQuasselNetworkRemovedByEvents(
      String serverId,
      TargetRef status,
      Integer targetNetworkId,
      String targetNetworkNameOrId,
      long timeoutMs) {
    String sid = Objects.toString(serverId, "").trim();
    String wanted = Objects.toString(targetNetworkNameOrId, "").trim();
    if (sid.isEmpty() || timeoutMs <= 0L) return Single.just(false);
    if (targetNetworkId == null && wanted.isEmpty()) return Single.just(false);

    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> initialSnapshot =
        quasselControl.quasselCoreNetworks(sid);
    if (!isNetworkStillPresent(initialSnapshot, targetNetworkId, wanted)) {
      return Single.just(true);
    }

    var events = quasselControl.quasselCoreNetworkEvents();
    if (events == null) {
      appendQnetDebug(status, "Quassel network event stream unavailable; skipping event wait.");
      return Single.just(false);
    }

    return events
        .filter(event -> sid.equals(Objects.toString(event.serverId(), "").trim()))
        .map(QuasselCoreControlPort.QuasselCoreNetworkSnapshotEvent::networks)
        .filter(networks -> !isNetworkStillPresent(networks, targetNetworkId, wanted))
        .firstElement()
        .timeout(timeoutMs, TimeUnit.MILLISECONDS)
        .map(ignored -> true)
        .onErrorReturnItem(false)
        .defaultIfEmpty(false)
        .doOnSuccess(
            observed -> {
              if (observed) return;
              appendQnetDebug(
                  status,
                  "Network removal not observed within timeout for target id="
                      + targetNetworkId
                      + ", token='"
                      + wanted
                      + "'.");
            });
  }

  private static boolean isObservedNetworkSnapshot(
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks,
      String expectedNetworkName,
      Set<Integer> baselineNetworkIds) {
    String wanted = Objects.toString(expectedNetworkName, "").trim();
    Set<Integer> baseline = baselineNetworkIds == null ? Set.of() : Set.copyOf(baselineNetworkIds);
    if (networks == null || networks.isEmpty()) return false;
    for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
      if (summary == null) continue;
      int networkId = summary.networkId();
      String name = Objects.toString(summary.networkName(), "").trim();
      if (!wanted.isEmpty() && name.equalsIgnoreCase(wanted)) return true;
      if (networkId >= 0 && !baseline.contains(networkId)) return true;
    }
    return false;
  }

  private static boolean isNetworkStillPresent(
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks,
      Integer targetNetworkId,
      String targetNetworkNameOrId) {
    String wanted = Objects.toString(targetNetworkNameOrId, "").trim();
    if (networks == null || networks.isEmpty()) return false;
    for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
      if (summary == null) continue;
      int networkId = summary.networkId();
      String name = Objects.toString(summary.networkName(), "").trim();
      if (targetNetworkId != null && networkId == targetNetworkId.intValue()) return true;
      if (!wanted.isEmpty() && name.equalsIgnoreCase(wanted)) return true;
    }
    return false;
  }

  private String normalizeConnectionTargetServerId(String target) {
    String sid = Objects.toString(target, "").trim();
    if (!sid.isBlank()) return sid;
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at != null) {
      sid = Objects.toString(at.serverId(), "").trim();
      if (!sid.isBlank()) return sid;
    }
    TargetRef safe = targetCoordinator.safeStatusTarget();
    return safe == null ? "" : Objects.toString(safe.serverId(), "").trim();
  }

  private boolean isQuasselNetworkVerb(String token) {
    return quasselCommandSupport.isNetworkVerb(token);
  }

  private static List<String> tokenizeWhitespaceArgs(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return List.of();
    String[] parts = value.split("\\s+");
    ArrayList<String> out = new ArrayList<>(parts.length);
    for (String part : parts) {
      String token = Objects.toString(part, "").trim();
      if (!token.isEmpty()) out.add(token);
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  private Integer tryParseInt(String value) {
    return quasselCommandSupport.tryParseInt(value);
  }

  private Boolean parseTlsToken(String raw) {
    return quasselCommandSupport.parseTlsToken(raw);
  }

  private String quasselNetworkUsage() {
    return quasselCommandSupport.networkUsage();
  }

  private boolean ensureQuasselServerBackend(String serverId, TargetRef out, String statusTag) {
    TargetRef fallback = targetCoordinator.safeStatusTarget();
    return quasselCommandSupport.ensureQuasselServerBackend(serverId, out, fallback, statusTag, ui);
  }

  private String renderQuasselNetworkSummary(
      QuasselCoreControlPort.QuasselCoreNetworkSummary network) {
    return quasselCommandSupport.renderNetworkSummary(network);
  }

  private Map<String, QuasselNetworkVerbHandler> buildQuasselNetworkVerbHandlers() {
    LinkedHashMap<String, QuasselNetworkVerbHandler> handlers = new LinkedHashMap<>();
    registerQuasselNetworkVerbHandler(handlers, this::handleQuasselNetworkListVerb, "list", "ls");
    registerQuasselNetworkVerbHandler(
        handlers,
        this::handleQuasselNetworkConnectLikeVerb,
        "connect",
        "disconnect",
        "remove",
        "rm");
    registerQuasselNetworkVerbHandler(handlers, this::handleQuasselNetworkAddVerb, "add", "create");
    registerQuasselNetworkVerbHandler(
        handlers, this::handleQuasselNetworkEditVerb, "edit", "update", "set");
    return Map.copyOf(handlers);
  }

  private void handleQuasselNetworkListVerb(
      CompositeDisposable disposables,
      String serverId,
      TargetRef status,
      List<String> tokens,
      int cursor,
      String verb) {
    if (cursor < tokens.size()) {
      ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
      return;
    }
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
        quasselControl.quasselCoreNetworks(serverId);
    ui.syncQuasselNetworks(serverId, networks == null ? List.of() : List.copyOf(networks));
    if (networks.isEmpty()) {
      ui.appendStatus(
          status,
          "(qnet)",
          "No Quassel networks are known yet. Connect first or add one with /quasselnet add.");
      return;
    }
    ui.appendStatus(status, "(qnet)", "Quassel networks:");
    for (QuasselCoreControlPort.QuasselCoreNetworkSummary network : networks) {
      ui.appendStatus(status, "(qnet)", renderQuasselNetworkSummary(network));
    }
  }

  private void handleQuasselNetworkConnectLikeVerb(
      CompositeDisposable disposables,
      String serverId,
      TargetRef status,
      List<String> tokens,
      int cursor,
      String verb) {
    if ((tokens.size() - cursor) != 1) {
      ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
      return;
    }
    String network = tokens.get(cursor);
    if ("connect".equals(verb)) {
      disposables.add(
          quasselControl
              .quasselCoreConnectNetwork(serverId, network)
              .subscribe(
                  () -> {
                    ui.appendStatus(
                        status,
                        "(qnet)",
                        "Requested connect for Quassel network '" + network + "'.");
                    syncQuasselNetworksToUi(serverId);
                  },
                  err -> ui.appendError(status, "(qnet-error)", String.valueOf(err))));
      return;
    }
    if ("disconnect".equals(verb)) {
      disposables.add(
          quasselControl
              .quasselCoreDisconnectNetwork(serverId, network)
              .subscribe(
                  () -> {
                    ui.appendStatus(
                        status,
                        "(qnet)",
                        "Requested disconnect for Quassel network '" + network + "'.");
                    syncQuasselNetworksToUi(serverId);
                  },
                  err -> ui.appendError(status, "(qnet-error)", String.valueOf(err))));
      return;
    }
    Integer targetNetworkId = resolveKnownNetworkId(serverId, network);
    disposables.add(
        quasselControl
            .quasselCoreRemoveNetwork(serverId, network)
            .andThen(
                awaitQuasselNetworkRemovedByEvents(
                    serverId, status, targetNetworkId, network, NETWORK_OBSERVE_TIMEOUT_MS))
            .subscribe(
                observed -> {
                  ui.appendStatus(
                      status, "(qnet)", "Requested removal of Quassel network '" + network + "'.");
                  if (!observed) {
                    ui.appendStatus(
                        status,
                        "(qnet)",
                        "Removal request sent, but core has not reported the updated network list yet.");
                  }
                  syncQuasselNetworksToUi(serverId);
                },
                err -> ui.appendError(status, "(qnet-error)", String.valueOf(err))));
  }

  private void handleQuasselNetworkAddVerb(
      CompositeDisposable disposables,
      String serverId,
      TargetRef status,
      List<String> tokens,
      int cursor,
      String verb) {
    if ((tokens.size() - cursor) < 2) {
      ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
      return;
    }
    String networkName = tokens.get(cursor++);
    String serverHost = tokens.get(cursor++);
    Integer explicitPort = null;
    boolean useTls = true;

    if (cursor < tokens.size()) {
      explicitPort = tryParseInt(tokens.get(cursor));
      if (explicitPort != null) {
        cursor++;
      }
    }
    if (cursor < tokens.size()) {
      Boolean tls = parseTlsToken(tokens.get(cursor));
      if (tls == null) {
        ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
        return;
      }
      useTls = tls.booleanValue();
      cursor++;
    }
    if (cursor < tokens.size()) {
      ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
      return;
    }

    int port = explicitPort != null ? explicitPort.intValue() : (useTls ? 6697 : 6667);
    final int requestedPort = port;
    final boolean requestedTls = useTls;
    QuasselCoreControlPort.QuasselCoreNetworkCreateRequest request =
        new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
            networkName, serverHost, requestedPort, requestedTls, "", true, null, List.of());
    appendQnetDebug(
        status,
        "Submitting /quasselnet add: name="
            + networkName
            + ", host="
            + serverHost
            + ", port="
            + requestedPort
            + ", tls="
            + requestedTls);
    Set<Integer> baselineNetworkIds =
        snapshotNetworkIds(quasselControl.quasselCoreNetworks(serverId));
    disposables.add(
        quasselControl
            .quasselCoreCreateNetwork(serverId, request)
            .andThen(
                awaitQuasselNetworkObservedByEvents(
                    serverId,
                    status,
                    request.networkName(),
                    baselineNetworkIds,
                    NETWORK_OBSERVE_TIMEOUT_MS))
            .subscribe(
                observed -> {
                  ui.appendStatus(
                      status,
                      "(qnet)",
                      "Requested Quassel network create: "
                          + networkName
                          + " -> "
                          + serverHost
                          + ":"
                          + requestedPort
                          + (requestedTls ? " (tls)" : " (plain)"));
                  if (!observed) {
                    ui.appendStatus(
                        status,
                        "(qnet)",
                        "Create request sent, but core has not reported that network yet. "
                            + "Run /quasselnet list, or reconnect this Quassel server.");
                  }
                  syncQuasselNetworksToUi(serverId);
                },
                err -> {
                  appendQnetDebug(
                      status,
                      "/quasselnet add failed: "
                          + err.getClass().getSimpleName()
                          + ": "
                          + String.valueOf(err.getMessage()));
                  ui.appendError(status, "(qnet-error)", String.valueOf(err));
                }));
  }

  private static Set<Integer> snapshotNetworkIds(
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    if (networks == null || networks.isEmpty()) return Set.of();
    LinkedHashSet<Integer> ids = new LinkedHashSet<>();
    for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
      if (summary == null) continue;
      int id = summary.networkId();
      if (id >= 0) ids.add(id);
    }
    return ids.isEmpty() ? Set.of() : Set.copyOf(ids);
  }

  private Integer resolveKnownNetworkId(String serverId, String networkIdOrName) {
    String sid = Objects.toString(serverId, "").trim();
    String token = Objects.toString(networkIdOrName, "").trim();
    if (sid.isEmpty() || token.isEmpty()) return null;
    Integer parsed = tryParseInt(token);
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
        quasselControl.quasselCoreNetworks(sid);
    if (networks == null || networks.isEmpty()) return parsed;
    if (parsed != null && parsed.intValue() >= 0) {
      for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
        if (summary != null && summary.networkId() == parsed.intValue()) {
          return parsed;
        }
      }
    }
    for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
      if (summary == null) continue;
      String name = Objects.toString(summary.networkName(), "").trim();
      if (!name.isEmpty() && name.equalsIgnoreCase(token)) {
        int networkId = summary.networkId();
        if (networkId >= 0) return networkId;
      }
    }
    return parsed;
  }

  private void handleQuasselNetworkEditVerb(
      CompositeDisposable disposables,
      String serverId,
      TargetRef status,
      List<String> tokens,
      int cursor,
      String verb) {
    if ((tokens.size() - cursor) < 2) {
      ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
      return;
    }
    String network = tokens.get(cursor++);
    String serverHost = tokens.get(cursor++);
    Integer explicitPort = null;
    boolean useTls = true;

    if (cursor < tokens.size()) {
      explicitPort = tryParseInt(tokens.get(cursor));
      if (explicitPort != null) {
        cursor++;
      }
    }
    if (cursor < tokens.size()) {
      Boolean tls = parseTlsToken(tokens.get(cursor));
      if (tls == null) {
        ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
        return;
      }
      useTls = tls.booleanValue();
      cursor++;
    }
    if (cursor < tokens.size()) {
      ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
      return;
    }

    int port = explicitPort != null ? explicitPort.intValue() : (useTls ? 6697 : 6667);
    final int requestedPort = port;
    final boolean requestedTls = useTls;
    QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest request =
        new QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest(
            "", serverHost, requestedPort, requestedTls, "", true, null, null);
    disposables.add(
        quasselControl
            .quasselCoreUpdateNetwork(serverId, network, request)
            .subscribe(
                () -> {
                  ui.appendStatus(
                      status,
                      "(qnet)",
                      "Requested update for Quassel network '"
                          + network
                          + "': "
                          + serverHost
                          + ":"
                          + requestedPort
                          + (requestedTls ? " (tls)" : " (plain)"));
                  syncQuasselNetworksToUi(serverId);
                },
                err -> ui.appendError(status, "(qnet-error)", String.valueOf(err))));
  }

  private void appendQnetDebug(TargetRef status, String message) {
    String line = Objects.toString(message, "").trim();
    if (line.isEmpty()) return;
    log.debug("{}", line);
    if (status != null) {
      ui.appendStatus(status, "(qnet-debug)", line);
    }
  }

  private static String summarizeNetworkSnapshot(
      List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks) {
    if (networks == null || networks.isEmpty()) {
      return "[]";
    }
    StringBuilder out = new StringBuilder();
    out.append('[');
    int written = 0;
    for (QuasselCoreControlPort.QuasselCoreNetworkSummary summary : networks) {
      if (summary == null) continue;
      if (written > 0) out.append("; ");
      if (written >= 8) {
        out.append("...");
        break;
      }
      String name = Objects.toString(summary.networkName(), "").trim();
      if (name.isEmpty()) name = "network-" + summary.networkId();
      out.append(summary.networkId()).append(':').append(name);
      written++;
    }
    out.append(']');
    return out.toString();
  }

  private static void registerQuasselNetworkVerbHandler(
      Map<String, QuasselNetworkVerbHandler> handlers,
      QuasselNetworkVerbHandler handler,
      String... verbs) {
    if (handlers == null || handler == null || verbs == null) return;
    for (String rawVerb : verbs) {
      String verb = Objects.toString(rawVerb, "").trim().toLowerCase(Locale.ROOT);
      if (!verb.isEmpty()) {
        handlers.put(verb, handler);
      }
    }
  }

  @FunctionalInterface
  private interface QuasselNetworkVerbHandler {
    void handle(
        CompositeDisposable disposables,
        String serverId,
        TargetRef status,
        List<String> tokens,
        int cursor,
        String verb);
  }
}
