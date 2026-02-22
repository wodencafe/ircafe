package cafe.woden.ircclient.app.notifications;

import cafe.woden.ircclient.notify.sound.BuiltInSound;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Rule for event-driven desktop notifications (kick/ban/invite/mode changes, etc).
 */
public record IrcEventNotificationRule(
    boolean enabled,
    EventType eventType,
    SourceMode sourceMode,
    String sourcePattern,
    ChannelScope channelScope,
    String channelPatterns,
    boolean toastEnabled,
    FocusScope focusScope,
    boolean statusBarEnabled,
    boolean notificationsNodeEnabled,
    boolean soundEnabled,
    String soundId,
    boolean soundUseCustom,
    String soundCustomPath,
    boolean scriptEnabled,
    String scriptPath,
    String scriptArgs,
    String scriptWorkingDirectory
) {

  public enum EventType {
    KICKED("Kicked"),
    YOU_KICKED("You Were Kicked"),
    BANNED("Banned"),
    YOU_BANNED("You Were Banned"),
    VOICED("Voiced"),
    DEVOICED("De-Voiced"),
    OPPED("Opped"),
    DEOPPED("De-Opped"),
    HALF_OPPED("Half-Opped"),
    DEHALF_OPPED("De-Half-Opped"),
    YOU_OPPED("You Were Opped"),
    YOU_DEOPPED("You Were De-Opped"),
    YOU_VOICED("You Were Voiced"),
    YOU_DEVOICED("You Were De-Voiced"),
    YOU_HALF_OPPED("You Were Half-Opped"),
    YOU_DEHALF_OPPED("You Were De-Half-Opped"),
    PRIVATE_MESSAGE_RECEIVED("Private Message Received"),
    CTCP_RECEIVED("CTCP Request Received"),
    NOTICE_RECEIVED("Notice Received"),
    WALLOPS_RECEIVED("WALLOPS Received"),
    INVITE_RECEIVED("Invite Received"),
    USER_JOINED("User Joined Channel"),
    USER_PARTED("User Parted Channel"),
    USER_QUIT("User Quit"),
    USER_NICK_CHANGED("User Nick Changed"),
    TOPIC_CHANGED("Topic Changed"),
    NETSPLIT_DETECTED("Netsplit Detected"),
    KLINED("User K-Lined / Restricted"),
    YOU_KLINED("You Were K-Lined / Restricted");

    private final String label;

    EventType(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  public enum SourceMode {
    ANY("Any"),
    SELF("Self"),
    OTHERS("Someone else"),
    NICK_LIST("Specific nicks"),
    GLOB("Nick glob"),
    REGEX("Nick regex");

    private final String label;

    SourceMode(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  public enum ChannelScope {
    ALL("All channels"),
    ACTIVE_TARGET_ONLY("Active channel only"),
    ONLY("Only matching"),
    ALL_EXCEPT("All except matching");

    private final String label;

    ChannelScope(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  public enum FocusScope {
    ANY("Any"),
    FOREGROUND_ONLY("Foreground Only"),
    BACKGROUND_ONLY("Background Only");

    private final String label;

    FocusScope(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  public IrcEventNotificationRule {
    if (eventType == null) eventType = EventType.INVITE_RECEIVED;

    if (sourceMode == null) sourceMode = SourceMode.ANY;
    sourcePattern = normalizeToNull(sourcePattern);
    if (sourceMode == SourceMode.ANY || sourceMode == SourceMode.SELF || sourceMode == SourceMode.OTHERS) {
      sourcePattern = null;
    }

    if (channelScope == null) channelScope = ChannelScope.ALL;
    channelPatterns = normalizeToNull(channelPatterns);
    if (channelScope == ChannelScope.ALL || channelScope == ChannelScope.ACTIVE_TARGET_ONLY) {
      channelPatterns = null;
    }

    if (focusScope == null) focusScope = defaultFocusScopeForEvent(eventType);

    if (soundId == null || soundId.isBlank()) soundId = defaultBuiltInSoundForEvent(eventType).name();
    if (soundCustomPath != null && soundCustomPath.isBlank()) soundCustomPath = null;
    if (soundUseCustom && soundCustomPath == null) soundUseCustom = false;

    scriptPath = normalizeToNull(scriptPath);
    if (scriptEnabled && scriptPath == null) scriptEnabled = false;
    scriptArgs = normalizeToNull(scriptArgs);
    scriptWorkingDirectory = normalizeToNull(scriptWorkingDirectory);
  }

  public boolean matches(EventType type, String sourceNick, Boolean sourceIsSelf, String channel) {
    return matches(type, sourceNick, sourceIsSelf, channel, false, null);
  }

  public boolean matches(
      EventType type,
      String sourceNick,
      Boolean sourceIsSelf,
      String channel,
      boolean activeTargetOnSameServer,
      String activeTarget
  ) {
    if (!enabled) return false;
    if (type == null || eventType != type) return false;
    if (!matchesSource(sourceNick, sourceIsSelf)) return false;
    return matchesChannel(channel, activeTargetOnSameServer, activeTarget);
  }

  /**
   * Backward-compatible overload used by older callers.
   */
  public boolean matches(EventType type, Boolean sourceIsSelf, String channel) {
    return matches(type, null, sourceIsSelf, channel);
  }

  public boolean matchesSource(String sourceNick, Boolean sourceIsSelf) {
    return switch (sourceMode) {
      case ANY -> true;
      case SELF -> Boolean.TRUE.equals(sourceIsSelf);
      case OTHERS -> Boolean.FALSE.equals(sourceIsSelf);
      case NICK_LIST -> matchesNickList(sourcePattern, sourceNick);
      case GLOB -> {
        String nick = normalizeToNull(sourceNick);
        if (nick == null) {
          yield false;
        }
        yield matchesAnyMask(parseMaskList(sourcePattern), nick);
      }
      case REGEX -> regexMatch(sourcePattern, sourceNick);
    };
  }

  public boolean matchesChannel(String channel) {
    return matchesChannel(channel, false, null);
  }

  public boolean matchesChannel(
      String channel,
      boolean activeTargetOnSameServer,
      String activeTarget
  ) {
    String ch = normalizeToNull(channel);
    String active = normalizeToNull(activeTarget);
    List<String> masks = parseMaskList(channelPatterns);

    return switch (channelScope) {
      case ALL -> true;
      case ACTIVE_TARGET_ONLY ->
          activeTargetOnSameServer && ch != null && active != null && ch.equalsIgnoreCase(active);
      case ONLY -> ch != null && !masks.isEmpty() && matchesAnyMask(masks, ch);
      case ALL_EXCEPT -> ch == null || masks.isEmpty() || !matchesAnyMask(masks, ch);
    };
  }

  public static List<IrcEventNotificationRule> defaults() {
    List<IrcEventNotificationRule> out = new ArrayList<>();
    for (EventType t : EventType.values()) {
      out.add(new IrcEventNotificationRule(
          defaultEnabledForEvent(t),
          t,
          defaultSourceModeForEvent(t),
          null,
          ChannelScope.ALL,
          null,
          true,
          defaultFocusScopeForEvent(t),
          true,
          true,
          false,
          defaultBuiltInSoundForEvent(t).name(),
          false,
          null,
          false,
          null,
          null,
          null));
    }
    for (EventType t : defaultStatusBarAnyCompanionEvents()) {
      out.add(new IrcEventNotificationRule(
          true,
          t,
          defaultSourceModeForEvent(t),
          null,
          ChannelScope.ALL,
          null,
          false,
          FocusScope.ANY,
          true,
          true,
          false,
          defaultBuiltInSoundForEvent(t).name(),
          false,
          null,
          false,
          null,
          null,
          null));
    }
    return List.copyOf(out);
  }

  public static BuiltInSound defaultBuiltInSoundForEvent(EventType eventType) {
    if (eventType == null) return BuiltInSound.NOTIF_1;
    return switch (eventType) {
      case KICKED -> BuiltInSound.SOMEBODY_GOT_KICKED;
      case YOU_KICKED -> BuiltInSound.YOU_KICKED_1;
      case BANNED -> BuiltInSound.SOMEBODY_BANNED;
      case YOU_BANNED -> BuiltInSound.YOU_BANNED_1;
      case VOICED -> BuiltInSound.SOMEBODY_GAVE_SOMEBODY_VOICE;
      case DEVOICED -> BuiltInSound.SOMEONE_ELSE_TOOK_VOICE;
      case OPPED -> BuiltInSound.SOMEBODY_OPPED;
      case DEOPPED -> BuiltInSound.SOMEBODY_DEOPPED;
      case HALF_OPPED -> BuiltInSound.SOMEBODY_HALF_OPPED;
      case DEHALF_OPPED -> BuiltInSound.SOMEBODY_LOST_HALFOPS;
      case YOU_HALF_OPPED -> BuiltInSound.YOU_HALF_OPS;
      case YOU_DEHALF_OPPED -> BuiltInSound.YOU_HALF_OPS_REMOVED;
      case YOU_OPPED -> BuiltInSound.YOU_OPS_1;
      case YOU_DEOPPED -> BuiltInSound.YOU_DEOPPED;
      case YOU_VOICED -> BuiltInSound.YOU_VOICE_1;
      case YOU_DEVOICED -> BuiltInSound.YOU_LOST_VOICE_1;
      case PRIVATE_MESSAGE_RECEIVED -> BuiltInSound.PM_RECEIVED_1;
      case CTCP_RECEIVED -> BuiltInSound.SOMEBODY_SENT_CTCP_1;
      case NOTICE_RECEIVED -> BuiltInSound.NOTICE_RECEIVED_1;
      case WALLOPS_RECEIVED -> BuiltInSound.WALLOPS_1;
      case INVITE_RECEIVED -> BuiltInSound.CHANNEL_INVITE_1;
      case USER_JOINED -> BuiltInSound.USER_JOINED;
      case USER_PARTED -> BuiltInSound.USER_LEFT_CHANNEL;
      case USER_QUIT -> BuiltInSound.USER_DISCONNECTED_SERVER;
      case USER_NICK_CHANGED -> BuiltInSound.SOMEBODY_NICK_CHANGED;
      case TOPIC_CHANGED -> BuiltInSound.TOPIC_CHANGED_1;
      case NETSPLIT_DETECTED -> BuiltInSound.NETSPLIT_1;
      case KLINED -> BuiltInSound.USER_KLINED_1;
      case YOU_KLINED -> BuiltInSound.YOU_KLINED;
    };
  }

  private static boolean defaultEnabledForEvent(EventType eventType) {
    if (eventType == null) return false;
    return switch (eventType) {
      case PRIVATE_MESSAGE_RECEIVED,
           INVITE_RECEIVED,
           YOU_KICKED,
           YOU_BANNED,
           YOU_KLINED -> true;
      default -> false;
    };
  }

  private static FocusScope defaultFocusScopeForEvent(EventType eventType) {
    return FocusScope.BACKGROUND_ONLY;
  }

  private static List<EventType> defaultStatusBarAnyCompanionEvents() {
    return List.of(
        EventType.KICKED,
        EventType.BANNED,
        EventType.KLINED);
  }

  private static SourceMode defaultSourceModeForEvent(EventType eventType) {
    if (eventType == null) return SourceMode.ANY;
    return switch (eventType) {
      case KICKED,
           BANNED,
           VOICED,
           DEVOICED,
           OPPED,
           DEOPPED,
           HALF_OPPED,
           DEHALF_OPPED,
           PRIVATE_MESSAGE_RECEIVED,
           CTCP_RECEIVED,
           NOTICE_RECEIVED,
           WALLOPS_RECEIVED,
           INVITE_RECEIVED,
           USER_JOINED,
           USER_PARTED,
           USER_QUIT,
           USER_NICK_CHANGED,
           NETSPLIT_DETECTED,
           KLINED -> SourceMode.OTHERS;
      default -> SourceMode.ANY;
    };
  }

  private static boolean matchesNickList(String rawList, String nick) {
    String n = normalizeToNull(nick);
    if (n == null) return false;

    for (String token : parseTokenList(rawList)) {
      if (token.equalsIgnoreCase(n)) return true;
    }
    return false;
  }

  private static boolean regexMatch(String regex, String value) {
    String r = normalizeToNull(regex);
    String v = normalizeToNull(value);
    if (r == null || v == null) return false;

    try {
      return Pattern.compile(r, Pattern.CASE_INSENSITIVE).matcher(v).matches();
    } catch (PatternSyntaxException ignored) {
      return false;
    }
  }

  private static String normalizeToNull(String raw) {
    String s = Objects.toString(raw, "").trim();
    return s.isEmpty() ? null : s;
  }

  private static List<String> parseTokenList(String raw) {
    String s = normalizeToNull(raw);
    if (s == null) return List.of();

    String[] tokens = s.split("[,\\s]+");
    List<String> out = new ArrayList<>();
    for (String token : tokens) {
      String t = normalizeToNull(token);
      if (t == null) continue;
      out.add(t);
    }
    return out;
  }

  private static List<String> parseMaskList(String raw) {
    List<String> tokens = parseTokenList(raw);
    if (tokens.isEmpty()) return List.of();

    List<String> out = new ArrayList<>(tokens.size());
    for (String token : tokens) {
      out.add(token.toLowerCase(Locale.ROOT));
    }
    return out;
  }

  private static boolean matchesAnyMask(List<String> masks, String value) {
    if (masks == null || masks.isEmpty()) return false;
    String v = normalizeToNull(value);
    if (v == null) return false;
    String normalized = v.toLowerCase(Locale.ROOT);

    for (String m : masks) {
      if (globMatch(m, normalized)) return true;
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
