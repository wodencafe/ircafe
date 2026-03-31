package cafe.woden.ircclient.ui.chat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class ChatTranscriptMessageMetadataSupport {

  private ChatTranscriptMessageMetadataSupport() {}

  static String normalizeMessageId(String raw) {
    return (raw == null) ? "" : raw.trim();
  }

  static String normalizePendingId(String raw) {
    return (raw == null) ? "" : raw.trim();
  }

  static Map<String, String> normalizeIrcv3Tags(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : raw.entrySet()) {
      String key = normalizeIrcv3TagKey(entry.getKey());
      if (key.isEmpty()) continue;
      String value = (entry.getValue() == null) ? "" : entry.getValue();
      out.put(key, value);
    }
    if (out.isEmpty()) return Map.of();
    return java.util.Collections.unmodifiableMap(out);
  }

  static String normalizeIrcv3TagKey(String rawKey) {
    String key = (rawKey == null) ? "" : rawKey.trim();
    if (key.startsWith("@")) key = key.substring(1).trim();
    if (key.startsWith("+")) key = key.substring(1).trim();
    if (key.isEmpty()) return "";
    return key.toLowerCase(Locale.ROOT);
  }

  static String firstIrcv3TagValue(Map<String, String> tags, String... keys) {
    if (tags == null || tags.isEmpty() || keys == null) return "";
    for (String key : keys) {
      String wanted = normalizeIrcv3TagKey(key);
      if (wanted.isEmpty()) continue;
      for (Map.Entry<String, String> entry : tags.entrySet()) {
        String actual = normalizeIrcv3TagKey(entry.getKey());
        if (!wanted.equals(actual)) continue;
        String value = (entry.getValue() == null) ? "" : entry.getValue().trim();
        if (value.isEmpty()) continue;
        return value;
      }
    }
    return "";
  }

  static String sanitizeTagForMeta(String rawKey) {
    String key = normalizeIrcv3TagKey(rawKey);
    if (key.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(key.length());
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    return sb.toString();
  }

  static String formatIrcv3Tags(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      String key = normalizeIrcv3TagKey(entry.getKey());
      if (key.isEmpty()) continue;
      if (sb.length() > 0) sb.append(';');
      sb.append(key);
      String value = (entry.getValue() == null) ? "" : entry.getValue();
      if (!value.isEmpty()) sb.append('=').append(value);
    }
    return sb.toString();
  }

  static Map<String, String> parseIrcv3TagsDisplay(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return Map.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    String[] parts = value.split(";");
    for (String part : parts) {
      String token = Objects.toString(part, "").trim();
      if (token.isEmpty()) continue;
      int eq = token.indexOf('=');
      if (eq < 0) {
        out.put(token, "");
      } else {
        String key = token.substring(0, eq).trim();
        String itemValue = token.substring(eq + 1).trim();
        if (!key.isEmpty()) out.put(key, itemValue);
      }
    }
    if (out.isEmpty()) return Map.of();
    return out;
  }

  static Map<String, String> mergeIrcv3Tags(Map<String, String> base, Map<String, String> overlay) {
    Map<String, String> left = normalizeIrcv3Tags(base);
    Map<String, String> right = normalizeIrcv3Tags(overlay);
    if (left.isEmpty()) return right;
    if (right.isEmpty()) return left;
    LinkedHashMap<String, String> out = new LinkedHashMap<>(left);
    out.putAll(right);
    return out;
  }
}
