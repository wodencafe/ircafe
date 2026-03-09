package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort;
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
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final ChatHistoryRequestRoutingPort chatHistoryRequestRoutingState;

  OutboundChatHistoryCommandService(
      IrcClientService irc,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      ChatHistoryRequestRoutingPort chatHistoryRequestRoutingState) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.chatHistoryRequestRoutingState =
        Objects.requireNonNull(chatHistoryRequestRoutingState, "chatHistoryRequestRoutingState");
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
        ChatHistoryRequestRoutingPort.QueryMode.BEFORE);
    disposables.add(
        irc.requestChatHistoryBefore(at.serverId(), at.target(), selectorFinal, limitFinal)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
  }

  void handleChatHistoryLatest(CompositeDisposable disposables, int limit, String selector) {
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
        ChatHistoryRequestRoutingPort.QueryMode.LATEST);
    disposables.add(
        irc.requestChatHistoryLatest(at.serverId(), at.target(), selectorFinal, limitFinal)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
  }

  void handleChatHistoryAround(CompositeDisposable disposables, String selector, int limit) {
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
        ChatHistoryRequestRoutingPort.QueryMode.AROUND);
    disposables.add(
        irc.requestChatHistoryAround(at.serverId(), at.target(), selectorFinal, limitFinal)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
  }

  void handleChatHistoryBetween(
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
        ChatHistoryRequestRoutingPort.QueryMode.BETWEEN);
    disposables.add(
        irc.requestChatHistoryBetween(at.serverId(), at.target(), startFinal, endFinal, limitFinal)
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(chathistory-error)", String.valueOf(err))));
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

  private void appendChatHistoryUsage(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    ui.appendStatus(target, "(help)", "/chathistory [limit]");
    ui.appendStatus(target, "(help)", "/chathistory before <msgid=...|timestamp=...> [limit]");
    ui.appendStatus(target, "(help)", "/chathistory latest [*|msgid=...|timestamp=...] [limit]");
    ui.appendStatus(target, "(help)", "/chathistory around <msgid=...|timestamp=...> [limit]");
    ui.appendStatus(target, "(help)", "/chathistory between <start> <end> [limit]");
  }
}
