package cafe.woden.ircclient.config;

import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.FilterDirection;
import cafe.woden.ircclient.model.LogKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Config-backed filter rule definition.
 *
 * <p>Filters never delete or prevent logging; they only affect what is rendered in a transcript.
 *
 * <p>YAML binding location: {@code ircafe.ui.filters.rules}.
 */
public record FilterRuleProperties(
    String name,
    Boolean enabled,
    String scope,
    FilterAction action,
    FilterDirection dir,
    List<LogKind> kinds,
    List<String> from,
    String tags,
    TextRegex text) {

  public FilterRuleProperties {
    name = Objects.toString(name, "").trim();

    if (enabled == null) enabled = true;

    scope = Objects.toString(scope, "*").trim();
    if (scope.isBlank()) scope = "*";

    action = (action != null) ? action : FilterAction.HIDE;
    dir = (dir != null) ? dir : FilterDirection.ANY;

    kinds = (kinds == null) ? List.of() : kinds.stream().filter(Objects::nonNull).toList();

    List<String> outFrom = new ArrayList<>();
    if (from != null) {
      for (String s : from) {
        String t = Objects.toString(s, "").trim();
        if (!t.isEmpty()) outFrom.add(t);
      }
    }
    from = List.copyOf(outFrom);

    tags = Objects.toString(tags, "").trim();

    if (text != null && text.pattern().isBlank()) {
      text = null;
    }

    // No name means no rule; treat as disabled.
    if (name.isEmpty()) enabled = false;
  }

  public record TextRegex(String pattern, String flags) {
    public TextRegex {
      pattern = Objects.toString(pattern, "").trim();

      String f = Objects.toString(flags, "").trim();
      if (!f.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (char c : f.toLowerCase(Locale.ROOT).toCharArray()) {
          if (c == 'i' || c == 'm' || c == 's') {
            if (sb.indexOf(String.valueOf(c)) < 0) sb.append(c);
          }
        }
        f = sb.toString();
      }
      flags = f;
    }
  }
}
