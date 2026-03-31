package cafe.woden.ircclient.model;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Optional per-scope overrides for filter behavior.
 *
 * <p>Any nullable field indicates "no override".
 */
@ValueObject
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
