package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.QuasselNetworkManagerAction;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.state.AwayRoutingState;
import cafe.woden.ircclient.app.state.ChatHistoryRequestRoutingState;
import cafe.woden.ircclient.app.state.JoinRoutingState;
import cafe.woden.ircclient.app.state.LabeledResponseRoutingState;
import cafe.woden.ircclient.app.state.PendingEchoMessageState;
import cafe.woden.ircclient.app.state.PendingInviteState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreMaskNormalizer;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Handles outbound "chatty" slash commands extracted from {@code IrcMediator}.
 *
 * <p>Includes: /join, /part, /connect, /disconnect, /reconnect, /quit, /nick, /away, /query, /msg,
 * /notice, /me, /topic, /kick, /invite, /names, /who, /list, /say, /quote.
 *
 * <p>Behavior is intended to be preserved.
 */
@Component
public class OutboundChatCommandService {

  private enum MultilineSendDecision {
    SEND_AS_MULTILINE,
    SEND_AS_SPLIT_LINES,
    CANCEL
  }

  private static final DateTimeFormatter CHATHISTORY_TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private final IrcClientService irc;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final ServerCatalog serverCatalog;
  private final RuntimeConfigStore runtimeConfig;
  private final AwayRoutingState awayRoutingState;
  private final ChatHistoryRequestRoutingState chatHistoryRequestRoutingState;
  private final JoinRoutingState joinRoutingState;
  private final LabeledResponseRoutingState labeledResponseRoutingState;
  private final PendingEchoMessageState pendingEchoMessageState;
  private final PendingInviteState pendingInviteState;
  private final WhoisRoutingState whoisRoutingState;
  private final IgnoreListCommandPort ignoreListService;

  public OutboundChatCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ServerCatalog serverCatalog,
      RuntimeConfigStore runtimeConfig,
      AwayRoutingState awayRoutingState,
      ChatHistoryRequestRoutingState chatHistoryRequestRoutingState,
      JoinRoutingState joinRoutingState,
      LabeledResponseRoutingState labeledResponseRoutingState,
      PendingEchoMessageState pendingEchoMessageState,
      PendingInviteState pendingInviteState,
      WhoisRoutingState whoisRoutingState,
      IgnoreListCommandPort ignoreListService) {
    this.irc = irc;
    this.ui = ui;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
    this.serverCatalog = serverCatalog;
    this.runtimeConfig = runtimeConfig;
    this.awayRoutingState = awayRoutingState;
    this.chatHistoryRequestRoutingState = chatHistoryRequestRoutingState;
    this.joinRoutingState = joinRoutingState;
    this.labeledResponseRoutingState = labeledResponseRoutingState;
    this.pendingEchoMessageState = pendingEchoMessageState;
    this.pendingInviteState = pendingInviteState;
    this.whoisRoutingState = whoisRoutingState;
    this.ignoreListService = ignoreListService;
  }

  public void handleJoin(CompositeDisposable disposables, String channel, String key) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(join)", "Select a server first.");
      return;
    }

    String chan = channel == null ? "" : channel.trim();
    String joinKey = key == null ? "" : key.trim();
    if (chan.isEmpty()) {
      ui.appendStatus(at, "(join)", "Usage: /join <#channel> [key]");
      return;
    }

    // Persist for auto-join next time.
    runtimeConfig.rememberJoinedChannel(at.serverId(), chan);

    // Remember where the user initiated the join so join-failure numerics can be routed
    // back to the correct buffer (and also to status). UI-only surfaces route to status.
    TargetRef joinOrigin = at.isUiOnly() ? new TargetRef(at.serverId(), "status") : at;
    joinRoutingState.rememberOrigin(at.serverId(), chan, joinOrigin);

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(conn)",
          "Not connected (join queued in config only)");
      return;
    }

    if (containsCrlf(chan) || containsCrlf(joinKey)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(join)",
          "Refusing to send multi-line /join input.");
      return;
    }

    if (!joinKey.isEmpty()) {
      String line = "JOIN " + chan + " " + joinKey;
      disposables.add(
          irc.sendRaw(at.serverId(), line)
              .subscribe(
                  () -> {},
                  err ->
                      ui.appendError(
                          targetCoordinator.safeStatusTarget(),
                          "(join-error)",
                          String.valueOf(err))));
      return;
    }

    disposables.add(
        irc.joinChannel(at.serverId(), chan)
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(join-error)",
                        String.valueOf(err))));
  }

  public void handlePart(CompositeDisposable disposables, String channel, String reason) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(part)", "Select a server first.");
      return;
    }

    String chan = channel == null ? "" : channel.trim();
    String msg = reason == null ? "" : reason.trim();

    // If no explicit channel was provided, we can only /part if the active target is a channel.
    TargetRef target;
    if (chan.isEmpty()) {
      if (!at.isChannel()) {
        ui.appendStatus(
            at, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
        return;
      }
      target = at;
    } else {
      target = new TargetRef(at.serverId(), chan);
      if (!target.isChannel()) {
        ui.appendStatus(at, "(part)", "Usage: /part [#channel] [reason]");
        return;
      }
    }

    targetCoordinator.disconnectChannel(target, msg);
  }

  public void handleConnect(String target) {
    ConnectionCommandTarget cmd = parseConnectionCommandTarget(target);
    if (cmd == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(connect)", "Usage: /connect [serverId|all]");
      return;
    }
    if (cmd.all()) {
      connectionCoordinator.connectAll();
      return;
    }
    connectionCoordinator.connectOne(cmd.serverId());
  }

  public void handleDisconnect(String target) {
    ConnectionCommandTarget cmd = parseConnectionCommandTarget(target);
    if (cmd == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(),
          "(disconnect)",
          "Usage: /disconnect [serverId|all]");
      return;
    }
    if (cmd.all()) {
      connectionCoordinator.disconnectAll();
      return;
    }
    connectionCoordinator.disconnectOne(cmd.serverId());
  }

  public void handleReconnect(String target) {
    ConnectionCommandTarget cmd = parseConnectionCommandTarget(target);
    if (cmd == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(reconnect)", "Usage: /reconnect [serverId|all]");
      return;
    }
    if (cmd.all()) {
      connectionCoordinator.reconnectAll();
      return;
    }
    connectionCoordinator.reconnectOne(cmd.serverId());
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
    if (!irc.isQuasselCoreSetupPending(sid)) {
      ui.appendStatus(status, "(qsetup)", "No pending Quassel Core setup for this server.");
      return;
    }

    IrcClientService.QuasselCoreSetupPrompt prompt =
        irc.quasselCoreSetupPrompt(sid)
            .orElse(
                new IrcClientService.QuasselCoreSetupPrompt(
                    sid, "", List.of(), List.of(), Map.of()));

    Optional<IrcClientService.QuasselCoreSetupRequest> maybeRequest =
        ui.promptQuasselCoreSetup(sid, prompt);
    if (maybeRequest.isEmpty()) {
      ui.appendStatus(status, "(qsetup)", "Quassel Core setup canceled.");
      return;
    }

    disposables.add(
        irc.submitQuasselCoreSetup(sid, maybeRequest.orElseThrow())
            .subscribe(
                () -> {
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

    if ("list".equals(verb) || "ls".equals(verb)) {
      if (cursor < tokens.size()) {
        ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
        return;
      }
      List<IrcClientService.QuasselCoreNetworkSummary> networks = irc.quasselCoreNetworks(sid);
      if (networks.isEmpty()) {
        ui.appendStatus(
            status,
            "(qnet)",
            "No Quassel networks are known yet. Connect first or add one with /quasselnet add.");
        return;
      }
      ui.appendStatus(status, "(qnet)", "Quassel networks:");
      for (IrcClientService.QuasselCoreNetworkSummary network : networks) {
        ui.appendStatus(status, "(qnet)", renderQuasselNetworkSummary(network));
      }
      return;
    }

    if ("connect".equals(verb)
        || "disconnect".equals(verb)
        || "remove".equals(verb)
        || "rm".equals(verb)) {
      if ((tokens.size() - cursor) != 1) {
        ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
        return;
      }
      String network = tokens.get(cursor);
      if ("connect".equals(verb)) {
        disposables.add(
            irc.quasselCoreConnectNetwork(sid, network)
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
            irc.quasselCoreDisconnectNetwork(sid, network)
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
          irc.quasselCoreRemoveNetwork(sid, network)
              .subscribe(
                  () ->
                      ui.appendStatus(
                          status,
                          "(qnet)",
                          "Requested removal of Quassel network '" + network + "'."),
                  err -> ui.appendError(status, "(qnet-error)", String.valueOf(err))));
      return;
    }

    if ("add".equals(verb) || "create".equals(verb)) {
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
      IrcClientService.QuasselCoreNetworkCreateRequest request =
          new IrcClientService.QuasselCoreNetworkCreateRequest(
              networkName, serverHost, requestedPort, requestedTls, "", true, null, List.of());
      disposables.add(
          irc.quasselCoreCreateNetwork(sid, request)
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
      return;
    }

    if ("edit".equals(verb) || "update".equals(verb) || "set".equals(verb)) {
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
      IrcClientService.QuasselCoreNetworkUpdateRequest request =
          new IrcClientService.QuasselCoreNetworkUpdateRequest(
              "", serverHost, requestedPort, requestedTls, "", true, null, null);
      disposables.add(
          irc.quasselCoreUpdateNetwork(sid, network, request)
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
      return;
    }

    ui.appendStatus(status, "(qnet)", quasselNetworkUsage());
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
    List<IrcClientService.QuasselCoreNetworkSummary> networks = irc.quasselCoreNetworks(serverId);
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
            irc.quasselCoreConnectNetwork(serverId, network)
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
            irc.quasselCoreDisconnectNetwork(serverId, network)
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
            irc.quasselCoreRemoveNetwork(serverId, network)
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
        IrcClientService.QuasselCoreNetworkCreateRequest request = action.createRequest();
        if (request == null) {
          openQuasselNetworkManagerPrompt(disposables, serverId, status);
          return;
        }
        disposables.add(
            irc.quasselCoreCreateNetwork(serverId, request)
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
        IrcClientService.QuasselCoreNetworkUpdateRequest request = action.updateRequest();
        if (network.isEmpty() || request == null) {
          ui.appendStatus(status, "(qnet-ui)", "Select a network first.");
          openQuasselNetworkManagerPrompt(disposables, serverId, status);
          return;
        }
        disposables.add(
            irc.quasselCoreUpdateNetwork(serverId, network, request)
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

  private static boolean isQuasselNetworkVerb(String token) {
    String normalized = Objects.toString(token, "").trim().toLowerCase(Locale.ROOT);
    return normalized.equals("list")
        || normalized.equals("ls")
        || normalized.equals("connect")
        || normalized.equals("disconnect")
        || normalized.equals("add")
        || normalized.equals("create")
        || normalized.equals("edit")
        || normalized.equals("update")
        || normalized.equals("set")
        || normalized.equals("remove")
        || normalized.equals("rm");
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

  private static Integer tryParseInt(String value) {
    String token = Objects.toString(value, "").trim();
    if (token.isEmpty()) return null;
    try {
      return Integer.parseInt(token);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static Boolean parseTlsToken(String raw) {
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return null;
    return switch (token) {
      case "1", "true", "yes", "on", "tls", "ssl" -> Boolean.TRUE;
      case "0", "false", "no", "off", "plain", "notls" -> Boolean.FALSE;
      default -> null;
    };
  }

  private static String quasselNetworkUsage() {
    return "Usage: /quasselnet [serverId] list | connect <network> | disconnect <network> | remove <network> | add <name> <host> [port] [tls|plain] | edit <network> <host> [port] [tls|plain]";
  }

  private boolean ensureQuasselServerBackend(String serverId, TargetRef out, String statusTag) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      ui.appendStatus(out, statusTag, "Select a Quassel server first.");
      return false;
    }
    if (serverCatalog == null) {
      return true;
    }
    Optional<IrcProperties.Server> server = serverCatalog.find(sid);
    if (server.isEmpty()) {
      ui.appendStatus(out, statusTag, "Unknown server '" + sid + "'.");
      return false;
    }
    IrcProperties.Server.Backend backend = server.orElseThrow().backend();
    if (backend == IrcProperties.Server.Backend.QUASSEL_CORE) {
      return true;
    }
    ui.appendStatus(
        out,
        statusTag,
        "Server '"
            + sid
            + "' uses the regular IRC backend. Quassel actions are only available on Quassel Core servers.");
    return false;
  }

  private static String renderQuasselNetworkSummary(
      IrcClientService.QuasselCoreNetworkSummary network) {
    if (network == null) return "(unknown network)";
    String name = Objects.toString(network.networkName(), "").trim();
    if (name.isBlank()) name = "network-" + network.networkId();
    String host = Objects.toString(network.serverHost(), "").trim();
    int port = network.serverPort();

    StringBuilder line = new StringBuilder();
    line.append("[").append(network.networkId()).append("] ").append(name).append(" - ");
    line.append(network.connected() ? "connected" : "disconnected");
    if (!network.enabled()) line.append(", disabled");
    if (!host.isBlank() && port > 0) {
      line.append(" @ ").append(host).append(":").append(port);
      line.append(network.useTls() ? " tls" : " plain");
    }
    if (network.identityId() >= 0) {
      line.append(" identity=").append(network.identityId());
    }
    return line.toString();
  }

  public void handleQuit(String reason) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef status = targetCoordinator.safeStatusTarget();
    String sid =
        (at != null && at.serverId() != null && !at.serverId().isBlank())
            ? at.serverId()
            : status.serverId();
    if (sid == null || sid.isBlank()) {
      ui.appendStatus(status, "(quit)", "No server selected.");
      return;
    }

    String msg = reason == null ? "" : reason.trim();
    if (containsCrlf(msg)) {
      ui.appendStatus(
          new TargetRef(sid, "status"), "(quit)", "Refusing to send multi-line /quit reason.");
      return;
    }

    connectionCoordinator.disconnectOne(sid, msg);
  }

  public void handleNick(CompositeDisposable disposables, String newNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(nick)", "Select a server first.");
      return;
    }

    String nick = newNick == null ? "" : newNick.trim();
    if (nick.isEmpty()) {
      ui.appendStatus(at, "(nick)", "Usage: /nick <newNick>");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      runtimeConfig.rememberNick(at.serverId(), nick);
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(nick)",
          "Not connected. Saved preferred nick for next connect.");
      return;
    }

    disposables.add(
        irc.changeNick(at.serverId(), nick)
            .subscribe(
                () ->
                    ui.appendStatus(
                        new TargetRef(at.serverId(), "status"),
                        "(nick)",
                        "Requested nick change to " + nick),
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(nick-error)",
                        String.valueOf(err))));
  }

  public void handleAway(CompositeDisposable disposables, String message) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(away)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");

    String msg = message == null ? "" : message.trim();

    boolean explicitClear =
        "-".equals(msg) || "off".equalsIgnoreCase(msg) || "clear".equalsIgnoreCase(msg);

    boolean clear;
    String toSend;

    // Bare /away toggles: if not away, set a default; if already away, clear it.
    if (msg.isEmpty()) {
      boolean currentlyAway = awayRoutingState.isAway(at.serverId());
      if (currentlyAway) {
        clear = true;
        toSend = "";
      } else {
        clear = false;
        toSend = "Gone for now.";
      }
    } else if (explicitClear) {
      clear = true;
      toSend = "";
    } else {
      clear = false;
      toSend = msg;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    // Remember where the user initiated /away so the server confirmation (305/306)
    // can be printed into the same buffer.
    awayRoutingState.rememberOrigin(at.serverId(), at);

    // Store the requested reason immediately so the upcoming 306 confirmation can
    // include it, even if the numeric arrives before the Completable callback runs.
    // If the request fails, we restore the previous value in the error handler.
    String prevReason = awayRoutingState.getLastReason(at.serverId());
    if (clear) awayRoutingState.setLastReason(at.serverId(), null);
    else awayRoutingState.setLastReason(at.serverId(), toSend);

    disposables.add(
        irc.setAway(at.serverId(), toSend)
            .subscribe(
                () -> {
                  awayRoutingState.setAway(at.serverId(), !clear);
                  ui.appendStatus(
                      status, "(away)", clear ? "Away cleared" : ("Away set: " + toSend));
                },
                err -> {
                  awayRoutingState.setLastReason(at.serverId(), prevReason);
                  ui.appendError(status, "(away-error)", String.valueOf(err));
                }));
  }

  public void handleQuery(String nick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(query)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      ui.appendStatus(at, "(query)", "Usage: /query <nick>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ui.ensureTargetExists(pm);
    ui.selectTarget(pm);
  }

  public void handleMsg(CompositeDisposable disposables, String nick, String body) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(msg)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    String m = body == null ? "" : body.trim();
    if (n.isEmpty() || m.isEmpty()) {
      ui.appendStatus(at, "(msg)", "Usage: /msg <nick> <message>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ui.ensureTargetExists(pm);
    ui.selectTarget(pm);
    sendMessage(disposables, pm, m);
  }

  public void handleNotice(CompositeDisposable disposables, String target, String body) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(notice)", "Select a server first.");
      return;
    }

    String t = target == null ? "" : target.trim();
    String m = body == null ? "" : body.trim();
    if (t.isEmpty() || m.isEmpty()) {
      ui.appendStatus(at, "(notice)", "Usage: /notice <target> <message>");
      return;
    }

    sendNotice(disposables, at, t, m);
  }

  public void handleMe(CompositeDisposable disposables, String action) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(me)", "Select a server first.");
      return;
    }

    String a = action == null ? "" : action.trim();
    if (a.isEmpty()) {
      ui.appendStatus(at, "(me)", "Usage: /me <action>");
      return;
    }

    if (at.isStatus()) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"), "(me)", "Select a channel or PM first.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (shouldUseLocalEcho(at.serverId())) {
      String me = irc.currentNick(at.serverId()).orElse("me");
      ui.appendAction(at, me, a, true);
    }

    disposables.add(
        irc.sendAction(at.serverId(), at.target(), a)
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(send-error)",
                        String.valueOf(err))));
  }

  public void handleTopic(CompositeDisposable disposables, String first, String rest) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(topic)", "Select a server first.");
      return;
    }

    String f = first == null ? "" : first.trim();
    String r = rest == null ? "" : rest.trim();

    String channel;
    String topicText = "";
    boolean settingTopic;

    if (f.startsWith("#") || f.startsWith("&")) {
      channel = f;
      topicText = r;
      settingTopic = !topicText.isEmpty();
    } else if (at.isChannel()) {
      channel = at.target();
      topicText = (f + (r.isEmpty() ? "" : " " + r)).trim();
      settingTopic = !topicText.isEmpty();
    } else {
      ui.appendStatus(at, "(topic)", "Usage: /topic [#channel] [new topic...]");
      ui.appendStatus(at, "(topic)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(channel) || containsCrlf(topicText)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(topic)",
          "Refusing to send multi-line /topic input.");
      return;
    }

    String line = settingTopic ? ("TOPIC " + channel + " :" + topicText) : ("TOPIC " + channel);
    TargetRef out = new TargetRef(at.serverId(), channel);
    TargetRef status = new TargetRef(at.serverId(), "status");
    PreparedRawLine prepared = prepareCorrelatedRawLine(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(topic)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        irc.sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(topic-error)", String.valueOf(err))));
  }

  public void handleKick(
      CompositeDisposable disposables, String channel, String nick, String reason) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(kick)", "Select a server first.");
      return;
    }

    String ch = resolveChannelOrNull(at, channel);
    String n = nick == null ? "" : nick.trim();
    String rsn = reason == null ? "" : reason.trim();

    if (ch == null || n.isEmpty()) {
      ui.appendStatus(at, "(kick)", "Usage: /kick [#channel] <nick> [reason]");
      ui.appendStatus(at, "(kick)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(ch) || containsCrlf(n) || containsCrlf(rsn)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(kick)",
          "Refusing to send multi-line /kick input.");
      return;
    }

    String line = "KICK " + ch + " " + n + (rsn.isEmpty() ? "" : " :" + rsn);
    TargetRef out = new TargetRef(at.serverId(), ch);
    TargetRef status = new TargetRef(at.serverId(), "status");
    PreparedRawLine prepared = prepareCorrelatedRawLine(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(kick)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        irc.sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(kick-error)", String.valueOf(err))));
  }

  public void handleInvite(CompositeDisposable disposables, String nick, String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(invite)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    String ch = resolveChannelOrNull(at, channel);

    if (n.isEmpty() || ch == null) {
      ui.appendStatus(at, "(invite)", "Usage: /invite <nick> [#channel]");
      ui.appendStatus(at, "(invite)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(ch) || containsCrlf(n)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(invite)",
          "Refusing to send multi-line /invite input.");
      return;
    }

    String line = "INVITE " + n + " " + ch;
    TargetRef out = new TargetRef(at.serverId(), ch);
    TargetRef status = new TargetRef(at.serverId(), "status");
    PreparedRawLine prepared = prepareCorrelatedRawLine(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(invite)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        irc.sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(invite-error)", String.valueOf(err))));
  }

  public void handleInviteList(String serverId) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();

    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) sid = Objects.toString(fallback.serverId(), "").trim();
    if (sid.isEmpty()) {
      ui.appendStatus(fallback, "(invite)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    List<PendingInviteState.PendingInvite> invites = pendingInviteState.listForServer(sid);
    if (invites.isEmpty()) {
      ui.appendStatus(status, "(invite)", "No pending invites on " + sid + ".");
      return;
    }

    ui.appendStatus(status, "(invite)", "Pending invites on " + sid + " (" + invites.size() + "):");
    for (PendingInviteState.PendingInvite invite : invites) {
      if (invite == null) continue;
      String from = invite.inviterNick().isBlank() ? "server" : invite.inviterNick();
      String invitee = invite.inviteeNick().isBlank() ? "you" : invite.inviteeNick();
      StringBuilder line =
          new StringBuilder()
              .append("  #")
              .append(invite.id())
              .append(" ")
              .append(from)
              .append(" invited ")
              .append(invitee)
              .append(" to ")
              .append(invite.channel());
      if (invite.repeatCount() > 1) line.append(" (x").append(invite.repeatCount()).append(")");
      if (!invite.reason().isBlank()) line.append(" - ").append(invite.reason());
      ui.appendStatus(status, "(invite)", line.toString());
    }
    ui.appendStatus(
        status,
        "(invite)",
        "Actions: /invjoin <id|last> (or /join -i), /invignore <id|last>, /invwhois <id|last>, /invblock <id|last>");
  }

  public void handleInviteJoin(CompositeDisposable disposables, String inviteToken) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();
    PendingInviteState.PendingInvite invite =
        resolveInviteByToken(inviteToken, fallback, "(invite)");
    if (invite == null) return;

    TargetRef status = new TargetRef(invite.serverId(), "status");
    if (!connectionCoordinator.isConnected(invite.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (containsCrlf(invite.channel())) {
      ui.appendStatus(status, "(invite)", "Refusing to send multi-line /invjoin input.");
      return;
    }

    runtimeConfig.rememberJoinedChannel(invite.serverId(), invite.channel());
    ui.appendStatus(
        status, "(invite)", "Joining " + invite.channel() + " from invite #" + invite.id() + "...");

    disposables.add(
        irc.joinChannel(invite.serverId(), invite.channel())
            .subscribe(
                () -> pendingInviteState.remove(invite.id()),
                err -> ui.appendError(status, "(invite-error)", String.valueOf(err))));
  }

  public void handleInviteIgnore(String inviteToken) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();
    PendingInviteState.PendingInvite invite =
        resolveInviteByToken(inviteToken, fallback, "(invite)");
    if (invite == null) return;

    pendingInviteState.remove(invite.id());
    TargetRef status = new TargetRef(invite.serverId(), "status");
    String from = invite.inviterNick().isBlank() ? "server" : invite.inviterNick();
    ui.appendStatus(
        status,
        "(invite)",
        "Ignored invite #" + invite.id() + " from " + from + " to " + invite.channel() + ".");
  }

  public void handleInviteWhois(CompositeDisposable disposables, String inviteToken) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();
    PendingInviteState.PendingInvite invite =
        resolveInviteByToken(inviteToken, fallback, "(invite)");
    if (invite == null) return;

    TargetRef status = new TargetRef(invite.serverId(), "status");
    String nick = Objects.toString(invite.inviterNick(), "").trim();
    if (nick.isEmpty() || "server".equalsIgnoreCase(nick)) {
      ui.appendStatus(
          status, "(invite)", "No inviter nick available for invite #" + invite.id() + ".");
      return;
    }
    if (!connectionCoordinator.isConnected(invite.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    whoisRoutingState.put(invite.serverId(), nick, status);
    ui.appendStatus(
        status, "(whois)", "Requesting WHOIS for " + nick + " (invite #" + invite.id() + ")...");
    disposables.add(
        irc.whois(invite.serverId(), nick)
            .subscribe(() -> {}, err -> ui.appendError(status, "(whois)", String.valueOf(err))));
  }

  public void handleInviteBlock(String inviteToken) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef fallback = at != null ? at : targetCoordinator.safeStatusTarget();
    PendingInviteState.PendingInvite invite =
        resolveInviteByToken(inviteToken, fallback, "(invite)");
    if (invite == null) return;

    TargetRef status = new TargetRef(invite.serverId(), "status");
    String nick = Objects.toString(invite.inviterNick(), "").trim();
    if (nick.isEmpty() || "server".equalsIgnoreCase(nick)) {
      ui.appendStatus(
          status, "(invite)", "No inviter nick available for invite #" + invite.id() + ".");
      return;
    }

    boolean added = ignoreListService.addMask(invite.serverId(), nick);
    String stored = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(nick);
    if (added) {
      ui.appendStatus(status, "(invite)", "Blocked invites from " + nick + " (" + stored + ").");
    } else {
      ui.appendStatus(status, "(invite)", "Already blocking " + nick + " (" + stored + ").");
    }
    pendingInviteState.remove(invite.id());
  }

  public void handleInviteAutoJoin(String mode) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef out = at != null ? at : targetCoordinator.safeStatusTarget();

    String raw = Objects.toString(mode, "").trim().toLowerCase(Locale.ROOT);
    if ("toggle".equals(raw)) {
      boolean enabled = !pendingInviteState.inviteAutoJoinEnabled();
      pendingInviteState.setInviteAutoJoinEnabled(enabled);
      runtimeConfig.rememberInviteAutoJoinEnabled(enabled);
      ui.appendStatus(
          out, "(invite)", "Invite auto-join is now " + (enabled ? "enabled." : "disabled."));
      return;
    }
    if (raw.isEmpty() || "status".equals(raw)) {
      ui.appendStatus(
          out,
          "(invite)",
          "Invite auto-join is "
              + (pendingInviteState.inviteAutoJoinEnabled() ? "enabled" : "disabled")
              + ". Use /inviteautojoin on|off or /ajinvite.");
      return;
    }

    Boolean enabled = parseOnOff(raw);
    if (enabled == null) {
      ui.appendStatus(
          out,
          "(invite)",
          "Usage: /inviteautojoin [on|off|status] (alias: /ajinvite [on|off|status|toggle])");
      return;
    }

    pendingInviteState.setInviteAutoJoinEnabled(enabled);
    runtimeConfig.rememberInviteAutoJoinEnabled(enabled);
    ui.appendStatus(
        out, "(invite)", "Invite auto-join is now " + (enabled ? "enabled." : "disabled."));
  }

  public void handleNames(CompositeDisposable disposables, String channel) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(names)", "Select a server first.");
      return;
    }

    String ch = resolveChannelOrNull(at, channel);
    if (ch == null) {
      ui.appendStatus(at, "(names)", "Usage: /names [#channel]");
      ui.appendStatus(at, "(names)", "Tip: from a channel tab you can omit #channel.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(ch)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(names)",
          "Refusing to send multi-line /names input.");
      return;
    }

    TargetRef out = new TargetRef(at.serverId(), ch);
    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(names)", "Requesting NAMES for " + ch + "...");

    disposables.add(
        irc.requestNames(at.serverId(), ch)
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(names-error)", String.valueOf(err))));
  }

  public void handleWho(CompositeDisposable disposables, String args) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(who)", "Select a server first.");
      return;
    }

    String a = args == null ? "" : args.trim();
    if (a.isEmpty()) {
      if (at.isChannel()) {
        a = at.target();
      } else {
        ui.appendStatus(at, "(who)", "Usage: /who [mask|#channel] [flags]");
        ui.appendStatus(at, "(who)", "Tip: from a channel tab, /who defaults to that channel.");
        return;
      }
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(a)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(who)",
          "Refusing to send multi-line /who input.");
      return;
    }

    String line = "WHO " + a;
    String firstToken = a;
    int sp = firstToken.indexOf(' ');
    if (sp >= 0) firstToken = firstToken.substring(0, sp).trim();

    TargetRef out =
        (firstToken.startsWith("#") || firstToken.startsWith("&"))
            ? new TargetRef(at.serverId(), firstToken)
            : new TargetRef(at.serverId(), "status");
    TargetRef status = new TargetRef(at.serverId(), "status");
    PreparedRawLine prepared = prepareCorrelatedRawLine(out, line);
    ui.ensureTargetExists(out);
    ui.appendStatus(out, "(who)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        irc.sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(who-error)", String.valueOf(err))));
  }

  public void handleList(CompositeDisposable disposables, String args) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(list)", "Select a server first.");
      return;
    }

    String a = args == null ? "" : args.trim();
    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    if (containsCrlf(a)) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(list)",
          "Refusing to send multi-line /list input.");
      return;
    }

    TargetRef channelList = TargetRef.channelList(at.serverId());
    ui.ensureTargetExists(channelList);
    ui.beginChannelList(
        at.serverId(),
        a.isEmpty() ? "Loading channel list..." : ("Loading channel list (" + a + ")..."));
    ui.selectTarget(channelList);

    String line = a.isEmpty() ? "LIST" : ("LIST " + a);
    TargetRef status = new TargetRef(at.serverId(), "status");
    PreparedRawLine prepared = prepareCorrelatedRawLine(status, line);
    ui.ensureTargetExists(status);
    ui.appendStatus(status, "(list)", "→ " + withLabelHint(line, prepared.label()));

    disposables.add(
        irc.sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(list-error)", String.valueOf(err))));
  }

  public void handleMarkRead(CompositeDisposable disposables) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(markread)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");
    if (at.isStatus() || at.isUiOnly()) {
      ui.appendStatus(status, "(markread)", "Select a channel or PM first.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    if (!irc.isReadMarkerAvailable(at.serverId())) {
      ui.appendStatus(
          status,
          "(markread)",
          featureUnavailableMessage(
              at.serverId(), "read-marker is not negotiated on this server."));
      return;
    }

    Instant now = Instant.now();
    long nowMs = now.toEpochMilli();
    ui.setReadMarker(at, nowMs);
    ui.clearUnread(at);

    disposables.add(
        irc.sendReadMarker(at.serverId(), at.target(), now)
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(markread-error)", String.valueOf(err))));
  }

  public void handleHelp(String topic) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef out = (at != null) ? at : targetCoordinator.safeStatusTarget();
    String t = normalizeHelpTopic(topic);
    if (!t.isEmpty()) {
      switch (t) {
        case "edit" -> {
          appendEditHelp(out);
          return;
        }
        case "redact", "delete" -> {
          appendRedactHelp(out);
          return;
        }
        case "dcc" -> {
          appendDccHelp(out);
          return;
        }
        case "markread" -> {
          appendMarkReadHelp(out);
          return;
        }
        default ->
            ui.appendStatus(
                out, "(help)", "No dedicated help for '" + t + "'. Showing common commands.");
      }
    }

    ui.appendStatus(
        out,
        "(help)",
        "Common: /join /part /msg /notice /me /query /whois /names /list /topic /monitor /chathistory /quote /dcc /quasselsetup /quasselnet");
    appendMarkReadHelp(out);
    ui.appendStatus(
        out,
        "(help)",
        "Invites: /invites /invjoin (/join -i) /invignore /invwhois /invblock /inviteautojoin (/ajinvite)");
    ui.appendStatus(out, "(help)", "/reply <msgid> <message> (requires draft/reply)");
    ui.appendStatus(
        out, "(help)", "/react <msgid> <reaction-token> (requires draft/react + draft/reply)");
    ui.appendStatus(
        out, "(help)", "/unreact <msgid> <reaction-token> (requires draft/unreact + draft/reply)");
    appendEditHelp(out);
    appendRedactHelp(out);
    ui.appendStatus(
        out, "(help)", "/quasselsetup [serverId] (complete pending Quassel Core setup)");
    ui.appendStatus(
        out,
        "(help)",
        "/quasselnet [serverId] list|connect|disconnect|remove|add|edit ... (manage Quassel networks)");
    ui.appendStatus(out, "(help)", "Tip: /help dcc for direct-chat/file-transfer commands.");
    ui.appendStatus(
        out, "(help)", "Tip: /help edit, /help redact, or /help markread for focused details.");
  }

  private void appendDccHelp(TargetRef out) {
    ui.appendStatus(out, "(help)", "/dcc chat <nick>");
    ui.appendStatus(out, "(help)", "/dcc send <nick> <file-path>");
    ui.appendStatus(out, "(help)", "/dcc accept <nick>");
    ui.appendStatus(out, "(help)", "/dcc get <nick> [save-path]");
    ui.appendStatus(out, "(help)", "/dcc msg <nick> <text>  (alias: /dccmsg <nick> <text>)");
    ui.appendStatus(out, "(help)", "/dcc close <nick>  /dcc list  /dcc panel");
    ui.appendStatus(out, "(help)", "UI: right-click a nick and use the DCC submenu.");
  }

  public void handleSay(CompositeDisposable disposables, String msg) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(system)", "Select a server first.");
      return;
    }

    String m = msg == null ? "" : msg.trim();
    if (m.isEmpty()) return;

    if (at.isStatus()) {
      sendRawFromStatus(disposables, at.serverId(), m);
      return;
    }

    sendMessage(disposables, at, m);
  }

  public void handleReplyMessage(CompositeDisposable disposables, String messageId, String body) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(reply)", "Select a server first.");
      return;
    }

    String msgId = normalizeIrcv3Token(messageId);
    String text = body == null ? "" : body.trim();
    if (msgId.isEmpty() || text.isEmpty()) {
      ui.appendStatus(at, "(reply)", "Usage: /reply <msgid> <message>");
      return;
    }

    if (at.isStatus()) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"), "(reply)", "Select a channel or PM first.");
      return;
    }

    if (!irc.isDraftReplyAvailable(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(reply)",
          featureUnavailableMessage(
              at.serverId(), "draft/reply is not negotiated on this server."));
      return;
    }

    sendReplyMessage(disposables, at, msgId, text);
  }

  public void handleReactMessage(
      CompositeDisposable disposables, String messageId, String reaction) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(react)", "Select a server first.");
      return;
    }

    String msgId = normalizeIrcv3Token(messageId);
    String token = normalizeReactionToken(reaction);
    if (msgId.isEmpty() || token.isEmpty()) {
      ui.appendStatus(at, "(react)", "Usage: /react <msgid> <reaction-token>");
      return;
    }

    if (at.isStatus()) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"), "(react)", "Select a channel or PM first.");
      return;
    }

    if (!irc.isDraftReplyAvailable(at.serverId()) || !irc.isDraftReactAvailable(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(react)",
          featureUnavailableMessage(
              at.serverId(), "draft/react is not negotiated on this server."));
      return;
    }

    sendReactionTag(disposables, at, msgId, token);
  }

  public void handleUnreactMessage(
      CompositeDisposable disposables, String messageId, String reaction) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(unreact)", "Select a server first.");
      return;
    }

    String msgId = normalizeIrcv3Token(messageId);
    String token = normalizeReactionToken(reaction);
    if (msgId.isEmpty() || token.isEmpty()) {
      ui.appendStatus(at, "(unreact)", "Usage: /unreact <msgid> <reaction-token>");
      return;
    }

    if (at.isStatus()) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"), "(unreact)", "Select a channel or PM first.");
      return;
    }

    if (!irc.isDraftReplyAvailable(at.serverId()) || !irc.isDraftUnreactAvailable(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(unreact)",
          featureUnavailableMessage(
              at.serverId(), "draft/unreact is not negotiated on this server."));
      return;
    }

    sendUnreactionTag(disposables, at, msgId, token);
  }

  public void handleEditMessage(CompositeDisposable disposables, String messageId, String body) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(edit)", "Select a server first.");
      return;
    }

    String msgId = normalizeIrcv3Token(messageId);
    String text = body == null ? "" : body.trim();
    if (msgId.isEmpty() || text.isEmpty()) {
      ui.appendStatus(at, "(edit)", "Usage: /edit <msgid> <message>");
      return;
    }

    if (at.isStatus()) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"), "(edit)", "Select a channel or PM first.");
      return;
    }

    if (!irc.isMessageEditAvailable(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(edit)",
          featureUnavailableMessage(
              at.serverId(), "draft/message-edit is not negotiated on this server."));
      return;
    }

    if (!isOwnMessageInBuffer(at, msgId)) {
      ui.appendStatus(at, "(edit)", "Can only edit your own messages in this buffer.");
      return;
    }

    sendEditMessage(disposables, at, msgId, text);
  }

  public void handleRedactMessage(
      CompositeDisposable disposables, String messageId, String reason) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(redact)", "Select a server first.");
      return;
    }

    String msgId = normalizeIrcv3Token(messageId);
    String redactReason = Objects.toString(reason, "").trim();
    if (msgId.isEmpty()) {
      ui.appendStatus(at, "(redact)", "Usage: /redact <msgid> [reason]");
      return;
    }

    if (containsCrlf(redactReason)) {
      ui.appendStatus(at, "(redact)", "Reason must be a single line.");
      return;
    }

    if (at.isStatus()) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"), "(redact)", "Select a channel or PM first.");
      return;
    }

    if (!irc.isMessageRedactionAvailable(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(redact)",
          featureUnavailableMessage(
              at.serverId(), "message-redaction is not negotiated on this server."));
      return;
    }

    if (!isOwnMessageInBuffer(at, msgId)) {
      ui.appendStatus(at, "(redact)", "Can only redact your own messages in this buffer.");
      return;
    }

    sendRedactionTag(disposables, at, msgId, redactReason);
  }

  private void sendRawFromStatus(CompositeDisposable disposables, String serverId, String rawLine) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ui.ensureTargetExists(status);

    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty()) return;

    // Prevent accidental multi-line injection.
    if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
      ui.appendStatus(status, "(raw)", "Refusing to send multi-line input.");
      return;
    }

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    PreparedRawLine prepared = prepareCorrelatedRawLine(status, line);

    // Echo a safe preview of what we are sending (avoid leaking secrets).
    ui.appendStatus(
        status, "(raw)", "→ " + withLabelHint(redactIfSensitive(line), prepared.label()));

    disposables.add(
        irc.sendRaw(sid, prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(raw-error)", String.valueOf(err))));
  }

  public void handleChatHistoryBefore(CompositeDisposable disposables, int limit) {
    handleChatHistoryBefore(disposables, limit, "");
  }

  public void handleChatHistoryBefore(CompositeDisposable disposables, int limit, String selector) {
    TargetRef at = resolveChatHistoryTargetOrNull();
    if (at == null) return;
    TargetRef status = new TargetRef(at.serverId(), "status");

    int lim = limit;
    String selectorToken = normalizeChatHistorySelector(selector);
    if (lim <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    if (!Objects.toString(selector, "").trim().isEmpty() && selectorToken.isEmpty()) {
      ui.appendStatus(at, "(chathistory)", "Selector must be msgid=... or timestamp=...");
      return;
    }
    lim = clampChatHistoryLimit(lim);

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (selectorToken.isEmpty()) {
      selectorToken = "timestamp=" + CHATHISTORY_TS_FMT.format(Instant.now());
    }
    String preview = "CHATHISTORY BEFORE " + at.target() + " " + selectorToken + " " + lim;
    ui.appendStatus(at, "(chathistory)", "Requesting older history… limit=" + lim);
    ui.appendStatus(at, "(chathistory)", "→ " + preview);

    final String selectorFinal = selectorToken;
    final int limitFinal = lim;
    chatHistoryRequestRoutingState.remember(
        at.serverId(),
        at.target(),
        at,
        limitFinal,
        selectorFinal,
        Instant.now(),
        ChatHistoryRequestRoutingState.QueryMode.BEFORE);
    disposables.add(
        irc.requestChatHistoryBefore(at.serverId(), at.target(), selectorFinal, limitFinal)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
  }

  public void handleChatHistoryLatest(CompositeDisposable disposables, int limit, String selector) {
    TargetRef at = resolveChatHistoryTargetOrNull();
    if (at == null) return;
    TargetRef status = new TargetRef(at.serverId(), "status");

    int lim = limit;
    if (lim <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    lim = clampChatHistoryLimit(lim);

    String selectorToken = normalizeChatHistorySelectorOrWildcard(selector);
    if (!Objects.toString(selector, "").trim().isEmpty() && selectorToken.isEmpty()) {
      ui.appendStatus(at, "(chathistory)", "Selector must be * or msgid=... or timestamp=...");
      return;
    }
    if (selectorToken.isEmpty()) {
      selectorToken = "*";
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    String preview = "CHATHISTORY LATEST " + at.target() + " " + selectorToken + " " + lim;
    ui.appendStatus(at, "(chathistory)", "Requesting latest/newer history… limit=" + lim);
    ui.appendStatus(at, "(chathistory)", "→ " + preview);

    final String selectorFinal = selectorToken;
    final int limitFinal = lim;
    chatHistoryRequestRoutingState.remember(
        at.serverId(),
        at.target(),
        at,
        limitFinal,
        selectorFinal,
        Instant.now(),
        ChatHistoryRequestRoutingState.QueryMode.LATEST);
    disposables.add(
        irc.requestChatHistoryLatest(at.serverId(), at.target(), selectorFinal, limitFinal)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
  }

  public void handleChatHistoryAround(CompositeDisposable disposables, String selector, int limit) {
    TargetRef at = resolveChatHistoryTargetOrNull();
    if (at == null) return;
    TargetRef status = new TargetRef(at.serverId(), "status");

    int lim = limit;
    if (lim <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    lim = clampChatHistoryLimit(lim);

    String selectorToken = normalizeChatHistorySelector(selector);
    if (selectorToken.isEmpty()) {
      ui.appendStatus(at, "(chathistory)", "Around selector must be msgid=... or timestamp=...");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    String preview = "CHATHISTORY AROUND " + at.target() + " " + selectorToken + " " + lim;
    ui.appendStatus(
        at, "(chathistory)", "Requesting message context around selector… limit=" + lim);
    ui.appendStatus(at, "(chathistory)", "→ " + preview);

    final String selectorFinal = selectorToken;
    final int limitFinal = lim;
    chatHistoryRequestRoutingState.remember(
        at.serverId(),
        at.target(),
        at,
        limitFinal,
        selectorFinal,
        Instant.now(),
        ChatHistoryRequestRoutingState.QueryMode.AROUND);
    disposables.add(
        irc.requestChatHistoryAround(at.serverId(), at.target(), selectorFinal, limitFinal)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
  }

  public void handleChatHistoryBetween(
      CompositeDisposable disposables, String startSelector, String endSelector, int limit) {
    TargetRef at = resolveChatHistoryTargetOrNull();
    if (at == null) return;
    TargetRef status = new TargetRef(at.serverId(), "status");

    int lim = limit;
    if (lim <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    lim = clampChatHistoryLimit(lim);

    String startToken = normalizeChatHistorySelectorOrWildcard(startSelector);
    String endToken = normalizeChatHistorySelectorOrWildcard(endSelector);
    if (startToken.isEmpty() || endToken.isEmpty()) {
      ui.appendStatus(
          at, "(chathistory)", "Between selectors must be * or msgid=... or timestamp=...");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    String preview =
        "CHATHISTORY BETWEEN " + at.target() + " " + startToken + " " + endToken + " " + lim;
    ui.appendStatus(at, "(chathistory)", "Requesting bounded history window… limit=" + lim);
    ui.appendStatus(at, "(chathistory)", "→ " + preview);

    final String startFinal = startToken;
    final String endFinal = endToken;
    final int limitFinal = lim;
    chatHistoryRequestRoutingState.remember(
        at.serverId(),
        at.target(),
        at,
        limitFinal,
        startFinal + " .. " + endFinal,
        Instant.now(),
        ChatHistoryRequestRoutingState.QueryMode.BETWEEN);
    disposables.add(
        irc.requestChatHistoryBetween(at.serverId(), at.target(), startFinal, endFinal, limitFinal)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
  }

  public void handleQuote(CompositeDisposable disposables, String rawLine) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(quote)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");
    ui.ensureTargetExists(status);

    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty()) {
      ui.appendStatus(at, "(quote)", "Usage: /quote <RAW IRC LINE>");
      ui.appendStatus(at, "(quote)", "Example: /quote MONITOR +nick");
      ui.appendStatus(at, "(quote)", "Alias: /raw <RAW IRC LINE>");
      return;
    }

    // Prevent accidental multi-line injection.
    if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
      ui.appendStatus(status, "(quote)", "Refusing to send multi-line /quote input.");
      return;
    }

    if (!connectionCoordinator.isConnected(at.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }

    TargetRef correlationOrigin = at.isUiOnly() ? status : at;
    PreparedRawLine prepared = prepareCorrelatedRawLine(correlationOrigin, line);

    // Echo a safe preview of what we are sending (avoid leaking secrets).
    String echo = redactIfSensitive(line);
    ui.appendStatus(status, "(quote)", "→ " + withLabelHint(echo, prepared.label()));

    disposables.add(
        irc.sendRaw(at.serverId(), prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(quote-error)", String.valueOf(err))));
  }

  private void sendMessage(CompositeDisposable disposables, TargetRef target, String message) {
    if (target == null) return;
    String m = message == null ? "" : message.trim();
    if (m.isEmpty()) return;

    if (!connectionCoordinator.isConnected(target.serverId())) {
      TargetRef status = new TargetRef(target.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!target.isStatus()) {
        ui.appendStatus(target, "(conn)", "Not connected");
      }
      return;
    }

    List<String> lines = normalizeMessageLines(m);
    if (lines.size() > 1) {
      MultilineSendDecision decision = resolveMultilineSendDecision(target, lines, "(send)");
      if (decision == MultilineSendDecision.CANCEL) {
        return;
      }
      if (decision == MultilineSendDecision.SEND_AS_SPLIT_LINES) {
        for (String line : lines) {
          sendMessage(disposables, target, line);
        }
        return;
      }
      m = joinMessageLines(lines);
    }

    boolean useLocalEcho = shouldUseLocalEcho(target.serverId());
    String me = irc.currentNick(target.serverId()).orElse("me");
    final PendingEchoMessageState.PendingOutboundChat pendingEntry;
    if (useLocalEcho) {
      pendingEntry = null;
    } else {
      pendingEntry = pendingEchoMessageState.register(target, me, m, Instant.now());
      ui.appendPendingOutgoingChat(
          target, pendingEntry.pendingId(), pendingEntry.createdAt(), me, m);
    }

    disposables.add(
        irc.sendMessage(target.serverId(), target.target(), m)
            .subscribe(
                () -> {},
                err -> {
                  if (pendingEntry != null) {
                    pendingEchoMessageState.removeById(pendingEntry.pendingId());
                    ui.failPendingOutgoingChat(
                        target,
                        pendingEntry.pendingId(),
                        Instant.now(),
                        pendingEntry.fromNick(),
                        pendingEntry.text(),
                        String.valueOf(err));
                  }
                  ui.appendError(
                      targetCoordinator.safeStatusTarget(), "(send-error)", String.valueOf(err));
                }));

    if (useLocalEcho) {
      ui.appendChat(target, "(" + me + ")", m, true);
    }
  }

  private void sendReplyMessage(
      CompositeDisposable disposables, TargetRef target, String replyToMessageId, String message) {
    if (target == null) return;
    String msgId = normalizeIrcv3Token(replyToMessageId);
    String m = message == null ? "" : message.trim();
    if (msgId.isEmpty() || m.isEmpty()) return;

    if (!connectionCoordinator.isConnected(target.serverId())) {
      TargetRef status = new TargetRef(target.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!target.isStatus()) {
        ui.appendStatus(target, "(conn)", "Not connected");
      }
      return;
    }

    String me = irc.currentNick(target.serverId()).orElse("me");
    boolean useLocalEcho = shouldUseLocalEcho(target.serverId());
    final PendingEchoMessageState.PendingOutboundChat pendingEntry;
    if (useLocalEcho) {
      pendingEntry = null;
    } else {
      pendingEntry = pendingEchoMessageState.register(target, me, m, Instant.now());
      ui.appendPendingOutgoingChat(
          target, pendingEntry.pendingId(), pendingEntry.createdAt(), me, m);
    }

    String rawLine =
        "@+draft/reply=" + escapeIrcv3TagValue(msgId) + " PRIVMSG " + target.target() + " :" + m;
    PreparedRawLine prepared = prepareCorrelatedRawLine(target, rawLine);

    disposables.add(
        irc.sendRaw(target.serverId(), prepared.line())
            .subscribe(
                () -> {},
                err -> {
                  if (pendingEntry != null) {
                    pendingEchoMessageState.removeById(pendingEntry.pendingId());
                    ui.failPendingOutgoingChat(
                        target,
                        pendingEntry.pendingId(),
                        Instant.now(),
                        pendingEntry.fromNick(),
                        pendingEntry.text(),
                        String.valueOf(err));
                  }
                  ui.appendError(
                      targetCoordinator.safeStatusTarget(), "(reply-error)", String.valueOf(err));
                }));

    if (useLocalEcho) {
      ui.appendChat(target, "(" + me + ")", m, true);
    }
  }

  private void sendReactionTag(
      CompositeDisposable disposables, TargetRef target, String replyToMessageId, String reaction) {
    if (target == null) return;
    String msgId = normalizeIrcv3Token(replyToMessageId);
    String react = normalizeReactionToken(reaction);
    if (msgId.isEmpty() || react.isEmpty()) return;

    if (!connectionCoordinator.isConnected(target.serverId())) {
      TargetRef status = new TargetRef(target.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!target.isStatus()) {
        ui.appendStatus(target, "(conn)", "Not connected");
      }
      return;
    }

    String rawLine =
        "@+draft/react="
            + escapeIrcv3TagValue(react)
            + ";+draft/reply="
            + escapeIrcv3TagValue(msgId)
            + " TAGMSG "
            + target.target();
    PreparedRawLine prepared = prepareCorrelatedRawLine(target, rawLine);

    String me = irc.currentNick(target.serverId()).orElse("me");
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.applyMessageReaction(target, now, me, msgId, react);
    }

    disposables.add(
        irc.sendRaw(target.serverId(), prepared.line())
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(react-error)",
                        String.valueOf(err))));
  }

  private void sendUnreactionTag(
      CompositeDisposable disposables, TargetRef target, String replyToMessageId, String reaction) {
    if (target == null) return;
    String msgId = normalizeIrcv3Token(replyToMessageId);
    String react = normalizeReactionToken(reaction);
    if (msgId.isEmpty() || react.isEmpty()) return;

    if (!connectionCoordinator.isConnected(target.serverId())) {
      TargetRef status = new TargetRef(target.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!target.isStatus()) {
        ui.appendStatus(target, "(conn)", "Not connected");
      }
      return;
    }

    String rawLine =
        "@+draft/unreact="
            + escapeIrcv3TagValue(react)
            + ";+draft/reply="
            + escapeIrcv3TagValue(msgId)
            + " TAGMSG "
            + target.target();
    PreparedRawLine prepared = prepareCorrelatedRawLine(target, rawLine);

    String me = irc.currentNick(target.serverId()).orElse("me");
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.removeMessageReaction(target, now, me, msgId, react);
    }

    disposables.add(
        irc.sendRaw(target.serverId(), prepared.line())
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(unreact-error)",
                        String.valueOf(err))));
  }

  private void sendEditMessage(
      CompositeDisposable disposables,
      TargetRef target,
      String targetMessageId,
      String editedText) {
    if (target == null) return;
    String msgId = normalizeIrcv3Token(targetMessageId);
    String text = editedText == null ? "" : editedText.trim();
    if (msgId.isEmpty() || text.isEmpty()) return;

    if (!connectionCoordinator.isConnected(target.serverId())) {
      TargetRef status = new TargetRef(target.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!target.isStatus()) {
        ui.appendStatus(target, "(conn)", "Not connected");
      }
      return;
    }

    String rawLine =
        "@+draft/edit=" + escapeIrcv3TagValue(msgId) + " PRIVMSG " + target.target() + " :" + text;
    PreparedRawLine prepared = prepareCorrelatedRawLine(target, rawLine);

    String me = irc.currentNick(target.serverId()).orElse("me");
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.applyMessageEdit(target, now, me, msgId, text, "", java.util.Map.of("draft/edit", msgId));
    }

    disposables.add(
        irc.sendRaw(target.serverId(), prepared.line())
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(edit-error)",
                        String.valueOf(err))));
  }

  private void sendRedactionTag(
      CompositeDisposable disposables, TargetRef target, String targetMessageId, String reason) {
    if (target == null) return;
    String msgId = normalizeIrcv3Token(targetMessageId);
    if (msgId.isEmpty()) return;

    if (!connectionCoordinator.isConnected(target.serverId())) {
      TargetRef status = new TargetRef(target.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!target.isStatus()) {
        ui.appendStatus(target, "(conn)", "Not connected");
      }
      return;
    }

    String why = Objects.toString(reason, "").trim();
    String rawLine =
        why.isEmpty()
            ? ("REDACT " + target.target() + " " + msgId)
            : ("REDACT " + target.target() + " " + msgId + " :" + why);
    PreparedRawLine prepared = prepareCorrelatedRawLine(target, rawLine);

    String me = irc.currentNick(target.serverId()).orElse("me");
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.applyMessageRedaction(target, now, me, msgId, "", java.util.Map.of("draft/delete", msgId));
    }

    disposables.add(
        irc.sendRaw(target.serverId(), prepared.line())
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(redact-error)",
                        String.valueOf(err))));
  }

  private void sendNotice(
      CompositeDisposable disposables, TargetRef echoTarget, String target, String message) {
    if (echoTarget == null) return;
    String t = target == null ? "" : target.trim();
    String m = message == null ? "" : message.trim();
    if (t.isEmpty() || m.isEmpty()) return;

    if (!connectionCoordinator.isConnected(echoTarget.serverId())) {
      TargetRef status = new TargetRef(echoTarget.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!echoTarget.isStatus()) {
        ui.appendStatus(echoTarget, "(conn)", "Not connected");
      }
      return;
    }

    List<String> lines = normalizeMessageLines(m);
    if (lines.size() > 1) {
      MultilineSendDecision decision = resolveMultilineSendDecision(echoTarget, lines, "(notice)");
      if (decision == MultilineSendDecision.CANCEL) {
        return;
      }
      if (decision == MultilineSendDecision.SEND_AS_SPLIT_LINES) {
        for (String line : lines) {
          sendNotice(disposables, echoTarget, t, line);
        }
        return;
      }
      m = joinMessageLines(lines);
    }

    disposables.add(
        irc.sendNotice(echoTarget.serverId(), t, m)
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(send-error)",
                        String.valueOf(err))));

    if (shouldUseLocalEcho(echoTarget.serverId())) {
      String me = irc.currentNick(echoTarget.serverId()).orElse("me");
      ui.appendNotice(echoTarget, "(" + me + ")", "NOTICE → " + t + ": " + m);
    }
  }

  private MultilineSendDecision resolveMultilineSendDecision(
      TargetRef target, List<String> lines, String statusPrefix) {
    if (target == null || lines == null || lines.size() <= 1) {
      return MultilineSendDecision.SEND_AS_MULTILINE;
    }

    int lineCount = lines.size();
    long payloadUtf8Bytes = multilinePayloadUtf8Bytes(lines);
    String reason =
        multilineUnavailableOrLimitReason(target.serverId(), lineCount, payloadUtf8Bytes);
    if (reason.isBlank()) {
      return MultilineSendDecision.SEND_AS_MULTILINE;
    }

    boolean sendSplit = false;
    try {
      sendSplit = ui.confirmMultilineSplitFallback(target, lineCount, payloadUtf8Bytes, reason);
    } catch (Exception ignored) {
      sendSplit = false;
    }

    if (!sendSplit) {
      ui.appendStatus(target, statusPrefix, "Send canceled.");
      return MultilineSendDecision.CANCEL;
    }

    ui.appendStatus(target, statusPrefix, reason + " Sending as " + lineCount + " separate lines.");
    return MultilineSendDecision.SEND_AS_SPLIT_LINES;
  }

  private String multilineUnavailableOrLimitReason(
      String serverId, int lineCount, long payloadUtf8Bytes) {
    String backendUnavailableReason = normalizedBackendAvailabilityReason(serverId);
    if (!backendUnavailableReason.isEmpty()) {
      return ensureTerminalPunctuation(backendUnavailableReason);
    }

    if (!irc.isMultilineAvailable(serverId)) {
      return "IRCv3 multiline is not negotiated on this server.";
    }

    int maxLines = irc.negotiatedMultilineMaxLines(serverId);
    if (maxLines > 0 && lineCount > maxLines) {
      return "Message has "
          + lineCount
          + " lines; negotiated multiline max-lines is "
          + maxLines
          + ".";
    }

    long maxBytes = irc.negotiatedMultilineMaxBytes(serverId);
    if (maxBytes > 0L && payloadUtf8Bytes > maxBytes) {
      return "Message is "
          + payloadUtf8Bytes
          + " UTF-8 bytes; negotiated multiline max-bytes is "
          + maxBytes
          + ".";
    }

    return "";
  }

  private static List<String> normalizeMessageLines(String raw) {
    String input = Objects.toString(raw, "");
    if (input.isEmpty()) return List.of();
    String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
    if (normalized.indexOf('\n') < 0) {
      return List.of(normalized);
    }
    String[] parts = normalized.split("\n", -1);
    List<String> out = new ArrayList<>(parts.length);
    for (String part : parts) {
      out.add(Objects.toString(part, ""));
    }
    return out;
  }

  private static String joinMessageLines(List<String> lines) {
    if (lines == null || lines.isEmpty()) return "";
    return String.join("\n", lines);
  }

  private static long multilinePayloadUtf8Bytes(List<String> lines) {
    if (lines == null || lines.isEmpty()) return 0L;
    long total = 0L;
    for (int i = 0; i < lines.size(); i++) {
      String line = Objects.toString(lines.get(i), "");
      total = addSaturated(total, line.getBytes(StandardCharsets.UTF_8).length);
      if (i < lines.size() - 1) {
        total = addSaturated(total, 1L);
      }
    }
    return total;
  }

  private static long addSaturated(long left, long right) {
    if (right <= 0L) return left;
    if (left >= Long.MAX_VALUE - right) return Long.MAX_VALUE;
    return left + right;
  }

  private PreparedRawLine prepareCorrelatedRawLine(TargetRef origin, String rawLine) {
    String line = rawLine == null ? "" : rawLine.trim();
    if (line.isEmpty() || origin == null) return new PreparedRawLine(line, "");
    if (!irc.isLabeledResponseAvailable(origin.serverId())) return new PreparedRawLine(line, "");

    LabeledResponseRoutingState.PreparedRawLine prepared =
        labeledResponseRoutingState.prepareOutgoingRaw(origin.serverId(), line);
    String sendLine =
        (prepared == null || prepared.line() == null || prepared.line().isBlank())
            ? line
            : prepared.line();
    String label = (prepared == null) ? "" : Objects.toString(prepared.label(), "").trim();
    if (!label.isEmpty()) {
      labeledResponseRoutingState.remember(
          origin.serverId(), label, origin, redactIfSensitive(line), Instant.now());
    }
    return new PreparedRawLine(sendLine, label);
  }

  private static String withLabelHint(String preview, String label) {
    String p = Objects.toString(preview, "").trim();
    String l = Objects.toString(label, "").trim();
    if (l.isEmpty()) return p;
    return p + " {label=" + l + "}";
  }

  private static String normalizeIrcv3Token(String raw) {
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return "";
    if (token.indexOf(' ') >= 0 || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) return "";
    return token;
  }

  private static String normalizeReactionToken(String raw) {
    return normalizeIrcv3Token(raw);
  }

  private static String escapeIrcv3TagValue(String value) {
    String raw = Objects.toString(value, "");
    if (raw.isEmpty()) return "";
    StringBuilder out = new StringBuilder(raw.length() + 8);
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      switch (c) {
        case ';' -> out.append("\\:");
        case ' ' -> out.append("\\s");
        case '\\' -> out.append("\\\\");
        case '\r' -> out.append("\\r");
        case '\n' -> out.append("\\n");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  private record PreparedRawLine(String line, String label) {}

  private static String redactIfSensitive(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.isEmpty()) return s;

    int sp = s.indexOf(' ');
    String head = (sp < 0 ? s : s.substring(0, sp)).trim();
    String upper = head.toUpperCase(Locale.ROOT);
    if (upper.equals("PASS") || upper.equals("OPER") || upper.equals("AUTHENTICATE")) {
      return upper + (sp < 0 ? "" : " <redacted>");
    }
    return s;
  }

  private PendingInviteState.PendingInvite resolveInviteByToken(
      String rawToken, TargetRef fallback, String statusTag) {
    TargetRef out = fallback != null ? fallback : targetCoordinator.safeStatusTarget();
    String token = Objects.toString(rawToken, "").trim();
    if (token.isEmpty() || "last".equalsIgnoreCase(token)) {
      PendingInviteState.PendingInvite invite = null;
      String sid = Objects.toString(out.serverId(), "").trim();
      if (!sid.isEmpty()) {
        invite = pendingInviteState.latestForServer(sid);
      }
      if (invite == null) {
        invite = pendingInviteState.latestAnyServer();
      }
      if (invite == null) {
        ui.appendStatus(out, statusTag, "No pending invites.");
      }
      return invite;
    }

    long id;
    try {
      id = Long.parseLong(token);
    } catch (NumberFormatException ex) {
      ui.appendStatus(out, statusTag, "Expected invite id or 'last' (example: /invjoin 12).");
      return null;
    }

    PendingInviteState.PendingInvite invite = pendingInviteState.get(id);
    if (invite == null) {
      ui.appendStatus(out, statusTag, "Invite #" + id + " not found.");
      return null;
    }
    return invite;
  }

  private static Boolean parseOnOff(String raw) {
    return switch (Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT)) {
      case "on", "true", "1", "yes" -> Boolean.TRUE;
      case "off", "false", "0", "no" -> Boolean.FALSE;
      default -> null;
    };
  }

  private ConnectionCommandTarget parseConnectionCommandTarget(String rawTarget) {
    String raw = Objects.toString(rawTarget, "").trim();
    if (raw.isEmpty()) {
      TargetRef at = targetCoordinator.getActiveTarget();
      if (at != null) {
        String sid = Objects.toString(at.serverId(), "").trim();
        if (!sid.isEmpty()) return new ConnectionCommandTarget(false, sid);
      }
      return new ConnectionCommandTarget(true, "");
    }

    if ("all".equalsIgnoreCase(raw) || "*".equals(raw)) {
      return new ConnectionCommandTarget(true, "");
    }

    if (raw.indexOf(' ') >= 0 || containsCrlf(raw)) {
      return null;
    }

    return new ConnectionCommandTarget(false, raw);
  }

  private record ConnectionCommandTarget(boolean all, String serverId) {}

  private static String resolveChannelOrNull(TargetRef active, String explicitChannel) {
    String ch = explicitChannel == null ? "" : explicitChannel.trim();
    if (!ch.isEmpty()) {
      if (ch.startsWith("#") || ch.startsWith("&")) return ch;
      return null;
    }
    if (active != null && active.isChannel()) return active.target();
    return null;
  }

  private static boolean containsCrlf(String s) {
    return s != null && (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0);
  }

  private static String normalizeChatHistorySelector(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return "";
    int eq = s.indexOf('=');
    if (eq <= 0 || eq == s.length() - 1) return "";
    String key = s.substring(0, eq).trim().toLowerCase(Locale.ROOT);
    String value = s.substring(eq + 1).trim();
    if (value.isEmpty()) return "";
    if (value.indexOf(' ') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) return "";
    if (!"msgid".equals(key) && !"timestamp".equals(key)) return "";
    return key + "=" + value;
  }

  private static String normalizeChatHistorySelectorOrWildcard(String raw) {
    String s = Objects.toString(raw, "").trim();
    if ("*".equals(s)) return "*";
    return normalizeChatHistorySelector(s);
  }

  private static int clampChatHistoryLimit(int limit) {
    int lim = limit;
    if (lim <= 0) lim = 50;
    if (lim > 200) lim = 200;
    return lim;
  }

  private TargetRef resolveChatHistoryTargetOrNull() {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(chathistory)", "Select a server first.");
      return null;
    }

    TargetRef status = new TargetRef(at.serverId(), "status");
    if (at.isStatus()) {
      ui.appendStatus(status, "(chathistory)", "Select a channel or query first.");
      return null;
    }
    if (at.isUiOnly()) {
      ui.appendStatus(status, "(chathistory)", "That view does not support history requests.");
      return null;
    }
    return at;
  }

  private void appendChatHistoryUsage(TargetRef at) {
    ui.appendStatus(at, "(chathistory)", "Usage: /chathistory [limit]");
    ui.appendStatus(
        at, "(chathistory)", "Usage: /chathistory before <msgid=...|timestamp=...> [limit]");
    ui.appendStatus(
        at, "(chathistory)", "Usage: /chathistory latest [*|msgid=...|timestamp=...] [limit]");
    ui.appendStatus(
        at, "(chathistory)", "Usage: /chathistory around <msgid=...|timestamp=...> [limit]");
    ui.appendStatus(at, "(chathistory)", "Usage: /chathistory between <start> <end> [limit]");
  }

  private boolean shouldUseLocalEcho(String serverId) {
    return !irc.isEchoMessageAvailable(serverId);
  }

  private void appendEditHelp(TargetRef out) {
    boolean available = isMessageEditSupportedForServer(out);
    String sid = out == null ? "" : out.serverId();
    ui.appendStatus(
        out,
        "(help)",
        "/edit <msgid> <message>"
            + availabilitySuffix(
                available,
                unavailableReasonForHelp(
                    sid, "requires negotiated draft/message-edit or message-edit")));
  }

  private void appendMarkReadHelp(TargetRef out) {
    boolean available = isReadMarkerSupportedForServer(out);
    String sid = out == null ? "" : out.serverId();
    ui.appendStatus(
        out,
        "(help)",
        "/markread"
            + availabilitySuffix(
                available,
                unavailableReasonForHelp(
                    sid, "requires negotiated read-marker or draft/read-marker")));
  }

  private void appendRedactHelp(TargetRef out) {
    boolean available = isMessageRedactionSupportedForServer(out);
    String sid = out == null ? "" : out.serverId();
    ui.appendStatus(
        out,
        "(help)",
        "/redact <msgid> [reason] (alias: /delete)"
            + availabilitySuffix(
                available,
                unavailableReasonForHelp(
                    sid, "requires negotiated draft/message-redaction or message-redaction")));
  }

  private boolean isMessageEditSupportedForServer(TargetRef target) {
    if (target == null) return false;
    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return false;
    try {
      return irc.isMessageEditAvailable(sid);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isMessageRedactionSupportedForServer(TargetRef target) {
    if (target == null) return false;
    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return false;
    try {
      return irc.isMessageRedactionAvailable(sid);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isReadMarkerSupportedForServer(TargetRef target) {
    if (target == null) return false;
    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return false;
    try {
      return irc.isReadMarkerAvailable(sid);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String normalizeHelpTopic(String raw) {
    String s = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (s.startsWith("/")) s = s.substring(1).trim();
    return s;
  }

  private static String availabilitySuffix(boolean available, String unavailableReason) {
    if (available) return "";
    String reason = Objects.toString(unavailableReason, "").trim();
    if (reason.isEmpty()) return " (unavailable)";
    return " (unavailable: " + reason + ")";
  }

  private String featureUnavailableMessage(String serverId, String fallback) {
    String backendReason = normalizedBackendAvailabilityReason(serverId);
    if (!backendReason.isEmpty()) return ensureTerminalPunctuation(backendReason);
    return fallback;
  }

  private String unavailableReasonForHelp(String serverId, String fallback) {
    String backendReason = normalizedBackendAvailabilityReason(serverId);
    if (!backendReason.isEmpty()) return backendReason;
    return fallback;
  }

  private String normalizedBackendAvailabilityReason(String serverId) {
    try {
      return Objects.toString(irc.backendAvailabilityReason(serverId), "").trim();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String ensureTerminalPunctuation(String message) {
    String text = Objects.toString(message, "").trim();
    if (text.isEmpty()) return "";
    char last = text.charAt(text.length() - 1);
    if (last == '.' || last == '!' || last == '?') return text;
    return text + ".";
  }

  private boolean isOwnMessageInBuffer(TargetRef target, String messageId) {
    try {
      return ui.isOwnMessage(target, messageId);
    } catch (Exception ignored) {
      return false;
    }
  }
}
