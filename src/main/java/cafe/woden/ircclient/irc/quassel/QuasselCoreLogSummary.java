package cafe.woden.ircclient.irc.quassel;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Bounded, redacted payload summaries shared by Core request and preflight diagnostics. */
final class QuasselCoreLogSummary {
  private QuasselCoreLogSummary() {}

  static String summarizeNetworkInfoForLog(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) return "{}";
    return summarizeValueForLog(payload, 0);
  }

  @SuppressWarnings("unchecked")
  private static String summarizeValueForLog(Object value, int depth) {
    if (value == null) return "null";
    if (depth > 3) return "<depth-limit>";
    if (value instanceof String s) return '"' + s + '"';
    if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
    if (value instanceof byte[] bytes) return "byte[" + bytes.length + "]";
    if (value instanceof QuasselCoreDatastreamCodec.UserTypeValue userType) {
      return "UserType("
          + userType.typeName()
          + "="
          + summarizeValueForLog(userType.value(), depth + 1)
          + ")";
    }
    if (value instanceof List<?> list) {
      StringBuilder out = new StringBuilder();
      out.append("List[");
      int index = 0;
      for (Object item : list) {
        if (index > 0) out.append(", ");
        if (index >= 8) {
          out.append("...");
          break;
        }
        out.append(summarizeValueForLog(item, depth + 1));
        index++;
      }
      out.append(']');
      return out.toString();
    }
    if (value instanceof Map<?, ?> map) {
      StringBuilder out = new StringBuilder();
      out.append('{');
      int index = 0;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (index > 0) out.append(", ");
        if (index >= 32) {
          out.append("...");
          break;
        }
        String key = Objects.toString(entry.getKey(), "");
        out.append(key).append('=');
        if (looksSensitiveLogKey(key)) {
          out.append("<redacted>");
        } else {
          out.append(summarizeValueForLog(entry.getValue(), depth + 1));
        }
        index++;
      }
      out.append('}');
      return out.toString();
    }
    return value.getClass().getSimpleName() + "(" + value + ")";
  }

  private static boolean looksSensitiveLogKey(String key) {
    String token = Objects.toString(key, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return false;
    return token.contains("pass")
        || token.contains("secret")
        || token.contains("token")
        || token.contains("auth");
  }
}
