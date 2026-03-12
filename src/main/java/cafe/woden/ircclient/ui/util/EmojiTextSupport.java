package cafe.woden.ircclient.ui.util;

import java.util.ArrayList;
import java.util.List;

/** Best-effort emoji-cluster detection used to split styled transcript runs. */
public final class EmojiTextSupport {

  public record Cluster(String text, boolean emoji) {}

  public record Segment(String text, boolean emoji) {}

  private EmojiTextSupport() {}

  public static boolean containsEmoji(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }

    int i = 0;
    while (i < text.length()) {
      int end = nextClusterEnd(text, i);
      if (isEmojiCluster(text, i, end)) {
        return true;
      }
      i = end;
    }
    return false;
  }

  public static List<Cluster> clusters(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }

    ArrayList<Cluster> out = new ArrayList<>();
    int i = 0;
    while (i < text.length()) {
      int end = nextClusterEnd(text, i);
      out.add(new Cluster(text.substring(i, end), isEmojiCluster(text, i, end)));
      i = end;
    }
    return List.copyOf(out);
  }

  public static List<Segment> split(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }

    ArrayList<Segment> out = new ArrayList<>();
    StringBuilder plain = new StringBuilder();

    for (Cluster cluster : clusters(text)) {
      if (cluster.emoji()) {
        if (!plain.isEmpty()) {
          out.add(new Segment(plain.toString(), false));
          plain.setLength(0);
        }
        out.add(new Segment(cluster.text(), true));
      } else {
        plain.append(cluster.text());
      }
    }
    if (!plain.isEmpty()) {
      out.add(new Segment(plain.toString(), false));
    }
    return List.copyOf(out);
  }

  private static int nextClusterEnd(String text, int start) {
    int len = text.length();
    int cp = text.codePointAt(start);
    int end = start + Character.charCount(cp);

    if (isRegionalIndicator(cp)) {
      if (end < len) {
        int next = text.codePointAt(end);
        if (isRegionalIndicator(next)) {
          end += Character.charCount(next);
        }
      }
      return end;
    }

    if (isKeycapBase(cp)) {
      if (end < len) {
        int next = text.codePointAt(end);
        if (next == 0xFE0F || next == 0xFE0E) {
          end += Character.charCount(next);
        }
      }
      if (end < len) {
        int next = text.codePointAt(end);
        if (next == 0x20E3) {
          end += Character.charCount(next);
        }
      }
      return end;
    }

    if (!isEmojiBase(cp)) {
      return end;
    }

    while (end < len) {
      int next = text.codePointAt(end);
      if (isEmojiModifier(next)
          || isEmojiVariationSelector(next)
          || isEmojiTag(next)
          || next == 0x20E3) {
        end += Character.charCount(next);
        continue;
      }
      if (next == 0x200D) {
        int joinerEnd = end + Character.charCount(next);
        if (joinerEnd >= len) {
          return joinerEnd;
        }
        int joined = text.codePointAt(joinerEnd);
        end = joinerEnd + Character.charCount(joined);
        continue;
      }
      break;
    }

    return end;
  }

  private static boolean isEmojiCluster(String text, int start, int end) {
    if (text == null || text.isEmpty() || start < 0 || start >= end || end > text.length()) {
      return false;
    }

    int cp = text.codePointAt(start);
    if (isRegionalIndicator(cp) || isEmojiBase(cp)) {
      return true;
    }
    return isKeycapBase(cp)
        && end > start + Character.charCount(cp)
        && text.codePointBefore(end) == 0x20E3;
  }

  private static boolean isEmojiBase(int cp) {
    if (cp >= 0x1F000 && cp <= 0x1FAFF) return true;
    if (cp >= 0x2600 && cp <= 0x27BF) return true;
    if (cp >= 0x2300 && cp <= 0x23FF) return true;
    if (cp >= 0x2190 && cp <= 0x21FF) return true;
    if (cp >= 0x2B00 && cp <= 0x2BFF) return true;
    if (cp == 0x00A9
        || cp == 0x00AE
        || cp == 0x203C
        || cp == 0x2049
        || cp == 0x2122
        || cp == 0x2139
        || cp == 0x2934
        || cp == 0x2935
        || cp == 0x3030
        || cp == 0x303D
        || cp == 0x3297
        || cp == 0x3299) {
      return true;
    }
    return false;
  }

  private static boolean isEmojiModifier(int cp) {
    return cp >= 0x1F3FB && cp <= 0x1F3FF;
  }

  private static boolean isEmojiVariationSelector(int cp) {
    return cp == 0xFE0F || cp == 0xFE0E;
  }

  private static boolean isEmojiTag(int cp) {
    return (cp >= 0xE0020 && cp <= 0xE007E) || cp == 0xE007F;
  }

  private static boolean isRegionalIndicator(int cp) {
    return cp >= 0x1F1E6 && cp <= 0x1F1FF;
  }

  private static boolean isKeycapBase(int cp) {
    return (cp >= '0' && cp <= '9') || cp == '#' || cp == '*';
  }
}
