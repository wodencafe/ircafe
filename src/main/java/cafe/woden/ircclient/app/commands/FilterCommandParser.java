package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.FilterDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.RegexFlag;
import cafe.woden.ircclient.model.RegexSpec;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Parses {@code /filter ...} commands using the mini-spec token grammar. */
@Component
public class FilterCommandParser {

  public FilterCommand parse(String raw) {
    String line = raw == null ? "" : raw.trim();
    if (line.isEmpty()) return new FilterCommand.Help();
    if (!line.startsWith("/")) return new FilterCommand.Error("Not a /filter command.");

    List<String> toks;
    try {
      toks = tokenize(line);
    } catch (IllegalArgumentException e) {
      return new FilterCommand.Error(e.getMessage());
    }
    if (toks.isEmpty()) return new FilterCommand.Help();

    String cmd0 = toks.getFirst();
    if (!cmd0.equalsIgnoreCase("/filter")) {
      return new FilterCommand.Error("Not a /filter command.");
    }
    if (toks.size() == 1) return new FilterCommand.Help();

    String sub = toks.get(1).trim().toLowerCase(Locale.ROOT);
    switch (sub) {
      case "help" -> {
        return new FilterCommand.Help();
      }
      case "list" -> {
        String format = "table";
        for (int i = 2; i < toks.size(); i++) {
          String t = toks.get(i);
          int eq = t.indexOf('=');
          if (eq < 0)
            return new FilterCommand.Error("Invalid token: '" + t + "' (expected key=value)");
          String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
          String val = t.substring(eq + 1).trim();
          if (key.equals("format")) {
            format = val;
          } else {
            return new FilterCommand.Error("Unknown key for /filter list: '" + key + "'");
          }
        }
        return new FilterCommand.ListRules(format);
      }
      case "export" -> {
        return parseExport(toks);
      }
      case "move" -> {
        return parseMove(toks);
      }
      case "show" -> {
        return parseShow(toks);
      }
      case "placeholders" -> {
        return parsePlaceholders(toks);
      }
      case "placeholder-preview", "placeholderpreview" -> {
        return parsePlaceholderPreview(toks);
      }
      case "defaults" -> {
        return parseDefaults(toks);
      }
      case "override", "overrides" -> {
        return parseOverride(toks);
      }
      case "add" -> {
        return parseAddOrAddReplace(toks, false);
      }
      case "addreplace", "add-replace", "addr" -> {
        return parseAddOrAddReplace(toks, true);
      }
      case "set" -> {
        if (toks.size() < 3)
          return new FilterCommand.Error("Usage: /filter set <name> key=value ...");
        String name = toks.get(2);
        try {
          FilterCommand.FilterRulePatch patch = parseRulePatch(toks, 3);
          return new FilterCommand.Set(name, patch);
        } catch (IllegalArgumentException e) {
          return new FilterCommand.Error(e.getMessage());
        }
      }
      case "rename", "ren" -> {
        if (toks.size() != 4) return new FilterCommand.Error("Usage: /filter rename <old> <new>");
        return new FilterCommand.Rename(toks.get(2), toks.get(3));
      }
      case "recreate", "rec" -> {
        if (toks.size() != 3) return new FilterCommand.Error("Usage: /filter recreate <name>");
        return new FilterCommand.Recreate(toks.get(2));
      }
      case "del", "delete", "rm", "remove" -> {
        if (toks.size() < 3)
          return new FilterCommand.Error(
              "Usage: /filter del <name-or-mask> [more...] (use '*' and '?' for masks, or re:/.../)");
        return new FilterCommand.Del(toks.subList(2, toks.size()));
      }
      case "enable" -> {
        // WeeChat parity: no args => enable filters globally; "@" => current buffer.
        if (toks.size() == 2) return new FilterCommand.Enable(List.of());
        return new FilterCommand.Enable(toks.subList(2, toks.size()));
      }
      case "disable" -> {
        // WeeChat parity: no args => disable filters globally; "@" => current buffer.
        if (toks.size() == 2) return new FilterCommand.Disable(List.of());
        return new FilterCommand.Disable(toks.subList(2, toks.size()));
      }
      case "toggle" -> {
        // WeeChat parity: no args => toggle filters globally; "@" => current buffer.
        if (toks.size() == 2) return new FilterCommand.Toggle(List.of());
        return new FilterCommand.Toggle(toks.subList(2, toks.size()));
      }
      default -> {
        return new FilterCommand.Error(
            "Unknown /filter subcommand: '" + sub + "'. Try: /filter help");
      }
    }
  }

  private static FilterCommand parseAddOrAddReplace(List<String> toks, boolean addReplace) {
    // /filter add <name> key=value ...
    // /filter add <name> <buffer> <tags> <regex>
    if (toks.size() < 3) {
      return new FilterCommand.Error(
          addReplace
              ? "Usage: /filter addreplace <name> key=value ... (or: /filter addreplace <name> <buffer> <tags> <regex>)"
              : "Usage: /filter add <name> key=value ... (or: /filter add <name> <buffer> <tags> <regex>)");
    }

    String name = toks.get(2);

    try {
      FilterCommand.FilterRulePatch patch;
      // Prefer positional form when the token shape is unambiguous:
      // /filter add <name> <buffer> <tags> <regex>
      // This avoids mis-detecting positional regex values that contain '='.
      if (looksLikeWeeChatPositionalAdd(toks)) {
        patch = parseWeeChatPositionalPatch(toks.get(3), toks.get(4), toks.get(5));
      } else if (containsKeyValueTokens(toks, 3)) {
        patch = parseRulePatch(toks, 3);
      } else {
        return new FilterCommand.Error(
            "Usage: /filter "
                + (addReplace ? "addreplace" : "add")
                + " <name> <buffer> <tags> <regex> (tip: quote the regex if it contains spaces)");
      }

      return addReplace
          ? new FilterCommand.AddReplace(name, patch)
          : new FilterCommand.Add(name, patch);
    } catch (IllegalArgumentException e) {
      return new FilterCommand.Error(e.getMessage());
    }
  }

  private static boolean containsKeyValueTokens(List<String> toks, int startIdx) {
    for (int i = startIdx; i < toks.size(); i++) {
      if (toks.get(i).contains("=")) return true;
    }
    return false;
  }

  private static boolean looksLikeWeeChatPositionalAdd(List<String> toks) {
    if (toks == null || toks.size() != 6) return false;
    // <buffer> and <tags> are positional and should not be key=value in this form.
    return !toks.get(3).contains("=") && !toks.get(4).contains("=");
  }

  private static FilterCommand.FilterRulePatch parseWeeChatPositionalPatch(
      String buffer, String tags, String regex) {
    String scope = normalizeWeeChatBufferTokenToScope(buffer);

    EnumSet<LogKind> kinds = EnumSet.noneOf(LogKind.class);
    boolean kindsSpecified = false;
    List<String> from = new ArrayList<>();
    boolean fromSpecified = false;

    String tagExpr = Objects.toString(tags, "").trim();
    if (!tagExpr.isEmpty() && !tagExpr.equals("*")) {
      // WeeChat uses tags like "irc_join", "irc_notice", "nick_toto" etc.
      // We support a pragmatic subset (parity-first) by mapping tags to our coarse LogKind buckets.
      String[] parts = tagExpr.split("[,+]");
      for (String p : parts) {
        String s = Objects.toString(p, "").trim();
        if (s.isEmpty()) continue;
        String sl = s.toLowerCase(Locale.ROOT);

        if (sl.startsWith("nick_") && sl.length() > 5) {
          fromSpecified = true;
          from.add(sl.substring(5));
          continue;
        }

        if (sl.contains("notice")) {
          kinds.add(LogKind.NOTICE);
          kindsSpecified = true;
        } else if (sl.contains("error")) {
          kinds.add(LogKind.ERROR);
          kindsSpecified = true;
        } else if (sl.contains("action")) {
          kinds.add(LogKind.ACTION);
          kindsSpecified = true;
        } else if (sl.contains("join")
            || sl.contains("part")
            || sl.contains("quit")
            || sl.contains("nick")
            || sl.contains("away")) {
          kinds.add(LogKind.PRESENCE);
          kindsSpecified = true;
        } else if (sl.contains("topic") || sl.contains("mode") || sl.contains("status")) {
          kinds.add(LogKind.STATUS);
          kindsSpecified = true;
        } else if (sl.contains("privmsg")
            || sl.contains("chat")
            || sl.equals("msg")
            || sl.endsWith("_msg")) {
          kinds.add(LogKind.CHAT);
          kindsSpecified = true;
        }
      }
    }

    RegexSpec textRegex = null;
    boolean textSpecified = false;
    String r = Objects.toString(regex, "").trim();
    if (!r.isEmpty()) {
      textSpecified = true;
      // WeeChat positional regex is already a regex; we also allow glob:... for convenience.
      textRegex = parseTextPattern(r);
    }

    if (fromSpecified) {
      from = from.stream().filter(s -> s != null && !s.trim().isEmpty()).map(String::trim).toList();
    }

    return new FilterCommand.FilterRulePatch(
        scope,
        true,
        null,
        false,
        null,
        false,
        null,
        false,
        kinds,
        kindsSpecified,
        from,
        fromSpecified,
        tagExpr,
        (!tagExpr.isEmpty() && !tagExpr.equals("*")),
        textRegex,
        textSpecified);
  }

  private static String normalizeWeeChatBufferTokenToScope(String buffer) {
    String b = Objects.toString(buffer, "").trim();
    if (b.isEmpty() || b.equals("*")) return "*";

    // Common WeeChat form: irc.<server>.<buffer>
    String bl = b.toLowerCase(Locale.ROOT);
    if (bl.startsWith("irc.")) {
      String rest = b.substring(4);
      String[] parts = rest.split("\\.");
      if (parts.length >= 2) {
        String server = parts[0];
        String target = rest.substring(server.length() + 1);
        // target may still contain '.'; keep it.
        return normalizeScopePattern(server + "/" + target);
      }
    }

    // If they provided <server>/<target>, normalize as usual.
    if (b.contains("/")) {
      return normalizeScopePattern(b);
    }

    // Fallback: treat as our shorthand.
    return normalizeScopePattern(b);
  }

  private static FilterCommand parseExport(List<String> toks) {
    // /filter export [format=cmd|all] [file=<path>]
    String format = "all";
    String file = null;

    for (int i = 2; i < toks.size(); i++) {
      String t = toks.get(i);
      int eq = t.indexOf('=');
      if (eq < 0) {
        // Allow a single positional format token: cmd|all
        String v = t.trim().toLowerCase(Locale.ROOT);
        if (v.equals("cmd") || v.equals("all")) {
          format = v;
        } else {
          return new FilterCommand.Error(
              "Invalid token for /filter export: '" + t + "' (expected format=... or file=...)");
        }
        continue;
      }

      String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      String val = t.substring(eq + 1).trim();
      switch (key) {
        case "format" -> format = val.trim().toLowerCase(Locale.ROOT);
        case "file", "path" -> file = val;
        default -> {
          return new FilterCommand.Error("Unknown key for /filter export: '" + key + "'");
        }
      }
    }

    if (!format.equals("cmd") && !format.equals("all")) {
      return new FilterCommand.Error(
          "Invalid export format: '" + format + "' (expected cmd or all)");
    }

    return new FilterCommand.Export(format, file);
  }

  private static FilterCommand parseMove(List<String> toks) {
    // /filter move <name> <pos|top|bottom|up|down|before|after> [arg]
    if (toks.size() < 4) {
      return new FilterCommand.Error(
          "Usage: /filter move <name> <pos|top|bottom|up [n]|down [n]|before <other>|after <other>>");
    }

    String name = toks.get(2);

    // Support a single key=value form, e.g. /filter move foo to=3
    if (toks.size() == 4) {
      String t = toks.get(3);
      int eq = t.indexOf('=');
      if (eq > 0) {
        String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String val = t.substring(eq + 1).trim();
        return switch (key) {
          case "to", "pos", "position" -> {
            Integer pos = safeParseInt(val);
            if (pos == null) yield new FilterCommand.Error("Invalid position: '" + val + "'");
            yield new FilterCommand.Move(name, FilterCommand.MoveMode.TO, pos, 1, null);
          }
          case "top" -> new FilterCommand.Move(name, FilterCommand.MoveMode.TOP, null, 1, null);
          case "bottom" ->
              new FilterCommand.Move(name, FilterCommand.MoveMode.BOTTOM, null, 1, null);
          case "up" -> {
            Integer n = safeParseInt(val);
            if (n == null || n < 1)
              yield new FilterCommand.Error("Invalid move amount: '" + val + "'");
            yield new FilterCommand.Move(name, FilterCommand.MoveMode.UP, null, n, null);
          }
          case "down" -> {
            Integer n = safeParseInt(val);
            if (n == null || n < 1)
              yield new FilterCommand.Error("Invalid move amount: '" + val + "'");
            yield new FilterCommand.Move(name, FilterCommand.MoveMode.DOWN, null, n, null);
          }
          case "before" -> {
            if (val.isBlank())
              yield new FilterCommand.Error("Usage: /filter move <name> before <other>");
            yield new FilterCommand.Move(name, FilterCommand.MoveMode.BEFORE, null, 1, val);
          }
          case "after" -> {
            if (val.isBlank())
              yield new FilterCommand.Error("Usage: /filter move <name> after <other>");
            yield new FilterCommand.Move(name, FilterCommand.MoveMode.AFTER, null, 1, val);
          }
          default -> new FilterCommand.Error("Unknown key for /filter move: '" + key + "'");
        };
      }
    }

    String spec = toks.get(3).trim().toLowerCase(Locale.ROOT);
    switch (spec) {
      case "top" -> {
        if (toks.size() != 4) return new FilterCommand.Error("Usage: /filter move <name> top");
        return new FilterCommand.Move(name, FilterCommand.MoveMode.TOP, null, 1, null);
      }
      case "bottom" -> {
        if (toks.size() != 4) return new FilterCommand.Error("Usage: /filter move <name> bottom");
        return new FilterCommand.Move(name, FilterCommand.MoveMode.BOTTOM, null, 1, null);
      }
      case "up" -> {
        if (toks.size() > 5) return new FilterCommand.Error("Usage: /filter move <name> up [n]");
        int n = 1;
        if (toks.size() == 5) {
          Integer parsed = safeParseInt(toks.get(4));
          if (parsed == null || parsed < 1)
            return new FilterCommand.Error("Invalid move amount: '" + toks.get(4) + "'");
          n = parsed;
        }
        return new FilterCommand.Move(name, FilterCommand.MoveMode.UP, null, n, null);
      }
      case "down" -> {
        if (toks.size() > 5) return new FilterCommand.Error("Usage: /filter move <name> down [n]");
        int n = 1;
        if (toks.size() == 5) {
          Integer parsed = safeParseInt(toks.get(4));
          if (parsed == null || parsed < 1)
            return new FilterCommand.Error("Invalid move amount: '" + toks.get(4) + "'");
          n = parsed;
        }
        return new FilterCommand.Move(name, FilterCommand.MoveMode.DOWN, null, n, null);
      }
      case "before" -> {
        if (toks.size() != 5)
          return new FilterCommand.Error("Usage: /filter move <name> before <other>");
        return new FilterCommand.Move(name, FilterCommand.MoveMode.BEFORE, null, 1, toks.get(4));
      }
      case "after" -> {
        if (toks.size() != 5)
          return new FilterCommand.Error("Usage: /filter move <name> after <other>");
        return new FilterCommand.Move(name, FilterCommand.MoveMode.AFTER, null, 1, toks.get(4));
      }
      default -> {
        if (toks.size() != 4) {
          return new FilterCommand.Error(
              "Usage: /filter move <name> <pos|top|bottom|up [n]|down [n]|before <other>|after <other>>");
        }
        Integer pos = safeParseInt(spec);
        if (pos == null) {
          return new FilterCommand.Error("Invalid position: '" + toks.get(3) + "'");
        }
        return new FilterCommand.Move(name, FilterCommand.MoveMode.TO, pos, 1, null);
      }
    }
  }

  private static Integer safeParseInt(String s) {
    try {
      return Integer.parseInt(Objects.toString(s, "").trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static FilterCommand parseShow(List<String> toks) {
    // /filter show [on|off|toggle|default] [target=<scope>]
    FilterCommand.ToggleMode mode = FilterCommand.ToggleMode.TOGGLE;
    String scope = null;

    int i = 2;
    if (toks.size() > i && !toks.get(i).contains("=")) {
      mode = parseToggleMode(toks.get(i));
      if (mode == null)
        return new FilterCommand.Error(
            "Invalid mode for /filter show: '" + toks.get(i) + "' (use on|off|toggle|default)");
      i++;
    }

    for (; i < toks.size(); i++) {
      String t = toks.get(i);
      int eq = t.indexOf('=');
      if (eq < 0) return new FilterCommand.Error("Invalid token: '" + t + "' (expected key=value)");
      String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      String val = t.substring(eq + 1).trim();

      if (key.equals("target") || key.equals("scope")) {
        scope = normalizeScopePattern(val);
      } else {
        return new FilterCommand.Error(
            "Unknown key for /filter show: '" + key + "' (allowed: target=)");
      }
    }

    return new FilterCommand.Show(mode, scope);
  }

  private static FilterCommand parsePlaceholders(List<String> toks) {
    // /filter placeholders [on|off|toggle|default] [target=<scope>]
    // /filter placeholders collapsed [on|off|toggle|default] [target=<scope>]
    boolean collapsed = false;
    int i = 2;

    if (toks.size() > i && toks.get(i).equalsIgnoreCase("collapsed")) {
      collapsed = true;
      i++;
    }

    FilterCommand.ToggleMode mode = FilterCommand.ToggleMode.TOGGLE;
    String scope = null;

    if (toks.size() > i && !toks.get(i).contains("=")) {
      mode = parseToggleMode(toks.get(i));
      if (mode == null) {
        return new FilterCommand.Error(
            "Invalid mode for /filter placeholders: '"
                + toks.get(i)
                + "' (use on|off|toggle|default)");
      }
      i++;
    }

    for (; i < toks.size(); i++) {
      String t = toks.get(i);
      int eq = t.indexOf('=');
      if (eq < 0) return new FilterCommand.Error("Invalid token: '" + t + "' (expected key=value)");
      String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      String val = t.substring(eq + 1).trim();

      if (key.equals("target") || key.equals("scope")) {
        scope = normalizeScopePattern(val);
      } else {
        return new FilterCommand.Error(
            "Unknown key for /filter placeholders: '" + key + "' (allowed: target=)");
      }
    }

    if (collapsed) return new FilterCommand.PlaceholdersCollapsed(mode, scope);
    return new FilterCommand.Placeholders(mode, scope);
  }

  private static FilterCommand parsePlaceholderPreview(List<String> toks) {
    // /filter placeholder-preview <n>
    // /filter placeholder-preview max=<n>
    if (toks.size() < 3)
      return new FilterCommand.Error("Usage: /filter placeholder-preview <0..25>");

    String t = toks.get(2).trim();
    int n;

    if (t.contains("=")) {
      int eq = t.indexOf('=');
      String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      String val = t.substring(eq + 1).trim();
      if (!(key.equals("max") || key.equals("n") || key.equals("lines") || key.equals("preview"))) {
        return new FilterCommand.Error(
            "Unknown key for /filter placeholder-preview: '" + key + "'");
      }
      Integer parsed = parseInt(val);
      if (parsed == null)
        return new FilterCommand.Error("Invalid integer for placeholder-preview: '" + val + "'");
      n = parsed;
    } else {
      Integer parsed = parseInt(t);
      if (parsed == null)
        return new FilterCommand.Error("Invalid integer for placeholder-preview: '" + t + "'");
      n = parsed;
    }

    return new FilterCommand.PlaceholderPreview(n);
  }

  private static FilterCommand parseDefaults(List<String> toks) {
    // /filter defaults filters=on placeholders=on collapsed=on preview=3
    if (toks.size() < 3) {
      return new FilterCommand.Error(
          "Usage: /filter defaults filters=on|off placeholders=on|off collapsed=on|off preview=<0..25> maxrun=<0..50000> maxtags=<0..500> maxbatch=<0..5000> history=on|off");
    }

    Boolean filters = null;
    boolean filtersSpecified = false;
    Boolean placeholders = null;
    boolean placeholdersSpecified = false;
    Boolean collapsed = null;
    boolean collapsedSpecified = false;
    Integer preview = null;
    boolean previewSpecified = false;

    Integer maxRun = null;
    boolean maxRunSpecified = false;
    Integer maxTags = null;
    boolean maxTagsSpecified = false;
    Integer maxBatch = null;
    boolean maxBatchSpecified = false;
    Boolean history = null;
    boolean historySpecified = false;

    for (int i = 2; i < toks.size(); i++) {
      String t = toks.get(i);
      int eq = t.indexOf('=');
      if (eq < 0) return new FilterCommand.Error("Invalid token: '" + t + "' (expected key=value)");

      String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      String val = t.substring(eq + 1).trim();

      switch (key) {
        case "filters", "enabled", "enabledbydefault", "filtersenabledbydefault" -> {
          filtersSpecified = true;
          Boolean b = parseBoolean(val);
          if (b == null)
            return new FilterCommand.Error("Invalid boolean for filters=: '" + val + "'");
          filters = b;
        }
        case "placeholders", "placeholdersenabledbydefault" -> {
          placeholdersSpecified = true;
          Boolean b = parseBoolean(val);
          if (b == null)
            return new FilterCommand.Error("Invalid boolean for placeholders=: '" + val + "'");
          placeholders = b;
        }
        case "collapsed", "placeholderscollapsedbydefault" -> {
          collapsedSpecified = true;
          Boolean b = parseBoolean(val);
          if (b == null)
            return new FilterCommand.Error("Invalid boolean for collapsed=: '" + val + "'");
          collapsed = b;
        }
        case "preview", "placeholderpreview", "placeholdermaxpreviewlines" -> {
          previewSpecified = true;
          Integer p = parseInt(val);
          if (p == null)
            return new FilterCommand.Error("Invalid integer for preview=: '" + val + "'");
          preview = p;
        }
        case "maxrun", "maxrunlines", "placeholdermaxlinesperrun", "runmax", "runcap" -> {
          maxRunSpecified = true;
          Integer p = parseInt(val);
          if (p == null)
            return new FilterCommand.Error("Invalid integer for maxrun=: '" + val + "'");
          maxRun = p;
        }
        case "maxtags", "tooltipmaxtags", "placeholdertooltipmaxtags" -> {
          maxTagsSpecified = true;
          Integer p = parseInt(val);
          if (p == null)
            return new FilterCommand.Error("Invalid integer for maxtags=: '" + val + "'");
          maxTags = p;
        }
        case "maxbatch",
            "maxbatchruns",
            "maxhistoryruns",
            "historymaxruns",
            "batchcap",
            "historybatchcap" -> {
          maxBatchSpecified = true;
          Integer p = parseInt(val);
          if (p == null)
            return new FilterCommand.Error("Invalid integer for maxbatch=: '" + val + "'");
          maxBatch = p;
        }
        case "history",
            "historyplaceholders",
            "historyplaceholdersenabled",
            "historyplaceholdersenabledbydefault" -> {
          historySpecified = true;
          Boolean b = parseBoolean(val);
          if (b == null)
            return new FilterCommand.Error("Invalid boolean for history=: '" + val + "'");
          history = b;
        }
        default -> {
          return new FilterCommand.Error("Unknown key for /filter defaults: '" + key + "'");
        }
      }
    }

    return new FilterCommand.Defaults(
        filters, filtersSpecified,
        placeholders, placeholdersSpecified,
        collapsed, collapsedSpecified,
        preview, previewSpecified,
        maxRun, maxRunSpecified,
        maxTags, maxTagsSpecified,
        maxBatch, maxBatchSpecified,
        history, historySpecified);
  }

  private static FilterCommand parseOverride(List<String> toks) {
    // /filter override list [format=table|cmd]
    // /filter override set scope=<glob> filters=on|off|default placeholders=on|off|default
    // collapsed=on|off|default
    // /filter override del scope=<glob>
    if (toks.size() < 3) {
      return new FilterCommand.Error("Usage: /filter override list|set|del ...");
    }

    String sub = toks.get(2).trim().toLowerCase(Locale.ROOT);
    switch (sub) {
      case "list" -> {
        String format = "table";
        for (int i = 3; i < toks.size(); i++) {
          String t = toks.get(i);
          int eq = t.indexOf('=');
          if (eq < 0)
            return new FilterCommand.Error("Invalid token: '" + t + "' (expected key=value)");
          String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
          String val = t.substring(eq + 1).trim();
          if (key.equals("format")) {
            format = val;
          } else {
            return new FilterCommand.Error("Unknown key for /filter override list: '" + key + "'");
          }
        }
        return new FilterCommand.OverrideList(format);
      }
      case "del", "delete", "rm", "remove" -> {
        if (toks.size() < 4)
          return new FilterCommand.Error("Usage: /filter override del scope=<glob>");
        String scope = null;

        // allow positional: /filter override del libera/*
        if (toks.size() == 4 && !toks.get(3).contains("=")) {
          scope = normalizeScopePattern(toks.get(3));
        } else {
          for (int i = 3; i < toks.size(); i++) {
            String t = toks.get(i);
            int eq = t.indexOf('=');
            if (eq < 0)
              return new FilterCommand.Error("Invalid token: '" + t + "' (expected key=value)");
            String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = t.substring(eq + 1).trim();
            if (key.equals("scope") || key.equals("target")) scope = normalizeScopePattern(val);
            else
              return new FilterCommand.Error(
                  "Unknown key for /filter override del: '" + key + "' (allowed: scope=)");
          }
        }
        if (scope == null || scope.isBlank())
          return new FilterCommand.Error("Usage: /filter override del scope=<glob>");
        return new FilterCommand.OverrideDel(scope);
      }
      case "set" -> {
        if (toks.size() < 4)
          return new FilterCommand.Error(
              "Usage: /filter override set scope=<glob> filters=... placeholders=... collapsed=...");

        String scope = null;

        FilterCommand.TriState filters = FilterCommand.TriState.DEFAULT;
        boolean filtersSpecified = false;
        FilterCommand.TriState placeholders = FilterCommand.TriState.DEFAULT;
        boolean placeholdersSpecified = false;
        FilterCommand.TriState collapsed = FilterCommand.TriState.DEFAULT;
        boolean collapsedSpecified = false;

        // allow positional scope as first arg
        int i = 3;
        if (toks.size() > i && !toks.get(i).contains("=")) {
          scope = normalizeScopePattern(toks.get(i));
          i++;
        }

        for (; i < toks.size(); i++) {
          String t = toks.get(i);
          int eq = t.indexOf('=');
          if (eq < 0)
            return new FilterCommand.Error("Invalid token: '" + t + "' (expected key=value)");
          String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
          String val = t.substring(eq + 1).trim();

          switch (key) {
            case "scope", "target" -> scope = normalizeScopePattern(val);
            case "filters", "filter", "show" -> {
              filtersSpecified = true;
              FilterCommand.TriState ts = parseTriState(val);
              if (ts == null)
                return new FilterCommand.Error(
                    "Invalid value for filters=: '" + val + "' (use on|off|default)");
              filters = ts;
            }
            case "placeholders" -> {
              placeholdersSpecified = true;
              FilterCommand.TriState ts = parseTriState(val);
              if (ts == null)
                return new FilterCommand.Error(
                    "Invalid value for placeholders=: '" + val + "' (use on|off|default)");
              placeholders = ts;
            }
            case "collapsed" -> {
              collapsedSpecified = true;
              FilterCommand.TriState ts = parseTriState(val);
              if (ts == null)
                return new FilterCommand.Error(
                    "Invalid value for collapsed=: '" + val + "' (use on|off|default)");
              collapsed = ts;
            }
            default -> {
              return new FilterCommand.Error("Unknown key for /filter override set: '" + key + "'");
            }
          }
        }

        if (scope == null || scope.isBlank())
          return new FilterCommand.Error("Usage: /filter override set scope=<glob> ...");

        return new FilterCommand.OverrideSet(
            scope,
            filters,
            filtersSpecified,
            placeholders,
            placeholdersSpecified,
            collapsed,
            collapsedSpecified);
      }
      default -> {
        return new FilterCommand.Error(
            "Unknown /filter override subcommand: '" + sub + "'. Try: /filter override list");
      }
    }
  }

  private static FilterCommand.ToggleMode parseToggleMode(String raw) {
    String v = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "on", "yes", "true", "1" -> FilterCommand.ToggleMode.ON;
      case "off", "no", "false", "0" -> FilterCommand.ToggleMode.OFF;
      case "toggle", "flip" -> FilterCommand.ToggleMode.TOGGLE;
      case "default", "inherit" -> FilterCommand.ToggleMode.DEFAULT;
      default -> null;
    };
  }

  private static FilterCommand.TriState parseTriState(String raw) {
    String v = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "on", "yes", "true", "1" -> FilterCommand.TriState.ON;
      case "off", "no", "false", "0" -> FilterCommand.TriState.OFF;
      case "default", "inherit", "" -> FilterCommand.TriState.DEFAULT;
      default -> null;
    };
  }

  private static Boolean parseBoolean(String val) {
    String v = Objects.toString(val, "").trim().toLowerCase(Locale.ROOT);
    if (v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("on")) return Boolean.TRUE;
    if (v.equals("false") || v.equals("0") || v.equals("no") || v.equals("off"))
      return Boolean.FALSE;
    return null;
  }

  private static Integer parseInt(String raw) {
    try {
      return Integer.parseInt(Objects.toString(raw, "").trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static FilterCommand.FilterRulePatch parseRulePatch(List<String> toks, int startIdx) {
    String scope = "";
    boolean scopeSpecified = false;
    Boolean enabled = null;
    boolean enabledSpecified = false;
    FilterAction action = null;
    boolean actionSpecified = false;
    FilterDirection direction = null;
    boolean directionSpecified = false;
    EnumSet<LogKind> kinds = EnumSet.noneOf(LogKind.class);
    boolean kindsSpecified = false;
    List<String> from = new ArrayList<>();
    boolean fromSpecified = false;
    String tagsExpr = "";
    boolean tagsSpecified = false;
    RegexSpec textRegex = null;
    boolean textSpecified = false;

    for (int i = startIdx; i < toks.size(); i++) {
      String t = toks.get(i);
      int eq = t.indexOf('=');
      if (eq < 0)
        throw new IllegalArgumentException("Invalid token: '" + t + "' (expected key=value)");
      String key = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      String val = t.substring(eq + 1);
      val = (val == null) ? "" : val.trim();

      switch (key) {
        case "scope" -> {
          scopeSpecified = true;
          scope = normalizeScopePattern(val);
        }
        case "enabled" -> {
          enabledSpecified = true;
          enabled = parseBoolean(val);
          if (enabled == null)
            throw new IllegalArgumentException("Invalid boolean for enabled=: '" + val + "'");
        }
        case "action" -> {
          actionSpecified = true;
          String a = val.trim().toLowerCase(Locale.ROOT);
          action =
              switch (a) {
                case "", "hide" -> FilterAction.HIDE;
                case "dim", "deemphasize", "de-emphasize" -> FilterAction.DIM;
                case "highlight", "hl", "emphasize", "emphasise" -> FilterAction.HIGHLIGHT;
                default ->
                    throw new IllegalArgumentException(
                        "Unknown action: '" + val + "' (use one of: hide, dim, highlight)");
              };
        }
        case "dir" -> {
          directionSpecified = true;
          direction = parseDirection(val);
          if (direction == null)
            throw new IllegalArgumentException("Invalid dir=: '" + val + "' (use in|out|any)");
        }
        case "kind", "kinds" -> {
          kindsSpecified = true;
          kinds = parseKinds(val);
          if (kinds == null) throw new IllegalArgumentException("Invalid kind list: '" + val + "'");
        }
        case "from" -> {
          fromSpecified = true;
          from.addAll(parseCsvOrSingle(val));
        }
        case "tag", "tags" -> {
          tagsSpecified = true;
          tagsExpr = val;
        }
        case "text", "regex" -> {
          textSpecified = true;
          textRegex = parseTextPattern(val);
          if (textRegex == null) textRegex = new RegexSpec("", EnumSet.noneOf(RegexFlag.class));
        }
        case "textglob", "globtext", "glob" -> {
          textSpecified = true;
          textRegex = parseTextPattern("glob:" + val);
        }
        default -> {
          throw new IllegalArgumentException(
              "Unknown key: '"
                  + key
                  + "'. Allowed: scope, enabled, action, dir, kind, from, tags, text");
        }
      }
    }

    if (fromSpecified) {
      from = from.stream().filter(s -> s != null && !s.trim().isEmpty()).map(String::trim).toList();
    }

    return new FilterCommand.FilterRulePatch(
        scope, scopeSpecified,
        enabled, enabledSpecified,
        action, actionSpecified,
        direction, directionSpecified,
        kinds, kindsSpecified,
        from, fromSpecified,
        tagsExpr, tagsSpecified,
        textRegex, textSpecified);
  }

  private static FilterDirection parseDirection(String val) {
    String v = Objects.toString(val, "").trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "any", "*", "" -> FilterDirection.ANY;
      case "in", "inbound" -> FilterDirection.IN;
      case "out", "outbound" -> FilterDirection.OUT;
      default -> null;
    };
  }

  private static EnumSet<LogKind> parseKinds(String val) {
    String v = Objects.toString(val, "").trim();
    if (v.isEmpty()) return EnumSet.noneOf(LogKind.class);
    String[] parts = v.split(",");
    EnumSet<LogKind> out = EnumSet.noneOf(LogKind.class);
    for (String p : parts) {
      String s = p == null ? "" : p.trim();
      if (s.isEmpty()) continue;
      try {
        out.add(LogKind.valueOf(s.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException iae) {
        return null;
      }
    }
    return out;
  }

  private static List<String> parseCsvOrSingle(String val) {
    String v = Objects.toString(val, "").trim();
    if (v.isEmpty()) return List.of();
    if (v.contains(",")) {
      String[] parts = v.split(",");
      List<String> out = new ArrayList<>();
      for (String p : parts) {
        String s = p == null ? "" : p.trim();
        if (!s.isEmpty()) out.add(s);
      }
      return out;
    }
    return List.of(v);
  }

  /**
   * Parses a text matcher.
   *
   * <p>Supported forms:
   *
   * <ul>
   *   <li><code>glob:&lt;glob&gt;</code> (simple * and ? wildcards)
   *   <li><code>re:&lt;regex&gt;</code> (raw Java regex body)
   *   <li><code>/{body}/{flags}</code> (regex literal, flags = i,m,s)
   *   <li><code>&lt;regex&gt;</code> (raw Java regex body)
   * </ul>
   */
  private static RegexSpec parseTextPattern(String val) {
    String v = Objects.toString(val, "").trim();
    if (v.isEmpty()) return new RegexSpec("", EnumSet.noneOf(RegexFlag.class));

    String vl = v.toLowerCase(Locale.ROOT);
    if (vl.startsWith("glob:")) {
      String glob = v.substring(5);
      String body = globToRegexBody(glob);
      return new RegexSpec(body, EnumSet.of(RegexFlag.I));
    }
    if (vl.startsWith("g:")) {
      String glob = v.substring(2);
      String body = globToRegexBody(glob);
      return new RegexSpec(body, EnumSet.of(RegexFlag.I));
    }
    if (vl.startsWith("re:")) {
      return new RegexSpec(v.substring(3), EnumSet.noneOf(RegexFlag.class));
    }

    return parseRegexLiteralOrBody(v);
  }

  /**
   * Accepts either a regex literal (/{body}/{flags}) or a plain pattern. Empty string clears the
   * regex.
   */
  private static RegexSpec parseRegexLiteralOrBody(String v) {
    if (v.startsWith("/") && v.length() >= 2) {
      int last = findLastUnescapedSlash(v);
      if (last > 0) {
        String body = v.substring(1, last);
        String flags = v.substring(last + 1);
        body = body.replace("\\/", "/");
        EnumSet<RegexFlag> fs = EnumSet.noneOf(RegexFlag.class);
        String fl = Objects.toString(flags, "").trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < fl.length(); i++) {
          char c = fl.charAt(i);
          if (c == 'i') fs.add(RegexFlag.I);
          if (c == 'm') fs.add(RegexFlag.M);
          if (c == 's') fs.add(RegexFlag.S);
        }
        return new RegexSpec(body, fs);
      }
    }
    return new RegexSpec(v, EnumSet.noneOf(RegexFlag.class));
  }

  private static String globToRegexBody(String glob) {
    String g = Objects.toString(glob, "");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < g.length(); i++) {
      char c = g.charAt(i);
      switch (c) {
        case '*' -> sb.append(".*");
        case '?' -> sb.append('.');
        case '\\' -> {
          // allow escaping a wildcard
          if (i + 1 < g.length()) {
            char n = g.charAt(i + 1);
            if (n == '*' || n == '?' || n == '\\') {
              sb.append(Pattern.quote(String.valueOf(n)));
              i++;
            } else {
              sb.append("\\\\");
            }
          } else {
            sb.append("\\\\");
          }
        }
        default -> {
          // escape regex meta
          if ("[](){}.^$|+?*\\".indexOf(c) >= 0) sb.append('\\');
          sb.append(c);
        }
      }
    }
    return sb.toString();
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

  /**
   * Scope shorthand support:
   *
   * <ul>
   *   <li>{@code libera} => {@code libera/*}
   *   <li>{@code #llamas} => {@code *}{@code /#llamas}
   *   <li>{@code status} => {@code *}{@code /status}
   * </ul>
   */
  private static String normalizeScopePattern(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return "*";
    if (s.equals("*")) return "*";

    if (!s.contains("/")) {
      String sl = s.toLowerCase(Locale.ROOT);
      if (sl.equals("status")) return "*/status";
      if (s.startsWith("#") || s.startsWith("&") || s.startsWith("@")) {
        return "*/" + normalizeTargetKey(s);
      }
      return s + "/*";
    }

    // If the target part looks like a channel, normalize its case.
    int sp = s.indexOf('/');
    if (sp >= 0 && sp < s.length() - 1) {
      String left = s.substring(0, sp);
      String right = s.substring(sp + 1);
      if (right.startsWith("#") || right.startsWith("&")) {
        right = normalizeTargetKey(right);
      }
      return left + "/" + right;
    }
    return s;
  }

  private static String normalizeTargetKey(String t) {
    return Objects.toString(t, "").trim().toLowerCase(Locale.ROOT);
  }

  /** Tokenizes with support for single and double quotes and backslash escapes. */
  static List<String> tokenize(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inSingle = false;
    boolean inDouble = false;
    boolean escaping = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);

      if (escaping) {
        switch (c) {
          case 'n' -> cur.append('\n');
          case 't' -> cur.append('\t');
          case '\\' -> cur.append('\\');
          case '\'' -> cur.append('\'');
          case '"' -> cur.append('"');
          default -> cur.append(c);
        }
        escaping = false;
        continue;
      }

      if (c == '\\') {
        escaping = true;
        continue;
      }

      if (inSingle) {
        if (c == '\'') {
          inSingle = false;
        } else {
          cur.append(c);
        }
        continue;
      }
      if (inDouble) {
        if (c == '"') {
          inDouble = false;
        } else {
          cur.append(c);
        }
        continue;
      }

      if (c == '\'') {
        inSingle = true;
        continue;
      }
      if (c == '"') {
        inDouble = true;
        continue;
      }

      if (Character.isWhitespace(c)) {
        if (!cur.isEmpty()) {
          out.add(cur.toString());
          cur.setLength(0);
        }
        continue;
      }

      cur.append(c);
    }

    if (escaping) {
      throw new IllegalArgumentException("Dangling escape at end of line.");
    }
    if (inSingle || inDouble) {
      throw new IllegalArgumentException("Unterminated quoted string.");
    }

    if (!cur.isEmpty()) out.add(cur.toString());
    return out;
  }
}
