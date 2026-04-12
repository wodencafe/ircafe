package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import java.util.Locale;
import java.util.Objects;
import javax.swing.text.AttributeSet;

/**
 * Pure attribute-inspection helpers shared across transcript document operations.
 *
 * <p>All methods are stateless; they operate only on their parameters.
 */
final class ChatTranscriptAttrSupport {

  private ChatTranscriptAttrSupport() {}

  /**
   * Returns {@code true} if the element attributes represent a conversational line (chat, action,
   * notice, or spoiler) — i.e. lines that are eligible for read-marker tracking.
   */
  static boolean isConversationLine(AttributeSet attrs) {
    if (attrs == null) return false;
    Object rawKind = attrs.getAttribute(ChatStyles.ATTR_META_KIND);
    if (rawKind == null) return false;
    String kind = String.valueOf(rawKind).trim().toUpperCase(Locale.ROOT);
    return "CHAT".equals(kind)
        || "ACTION".equals(kind)
        || "NOTICE".equals(kind)
        || "SPOILER".equals(kind);
  }

  /**
   * Reads the {@link ChatStyles#ATTR_META_EPOCH_MS} attribute from a character element, accepting
   * both {@link Number} instances and parseable string forms.
   *
   * @return the epoch-millisecond timestamp, or {@code null} if absent or unparseable
   */
  static Long lineEpochMs(AttributeSet attrs) {
    if (attrs == null) return null;
    Object raw = attrs.getAttribute(ChatStyles.ATTR_META_EPOCH_MS);
    if (raw instanceof Number n) {
      return n.longValue();
    }
    if (raw == null) return null;
    try {
      String s = String.valueOf(raw).trim();
      if (s.isEmpty()) return null;
      return Long.parseLong(s);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Reads the {@link ChatStyles#ATTR_META_KIND} attribute, returning {@link LogKind#CHAT} as a safe
   * default when the attribute is absent or unrecognised.
   */
  static LogKind logKindFromAttrs(AttributeSet attrs) {
    if (attrs == null) return LogKind.CHAT;
    String raw = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_KIND), "").trim();
    if (raw.isEmpty()) return LogKind.CHAT;
    try {
      return LogKind.valueOf(raw.toUpperCase(Locale.ROOT));
    } catch (Exception ignored) {
      return LogKind.CHAT;
    }
  }

  /**
   * Reads the {@link ChatStyles#ATTR_META_DIRECTION} attribute, returning {@link LogDirection#IN}
   * as a safe default when the attribute is absent or unrecognised.
   */
  static LogDirection logDirectionFromAttrs(AttributeSet attrs) {
    if (attrs == null) return LogDirection.IN;
    String raw = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_DIRECTION), "").trim();
    if (raw.isEmpty()) return LogDirection.IN;
    try {
      return LogDirection.valueOf(raw.toUpperCase(Locale.ROOT));
    } catch (Exception ignored) {
      return LogDirection.IN;
    }
  }

  /**
   * Parses a raw attribute value into a {@link FilterAction}, returning {@code null} when the value
   * is absent, blank, or does not match any known action name.
   */
  static FilterAction filterActionFromAttr(Object raw) {
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return null;
    try {
      return FilterAction.valueOf(token.toUpperCase(Locale.ROOT));
    } catch (Exception ignored) {
      return null;
    }
  }
}
