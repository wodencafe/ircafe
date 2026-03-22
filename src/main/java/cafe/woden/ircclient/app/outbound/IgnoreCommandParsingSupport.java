package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.ignore.api.IgnoreLevels;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared parsing and normalization support for outbound ignore command options. */
@ApplicationLayer
final class IgnoreCommandParsingSupport {

  private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([a-z]*)");

  private IgnoreCommandParsingSupport() {}

  static IrssiIgnoreSpec parseIrssiIgnoreSpec(String raw) {
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
      String token = tokens.get(i);
      String low = token.toLowerCase(Locale.ROOT);
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
        default -> positional.add(token);
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

  static List<String> parseIrssiLevelsToken(String token) {
    String value = Objects.toString(token, "").trim();
    if (value.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    String[] parts = value.split(",");
    for (String part : parts) {
      String parsed = Objects.toString(part, "").trim();
      if (parsed.isEmpty()) return List.of();
      while (parsed.startsWith("+") || parsed.startsWith("-")) {
        parsed = parsed.substring(1).trim();
      }
      if (parsed.isEmpty()) return List.of();
      String upper = parsed.toUpperCase(Locale.ROOT);
      if ("*".equals(upper)) upper = "ALL";
      if (!IgnoreLevels.KNOWN.contains(upper)) return List.of();
      out.add(upper);
    }
    return out;
  }

  static Long parseExpiryEpochMs(String raw, long nowEpochMs) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return null;
    OptionalLong durationMs = parseIrssiDurationMs(value);
    if (durationMs.isEmpty()) return null;
    long millis = durationMs.getAsLong();
    if (millis <= 0L) return null;
    try {
      return Math.addExact(nowEpochMs, millis);
    } catch (ArithmeticException ex) {
      return Long.MAX_VALUE;
    }
  }

  static OptionalLong parseIrssiDurationMs(String raw) {
    String value = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (value.isEmpty()) return OptionalLong.empty();
    value = value.replace("_", "").replace(" ", "");
    if (value.isEmpty()) return OptionalLong.empty();

    long total = 0L;
    int idx = 0;
    while (idx < value.length()) {
      Matcher matcher = DURATION_PART.matcher(value);
      matcher.region(idx, value.length());
      if (!matcher.lookingAt()) return OptionalLong.empty();

      long amount;
      try {
        amount = Long.parseLong(matcher.group(1));
      } catch (Exception ex) {
        return OptionalLong.empty();
      }
      String unit = Objects.toString(matcher.group(2), "");
      long factor = unitToMillis(unit);
      if (factor <= 0L) return OptionalLong.empty();

      try {
        total = Math.addExact(total, Math.multiplyExact(amount, factor));
      } catch (ArithmeticException ex) {
        return OptionalLong.of(Long.MAX_VALUE);
      }
      idx = matcher.end();
    }
    return (total <= 0L) ? OptionalLong.empty() : OptionalLong.of(total);
  }

  static boolean isValidRegexPattern(String pattern) {
    try {
      Pattern.compile(pattern);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private static List<String> tokenize(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingle = false;
    boolean inDouble = false;
    boolean escaping = false;

    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (escaping) {
        current.append(c);
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
          current.append(c);
        }
        continue;
      }
      if (inDouble) {
        if (c == '"') {
          inDouble = false;
        } else {
          current.append(c);
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
        if (current.length() > 0) {
          out.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(c);
    }
    if (current.length() > 0) out.add(current.toString());
    return out;
  }

  private static List<String> parseIrssiChannelsToken(String token) {
    String value = Objects.toString(token, "").trim();
    if (value.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    String[] parts = value.split(",");
    for (String part : parts) {
      String parsed = Objects.toString(part, "").trim();
      if (parsed.isEmpty()) continue;
      if (!(parsed.startsWith("#") || parsed.startsWith("&"))) continue;
      if (out.stream().noneMatch(existing -> existing.equalsIgnoreCase(parsed))) {
        out.add(parsed);
      }
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
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

  record IrssiIgnoreSpec(
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
