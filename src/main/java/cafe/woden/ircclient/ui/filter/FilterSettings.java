package cafe.woden.ircclient.ui.filter;

import java.util.List;

/**
 * Current filter settings snapshot.
 */
public record FilterSettings(
    boolean filtersEnabledByDefault,
    boolean placeholdersEnabledByDefault,
    boolean placeholdersCollapsedByDefault,
    int placeholderMaxPreviewLines,
    List<FilterRule> rules,
    List<FilterScopeOverride> overrides
) {

  public FilterSettings {
    if (placeholderMaxPreviewLines < 0) placeholderMaxPreviewLines = 0;
    if (placeholderMaxPreviewLines > 25) placeholderMaxPreviewLines = 25;
    rules = (rules == null) ? List.of() : List.copyOf(rules);
    overrides = (overrides == null) ? List.of() : List.copyOf(overrides);
  }

  public static FilterSettings defaults() {
    return new FilterSettings(true, true, true, 3, List.of(), List.of());
  }
}
