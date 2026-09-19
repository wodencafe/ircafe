package cafe.woden.ircclient.ui.util;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Resolves whole reaction tokens using bundled aliases; call off the EDT on first use. */
public final class EmojiShortcodeSupport {
  private static final Logger log = LoggerFactory.getLogger(EmojiShortcodeSupport.class);

  private EmojiShortcodeSupport() {}

  public static String resolve(String token) {
    String text = Objects.toString(token, "");
    String alias = text;
    if (text.length() > 2 && text.startsWith(":") && text.endsWith(":")) {
      alias = text.substring(1, text.length() - 1);
    }
    return Aliases.VALUES.getOrDefault(alias, text);
  }

  private static final class Aliases {
    private static final Map<String, String> VALUES = load();

    private static Map<String, String> load() {
      try (var stream =
          EmojiShortcodeSupport.class.getResourceAsStream("/emoji/gemoji-aliases.properties")) {
        if (stream == null) {
          log.warn("[ircafe] bundled emoji aliases are missing");
          return Map.of();
        }
        Properties properties = new Properties();
        properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        return properties.stringPropertyNames().stream()
            .collect(Collectors.toUnmodifiableMap(name -> name, properties::getProperty));
      } catch (java.io.IOException e) {
        log.warn("[ircafe] loading bundled emoji aliases failed", e);
        return Map.of();
      }
    }
  }
}
