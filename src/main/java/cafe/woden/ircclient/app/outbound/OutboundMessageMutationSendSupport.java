package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.MessageMutationOutboundCommandsRouter;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.port.IrcEchoCapabilityPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.time.Instant;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Shared send/local-echo workflow support for outbound message-mutation commands. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class OutboundMessageMutationSendSupport {

  @NonNull
  @Qualifier("ircTargetMembershipPort")
  private final IrcTargetMembershipPort targetMembership;

  @NonNull private final IrcEchoCapabilityPort echoCapabilityPort;
  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;

  @NonNull
  private final MessageMutationOutboundCommandsRouter messageMutationOutboundCommandsRouter;

  @NonNull private final UiPort ui;
  @NonNull private final ConnectionCoordinator connectionCoordinator;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final PendingEchoMessagePort pendingEchoMessageState;
  @NonNull private final OutboundRawLineCorrelationService rawLineCorrelationService;

  void sendReply(
      CompositeDisposable disposables, TargetRef target, String replyToMessageId, String message) {
    if (target == null) return;
    String msgId = OutboundMessageMutationCommandService.normalizeIrcv3Token(replyToMessageId);
    String text = message == null ? "" : message.trim();
    if (msgId.isEmpty() || text.isEmpty() || !ensureConnected(target)) return;

    String me = currentNick(target.serverId());
    boolean useLocalEcho = shouldUseLocalEcho(target.serverId());
    PendingEchoMessagePort.PendingOutboundChat pendingEntry = null;
    if (!useLocalEcho) {
      pendingEntry = pendingEchoMessageState.register(target, me, text, Instant.now());
      ui.appendPendingOutgoingChat(
          target, pendingEntry.pendingId(), pendingEntry.createdAt(), me, text);
    }

    MessageMutationOutboundCommands mutationCommands = mutationCommandsForServer(target.serverId());
    String rawLine = mutationCommands.buildReplyRawLine(target, msgId, text);
    if (rawLine.isBlank()) return;

    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);
    PendingEchoMessagePort.PendingOutboundChat pending = pendingEntry;

    disposables.add(
        targetMembership
            .sendRaw(target.serverId(), prepared.line())
            .subscribe(
                () -> {},
                err -> {
                  if (pending != null) {
                    pendingEchoMessageState.removeById(pending.pendingId());
                    ui.failPendingOutgoingChat(
                        target,
                        pending.pendingId(),
                        Instant.now(),
                        pending.fromNick(),
                        pending.text(),
                        String.valueOf(err));
                  }
                  ui.appendError(
                      targetCoordinator.safeStatusTarget(), "(reply-error)", String.valueOf(err));
                }));

    if (useLocalEcho) {
      ui.appendChat(target, "(" + me + ")", text, true);
    }
  }

  void sendReaction(
      CompositeDisposable disposables, TargetRef target, String replyToMessageId, String reaction) {
    if (target == null) return;
    String msgId = OutboundMessageMutationCommandService.normalizeIrcv3Token(replyToMessageId);
    String react = OutboundMessageMutationCommandService.normalizeReactionToken(reaction);
    if (msgId.isEmpty() || react.isEmpty() || !ensureConnected(target)) return;

    MessageMutationOutboundCommands mutationCommands = mutationCommandsForServer(target.serverId());
    String rawLine = mutationCommands.buildReactRawLine(target, msgId, react);
    if (rawLine.isBlank()) return;

    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);

    String me = currentNick(target.serverId());
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.applyMessageReaction(target, now, me, msgId, react);
    }

    sendRaw(disposables, target, prepared.line(), "(react-error)");
  }

  void sendUnreaction(
      CompositeDisposable disposables, TargetRef target, String replyToMessageId, String reaction) {
    if (target == null) return;
    String msgId = OutboundMessageMutationCommandService.normalizeIrcv3Token(replyToMessageId);
    String react = OutboundMessageMutationCommandService.normalizeReactionToken(reaction);
    if (msgId.isEmpty() || react.isEmpty() || !ensureConnected(target)) return;

    MessageMutationOutboundCommands mutationCommands = mutationCommandsForServer(target.serverId());
    String rawLine = mutationCommands.buildUnreactRawLine(target, msgId, react);
    if (rawLine.isBlank()) return;

    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);

    String me = currentNick(target.serverId());
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.removeMessageReaction(target, now, me, msgId, react);
    }

    sendRaw(disposables, target, prepared.line(), "(unreact-error)");
  }

  void sendEdit(
      CompositeDisposable disposables,
      TargetRef target,
      String targetMessageId,
      String editedText) {
    if (target == null) return;
    String msgId = OutboundMessageMutationCommandService.normalizeIrcv3Token(targetMessageId);
    String text = editedText == null ? "" : editedText.trim();
    if (msgId.isEmpty() || text.isEmpty() || !ensureConnected(target)) return;

    MessageMutationOutboundCommands mutationCommands = mutationCommandsForServer(target.serverId());
    String rawLine = mutationCommands.buildEditRawLine(target, msgId, text);
    if (rawLine.isBlank()) return;

    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);

    String me = currentNick(target.serverId());
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.applyMessageEdit(
          target, now, me, msgId, text, "", mutationCommands.localEchoEditTags(msgId));
    }

    sendRaw(disposables, target, prepared.line(), "(edit-error)");
  }

  void sendRedaction(
      CompositeDisposable disposables, TargetRef target, String targetMessageId, String reason) {
    if (target == null) return;
    String msgId = OutboundMessageMutationCommandService.normalizeIrcv3Token(targetMessageId);
    if (msgId.isEmpty() || !ensureConnected(target)) return;

    MessageMutationOutboundCommands mutationCommands = mutationCommandsForServer(target.serverId());
    String rawLine = mutationCommands.buildRedactRawLine(target, msgId, reason);
    if (rawLine.isBlank()) return;

    OutboundRawLineCorrelationService.PreparedRawLine prepared =
        rawLineCorrelationService.prepare(target, rawLine);

    String me = currentNick(target.serverId());
    Instant now = Instant.now();
    if (shouldUseLocalEcho(target.serverId())) {
      ui.applyMessageRedaction(
          target, now, me, msgId, "", mutationCommands.localEchoRedactionTags(msgId));
    }

    sendRaw(disposables, target, prepared.line(), "(redact-error)");
  }

  private void sendRaw(
      CompositeDisposable disposables, TargetRef target, String rawLine, String errorTag) {
    disposables.add(
        targetMembership
            .sendRaw(target.serverId(), rawLine)
            .subscribe(
                () -> {},
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), errorTag, String.valueOf(err))));
  }

  private boolean ensureConnected(TargetRef target) {
    if (connectionCoordinator.isConnected(target.serverId())) return true;
    TargetRef status = new TargetRef(target.serverId(), "status");
    ui.appendStatus(status, "(conn)", "Not connected");
    if (!target.isStatus()) {
      ui.appendStatus(target, "(conn)", "Not connected");
    }
    return false;
  }

  private String currentNick(String serverId) {
    return targetMembership.currentNick(serverId).orElse("me");
  }

  private boolean shouldUseLocalEcho(String serverId) {
    return !echoCapabilityPort.isEchoMessageAvailable(serverId);
  }

  private MessageMutationOutboundCommands mutationCommandsForServer(String serverId) {
    return messageMutationOutboundCommandsRouter.commandsFor(
        backendCapabilityPolicy.backendForServer(serverId));
  }
}
