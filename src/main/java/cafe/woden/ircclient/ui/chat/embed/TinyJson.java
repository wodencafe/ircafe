package cafe.woden.ircclient.ui.chat.embed;

/**
 * Tiny, best-effort JSON string helper.
 *
 * <p>This is not a full JSON parser. It is intended for the limited, stable shapes we
 * consume in link preview resolvers (oEmbed and a couple of public JSON endpoints).
 */
final class TinyJson {

  private TinyJson() {}

  static String findString(String json, String key) {
    if (json == null || key == null) return null;
    int i = indexOfKey(json, key, 0);
    while (i >= 0) {
      int p = skipToValue(json, i + key.length() + 2);
      if (p < 0) return null;
      p = skipWs(json, p);
      if (p < json.length() && json.charAt(p) == '"') {
        return parseString(json, p);
      }
      i = indexOfKey(json, key, i + key.length() + 2);
    }
    return null;
  }

  static String findObject(String json, String key) {
    if (json == null || key == null) return null;
    int i = indexOfKey(json, key, 0);
    while (i >= 0) {
      int p = skipToValue(json, i + key.length() + 2);
      if (p < 0) return null;
      p = skipWs(json, p);
      if (p < json.length() && json.charAt(p) == '{') {
        return captureBalanced(json, p, '{', '}');
      }
      i = indexOfKey(json, key, i + key.length() + 2);
    }
    return null;
  }

  static String findArray(String json, String key) {
    if (json == null || key == null) return null;
    int i = indexOfKey(json, key, 0);
    while (i >= 0) {
      int p = skipToValue(json, i + key.length() + 2);
      if (p < 0) return null;
      p = skipWs(json, p);
      if (p < json.length() && json.charAt(p) == '[') {
        return captureBalanced(json, p, '[', ']');
      }
      i = indexOfKey(json, key, i + key.length() + 2);
    }
    return null;
  }

  
  static String firstObjectInArray(String arrayJson) {
    if (arrayJson == null) return null;
    int start = arrayJson.indexOf('{');
    if (start < 0) return null;
    return captureBalanced(arrayJson, start, '{', '}');
  }

  private static int indexOfKey(String json, String key, int from) {
    String needle = "\"" + key + "\"";
    return json.indexOf(needle, Math.max(0, from));
  }

  private static int skipToValue(String json, int from) {
    int p = Math.max(0, from);
    while (p < json.length()) {
      if (json.charAt(p) == ':') return p + 1;
      p++;
    }
    return -1;
  }

  private static int skipWs(String s, int i) {
    int p = Math.max(0, i);
    while (p < s.length()) {
      char c = s.charAt(p);
      if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
      p++;
    }
    return p;
  }

  private static String parseString(String json, int quotePos) {
    StringBuilder out = new StringBuilder();
    int i = quotePos + 1;
    while (i < json.length()) {
      char c = json.charAt(i);
      if (c == '"') return out.toString();
      if (c == '\\') {
        if (i + 1 >= json.length()) break;
        char e = json.charAt(i + 1);
        switch (e) {
          case '"' -> out.append('"');
          case '\\' -> out.append('\\');
          case '/' -> out.append('/');
          case 'b' -> out.append('\b');
          case 'f' -> out.append('\f');
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case 'u' -> {
            if (i + 6 <= json.length()) {
              String hex = json.substring(i + 2, i + 6);
              try {
                out.append((char) Integer.parseInt(hex, 16));
              } catch (Exception ignored) {
              }
              i += 4;
            }
          }
          default -> out.append(e);
        }
        i += 2;
        continue;
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  private static String captureBalanced(String s, int start, char open, char close) {
    int depth = 0;
    boolean inStr = false;
    boolean esc = false;
    for (int i = start; i < s.length(); i++) {
      char c = s.charAt(i);
      if (inStr) {
        if (esc) {
          esc = false;
        } else if (c == '\\') {
          esc = true;
        } else if (c == '"') {
          inStr = false;
        }
        continue;
      }
      if (c == '"') {
        inStr = true;
        continue;
      }
      if (c == open) depth++;
      if (c == close) {
        depth--;
        if (depth == 0) return s.substring(start, i + 1);
      }
    }
    return null;
  }
}
