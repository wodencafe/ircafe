package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ChatTimestampFormatter {

  private static final Logger log = LoggerFactory.getLogger(ChatTimestampFormatter.class);

  private final UiSettingsBus settingsBus;
  private final boolean defaultEnabled;
  private final String defaultPattern;

  private volatile boolean enabled;
  private volatile String pattern;
  private volatile DateTimeFormatter formatter;

  private volatile String lastWarnedBadPattern;

  public ChatTimestampFormatter(UiProperties props, UiSettingsBus settingsBus) {
    this.settingsBus = settingsBus;

    UiProperties.Timestamps ts = props != null ? props.timestamps() : null;
    this.defaultEnabled = ts == null || ts.enabled() == null || Boolean.TRUE.equals(ts.enabled());
    this.defaultPattern = ts != null && ts.format() != null && !ts.format().isBlank() ? ts.format().trim() : "HH:mm:ss";

    // Prime caches
    refreshFromSettings();
  }

  public boolean enabled() {
    refreshFromSettings();
    return enabled;
  }

  public String prefixAt(long epochMs) {
    DateTimeFormatter fmt = ensureFormatter();
    String s;
    try {
      s = LocalTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault()).format(fmt);
    } catch (RuntimeException e) {
      s = LocalTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
          .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    return "[" + s + "] ";
  }

  public String prefixNow() {
    DateTimeFormatter fmt = ensureFormatter();
    String s;
    try {
      s = LocalTime.now().format(fmt);
    } catch (RuntimeException e) {
      s = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    return "[" + s + "] ";
  }

  private DateTimeFormatter ensureFormatter() {
    refreshFromSettings();
    DateTimeFormatter fmt = this.formatter;
    if (fmt != null) return fmt;

    // Shouldn't happen, but fall back safely.
    fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
    this.formatter = fmt;
    this.pattern = "HH:mm:ss";
    return fmt;
  }

  private void refreshFromSettings() {
    UiSettings s = settingsBus != null ? settingsBus.get() : null;

    boolean nextEnabled = s != null ? s.timestampsEnabled() : defaultEnabled;
    String nextPattern = s != null ? s.timestampFormat() : defaultPattern;
    if (nextPattern == null || nextPattern.isBlank()) nextPattern = defaultPattern;

    // Update enabled
    this.enabled = nextEnabled;

    // Update formatter only if pattern changed
    if (!nextPattern.equals(this.pattern) || this.formatter == null) {
      this.pattern = nextPattern;
      this.formatter = parseOrFallback(nextPattern);
    }
  }

  private DateTimeFormatter parseOrFallback(String pattern) {
    try {
      return DateTimeFormatter.ofPattern(pattern);
    } catch (IllegalArgumentException | DateTimeParseException e) {
      if (lastWarnedBadPattern == null || !lastWarnedBadPattern.equals(pattern)) {
        lastWarnedBadPattern = pattern;
        log.warn("Invalid timestamp format '{}' (Preferences → Chat → Timestamps); falling back to HH:mm:ss", pattern);
      }
      return DateTimeFormatter.ofPattern("HH:mm:ss");
    }
  }
}
