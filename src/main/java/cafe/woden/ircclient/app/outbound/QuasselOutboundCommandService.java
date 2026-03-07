package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.QuasselNetworkManagerAction;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.QuasselCoreControlPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles Quassel-specific outbound commands and manager workflows. */
@Component
final class QuasselOutboundCommandService implements OutboundHelpContributor {

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

    disposables.add(
        quasselControl
            .submitQuasselCoreSetup(sid, maybeRequest.orElseThrow())
            .subscribe(
                () -> {
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

  public void handleQuasselNetworkManager(CompositeDisposable disposables, String serverId) {
    String sid = normalizeConnectionTargetServerId(serverId);
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
    openQuasselNetworkManagerPrompt(disposables, sid, status);
  }

  private void openQuasselNetworkManagerPrompt(
      CompositeDisposable disposables, String serverId, TargetRef status) {
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
        quasselControl.quasselCoreNetworks(serverId);
    Optional<QuasselNetworkManagerAction> maybeAction =
        ui.promptQuasselNetworkManagerAction(serverId, networks);
    if (maybeAction.isEmpty()) {
      return;
    }
    QuasselNetworkManagerAction action = maybeAction.orElseThrow();
    processQuasselNetworkManagerAction(disposables, serverId, status, action);
  }

  private void processQuasselNetworkManagerAction(
      CompositeDisposable disposables,
      String serverId,
      TargetRef status,
      QuasselNetworkManagerAction action) {
    if (action == null || action.operation() == null) {
      openQuasselNetworkManagerPrompt(disposables, serverId, status);
      return;
    }

    switch (action.operation()) {
      case REFRESH -> openQuasselNetworkManagerPrompt(disposables, serverId, status);
      case CONNECT -> {
        String network = Objects.toString(action.networkIdOrName(), "").trim();
        if (network.isEmpty()) {
          ui.appendStatus(status, "(qnet-ui)", "Select a network first.");
          openQuasselNetworkManagerPrompt(disposables, serverId, status);
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
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    },
                    err -> {
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    }));
      }
      case DISCONNECT -> {
        String network = Objects.toString(action.networkIdOrName(), "").trim();
        if (network.isEmpty()) {
          ui.appendStatus(status, "(qnet-ui)", "Select a network first.");
          openQuasselNetworkManagerPrompt(disposables, serverId, status);
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
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    },
                    err -> {
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    }));
      }
      case REMOVE -> {
        String network = Objects.toString(action.networkIdOrName(), "").trim();
        if (network.isEmpty()) {
          ui.appendStatus(status, "(qnet-ui)", "Select a network first.");
          openQuasselNetworkManagerPrompt(disposables, serverId, status);
          return;
        }
        disposables.add(
            quasselControl
                .quasselCoreRemoveNetwork(serverId, network)
                .subscribe(
                    () -> {
                      ui.appendStatus(
                          status,
                          "(qnet-ui)",
                          "Requested removal of Quassel network '" + network + "'.");
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    },
                    err -> {
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    }));
      }
      case ADD -> {
        QuasselCoreControlPort.QuasselCoreNetworkCreateRequest request = action.createRequest();
        if (request == null) {
          openQuasselNetworkManagerPrompt(disposables, serverId, status);
          return;
        }
        disposables.add(
            quasselControl
                .quasselCoreCreateNetwork(serverId, request)
                .subscribe(
                    () -> {
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
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    },
                    err -> {
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    }));
      }
      case EDIT -> {
        String network = Objects.toString(action.networkIdOrName(), "").trim();
        QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest request = action.updateRequest();
        if (network.isEmpty() || request == null) {
          ui.appendStatus(status, "(qnet-ui)", "Select a network first.");
          openQuasselNetworkManagerPrompt(disposables, serverId, status);
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
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    },
                    err -> {
                      ui.appendError(status, "(qnet-ui-error)", String.valueOf(err));
                      openQuasselNetworkManagerPrompt(disposables, serverId, status);
                    }));
      }
    }
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
    return quasselCommandSupport.ensureQuasselServerBackend(serverId, out, statusTag, ui);
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
                  () ->
                      ui.appendStatus(
                          status,
                          "(qnet)",
                          "Requested connect for Quassel network '" + network + "'."),
                  err -> ui.appendError(status, "(qnet-error)", String.valueOf(err))));
      return;
    }
    if ("disconnect".equals(verb)) {
      disposables.add(
          quasselControl
              .quasselCoreDisconnectNetwork(serverId, network)
              .subscribe(
                  () ->
                      ui.appendStatus(
                          status,
                          "(qnet)",
                          "Requested disconnect for Quassel network '" + network + "'."),
                  err -> ui.appendError(status, "(qnet-error)", String.valueOf(err))));
      return;
    }
    disposables.add(
        quasselControl
            .quasselCoreRemoveNetwork(serverId, network)
            .subscribe(
                () ->
                    ui.appendStatus(
                        status,
                        "(qnet)",
                        "Requested removal of Quassel network '" + network + "'."),
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
    disposables.add(
        quasselControl
            .quasselCoreCreateNetwork(serverId, request)
            .subscribe(
                () ->
                    ui.appendStatus(
                        status,
                        "(qnet)",
                        "Requested Quassel network create: "
                            + networkName
                            + " -> "
                            + serverHost
                            + ":"
                            + requestedPort
                            + (requestedTls ? " (tls)" : " (plain)")),
                err -> ui.appendError(status, "(qnet-error)", String.valueOf(err))));
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
                () ->
                    ui.appendStatus(
                        status,
                        "(qnet)",
                        "Requested update for Quassel network '"
                            + network
                            + "': "
                            + serverHost
                            + ":"
                            + requestedPort
                            + (requestedTls ? " (tls)" : " (plain)")),
                err -> ui.appendError(status, "(qnet-error)", String.valueOf(err))));
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
