package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.AwayRoutingPort;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Coordinates server/status event rendering side effects extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorServerStatusEventHandler {
  private static final Duration AWAY_ORIGIN_MAX_AGE = Duration.ofSeconds(15);
  private static final Duration LABELED_RESPONSE_CORRELATION_WINDOW = Duration.ofMinutes(2);
  private static final Duration LABELED_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration PENDING_ECHO_TIMEOUT = Duration.ofSeconds(45);
  private static final int PENDING_ECHO_TIMEOUT_BATCH_MAX = 64;

  interface Callbacks {
    TargetRef safeStatusTarget();

    void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write);

    TargetRef resolveActiveOrStatus(String sid, TargetRef status);

    void recordInterceptorEvent(
        String serverId,
        String target,
        String actorNick,
        String hostmask,
        String text,
        InterceptorEventType eventType);

    String learnedHostmaskForNick(String sid, String nick);

    boolean notifyIrcEvent(
        IrcEventNotificationRule.EventType eventType,
        String serverId,
        String channel,
        String sourceNick,
        String title,
        String body);
  }

  private final IrcMediatorInteractionPort irc;
  private final UiPort ui;
  private final AwayRoutingPort awayRoutingState;
  private final WhoisRoutingPort whoisRoutingState;
  private final MonitorFallbackPort monitorFallbackPort;
  private final LabeledResponseRoutingPort labeledResponseRoutingState;
  private final PendingEchoMessagePort pendingEchoMessageState;
  private final ServerIsupportStatePort serverIsupportState;
  private final ConnectionCoordinator connectionCoordinator;

  public void handleNickChanged(String sid, TargetRef status, IrcEvent.NickChanged event) {
    irc.currentNick(sid)
        .ifPresent(
            currentNick -> {
              if (!Objects.equals(currentNick, event.oldNick())
                  && !Objects.equals(currentNick, event.newNick())) {
                ui.appendNotice(
                    status, "(nick)", event.oldNick() + " is now known as " + event.newNick());
              } else {
                ui.appendStatus(status, "(nick)", "Now known as " + event.newNick());
                ui.setChatCurrentNick(sid, event.newNick());
              }
            });
  }

  public void handleWallopsReceived(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.WallopsReceived event) {
    TargetRef dest = statusOrSafe(callbacks, status);
    String from = Objects.toString(event.from(), "").trim();
    if (from.isEmpty()) {
      from = "server";
    }
    String body = Objects.toString(event.text(), "").trim();
    if (body.isEmpty()) {
      body = "(empty WALLOPS)";
    }

    String rendered = from + ": " + body;
    callbacks.postTo(dest, true, d -> ui.appendStatusAt(d, event.at(), "(wallops)", rendered));
    callbacks.recordInterceptorEvent(
        sid,
        "status",
        from,
        callbacks.learnedHostmaskForNick(sid, from),
        body,
        InterceptorEventType.SERVER);

    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.WALLOPS_RECEIVED,
        sid,
        null,
        from,
        from.equalsIgnoreCase("server") ? "WALLOPS" : ("WALLOPS from " + from),
        body);
  }

  public void handleServerTimeNotNegotiated(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.ServerTimeNotNegotiated event) {
    TargetRef dest = statusOrSafe(callbacks, status);
    ui.appendStatus(dest, "(ircv3)", event.message());
    callbacks.recordInterceptorEvent(
        sid, "status", "server", "", event.message(), InterceptorEventType.SERVER);
  }

  public void handleStandardReplyEvent(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.StandardReply event) {
    handleStandardReply(callbacks, sid, status, event);
    callbacks.recordInterceptorEvent(
        sid,
        "status",
        "server",
        "",
        Objects.toString(event.description(), "").trim(),
        InterceptorEventType.SERVER);
  }

  public void handleChannelListStarted(String sid, IrcEvent.ChannelListStarted event) {
    ui.beginChannelList(sid, event.banner());
  }

  public void handleChannelListEntry(String sid, IrcEvent.ChannelListEntry event) {
    ui.appendChannelListEntry(sid, event.channel(), event.visibleUsers(), event.topic());
  }

  public void handleChannelListEnded(String sid, IrcEvent.ChannelListEnded event) {
    ui.endChannelList(sid, event.summary());
  }

  public void handleChannelBanListStarted(String sid, IrcEvent.ChannelBanListStarted event) {
    ui.beginChannelBanList(sid, event.channel());
  }

  public void handleChannelBanListEntry(String sid, IrcEvent.ChannelBanListEntry event) {
    ui.appendChannelBanListEntry(
        sid, event.channel(), event.mask(), event.setBy(), event.setAtEpochSeconds());
  }

  public void handleChannelBanListEnded(String sid, IrcEvent.ChannelBanListEnded event) {
    ui.endChannelBanList(sid, event.channel(), event.summary());
  }

  public void handleServerResponseLineEvent(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.ServerResponseLine event) {
    handleServerResponseLine(callbacks, sid, status, event);
    String rawLine = Objects.toString(event.rawLine(), "").trim();
    if (rawLine.isEmpty()) {
      rawLine = Objects.toString(event.message(), "").trim();
    }
    callbacks.recordInterceptorEvent(
        sid, "status", "server", "", rawLine, InterceptorEventType.SERVER);
  }

  public void handleError(Callbacks callbacks, String sid, TargetRef status, IrcEvent.Error event) {
    TargetRef dest = status != null ? status : callbacks.safeStatusTarget();
    connectionCoordinator.noteConnectionError(sid, event.message());
    ui.appendError(dest, "(error)", event.message());
    callbacks.recordInterceptorEvent(
        sid, "status", "server", "", event.message(), InterceptorEventType.ERROR);
    maybeNotifyKline(callbacks, sid, event.message(), "Server restriction");
  }

  public void handleAwayStatusChanged(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.AwayStatusChanged event) {
    awayRoutingState.setAway(sid, event.away());
    if (!event.away()) {
      awayRoutingState.setLastReason(sid, null);
    }
    TargetRef dest = null;
    TargetRef origin = awayRoutingState.recentOriginIfFresh(sid, AWAY_ORIGIN_MAX_AGE);
    if (origin != null && Objects.equals(origin.serverId(), sid)) {
      dest = origin;
    }
    if (dest == null) {
      dest = callbacks.resolveActiveOrStatus(sid, status);
    }

    final String rendered;
    if (event.away()) {
      String reason = awayRoutingState.getLastReason(sid);
      if (reason != null && !reason.isBlank()) {
        rendered = "You are now marked as being away (Reason: " + reason + ")";
      } else {
        rendered = event.message();
      }
    } else {
      rendered = "You are no longer marked as being away";
    }

    TargetRef finalDest = dest;
    callbacks.postTo(finalDest, true, d -> ui.appendStatus(d, "(away)", rendered));
  }

  public void handleWhoisResult(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.WhoisResult event) {
    TargetRef dest = whoisRoutingState.remove(sid, event.nick());
    if (dest == null) {
      dest = status;
    }
    callbacks.postTo(
        dest,
        true,
        d -> {
          ui.appendStatus(d, "(whois)", "WHOIS for " + event.nick());
          for (String line : event.lines()) {
            ui.appendStatus(d, "(whois)", line);
          }
        });
  }

  public void handleLabeledRequestTimeouts(Callbacks callbacks) {
    List<LabeledResponseRoutingPort.TimedOutLabeledRequest> timedOut =
        labeledResponseRoutingState.collectTimedOut(LABELED_RESPONSE_TIMEOUT, 32);
    if (timedOut == null || timedOut.isEmpty()) {
      return;
    }
    for (LabeledResponseRoutingPort.TimedOutLabeledRequest timeout : timedOut) {
      if (timeout == null || timeout.request() == null) {
        continue;
      }
      TargetRef status = new TargetRef(timeout.serverId(), "status");
      TargetRef dest =
          normalizeLabeledDestination(timeout.serverId(), status, timeout.request().originTarget());
      appendLabeledOutcome(
          dest,
          timeout.timedOutAt(),
          timeout.label(),
          timeout.request().requestPreview(),
          LabeledResponseRoutingPort.Outcome.TIMEOUT,
          "no reply within " + LABELED_RESPONSE_TIMEOUT.toSeconds() + "s");
    }
  }

  public void handlePendingEchoTimeouts() {
    Instant now = Instant.now();
    List<PendingEchoMessagePort.PendingOutboundChat> timedOut =
        pendingEchoMessageState.collectTimedOut(
            PENDING_ECHO_TIMEOUT, PENDING_ECHO_TIMEOUT_BATCH_MAX, now);
    if (timedOut == null || timedOut.isEmpty()) {
      return;
    }

    String reason =
        "Timed out waiting for server echo after " + PENDING_ECHO_TIMEOUT.toSeconds() + "s";
    for (PendingEchoMessagePort.PendingOutboundChat pending : timedOut) {
      if (pending == null || pending.target() == null) {
        continue;
      }
      ui.failPendingOutgoingChat(
          pending.target(), pending.pendingId(), now, pending.fromNick(), pending.text(), reason);
    }
  }

  private static TargetRef statusOrSafe(Callbacks callbacks, TargetRef status) {
    return status != null ? status : callbacks.safeStatusTarget();
  }

  private void handleServerResponseLine(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.ServerResponseLine event) {
    ui.ensureTargetExists(status);
    String msg = Objects.toString(event.message(), "");
    String rendered = "[" + event.code() + "] " + msg;
    updateServerMetadataFromServerResponseLine(sid, event);
    maybeHandlePendingPrivateMessageDeliveryError(sid, event);
    boolean suppressStatusLine =
        event.code() == 322; // /LIST entry rows are shown in the dedicated channel-list panel.
    if (event.code() == 303 && monitorFallbackPort.shouldSuppressIsonServerResponse(sid)) {
      suppressStatusLine = true;
    }
    if (event.code() == 321) {
      rendered =
          "[321] "
              + (msg.isBlank()
                  ? "Channel list follows (see Channel List)."
                  : (msg + " (see Channel List)."));
    } else if (event.code() == 323 && !msg.isBlank()) {
      rendered = "[323] " + msg + " (see Channel List).";
    }
    String label = Objects.toString(event.ircv3Tags().get("label"), "").trim();
    if (!label.isBlank()) {
      LabeledResponseRoutingPort.PendingLabeledRequest pending =
          labeledResponseRoutingState.findIfFresh(sid, label, LABELED_RESPONSE_CORRELATION_WINDOW);
      if (pending != null && pending.originTarget() != null) {
        TargetRef dest = normalizeLabeledDestination(sid, status, pending.originTarget());
        LabeledResponseRoutingPort.PendingLabeledRequest transitioned =
            labeledResponseRoutingState.markOutcomeIfPending(
                sid, label, LabeledResponseRoutingPort.Outcome.SUCCESS, event.at());
        if (transitioned != null) {
          appendLabeledOutcome(
              dest,
              event.at(),
              label,
              transitioned.requestPreview(),
              transitioned.outcome(),
              "response received");
        }

        String preview = Objects.toString(pending.requestPreview(), "").trim();
        String correlated = preview.isBlank() ? rendered : (rendered + " \u2190 " + preview);
        if (!suppressStatusLine) {
          callbacks.postTo(
              dest,
              true,
              target ->
                  ui.appendStatusAt(
                      target,
                      event.at(),
                      "(server)",
                      correlated,
                      event.messageId(),
                      event.ircv3Tags()));
        }
        return;
      }
      rendered = rendered + " {label=" + label + "}";
    }
    if (!suppressStatusLine) {
      ui.appendStatusAt(
          status, event.at(), "(server)", rendered, event.messageId(), event.ircv3Tags());
    }

    if (event.code() == 465 || event.code() == 466 || event.code() == 463 || event.code() == 464) {
      String msgTrim = Objects.toString(event.message(), "").trim();
      String body =
          msgTrim.isBlank()
              ? ("Server response [" + event.code() + "]")
              : ("[" + event.code() + "] " + msgTrim);
      callbacks.notifyIrcEvent(
          IrcEventNotificationRule.EventType.YOU_KLINED,
          sid,
          null,
          null,
          "Server restriction",
          body);
    } else {
      maybeNotifyKline(callbacks, sid, event.message(), "Server restriction");
    }
  }

  private void handleStandardReply(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.StandardReply event) {
    ui.ensureTargetExists(status);
    String rendered = renderStandardReply(event);
    String label = Objects.toString(event.ircv3Tags().get("label"), "").trim();
    if (!label.isBlank()) {
      LabeledResponseRoutingPort.PendingLabeledRequest pending =
          labeledResponseRoutingState.findIfFresh(sid, label, LABELED_RESPONSE_CORRELATION_WINDOW);
      if (pending != null && pending.originTarget() != null) {
        TargetRef dest = normalizeLabeledDestination(sid, status, pending.originTarget());
        LabeledResponseRoutingPort.Outcome outcome =
            event.kind() == IrcEvent.StandardReplyKind.FAIL
                ? LabeledResponseRoutingPort.Outcome.FAILURE
                : LabeledResponseRoutingPort.Outcome.SUCCESS;
        LabeledResponseRoutingPort.PendingLabeledRequest transitioned =
            labeledResponseRoutingState.markOutcomeIfPending(sid, label, outcome, event.at());
        if (transitioned != null) {
          appendLabeledOutcome(
              dest,
              event.at(),
              label,
              transitioned.requestPreview(),
              transitioned.outcome(),
              event.description());
        }

        String preview = Objects.toString(pending.requestPreview(), "").trim();
        String correlated = preview.isBlank() ? rendered : (rendered + " \u2190 " + preview);
        callbacks.postTo(
            dest,
            true,
            target ->
                ui.appendStatusAt(
                    target,
                    event.at(),
                    "(standard-reply)",
                    correlated,
                    event.messageId(),
                    event.ircv3Tags()));
        return;
      }
      rendered = rendered + " {label=" + label + "}";
    }
    ui.appendStatusAt(
        status, event.at(), "(standard-reply)", rendered, event.messageId(), event.ircv3Tags());
    maybeNotifyKline(callbacks, sid, event.description(), "Server restriction");
  }

  private void maybeNotifyKline(
      Callbacks callbacks, String serverId, String message, String title) {
    String msg = Objects.toString(message, "").trim();
    if (msg.isEmpty() || !MediatorAlertNotificationHandler.looksLikeKlineMessage(msg)) {
      return;
    }
    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.YOU_KLINED, serverId, null, null, title, msg);
  }

  private void maybeHandlePendingPrivateMessageDeliveryError(
      String sid, IrcEvent.ServerResponseLine event) {
    if (sid == null || sid.isBlank() || event == null || event.code() != 401) {
      return;
    }

    ParsedIrcLine parsedLine = parseIrcLineForMetadata(event.rawLine());
    if (parsedLine == null || parsedLine.params() == null || parsedLine.params().size() < 2) {
      return;
    }

    String targetToken = Objects.toString(parsedLine.params().get(1), "").trim();
    if (targetToken.isEmpty()) {
      return;
    }

    final TargetRef pmTarget;
    try {
      pmTarget = new TargetRef(sid, targetToken);
    } catch (IllegalArgumentException ignored) {
      return;
    }
    if (pmTarget.isChannel() || pmTarget.isUiOnly() || pmTarget.isStatus()) {
      return;
    }

    PendingEchoMessagePort.PendingOutboundChat pending =
        pendingEchoMessageState.consumeOldestByTarget(pmTarget).orElse(null);
    if (pending == null) {
      return;
    }

    String reason = Objects.toString(parsedLine.trailing(), "").trim();
    if (reason.isEmpty()) {
      reason = Objects.toString(event.message(), "").trim();
    }
    if (reason.isEmpty()) {
      reason = "No such nick/channel";
    }

    String pendingReason = "[" + event.code() + "] " + reason;
    ui.failPendingOutgoingChat(
        pmTarget,
        pending.pendingId(),
        event.at(),
        pending.fromNick(),
        pending.text(),
        pendingReason);

    ui.ensureTargetExists(pmTarget);
    ui.appendErrorAt(
        pmTarget,
        event.at(),
        "(send)",
        "Cannot deliver to " + pmTarget.target() + " [" + event.code() + "]: " + reason);
  }

  private void updateServerMetadataFromServerResponseLine(
      String serverId, IrcEvent.ServerResponseLine event) {
    if (event == null) {
      return;
    }
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      return;
    }

    ParsedIrcLine parsedLine = parseIrcLineForMetadata(event.rawLine());
    if (parsedLine == null) {
      return;
    }

    String cmd = Objects.toString(parsedLine.command(), "").trim();
    if (cmd.isEmpty()) {
      return;
    }

    if ("004".equals(cmd) || event.code() == 4) {
      List<String> params = parsedLine.params();
      String serverName = params.size() >= 2 ? params.get(1) : "";
      String version = params.size() >= 3 ? params.get(2) : "";
      String userModes = params.size() >= 4 ? params.get(3) : "";
      String channelModes = params.size() >= 5 ? params.get(4) : "";
      ui.setServerVersionDetails(sid, serverName, version, userModes, channelModes);
      return;
    }

    if ("351".equals(cmd) || event.code() == 351) {
      List<String> params = parsedLine.params();
      String version = params.size() >= 2 ? params.get(1) : "";
      String serverName = params.size() >= 3 ? params.get(2) : "";
      ui.setServerVersionDetails(sid, serverName, version, "", "");
      return;
    }

    if ("005".equals(cmd) || event.code() == 5) {
      List<String> params = parsedLine.params();
      int start = params.size() >= 1 ? 1 : 0;
      for (int i = start; i < params.size(); i++) {
        String tok = Objects.toString(params.get(i), "").trim();
        if (tok.isEmpty()) {
          continue;
        }

        if (tok.startsWith("-") && tok.length() > 1) {
          ui.setServerIsupportToken(sid, tok.substring(1), null);
          serverIsupportState.applyIsupportToken(sid, tok.substring(1), null);
          continue;
        }

        int eq = tok.indexOf('=');
        if (eq >= 0) {
          String key = tok.substring(0, eq).trim();
          String value = tok.substring(eq + 1).trim();
          if (!key.isEmpty()) {
            ui.setServerIsupportToken(sid, key, value);
            serverIsupportState.applyIsupportToken(sid, key, value);
          }
          continue;
        }

        ui.setServerIsupportToken(sid, tok, "");
        serverIsupportState.applyIsupportToken(sid, tok, "");
      }
    }
  }

  private record ParsedIrcLine(
      String prefix, String command, List<String> params, String trailing) {}

  private static ParsedIrcLine parseIrcLineForMetadata(String rawLine) {
    String s = Objects.toString(rawLine, "").trim();
    if (s.isEmpty()) {
      return null;
    }

    if (s.startsWith("@")) {
      int sp = s.indexOf(' ');
      if (sp <= 0 || sp >= s.length() - 1) {
        return null;
      }
      s = s.substring(sp + 1).trim();
    }

    String prefix = "";
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp <= 1 || sp >= s.length() - 1) {
        return null;
      }
      prefix = s.substring(1, sp).trim();
      s = s.substring(sp + 1).trim();
    }

    String trailing = "";
    int trailStart = s.indexOf(" :");
    if (trailStart >= 0) {
      trailing = s.substring(trailStart + 2).trim();
      s = s.substring(0, trailStart).trim();
    }

    if (s.isEmpty()) {
      return null;
    }
    String[] toks = s.split("\\s+");
    if (toks.length == 0) {
      return null;
    }

    String command = toks[0].trim();
    if (command.isEmpty()) {
      return null;
    }

    List<String> params = new java.util.ArrayList<>();
    for (int i = 1; i < toks.length; i++) {
      String tok = Objects.toString(toks[i], "").trim();
      if (!tok.isEmpty()) {
        params.add(tok);
      }
    }

    return new ParsedIrcLine(prefix, command, List.copyOf(params), trailing);
  }

  private static String renderStandardReply(IrcEvent.StandardReply event) {
    if (event == null) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    out.append(event.kind().name());
    String cmd = Objects.toString(event.command(), "").trim();
    if (!cmd.isBlank()) {
      out.append(' ').append(cmd);
    }
    String code = Objects.toString(event.code(), "").trim();
    if (!code.isBlank()) {
      out.append(' ').append(code);
    }
    String context = Objects.toString(event.context(), "").trim();
    if (!context.isBlank()) {
      out.append(" [").append(context).append(']');
    }
    String desc = Objects.toString(event.description(), "").trim();
    if (!desc.isBlank()) {
      out.append(": ").append(desc);
    }
    return out.toString();
  }

  private void appendLabeledOutcome(
      TargetRef dest,
      Instant at,
      String label,
      String requestPreview,
      LabeledResponseRoutingPort.Outcome outcome,
      String detail) {
    String lbl = Objects.toString(label, "").trim();
    if (lbl.isEmpty()) {
      return;
    }
    String preview = Objects.toString(requestPreview, "").trim();
    String d = Objects.toString(detail, "").trim();
    String state =
        switch (outcome) {
          case FAILURE -> "failed";
          case TIMEOUT -> "timed out";
          case SUCCESS -> "completed";
          case PENDING -> "pending";
        };

    StringBuilder text =
        new StringBuilder("Request ").append(state).append(" {label=").append(lbl).append('}');
    if (!preview.isBlank()) {
      text.append(": ").append(preview);
    }
    if (!d.isBlank()) {
      text.append(" (").append(d).append(')');
    }
    String from =
        switch (outcome) {
          case FAILURE -> "(label-fail)";
          case TIMEOUT -> "(label-timeout)";
          case SUCCESS -> "(label-ok)";
          case PENDING -> "(label)";
        };
    ui.appendStatusAt(dest, at == null ? Instant.now() : at, from, text.toString());
  }

  private TargetRef normalizeLabeledDestination(String sid, TargetRef status, TargetRef origin) {
    if (origin == null) {
      return status;
    }
    TargetRef dest = origin;
    if (!Objects.equals(dest.serverId(), sid)) {
      dest = new TargetRef(sid, dest.target());
    }
    if (dest.isUiOnly()) {
      return status;
    }
    return dest;
  }
}
