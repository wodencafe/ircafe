package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Handles outbound /query, /msg, /notice, /me and shared message send flow. */
@Component
final class OutboundMessagingCommandService {

  private enum MultilineSendDecision {
    SEND_AS_MULTILINE,
    SEND_AS_SPLIT_LINES,
    CANCEL
  }

  private final IrcClientService irc;
  private final IrcNegotiatedFeaturePort negotiatedFeaturePort;
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final PendingEchoMessagePort pendingEchoMessageState;

  OutboundMessagingCommandService(
      IrcClientService irc,
      IrcNegotiatedFeaturePort negotiatedFeaturePort,
      OutboundBackendCapabilityPolicy backendCapabilityPolicy,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      PendingEchoMessagePort pendingEchoMessageState) {
    this.irc = Objects.requireNonNull(irc, "irc");
    this.negotiatedFeaturePort =
        Objects.requireNonNull(negotiatedFeaturePort, "negotiatedFeaturePort");
    this.backendCapabilityPolicy =
        Objects.requireNonNull(backendCapabilityPolicy, "backendCapabilityPolicy");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.pendingEchoMessageState =
        Objects.requireNonNull(pendingEchoMessageState, "pendingEchoMessageState");
  }

  void handleQuery(String nick) {
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

  void handleMsg(CompositeDisposable disposables, String nick, String body) {
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

  void handleNotice(CompositeDisposable disposables, String target, String body) {
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

  void handleMe(CompositeDisposable disposables, String action) {
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

  void sendMessage(CompositeDisposable disposables, TargetRef target, String message) {
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
    String backendUnavailableReason =
        backendCapabilityPolicy.featureUnavailableMessage(serverId, "");
    if (!backendUnavailableReason.isBlank()) {
      return backendUnavailableReason;
    }

    if (!backendCapabilityPolicy.supportsMultiline(serverId)) {
      return "IRCv3 multiline is not negotiated on this server.";
    }

    int maxLines = negotiatedFeaturePort.negotiatedMultilineMaxLines(serverId);
    if (maxLines > 0 && lineCount > maxLines) {
      return "Message has "
          + lineCount
          + " lines; negotiated multiline max-lines is "
          + maxLines
          + ".";
    }

    long maxBytes = negotiatedFeaturePort.negotiatedMultilineMaxBytes(serverId);
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

  private boolean shouldUseLocalEcho(String serverId) {
    return !irc.isEchoMessageAvailable(serverId);
  }
}
