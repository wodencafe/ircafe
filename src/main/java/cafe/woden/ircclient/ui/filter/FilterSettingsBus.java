package cafe.woden.ircclient.ui.filter;

import cafe.woden.ircclient.config.FilterRuleProperties;
import cafe.woden.ircclient.config.FilterScopeOverrideProperties;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.logging.model.LogKind;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Publishes the current filter settings snapshot.
 */
@Component
@Lazy
public class FilterSettingsBus {

  public static final String PROP_FILTER_SETTINGS = "filterSettings";

  private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
  private volatile FilterSettings current;

  public FilterSettingsBus(UiProperties props) {
    UiProperties.Filters f = (props != null) ? props.filters() : null;

    boolean filtersEnabledByDefault = f == null || f.enabledByDefault() == null || Boolean.TRUE.equals(f.enabledByDefault());
    boolean placeholdersEnabledByDefault = f == null || f.placeholdersEnabledByDefault() == null || Boolean.TRUE.equals(f.placeholdersEnabledByDefault());
    boolean placeholdersCollapsedByDefault = f == null || f.placeholdersCollapsedByDefault() == null || Boolean.TRUE.equals(f.placeholdersCollapsedByDefault());

    int placeholderMaxPreviewLines = 3;
    if (f != null && f.placeholderMaxPreviewLines() != null) {
      placeholderMaxPreviewLines = f.placeholderMaxPreviewLines();
    }

int placeholderMaxLinesPerRun = 250;
if (f != null && f.placeholderMaxLinesPerRun() != null) {
  placeholderMaxLinesPerRun = f.placeholderMaxLinesPerRun();
}

int placeholderTooltipMaxTags = 12;
if (f != null && f.placeholderTooltipMaxTags() != null) {
  placeholderTooltipMaxTags = f.placeholderTooltipMaxTags();
}

int historyPlaceholderMaxRunsPerBatch = 10;
if (f != null && f.historyPlaceholderMaxRunsPerBatch() != null) {
  historyPlaceholderMaxRunsPerBatch = f.historyPlaceholderMaxRunsPerBatch();
}

boolean historyPlaceholdersEnabledByDefault = true;
if (f != null && f.historyPlaceholdersEnabledByDefault() != null) {
  historyPlaceholdersEnabledByDefault = Boolean.TRUE.equals(f.historyPlaceholdersEnabledByDefault());
}

    List<FilterRule> rules = List.of();
    if (f != null && f.rules() != null) {
      rules = f.rules().stream()
          .filter(Objects::nonNull)
          .map(FilterSettingsBus::toRule)
          .toList();
    }

    List<FilterScopeOverride> overrides = List.of();
    if (f != null && f.overrides() != null) {
      overrides = f.overrides().stream()
          .filter(Objects::nonNull)
          .map(FilterSettingsBus::toOverride)
          .toList();
    }

    this.current = new FilterSettings(
        filtersEnabledByDefault,
        placeholdersEnabledByDefault,
        placeholdersCollapsedByDefault,
        placeholderMaxPreviewLines,
        placeholderMaxLinesPerRun,
        placeholderTooltipMaxTags,
        historyPlaceholderMaxRunsPerBatch,
        historyPlaceholdersEnabledByDefault,
        rules,
        overrides);
  }

  private static FilterRule toRule(FilterRuleProperties p) {
    String name = p != null ? Objects.toString(p.name(), "").trim() : "";
    boolean enabled = p != null && (p.enabled() == null || Boolean.TRUE.equals(p.enabled()));

    String scope = (p != null) ? Objects.toString(p.scope(), "*").trim() : "*";
    if (scope.isBlank()) scope = "*";

    FilterAction action = (p != null && p.action() != null) ? p.action() : FilterAction.HIDE;
    FilterDirection dir = (p != null && p.dir() != null) ? p.dir() : FilterDirection.ANY;

    EnumSet<LogKind> kinds = EnumSet.noneOf(LogKind.class);
    if (p != null && p.kinds() != null && !p.kinds().isEmpty()) {
      kinds = EnumSet.copyOf(p.kinds());
    }

    List<String> from = (p != null && p.from() != null) ? List.copyOf(p.from()) : List.of();

    RegexSpec regex = null;
    if (p != null && p.text() != null) {
      String pattern = Objects.toString(p.text().pattern(), "").trim();
      if (!pattern.isBlank()) {
        regex = new RegexSpec(pattern, parseFlags(p.text().flags()));
      }
    }

    return new FilterRule(null, name, enabled, scope, action, dir, kinds, from, regex, TagSpec.parse(Objects.toString(p.tags(), "")));
  }

  private static FilterScopeOverride toOverride(FilterScopeOverrideProperties p) {
    if (p == null) return new FilterScopeOverride("*", null, null, null);
    return new FilterScopeOverride(
        Objects.toString(p.scope(), "*").trim(),
        p.filtersEnabled(),
        p.placeholdersEnabled(),
        p.placeholdersCollapsed());
  }

  private static EnumSet<RegexFlag> parseFlags(String flags) {
    EnumSet<RegexFlag> out = EnumSet.noneOf(RegexFlag.class);
    String f = Objects.toString(flags, "").trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < f.length(); i++) {
      char c = f.charAt(i);
      if (c == 'i') out.add(RegexFlag.I);
      if (c == 'm') out.add(RegexFlag.M);
      if (c == 's') out.add(RegexFlag.S);
    }
    return out;
  }

  public FilterSettings get() {
    return current;
  }

  public void set(FilterSettings next) {
    FilterSettings prev = this.current;
    this.current = next;
    pcs.firePropertyChange(PROP_FILTER_SETTINGS, prev, next);
  }

  public void refresh() {
    FilterSettings cur = this.current;
    pcs.firePropertyChange(PROP_FILTER_SETTINGS, cur, cur);
  }

  public void addListener(PropertyChangeListener l) {
    pcs.addPropertyChangeListener(l);
  }

  public void removeListener(PropertyChangeListener l) {
    pcs.removePropertyChangeListener(l);
  }
}
