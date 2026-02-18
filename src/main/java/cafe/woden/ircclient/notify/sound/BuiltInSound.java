package cafe.woden.ircclient.notify.sound;

/**
 * Bundled notification sounds shipped with IRCafe.
 *
 * <p>Phase 2: a single global sound is selected. Per-notification overrides come later.
 */
public enum BuiltInSound {

  NOTIF_1("Notification 1", "sounds/notif1.mp3"),
  NOTIF_2("Notification 2", "sounds/notif2.mp3"),
  NOTIF_3("Notification 3", "sounds/notif3.mp3"),
  NOTIF_4("Notification 4", "sounds/notif4.mp3"),
  NOTIF_5("Notification 5", "sounds/notif5.mp3"),
  NOTIF_6("Notification 6", "sounds/notif6.mp3"),
  NOTIF_7("Notification 7", "sounds/notif7.mp3"),
  NOTIF_8("Notification 8", "sounds/notif8.mp3"),
  NOTIF_9("Notification 9", "sounds/notif9.mp3"),
  NOTIF_10("Notification 10", "sounds/notif10.mp3"),
  NOTIF_11("Notification 11", "sounds/notif11.mp3"),
  NOTIF_12("Notification 12", "sounds/notif12.mp3"),
  NOTIF_13("Notification 13", "sounds/notif13.mp3"),
  NOTIF_14("Notification 14", "sounds/notif14.mp3"),
  NOTIF_15("Notification 15", "sounds/notif15.mp3"),
  NOTIF_16("Notification 16", "sounds/notif16.mp3"),
  NOTIF_17("Notification 17", "sounds/notif17.mp3"),
  NOTIF_18("Notification 18", "sounds/notif18.mp3"),
  NOTIF_19("Notification 19", "sounds/notif19.mp3"),
  NOTIF_20("Notification 20", "sounds/notif20.mp3"),
  NOTIF_21("Notification 21", "sounds/notif21.mp3");

  private final String displayName;
  private final String resourcePath;

  BuiltInSound(String displayName, String resourcePath) {
    this.displayName = displayName;
    this.resourcePath = resourcePath;
  }

  public String displayName() {
    return displayName;
  }

  public String resourcePath() {
    return resourcePath;
  }

  @Override
  public String toString() {
    return displayName;
  }

  public static BuiltInSound fromId(String id) {
    if (id == null || id.isBlank()) return NOTIF_1;
    try {
      return BuiltInSound.valueOf(id.trim());
    } catch (Exception ignored) {
      return NOTIF_1;
    }
  }
}
