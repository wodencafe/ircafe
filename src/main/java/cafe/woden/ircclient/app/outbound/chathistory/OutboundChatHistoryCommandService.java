package cafe.woden.ircclient.app.outbound.chathistory;

import cafe.woden.ircclient.app.api.Ircv3ChatHistoryFeatureSupport;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.help.spi.OutboundHelpContributor;
import cafe.woden.ircclient.app.outbound.help.spi.OutboundHelpSink;
import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryRuntimeSupport;
import cafe.woden.ircclient.irc.port.IrcChatHistoryPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.ChatHistoryRequestRoutingPort.QueryMode;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.NonNull;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Handles outbound /chathistory command flow and targeted /help chathistory output. */
@Component
@ApplicationLayer
public final class OutboundChatHistoryCommandService implements OutboundHelpContributor {

  @NonNull private final IrcChatHistoryPort irc;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport;
  @NonNull private final OutboundChatHistoryRequestSupport chatHistoryRequestSupport;
  @NonNull private final Ircv3ChatHistoryRuntimeSupport chatHistoryRuntimeSupport;

  @Autowired
  public OutboundChatHistoryCommandService(
      IrcChatHistoryPort irc,
      TargetCoordinator targetCoordinator,
      Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport,
      OutboundChatHistoryRequestSupport chatHistoryRequestSupport,
      Ircv3ChatHistoryRuntimeSupport chatHistoryRuntimeSupport) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.chatHistoryFeatureSupport =
        Objects.requireNonNull(chatHistoryFeatureSupport, "chatHistoryFeatureSupport");
    this.chatHistoryRequestSupport =
        Objects.requireNonNull(chatHistoryRequestSupport, "chatHistoryRequestSupport");
    this.chatHistoryRuntimeSupport =
        Objects.requireNonNull(chatHistoryRuntimeSupport, "chatHistoryRuntimeSupport");
  }

  @Override
  public void appendGeneralHelp(OutboundHelpSink help) {}

  @Override
  public Map<String, Consumer<OutboundHelpSink>> topicHelpHandlers() {
    return Map.of(
        "chathistory", help -> appendChatHistoryAvailability(targetRef(help)),
        "history", help -> appendChatHistoryAvailability(targetRef(help)));
  }

  public void handleChatHistoryBefore(CompositeDisposable disposables, int limit) {
    handleChatHistoryBefore(disposables, limit, "");
  }

  public void handleChatHistoryBefore(CompositeDisposable disposables, int limit, String selector) {
    TargetRef at = chatHistoryRequestSupport.resolveChatHistoryTargetOrNull();
    if (at == null) return;

    if (limit <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    Ircv3ChatHistoryRuntimeSupport.Plan plan;
    try {
      plan = chatHistoryRuntimeSupport.before(at.target(), selector, limit, Instant.now());
    } catch (IllegalArgumentException error) {
      chatHistoryRequestSupport.appendStatus(at, "Selector must be msgid=... or timestamp=...");
      return;
    }
    final String selectorFinal = plan.primarySelector();
    final int limitFinal = plan.limit();
    chatHistoryRequestSupport.requestChatHistory(
        disposables,
        at,
        limitFinal,
        selectorFinal,
        QueryMode.BEFORE,
        "Requesting older history… limit=" + limitFinal,
        plan.rawLine(),
        () -> irc.requestChatHistoryBefore(at.serverId(), at.target(), selectorFinal, limitFinal));
  }

  public void handleChatHistoryLatest(CompositeDisposable disposables, int limit, String selector) {
    TargetRef at = chatHistoryRequestSupport.resolveChatHistoryTargetOrNull();
    if (at == null) return;

    if (limit <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    Ircv3ChatHistoryRuntimeSupport.Plan plan;
    try {
      plan = chatHistoryRuntimeSupport.latest(at.target(), selector, limit);
    } catch (IllegalArgumentException error) {
      chatHistoryRequestSupport.appendStatus(
          at, "Selector must be * or msgid=... or timestamp=...");
      return;
    }
    final String selectorFinal = plan.primarySelector();
    final int limitFinal = plan.limit();
    chatHistoryRequestSupport.requestChatHistory(
        disposables,
        at,
        limitFinal,
        selectorFinal,
        QueryMode.LATEST,
        "Requesting latest/newer history… limit=" + limitFinal,
        plan.rawLine(),
        () -> irc.requestChatHistoryLatest(at.serverId(), at.target(), selectorFinal, limitFinal));
  }

  public void handleChatHistoryAround(CompositeDisposable disposables, String selector, int limit) {
    TargetRef at = chatHistoryRequestSupport.resolveChatHistoryTargetOrNull();
    if (at == null) return;

    if (limit <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    Ircv3ChatHistoryRuntimeSupport.Plan plan;
    try {
      plan = chatHistoryRuntimeSupport.around(at.target(), selector, limit);
    } catch (IllegalArgumentException error) {
      chatHistoryRequestSupport.appendStatus(
          at, "Around selector must be msgid=... or timestamp=...");
      return;
    }
    final String selectorFinal = plan.primarySelector();
    final int limitFinal = plan.limit();
    chatHistoryRequestSupport.requestChatHistory(
        disposables,
        at,
        limitFinal,
        selectorFinal,
        QueryMode.AROUND,
        "Requesting message context around selector… limit=" + limitFinal,
        plan.rawLine(),
        () -> irc.requestChatHistoryAround(at.serverId(), at.target(), selectorFinal, limitFinal));
  }

  public void handleChatHistoryBetween(
      CompositeDisposable disposables, String startSelector, String endSelector, int limit) {
    TargetRef at = chatHistoryRequestSupport.resolveChatHistoryTargetOrNull();
    if (at == null) return;

    if (limit <= 0) {
      appendChatHistoryUsage(at);
      return;
    }
    Ircv3ChatHistoryRuntimeSupport.Plan plan;
    try {
      plan = chatHistoryRuntimeSupport.between(at.target(), startSelector, endSelector, limit);
    } catch (IllegalArgumentException error) {
      chatHistoryRequestSupport.appendStatus(
          at, "Between selectors must be * or msgid=... or timestamp=...");
      return;
    }
    final String startFinal = plan.primarySelector();
    final String endFinal = plan.secondarySelector();
    final int limitFinal = plan.limit();
    chatHistoryRequestSupport.requestChatHistory(
        disposables,
        at,
        limitFinal,
        startFinal + " .. " + endFinal,
        QueryMode.BETWEEN,
        "Requesting bounded history window… limit=" + limitFinal,
        plan.rawLine(),
        () ->
            irc.requestChatHistoryBetween(
                at.serverId(), at.target(), startFinal, endFinal, limitFinal));
  }

  private void appendChatHistoryUsage(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    appendChatHistoryAvailability(target);
    appendChatHistoryUsageDetails(target);
  }

  private void appendChatHistoryAvailability(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    chatHistoryRequestSupport.appendHelp(
        target, "/chathistory [limit]" + helpAvailabilitySuffix(target.serverId()));
  }

  private void appendChatHistoryUsageDetails(TargetRef target) {
    chatHistoryRequestSupport.appendHelp(
        target, "/chathistory before <msgid=...|timestamp=...> [limit]");
    chatHistoryRequestSupport.appendHelp(
        target, "/chathistory latest [*|msgid=...|timestamp=...] [limit]");
    chatHistoryRequestSupport.appendHelp(
        target, "/chathistory around <msgid=...|timestamp=...> [limit]");
    chatHistoryRequestSupport.appendHelp(target, "/chathistory between <start> <end> [limit]");
  }

  private TargetRef targetRef(OutboundHelpSink help) {
    if (help == null
        || help.target() == null
        || help.target().serverId().isBlank()
        || help.target().target().isBlank()) {
      return targetCoordinator.safeStatusTarget();
    }
    return new TargetRef(help.target().serverId(), help.target().target());
  }

  private String helpAvailabilitySuffix(String serverId) {
    if (chatHistoryFeatureSupport.isAvailable(serverId)) {
      return "";
    }
    String reason = chatHistoryFeatureSupport.unavailableReasonForHelp(serverId);
    if (reason.isBlank()) {
      return "";
    }
    return " (unavailable: " + reason + ")";
  }
}
