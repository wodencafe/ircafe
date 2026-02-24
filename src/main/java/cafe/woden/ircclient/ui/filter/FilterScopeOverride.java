package cafe.woden.ircclient.ui.filter;

import java.util.Objects;

/**
 * Optional per-scope overrides for filter behavior.
 *
 * <p>Any nullable field indicates "no override".
 */
public record FilterScopeOverride(
    String scopePattern,
    Boolean filtersEnabled,
    Boolean placeholdersEnabled,
    Boolean placeholdersCollapsed) {

  public FilterScopeOverride {
    scopePattern = Objects.toString(scopePattern, "*").trim();
    if (scopePattern.isBlank()) scopePattern = "*";
  }
}
