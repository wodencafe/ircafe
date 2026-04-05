package cafe.woden.ircclient.app.outbound.mutation;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.app.outbound.help.spi.OutboundHelpContributor;
import cafe.woden.ircclient.app.outbound.support.OutboundCommandAvailabilitySupport;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles reply/reaction/edit/redaction outbound command flows. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class OutboundMessageMutationCommandService implements OutboundHelpContributor {

  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  @NonNull private final OutboundCommandAvailabilitySupport outboundCommandAvailabilitySupport;
  @NonNull private final UiPort ui;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final OutboundMessageMutationSendSupport outboundMessageMutationSendSupport;

  @Override
  public void appendGeneralHelp(TargetRef out) {
    ui.appendStatus(out, "(help)", "/reply <msgid> <message> (requires message-tags)");
    ui.appendStatus(out, "(help)", "/react <msgid> <reaction-token> (requires message-tags)");
    ui.appendStatus(out, "(help)", "/unreact <msgid> <reaction-token> (requires message-tags)");
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

  public void handleReplyMessage(CompositeDisposable disposables, String messageId, String body) {
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

    if (!backendCapabilityPolicy.supportsMessageTags(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(reply)",
          featureUnavailableMessage(
              at.serverId(), "message-tags are not negotiated on this server."));
      return;
    }

    outboundMessageMutationSendSupport.sendReply(disposables, at, msgId, text);
  }

  public void handleReactMessage(
      CompositeDisposable disposables, String messageId, String reaction) {
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

    if (!backendCapabilityPolicy.supportsMessageTags(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(react)",
          featureUnavailableMessage(
              at.serverId(), "message-tags are not negotiated on this server."));
      return;
    }

    outboundMessageMutationSendSupport.sendReaction(disposables, at, msgId, token);
  }

  public void handleUnreactMessage(
      CompositeDisposable disposables, String messageId, String reaction) {
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

    if (!backendCapabilityPolicy.supportsMessageTags(at.serverId())) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(unreact)",
          featureUnavailableMessage(
              at.serverId(), "message-tags are not negotiated on this server."));
      return;
    }

    outboundMessageMutationSendSupport.sendUnreaction(disposables, at, msgId, token);
  }

  public void handleEditMessage(CompositeDisposable disposables, String messageId, String body) {
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

    outboundMessageMutationSendSupport.sendEdit(disposables, at, msgId, text);
  }

  public void handleRedactMessage(
      CompositeDisposable disposables, String messageId, String reason) {
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

    outboundMessageMutationSendSupport.sendRedaction(disposables, at, msgId, redactReason);
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
                : outboundCommandAvailabilitySupport.helpAvailabilitySuffix(
                    serverId, false, "requires negotiated draft/message-edit or message-edit")));
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
                : outboundCommandAvailabilitySupport.helpAvailabilitySuffix(
                    serverId,
                    false,
                    "requires negotiated draft/message-redaction or message-redaction")));
  }

  private String featureUnavailableMessage(String serverId, String fallback) {
    return outboundCommandAvailabilitySupport.featureUnavailableMessage(serverId, fallback);
  }

  private boolean isOwnMessageInBuffer(TargetRef target, String messageId) {
    if (target == null) return false;
    String msgId = normalizeIrcv3Token(messageId);
    if (msgId.isEmpty()) return false;
    return ui.isOwnMessage(target, msgId);
  }

  static String normalizeIrcv3Token(String raw) {
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return "";
    if (token.indexOf(' ') >= 0 || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) return "";
    return token;
  }

  static String normalizeReactionToken(String raw) {
    return normalizeIrcv3Token(raw);
  }
}
