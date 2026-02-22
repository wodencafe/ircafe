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
  NOTIF_21("Notification 21", "sounds/notif21.mp3"),
  YOU_VOICE_1("You Voice", "sounds/voice/you_voice_1.mp3"),
  YOU_LOST_VOICE_1("You Lost Voice", "sounds/voice/you_lost_voice_1.mp3"),
  YOU_DEOPPED("You De-Opped", "sounds/voice/you_deopped.mp3"),
  SOMEBODY_GOT_KICKED("Somebody Got Kicked", "sounds/voice/somebody_got_kicked.mp3"),
  SOMEBODY_DEOPPED("Somebody De-Opped", "sounds/voice/somebody_deopped.mp3"),
  SOMEBODY_BANNED("Somebody Banned", "sounds/voice/somebody_banned.mp3"),
  SOMEBODY_OPPED("Somebody Opped", "sounds/voice/somebody_opped.mp3"),
  SOMEBODY_GAVE_SOMEBODY_VOICE("Somebody Gave Voice", "sounds/voice/somebody_gave_somebody_voice.mp3"),
  SOMEONE_ELSE_TOOK_VOICE("Someone Else Took Voice", "sounds/voice/someone_else_took_voice.mp3"),
  SOMEBODY_HALF_OPPED("Somebody Half-Opped", "sounds/voice/somebody_half_opped.mp3"),
  SOMEBODY_LOST_HALFOPS("Somebody Lost Half-Ops", "sounds/voice/somebody_lost_halfops.mp3"),
  SOMEBODY_NICK_CHANGED("Somebody Nick Changed", "sounds/voice/somebody_nick_changed.mp3"),
  CHANNEL_INVITE_1("Channel Invite", "sounds/voice/channel_invite_1.mp3"),
  WALLOPS_1("Wallops", "sounds/voice/wallops_1.mp3"),
  NETSPLIT_1("Netsplit", "sounds/voice/netsplit_1.mp3"),
  PM_RECEIVED_1("PM Received", "sounds/voice/pm_received_1.mp3"),
  SOMEBODY_SENT_CTCP_1("Somebody Sent CTCP", "sounds/voice/somebody_sent_ctcp_1.mp3"),
  NOTICE_RECEIVED_1("Notice Received", "sounds/voice/someone_notice_you_1.mp3"),
  USER_JOINED_1("User Joined", "sounds/voice/watched_online_1.mp3"),
  USER_LEFT_1("User Left", "sounds/voice/watched_user_offline_1.mp3"),
  USER_JOINED("User Joined (Alt)", "sounds/voice/user_joined.mp3"),
  USER_LEFT_CHANNEL("User Left Channel", "sounds/voice/user_left_channel.mp3"),
  USER_DISCONNECTED_SERVER("User Disconnected", "sounds/voice/user_disconnected_server.mp3"),
  TOPIC_CHANGED_1("Topic Changed", "sounds/voice/topic_changed.mp3"),
  USER_KLINED_1("User K-Lined", "sounds/voice/user_klined_1.mp3"),
  YOU_HALF_OPS("You Half-Opped", "sounds/voice/you_half_ops.mp3"),
  YOU_HALF_OPS_REMOVED("You Half-Op Removed", "sounds/voice/you_half_ops_removed.mp3"),
  YOU_KICKED_1("You Kicked", "sounds/voice/you_kicked_1.mp3"),
  YOU_BANNED_1("You Banned", "sounds/voice/you_banned_1.mp3"),
  YOU_OPS_1("You Opped", "sounds/voice/you_ops_1.mp3"),
  YOU_KLINED("You K-Lined", "sounds/voice/you_klined.mp3");

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
