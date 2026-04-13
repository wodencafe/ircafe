package cafe.woden.ircclient.ui.util;

import cafe.woden.ircclient.logging.viewer.ChatRedactionAuditRecord;
import cafe.woden.ircclient.logging.viewer.ChatRedactionAuditService;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.util.VirtualThreads;
import java.awt.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class ChatRedactedMessageRevealSupport {

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  private ChatRedactedMessageRevealSupport() {}

  public static void reveal(
      Component owner,
      TargetRef target,
      String messageId,
      ChatTranscriptStore transcripts,
      ChatRedactionAuditService auditService) {
    if (target == null || transcripts == null) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;

    ChatTranscriptStore.RedactedMessageContent live =
        transcripts.redactedOriginalById(target, msgId);
    if (live != null) {
      ChatLineInspectorDialog.showReadOnlyTextDialog(
          owner, "Redacted Message", formatLiveRevealText(target, live));
      return;
    }

    if (auditService == null || !auditService.enabled()) {
      showUnavailable(owner);
      return;
    }

    VirtualThreads.start(
        "ircafe-reveal-redacted-message",
        () -> {
          Optional<ChatRedactionAuditRecord> audit = auditService.findLatest(target, msgId);
          SwingUtilities.invokeLater(
              () -> {
                if (audit.isPresent()) {
                  ChatLineInspectorDialog.showReadOnlyTextDialog(
                      owner, "Redacted Message", formatAuditRevealText(audit.get()));
                } else {
                  showUnavailable(owner);
                }
              });
        });
  }

  private static String formatLiveRevealText(
      TargetRef target, ChatTranscriptStore.RedactedMessageContent content) {
    StringBuilder sb = new StringBuilder();
    sb.append("Source: live transcript cache\n");
    appendCommonHeader(
        sb,
        target.serverId(),
        target.target(),
        content.messageId(),
        content.originalKind() == null ? "" : content.originalKind().name(),
        content.originalFromNick(),
        content.originalEpochMs(),
        content.redactedBy(),
        content.redactedAtEpochMs());
    sb.append('\n').append(Objects.toString(content.originalText(), ""));
    return sb.toString();
  }

  private static String formatAuditRevealText(ChatRedactionAuditRecord record) {
    StringBuilder sb = new StringBuilder();
    sb.append("Source: redaction audit log\n");
    appendCommonHeader(
        sb,
        record.serverId(),
        record.target(),
        record.messageId(),
        record.originalKind() == null ? "" : record.originalKind().name(),
        record.originalFromNick(),
        record.originalEpochMs(),
        record.redactedBy(),
        record.redactedAtEpochMs());
    sb.append('\n').append(Objects.toString(record.originalText(), ""));
    return sb.toString();
  }

  private static void appendCommonHeader(
      StringBuilder sb,
      String serverId,
      String target,
      String messageId,
      String kind,
      String fromNick,
      Long originalEpochMs,
      String redactedBy,
      Long redactedAtEpochMs) {
    if (!Objects.toString(serverId, "").isBlank()) {
      sb.append("Server: ").append(serverId).append('\n');
    }
    if (!Objects.toString(target, "").isBlank()) {
      sb.append("Target: ").append(target).append('\n');
    }
    if (!Objects.toString(messageId, "").isBlank()) {
      sb.append("Message ID: ").append(messageId).append('\n');
    }
    if (!Objects.toString(kind, "").isBlank()) {
      sb.append("Kind: ").append(kind).append('\n');
    }
    if (!Objects.toString(fromNick, "").isBlank()) {
      sb.append("Original from: ").append(fromNick).append('\n');
    }
    if (originalEpochMs != null && originalEpochMs > 0) {
      sb.append("Original time: ")
          .append(TS_FMT.format(Instant.ofEpochMilli(originalEpochMs)))
          .append('\n');
    }
    if (!Objects.toString(redactedBy, "").isBlank()) {
      sb.append("Redacted by: ").append(redactedBy).append('\n');
    }
    if (redactedAtEpochMs != null && redactedAtEpochMs > 0) {
      sb.append("Redacted at: ")
          .append(TS_FMT.format(Instant.ofEpochMilli(redactedAtEpochMs)))
          .append('\n');
    }
  }

  private static void showUnavailable(Component owner) {
    JOptionPane.showMessageDialog(
        owner,
        "Original redacted content is not available for this message.",
        "Redacted Message",
        JOptionPane.INFORMATION_MESSAGE);
  }
}
