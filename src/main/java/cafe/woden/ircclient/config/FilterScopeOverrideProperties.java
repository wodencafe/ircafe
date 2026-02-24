package cafe.woden.ircclient.config;

import java.util.Objects;

/**
 * Config-backed per-scope filter behavior overrides.
 *
 * <p>YAML binding location: {@code ircafe.ui.filters.overrides}.
 */
public record FilterScopeOverrideProperties(
    String scope,
    Boolean filtersEnabled,
    Boolean placeholdersEnabled,
    Boolean placeholdersCollapsed) {

  public FilterScopeOverrideProperties {
    scope = Objects.toString(scope, "*").trim();
    if (scope.isBlank()) scope = "*";
  }
}
