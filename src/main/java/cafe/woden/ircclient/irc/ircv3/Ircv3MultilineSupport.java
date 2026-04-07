package cafe.woden.ircclient.irc.ircv3;

import java.util.Locale;
import java.util.Objects;

/** Shared final/draft token helpers for IRCv3 multiline capability handling. */
public final class Ircv3MultilineSupport {

  public record LimitParams(long maxBytes, long maxLines) {}

  public static final String MULTILINE_CAPABILITY = "multiline";
  public static final String DRAFT_MULTILINE_CAPABILITY = "draft/multiline";
  public static final String MULTILINE_CONCAT_TAG = "multiline-concat";
  public static final String DRAFT_MULTILINE_CONCAT_TAG = "draft/multiline-concat";

  private Ircv3MultilineSupport() {}

  public static boolean isMultilineCapability(String rawCapability) {
    String token = normalizeToken(rawCapability);
    return MULTILINE_CAPABILITY.equals(token) || DRAFT_MULTILINE_CAPABILITY.equals(token);
  }

  public static boolean isDraftMultilineCapability(String rawCapability) {
    return DRAFT_MULTILINE_CAPABILITY.equals(normalizeToken(rawCapability));
  }

  public static boolean isMultilineConcatTag(String rawTagKey) {
    String key = normalizeTagKey(rawTagKey);
    return MULTILINE_CONCAT_TAG.equals(key) || DRAFT_MULTILINE_CONCAT_TAG.equals(key);
  }

  public static String negotiatedBatchType(boolean finalAcked, boolean draftAcked) {
    if (finalAcked) {
      return MULTILINE_CAPABILITY;
    }
    if (draftAcked) {
      return DRAFT_MULTILINE_CAPABILITY;
    }
    return "";
  }

  public static String negotiatedConcatTag(boolean finalAcked, boolean draftAcked) {
    if (finalAcked) {
      return MULTILINE_CONCAT_TAG;
    }
    if (draftAcked) {
      return DRAFT_MULTILINE_CONCAT_TAG;
    }
    return "";
  }

  public static LimitParams parseLimitParams(String rawParams) {
    String params = Objects.toString(rawParams, "").trim();
    if (params.isEmpty()) {
      return new LimitParams(-1L, -1L);
    }
    return new LimitParams(
        parseNamedLongParam(params, "max-bytes", "maxbytes", "max_bytes", "bytes"),
        parseNamedLongParam(params, "max-lines", "maxlines", "max_lines", "lines"));
  }

  public static String normalizeTagKey(String rawTagKey) {
    String key = Objects.toString(rawTagKey, "").trim();
    if (key.startsWith("@")) key = key.substring(1).trim();
    if (key.startsWith("+")) key = key.substring(1).trim();
    return key.toLowerCase(Locale.ROOT);
  }

  private static String normalizeToken(String rawCapability) {
    return Objects.toString(rawCapability, "").trim().toLowerCase(Locale.ROOT);
  }

  private static long parseNamedLongParam(String params, String... names) {
    String text = Objects.toString(params, "").trim();
    if (text.isEmpty() || names == null || names.length == 0) return -1L;
    String lower = text.toLowerCase(Locale.ROOT);
    for (String rawName : names) {
      String name = Objects.toString(rawName, "").trim().toLowerCase(Locale.ROOT);
      if (name.isEmpty()) continue;
      String needle = name + "=";
      int idx = lower.indexOf(needle);
      while (idx >= 0) {
        int start = idx + needle.length();
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
        if (end > start) {
          try {
            return Long.parseLong(text.substring(start, end));
          } catch (NumberFormatException ignored) {
          }
        }
        idx = lower.indexOf(needle, idx + 1);
      }
    }
    return -1L;
  }
}
