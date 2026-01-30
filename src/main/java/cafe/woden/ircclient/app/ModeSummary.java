package cafe.woden.ircclient.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Human-friendly summaries for common channel mode flags. */
final class ModeSummary {

  // Preference order for the "join burst" / summary display.
  private static final List<Character> ORDER = List.of('t', 'n', 's', 'p', 'i', 'm', 'r');

  private ModeSummary() {}

  static String describeBufferedJoinModes(Set<Character> plus, Set<Character> minus) {
    List<String> parts = new ArrayList<>();

    addOrdered(parts, plus, true);
    addOrdered(parts, minus, false);

    // Unknowns (stable-ish): keep insertion order if possible.
    addUnknown(parts, plus, true);
    addUnknown(parts, minus, false);

    if (parts.isEmpty()) return "";
    return "Channel modes: " + String.join(", ", parts);
  }

  static String describeCurrentChannelModes(String details) {
    if (details == null) return "";
    String d = details.trim();
    if (d.isEmpty()) return "";

    String[] toks = d.split("\\s+");
    if (toks.length == 0) return "";

    String modeStr = toks[0];

    List<String> args = new ArrayList<>();
    for (int i = 1; i < toks.length; i++) args.add(toks[i]);

    // Enabled/disabled sets for arg-less flags
    LinkedHashSet<Character> plus = new LinkedHashSet<>();
    LinkedHashSet<Character> minus = new LinkedHashSet<>();

    // Extra phrases for arg-modes like +l 50, +k <key>
    List<String> extras = new ArrayList<>();

    int argIdx = 0;
    char sign = '+';
    for (int i = 0; i < modeStr.length(); i++) {
      char c = modeStr.charAt(i);
      if (c == '+' || c == '-') {
        sign = c;
        continue;
      }

      // Modes that take args in 324 output.
      if (c == 'l') {
        if (sign == '+') {
          String limit = (argIdx < args.size()) ? args.get(argIdx) : null;
          argIdx++;
          if (limit != null && !limit.isBlank()) extras.add("user limit " + limit);
          else extras.add("user limit set");
        } else {
          extras.add("user limit removed");
        }
        continue;
      }

      if (c == 'k') {
        if (sign == '+') {
          // Don't leak the key value.
          if (argIdx < args.size()) argIdx++;
          extras.add("channel key set");
        } else {
          extras.add("channel key removed");
        }
        continue;
      }

      if (sign == '+') plus.add(c);
      else minus.add(c);
    }

    List<String> parts = new ArrayList<>();
    addOrdered(parts, plus, true);
    addUnknown(parts, plus, true);
    if (!extras.isEmpty()) parts.addAll(extras);

    // Usually 324 is only '+'; keep '-' handling just in case.
    addOrdered(parts, minus, false);
    addUnknown(parts, minus, false);

    if (parts.isEmpty()) return "Channel modes: (none)";
    return "Channel modes: " + String.join(", ", parts);
  }

  private static void addOrdered(List<String> out, Set<Character> set, boolean enabled) {
    if (set == null || set.isEmpty()) return;
    for (char c : ORDER) {
      if (set.contains(c)) {
        String s = describeFlag(c, enabled);
        if (!s.isEmpty()) out.add(s);
      }
    }
  }

  private static void addUnknown(List<String> out, Set<Character> set, boolean enabled) {
    if (set == null || set.isEmpty()) return;
    for (char c : set) {
      if (ORDER.contains(c)) continue;
      String s = describeFlag(c, enabled);
      if (!s.isEmpty()) out.add(s);
    }
  }

  private static String describeFlag(char c, boolean enabled) {
    return switch (c) {
      case 't' -> enabled ? "topic locked" : "topic unlocked";
      case 'n' -> enabled ? "no outside messages" : "outside messages allowed";
      case 's' -> enabled ? "secret" : "not secret";
      case 'p' -> enabled ? "private" : "not private";
      case 'i' -> enabled ? "invite only" : "invite only disabled";
      case 'm' -> enabled ? "moderated" : "unmoderated";
      case 'r' -> enabled ? "registered only" : "registered only disabled";
      default -> (enabled ? "+" : "-") + c;
    };
  }
}
