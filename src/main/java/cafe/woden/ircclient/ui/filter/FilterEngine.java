package cafe.woden.ircclient.ui.filter;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Evaluates filter rules against incoming transcript lines.
 *
 * <p>Filters are UI-only: they never delete or prevent logging.
 */
@Component
public class FilterEngine implements PropertyChangeListener {

  private static final Logger log = LoggerFactory.getLogger(FilterEngine.class);

  private final FilterSettingsBus bus;
  private final AtomicReference<Compiled> compiled;

  /** Resolved, per-target effective settings (after applying scope overrides). */
  public record Effective(
      boolean filtersEnabled,
      boolean placeholdersEnabled,
      boolean placeholdersCollapsed,
      int placeholderMaxPreviewLines,
      int placeholderMaxLinesPerRun,
      int placeholderTooltipMaxTags,
      int historyPlaceholderMaxRunsPerBatch,
      boolean historyPlaceholdersEnabled
  ) {
  }

  /** Details about the first rule that matched a given line. */
  public record Match(UUID ruleId, String ruleName, FilterAction action) {
    public boolean isHide() {
      return action == FilterAction.HIDE;
    }
  }

  /** A resolved boolean value plus where it came from (null => global default). */
  public record ResolvedBool(boolean value, String sourceScopePattern) {
    public boolean isFromOverride() {
      return sourceScopePattern != null;
    }

    public String sourceLabel() {
      return (sourceScopePattern == null) ? "default" : sourceScopePattern;
    }
  }

  /** Resolved, per-target effective settings plus per-field sources. */
  public record EffectiveResolved(
      ResolvedBool filtersEnabled,
      ResolvedBool placeholdersEnabled,
      ResolvedBool placeholdersCollapsed,
      int placeholderMaxPreviewLines,
      int placeholderMaxLinesPerRun,
      int placeholderTooltipMaxTags,
      int historyPlaceholderMaxRunsPerBatch,
      boolean historyPlaceholdersEnabled
  ) {
  }

  public FilterEngine(FilterSettingsBus bus) {
    this.bus = bus;
    this.compiled = new AtomicReference<>(compile(bus != null ? bus.get() : null));
    if (this.bus != null) {
      this.bus.addListener(this);
    }
  }

  /**
   * Returns true when the line should be hidden from the transcript.
   *
   * <p>This method is defensive: it never throws.
   */
  public boolean shouldHide(FilterContext ctx) {
    if (ctx == null) return false;
    try {
      Compiled c = compiled.get();
      if (c == null) return false;
      String key = ctx.bufferKey();
      if (!c.filtersEnabledFor(key)) return false;

      for (CompiledRule r : c.rules) {
        if (r == null) continue;
        if (!r.matches(ctx)) continue;
        // v1.0 only supports HIDE.
        return r.action == FilterAction.HIDE;
      }
    } catch (Exception e) {
      log.debug("FilterEngine.shouldHide failed; allowing line", e);
    }
    return false;
  }

  /** Returns the first matching rule for a line, or null if none match (or filtering is disabled). */
  public Match firstMatch(FilterContext ctx) {
    if (ctx == null) return null;
    try {
      Compiled c = compiled.get();
      if (c == null) return null;
      String key = ctx.bufferKey();
      if (!c.filtersEnabledFor(key)) return null;

      for (CompiledRule r : c.rules) {
        if (r == null) continue;
        if (!r.matches(ctx)) continue;
        return new Match(r.id, r.name, r.action);
      }
    } catch (Exception e) {
      log.debug("FilterEngine.firstMatch failed; treating as no match", e);
    }
    return null;
  }

  /** Compute the effective settings for a specific target (including scope overrides). */
  public Effective effectiveFor(TargetRef target) {
    String key;
    try {
      // Use the same key shape as FilterContext: serverId + "/" + target.key().
      if (target == null) {
        key = "*/status";
      } else {
        key = target.serverId() + "/" + target.key();
      }
    } catch (Exception ignored) {
      key = "*/status";
    }

    try {
      Compiled c = compiled.get();
      if (c == null) {
        // Very defensive fallback.
        FilterSettings fs = (bus != null) ? bus.get() : null;
        c = compile(fs);
        compiled.set(c);
      }
      return new Effective(
          c.filtersEnabledFor(key),
          c.placeholdersEnabledFor(key),
          c.placeholdersCollapsedFor(key),
          c.placeholderMaxPreviewLines(),
          c.placeholderMaxLinesPerRun(),
          c.placeholderTooltipMaxTags(),
          c.historyPlaceholderMaxRunsPerBatch(),
          c.historyPlaceholdersEnabled()
      );
    } catch (Exception ignored) {
      return new Effective(true, true, true, 0, 250, 12, 10, true);
    }
  }

  /** Compute the effective settings for a specific target and include where each value came from. */
  public EffectiveResolved effectiveResolvedFor(TargetRef target) {
    String key;
    try {
      if (target == null) {
        key = "*/status";
      } else {
        key = target.serverId() + "/" + target.key();
      }
    } catch (Exception ignored) {
      key = "*/status";
    }

    try {
      Compiled c = compiled.get();
      if (c == null) {
        FilterSettings fs = (bus != null) ? bus.get() : null;
        c = compile(fs);
        compiled.set(c);
      }
      return new EffectiveResolved(
          c.filtersEnabledResolvedFor(key),
          c.placeholdersEnabledResolvedFor(key),
          c.placeholdersCollapsedResolvedFor(key),
          c.placeholderMaxPreviewLines(),
          c.placeholderMaxLinesPerRun(),
          c.placeholderTooltipMaxTags(),
          c.historyPlaceholderMaxRunsPerBatch(),
          c.historyPlaceholdersEnabled()
      );
    } catch (Exception ignored) {
      return new EffectiveResolved(
          new ResolvedBool(true, null),
          new ResolvedBool(true, null),
          new ResolvedBool(true, null),
          0,
          250,
          12,
          10,
          true
      );
    }
  }

  /** True when filtering is enabled for the given target. */
  public boolean filtersEnabledFor(TargetRef target) {
    return effectiveFor(target).filtersEnabled();
  }

  /** True when "Filtered (N)" placeholders are enabled for the given target. */
  public boolean placeholdersEnabledFor(TargetRef target) {
    return effectiveFor(target).placeholdersEnabled();
  }

  /** True when placeholders should start collapsed for the given target. */
  public boolean placeholdersCollapsedFor(TargetRef target) {
    return effectiveFor(target).placeholdersCollapsed();
  }

  /** True when "Filtered (N)" placeholders are enabled for the given buffer key. */
  public boolean placeholdersEnabledFor(String bufferKey) {
    try {
      Compiled c = compiled.get();
      if (c == null) return true;
      return c.placeholdersEnabledFor(bufferKey);
    } catch (Exception ignored) {
      return true;
    }
  }

  /** True when filtering is enabled for the given buffer key. */
  public boolean filtersEnabledFor(String bufferKey) {
    try {
      Compiled c = compiled.get();
      if (c == null) return true;
      return c.filtersEnabledFor(bufferKey);
    } catch (Exception ignored) {
      return true;
    }
  }

  /** True when placeholders should start collapsed for the given buffer key. */
  public boolean placeholdersCollapsedFor(String bufferKey) {
    try {
      Compiled c = compiled.get();
      if (c == null) return true;
      return c.placeholdersCollapsedFor(bufferKey);
    } catch (Exception ignored) {
      return true;
    }
  }

  /** Maximum number of preview samples a placeholder will keep (0 disables previews). */
  public int placeholderMaxPreviewLines() {
    try {
      Compiled c = compiled.get();
      if (c == null) return 0;
      return c.placeholderMaxPreviewLines();
    } catch (Exception ignored) {
      return 0;
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt == null) return;
    if (!FilterSettingsBus.PROP_FILTER_SETTINGS.equals(evt.getPropertyName())) return;
    try {
      FilterSettings next = (evt.getNewValue() instanceof FilterSettings fs) ? fs : bus.get();
      compiled.set(compile(next));
    } catch (Exception e) {
      log.warn("Failed to recompile filter settings; keeping prior snapshot", e);
    }
  }

  private static Compiled compile(FilterSettings s) {
    FilterSettings cur = (s != null) ? s : FilterSettings.defaults();

    List<CompiledOverride> overrides = new ArrayList<>();
    for (FilterScopeOverride o : cur.overrides()) {
      if (o == null) continue;
      String pat = normalizeScopePattern(o.scopePattern());
      Pattern p = compileGlobSafe(pat);
      overrides.add(new CompiledOverride(o, pat, p, specificityScore(pat)));
    }

    // Sort overrides so "more specific" wins when multiple match.
    overrides.sort(Comparator.comparingInt((CompiledOverride o) -> o.specificity).reversed());

    List<CompiledRule> rules = new ArrayList<>();
    for (FilterRule r : cur.rules()) {
      if (r == null) continue;
      rules.add(compileRule(r));
    }

    return new Compiled(
        cur.filtersEnabledByDefault(),
        cur.placeholdersEnabledByDefault(),
        cur.placeholdersCollapsedByDefault(),
        cur.placeholderMaxPreviewLines(),
        cur.placeholderMaxLinesPerRun(),
        cur.placeholderTooltipMaxTags(),
        cur.historyPlaceholderMaxRunsPerBatch(),
        cur.historyPlaceholdersEnabledByDefault(),
        overrides,
        rules
    );
  }

  private static CompiledRule compileRule(FilterRule r) {
    String scope = normalizeScopePattern(r.scopePattern());
    Pattern scopePattern = compileGlobSafe(scope);

    List<Pattern> from = new ArrayList<>();
    for (String g : r.fromNickGlobs()) {
      String gg = Objects.toString(g, "").trim();
      if (gg.isBlank()) continue;
      from.add(compileGlobSafe(gg));
    }

    Pattern text = null;
    if (r.textRegex() != null && !r.textRegex().isEmpty()) {
      text = compileRegexSafe(r.textRegex());
    }

    EnumSet<LogKind> kinds = (r.kinds() == null) ? EnumSet.noneOf(LogKind.class) : EnumSet.copyOf(r.kinds());

    TagSpec tags = (r.tags() == null) ? TagSpec.empty() : r.tags();

    return new CompiledRule(
        r.id(),
        r.name(),
        r.nameKey(),
        r.enabled(),
        scopePattern,
        r.action() != null ? r.action() : FilterAction.HIDE,
        r.direction() != null ? r.direction() : FilterDirection.ANY,
        kinds,
        from,
        tags,
        text
    );
}

  private static Pattern compileRegexSafe(RegexSpec spec) {
    if (spec == null || spec.isEmpty()) return null;
    int flags = 0;
    if (spec.flags() != null) {
      if (spec.flags().contains(RegexFlag.I)) flags |= Pattern.CASE_INSENSITIVE;
      if (spec.flags().contains(RegexFlag.M)) flags |= Pattern.MULTILINE;
      if (spec.flags().contains(RegexFlag.S)) flags |= Pattern.DOTALL;
    }
    try {
      return Pattern.compile(spec.pattern(), flags);
    } catch (PatternSyntaxException e) {
      log.warn("Invalid regex pattern in filter: {}", spec.pattern());
      return null;
    }
  }

  private static Pattern compileGlobSafe(String glob) {
    String g = Objects.toString(glob, "*").trim();
    if (g.isBlank()) g = "*";

    // Convert glob -> regex.
    StringBuilder rx = new StringBuilder(g.length() * 2);
    rx.append('^');
    for (int i = 0; i < g.length(); i++) {
      char c = g.charAt(i);
      switch (c) {
        case '*':
          rx.append(".*");
          break;
        case '?':
          rx.append('.');
          break;
        case '.', '(', ')', '[', ']', '{', '}', '+', '$', '^', '|', '\\':
          rx.append('\\').append(c);
          break;
        default:
          rx.append(c);
      }
    }
    rx.append('$');
    try {
      return Pattern.compile(rx.toString(), Pattern.CASE_INSENSITIVE);
    } catch (Exception e) {
      // Should be impossible, but stay defensive.
      log.debug("Failed to compile glob regex for '{}'", glob, e);
      return Pattern.compile("^.*$", Pattern.CASE_INSENSITIVE);
    }
  }

  /**
   * Normalizes scope patterns so users can type shorthand.
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code libera} => {@code libera/*}</li>
   *   <li>{@code #llamas} => {@code *}{@code /#llamas}</li>
   *   <li>{@code status} => {@code *}{@code /status}</li>
   * </ul>
   */
  public static String normalizeScopePattern(String raw) {
    String s = Objects.toString(raw, "*").trim();
    if (s.isEmpty()) return "*";
    if (s.equals("*")) return "*";

    // Shorthand forms:
    // - serverId -> serverId/*
    // - #chan / &chan / @nick -> */#chan
    // - status -> */status
    if (!s.contains("/")) {
      String sl = s.toLowerCase(Locale.ROOT);
      if (sl.equals("status")) return "*/status";
      if (s.startsWith("#") || s.startsWith("&") || s.startsWith("@")) {
        return "*/" + s;
      }
      return s + "/*";
    }

    // If the right side is a channel but user forgot the leading slash normalization, keep it.
    return s;
  }

  private static int specificityScore(String pat) {
    // Higher = more specific. Treat wildcards as low information.
    if (pat == null) return 0;
    int score = 0;
    for (int i = 0; i < pat.length(); i++) {
      char c = pat.charAt(i);
      if (c == '*' || c == '?') continue;
      score++;
    }
    return score;
  }

  private record Compiled(boolean enabledByDefault,
                          boolean placeholdersEnabledByDefault,
                          boolean placeholdersCollapsedByDefault,
                          int placeholderMaxPreviewLines,
                          int placeholderMaxLinesPerRun,
                          int placeholderTooltipMaxTags,
                          int historyPlaceholderMaxRunsPerBatch,
                          boolean historyPlaceholdersEnabled,
                          List<CompiledOverride> overrides,
                          List<CompiledRule> rules) {
    boolean filtersEnabledFor(String bufferKey) {
      boolean base = enabledByDefault;
      if (overrides == null || overrides.isEmpty()) return base;
      for (CompiledOverride o : overrides) {
        if (o == null || o.pattern == null) continue;
        if (!o.pattern.matcher(bufferKey).matches()) continue;
        Boolean v = o.override.filtersEnabled();
        if (v != null) return v;
      }
      return base;
    }

    ResolvedBool filtersEnabledResolvedFor(String bufferKey) {
      boolean base = enabledByDefault;
      if (overrides == null || overrides.isEmpty()) return new ResolvedBool(base, null);
      for (CompiledOverride o : overrides) {
        if (o == null || o.pattern == null) continue;
        if (!o.pattern.matcher(bufferKey).matches()) continue;
        Boolean v = o.override.filtersEnabled();
        if (v != null) return new ResolvedBool(v, o.scopePattern);
      }
      return new ResolvedBool(base, null);
    }

    boolean placeholdersEnabledFor(String bufferKey) {
      boolean base = placeholdersEnabledByDefault;
      if (overrides == null || overrides.isEmpty()) return base;
      for (CompiledOverride o : overrides) {
        if (o == null || o.pattern == null) continue;
        if (!o.pattern.matcher(bufferKey).matches()) continue;
        Boolean v = o.override.placeholdersEnabled();
        if (v != null) return v;
      }
      return base;
    }

    ResolvedBool placeholdersEnabledResolvedFor(String bufferKey) {
      boolean base = placeholdersEnabledByDefault;
      if (overrides == null || overrides.isEmpty()) return new ResolvedBool(base, null);
      for (CompiledOverride o : overrides) {
        if (o == null || o.pattern == null) continue;
        if (!o.pattern.matcher(bufferKey).matches()) continue;
        Boolean v = o.override.placeholdersEnabled();
        if (v != null) return new ResolvedBool(v, o.scopePattern);
      }
      return new ResolvedBool(base, null);
    }

    boolean placeholdersCollapsedFor(String bufferKey) {
      boolean base = placeholdersCollapsedByDefault;
      if (overrides == null || overrides.isEmpty()) return base;
      for (CompiledOverride o : overrides) {
        if (o == null || o.pattern == null) continue;
        if (!o.pattern.matcher(bufferKey).matches()) continue;
        Boolean v = o.override.placeholdersCollapsed();
        if (v != null) return v;
      }
      return base;
    }

    ResolvedBool placeholdersCollapsedResolvedFor(String bufferKey) {
      boolean base = placeholdersCollapsedByDefault;
      if (overrides == null || overrides.isEmpty()) return new ResolvedBool(base, null);
      for (CompiledOverride o : overrides) {
        if (o == null || o.pattern == null) continue;
        if (!o.pattern.matcher(bufferKey).matches()) continue;
        Boolean v = o.override.placeholdersCollapsed();
        if (v != null) return new ResolvedBool(v, o.scopePattern);
      }
      return new ResolvedBool(base, null);
    }
  }

  private record CompiledOverride(FilterScopeOverride override,
                                  String scopePattern,
                                  Pattern pattern,
                                  int specificity) {
  }


  private static final class CompiledRule {
    private final UUID id;
    private final String name;
    private final String nameKey;
    private final boolean enabled;
    private final Pattern scope;
    private final FilterAction action;
    private final FilterDirection direction;
    private final EnumSet<LogKind> kinds;
    private final List<Pattern> from;
    private final TagSpec tags;
    private final Pattern text;

    private CompiledRule(
        UUID id,
        String name,
        String nameKey,
        boolean enabled,
        Pattern scope,
        FilterAction action,
        FilterDirection direction,
        EnumSet<LogKind> kinds,
        List<Pattern> from,
        TagSpec tags,
        Pattern text
    ) {
      this.id = id;
      this.name = Objects.toString(name, "");
      this.nameKey = Objects.toString(nameKey, "");
      this.enabled = enabled;
      this.scope = scope;
      this.action = action;
      this.direction = direction;
      this.kinds = kinds;
      this.from = (from == null) ? List.of() : List.copyOf(from);
      this.tags = (tags == null) ? TagSpec.empty() : tags;
      this.text = text;
    }

    boolean matches(FilterContext ctx) {
      if (!enabled) return false;
      if (scope != null && !scope.matcher(ctx.bufferKey()).matches()) return false;

      if (direction != null && direction != FilterDirection.ANY) {
        LogDirection d = ctx.direction();
        if (direction == FilterDirection.IN && d != LogDirection.IN) return false;
        if (direction == FilterDirection.OUT && d != LogDirection.OUT) return false;
      }

      if (kinds != null && !kinds.isEmpty()) {
        if (!kinds.contains(ctx.kind())) return false;
      }

      if (from != null && !from.isEmpty()) {
        String f = Objects.toString(ctx.fromNick(), "");
        if (f.isBlank()) return false;
        boolean any = false;
        for (Pattern p : from) {
          if (p == null) continue;
          if (p.matcher(f).matches()) {
            any = true;
            break;
          }
        }
        if (!any) return false;
      }

      if (tags != null && !tags.isEmpty()) {
        if (!tags.matches(ctx.tags())) return false;
      }

      if (text != null) {
        String t = Objects.toString(ctx.text(), "");
        if (!text.matcher(t).find()) return false;
      }

      return true;
    }
  }
}
