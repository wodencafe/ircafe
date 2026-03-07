package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Handles outbound /help and delegates /say and /quote flow. */
@Component
public class OutboundChatCommandService {

  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final OutboundSayQuoteCommandService outboundSayQuoteCommandService;
  private final List<OutboundHelpContributor> helpContributors;
  private final Map<String, HelpTopicHandler> helpTopicHandlers;

  public OutboundChatCommandService(
      UiPort ui,
      TargetCoordinator targetCoordinator,
      OutboundSayQuoteCommandService outboundSayQuoteCommandService,
      List<OutboundHelpContributor> helpContributors) {
    this.ui = Objects.requireNonNull(ui, "ui");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.outboundSayQuoteCommandService =
        Objects.requireNonNull(outboundSayQuoteCommandService, "outboundSayQuoteCommandService");
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

  public void handleSay(CompositeDisposable disposables, String msg) {
    outboundSayQuoteCommandService.handleSay(disposables, msg);
  }

  public void handleQuote(CompositeDisposable disposables, String rawLine) {
    outboundSayQuoteCommandService.handleQuote(disposables, rawLine);
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

  private static String normalizeHelpTopic(String raw) {
    String s = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (s.startsWith("/")) s = s.substring(1).trim();
    return s;
  }
}
