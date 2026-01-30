package cafe.woden.ircclient.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Pretty-prints IRC MODE changes into human-friendly sentences.
 *
 * <p>Input format is typically something like "+o nick", "+b mask", "+ov nick1 nick2", etc.
 * If a raw line sneaks through, we try to recover the mode section.
 */
public final class ModePrettyPrinter {

  private ModePrettyPrinter() {}

  public static List<String> pretty(String actor, String channel, String details) {
    String a = normalizeActor(actor, details);
    String d = normalizeDetails(channel, details);

    if (d == null || d.isBlank()) {
      return List.of(a + " sets mode.");
    }

    ParsedModes pm = parse(d);
    if (pm == null || pm.changes.isEmpty()) {
      return List.of(a + " sets mode: " + d);
    }

    List<String> out = new ArrayList<>();
    for (ModeChange c : pm.changes) {
      String s = prettyOne(a, channel, c);
      if (s != null && !s.isBlank()) out.add(s);
    }
    if (out.isEmpty()) {
      return List.of(a + " sets mode: " + d);
    }
    return out;
  }

  private static String normalizeActor(String actor, String details) {
    String a = actor == null ? "" : actor.trim();
    if (!a.isBlank()) {
      // Preserve canonical casing for ChanServ.
      if ("chanserv".equalsIgnoreCase(a)) return "ChanServ";
      return a;
    }

    // If actor is missing but details includes a raw line, try to recover prefix.
    if (details != null) {
      String det = details.trim();
      if (det.startsWith(":")) {
        int sp = det.indexOf(' ');
        if (sp > 1) {
          String prefix = det.substring(1, sp);
          int bang = prefix.indexOf('!');
          if (bang >= 0) prefix = prefix.substring(0, bang);
          if (!prefix.isBlank()) {
            if ("chanserv".equalsIgnoreCase(prefix)) return "ChanServ";
            return prefix;
          }
        }
      }
    }

    return "server";
  }

  private static String normalizeDetails(String channel, String details) {
    if (details == null) return null;
    String d = details.trim();
    if (d.isEmpty()) return d;

    // If this looks like a raw MODE line, reduce it to the mode section.
    String det = d;
    if (det.startsWith(":")) {
      // strip prefix for parsing
      int sp = det.indexOf(' ');
      if (sp > 0) det = det.substring(sp + 1).trim();
    }

    String[] toks = det.split("\\s+");
    for (int i = 0; i < toks.length; i++) {
      if ("MODE".equalsIgnoreCase(toks[i])) {
        int idx = i + 2; // MODE <chan> <modes...>
        if (idx <= toks.length - 1) {
          StringBuilder sb = new StringBuilder();
          for (int j = idx; j < toks.length; j++) {
            if (j > idx) sb.append(' ');
            sb.append(toks[j]);
          }
          String reduced = sb.toString().trim();
          return reduced.isEmpty() ? d : reduced;
        }
      }
    }

    // If we were provided channel name, sometimes details may start with it.
    if (channel != null && !channel.isBlank()) {
      String ch = channel.trim();
      if (d.startsWith(ch + " ")) {
        return d.substring(ch.length()).trim();
      }
    }

    return d;
  }

  private static ParsedModes parse(String details) {
    if (details == null) return null;
    String d = details.trim();
    if (d.isEmpty()) return null;

    String[] parts = d.split("\\s+");
    if (parts.length == 0) return null;

    String modeSeq = parts[0];
    List<String> args = new ArrayList<>();
    for (int i = 1; i < parts.length; i++) args.add(parts[i]);

    List<ModeChange> changes = new ArrayList<>();
    boolean adding = true;
    int argIdx = 0;

    for (int i = 0; i < modeSeq.length(); i++) {
      char c = modeSeq.charAt(i);
      if (c == '+') {
        adding = true;
        continue;
      }
      if (c == '-') {
        adding = false;
        continue;
      }

      boolean wantsArg = wantsArgument(c, adding);
      String arg = null;
      if (wantsArg && argIdx < args.size()) {
        arg = args.get(argIdx++);
      }
      changes.add(new ModeChange(adding, c, arg));
    }

    // If there are leftover args, attach them to the last mode (best-effort)
    if (argIdx < args.size() && !changes.isEmpty()) {
      ModeChange last = changes.get(changes.size() - 1);
      if (last.arg == null) {
        last.arg = String.join(" ", args.subList(argIdx, args.size()));
      }
    }

    return new ParsedModes(changes);
  }

  private static boolean wantsArgument(char mode, boolean adding) {
    return switch (mode) {
      // user prefix modes
      case 'o', 'v', 'h', 'a', 'q', 'y' -> true;

      // list modes
      case 'b', 'e', 'I' -> true;

      // key/limit and other common arg modes
      case 'k' -> true; // sometimes -k includes key, sometimes not; best-effort
      case 'l' -> adding; // +l needs arg; -l often doesn't
      case 'f', 'j' -> true;

      default -> false;
    };
  }

  private static String prettyOne(String actor, String channel, ModeChange c) {
    String who = actor;
    String arg = c.arg;
    char m = c.mode;
    boolean add = c.add;

    // Privilege modes
    if (m == 'o') {
      if (add) return who + " gives channel operator privileges to " + safe(arg) + ".";
      return who + " removes channel operator privileges from " + safe(arg) + ".";
    }
    if (m == 'v') {
      if (add) return who + " gives voice privileges to " + safe(arg) + ".";
      return who + " removes voice privileges from " + safe(arg) + ".";
    }
    if (m == 'h') {
      if (add) return who + " gives half-operator privileges to " + safe(arg) + ".";
      return who + " removes half-operator privileges from " + safe(arg) + ".";
    }
    if (m == 'a') {
      if (add) return who + " gives admin privileges to " + safe(arg) + ".";
      return who + " removes admin privileges from " + safe(arg) + ".";
    }
    if (m == 'q') {
      if (add) return who + " gives owner privileges to " + safe(arg) + ".";
      return who + " removes owner privileges from " + safe(arg) + ".";
    }

    // Ban & lists
    if (m == 'b') {
      if (add) return who + " adds a ban on " + safe(arg) + ".";
      return who + " removes a ban on " + safe(arg) + ".";
    }
    if (m == 'e') {
      if (add) return who + " adds a ban exception for " + safe(arg) + ".";
      return who + " removes a ban exception for " + safe(arg) + ".";
    }
    if (m == 'I') {
      if (add) return who + " adds an invite exception for " + safe(arg) + ".";
      return who + " removes an invite exception for " + safe(arg) + ".";
    }

    // Common channel state modes (no args)
    if (m == 'm') {
      if (add) return who + " sets the channel to moderated.";
      return who + " removes moderated mode.";
    }
    if (m == 'i') {
      if (add) return who + " sets the channel to invite-only.";
      return who + " removes invite-only mode.";
    }
    if (m == 't') {
      if (add) return who + " locks the topic to channel operators.";
      return who + " unlocks the topic.";
    }
    if (m == 'n') {
      if (add) return who + " blocks messages from outside the channel.";
      return who + " allows messages from outside the channel.";
    }
    if (m == 's') {
      if (add) return who + " sets the channel to secret.";
      return who + " removes secret mode.";
    }
    if (m == 'p') {
      if (add) return who + " sets the channel to private.";
      return who + " removes private mode.";
    }
    if (m == 'r') {
      if (add) return who + " sets the channel to registered-only.";
      return who + " removes registered-only mode.";
    }

    // Key/limit
    if (m == 'k') {
      if (add) return who + " sets a channel key.";
      return who + " removes the channel key.";
    }
    if (m == 'l') {
      if (add) return who + " sets the user limit to " + safe(arg) + ".";
      return who + " removes the user limit.";
    }

    // Fallback
    String sign = add ? "+" : "-";
    if (arg != null && !arg.isBlank()) {
      return who + " sets mode " + sign + m + " " + arg + ".";
    }
    return who + " sets mode " + sign + m + ".";
  }

  private static String safe(String s) {
    if (s == null || s.isBlank()) return "(unknown)";
    return s;
  }

  private record ParsedModes(List<ModeChange> changes) {}

  private static final class ModeChange {
    final boolean add;
    final char mode;
    String arg;

    private ModeChange(boolean add, char mode, String arg) {
      this.add = add;
      this.mode = mode;
      this.arg = arg;
    }
  }
}
