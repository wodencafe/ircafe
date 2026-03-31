package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.FilterScopeOverride;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import cafe.woden.ircclient.ui.filter.FilterSettings;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import java.util.List;
import java.util.Objects;

final class FilterSettingsApplySupport {
  private FilterSettingsApplySupport() {}

  static void applyFromUi(
      FilterControls c,
      FilterSettingsBus filterSettingsBus,
      RuntimeConfigStore runtimeConfig,
      ActiveTargetPort targetCoordinator,
      TranscriptRebuildService transcriptRebuildService) {
    if (c == null) return;

    FilterSettings prev = filterSettingsBus.get();
    boolean enabledByDefault = c.filtersEnabledByDefault.isSelected();
    boolean placeholdersEnabledByDefault = c.placeholdersEnabledByDefault.isSelected();
    boolean placeholdersCollapsedByDefault = c.placeholdersCollapsedByDefault.isSelected();
    int previewLines = ((Number) c.placeholderPreviewLines.getValue()).intValue();
    if (previewLines < 0) previewLines = 0;
    if (previewLines > 25) previewLines = 25;

    int maxLinesPerRun = ((Number) c.placeholderMaxLinesPerRun.getValue()).intValue();
    if (maxLinesPerRun < 0) maxLinesPerRun = 0;
    if (maxLinesPerRun > 50_000) maxLinesPerRun = 50_000;

    int tooltipMaxTags = ((Number) c.placeholderTooltipMaxTags.getValue()).intValue();
    if (tooltipMaxTags < 0) tooltipMaxTags = 0;
    if (tooltipMaxTags > 500) tooltipMaxTags = 500;

    boolean historyPlaceholdersEnabledByDefault =
        c.historyPlaceholdersEnabledByDefault.isSelected();

    int maxRunsPerBatch = ((Number) c.historyPlaceholderMaxRunsPerBatch.getValue()).intValue();
    if (maxRunsPerBatch < 0) maxRunsPerBatch = 0;
    if (maxRunsPerBatch > 5_000) maxRunsPerBatch = 5_000;

    List<FilterScopeOverride> overrides = c.overridesModel.toOverrides();

    FilterSettings next =
        new FilterSettings(
            enabledByDefault,
            placeholdersEnabledByDefault,
            placeholdersCollapsedByDefault,
            previewLines,
            maxLinesPerRun,
            tooltipMaxTags,
            maxRunsPerBatch,
            historyPlaceholdersEnabledByDefault,
            prev != null ? prev.rules() : List.of(),
            overrides);

    if (Objects.equals(prev, next)) {
      return;
    }

    filterSettingsBus.set(next);
    runtimeConfig.rememberFiltersEnabledByDefault(enabledByDefault);
    runtimeConfig.rememberFilterPlaceholdersEnabledByDefault(placeholdersEnabledByDefault);
    runtimeConfig.rememberFilterPlaceholdersCollapsedByDefault(placeholdersCollapsedByDefault);
    runtimeConfig.rememberFilterPlaceholderMaxPreviewLines(previewLines);
    runtimeConfig.rememberFilterPlaceholderMaxLinesPerRun(maxLinesPerRun);
    runtimeConfig.rememberFilterPlaceholderTooltipMaxTags(tooltipMaxTags);
    runtimeConfig.rememberFilterHistoryPlaceholdersEnabledByDefault(
        historyPlaceholdersEnabledByDefault);
    runtimeConfig.rememberFilterHistoryPlaceholderMaxRunsPerBatch(maxRunsPerBatch);
    runtimeConfig.rememberFilterOverrides(overrides);

    try {
      TargetRef active = targetCoordinator.getActiveTarget();
      if (active != null) {
        transcriptRebuildService.rebuild(active);
      }
    } catch (Exception ignored) {
    }
  }
}
