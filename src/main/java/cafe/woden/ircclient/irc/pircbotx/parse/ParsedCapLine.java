package cafe.woden.ircclient.irc.pircbotx.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Normalized IRC CAP subcommand and capability token list. */
public record ParsedCapLine(String action, String normalizedCaps, List<String> tokens) {

  public static ParsedCapLine parse(String actionRaw, String capListRaw) {
    String action = Objects.toString(actionRaw, "").trim().toUpperCase(Locale.ROOT);
    String normalizedCaps = Objects.toString(capListRaw, "").trim();
    if (normalizedCaps.startsWith(":")) normalizedCaps = normalizedCaps.substring(1).trim();

    List<String> tokens = new ArrayList<>();
    if (!normalizedCaps.isEmpty()) {
      for (String token : normalizedCaps.split("\\s+")) {
        String normalized = Objects.toString(token, "").trim();
        if (!normalized.isEmpty()) {
          tokens.add(normalized);
        }
      }
    }
    return new ParsedCapLine(action, normalizedCaps, List.copyOf(tokens));
  }

  public boolean hasTokens() {
    return !tokens.isEmpty();
  }

  public boolean isAction(String... expected) {
    for (String candidate : expected) {
      if (action.equals(Objects.toString(candidate, "").trim().toUpperCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }
}
