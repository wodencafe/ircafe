package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.irc.IrcEchoCapabilityPort;
import cafe.woden.ircclient.irc.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles reply/reaction/edit/redaction outbound command flows. */
@Component
final class OutboundMessageMutationCommandService implements OutboundHelpContributor {

  private final IrcTargetMembershipPort targetMembership;
  private final IrcEchoCapabilityPort echoCapabilityPort;
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  private final UiPort ui;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final PendingEchoMessagePort pendingEchoMessageState;
  private final OutboundRawLineCorrelationService rawLineCorrelationService;

  OutboundMessageMutationCommandService(
      @Qualifier("ircTargetMembershipPort") IrcTargetMembershipPort targetMembership,
      IrcEchoCapabilityPort echoCapabilityPort,
      OutboundBackendCapabilityPolicy backendCapabilityPolicy,
      UiPort ui,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      PendingEchoMessagePort pendingEchoMessageState,
      OutboundRawLineCorrelationService rawLineCorrelationService) {
    this.targetMembership = Objects.requireNonNull(targetMembership, "targetMembership");
    this.echoCapabilityPort = Objects.requireNonNull(echoCapabilityPort, "echoCapabilityPort");
    this.backendCapabilityPolicy =
        Objects.requireNonNull(backendCapabilityPolicy, "backendCapabilityPolicy");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.connectionCoordinator =
        Objects.requireNonNull(connectionCoordinator, "connectionCoordinator");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
    this.pendingEchoMessageState =
        Objects.requireNonNull(pendingEchoMessageState, "pendingEchoMessageState");
    this.rawLineCorrelationService =
        Objects.requireNonNull(rawLineCorrelationService, "rawLineCorrelationService");
  }

  @Override
  public void appendGeneralHelp(TargetRef out) {
    ui.appendStatus(out, "(help)", "/reply <msgid> <message> (requires draft/reply)");
    ui.appendStatus(
        out, "(help)", "/react <msgid> <reaction-token> (requires draft/react + draft/reply)");
    ui.appendStatus(
        out, "(help)", "/unreact <msgid> <reaction-token> (requires draft/unreact + draft/reply)");
    appendEditHelp(out);
    appendRedactHelp(out);
  }

  @Override
  public Map<String, Consumer<TargetRef>> topicHelpHandlers() {
    return Map.of(
        "edit",
        this::appendEditHelp,
        "redact",
        this::appendRedactHelp,
        "delete",
        this::appendRedactHelp);
  }

  void handleReplyMessage(CompositeDisposable disposables, String messageId, String body) {
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

    if (!backendCapabilityPolicy.supportsDraftReply(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(reply)",
          featureUnavailableMessage(
              at.serverId(), "draft/reply is not negotiated on this server."));
      return;
    }

    sendReplyMessage(disposables, at, msgId, text);
  }

  void handleReactMessage(CompositeDisposable disposables, String messageId, String reaction) {
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

    if (!backendCapabilityPolicy.supportsDraftReply(at.serverId())
        || !backendCapabilityPolicy.supportsDraftReact(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(react)",
          featureUnavailableMessage(
              at.serverId(), "draft/react is not negotiated on this server."));
      return;
    }

    sendReactionTag(disposables, at, msgId, token);
  }

  void handleUnreactMessage(CompositeDisposable disposables, String messageId, String reaction) {
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

    if (!backendCapabilityPolicy.supportsDraftReply(at.serverId())
        || !backendCapabilityPolicy.supportsDraftUnreact(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(unreact)",
          featureUnavailableMessage(
              at.serverId(), "draft/unreact is not negotiated on this server."));
      return;
    }

    sendUnreactionTag(disposables, at, msgId, token);
  }

  void handleEditMessage(CompositeDisposable disposables, String messageId, String body) {
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

    if (!backendCapabilityPolicy.supportsMessageEdit(at.serverId())) {
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

  void handleRedactMessage(CompositeDisposable disposables, String messageId, String reason) {
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

    if (!backendCapabilityPolicy.supportsMessageRedaction(at.serverId())) {
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

    String me = targetMembership.currentNick(target.serverId()).orElse("me");
    boolean useLocalEcho = shouldUseLocalEcho(target.serverId());
    final PendingEchoMessagePort.PendingOutboundChat pendingEntry;
    if (useLocalEcho) {
      pendingEntry = null;
    } else {
      pendingEntry = pendingEchoMessageState.register(target, me, m, Instant.now());
      ui.appendPendingOutgoingChat(
          target, pendingEntry.pendingId(), pendingEntry.createdAt(), me, m);
    }

    String rawLine =
        "@+draft/reply=" + escapeIrcv3TagValue(msgId) + " PRIVMSG " + target.target() + " :" + m;
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);

    disposables.add(
        targetMembership
            .sendRaw(target.serverId(), prepared.line())
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
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);

    String me = targetMembership.currentNick(target.serverId()).orElse("me");
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.applyMessageReaction(target, now, me, msgId, react);
    }

    disposables.add(
        targetMembership
            .sendRaw(target.serverId(), prepared.line())
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
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);

    String me = targetMembership.currentNick(target.serverId()).orElse("me");
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.removeMessageReaction(target, now, me, msgId, react);
    }

    disposables.add(
        targetMembership
            .sendRaw(target.serverId(), prepared.line())
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
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);

    String me = targetMembership.currentNick(target.serverId()).orElse("me");
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.applyMessageEdit(target, now, me, msgId, text, "", java.util.Map.of("draft/edit", msgId));
    }

    disposables.add(
        targetMembership
            .sendRaw(target.serverId(), prepared.line())
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
    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);

    String me = targetMembership.currentNick(target.serverId()).orElse("me");
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.applyMessageRedaction(target, now, me, msgId, "", java.util.Map.of("draft/delete", msgId));
    }

    disposables.add(
        targetMembership
            .sendRaw(target.serverId(), prepared.line())
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(redact-error)",
                        String.valueOf(err))));
  }

  private static boolean containsCrlf(String input) {
    return input != null && (input.indexOf('\n') >= 0 || input.indexOf('\r') >= 0);
  }

  private void appendEditHelp(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    String serverId = target.serverId();
    boolean available = backendCapabilityPolicy.supportsMessageEdit(serverId);
    ui.appendStatus(
        target,
        "(help)",
        "/edit <msgid> <message>"
            + (available
                ? ""
                : unavailableSuffix(
                    unavailableReasonForHelp(
                        serverId, "requires negotiated draft/message-edit or message-edit"))));
  }

  private void appendRedactHelp(TargetRef out) {
    TargetRef target = out != null ? out : targetCoordinator.safeStatusTarget();
    String serverId = target.serverId();
    boolean available = backendCapabilityPolicy.supportsMessageRedaction(serverId);
    ui.appendStatus(
        target,
        "(help)",
        "/redact <msgid> [reason] (alias: /delete)"
            + (available
                ? ""
                : unavailableSuffix(
                    unavailableReasonForHelp(
                        serverId,
                        "requires negotiated draft/message-redaction or message-redaction"))));
  }

  private boolean shouldUseLocalEcho(String serverId) {
    return !echoCapabilityPort.isEchoMessageAvailable(serverId);
  }

  private String featureUnavailableMessage(String serverId, String fallback) {
    return backendCapabilityPolicy.featureUnavailableMessage(serverId, fallback);
  }

  private String unavailableReasonForHelp(String serverId, String fallback) {
    return backendCapabilityPolicy.unavailableReasonForHelp(serverId, fallback);
  }

  private static String unavailableSuffix(String reason) {
    if (reason == null || reason.isBlank()) return "";
    return " (unavailable: " + reason + ")";
  }

  private boolean isOwnMessageInBuffer(TargetRef target, String messageId) {
    if (target == null) return false;
    String msgId = normalizeIrcv3Token(messageId);
    if (msgId.isEmpty()) return false;
    return ui.isOwnMessage(target, msgId);
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
}
