package cafe.woden.ircclient.config.api;

import cafe.woden.ircclient.model.FilterRule;
import cafe.woden.ircclient.model.FilterScopeOverride;
import java.util.List;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted local transcript filter settings. */
@ApplicationLayer
public interface FilterSettingsConfigPort {

  void rememberFiltersEnabledByDefault(boolean enabled);

  void rememberFilterPlaceholdersEnabledByDefault(boolean enabled);

  void rememberFilterPlaceholdersCollapsedByDefault(boolean collapsed);

  void rememberFilterPlaceholderMaxPreviewLines(int maxLines);

  void rememberFilterPlaceholderMaxLinesPerRun(int maxLines);

  void rememberFilterPlaceholderTooltipMaxTags(int maxTags);

  void rememberFilterHistoryPlaceholderMaxRunsPerBatch(int maxRuns);

  void rememberFilterHistoryPlaceholdersEnabledByDefault(boolean enabled);

  void rememberFilterRules(List<FilterRule> rules);

  void rememberFilterOverrides(List<FilterScopeOverride> overrides);
}
