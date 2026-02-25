package cafe.woden.ircclient.ui.filter;

import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.commands.FilterCommand;
import cafe.woden.ircclient.app.outbound.LocalFilterCommandHandler;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.FilterDirection;
import cafe.woden.ircclient.model.FilterRule;
import cafe.woden.ircclient.model.FilterScopeOverride;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.RegexFlag;
import cafe.woden.ircclient.model.RegexSpec;
import cafe.woden.ircclient.model.TagSpec;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/**
 * Handles local /filter commands.
 *
 * <p>This is intentionally "local" (no IRC traffic). It updates the in-memory FilterSettingsBus and
 * persists changes to runtime config.
 */
@Component
public class LocalFilterCommandService implements LocalFilterCommandHandler {

  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final FilterSettingsBus filterSettingsBus;
  private final FilterEngine filterEngine;
  private final RuntimeConfigStore runtimeConfig;
  private final TranscriptRebuildService rebuildService;

  public LocalFilterCommandService(
      UiPort ui,
      TargetCoordinator targetCoordinator,
      FilterSettingsBus filterSettingsBus,
      FilterEngine filterEngine,
      RuntimeConfigStore runtimeConfig,
      TranscriptRebuildService rebuildService) {

    this.ui = ui;
    this.targetCoordinator = targetCoordinator;
    this.filterSettingsBus = filterSettingsBus;
    this.filterEngine = filterEngine;
    this.runtimeConfig = runtimeConfig;
    this.rebuildService = rebuildService;
  }

  @Override
  public void handle(FilterCommand cmd) {
    TargetRef out = outputTarget();
    if (cmd == null) {
      ui.appendStatus(out, "(filter)", "Usage: /filter help");
      return;
    }

    switch (cmd) {
      case FilterCommand.Help ignored -> printHelp(out);
      case FilterCommand.Export ex -> export(out, ex);
      case FilterCommand.Move mv -> move(out, mv);
      case FilterCommand.ListRules list -> list(out, list.format());
      case FilterCommand.Show show -> handleShow(out, show);
      case FilterCommand.Placeholders ph -> handlePlaceholders(out, ph);
      case FilterCommand.PlaceholdersCollapsed pc -> handlePlaceholdersCollapsed(out, pc);
      case FilterCommand.PlaceholderPreview pp -> handlePlaceholderPreview(out, pp);
      case FilterCommand.Defaults d -> handleDefaults(out, d);
      case FilterCommand.OverrideList ol -> listOverrides(out, ol.format());
      case FilterCommand.OverrideSet os -> setOverride(out, os);
      case FilterCommand.OverrideDel od -> delOverride(out, od.scopePattern());
      case FilterCommand.Add add -> add(out, add.name(), add.patch());
      case FilterCommand.AddReplace ar -> addReplace(out, ar.name(), ar.patch());
      case FilterCommand.Set set -> set(out, set.name(), set.patch());
      case FilterCommand.Rename rn -> rename(out, rn.name(), rn.newName());
      case FilterCommand.Recreate rc -> recreate(out, rc.name());
      case FilterCommand.Del del -> del(out, del.namesOrMasks());
      case FilterCommand.Enable en -> setEnabled(out, en.namesOrMasks(), true);
      case FilterCommand.Disable dis -> setEnabled(out, dis.namesOrMasks(), false);
      case FilterCommand.Toggle tog -> toggle(out, tog.namesOrMasks());
      case FilterCommand.Error err -> ui.appendError(out, "(filter)", err.message());
    }
  }

  private TargetRef outputTarget() {
    TargetRef at = targetCoordinator.getActiveTarget();
    return (at != null) ? at : targetCoordinator.safeStatusTarget();
  }

  private void printHelp(TargetRef out) {
    ui.appendStatus(
        out,
        "(filter)",
        "Filters hide lines from transcripts (they do not affect IRC traffic or logging). ");
    ui.appendStatus(out, "(filter)", "Commands:");
    ui.appendStatus(out, "(filter)", "  /filter list [format=table|cmd]");
    ui.appendStatus(out, "(filter)", "  /filter export [format=all|cmd] [file=<path>]");
    ui.appendStatus(
        out,
        "(filter)",
        "  /filter move <name> <pos|top|bottom|up [n]|down [n]|before <other>|after <other>>");
    ui.appendStatus(
        out,
        "(filter)",
        "  /filter add <name> [key=value ...]  (also WeeChat positional: /filter add <name> <buffer> <tags> <regex>)");
    ui.appendStatus(
        out,
        "(filter)",
        "  /filter addreplace <name> [key=value ...]  (also WeeChat positional form)");
    ui.appendStatus(out, "(filter)", "  /filter set <name> key=value ...");
    ui.appendStatus(out, "(filter)", "  /filter rename <old> <new>");
    ui.appendStatus(out, "(filter)", "  /filter recreate <name>");
    ui.appendStatus(
        out,
        "(filter)",
        "  /filter del <name-or-mask> [more...]  (mask supports * and ?, or re:/.../)");
    ui.appendStatus(
        out,
        "(filter)",
        "  /filter enable|disable|toggle [<name-or-mask> ...]  (no args = global; use @ for current buffer)");
    ui.appendStatus(out, "(filter)", "");
    ui.appendStatus(out, "(filter)", "Per-scope settings:");
    ui.appendStatus(out, "(filter)", "  /filter show [on|off|toggle|default] [target=<scope>]");
    ui.appendStatus(
        out, "(filter)", "  /filter placeholders [on|off|toggle|default] [target=<scope>]");
    ui.appendStatus(
        out,
        "(filter)",
        "  /filter placeholders collapsed [on|off|toggle|default] [target=<scope>]");
    ui.appendStatus(out, "(filter)", "  /filter override list [format=table|cmd]");
    ui.appendStatus(
        out,
        "(filter)",
        "  /filter override set scope=<glob> filters=on|off|default placeholders=on|off|default collapsed=on|off|default");
    ui.appendStatus(out, "(filter)", "  /filter override del scope=<glob>");
    ui.appendStatus(out, "(filter)", "");
    ui.appendStatus(out, "(filter)", "Global defaults:");
    ui.appendStatus(
        out,
        "(filter)",
        "  /filter defaults filters=on|off placeholders=on|off collapsed=on|off preview=<0..25> maxrun=<0..50000> maxtags=<0..500> maxbatch=<0..5000> history=on|off");
    ui.appendStatus(out, "(filter)", "  /filter placeholder-preview <0..25>");
    ui.appendStatus(out, "(filter)", "");
    ui.appendStatus(
        out, "(filter)", "Rule keys: scope, enabled, action, dir, kind, from, tags, text");
    ui.appendStatus(
        out,
        "(filter)",
        "Keybinds (WeeChat-style): Alt+= toggle global filtering; Alt+- toggle current buffer");
    ui.appendStatus(
        out,
        "(filter)",
        "Scope shorthand: libera => libera/* ; #llamas => */#llamas ; status => */status");
    ui.appendStatus(
        out,
        "(filter)",
        "from/text matchers: glob:* ? (or prefix glob:...), or regex via re:<pattern> or /pattern/i");
  }

  private void list(TargetRef out, String formatRaw) {
    String format = Objects.toString(formatRaw, "table").trim().toLowerCase(Locale.ROOT);
    if (format.isBlank()) format = "table";

    FilterSettings s = filterSettingsBus.get();
    List<FilterRule> rules = s != null ? s.rules() : List.of();
    if (rules.isEmpty()) {
      ui.appendStatus(out, "(filter)", "No filter rules.");
      return;
    }

    if (format.equals("cmd") || format.equals("commands")) {
      ui.appendStatus(out, "(filter)", "Filter rules (normalized commands):");
      for (FilterRule r : rules) {
        ui.appendStatus(out, "(filter)", toNormalizedAddCommand(r));
      }
      return;
    }

    // table-ish
    ui.appendStatus(out, "(filter)", "Filter rules (" + rules.size() + "): ");
    for (int i = 0; i < rules.size(); i++) {
      FilterRule r = rules.get(i);
      StringBuilder b = new StringBuilder();
      b.append(i + 1).append(") ");
      b.append(r.enabled() ? "[x] " : "[ ] ").append(r.name());
      b.append("  scope=").append(r.scopePattern());
      if (r.action() != null && r.action() != FilterAction.HIDE)
        b.append(" action=").append(r.action());
      if (r.direction() != null && r.direction() != FilterDirection.ANY)
        b.append(" dir=").append(r.direction());
      if (r.hasKinds()) b.append(" kind=").append(joinKinds(r.kinds()));
      if (r.hasFromNickGlobs()) b.append(" from=").append(String.join(",", r.fromNickGlobs()));
      if (r.hasTags()) b.append(" tags=").append(r.tags().expr());
      if (r.hasTextRegex())
        b.append(" text=/")
            .append(r.textRegex().pattern())
            .append("/")
            .append(flagsString(r.textRegex()));
      ui.appendStatus(out, "(filter)", b.toString());
    }
  }

  private void move(TargetRef out, FilterCommand.Move cmd) {
    String name = (cmd != null) ? Objects.toString(cmd.name(), "").trim() : "";
    if (name.isEmpty()) {
      ui.appendError(
          out,
          "(filter)",
          "Usage: /filter move <name> <pos|top|bottom|up [n]|down [n]|before <other>|after <other>>");
      return;
    }

    FilterSettings cur = filterSettingsBus.get();
    List<FilterRule> rules = new ArrayList<>(cur != null ? cur.rules() : List.of());

    if (rules.isEmpty()) {
      ui.appendStatus(out, "(filter)", "No filters to move.");
      return;
    }

    int fromIdx = indexOf(rules, name);
    if (fromIdx < 0) {
      ui.appendError(out, "(filter)", "No such rule: '" + name + "'");
      return;
    }

    int size = rules.size();
    int toIdx;
    FilterCommand.MoveMode mode = (cmd != null) ? cmd.mode() : FilterCommand.MoveMode.TO;

    switch (mode) {
      case TOP -> toIdx = 0;
      case BOTTOM -> toIdx = size - 1;
      case UP ->
          toIdx = Math.max(0, fromIdx - Math.max(1, Objects.requireNonNullElse(cmd.amount(), 1)));
      case DOWN ->
          toIdx =
              Math.min(
                  size - 1, fromIdx + Math.max(1, Objects.requireNonNullElse(cmd.amount(), 1)));
      case TO -> {
        Integer pos = (cmd != null) ? cmd.positionOneBased() : null;
        if (pos == null) {
          ui.appendError(out, "(filter)", "Usage: /filter move <name> <position>");
          return;
        }
        int p = Math.max(1, pos);
        p = Math.min(size, p);
        toIdx = p - 1;
      }
      case BEFORE, AFTER -> {
        String other = (cmd != null) ? Objects.toString(cmd.other(), "").trim() : "";
        if (other.isEmpty()) {
          ui.appendError(
              out,
              "(filter)",
              "Usage: /filter move <name> "
                  + (mode == FilterCommand.MoveMode.BEFORE ? "before" : "after")
                  + " <other>");
          return;
        }
        int otherIdx = indexOf(rules, other);
        if (otherIdx < 0) {
          ui.appendError(out, "(filter)", "No such rule: '" + other + "'");
          return;
        }
        if (otherIdx == fromIdx) {
          ui.appendStatus(
              out,
              "(filter)",
              "Rule '"
                  + name
                  + "' is already "
                  + (mode == FilterCommand.MoveMode.BEFORE ? "before" : "after")
                  + " itself.");
          return;
        }
        // Destination index is expressed as the final index in the full list.
        // Adjust when moving relative to a rule that is after/before us.
        if (mode == FilterCommand.MoveMode.BEFORE) {
          toIdx = (fromIdx < otherIdx) ? (otherIdx - 1) : otherIdx;
        } else {
          // AFTER
          toIdx = (fromIdx < otherIdx) ? otherIdx : (otherIdx + 1);
          if (toIdx > size - 1) toIdx = size - 1;
        }
      }
      default -> {
        ui.appendError(
            out,
            "(filter)",
            "Usage: /filter move <name> <pos|top|bottom|up [n]|down [n]|before <other>|after <other>>");
        return;
      }
    }

    // No-op.
    if (toIdx == fromIdx) {
      ui.appendStatus(
          out,
          "(filter)",
          "Rule '"
              + rules.get(fromIdx).nameKey()
              + "' is already at position "
              + (fromIdx + 1)
              + ".");
      return;
    }

    // Remove + insert at the final desired index.
    FilterRule moving = rules.remove(fromIdx);
    int insertIdx = Math.max(0, Math.min(rules.size(), toIdx));
    rules.add(insertIdx, moving);

    applyNewRules(cur, rules);
    ui.appendStatus(
        out,
        "(filter)",
        "Moved '"
            + moving.nameKey()
            + "' from "
            + (fromIdx + 1)
            + " to "
            + (insertIdx + 1)
            + " (of "
            + rules.size()
            + ").");
  }

  private void add(TargetRef out, String nameRaw, FilterCommand.FilterRulePatch patch) {
    String name = Objects.toString(nameRaw, "").trim();
    if (name.isEmpty()) {
      ui.appendError(out, "(filter)", "Usage: /filter add <name> ...");
      return;
    }

    FilterSettings cur = filterSettingsBus.get();
    List<FilterRule> rules = new ArrayList<>(cur != null ? cur.rules() : List.of());

    String key = name.toLowerCase(Locale.ROOT);
    if (rules.stream().anyMatch(r -> r != null && r.nameKey().equals(key))) {
      ui.appendError(
          out, "(filter)", "Rule already exists: '" + name + "' (use /filter set or /filter del)");
      return;
    }

    FilterRule r;
    try {
      r = buildRuleForAdd(name, patch);
    } catch (IllegalArgumentException e) {
      ui.appendError(out, "(filter)", e.getMessage());
      return;
    }

    rules.add(r);
    applyNewRules(cur, rules);
    ui.appendStatus(out, "(filter)", "Added filter rule: " + r.nameKey());
  }

  private void addReplace(TargetRef out, String nameRaw, FilterCommand.FilterRulePatch patch) {
    String name = Objects.toString(nameRaw, "").trim();
    if (name.isEmpty()) {
      ui.appendError(out, "(filter)", "Usage: /filter addreplace <name> ...");
      return;
    }

    FilterSettings cur = filterSettingsBus.get();
    List<FilterRule> rules = new ArrayList<>(cur != null ? cur.rules() : List.of());

    FilterRule r;
    try {
      r = buildRuleForAdd(name, patch);
    } catch (IllegalArgumentException e) {
      ui.appendError(out, "(filter)", e.getMessage());
      return;
    }

    int idx = indexOf(rules, r.nameKey());
    if (idx >= 0) {
      FilterRule prev = rules.get(idx);
      // keep stable id, replace everything else
      FilterRule replacement =
          new FilterRule(
              prev.id(),
              r.name(),
              r.enabled(),
              r.scopePattern(),
              r.action(),
              r.direction(),
              r.kinds(),
              r.fromNickGlobs(),
              r.textRegex(),
              r.tags());
      rules.set(idx, replacement);
      applyNewRules(cur, rules);
      ui.appendStatus(out, "(filter)", "Replaced filter rule: " + replacement.nameKey());
      return;
    }

    rules.add(r);
    applyNewRules(cur, rules);
    ui.appendStatus(out, "(filter)", "Added filter rule: " + r.nameKey());
  }

  private void rename(TargetRef out, String oldNameRaw, String newNameRaw) {
    String oldName = Objects.toString(oldNameRaw, "").trim();
    String newName = Objects.toString(newNameRaw, "").trim();
    if (oldName.isEmpty() || newName.isEmpty()) {
      ui.appendError(out, "(filter)", "Usage: /filter rename <old> <new>");
      return;
    }

    FilterSettings cur = filterSettingsBus.get();
    List<FilterRule> rules = new ArrayList<>(cur != null ? cur.rules() : List.of());

    int idx = indexOf(rules, oldName);
    if (idx < 0) {
      ui.appendError(out, "(filter)", "No such rule: '" + oldName + "'");
      return;
    }

    String newKey = newName.toLowerCase(Locale.ROOT);
    if (rules.stream().anyMatch(r -> r != null && r.nameKey().equals(newKey))
        && !oldName.toLowerCase(Locale.ROOT).equals(newKey)) {
      ui.appendError(out, "(filter)", "A rule named '" + newName + "' already exists.");
      return;
    }

    FilterRule prev = rules.get(idx);
    FilterRule next =
        new FilterRule(
            prev.id(),
            newName,
            prev.enabled(),
            prev.scopePattern(),
            prev.action(),
            prev.direction(),
            prev.kinds(),
            prev.fromNickGlobs(),
            prev.textRegex(),
            prev.tags());

    rules.set(idx, next);
    applyNewRules(cur, rules);
    ui.appendStatus(
        out, "(filter)", "Renamed filter rule: " + prev.nameKey() + " -> " + next.nameKey());
  }

  private void recreate(TargetRef out, String nameRaw) {
    String name = Objects.toString(nameRaw, "").trim();
    if (name.isEmpty()) {
      ui.appendError(out, "(filter)", "Usage: /filter recreate <name>");
      return;
    }

    FilterSettings cur = filterSettingsBus.get();
    List<FilterRule> rules = new ArrayList<>(cur != null ? cur.rules() : List.of());
    int idx = indexOf(rules, name);
    if (idx < 0) {
      ui.appendError(out, "(filter)", "No such rule: '" + name + "'");
      return;
    }

    FilterRule r = rules.get(idx);
    String cmd = toNormalizedAddCommand(r);
    if (cmd.startsWith("/filter add ")) {
      cmd = "/filter addreplace " + cmd.substring("/filter add ".length());
    }

    ui.appendStatus(out, "(filter)", "Recreate command for '" + r.nameKey() + "': " + cmd);
  }

  private void set(TargetRef out, String nameRaw, FilterCommand.FilterRulePatch patch) {
    String name = Objects.toString(nameRaw, "").trim();
    if (name.isEmpty()) {
      ui.appendError(out, "(filter)", "Usage: /filter set <name> ...");
      return;
    }

    FilterSettings cur = filterSettingsBus.get();
    List<FilterRule> rules = new ArrayList<>(cur != null ? cur.rules() : List.of());
    int idx = indexOf(rules, name);
    if (idx < 0) {
      ui.appendError(out, "(filter)", "No such rule: '" + name + "'");
      return;
    }

    FilterRule prev = rules.get(idx);
    FilterRule next;
    try {
      next = applyPatch(prev, patch);
    } catch (IllegalArgumentException e) {
      ui.appendError(out, "(filter)", e.getMessage());
      return;
    }

    rules.set(idx, next);
    applyNewRules(cur, rules);
    ui.appendStatus(out, "(filter)", "Updated filter rule: " + next.nameKey());
  }

  private void del(TargetRef out, List<String> namesOrMasksRaw) {
    List<String> specs =
        (namesOrMasksRaw == null)
            ? List.of()
            : namesOrMasksRaw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

    if (specs.isEmpty()) {
      ui.appendStatus(
          out,
          "(filter)",
          "Usage: /filter del <name-or-mask> [more...] (masks support * and ?, or re:/.../)");
      return;
    }

    var cur = filterSettingsBus.get();
    var rules = new ArrayList<>(cur.rules());

    if (rules.isEmpty()) {
      ui.appendStatus(out, "(filter)", "No filters to delete.");
      return;
    }

    boolean[] remove = new boolean[rules.size()];
    int removed = 0;

    for (String spec : specs) {
      String specKey = spec.toLowerCase(Locale.ROOT);
      int exactIdx = indexOf(rules, specKey);
      if (exactIdx >= 0) {
        if (!remove[exactIdx]) {
          remove[exactIdx] = true;
          removed++;
        }
        continue;
      }

      Pattern p = compileNameSpecPattern(spec);
      if (p == null) continue;
      for (int i = 0; i < rules.size(); i++) {
        if (remove[i]) continue;
        if (p.matcher(rules.get(i).nameKey()).find()) {
          remove[i] = true;
          removed++;
        }
      }
    }

    if (removed == 0) {
      ui.appendStatus(out, "(filter)", "No matching filters for: " + String.join(", ", specs));
      return;
    }

    var next = new ArrayList<FilterRule>();
    for (int i = 0; i < rules.size(); i++) {
      if (!remove[i]) next.add(rules.get(i));
    }

    applyNewRules(cur, next);
    ui.appendStatus(
        out, "(filter)", "Deleted " + removed + " filter(s). Now have " + next.size() + ".");
  }

  private void setEnabled(TargetRef out, List<String> namesOrMasksRaw, boolean enabled) {
    List<String> specs =
        (namesOrMasksRaw == null)
            ? List.of()
            : namesOrMasksRaw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

    // WeeChat parity:
    // - no args => enable/disable filtering globally
    // - "@" => enable/disable filtering for current buffer
    boolean hasAt = specs.stream().anyMatch(s -> s.equals("@"));
    List<String> ruleSpecs = specs.stream().filter(s -> !s.equals("@")).toList();

    if (specs.isEmpty()) {
      setGlobalFiltering(out, enabled);
      return;
    }

    if (hasAt) {
      // enable filtering => hide filtered lines => /filter show off
      FilterCommand.ToggleMode mode =
          enabled ? FilterCommand.ToggleMode.OFF : FilterCommand.ToggleMode.ON;
      handleShow(out, new FilterCommand.Show(mode, null));
      if (ruleSpecs.isEmpty()) return;
    }

    var cur = filterSettingsBus.get();
    var rules = new ArrayList<>(cur.rules());

    if (rules.isEmpty()) {
      ui.appendStatus(out, "(filter)", "No filters to " + (enabled ? "enable" : "disable") + ".");
      return;
    }

    boolean[] touched = new boolean[rules.size()];
    int changed = 0;

    for (String spec : ruleSpecs) {
      String specKey = spec.toLowerCase(Locale.ROOT);
      int exactIdx = indexOf(rules, specKey);
      if (exactIdx >= 0) {
        if (!touched[exactIdx]) {
          touched[exactIdx] = true;
          changed++;
        }
        rules.set(exactIdx, copyWithEnabled(rules.get(exactIdx), enabled));
        continue;
      }

      Pattern p = compileNameSpecPattern(spec);
      if (p == null) continue;
      for (int i = 0; i < rules.size(); i++) {
        if (touched[i]) continue;
        if (p.matcher(rules.get(i).nameKey()).find()) {
          touched[i] = true;
          changed++;
          rules.set(i, copyWithEnabled(rules.get(i), enabled));
        }
      }
    }

    if (changed == 0) {
      ui.appendStatus(out, "(filter)", "No matching filters for: " + String.join(", ", ruleSpecs));
      return;
    }

    applyNewRules(cur, rules);
    ui.appendStatus(
        out, "(filter)", (enabled ? "Enabled " : "Disabled ") + changed + " filter(s).");
  }

  private void setGlobalFiltering(TargetRef out, boolean enabled) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) active = targetCoordinator.safeStatusTarget();

    FilterSettings cur = safeSettings();
    FilterSettings next =
        new FilterSettings(
            enabled,
            cur.placeholdersEnabledByDefault(),
            cur.placeholdersCollapsedByDefault(),
            cur.placeholderMaxPreviewLines(),
            cur.placeholderMaxLinesPerRun(),
            cur.placeholderTooltipMaxTags(),
            cur.historyPlaceholderMaxRunsPerBatch(),
            cur.historyPlaceholdersEnabledByDefault(),
            cur.rules(),
            cur.overrides());

    applySettings(next, false, false, true, false, false);
    rebuildMaybe(out, active);

    ui.appendStatus(
        out,
        "(filter)",
        "Global filtering => " + onOff(enabled) + " (show-filtered=" + onOff(!enabled) + ")");
    printEffectiveSummary(out, active);
  }

  private void toggle(TargetRef out, List<String> namesOrMasksRaw) {
    List<String> specs =
        (namesOrMasksRaw == null)
            ? List.of()
            : namesOrMasksRaw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

    // WeeChat parity:
    // - no args => toggle filtering globally
    // - "@" => toggle filtering for current buffer
    boolean hasAt = specs.stream().anyMatch(s -> s.equals("@"));
    List<String> ruleSpecs = specs.stream().filter(s -> !s.equals("@")).toList();

    if (specs.isEmpty()) {
      FilterSettings cur = safeSettings();
      setGlobalFiltering(out, !cur.filtersEnabledByDefault());
      return;
    }

    if (hasAt) {
      handleShow(out, new FilterCommand.Show(FilterCommand.ToggleMode.TOGGLE, null));
      if (ruleSpecs.isEmpty()) return;
    }

    FilterSettings cur = filterSettingsBus.get();
    List<FilterRule> rules = new ArrayList<>(cur != null ? cur.rules() : List.of());

    if (rules.isEmpty()) {
      ui.appendStatus(out, "(filter)", "No filters to toggle.");
      return;
    }

    boolean[] touched = new boolean[rules.size()];
    int toggled = 0;

    for (String spec : ruleSpecs) {
      String specKey = spec.toLowerCase(Locale.ROOT);
      int exactIdx = indexOf(rules, specKey);
      if (exactIdx >= 0) {
        if (!touched[exactIdx]) {
          touched[exactIdx] = true;
          toggled++;
          rules.set(exactIdx, copyWithEnabled(rules.get(exactIdx), !rules.get(exactIdx).enabled()));
        }
        continue;
      }

      Pattern p = compileNameSpecPattern(spec);
      if (p == null) continue;
      for (int i = 0; i < rules.size(); i++) {
        if (touched[i]) continue;
        if (p.matcher(rules.get(i).nameKey()).find()) {
          touched[i] = true;
          toggled++;
          rules.set(i, copyWithEnabled(rules.get(i), !rules.get(i).enabled()));
        }
      }
    }

    if (toggled == 0) {
      ui.appendStatus(out, "(filter)", "No matching filters for: " + String.join(", ", ruleSpecs));
      return;
    }

    applyNewRules(cur, rules);
    ui.appendStatus(out, "(filter)", "Toggled " + toggled + " filter(s).");
  }

  private int indexOf(List<FilterRule> rules, String name) {
    String key = Objects.toString(name, "").trim().toLowerCase(Locale.ROOT);
    for (int i = 0; i < rules.size(); i++) {
      FilterRule r = rules.get(i);
      if (r != null && r.nameKey().equals(key)) return i;
    }
    return -1;
  }

  private static Pattern compileNameSpecPattern(String spec) {
    if (spec == null) return null;
    String s = spec.trim();
    if (s.isEmpty()) return null;

    String sl = s.toLowerCase(Locale.ROOT);
    if (sl.equals("all")) s = "*";

    // Regex form: re:<pattern>
    if (sl.startsWith("re:")) {
      String body = s.substring(3);
      try {
        return Pattern.compile(body, Pattern.CASE_INSENSITIVE);
      } catch (Exception ignored) {
        return null;
      }
    }

    // Regex literal: /<body>/<flags>
    if (s.startsWith("/") && s.length() > 1) {
      Pattern p = tryCompileRegexLiteral(s);
      if (p != null) return p;
    }

    // Glob mask: * and ?
    if (s.indexOf('*') >= 0 || s.indexOf('?') >= 0) {
      String regex = globToAnchoredRegex(s);
      try {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
      } catch (Exception ignored) {
        return null;
      }
    }

    // No wildcard/regex marker: treat as exact-only (handled by indexOf before calling this).
    return null;
  }

  private static Pattern tryCompileRegexLiteral(String raw) {
    int last = findLastUnescapedSlash(raw);
    if (last <= 0) return null;

    // Unescape WeeChat-style \/ inside /regex/ literals.
    String body = raw.substring(1, last).replace("\\/", "/");
    String flags = raw.substring(last + 1);

    int f = 0;
    String fl = (flags == null) ? "" : flags.trim().toLowerCase(Locale.ROOT);
    if (fl.indexOf('i') >= 0) f |= Pattern.CASE_INSENSITIVE;
    if (fl.indexOf('m') >= 0) f |= Pattern.MULTILINE;
    if (fl.indexOf('s') >= 0) f |= Pattern.DOTALL;

    try {
      return Pattern.compile(body, f);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static int findLastUnescapedSlash(String v) {
    for (int i = v.length() - 1; i > 0; i--) {
      if (v.charAt(i) != '/') continue;
      int bs = 0;
      for (int j = i - 1; j >= 0 && v.charAt(j) == '\\'; j--) bs++;
      if ((bs % 2) == 0) return i;
    }
    return -1;
  }

  private static String globToAnchoredRegex(String glob) {
    StringBuilder sb = new StringBuilder();
    sb.append('^');
    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      switch (c) {
        case '*' -> sb.append(".*");
        case '?' -> sb.append('.');
        default -> {
          if ("[](){}.^$|+?*\\\"".indexOf(c) >= 0) sb.append('\\');
          sb.append(c);
        }
      }
    }
    sb.append('$');
    return sb.toString();
  }

  private void applyNewRules(FilterSettings cur, List<FilterRule> nextRules) {
    FilterSettings base = (cur != null) ? cur : FilterSettings.defaults();
    FilterSettings next =
        new FilterSettings(
            base.filtersEnabledByDefault(),
            base.placeholdersEnabledByDefault(),
            base.placeholdersCollapsedByDefault(),
            base.placeholderMaxPreviewLines(),
            base.placeholderMaxLinesPerRun(),
            base.placeholderTooltipMaxTags(),
            base.historyPlaceholderMaxRunsPerBatch(),
            base.historyPlaceholdersEnabledByDefault(),
            List.copyOf(nextRules),
            base.overrides());

    filterSettingsBus.set(next);
    runtimeConfig.rememberFilterRules(next.rules());

    // Step 6.4: rebuild the currently active transcript so the new rules apply
    // to already-loaded history (placeholders/folds included).
    try {
      TargetRef active = outputTarget();
      if (rebuildService != null && active != null) {
        boolean ok = rebuildService.rebuild(active);
        if (!ok) {
          ui.appendStatus(
              active,
              "(filter)",
              "Note: transcript rebuild skipped (no history available). New rules apply to new lines only.");
        }
      }
    } catch (Exception ignored) {
    }

    // Show the resolved settings for the active buffer after changes.
    try {
      TargetRef active = outputTarget();
      if (active != null) {
        printEffectiveSummary(active, active);
      }
    } catch (Exception ignored) {
    }
  }

  private static FilterRule buildRuleForAdd(String name, FilterCommand.FilterRulePatch patch) {
    boolean enabled = patch.enabledSpecified() ? Boolean.TRUE.equals(patch.enabled()) : true;
    String scope = patch.scopeSpecified() ? patch.scope() : "*";
    FilterAction action = patch.actionSpecified() ? patch.action() : FilterAction.HIDE;
    FilterDirection dir = patch.directionSpecified() ? patch.direction() : FilterDirection.ANY;

    EnumSet<LogKind> kinds = patch.kindsSpecified() ? patch.kinds() : EnumSet.noneOf(LogKind.class);
    List<String> from = patch.fromSpecified() ? patch.from() : List.of();

    RegexSpec text =
        patch.textSpecified()
            ? normalizeText(patch.textRegex())
            : new RegexSpec("", EnumSet.noneOf(RegexFlag.class));

    TagSpec tags = patch.tagsSpecified() ? TagSpec.parse(patch.tagsExpr()) : TagSpec.empty();
    validateRegex(text);

    return new FilterRule(null, name, enabled, scope, action, dir, kinds, from, text, tags);
  }

  private static FilterRule applyPatch(FilterRule prev, FilterCommand.FilterRulePatch patch) {
    UUID id = prev.id();
    String name = prev.name();
    boolean enabled =
        patch.enabledSpecified() ? Boolean.TRUE.equals(patch.enabled()) : prev.enabled();
    String scope = patch.scopeSpecified() ? patch.scope() : prev.scopePattern();
    FilterAction action = patch.actionSpecified() ? patch.action() : prev.action();
    FilterDirection dir = patch.directionSpecified() ? patch.direction() : prev.direction();
    EnumSet<LogKind> kinds = patch.kindsSpecified() ? patch.kinds() : prev.kinds();
    List<String> from = patch.fromSpecified() ? patch.from() : prev.fromNickGlobs();
    RegexSpec text = patch.textSpecified() ? normalizeText(patch.textRegex()) : prev.textRegex();
    TagSpec tags = patch.tagsSpecified() ? TagSpec.parse(patch.tagsExpr()) : prev.tags();

    validateRegex(text);
    return new FilterRule(id, name, enabled, scope, action, dir, kinds, from, text, tags);
  }

  private static FilterRule copyWithEnabled(FilterRule r, boolean enabled) {
    if (r == null) return null;
    return new FilterRule(
        r.id(),
        r.name(),
        enabled,
        r.scopePattern(),
        r.action(),
        r.direction(),
        r.kinds(),
        r.fromNickGlobs(),
        r.textRegex(),
        r.tags());
  }

  private static RegexSpec normalizeText(RegexSpec in) {
    if (in == null || in.isEmpty()) return new RegexSpec("", EnumSet.noneOf(RegexFlag.class));
    return in;
  }

  private void export(TargetRef out, FilterCommand.Export cmd) {
    FilterSettings cur = safeSettings();
    String format = (cmd != null) ? cmd.format() : "all";
    String file = (cmd != null) ? cmd.file() : null;

    List<String> lines = buildExportLines(cur, format);

    if (file == null) {
      ui.appendStatus(out, "(filter)", "Export (" + format + "):");
      for (String line : lines) {
        ui.appendStatus(out, "(filter)", line);
      }
      return;
    }

    try {
      java.nio.file.Path path = java.nio.file.Paths.get(file).toAbsolutePath().normalize();
      java.nio.file.Path parent = path.getParent();
      if (parent != null) java.nio.file.Files.createDirectories(parent);
      java.nio.file.Files.write(path, lines, java.nio.charset.StandardCharsets.UTF_8);
      ui.appendStatus(out, "(filter)", "Exported " + lines.size() + " line(s) to: " + path);
    } catch (Exception e) {
      ui.appendError(out, "(filter)", "Failed to export to file: " + e.getMessage());
    }
  }

  private List<String> buildExportLines(FilterSettings cur, String format) {
    String f =
        (format == null || format.isBlank()) ? "all" : format.trim().toLowerCase(Locale.ROOT);
    if (f.equals("cmd") || f.equals("commands")) {
      List<String> out = new ArrayList<>();
      for (FilterRule r : cur.rules()) {
        out.add(toNormalizedAddCommand(r));
      }
      return out;
    }

    // default: all
    List<String> out = new ArrayList<>();
    out.add(
        "/filter defaults"
            + " filters="
            + (cur.filtersEnabledByDefault() ? "on" : "off")
            + " placeholders="
            + (cur.placeholdersEnabledByDefault() ? "on" : "off")
            + " collapsed="
            + (cur.placeholdersCollapsedByDefault() ? "on" : "off")
            + " preview="
            + cur.placeholderMaxPreviewLines()
            + " maxrun="
            + cur.placeholderMaxLinesPerRun()
            + " maxtags="
            + cur.placeholderTooltipMaxTags()
            + " maxbatch="
            + cur.historyPlaceholderMaxRunsPerBatch()
            + " history="
            + (cur.historyPlaceholdersEnabledByDefault() ? "on" : "off"));

    for (FilterScopeOverride o : cur.overrides()) {
      out.add(
          "/filter override set scope="
              + o.scopePattern()
              + " filters="
              + tri(o.filtersEnabled())
              + " placeholders="
              + tri(o.placeholdersEnabled())
              + " collapsed="
              + tri(o.placeholdersCollapsed()));
    }

    for (FilterRule r : cur.rules()) {
      out.add(toNormalizedAddCommand(r));
    }
    return out;
  }

  private void handleShow(TargetRef out, FilterCommand.Show cmd) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) active = targetCoordinator.safeStatusTarget();

    String scope = (cmd.scopePattern() != null) ? cmd.scopePattern() : activeScopePattern(active);

    FilterSettings cur = safeSettings();
    boolean currentFiltersEnabled = resolveCurrentFiltersEnabled(cmd, cur, active, scope);

    // Use a switch *expression* to keep definite assignment simple for the compiler.
    Boolean newFiltersEnabled =
        switch (cmd.mode()) {
          case ON -> Boolean.FALSE; // show filtered lines => disable filtering
          case OFF -> Boolean.TRUE; // hide filtered lines => enable filtering
          case DEFAULT -> null; // inherit
          case TOGGLE -> !currentFiltersEnabled;
        };

    FilterSettings next =
        upsertOverride(cur, scope, newFiltersEnabled, true, null, false, null, false);

    boolean had = findOverride(cur.overrides(), scope) != null;
    applySettings(next, false, true, false, false, false);
    rebuildMaybe(out, active);

    boolean has = findOverride(next.overrides(), scope) != null;
    ui.appendStatus(
        out,
        "(filter)",
        "Show filtered lines for " + scope + " => " + showFilteredLabel(newFiltersEnabled));
    if (had && !has && newFiltersEnabled == null) {
      ui.appendStatus(out, "(filter)", "(override cleared; now inheriting defaults)");
    }

    printEffectiveSummary(out, active);
    ui.appendStatus(
        out,
        "(filter)",
        "Runnable: /filter override set scope="
            + scope
            + " filters="
            + tri(newFiltersEnabled)
            + " placeholders=default collapsed=default");
  }

  private boolean resolveCurrentFiltersEnabled(
      FilterCommand.Show cmd, FilterSettings cur, TargetRef active, String scope) {
    try {
      if (cmd.scopePattern() == null) {
        return filterEngine.effectiveFor(active).filtersEnabled();
      }
      FilterScopeOverride ov = findOverride(cur.overrides(), scope);
      if (ov != null && ov.filtersEnabled() != null) return ov.filtersEnabled();
      return cur.filtersEnabledByDefault();
    } catch (Exception ignored) {
      return true;
    }
  }

  private void handlePlaceholders(TargetRef out, FilterCommand.Placeholders cmd) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) active = targetCoordinator.safeStatusTarget();

    String scope = (cmd.scopePattern() != null) ? cmd.scopePattern() : activeScopePattern(active);

    FilterSettings cur = safeSettings();
    boolean current = resolveCurrentPlaceholdersEnabled(cmd, cur, active, scope);

    // Use a switch *expression* to keep definite assignment simple for the compiler.
    Boolean newVal =
        switch (cmd.mode()) {
          case ON -> Boolean.TRUE;
          case OFF -> Boolean.FALSE;
          case DEFAULT -> null;
          case TOGGLE -> !current;
        };

    FilterSettings next = upsertOverride(cur, scope, null, false, newVal, true, null, false);

    boolean had = findOverride(cur.overrides(), scope) != null;
    applySettings(next, false, true, false, false, false);
    rebuildMaybe(out, active);

    boolean has = findOverride(next.overrides(), scope) != null;
    ui.appendStatus(
        out, "(filter)", "Filtered (N) placeholders for " + scope + " => " + tri(newVal));
    if (had && !has && newVal == null) {
      ui.appendStatus(out, "(filter)", "(override cleared; now inheriting defaults)");
    }

    printEffectiveSummary(out, active);
    ui.appendStatus(
        out,
        "(filter)",
        "Runnable: /filter override set scope="
            + scope
            + " placeholders="
            + tri(newVal)
            + " filters=default collapsed=default");
  }

  private boolean resolveCurrentPlaceholdersEnabled(
      FilterCommand.Placeholders cmd, FilterSettings cur, TargetRef active, String scope) {
    try {
      if (cmd.scopePattern() == null) {
        return filterEngine.effectiveFor(active).placeholdersEnabled();
      }
      FilterScopeOverride ov = findOverride(cur.overrides(), scope);
      if (ov != null && ov.placeholdersEnabled() != null) return ov.placeholdersEnabled();
      return cur.placeholdersEnabledByDefault();
    } catch (Exception ignored) {
      return true;
    }
  }

  private void handlePlaceholdersCollapsed(TargetRef out, FilterCommand.PlaceholdersCollapsed cmd) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) active = targetCoordinator.safeStatusTarget();

    String scope = (cmd.scopePattern() != null) ? cmd.scopePattern() : activeScopePattern(active);

    FilterSettings cur = safeSettings();
    boolean current = resolveCurrentCollapsed(cmd, cur, active, scope);

    // Use a switch *expression* to keep definite assignment simple for the compiler.
    Boolean newVal =
        switch (cmd.mode()) {
          case ON -> Boolean.TRUE;
          case OFF -> Boolean.FALSE;
          case DEFAULT -> null;
          case TOGGLE -> !current;
        };

    FilterSettings next = upsertOverride(cur, scope, null, false, null, false, newVal, true);

    boolean had = findOverride(cur.overrides(), scope) != null;
    applySettings(next, false, true, false, false, false);
    rebuildMaybe(out, active);

    boolean has = findOverride(next.overrides(), scope) != null;
    ui.appendStatus(out, "(filter)", "Placeholders collapsed for " + scope + " => " + tri(newVal));
    if (had && !has && newVal == null) {
      ui.appendStatus(out, "(filter)", "(override cleared; now inheriting defaults)");
    }

    printEffectiveSummary(out, active);
    ui.appendStatus(
        out,
        "(filter)",
        "Runnable: /filter override set scope="
            + scope
            + " collapsed="
            + tri(newVal)
            + " filters=default placeholders=default");
  }

  private boolean resolveCurrentCollapsed(
      FilterCommand.PlaceholdersCollapsed cmd, FilterSettings cur, TargetRef active, String scope) {
    try {
      if (cmd.scopePattern() == null) {
        return filterEngine.effectiveFor(active).placeholdersCollapsed();
      }
      FilterScopeOverride ov = findOverride(cur.overrides(), scope);
      if (ov != null && ov.placeholdersCollapsed() != null) return ov.placeholdersCollapsed();
      return cur.placeholdersCollapsedByDefault();
    } catch (Exception ignored) {
      return true;
    }
  }

  private void handlePlaceholderPreview(TargetRef out, FilterCommand.PlaceholderPreview cmd) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) active = targetCoordinator.safeStatusTarget();

    FilterSettings cur = safeSettings();
    int v = cmd.maxLines();

    FilterSettings next =
        new FilterSettings(
            cur.filtersEnabledByDefault(),
            cur.placeholdersEnabledByDefault(),
            cur.placeholdersCollapsedByDefault(),
            v,
            cur.placeholderMaxLinesPerRun(),
            cur.placeholderTooltipMaxTags(),
            cur.historyPlaceholderMaxRunsPerBatch(),
            cur.historyPlaceholdersEnabledByDefault(),
            cur.rules(),
            cur.overrides());

    applySettings(next, false, false, false, true, false);
    rebuildMaybe(out, active);

    ui.appendStatus(out, "(filter)", "Placeholder preview max lines => " + v);
    printEffectiveSummary(out, active);
    ui.appendStatus(
        out,
        "(filter)",
        "Runnable: /filter defaults"
            + " filters="
            + (next.filtersEnabledByDefault() ? "on" : "off")
            + " placeholders="
            + (next.placeholdersEnabledByDefault() ? "on" : "off")
            + " collapsed="
            + (next.placeholdersCollapsedByDefault() ? "on" : "off")
            + " preview="
            + v
            + " maxrun="
            + next.placeholderMaxLinesPerRun()
            + " maxtags="
            + next.placeholderTooltipMaxTags()
            + " maxbatch="
            + next.historyPlaceholderMaxRunsPerBatch()
            + " history="
            + (next.historyPlaceholdersEnabledByDefault() ? "on" : "off"));
  }

  private void handleDefaults(TargetRef out, FilterCommand.Defaults cmd) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) active = targetCoordinator.safeStatusTarget();

    FilterSettings cur = safeSettings();

    boolean f =
        cmd.filtersSpecified()
            ? Boolean.TRUE.equals(cmd.filtersEnabledByDefault())
            : cur.filtersEnabledByDefault();
    boolean p =
        cmd.placeholdersSpecified()
            ? Boolean.TRUE.equals(cmd.placeholdersEnabledByDefault())
            : cur.placeholdersEnabledByDefault();
    boolean c =
        cmd.collapsedSpecified()
            ? Boolean.TRUE.equals(cmd.placeholdersCollapsedByDefault())
            : cur.placeholdersCollapsedByDefault();
    int preview =
        cmd.previewSpecified()
            ? (cmd.placeholderMaxPreviewLines() == null ? 0 : cmd.placeholderMaxPreviewLines())
            : cur.placeholderMaxPreviewLines();

    int maxRun =
        cmd.maxRunSpecified()
            ? (cmd.placeholderMaxLinesPerRun() == null ? 0 : cmd.placeholderMaxLinesPerRun())
            : cur.placeholderMaxLinesPerRun();
    int maxTags =
        cmd.tooltipTagsSpecified()
            ? (cmd.placeholderTooltipMaxTags() == null ? 0 : cmd.placeholderTooltipMaxTags())
            : cur.placeholderTooltipMaxTags();
    int maxBatch =
        cmd.maxBatchSpecified()
            ? (cmd.historyPlaceholderMaxRunsPerBatch() == null
                ? 0
                : cmd.historyPlaceholderMaxRunsPerBatch())
            : cur.historyPlaceholderMaxRunsPerBatch();
    boolean history =
        cmd.historySpecified()
            ? Boolean.TRUE.equals(cmd.historyPlaceholdersEnabledByDefault())
            : cur.historyPlaceholdersEnabledByDefault();

    FilterSettings next =
        new FilterSettings(
            f, p, c, preview, maxRun, maxTags, maxBatch, history, cur.rules(), cur.overrides());

    boolean defaultsChanged =
        cmd.filtersSpecified() || cmd.placeholdersSpecified() || cmd.collapsedSpecified();
    boolean tuningChanged =
        cmd.maxRunSpecified()
            || cmd.tooltipTagsSpecified()
            || cmd.maxBatchSpecified()
            || cmd.historySpecified();
    applySettings(next, false, false, defaultsChanged, cmd.previewSpecified(), tuningChanged);
    rebuildMaybe(out, active);

    ui.appendStatus(
        out,
        "(filter)",
        "Defaults updated:"
            + " filters="
            + (cur.filtersEnabledByDefault() ? "on" : "off")
            + "->"
            + (next.filtersEnabledByDefault() ? "on" : "off")
            + " placeholders="
            + (cur.placeholdersEnabledByDefault() ? "on" : "off")
            + "->"
            + (next.placeholdersEnabledByDefault() ? "on" : "off")
            + " collapsed="
            + (cur.placeholdersCollapsedByDefault() ? "on" : "off")
            + "->"
            + (next.placeholdersCollapsedByDefault() ? "on" : "off")
            + " preview="
            + cur.placeholderMaxPreviewLines()
            + "->"
            + next.placeholderMaxPreviewLines()
            + " maxrun="
            + cur.placeholderMaxLinesPerRun()
            + "->"
            + next.placeholderMaxLinesPerRun()
            + " maxtags="
            + cur.placeholderTooltipMaxTags()
            + "->"
            + next.placeholderTooltipMaxTags()
            + " maxbatch="
            + cur.historyPlaceholderMaxRunsPerBatch()
            + "->"
            + next.historyPlaceholderMaxRunsPerBatch()
            + " history="
            + (cur.historyPlaceholdersEnabledByDefault() ? "on" : "off")
            + "->"
            + (next.historyPlaceholdersEnabledByDefault() ? "on" : "off"));
    printEffectiveSummary(out, active);
    ui.appendStatus(
        out,
        "(filter)",
        "Runnable: /filter defaults"
            + " filters="
            + (next.filtersEnabledByDefault() ? "on" : "off")
            + " placeholders="
            + (next.placeholdersEnabledByDefault() ? "on" : "off")
            + " collapsed="
            + (next.placeholdersCollapsedByDefault() ? "on" : "off")
            + " preview="
            + next.placeholderMaxPreviewLines()
            + " maxrun="
            + next.placeholderMaxLinesPerRun()
            + " maxtags="
            + next.placeholderTooltipMaxTags()
            + " maxbatch="
            + next.historyPlaceholderMaxRunsPerBatch()
            + " history="
            + (next.historyPlaceholdersEnabledByDefault() ? "on" : "off"));
  }

  private void listOverrides(TargetRef out, String formatRaw) {
    FilterSettings cur = safeSettings();
    String format =
        (formatRaw == null || formatRaw.isBlank())
            ? "table"
            : formatRaw.trim().toLowerCase(Locale.ROOT);

    if (cur.overrides().isEmpty()) {
      ui.appendStatus(out, "(filter)", "No scope overrides defined.");
      return;
    }

    if (format.equals("cmd") || format.equals("commands")) {
      ui.appendStatus(out, "(filter)", "Scope overrides (runnable):");
      for (FilterScopeOverride o : cur.overrides()) {
        ui.appendStatus(
            out,
            "(filter)",
            "/filter override set scope="
                + o.scopePattern()
                + " filters="
                + tri(o.filtersEnabled())
                + " placeholders="
                + tri(o.placeholdersEnabled())
                + " collapsed="
                + tri(o.placeholdersCollapsed()));
      }
      return;
    }

    ui.appendStatus(out, "(filter)", "Scope overrides:");
    ui.appendStatus(
        out,
        "(filter)",
        String.format("%-30s %-10s %-14s %-10s", "Scope", "Filters", "Placeholders", "Collapsed"));
    for (FilterScopeOverride o : cur.overrides()) {
      ui.appendStatus(
          out,
          "(filter)",
          String.format(
              "%-30s %-10s %-14s %-10s",
              o.scopePattern(),
              tri(o.filtersEnabled()),
              tri(o.placeholdersEnabled()),
              tri(o.placeholdersCollapsed())));
    }
  }

  private void setOverride(TargetRef out, FilterCommand.OverrideSet cmd) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) active = targetCoordinator.safeStatusTarget();

    FilterSettings cur = safeSettings();
    String scope = cmd.scopePattern();

    Boolean fe = cmd.filtersSpecified() ? triToBool(cmd.filtersEnabled()) : null;
    Boolean pe = cmd.placeholdersSpecified() ? triToBool(cmd.placeholdersEnabled()) : null;
    Boolean ce = cmd.collapsedSpecified() ? triToBool(cmd.placeholdersCollapsed()) : null;

    FilterSettings next =
        upsertOverride(
            cur,
            scope,
            fe,
            cmd.filtersSpecified(),
            pe,
            cmd.placeholdersSpecified(),
            ce,
            cmd.collapsedSpecified());

    boolean had = findOverride(cur.overrides(), scope) != null;
    applySettings(next, false, true, false, false, false);
    rebuildMaybe(out, active);

    FilterScopeOverride eff = findOverride(next.overrides(), scope);
    boolean has = eff != null;
    ui.appendStatus(
        out,
        "(filter)",
        "Override "
            + (has ? "set" : "cleared")
            + " for "
            + scope
            + (had && !has ? " (now inheriting defaults)" : ""));
    printEffectiveSummary(out, active);
    ui.appendStatus(
        out,
        "(filter)",
        "Runnable: /filter override set scope="
            + scope
            + " filters="
            + tri(eff == null ? null : eff.filtersEnabled())
            + " placeholders="
            + tri(eff == null ? null : eff.placeholdersEnabled())
            + " collapsed="
            + tri(eff == null ? null : eff.placeholdersCollapsed()));
  }

  private void delOverride(TargetRef out, String scopePattern) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active == null) active = targetCoordinator.safeStatusTarget();

    FilterSettings cur = safeSettings();
    String scope = (scopePattern == null || scopePattern.isBlank()) ? "*" : scopePattern.trim();

    List<FilterScopeOverride> outList = new ArrayList<>();
    for (FilterScopeOverride o : cur.overrides()) {
      if (o.scopePattern().equalsIgnoreCase(scope)) continue;
      outList.add(o);
    }

    FilterSettings next =
        new FilterSettings(
            cur.filtersEnabledByDefault(),
            cur.placeholdersEnabledByDefault(),
            cur.placeholdersCollapsedByDefault(),
            cur.placeholderMaxPreviewLines(),
            cur.placeholderMaxLinesPerRun(),
            cur.placeholderTooltipMaxTags(),
            cur.historyPlaceholderMaxRunsPerBatch(),
            cur.historyPlaceholdersEnabledByDefault(),
            cur.rules(),
            List.copyOf(outList));

    applySettings(next, false, true, false, false, false);
    rebuildMaybe(out, active);

    ui.appendStatus(out, "(filter)", "Override removed for " + scope);
    printEffectiveSummary(out, active);
    ui.appendStatus(out, "(filter)", "Runnable: /filter override del scope=" + scope);
  }

  private void applySettings(
      FilterSettings next,
      boolean rulesChanged,
      boolean overridesChanged,
      boolean defaultsChanged,
      boolean previewChanged,
      boolean tuningChanged) {
    filterSettingsBus.set(next);

    if (rulesChanged) {
      runtimeConfig.rememberFilterRules(next.rules());
    }
    if (overridesChanged) {
      runtimeConfig.rememberFilterOverrides(next.overrides());
    }
    if (defaultsChanged) {
      runtimeConfig.rememberFiltersEnabledByDefault(next.filtersEnabledByDefault());
      runtimeConfig.rememberFilterPlaceholdersEnabledByDefault(next.placeholdersEnabledByDefault());
      runtimeConfig.rememberFilterPlaceholdersCollapsedByDefault(
          next.placeholdersCollapsedByDefault());
    }
    if (previewChanged) {
      runtimeConfig.rememberFilterPlaceholderMaxPreviewLines(next.placeholderMaxPreviewLines());
    }
    if (tuningChanged) {
      runtimeConfig.rememberFilterPlaceholderMaxLinesPerRun(next.placeholderMaxLinesPerRun());
      runtimeConfig.rememberFilterPlaceholderTooltipMaxTags(next.placeholderTooltipMaxTags());
      runtimeConfig.rememberFilterHistoryPlaceholderMaxRunsPerBatch(
          next.historyPlaceholderMaxRunsPerBatch());
      runtimeConfig.rememberFilterHistoryPlaceholdersEnabledByDefault(
          next.historyPlaceholdersEnabledByDefault());
    }
  }

  private FilterSettings safeSettings() {
    try {
      FilterSettings cur = filterSettingsBus.get();
      return (cur != null) ? cur : FilterSettings.defaults();
    } catch (Exception ignored) {
      return FilterSettings.defaults();
    }
  }

  private void rebuildMaybe(TargetRef out, TargetRef active) {
    try {
      boolean rebuilt = rebuildService.rebuild(active);
      if (!rebuilt) {
        ui.appendStatus(
            out,
            "(filter)",
            "(note: transcript rebuild skipped for this buffer; filter changes apply to new lines)");
      }
    } catch (Exception ignored) {
    }
  }

  private void printEffectiveSummary(TargetRef out, TargetRef target) {
    try {
      String key = activeScopePattern(target);
      FilterEngine.EffectiveResolved eff = filterEngine.effectiveResolvedFor(target);

      ui.appendStatus(out, "(filter)", "Effective for " + key + ":");

      FilterEngine.ResolvedBool fe = eff.filtersEnabled();
      ui.appendStatus(
          out,
          "(filter)",
          "  filtering="
              + onOff(fe.value())
              + sourceSuffix(fe)
              + " (show-filtered="
              + onOff(!fe.value())
              + ")");

      FilterEngine.ResolvedBool ph = eff.placeholdersEnabled();
      ui.appendStatus(out, "(filter)", "  placeholders=" + onOff(ph.value()) + sourceSuffix(ph));

      FilterEngine.ResolvedBool col = eff.placeholdersCollapsed();
      ui.appendStatus(out, "(filter)", "  collapsed=" + onOff(col.value()) + sourceSuffix(col));

      ui.appendStatus(
          out, "(filter)", "  preview=" + eff.placeholderMaxPreviewLines() + " (global)");
      ui.appendStatus(out, "(filter)", "  maxrun=" + eff.placeholderMaxLinesPerRun() + " (global)");
      ui.appendStatus(
          out, "(filter)", "  maxtags=" + eff.placeholderTooltipMaxTags() + " (global)");
      ui.appendStatus(
          out,
          "(filter)",
          "  maxbatch=" + eff.historyPlaceholderMaxRunsPerBatch() + " (global, 0=unlimited)");
      ui.appendStatus(
          out, "(filter)", "  history=" + onOff(eff.historyPlaceholdersEnabled()) + " (global)");
    } catch (Exception ignored) {
    }
  }

  private static String onOff(boolean v) {
    return v ? "on" : "off";
  }

  private static String sourceSuffix(FilterEngine.ResolvedBool rb) {
    if (rb == null || !rb.isFromOverride()) return " [default]";
    return " [override: " + rb.sourceScopePattern() + "]";
  }

  private static String activeScopePattern(TargetRef active) {
    if (active == null) return "*/status";
    return active.serverId() + "/" + active.key();
  }

  private static String tri(Boolean v) {
    if (v == null) return "default";
    return v ? "on" : "off";
  }

  private static Boolean triToBool(FilterCommand.TriState ts) {
    if (ts == null) return null;
    return switch (ts) {
      case ON -> Boolean.TRUE;
      case OFF -> Boolean.FALSE;
      case DEFAULT -> null;
    };
  }

  private static FilterScopeOverride findOverride(
      List<FilterScopeOverride> overrides, String scope) {
    if (overrides == null || overrides.isEmpty()) return null;
    for (FilterScopeOverride o : overrides) {
      if (o.scopePattern().equalsIgnoreCase(scope)) return o;
    }
    return null;
  }

  private static String showState(Boolean newFiltersEnabled) {
    if (newFiltersEnabled == null) return "default";
    return newFiltersEnabled ? "off" : "on";
  }

  private static String showFilteredLabel(Boolean newFiltersEnabled) {
    // "Show filtered lines" is the inverse of "filtersEnabled".
    if (newFiltersEnabled == null) return "default";
    return newFiltersEnabled ? "off" : "on";
  }

  private static FilterSettings upsertOverride(
      FilterSettings cur,
      String scopePattern,
      Boolean filtersEnabled,
      boolean filtersSpecified,
      Boolean placeholdersEnabled,
      boolean placeholdersSpecified,
      Boolean collapsed,
      boolean collapsedSpecified) {
    String scope = (scopePattern == null || scopePattern.isBlank()) ? "*" : scopePattern.trim();

    List<FilterScopeOverride> out = new ArrayList<>();
    FilterScopeOverride prev = null;
    for (FilterScopeOverride o : cur.overrides()) {
      if (o.scopePattern().equalsIgnoreCase(scope)) {
        prev = o;
      } else {
        out.add(o);
      }
    }
    if (prev == null) prev = new FilterScopeOverride(scope, null, null, null);

    Boolean fe = prev.filtersEnabled();
    Boolean pe = prev.placeholdersEnabled();
    Boolean ce = prev.placeholdersCollapsed();

    if (filtersSpecified) fe = filtersEnabled;
    if (placeholdersSpecified) pe = placeholdersEnabled;
    if (collapsedSpecified) ce = collapsed;

    FilterScopeOverride nextOv = new FilterScopeOverride(scope, fe, pe, ce);

    if (!(nextOv.filtersEnabled() == null
        && nextOv.placeholdersEnabled() == null
        && nextOv.placeholdersCollapsed() == null)) {
      out.add(nextOv);
    }

    out.sort(
        Comparator.comparingInt((FilterScopeOverride o) -> o.scopePattern().length()).reversed());

    return new FilterSettings(
        cur.filtersEnabledByDefault(),
        cur.placeholdersEnabledByDefault(),
        cur.placeholdersCollapsedByDefault(),
        cur.placeholderMaxPreviewLines(),
        cur.placeholderMaxLinesPerRun(),
        cur.placeholderTooltipMaxTags(),
        cur.historyPlaceholderMaxRunsPerBatch(),
        cur.historyPlaceholdersEnabledByDefault(),
        cur.rules(),
        List.copyOf(out));
  }

  private static void validateRegex(RegexSpec spec) {
    if (spec == null || spec.isEmpty()) return;
    int flags = 0;
    if (spec.flags() != null) {
      if (spec.flags().contains(RegexFlag.I)) flags |= Pattern.CASE_INSENSITIVE;
      if (spec.flags().contains(RegexFlag.M)) flags |= Pattern.MULTILINE;
      if (spec.flags().contains(RegexFlag.S)) flags |= Pattern.DOTALL;
    }
    try {
      Pattern.compile(spec.pattern(), flags);
    } catch (PatternSyntaxException pse) {
      throw new IllegalArgumentException("Invalid regex: " + pse.getDescription());
    }
  }

  private static String joinKinds(EnumSet<LogKind> kinds) {
    if (kinds == null || kinds.isEmpty()) return "";
    List<LogKind> sorted = new ArrayList<>(kinds);
    sorted.sort(Comparator.comparingInt(Enum::ordinal));
    List<String> out = new ArrayList<>();
    for (LogKind k : sorted) out.add(k.name());
    return String.join(",", out);
  }

  private static String flagsString(RegexSpec spec) {
    if (spec == null || spec.flags() == null || spec.flags().isEmpty()) return "";
    StringBuilder b = new StringBuilder();
    if (spec.flags().contains(RegexFlag.I)) b.append('i');
    if (spec.flags().contains(RegexFlag.M)) b.append('m');
    if (spec.flags().contains(RegexFlag.S)) b.append('s');
    return b.toString();
  }

  private static String toNormalizedAddCommand(FilterRule r) {
    StringBuilder b = new StringBuilder();
    b.append("/filter add ").append(r.nameKey());
    b.append(" scope=").append(formatTokenValue(r.scopePattern()));
    b.append(" action=").append((r.action() != null) ? r.action().name() : "HIDE");
    b.append(" enabled=").append(r.enabled());
    if (r.direction() != null && r.direction() != FilterDirection.ANY) {
      b.append(" dir=").append(r.direction().name().toLowerCase(Locale.ROOT));
    }
    if (r.hasKinds()) {
      b.append(" kind=").append(joinKinds(r.kinds()));
    }
    if (r.hasFromNickGlobs()) {
      List<String> sorted = new ArrayList<>(r.fromNickGlobs());
      sorted.sort(String.CASE_INSENSITIVE_ORDER);
      for (String f : sorted) {
        b.append(" from=").append(formatTokenValue(f));
      }
    }
    if (r.hasTags()) {
      b.append(" tags=").append(formatTokenValue(r.tags().expr()));
    }
    if (r.hasTextRegex()) {
      String literal =
          "/" + r.textRegex().pattern().replace("/", "\\/") + "/" + flagsString(r.textRegex());
      b.append(" text=").append(formatTokenValue(literal));
    }
    return b.toString();
  }

  private static String formatTokenValue(String v) {
    String s = Objects.toString(v, "");
    boolean needsQuotes =
        s.chars().anyMatch(Character::isWhitespace) || s.contains("\"") || s.contains("\\");
    if (!needsQuotes) return s;

    String esc =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
    return "\"" + esc + "\"";
  }
}
