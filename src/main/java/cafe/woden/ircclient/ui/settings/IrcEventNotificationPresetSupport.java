package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.model.BuiltInSound;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import java.util.Arrays;
import java.util.List;

final class IrcEventNotificationPresetSupport {
  private IrcEventNotificationPresetSupport() {}

  static List<IrcEventNotificationRule> buildPreset(Preset preset) {
    if (preset == null) return List.of();
    return switch (preset) {
      case ESSENTIAL ->
          List.of(
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.INVITE_RECEIVED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.YOU_KICKED,
                  IrcEventNotificationRule.SourceMode.ANY,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.YOU_BANNED,
                  IrcEventNotificationRule.SourceMode.ANY,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.YOU_KLINED,
                  IrcEventNotificationRule.SourceMode.ANY,
                  false));
      case MODERATION ->
          List.of(
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.KICKED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.BANNED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.OPPED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.DEOPPED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.VOICED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.DEVOICED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.HALF_OPPED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.DEHALF_OPPED,
                  IrcEventNotificationRule.SourceMode.OTHERS,
                  false),
              eventDefaultRule(
                  IrcEventNotificationRule.EventType.INVITE_RECEIVED,
                  IrcEventNotificationRule.SourceMode.ANY,
                  false));
      case ALL_EVENTS ->
          Arrays.stream(IrcEventNotificationRule.EventType.values())
              .map(
                  eventType ->
                      eventDefaultRule(eventType, IrcEventNotificationRule.SourceMode.ANY, false))
              .toList();
    };
  }

  static BuiltInSound defaultBuiltInSoundForEvent(IrcEventNotificationRule.EventType eventType) {
    return IrcEventNotificationRule.defaultBuiltInSoundForEvent(eventType);
  }

  private static IrcEventNotificationRule eventDefaultRule(
      IrcEventNotificationRule.EventType eventType,
      IrcEventNotificationRule.SourceMode sourceMode,
      boolean soundEnabled) {
    return new IrcEventNotificationRule(
        true,
        eventType,
        sourceMode,
        null,
        IrcEventNotificationRule.ChannelScope.ALL,
        null,
        true,
        IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
        true,
        true,
        soundEnabled,
        defaultBuiltInSoundForEvent(eventType).name(),
        false,
        null,
        false,
        null,
        null,
        null);
  }

  enum Preset {
    ESSENTIAL("Essential alerts (Recommended)"),
    MODERATION("Moderation focused"),
    ALL_EVENTS("All events");

    private final String label;

    Preset(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
