package cafe.woden.ircclient.ignore;

import java.util.Locale;
import java.util.Objects;

/** Matching mode for optional irssi-style ignore text patterns. */
public enum IgnoreTextPatternMode {
  GLOB("glob"),
  REGEXP("regexp"),
  FULL("full");

  private final String token;

  IgnoreTextPatternMode(String token) {
    this.token = token;
  }

  public String token() {
    return token;
  }

  public static IgnoreTextPatternMode fromToken(String rawToken) {
    String token = Objects.toString(rawToken, "").trim().toLowerCase(Locale.ROOT);
    return switch (token) {
      case "regexp", "regex" -> REGEXP;
      case "full" -> FULL;
      default -> GLOB;
    };
  }
}
