package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort.QueryMode;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles outbound /chathistory command flow and targeted /help chathistory output. */
@Component
@ApplicationLayer
final class OutboundChatHistoryCommandService implements OutboundHelpContributor {

  private static final DateTimeFormatter CHATHISTORY_TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private final IrcClientService irc;
  private final TargetCoordinator targetCoordinator;
  private final OutboundChatHistoryRequestSupport chatHistoryRequestSupport;

  OutboundChatHistoryCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ChatHistoryRequestRoutingPort chatHistoryRequestRoutingState) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.chatHistoryRequestSupport =
        new OutboundChatHistoryRequestSupport(
            Objects.requireNonNull(ui, "ui"),
            Objects.requireNonNull(connectionCoordinator, "connectionCoordinator"),
            targetCoordinator,
            Objects.requireNonNull(
                chatHistoryRequestRoutingState, "chatHistoryRequestRoutingState"));
  }

  @Override
  public void appendGeneralHelp(TargetRef out) {}

  @Override
  public Map<String, Consumer<TargetRef>> topicHelpHandlers() {
    return Map.of("chathistory", this::appendChatHistoryUsage);
  }

  void handleChatHistoryBefore(CompositeDisposable disposables, int limit) {
    handleChatHistoryBefore(disposables, limit, "");
  }

  void handleChatHistoryBefore(CompositeDisposable disposables, int limit, String selector) {
    TargetRef at = chatHistoryRequestSupport.resolveChatHistoryTargetOrNull();
    if (at == null) return;

    int lim = limit;
    String selectorToken = normalizeChatHistorySelector(selector);
    if (lim <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    if (!Objects.toString(selector, "").trim().isEmpty() && selectorToken.isEmpty()) {
      chatHistoryRequestSupport.appendStatus(at, "Selector must be msgid=... or timestamp=...");
      return;
    }
    lim = clampChatHistoryLimit(lim);

    if (selectorToken.isEmpty()) {
      selectorToken = "timestamp=" + CHATHISTORY_TS_FMT.format(Instant.now());
    }
    String preview = "CHATHISTORY BEFORE " + at.target() + " " + selectorToken + " " + lim;
    final String selectorFinal = selectorToken;
    final int limitFinal = lim;
    chatHistoryRequestSupport.requestChatHistory(
        disposables,
        at,
        limitFinal,
        selectorFinal,
        QueryMode.BEFORE,
        "Requesting older history… limit=" + limitFinal,
        preview,
        () -> irc.requestChatHistoryBefore(at.serverId(), at.target(), selectorFinal, limitFinal));
  }

  void handleChatHistoryLatest(CompositeDisposable disposables, int limit, String selector) {
    TargetRef at = chatHistoryRequestSupport.resolveChatHistoryTargetOrNull();
    if (at == null) return;

    int lim = limit;
    if (lim <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    lim = clampChatHistoryLimit(lim);

    String selectorToken = normalizeChatHistorySelectorOrWildcard(selector);
    if (!Objects.toString(selector, "").trim().isEmpty() && selectorToken.isEmpty()) {
      chatHistoryRequestSupport.appendStatus(
          at, "Selector must be * or msgid=... or timestamp=...");
      return;
    }
    if (selectorToken.isEmpty()) {
      selectorToken = "*";
    }

    String preview = "CHATHISTORY LATEST " + at.target() + " " + selectorToken + " " + lim;
    final String selectorFinal = selectorToken;
    final int limitFinal = lim;
    chatHistoryRequestSupport.requestChatHistory(
        disposables,
        at,
        limitFinal,
        selectorFinal,
        QueryMode.LATEST,
        "Requesting latest/newer history… limit=" + limitFinal,
        preview,
        () -> irc.requestChatHistoryLatest(at.serverId(), at.target(), selectorFinal, limitFinal));
  }

  void handleChatHistoryAround(CompositeDisposable disposables, String selector, int limit) {
    TargetRef at = chatHistoryRequestSupport.resolveChatHistoryTargetOrNull();
    if (at == null) return;

    int lim = limit;
    if (lim <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    lim = clampChatHistoryLimit(lim);

    String selectorToken = normalizeChatHistorySelector(selector);
    if (selectorToken.isEmpty()) {
      chatHistoryRequestSupport.appendStatus(
          at, "Around selector must be msgid=... or timestamp=...");
      return;
    }

    String preview = "CHATHISTORY AROUND " + at.target() + " " + selectorToken + " " + lim;
    final String selectorFinal = selectorToken;
    final int limitFinal = lim;
    chatHistoryRequestSupport.requestChatHistory(
        disposables,
        at,
        limitFinal,
        selectorFinal,
        QueryMode.AROUND,
        "Requesting message context around selector… limit=" + limitFinal,
        preview,
        () -> irc.requestChatHistoryAround(at.serverId(), at.target(), selectorFinal, limitFinal));
  }

  void handleChatHistoryBetween(
      CompositeDisposable disposables, String startSelector, String endSelector, int limit) {
    TargetRef at = chatHistoryRequestSupport.resolveChatHistoryTargetOrNull();
    if (at == null) return;

    int lim = limit;
    if (lim <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    lim = clampChatHistoryLimit(lim);

    String startToken = normalizeChatHistorySelectorOrWildcard(startSelector);
    String endToken = normalizeChatHistorySelectorOrWildcard(endSelector);
    if (startToken.isEmpty() || endToken.isEmpty()) {
      chatHistoryRequestSupport.appendStatus(
          at, "Between selectors must be * or msgid=... or timestamp=...");
      return;
    }

    String preview =
        "CHATHISTORY BETWEEN " + at.target() + " " + startToken + " " + endToken + " " + lim;
    final String startFinal = startToken;
    final String endFinal = endToken;
    final int limitFinal = lim;
    chatHistoryRequestSupport.requestChatHistory(
        disposables,
        at,
        limitFinal,
        startFinal + " .. " + endFinal,
        QueryMode.BETWEEN,
        "Requesting bounded history window… limit=" + limitFinal,
        preview,
        () ->
            irc.requestChatHistoryBetween(
                at.serverId(), at.target(), startFinal, endFinal, limitFinal));
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

  private void appendChatHistoryUsage(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    chatHistoryRequestSupport.appendHelp(target, "/chathistory [limit]");
    chatHistoryRequestSupport.appendHelp(
        target, "/chathistory before <msgid=...|timestamp=...> [limit]");
    chatHistoryRequestSupport.appendHelp(
        target, "/chathistory latest [*|msgid=...|timestamp=...] [limit]");
    chatHistoryRequestSupport.appendHelp(
        target, "/chathistory around <msgid=...|timestamp=...> [limit]");
    chatHistoryRequestSupport.appendHelp(target, "/chathistory between <start> <end> [limit]");
  }
}
