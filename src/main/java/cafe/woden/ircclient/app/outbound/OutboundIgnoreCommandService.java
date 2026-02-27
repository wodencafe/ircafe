package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.ignore.IgnoreLevels;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreTextPatternMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Handles outbound ignore-related slash commands extracted from {@code IrcMediator}.
 *
 * <p>Includes: /ignore, /unignore, /ignorelist, /softignore, /unsoftignore, /softignorelist.
 *
 * <p>Behavior is intended to be preserved.
 */
@Component
public class OutboundIgnoreCommandService {

  private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([a-z]*)");

  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final IgnoreListService ignoreListService;

  public OutboundIgnoreCommandService(
      UiPort ui, TargetCoordinator targetCoordinator, IgnoreListService ignoreListService) {
    this.ui = ui;
    this.targetCoordinator = targetCoordinator;
    this.ignoreListService = ignoreListService;
  }

  public void handleIgnore(String maskOrNick) {
    TargetRef active = targetCoordinator.getActiveTarget();
    IrssiIgnoreSpec spec = parseIrssiIgnoreSpec(maskOrNick);
    String sid = resolveServerIdForIgnore(active, spec.network());
    if (sid.isEmpty()) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }

    if (spec.listRequested() && !spec.except()) {
      handleIgnoreListForServer(sid, "(ignore)");
      appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
      return;
    }

    if (spec.mask().isEmpty()) {
      ui.appendStatus(
          new TargetRef(sid, "status"),
          "(ignore)",
          "Usage: /ignore [-options] [levels] <maskOrNick>");
      return;
    }

    if (spec.except()) {
      handleUnignoreForServer(sid, spec.mask(), true);
      appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
      return;
    }

    Long expiresAtEpochMs = parseExpiryEpochMs(spec.time(), System.currentTimeMillis());
    if (!spec.time().isBlank() && expiresAtEpochMs == null) {
      ui.appendStatus(
          new TargetRef(sid, "status"),
          "(ignore)",
          "Invalid -time value: \"" + spec.time() + "\" (use values like 10min, 2h, 1d, 1w).");
      return;
    }

    IgnoreTextPatternMode patternMode =
        spec.patternText().isBlank() ? IgnoreTextPatternMode.GLOB : spec.patternMode();
    if (!spec.patternText().isBlank() && patternMode == IgnoreTextPatternMode.REGEXP) {
      if (!isValidRegexPattern(spec.patternText())) {
        ui.appendStatus(
            new TargetRef(sid, "status"),
            "(ignore)",
            "Invalid -pattern regexp: \"" + spec.patternText() + "\"");
        return;
      }
    }

    IgnoreListService.AddMaskResult addResult =
        ignoreListService.addMaskWithLevels(
            sid,
            spec.mask(),
            spec.levels(),
            spec.channels(),
            expiresAtEpochMs,
            spec.patternText(),
            patternMode,
            spec.replies());
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(spec.mask());
    if (addResult == IgnoreListService.AddMaskResult.ADDED) {
      ui.appendStatus(new TargetRef(sid, "status"), "(ignore)", "Ignoring: " + stored);
    } else if (addResult == IgnoreListService.AddMaskResult.UPDATED) {
      ui.appendStatus(new TargetRef(sid, "status"), "(ignore)", "Updated ignore: " + stored);
    } else {
      ui.appendStatus(new TargetRef(sid, "status"), "(ignore)", "Already ignored: " + stored);
    }
    appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
  }

  public void handleUnignore(String maskOrNick) {
    TargetRef active = targetCoordinator.getActiveTarget();
    IrssiIgnoreSpec spec = parseIrssiIgnoreSpec(maskOrNick);
    String sid = resolveServerIdForIgnore(active, spec.network());
    if (sid.isEmpty()) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(unignore)", "Select a server first.");
      return;
    }

    if (spec.mask().isEmpty()) {
      ui.appendStatus(
          new TargetRef(sid, "status"), "(unignore)", "Usage: /unignore <maskOrNick|index>");
      return;
    }

    String arg = spec.mask();
    if (isPositiveInteger(arg)) {
      int idx = Integer.parseInt(arg);
      List<String> masks = ignoreListService.listMasks(sid);
      if (idx < 1 || idx > masks.size()) {
        ui.appendStatus(
            new TargetRef(sid, "status"),
            "(unignore)",
            "Ignore index out of range: " + idx + " (1.." + masks.size() + ")");
        return;
      }
      String byIndex = masks.get(idx - 1);
      handleUnignoreForServer(sid, byIndex, false);
      appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
      return;
    }

    handleUnignoreForServer(sid, arg, false);
    appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
  }

  private void handleUnignoreForServer(
      String serverId, String maskOrNick, boolean exceptCompatibility) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    String arg = Objects.toString(maskOrNick, "").trim();
    if (arg.isEmpty()) return;

    boolean removed = ignoreListService.removeMask(sid, arg);
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
    if (removed) {
      ui.appendStatus(new TargetRef(sid, "status"), "(unignore)", "Removed ignore: " + stored);
    } else {
      ui.appendStatus(new TargetRef(sid, "status"), "(unignore)", "Not in ignore list: " + stored);
    }

    if (exceptCompatibility) {
      ui.appendStatus(
          new TargetRef(sid, "status"),
          "(ignore)",
          "Applied irssi-style -except as /unignore (IRCafe compatibility mode).");
    }
  }

  public void handleIgnoreList() {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }

    handleIgnoreListForServer(at.serverId(), "(ignore)");
  }

  private void handleIgnoreListForServer(String serverId, String tag) {
    String sid = Objects.toString(serverId, "").trim();
    ignoreListService.pruneExpiredHardMasks(sid, System.currentTimeMillis());
    TargetRef status = new TargetRef(sid, "status");
    List<String> masks = ignoreListService.listMasks(sid);
    if (masks.isEmpty()) {
      ui.appendStatus(status, tag, "Ignore list is empty.");
      return;
    }

    ui.appendStatus(status, tag, "Ignore masks (" + masks.size() + "): ");
    for (int i = 0; i < masks.size(); i++) {
      String m = masks.get(i);
      List<String> levels =
          IgnoreLevels.normalizeConfigured(ignoreListService.levelsForHardMask(sid, m));
      List<String> channels = ignoreListService.channelsForHardMask(sid, m);
      long expiresAtEpochMs = ignoreListService.expiresAtEpochMsForHardMask(sid, m);
      String pattern = Objects.toString(ignoreListService.patternForHardMask(sid, m), "").trim();
      IgnoreTextPatternMode patternMode = ignoreListService.patternModeForHardMask(sid, m);
      boolean replies = ignoreListService.repliesForHardMask(sid, m);
      List<String> metadata = new ArrayList<>();
      if (!(levels.size() == 1 && "ALL".equalsIgnoreCase(levels.getFirst()))) {
        metadata.add("levels=" + String.join(",", levels));
      }
      if (channels != null && !channels.isEmpty()) {
        metadata.add("channels=" + String.join(",", channels));
      }
      if (expiresAtEpochMs > 0L) {
        metadata.add("expires=" + formatExpiry(expiresAtEpochMs));
      }
      if (!pattern.isEmpty()) {
        metadata.add("pattern=" + renderPattern(pattern, patternMode));
      }
      if (replies) {
        metadata.add("replies");
      }
      String suffix = metadata.isEmpty() ? "" : (" [" + String.join("; ", metadata) + "]");
      ui.appendStatus(status, tag, "  " + (i + 1) + ") " + m + suffix);
    }
  }

  public void handleSoftIgnore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(soft-ignore)", "Select a server first.");
      return;
    }

    String arg = maskOrNick == null ? "" : maskOrNick.trim();
    if (arg.isEmpty()) {
      ui.appendStatus(at, "(soft-ignore)", "Usage: /softignore <maskOrNick>");
      return;
    }

    boolean added = ignoreListService.addSoftMask(at.serverId(), arg);
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
    if (added) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"), "(soft-ignore)", "Soft-ignoring: " + stored);
    } else {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(soft-ignore)",
          "Already soft-ignored: " + stored);
    }
  }

  public void handleUnsoftIgnore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(unsoftignore)", "Select a server first.");
      return;
    }

    String arg = maskOrNick == null ? "" : maskOrNick.trim();
    if (arg.isEmpty()) {
      ui.appendStatus(at, "(unsoftignore)", "Usage: /unsoftignore <maskOrNick>");
      return;
    }

    boolean removed = ignoreListService.removeSoftMask(at.serverId(), arg);
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
    if (removed) {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(unsoftignore)",
          "Removed soft-ignore: " + stored);
    } else {
      ui.appendStatus(
          new TargetRef(at.serverId(), "status"),
          "(unsoftignore)",
          "Not in soft-ignore list: " + stored);
    }
  }

  public void handleSoftIgnoreList() {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(soft-ignore)", "Select a server first.");
      return;
    }

    List<String> masks = ignoreListService.listSoftMasks(at.serverId());
    TargetRef status = new TargetRef(at.serverId(), "status");
    if (masks.isEmpty()) {
      ui.appendStatus(status, "(soft-ignore)", "Soft-ignore list is empty.");
      return;
    }

    ui.appendStatus(status, "(soft-ignore)", "Soft-ignore masks (" + masks.size() + "): ");
    for (String m : masks) {
      ui.appendStatus(status, "(soft-ignore)", "  - " + m);
    }
  }

  private static String resolveServerIdForIgnore(TargetRef active, String network) {
    String net = Objects.toString(network, "").trim();
    if (!net.isEmpty()) return net;
    return active == null ? "" : Objects.toString(active.serverId(), "").trim();
  }

  private static boolean isPositiveInteger(String s) {
    String v = Objects.toString(s, "").trim();
    if (v.isEmpty()) return false;
    for (int i = 0; i < v.length(); i++) {
      if (!Character.isDigit(v.charAt(i))) return false;
    }
    try {
      return Integer.parseInt(v) > 0;
    } catch (Exception ignored) {
      return false;
    }
  }

  private void appendCompatibilityNotes(TargetRef out, IrssiIgnoreSpec spec) {
    if (spec == null || out == null) return;

    if (spec.patternMode() != IgnoreTextPatternMode.GLOB && spec.patternText().isBlank()) {
      ui.appendStatus(
          out,
          "(ignore)",
          "Compatibility: -regexp/-full provided without -pattern; modifier ignored.");
    }
    if (!spec.reason().isEmpty()) {
      ui.appendStatus(
          out,
          "(ignore)",
          "Compatibility: trailing reason text parsed but not persisted: \""
              + spec.reason()
              + "\"");
    }
  }

  private static IrssiIgnoreSpec parseIrssiIgnoreSpec(String raw) {
    List<String> tokens = tokenize(raw);
    if (tokens.isEmpty()) {
      return new IrssiIgnoreSpec(
          "", List.of(), "", false, false, "", IgnoreTextPatternMode.GLOB, List.of(), "", true, "");
    }

    String network = "";
    String channelsRaw = "";
    String time = "";
    String patternText = "";
    IgnoreTextPatternMode patternMode = IgnoreTextPatternMode.GLOB;
    boolean except = false;
    boolean replies = false;
    List<String> positional = new ArrayList<>();

    for (int i = 0; i < tokens.size(); i++) {
      String t = tokens.get(i);
      String low = t.toLowerCase(Locale.ROOT);
      switch (low) {
        case "-network" -> {
          if (i + 1 < tokens.size()) network = tokens.get(++i);
        }
        case "-channels" -> {
          if (i + 1 < tokens.size()) channelsRaw = tokens.get(++i);
        }
        case "-time" -> {
          if (i + 1 < tokens.size()) time = tokens.get(++i);
        }
        case "-pattern" -> {
          if (i + 1 < tokens.size()) patternText = tokens.get(++i);
        }
        case "-regexp" -> patternMode = IgnoreTextPatternMode.REGEXP;
        case "-full" -> patternMode = IgnoreTextPatternMode.FULL;
        case "-except" -> except = true;
        case "-replies" -> replies = true;
        default -> positional.add(t);
      }
    }

    List<String> channels = parseIrssiChannelsToken(channelsRaw);
    patternText = Objects.toString(patternText, "").trim();

    if (positional.isEmpty()) {
      return new IrssiIgnoreSpec(
          network,
          channels,
          time,
          except,
          replies,
          patternText,
          patternMode,
          List.of(),
          "",
          true,
          "");
    }

    List<String> levels = new ArrayList<>();
    int idx = 0;
    while (idx < positional.size()) {
      List<String> parsed = parseIrssiLevelsToken(positional.get(idx));
      if (parsed.isEmpty()) break;
      levels.addAll(parsed);
      idx++;
    }

    String mask;
    String reason;
    if (!levels.isEmpty() && idx < positional.size()) {
      mask = positional.get(idx);
      reason = String.join(" ", positional.subList(idx + 1, positional.size())).trim();
    } else {
      mask = positional.getFirst();
      reason = String.join(" ", positional.subList(1, positional.size())).trim();
      levels = List.of();
    }

    return new IrssiIgnoreSpec(
        network,
        channels,
        time,
        except,
        replies,
        patternText,
        patternMode,
        List.copyOf(levels),
        mask,
        false,
        reason);
  }

  private static List<String> parseIrssiLevelsToken(String token) {
    String t = Objects.toString(token, "").trim();
    if (t.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    String[] parts = t.split(",");
    for (String part : parts) {
      String p = Objects.toString(part, "").trim();
      if (p.isEmpty()) return List.of();
      while (p.startsWith("+") || p.startsWith("-")) {
        p = p.substring(1).trim();
      }
      if (p.isEmpty()) return List.of();
      String up = p.toUpperCase(Locale.ROOT);
      if ("*".equals(up)) up = "ALL";
      if (!IgnoreLevels.KNOWN.contains(up)) return List.of();
      out.add(up);
    }
    return out;
  }

  private static List<String> tokenize(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inSingle = false;
    boolean inDouble = false;
    boolean escaping = false;

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (escaping) {
        cur.append(c);
        escaping = false;
        continue;
      }
      if ((inSingle || inDouble) && c == '\\') {
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
        if (cur.length() > 0) {
          out.add(cur.toString());
          cur.setLength(0);
        }
        continue;
      }
      cur.append(c);
    }
    if (cur.length() > 0) out.add(cur.toString());
    return out;
  }

  private static List<String> parseIrssiChannelsToken(String token) {
    String t = Objects.toString(token, "").trim();
    if (t.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    String[] parts = t.split(",");
    for (String part : parts) {
      String p = Objects.toString(part, "").trim();
      if (p.isEmpty()) continue;
      if (!(p.startsWith("#") || p.startsWith("&"))) continue;
      if (out.stream().noneMatch(existing -> existing.equalsIgnoreCase(p))) {
        out.add(p);
      }
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private static Long parseExpiryEpochMs(String raw, long nowEpochMs) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return null;
    OptionalLong durationMs = parseIrssiDurationMs(value);
    if (durationMs.isEmpty()) return null;
    long ms = durationMs.getAsLong();
    if (ms <= 0L) return null;
    try {
      return Math.addExact(nowEpochMs, ms);
    } catch (ArithmeticException ex) {
      return Long.MAX_VALUE;
    }
  }

  private static OptionalLong parseIrssiDurationMs(String raw) {
    String s = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return OptionalLong.empty();
    s = s.replace("_", "").replace(" ", "");
    if (s.isEmpty()) return OptionalLong.empty();

    long total = 0L;
    int idx = 0;
    while (idx < s.length()) {
      Matcher m = DURATION_PART.matcher(s);
      m.region(idx, s.length());
      if (!m.lookingAt()) return OptionalLong.empty();

      long amount;
      try {
        amount = Long.parseLong(m.group(1));
      } catch (Exception ex) {
        return OptionalLong.empty();
      }
      String unit = Objects.toString(m.group(2), "");
      long factor = unitToMillis(unit);
      if (factor <= 0L) return OptionalLong.empty();

      try {
        total = Math.addExact(total, Math.multiplyExact(amount, factor));
      } catch (ArithmeticException ex) {
        return OptionalLong.of(Long.MAX_VALUE);
      }
      idx = m.end();
    }
    return (total <= 0L) ? OptionalLong.empty() : OptionalLong.of(total);
  }

  private static long unitToMillis(String rawUnit) {
    String unit = Objects.toString(rawUnit, "").trim().toLowerCase(Locale.ROOT);
    if (unit.isEmpty()) return 1_000L;
    return switch (unit) {
      case "ms", "msec", "msecs", "millisecond", "milliseconds" -> 1L;
      case "s", "sec", "secs", "second", "seconds" -> 1_000L;
      case "m", "min", "mins", "minute", "minutes" -> 60_000L;
      case "h", "hr", "hrs", "hour", "hours" -> 3_600_000L;
      case "d", "day", "days" -> 86_400_000L;
      case "w", "wk", "wks", "week", "weeks" -> 604_800_000L;
      default -> -1L;
    };
  }

  private static String formatExpiry(long expiresAtEpochMs) {
    long now = System.currentTimeMillis();
    long remaining = Math.max(0L, expiresAtEpochMs - now);
    String iso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(expiresAtEpochMs));
    return iso + " (" + formatRemaining(remaining) + ")";
  }

  private static String formatRemaining(long remainingMs) {
    if (remainingMs <= 0L) return "expired";
    long totalSeconds = remainingMs / 1_000L;
    long days = totalSeconds / 86_400L;
    long hours = (totalSeconds % 86_400L) / 3_600L;
    long mins = (totalSeconds % 3_600L) / 60L;
    long secs = totalSeconds % 60L;
    StringBuilder sb = new StringBuilder("in ");
    if (days > 0) sb.append(days).append("d");
    if (hours > 0) sb.append(hours).append("h");
    if (mins > 0) sb.append(mins).append("m");
    if (secs > 0 || sb.length() == 3) sb.append(secs).append("s");
    return sb.toString();
  }

  private static boolean isValidRegexPattern(String pattern) {
    try {
      Pattern.compile(pattern);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private static String renderPattern(String pattern, IgnoreTextPatternMode mode) {
    String p = Objects.toString(pattern, "").trim();
    if (p.isEmpty()) return "";
    IgnoreTextPatternMode m = (mode == null) ? IgnoreTextPatternMode.GLOB : mode;
    return switch (m) {
      case REGEXP -> "/" + p + "/ (regexp)";
      case FULL -> p + " (full)";
      case GLOB -> p;
    };
  }

  private record IrssiIgnoreSpec(
      String network,
      List<String> channels,
      String time,
      boolean except,
      boolean replies,
      String patternText,
      IgnoreTextPatternMode patternMode,
      List<String> levels,
      String mask,
      boolean listRequested,
      String reason) {}
}
