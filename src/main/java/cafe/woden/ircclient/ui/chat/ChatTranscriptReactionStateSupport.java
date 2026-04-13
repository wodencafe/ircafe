package cafe.woden.ircclient.ui.chat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Shared transcript reaction-state bookkeeping helpers. */
final class ChatTranscriptReactionStateSupport {

  private ChatTranscriptReactionStateSupport() {}

  static void observe(
      Map<String, LinkedHashSet<String>> nicksByReaction, String reaction, String nick) {
    if (nicksByReaction == null) return;
    String token = Objects.toString(reaction, "").trim();
    String n = Objects.toString(nick, "").trim();
    if (token.isEmpty() || n.isEmpty()) return;
    nicksByReaction.computeIfAbsent(token, k -> new LinkedHashSet<>()).add(n);
  }

  static void forget(
      Map<String, LinkedHashSet<String>> nicksByReaction, String reaction, String nick) {
    if (nicksByReaction == null) return;
    String token = Objects.toString(reaction, "").trim();
    String n = Objects.toString(nick, "").trim();
    if (token.isEmpty() || n.isEmpty()) return;
    LinkedHashSet<String> nicks = nicksByReaction.get(token);
    if (nicks == null) return;
    nicks.remove(n);
    if (nicks.isEmpty()) {
      nicksByReaction.remove(token);
    }
  }

  static boolean hasReactionFromNick(
      Map<String, LinkedHashSet<String>> nicksByReaction, String reaction, String normalizedNick) {
    if (nicksByReaction == null) return false;
    String token = Objects.toString(reaction, "").trim();
    String nickKey = normalizeReactionNickKey(normalizedNick);
    if (token.isEmpty() || nickKey.isEmpty()) return false;
    LinkedHashSet<String> nicks = nicksByReaction.get(token);
    if (nicks == null || nicks.isEmpty()) return false;
    for (String existingNick : nicks) {
      if (nickKey.equals(normalizeReactionNickKey(existingNick))) {
        return true;
      }
    }
    return false;
  }

  static Map<String, Collection<String>> reactionsSnapshot(
      Map<String, LinkedHashSet<String>> nicksByReaction) {
    LinkedHashMap<String, Collection<String>> out = new LinkedHashMap<>();
    if (nicksByReaction == null) return out;
    for (Map.Entry<String, LinkedHashSet<String>> entry : nicksByReaction.entrySet()) {
      out.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return out;
  }

  static String normalizeReactionNickKey(String rawNick) {
    String nick = Objects.toString(rawNick, "").trim();
    return nick.isEmpty() ? "" : nick.toLowerCase(Locale.ROOT);
  }
}
