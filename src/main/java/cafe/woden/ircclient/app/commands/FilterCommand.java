package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.logging.model.LogKind;
import cafe.woden.ircclient.ui.filter.FilterAction;
import cafe.woden.ircclient.ui.filter.FilterDirection;
import cafe.woden.ircclient.ui.filter.RegexSpec;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Parsed representation of the /filter command family. */
public sealed interface FilterCommand permits
    FilterCommand.Help,
    FilterCommand.ListRules,
    FilterCommand.Export,
    FilterCommand.Move,
    FilterCommand.Add,
    FilterCommand.AddReplace,
    FilterCommand.Set,
    FilterCommand.Rename,
    FilterCommand.Recreate,
    FilterCommand.Del,
    FilterCommand.Enable,
    FilterCommand.Disable,
    FilterCommand.Toggle,
    FilterCommand.Show,
    FilterCommand.Placeholders,
    FilterCommand.PlaceholdersCollapsed,
    FilterCommand.PlaceholderPreview,
    FilterCommand.Defaults,
    FilterCommand.OverrideList,
    FilterCommand.OverrideSet,
    FilterCommand.OverrideDel,
    FilterCommand.Error {

  /** For commands that accept on/off/toggle/default. */
  enum ToggleMode { ON, OFF, TOGGLE, DEFAULT }

  /** For override fields: on/off/default (where default means "inherit", i.e., null). */
  enum TriState { ON, OFF, DEFAULT }

  record Help() implements FilterCommand {}

  record ListRules(String format) implements FilterCommand {
    public ListRules {
      format = (format == null || format.isBlank()) ? "table" : format.trim();
    }
  }

  /** Export current filter configuration (rules + defaults/overrides) in a pasteable format. */
  record Export(String format, String file) implements FilterCommand {
    public Export {
      format = (format == null || format.isBlank()) ? "all" : format.trim();
      file = (file == null || file.isBlank()) ? null : file.trim();
    }
  }

  /** Reorder filter rules (evaluation is first-match-wins). */
  enum MoveMode { TO, TOP, BOTTOM, UP, DOWN, BEFORE, AFTER }

  record Move(String name, MoveMode mode, Integer positionOneBased, Integer amount, String other) implements FilterCommand {
    public Move {
      name = Objects.toString(name, "").trim();
      mode = Objects.requireNonNullElse(mode, MoveMode.TO);
      if (positionOneBased != null && positionOneBased < 1) positionOneBased = 1;
      if (amount == null || amount < 1) amount = 1;
      other = (other == null || other.isBlank()) ? null : other.trim();
    }
  }

  /**
   * Add a new filter rule.
   *
   * <p>IRCafe supports both key=value syntax and WeeChat-style positional syntax:
   * <ul>
   *   <li><code>/filter add &lt;name&gt; key=value ...</code></li>
   *   <li><code>/filter add &lt;name&gt; &lt;buffer&gt; &lt;tags&gt; &lt;regex&gt;</code></li>
   * </ul>
   */
  record Add(String name, FilterRulePatch patch) implements FilterCommand {}

  /** Add or replace an existing filter (WeeChat parity). */
  record AddReplace(String name, FilterRulePatch patch) implements FilterCommand {}

  /** Patch/update an existing rule. */
  record Set(String name, FilterRulePatch patch) implements FilterCommand {}

  /** Rename a filter. */
  record Rename(String name, String newName) implements FilterCommand {
    public Rename {
      name = Objects.toString(name, "").trim();
      newName = Objects.toString(newName, "").trim();
    }
  }

  /** Print a copy/pasteable command to edit a filter (WeeChat parity). */
  record Recreate(String name) implements FilterCommand {
    public Recreate {
      name = Objects.toString(name, "").trim();
    }
  }

  /** Delete filters by exact name and/or mask(s). */
  record Del(List<String> namesOrMasks) implements FilterCommand {
    public Del {
      namesOrMasks = (namesOrMasks == null) ? List.of() : List.copyOf(namesOrMasks);
    }
  }

  /** Enable filters by exact name and/or mask(s). */
  record Enable(List<String> namesOrMasks) implements FilterCommand {
    public Enable {
      namesOrMasks = (namesOrMasks == null) ? List.of() : List.copyOf(namesOrMasks);
    }
  }

  /** Disable filters by exact name and/or mask(s). */
  record Disable(List<String> namesOrMasks) implements FilterCommand {
    public Disable {
      namesOrMasks = (namesOrMasks == null) ? List.of() : List.copyOf(namesOrMasks);
    }
  }

  /** Toggle filters by exact name and/or mask(s). */
  record Toggle(List<String> namesOrMasks) implements FilterCommand {
    public Toggle {
      namesOrMasks = (namesOrMasks == null) ? List.of() : List.copyOf(namesOrMasks);
    }
  }

  /**
   * Toggle "show filtered lines" for a scope.
   *
   * <p>Mode semantics:
   * <ul>
   *   <li>ON  = show filtered lines (disable filtering)</li>
   *   <li>OFF = hide filtered lines (enable filtering)</li>
   *   <li>TOGGLE = invert current effective state for the active buffer</li>
   *   <li>DEFAULT = clear override (inherit)</li>
   * </ul>
   *
   * <p>If scopePattern is null/blank, it means "active buffer exact scope".
   */
  record Show(ToggleMode mode, String scopePattern) implements FilterCommand {
    public Show {
      mode = Objects.requireNonNullElse(mode, ToggleMode.TOGGLE);
      scopePattern = (scopePattern == null || scopePattern.isBlank()) ? null : scopePattern.trim();
    }
  }

  /** Toggle "Filtered (N)" placeholders for a scope (or active buffer if scope omitted). */
  record Placeholders(ToggleMode mode, String scopePattern) implements FilterCommand {
    public Placeholders {
      mode = Objects.requireNonNullElse(mode, ToggleMode.TOGGLE);
      scopePattern = (scopePattern == null || scopePattern.isBlank()) ? null : scopePattern.trim();
    }
  }

  /** Toggle whether placeholders start collapsed for a scope (or active buffer if scope omitted). */
  record PlaceholdersCollapsed(ToggleMode mode, String scopePattern) implements FilterCommand {
    public PlaceholdersCollapsed {
      mode = Objects.requireNonNullElse(mode, ToggleMode.TOGGLE);
      scopePattern = (scopePattern == null || scopePattern.isBlank()) ? null : scopePattern.trim();
    }
  }

  /** Set the global maximum number of preview lines stored per placeholder (0..25). */
  record PlaceholderPreview(int maxLines) implements FilterCommand {
    public PlaceholderPreview {
      if (maxLines < 0) maxLines = 0;
      if (maxLines > 25) maxLines = 25;
    }
  }

  /**
   * Update global defaults.
   *
   * <p>Any field that is not specified is left unchanged.
   */
  record Defaults(
      Boolean filtersEnabledByDefault, boolean filtersSpecified,
      Boolean placeholdersEnabledByDefault, boolean placeholdersSpecified,
      Boolean placeholdersCollapsedByDefault, boolean collapsedSpecified,
      Integer placeholderMaxPreviewLines, boolean previewSpecified
  ) implements FilterCommand {
    public Defaults {
      if (!previewSpecified) {
        placeholderMaxPreviewLines = null;
      } else {
        int v = (placeholderMaxPreviewLines == null) ? 0 : placeholderMaxPreviewLines;
        if (v < 0) v = 0;
        if (v > 25) v = 25;
        placeholderMaxPreviewLines = v;
      }
    }
  }

  record OverrideList(String format) implements FilterCommand {
    public OverrideList {
      format = (format == null || format.isBlank()) ? "table" : format.trim();
    }
  }

  /**
   * Upserts a scope override.
   *
   * <p>Unspecified fields keep existing values (if any). TriState.DEFAULT clears that field (null).
   */
  record OverrideSet(
      String scopePattern,
      TriState filtersEnabled, boolean filtersSpecified,
      TriState placeholdersEnabled, boolean placeholdersSpecified,
      TriState placeholdersCollapsed, boolean collapsedSpecified
  ) implements FilterCommand {
    public OverrideSet {
      scopePattern = Objects.toString(scopePattern, "*").trim();
      if (scopePattern.isBlank()) scopePattern = "*";
      filtersEnabled = Objects.requireNonNullElse(filtersEnabled, TriState.DEFAULT);
      placeholdersEnabled = Objects.requireNonNullElse(placeholdersEnabled, TriState.DEFAULT);
      placeholdersCollapsed = Objects.requireNonNullElse(placeholdersCollapsed, TriState.DEFAULT);
    }
  }

  record OverrideDel(String scopePattern) implements FilterCommand {
    public OverrideDel {
      scopePattern = Objects.toString(scopePattern, "*").trim();
      if (scopePattern.isBlank()) scopePattern = "*";
    }
  }

  record Error(String message) implements FilterCommand {
    public Error {
      message = (message == null) ? "" : message.trim();
    }
  }

  /**
   * Patch-like update for a {@link cafe.woden.ircclient.ui.filter.FilterRule}.
   *
   * <p>We need explicit "specified" flags so users can clear a field (e.g., {@code kind=} to mean
   * "any kind").
   */
  record FilterRulePatch(
      String scope,
      boolean scopeSpecified,
      Boolean enabled,
      boolean enabledSpecified,
      FilterAction action,
      boolean actionSpecified,
      FilterDirection direction,
      boolean directionSpecified,
      EnumSet<LogKind> kinds,
      boolean kindsSpecified,
      List<String> from,
      boolean fromSpecified,
      String tagsExpr,
      boolean tagsSpecified,
      RegexSpec textRegex,
      boolean textSpecified
  ) {
    public FilterRulePatch {
      scope = (scope == null) ? "" : scope.trim();
      from = (from == null) ? List.of() : List.copyOf(from);
      kinds = (kinds == null) ? EnumSet.noneOf(LogKind.class) : EnumSet.copyOf(kinds);
      tagsExpr = Objects.toString(tagsExpr, "").trim();
    }

    public static FilterRulePatch empty() {
      return new FilterRulePatch(
          "", false,
          null, false,
          null, false,
          null, false,
          EnumSet.noneOf(LogKind.class), false,
          List.of(), false,
          "", false,
          null, false
      );
    }
  }
}
