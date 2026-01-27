package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.config.UiProperties;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Formats timestamps for chat/status lines.
 */
@Component
@Lazy
public class ChatTimestampFormatter {

  private static final Logger log = LoggerFactory.getLogger(ChatTimestampFormatter.class);

  private final boolean enabled;
  private final DateTimeFormatter formatter;

  public ChatTimestampFormatter(UiProperties props) {
    UiProperties.Timestamps ts = props != null ? props.timestamps() : null;
    this.enabled = ts == null || ts.enabled();

    String pattern = ts != null ? ts.format() : null;
    DateTimeFormatter fmt;
    try {
      fmt = DateTimeFormatter.ofPattern(pattern != null ? pattern : "HH:mm:ss");
    } catch (IllegalArgumentException | DateTimeParseException e) {
      log.warn("Invalid ircafe.ui.timestamps.format '{}'; falling back to HH:mm:ss", pattern);
      fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
    }
    this.formatter = fmt;
  }

  public boolean enabled() {
    return enabled;
  }

  public String prefixNow() {
    String s;
    try {
      s = LocalTime.now().format(formatter);
    } catch (RuntimeException e) {
      s = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    return "[" + s + "] ";
  }
}
