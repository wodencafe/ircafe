package cafe.woden.ircclient.notify.sound;

/**
 * Notification sound preferences.
 *
 * <p>Phase 3: a single global sound with a global enable toggle.
 */
public record NotificationSoundSettings(
    boolean enabled,
    String soundId,
    boolean useCustom,
    String customPath
) {

  public NotificationSoundSettings {
    if (soundId == null || soundId.isBlank()) {
      soundId = BuiltInSound.NOTIF_1.name();
    }

    if (customPath != null && customPath.isBlank()) {
      customPath = null;
    }

    // If custom is requested but we don't have a path, fall back to built-in.
    if (useCustom && customPath == null) {
      useCustom = false;
    }
  }
}
