package cafe.woden.ircclient.app.notifications;

import cafe.woden.ircclient.notify.sound.BuiltInSound;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Rule for event-driven desktop notifications (kick/ban/invite/mode changes, etc).
 */
public record IrcEventNotificationRule(
    boolean enabled,
    EventType eventType,
    SourceFilter sourceFilter,
    boolean toastEnabled,
    boolean soundEnabled,
    String soundId,
    boolean soundUseCustom,
    String soundCustomPath,
    String channelWhitelist,
    String channelBlacklist
) {

  public enum EventType {
    KICKED("Kicked"),
    BANNED("Banned"),
    VOICED("Voiced"),
    DEVOICED("De-Voiced"),
    OPPED("Opped"),
    DEOPPED("De-Opped"),
    HALF_OPPED("Half-Opped"),
    DEHALF_OPPED("De-Half-Opped"),
    INVITE_RECEIVED("Invite Received"),
    KLINED("K-Lined / Restricted");

    private final String label;

    EventType(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  public enum SourceFilter {
    ANY("Any"),
    SELF("Self"),
    OTHERS("Someone else");

    private final String label;

    SourceFilter(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  public IrcEventNotificationRule {
    if (eventType == null) eventType = EventType.INVITE_RECEIVED;
    if (sourceFilter == null) sourceFilter = SourceFilter.ANY;

    if (soundId == null || soundId.isBlank()) soundId = BuiltInSound.NOTIF_1.name();
    if (soundCustomPath != null && soundCustomPath.isBlank()) soundCustomPath = null;
    if (soundUseCustom && soundCustomPath == null) soundUseCustom = false;

    channelWhitelist = normalizeChannelMaskList(channelWhitelist);
    channelBlacklist = normalizeChannelMaskList(channelBlacklist);
  }

  public boolean matches(EventType type, Boolean sourceIsSelf, String channel) {
    if (!enabled) return false;
    if (type == null || eventType != type) return false;
    if (!matchesSource(sourceIsSelf)) return false;
    return matchesChannel(channel);
  }

  public boolean matchesSource(Boolean sourceIsSelf) {
    return switch (sourceFilter) {
      case ANY -> true;
      case SELF -> Boolean.TRUE.equals(sourceIsSelf);
      case OTHERS -> Boolean.FALSE.equals(sourceIsSelf);
    };
  }

  public boolean matchesChannel(String channel) {
    String ch = Objects.toString(channel, "").trim();
    List<String> whitelist = parseChannelMaskList(channelWhitelist);
    if (!whitelist.isEmpty()) {
      if (ch.isEmpty()) return false;
      if (!matchesAnyMask(whitelist, ch)) return false;
    }

    List<String> blacklist = parseChannelMaskList(channelBlacklist);
    if (!blacklist.isEmpty() && !ch.isEmpty() && matchesAnyMask(blacklist, ch)) {
      return false;
    }

    return true;
  }

  public static List<IrcEventNotificationRule> defaults() {
    List<IrcEventNotificationRule> out = new ArrayList<>();
    for (EventType t : EventType.values()) {
      out.add(new IrcEventNotificationRule(
          false,
          t,
          SourceFilter.ANY,
          true,
          false,
          BuiltInSound.NOTIF_1.name(),
          false,
          null,
          null,
          null));
    }
    return List.copyOf(out);
  }

  private static String normalizeChannelMaskList(String raw) {
    String s = Objects.toString(raw, "").trim();
    return s.isEmpty() ? null : s;
  }

  private static List<String> parseChannelMaskList(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return List.of();
    String[] tokens = s.split("[,\\s]+");
    List<String> out = new ArrayList<>();
    for (String t : tokens) {
      String token = Objects.toString(t, "").trim();
      if (token.isEmpty()) continue;
      out.add(token.toLowerCase(Locale.ROOT));
    }
    return out;
  }

  private static boolean matchesAnyMask(List<String> masks, String value) {
    if (masks == null || masks.isEmpty()) return false;
    String v = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
    if (v.isEmpty()) return false;
    for (String m : masks) {
      if (globMatch(m, v)) return true;
    }
    return false;
  }

  private static boolean globMatch(String mask, String value) {
    if (mask == null || mask.isEmpty()) return false;
    StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < mask.length(); i++) {
      char c = mask.charAt(i);
      if (c == '*') {
        regex.append(".*");
        continue;
      }
      if (c == '?') {
        regex.append('.');
        continue;
      }
      if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
        regex.append('\\');
      }
      regex.append(c);
    }
    regex.append('$');
    return Pattern.compile(regex.toString()).matcher(value).matches();
  }
}
