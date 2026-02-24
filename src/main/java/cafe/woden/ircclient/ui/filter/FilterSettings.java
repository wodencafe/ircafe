package cafe.woden.ircclient.ui.filter;

import java.util.List;

/** Current filter settings snapshot. */
public record FilterSettings(
    boolean filtersEnabledByDefault,
    boolean placeholdersEnabledByDefault,
    boolean placeholdersCollapsedByDefault,
    int placeholderMaxPreviewLines,
    int placeholderMaxLinesPerRun,
    int placeholderTooltipMaxTags,
    int historyPlaceholderMaxRunsPerBatch,
    boolean historyPlaceholdersEnabledByDefault,
    List<FilterRule> rules,
    List<FilterScopeOverride> overrides) {

  public FilterSettings {
    if (placeholderMaxPreviewLines < 0) placeholderMaxPreviewLines = 0;
    if (placeholderMaxPreviewLines > 25) placeholderMaxPreviewLines = 25;

    if (placeholderMaxLinesPerRun < 0) placeholderMaxLinesPerRun = 0;
    if (placeholderMaxLinesPerRun > 50_000) placeholderMaxLinesPerRun = 50_000;

    if (placeholderTooltipMaxTags < 0) placeholderTooltipMaxTags = 0;
    if (placeholderTooltipMaxTags > 500) placeholderTooltipMaxTags = 500;

    if (historyPlaceholderMaxRunsPerBatch < 0) historyPlaceholderMaxRunsPerBatch = 0;
    if (historyPlaceholderMaxRunsPerBatch > 5_000) historyPlaceholderMaxRunsPerBatch = 5_000;

    rules = (rules == null) ? List.of() : List.copyOf(rules);
    overrides = (overrides == null) ? List.of() : List.copyOf(overrides);
  }

  public static FilterSettings defaults() {
    return new FilterSettings(true, true, true, 3, 250, 12, 10, true, List.of(), List.of());
  }
}
