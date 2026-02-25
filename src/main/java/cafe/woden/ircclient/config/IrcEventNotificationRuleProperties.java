package cafe.woden.ircclient.config;

import cafe.woden.ircclient.model.BuiltInSound;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Config-backed rule definition for IRC event notifications. */
public record IrcEventNotificationRuleProperties(
    Boolean enabled,
    EventType eventType,
    SourceMode sourceMode,
    String sourcePattern,
    ChannelScope channelScope,
    String channelPatterns,
    Boolean toastEnabled,
    Boolean toastWhenFocused,
    FocusScope focusScope,
    Boolean statusBarEnabled,
    Boolean notificationsNodeEnabled,
    Boolean soundEnabled,
    String soundId,
    Boolean soundUseCustom,
    String soundCustomPath,
    Boolean scriptEnabled,
    String scriptPath,
    String scriptArgs,
    String scriptWorkingDirectory,
    SourceFilter sourceFilter,
    String channelWhitelist,
    String channelBlacklist) {

  public enum EventType {
    KICKED,
    YOU_KICKED,
    BANNED,
    YOU_BANNED,
    VOICED,
    DEVOICED,
    OPPED,
    DEOPPED,
    HALF_OPPED,
    DEHALF_OPPED,
    YOU_OPPED,
    YOU_DEOPPED,
    YOU_VOICED,
    YOU_DEVOICED,
    YOU_HALF_OPPED,
    YOU_DEHALF_OPPED,
    PRIVATE_MESSAGE_RECEIVED,
    CTCP_RECEIVED,
    NOTICE_RECEIVED,
    WALLOPS_RECEIVED,
    INVITE_RECEIVED,
    USER_JOINED,
    USER_PARTED,
    USER_QUIT,
    USER_NICK_CHANGED,
    TOPIC_CHANGED,
    NETSPLIT_DETECTED,
    KLINED,
    YOU_KLINED
  }

  public enum SourceMode {
    ANY,
    SELF,
    OTHERS,
    NICK_LIST,
    GLOB,
    REGEX
  }

  public enum ChannelScope {
    ALL,
    ACTIVE_TARGET_ONLY,
    ONLY,
    ALL_EXCEPT
  }

  public enum FocusScope {
    ANY,
    FOREGROUND_ONLY,
    BACKGROUND_ONLY
  }

  /** Legacy source mode field kept for runtime-config backward compatibility. */
  @Deprecated
  public enum SourceFilter {
    ANY,
    SELF,
    OTHERS
  }

  public IrcEventNotificationRuleProperties {
    if (enabled == null) enabled = false;
    if (eventType == null) eventType = EventType.INVITE_RECEIVED;

    if (sourceMode == null) {
      if (sourceFilter == SourceFilter.SELF) {
        sourceMode = SourceMode.SELF;
      } else if (sourceFilter == SourceFilter.OTHERS) {
        sourceMode = SourceMode.OTHERS;
      } else {
        sourceMode = SourceMode.ANY;
      }
    }
    sourcePattern = trimToNull(sourcePattern);
    if (sourceMode == SourceMode.ANY
        || sourceMode == SourceMode.SELF
        || sourceMode == SourceMode.OTHERS) {
      sourcePattern = null;
    }

    String includeLegacy = trimToNull(channelWhitelist);
    String excludeLegacy = trimToNull(channelBlacklist);

    if (channelScope == null) {
      if (includeLegacy != null) {
        channelScope = ChannelScope.ONLY;
      } else if (excludeLegacy != null) {
        channelScope = ChannelScope.ALL_EXCEPT;
      } else {
        channelScope = ChannelScope.ALL;
      }
    }

    channelPatterns = trimToNull(channelPatterns);
    if (channelPatterns == null) {
      if (channelScope == ChannelScope.ONLY) {
        channelPatterns = includeLegacy;
      } else if (channelScope == ChannelScope.ALL_EXCEPT) {
        channelPatterns = excludeLegacy;
      }
    }
    if (channelScope == ChannelScope.ALL || channelScope == ChannelScope.ACTIVE_TARGET_ONLY) {
      channelPatterns = null;
    }

    if (toastEnabled == null) toastEnabled = true;
    if (focusScope == null) {
      focusScope =
          Boolean.TRUE.equals(toastWhenFocused) ? FocusScope.ANY : FocusScope.BACKGROUND_ONLY;
    }
    if (toastWhenFocused == null) {
      toastWhenFocused = focusScope != FocusScope.BACKGROUND_ONLY;
    }
    if (soundEnabled == null) soundEnabled = false;
    if (statusBarEnabled == null) {
      // Backward-compatible default for old configs with no statusBarEnabled field.
      // Previously status-bar notices were emitted whenever toast or sound delivery ran.
      statusBarEnabled = Boolean.TRUE.equals(toastEnabled) || Boolean.TRUE.equals(soundEnabled);
    }
    if (notificationsNodeEnabled == null) notificationsNodeEnabled = true;

    if (soundId == null || soundId.isBlank())
      soundId = defaultBuiltInSoundForEvent(eventType).name();
    if (soundUseCustom == null) soundUseCustom = false;

    soundCustomPath = trimToNull(soundCustomPath);
    if (Boolean.TRUE.equals(soundUseCustom) && soundCustomPath == null) {
      soundUseCustom = false;
    }

    if (scriptEnabled == null) scriptEnabled = false;
    scriptPath = trimToNull(scriptPath);
    scriptArgs = trimToNull(scriptArgs);
    scriptWorkingDirectory = trimToNull(scriptWorkingDirectory);
    if (Boolean.TRUE.equals(scriptEnabled) && scriptPath == null) {
      scriptEnabled = false;
    }

    sourceFilter = sourceFilter != null ? sourceFilter : SourceFilter.ANY;
    channelWhitelist = includeLegacy;
    channelBlacklist = excludeLegacy;
  }

  public static List<IrcEventNotificationRuleProperties> defaultRules() {
    List<IrcEventNotificationRuleProperties> out = new ArrayList<>();
    for (EventType t : EventType.values()) {
      out.add(
          new IrcEventNotificationRuleProperties(
              defaultEnabledForEvent(t),
              t,
              defaultSourceModeForEvent(t),
              null,
              ChannelScope.ALL,
              null,
              true,
              false,
              FocusScope.BACKGROUND_ONLY,
              true,
              true,
              false,
              defaultBuiltInSoundForEvent(t).name(),
              false,
              null,
              false,
              null,
              null,
              null,
              SourceFilter.ANY,
              null,
              null));
    }
    for (EventType t : defaultStatusBarAnyCompanionEvents()) {
      out.add(
          new IrcEventNotificationRuleProperties(
              true,
              t,
              defaultSourceModeForEvent(t),
              null,
              ChannelScope.ALL,
              null,
              false,
              true,
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
              null,
              SourceFilter.ANY,
              null,
              null));
    }
    return List.copyOf(out);
  }

  private static boolean defaultEnabledForEvent(EventType eventType) {
    if (eventType == null) return false;
    return switch (eventType) {
      case PRIVATE_MESSAGE_RECEIVED, INVITE_RECEIVED, YOU_KICKED, YOU_BANNED, YOU_KLINED -> true;
      default -> false;
    };
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
          KLINED ->
          SourceMode.OTHERS;
      default -> SourceMode.ANY;
    };
  }

  private static List<EventType> defaultStatusBarAnyCompanionEvents() {
    return List.of(EventType.KICKED, EventType.BANNED, EventType.KLINED);
  }

  private static BuiltInSound defaultBuiltInSoundForEvent(EventType eventType) {
    if (eventType == null) return BuiltInSound.NOTIF_1;
    try {
      IrcEventNotificationRule.EventType mapped =
          IrcEventNotificationRule.EventType.valueOf(eventType.name());
      return IrcEventNotificationRule.defaultBuiltInSoundForEvent(mapped);
    } catch (Exception ignored) {
      return BuiltInSound.NOTIF_1;
    }
  }

  private static String trimToNull(String raw) {
    String v = Objects.toString(raw, "").trim();
    return v.isEmpty() ? null : v;
  }
}
