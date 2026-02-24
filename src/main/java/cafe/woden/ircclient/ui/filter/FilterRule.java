package cafe.woden.ircclient.ui.filter;

import cafe.woden.ircclient.logging.model.LogKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A view-only filter rule.
 *
 * <p>Filters never delete or prevent logging; they only affect what is rendered in a transcript.
 */
public record FilterRule(
    UUID id,
    String name,
    boolean enabled,
    String scopePattern,
    FilterAction action,
    FilterDirection direction,
    EnumSet<LogKind> kinds,
    List<String> fromNickGlobs,
    RegexSpec textRegex,
    TagSpec tags) {

  public FilterRule {
    id = (id != null) ? id : UUID.randomUUID();
    name = Objects.toString(name, "").trim();
    scopePattern = Objects.toString(scopePattern, "*").trim();
    if (scopePattern.isBlank()) scopePattern = "*";

    action = (action != null) ? action : FilterAction.HIDE;
    direction = (direction != null) ? direction : FilterDirection.ANY;

    kinds = (kinds == null) ? EnumSet.noneOf(LogKind.class) : EnumSet.copyOf(kinds);
    fromNickGlobs = (fromNickGlobs == null) ? List.of() : List.copyOf(fromNickGlobs);
    textRegex = (textRegex == null) ? new RegexSpec("", null) : textRegex;
    tags = (tags == null) ? TagSpec.empty() : tags;
  }

  /** Canonical key for case-insensitive name comparisons. */
  public String nameKey() {
    return name.trim().toLowerCase(Locale.ROOT);
  }

  public boolean hasKinds() {
    return kinds != null && !kinds.isEmpty();
  }

  public boolean hasFromNickGlobs() {
    return fromNickGlobs != null && !fromNickGlobs.isEmpty();
  }

  public boolean hasTextRegex() {
    return textRegex != null && !textRegex.isEmpty();
  }

  public boolean hasTags() {
    return tags != null && !tags.isEmpty();
  }
}
