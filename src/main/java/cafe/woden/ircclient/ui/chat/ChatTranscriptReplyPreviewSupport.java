package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.model.LogKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class ChatTranscriptReplyPreviewSupport {

  private ChatTranscriptReplyPreviewSupport() {}

  static LinkedHashMap<String, String> createBoundedReplyPreviewCache(int maxEntries) {
    return new LinkedHashMap<>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
        return size() > maxEntries;
      }
    };
  }

  static String previewForMessageId(Map<String, String> previews, String messageId) {
    if (previews == null) return "";
    String msgId = ChatTranscriptMessageMetadataSupport.normalizeMessageId(messageId);
    if (msgId.isEmpty()) return "";
    return Objects.toString(previews.get(msgId), "").trim();
  }

  static String formatReplyPreviewSnippet(LogKind kind, String from, String text, int maxChars) {
    String body = normalizeReplyPreviewText(text, maxChars);
    if (body.isEmpty()) return "";
    String nick = Objects.toString(from, "").trim();
    return switch (kind) {
      case ACTION -> nick.isEmpty() ? ("* " + body) : ("* " + nick + " " + body);
      case NOTICE -> nick.isEmpty() ? ("[notice] " + body) : ("[notice] " + nick + ": " + body);
      default -> nick.isEmpty() ? body : (nick + ": " + body);
    };
  }

  static String normalizeReplyPreviewText(String rawText, int maxChars) {
    String raw = Objects.toString(rawText, "");
    if (raw.isEmpty()) return "";

    StringBuilder out = new StringBuilder(Math.min(maxChars, raw.length()));
    boolean pendingSpace = false;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (Character.isWhitespace(c)) {
        pendingSpace = out.length() > 0;
        continue;
      }
      if (c < 0x20 && c != '\t') continue;
      if (pendingSpace && out.length() > 0) {
        out.append(' ');
        pendingSpace = false;
      }
      out.append(c);
      if (out.length() >= maxChars) break;
    }

    String normalized = out.toString().trim();
    if (normalized.length() >= maxChars && raw.length() > normalized.length()) {
      int max = Math.max(1, maxChars - 3);
      normalized = normalized.substring(0, Math.min(max, normalized.length())).trim() + "...";
    }
    return normalized;
  }
}
