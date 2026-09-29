package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.FilterSettingsConfigPort;
import cafe.woden.ircclient.config.api.FilterSettingsConfigPort.FilterSettingsSnapshot;
import cafe.woden.ircclient.config.properties.FilterRuleProperties;
import cafe.woden.ircclient.config.properties.FilterScopeOverrideProperties;
import cafe.woden.ircclient.config.properties.UiProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.FilterDirection;
import cafe.woden.ircclient.model.FilterPlaceholderRanges;
import cafe.woden.ircclient.model.FilterRule;
import cafe.woden.ircclient.model.FilterScopeOverride;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.RegexFlag;
import cafe.woden.ircclient.model.RegexSpec;
import cafe.woden.ircclient.model.TagSpec;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Secondary adapter for filter runtime settings backed by {@link RuntimeConfigStore} and bound UI
 * properties.
 */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigFilterSettingsAdapter implements FilterSettingsConfigPort {
  private final RuntimeConfigStore runtimeConfig;
  private final UiProperties uiProperties;

  public RuntimeConfigFilterSettingsAdapter(
      RuntimeConfigStore runtimeConfig, UiProperties uiProperties) {
    this.runtimeConfig = runtimeConfig;
    this.uiProperties = uiProperties;
  }

  @Override
  public FilterSettingsSnapshot readFilterSettings() {
    UiProperties.Filters filters = uiProperties != null ? uiProperties.filters() : null;
    return new FilterSettingsSnapshot(
        filters != null && filters.enabledByDefault() != null
            ? Boolean.TRUE.equals(filters.enabledByDefault())
            : true,
        filters != null && filters.placeholdersEnabledByDefault() != null
            ? Boolean.TRUE.equals(filters.placeholdersEnabledByDefault())
            : true,
        filters != null && filters.placeholdersCollapsedByDefault() != null
            ? Boolean.TRUE.equals(filters.placeholdersCollapsedByDefault())
            : true,
        filters != null
            ? filters.placeholderMaxPreviewLines()
            : FilterPlaceholderRanges.DEFAULT_MAX_PREVIEW_LINES,
        filters != null
            ? filters.placeholderMaxLinesPerRun()
            : FilterPlaceholderRanges.DEFAULT_MAX_LINES_PER_RUN,
        filters != null
            ? filters.placeholderTooltipMaxTags()
            : FilterPlaceholderRanges.DEFAULT_TOOLTIP_MAX_TAGS,
        filters != null
            ? filters.historyPlaceholderMaxRunsPerBatch()
            : FilterPlaceholderRanges.DEFAULT_HISTORY_MAX_RUNS_PER_BATCH,
        filters != null && filters.historyPlaceholdersEnabledByDefault() != null
            ? Boolean.TRUE.equals(filters.historyPlaceholdersEnabledByDefault())
            : true,
        readRules(filters),
        readOverrides(filters));
  }

  @Override
  public void rememberFiltersEnabledByDefault(boolean enabled) {
    runtimeConfig.rememberFiltersEnabledByDefault(enabled);
  }

  @Override
  public void rememberFilterPlaceholdersEnabledByDefault(boolean enabled) {
    runtimeConfig.rememberFilterPlaceholdersEnabledByDefault(enabled);
  }

  @Override
  public void rememberFilterPlaceholdersCollapsedByDefault(boolean collapsed) {
    runtimeConfig.rememberFilterPlaceholdersCollapsedByDefault(collapsed);
  }

  @Override
  public void rememberFilterPlaceholderMaxPreviewLines(int maxLines) {
    runtimeConfig.rememberFilterPlaceholderMaxPreviewLines(maxLines);
  }

  @Override
  public void rememberFilterPlaceholderMaxLinesPerRun(int maxLines) {
    runtimeConfig.rememberFilterPlaceholderMaxLinesPerRun(maxLines);
  }

  @Override
  public void rememberFilterPlaceholderTooltipMaxTags(int maxTags) {
    runtimeConfig.rememberFilterPlaceholderTooltipMaxTags(maxTags);
  }

  @Override
  public void rememberFilterHistoryPlaceholderMaxRunsPerBatch(int maxRuns) {
    runtimeConfig.rememberFilterHistoryPlaceholderMaxRunsPerBatch(maxRuns);
  }

  @Override
  public void rememberFilterHistoryPlaceholdersEnabledByDefault(boolean enabled) {
    runtimeConfig.rememberFilterHistoryPlaceholdersEnabledByDefault(enabled);
  }

  @Override
  public void rememberFilterRules(List<FilterRule> rules) {
    runtimeConfig.rememberFilterRules(rules);
  }

  @Override
  public void rememberFilterOverrides(List<FilterScopeOverride> overrides) {
    runtimeConfig.rememberFilterOverrides(overrides);
  }

  private static List<FilterRule> readRules(UiProperties.Filters filters) {
    if (filters == null || filters.rules() == null) {
      return List.of();
    }
    return filters.rules().stream()
        .filter(Objects::nonNull)
        .map(RuntimeConfigFilterSettingsAdapter::toRule)
        .toList();
  }

  private static List<FilterScopeOverride> readOverrides(UiProperties.Filters filters) {
    if (filters == null || filters.overrides() == null) {
      return List.of();
    }
    return filters.overrides().stream()
        .filter(Objects::nonNull)
        .map(RuntimeConfigFilterSettingsAdapter::toOverride)
        .toList();
  }

  private static FilterRule toRule(FilterRuleProperties properties) {
    String name = properties != null ? Objects.toString(properties.name(), "").trim() : "";
    boolean enabled =
        properties != null
            && (properties.enabled() == null || Boolean.TRUE.equals(properties.enabled()));

    String scope = properties != null ? Objects.toString(properties.scope(), "*").trim() : "*";
    if (scope.isBlank()) scope = "*";

    FilterAction action =
        properties != null && properties.action() != null ? properties.action() : FilterAction.HIDE;
    FilterDirection direction =
        properties != null && properties.dir() != null ? properties.dir() : FilterDirection.ANY;

    EnumSet<LogKind> kinds = EnumSet.noneOf(LogKind.class);
    if (properties != null && properties.kinds() != null && !properties.kinds().isEmpty()) {
      kinds = EnumSet.copyOf(properties.kinds());
    }

    List<String> from =
        properties != null && properties.from() != null
            ? List.copyOf(properties.from())
            : List.of();

    RegexSpec regex = null;
    if (properties != null && properties.text() != null) {
      String pattern = Objects.toString(properties.text().pattern(), "").trim();
      if (!pattern.isBlank()) {
        regex = new RegexSpec(pattern, parseFlags(properties.text().flags()));
      }
    }

    return new FilterRule(
        null,
        name,
        enabled,
        scope,
        action,
        direction,
        kinds,
        from,
        regex,
        TagSpec.parse(Objects.toString(properties != null ? properties.tags() : "", "")));
  }

  private static FilterScopeOverride toOverride(FilterScopeOverrideProperties properties) {
    if (properties == null) {
      return new FilterScopeOverride("*", null, null, null);
    }
    return new FilterScopeOverride(
        Objects.toString(properties.scope(), "*").trim(),
        properties.filtersEnabled(),
        properties.placeholdersEnabled(),
        properties.placeholdersCollapsed());
  }

  private static EnumSet<RegexFlag> parseFlags(String flags) {
    EnumSet<RegexFlag> out = EnumSet.noneOf(RegexFlag.class);
    String normalized = Objects.toString(flags, "").trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if (c == 'i') out.add(RegexFlag.I);
      if (c == 'm') out.add(RegexFlag.M);
      if (c == 's') out.add(RegexFlag.S);
    }
    return out;
  }
}
