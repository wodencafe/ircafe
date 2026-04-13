package cafe.woden.ircclient.ui.chat;

import java.awt.Color;
import java.util.Objects;
import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/** Shared helpers for pending outbound transcript rows and delivery indicators. */
final class ChatTranscriptPendingOutgoingSupport {

  private static final String PENDING_STATE = "pending";

  private ChatTranscriptPendingOutgoingSupport() {}

  static void markPending(MutableAttributeSet attrs, String pendingId) {
    if (attrs == null) return;
    String pid = Objects.toString(pendingId, "").trim();
    if (pid.isEmpty()) return;
    attrs.addAttribute(ChatStyles.ATTR_META_PENDING_ID, pid);
    attrs.addAttribute(ChatStyles.ATTR_META_PENDING_STATE, PENDING_STATE);
  }

  static SimpleAttributeSet pendingTailAttrs(AttributeSet base, String pendingId) {
    SimpleAttributeSet tail = new SimpleAttributeSet(base);
    markPending(tail, pendingId);
    return tail;
  }

  static Color pendingSpinnerColor(AttributeSet messageAttrs) {
    if (messageAttrs == null) return null;
    try {
      return StyleConstants.getForeground(messageAttrs);
    } catch (Exception ignored) {
      return null;
    }
  }

  static String renderPendingFailure(String reason) {
    String normalized = Objects.toString(reason, "").trim();
    if (normalized.isEmpty()) return "[failed]";
    if (normalized.length() > 120) {
      normalized = normalized.substring(0, 117) + "...";
    }
    return "[failed: " + normalized + "]";
  }
}
