package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * Handles outbound "chatty" slash commands extracted from {@code IrcMediator}.
 *
 * <p>Includes: /say, /quote, /help.
 *
 * <p>Behavior is intended to be preserved.
 */
@Component
public class OutboundChatCommandService {

  private final IrcClientService irc;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final OutboundRawLineCorrelationService rawLineCorrelationService;
  private final OutboundMessagingCommandService outboundMessagingCommandService;
  private final List<OutboundHelpContributor> helpContributors;
  private final Map<String, HelpTopicHandler> helpTopicHandlers;

  public OutboundChatCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      OutboundRawLineCorrelationService rawLineCorrelationService,
      List<OutboundHelpContributor> helpContributors,
      OutboundMessagingCommandService outboundMessagingCommandService) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.rawLineCorrelationService =
        Objects.requireNonNull(rawLineCorrelationService, "rawLineCorrelationService");
    this.outboundMessagingCommandService =
        Objects.requireNonNull(outboundMessagingCommandService, "outboundMessagingCommandService");
    this.helpContributors =
        List.copyOf(Objects.requireNonNull(helpContributors, "helpContributors"));
    this.helpTopicHandlers = buildHelpTopicHandlers();
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

    outboundMessagingCommandService.sendMessage(disposables, at, m);
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

  private static String normalizeHelpTopic(String raw) {
    String s = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (s.startsWith("/")) s = s.substring(1).trim();
    return s;
  }
}
