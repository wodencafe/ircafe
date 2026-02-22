package cafe.woden.ircclient.config;

import cafe.woden.ircclient.notify.sound.BuiltInSound;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Config-backed rule definition for IRC event notifications.
 */
public record IrcEventNotificationRuleProperties(
    Boolean enabled,
    EventType eventType,
    SourceFilter sourceFilter,
    Boolean toastEnabled,
    Boolean soundEnabled,
    String soundId,
    Boolean soundUseCustom,
    String soundCustomPath,
    String channelWhitelist,
    String channelBlacklist
) {

  public enum EventType {
    KICKED,
    BANNED,
    VOICED,
    DEVOICED,
    OPPED,
    DEOPPED,
    HALF_OPPED,
    DEHALF_OPPED,
    INVITE_RECEIVED,
    KLINED
  }

  public enum SourceFilter {
    ANY,
    SELF,
    OTHERS
  }

  public IrcEventNotificationRuleProperties {
    if (enabled == null) enabled = false;
    if (eventType == null) eventType = EventType.INVITE_RECEIVED;
    if (sourceFilter == null) sourceFilter = SourceFilter.ANY;
    if (toastEnabled == null) toastEnabled = true;
    if (soundEnabled == null) soundEnabled = false;

    if (soundId == null || soundId.isBlank()) soundId = BuiltInSound.NOTIF_1.name();
    if (soundUseCustom == null) soundUseCustom = false;

    soundCustomPath = trimToNull(soundCustomPath);
    if (Boolean.TRUE.equals(soundUseCustom) && soundCustomPath == null) {
      soundUseCustom = false;
    }

    channelWhitelist = trimToNull(channelWhitelist);
    channelBlacklist = trimToNull(channelBlacklist);
  }

  public static List<IrcEventNotificationRuleProperties> defaultRules() {
    List<IrcEventNotificationRuleProperties> out = new ArrayList<>();
    for (EventType t : EventType.values()) {
      out.add(new IrcEventNotificationRuleProperties(
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

  private static String trimToNull(String raw) {
    String v = Objects.toString(raw, "").trim();
    return v.isEmpty() ? null : v;
  }
}
