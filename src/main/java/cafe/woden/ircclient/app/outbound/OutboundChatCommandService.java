package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.api.ChatCommandRuntimeConfigPort;
import cafe.woden.ircclient.irc.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.JoinRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Handles outbound "chatty" slash commands extracted from {@code IrcMediator}.
 *
 * <p>Includes: /join, /part, /connect, /disconnect, /reconnect, /quit, /nick, /away, /query, /msg,
 * /notice, /me, /topic, /kick, /say, /quote.
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

  private final IrcClientService irc;
  private final IrcBackendAvailabilityPort backendAvailability;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final OutboundRawLineCorrelationService rawLineCorrelationService;
  private final List<OutboundHelpContributor> helpContributors;

  private final CommandTargetPolicy commandTargetPolicy;
  private final ChatCommandRuntimeConfigPort runtimeConfig;
  private final AwayRoutingPort awayRoutingState;
  private final JoinRoutingPort joinRoutingState;
  private final PendingEchoMessagePort pendingEchoMessageState;
  private final Map<String, HelpTopicHandler> helpTopicHandlers;

  public OutboundChatCommandService(
      IrcClientService irc,
      @Qualifier("ircClientService") IrcBackendAvailabilityPort backendAvailability,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      OutboundRawLineCorrelationService rawLineCorrelationService,
      List<OutboundHelpContributor> helpContributors,
      CommandTargetPolicy commandTargetPolicy,
      ChatCommandRuntimeConfigPort runtimeConfig,
      AwayRoutingPort awayRoutingState,
      JoinRoutingPort joinRoutingState,
      PendingEchoMessagePort pendingEchoMessageState) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.backendAvailability = Objects.requireNonNull(backendAvailability, "backendAvailability");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.rawLineCorrelationService =
        Objects.requireNonNull(rawLineCorrelationService, "rawLineCorrelationService");
    this.helpContributors =
        List.copyOf(Objects.requireNonNull(helpContributors, "helpContributors"));

    this.commandTargetPolicy = Objects.requireNonNull(commandTargetPolicy, "commandTargetPolicy");
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.awayRoutingState = Objects.requireNonNull(awayRoutingState, "awayRoutingState");
    this.joinRoutingState = Objects.requireNonNull(joinRoutingState, "joinRoutingState");
    this.pendingEchoMessageState =
        Objects.requireNonNull(pendingEchoMessageState, "pendingEchoMessageState");
    this.helpTopicHandlers = buildHelpTopicHandlers();
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
      if (!commandTargetPolicy.isChannelLikeTarget(at)) {
        ui.appendStatus(
            at, "(part)", "Usage: /part [#channel] [reason] (or select a channel first)");
        return;
      }
      target = at;
    } else {
      target = new TargetRef(at.serverId(), chan);
      if (!commandTargetPolicy.isChannelLikeTargetForServer(at.serverId(), target.target())) {
        ui.appendStatus(at, "(part)", "Usage: /part [#channel] [reason]");
        return;
      }
    }

    if (target.isChannel()) {
      targetCoordinator.disconnectChannel(target, msg);
      return;
    }

    TargetRef status = new TargetRef(target.serverId(), "status");
    if (!connectionCoordinator.isConnected(target.serverId())) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (containsCrlf(target.target()) || containsCrlf(msg)) {
      ui.appendStatus(status, "(part)", "Refusing to send multi-line /part input.");
      return;
    }

    disposables.add(
        irc.partChannel(target.serverId(), target.target(), msg.isEmpty() ? null : msg)
            .subscribe(
                () -> ui.appendStatus(status, "(part)", "Requested leave for " + target.target()),
                err -> ui.appendError(status, "(part-error)", String.valueOf(err))));
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

    if (commandTargetPolicy.isChannelLikeTargetForServer(at.serverId(), f)) {
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

  public void handleHelp(String topic) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef out = (at != null) ? at : targetCoordinator.safeStatusTarget();
    String t = normalizeHelpTopic(topic);
    if (!t.isEmpty()) {
      HelpTopicHandler handler = helpTopicHandlers.get(t);
      if (handler != null) {
        handler.handle(out);
        return;
      }
      ui.appendStatus(out, "(help)", "No dedicated help for '" + t + "'. Showing common commands.");
    }

    ui.appendStatus(
        out,
        "(help)",
        "Common: /join /part /msg /notice /me /query /whois /names /list /topic /monitor /chathistory /quote /dcc /quasselsetup /quasselnet");
    ui.appendStatus(
        out,
        "(help)",
        "Invites: /invites /invjoin (/join -i) /invignore /invwhois /invblock /inviteautojoin (/ajinvite)");
    helpContributors.forEach(contributor -> contributor.appendGeneralHelp(out));
    ui.appendStatus(out, "(help)", "Tip: /help dcc for direct-chat/file-transfer commands.");
    ui.appendStatus(
        out,
        "(help)",
        "Tip: /help edit, /help redact, /help markread, or /help upload for focused details.");
  }

  private Map<String, HelpTopicHandler> buildHelpTopicHandlers() {
    LinkedHashMap<String, HelpTopicHandler> handlers = new LinkedHashMap<>();
    registerHelpTopicHandler(handlers, this::appendDccHelp, "dcc");
    for (OutboundHelpContributor contributor : helpContributors) {
      registerHelpTopicHandlers(handlers, contributor.topicHelpHandlers());
    }
    return Map.copyOf(handlers);
  }

  private static void registerHelpTopicHandler(
      Map<String, HelpTopicHandler> handlers, HelpTopicHandler handler, String... topics) {
    if (handlers == null || handler == null || topics == null) return;
    for (String rawTopic : topics) {
      String topic = normalizeHelpTopic(rawTopic);
      if (!topic.isEmpty()) {
        handlers.put(topic, handler);
      }
    }
  }

  private static void registerHelpTopicHandlers(
      Map<String, HelpTopicHandler> handlers, Map<String, Consumer<TargetRef>> topicHandlers) {
    if (handlers == null || topicHandlers == null || topicHandlers.isEmpty()) return;
    for (Map.Entry<String, Consumer<TargetRef>> entry : topicHandlers.entrySet()) {
      String topic = normalizeHelpTopic(entry.getKey());
      Consumer<TargetRef> consumer = entry.getValue();
      if (!topic.isEmpty() && consumer != null) {
        handlers.put(topic, consumer::accept);
      }
    }
  }

  @FunctionalInterface
  private interface HelpTopicHandler {
    void handle(TargetRef out);
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
        status,
        "(raw)",
        "→ "
            + withLabelHint(
                OutboundRawLineCorrelationService.redactIfSensitive(line), prepared.label()));

    disposables.add(
        irc.sendRaw(sid, prepared.line())
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(raw-error)", String.valueOf(err))));
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
    String echo = OutboundRawLineCorrelationService.redactIfSensitive(line);
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
    final PendingEchoMessagePort.PendingOutboundChat pendingEntry;
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
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(origin, rawLine);
    return new PreparedRawLine(prepared.line(), prepared.label());
  }

  private static String withLabelHint(String preview, String label) {
    String p = Objects.toString(preview, "").trim();
    String l = Objects.toString(label, "").trim();
    if (l.isEmpty()) return p;
    return p + " {label=" + l + "}";
  }

  private record PreparedRawLine(String line, String label) {}

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

  private String resolveChannelOrNull(TargetRef active, String explicitChannel) {
    String ch = explicitChannel == null ? "" : explicitChannel.trim();
    if (!ch.isEmpty()) {
      String sid = active == null ? "" : active.serverId();
      if (commandTargetPolicy.isChannelLikeTargetForServer(sid, ch)) return ch;
      return null;
    }
    if (commandTargetPolicy.isChannelLikeTarget(active)) return active.target();
    return null;
  }

  private static boolean containsCrlf(String s) {
    return s != null && (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0);
  }

  private boolean shouldUseLocalEcho(String serverId) {
    return !irc.isEchoMessageAvailable(serverId);
  }

  private static String normalizeHelpTopic(String raw) {
    String s = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (s.startsWith("/")) s = s.substring(1).trim();
    return s;
  }

  private String normalizedBackendAvailabilityReason(String serverId) {
    try {
      return Objects.toString(backendAvailability.backendAvailabilityReason(serverId), "").trim();
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
}
