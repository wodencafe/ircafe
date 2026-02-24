package cafe.woden.ircclient.notify.sound;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Bundled notification sounds shipped with IRCafe.
 *
 * <p>Phase 2: a single global sound is selected. Per-notification overrides come later.
 */
public enum BuiltInSound {

  NOTIF_1("Notification 1", "sounds/notification/notif1.mp3"),
  NOTIF_2("Notification 2", "sounds/notification/notif2.mp3"),
  NOTIF_3("Notification 3", "sounds/notification/notif3.mp3"),
  NOTIF_4("Notification 4", "sounds/notification/notif4.mp3"),
  NOTIF_5("Notification 5", "sounds/notification/notif5.mp3"),
  NOTIF_6("Notification 6", "sounds/notification/notif6.mp3"),
  NOTIF_7("Notification 7", "sounds/notification/notif7.mp3"),
  NOTIF_8("Notification 8", "sounds/notification/notif8.mp3"),
  NOTIF_9("Notification 9", "sounds/notification/notif9.mp3"),
  NOTIF_10("Notification 10", "sounds/notification/notif10.mp3"),
  NOTIF_11("Notification 11", "sounds/notification/notif11.mp3"),
  NOTIF_12("Notification 12", "sounds/notification/notif12.mp3"),
  NOTIF_13("Notification 13", "sounds/notification/notif13.mp3"),
  NOTIF_14("Notification 14", "sounds/notification/notif14.mp3"),
  NOTIF_15("Notification 15", "sounds/notification/notif15.mp3"),
  NOTIF_16("Notification 16", "sounds/notification/notif16.mp3"),
  NOTIF_17("Notification 17", "sounds/notification/notif17.mp3"),
  NOTIF_18("Notification 18", "sounds/notification/notif18.mp3"),
  NOTIF_19("Notification 19", "sounds/notification/notif19.mp3"),
  NOTIF_20("Notification 20", "sounds/notification/notif20.mp3"),
  NOTIF_21("Notification 21", "sounds/notification/notif21.mp3"),
  NOTIF_22("Notification 22", "sounds/notification/notif22.mp3"),
  NOTIF_23("Sci-Fi 1", "sounds/notification/scifi_1.mp3"),
  NOTIF_24("Sci-Fi 2", "sounds/notification/scifi_2.mp3"),
  NOTIF_25("Sci-Fi 3", "sounds/notification/scifi_3.mp3"),
  NOTIF_26("Sci-Fi 4", "sounds/notification/scifi_4.mp3"),
  NOTIF_27("Bing Ding 2", "sounds/notification/bing_ding_2.mp3"),
  NOTIF_28("Bing Ding 3", "sounds/notification/bing_ding_3.mp3"),
  NOTIF_29("Notification 29", "sounds/notification/notif29.mp3"),
  NOTIF_30("Notification 30", "sounds/notification/notif30.mp3"),
  GRUNT_1("Grunt 1", "sounds/notification/grunt1.mp3"),
  GRUNT_2("Grunt 2", "sounds/notification/grunt2.mp3"),
  GRUNT_3("Grunt 3", "sounds/notification/grunt3.mp3"),
  GRUNT_4("Grunt 4", "sounds/notification/grunt4.mp3"),
  BING_DING_1("Bing Ding 1", "sounds/notification/bing_ding_1.mp3"),
  CODE_ALPHA("Code Alpha", "sounds/voice/code_alpha.mp3"),
  CODE_BRAVO("Code Bravo", "sounds/voice/code_bravo.mp3"),
  CODE_CHARLIE("Code Charlie", "sounds/voice/code_charlie.mp3"),
  CODE_DELTA("Code Delta", "sounds/voice/code_delta.mp3"),
  CODE_ECHO("Code Echo", "sounds/voice/code_echo.mp3"),
  CODE_FOXTROT("Code Foxtrot", "sounds/voice/code_foxtrot.mp3"),
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
  PM_RECEIVED_2("PM Received (Alt 2)", "sounds/voice/pm_received_2.mp3"),
  SOMEBODY_SENT_CTCP_1("Somebody Sent CTCP", "sounds/voice/somebody_sent_ctcp_1.mp3"),
  NOTICE_RECEIVED_1("Notice Received", "sounds/voice/someone_notice_you_1.mp3"),
  NOTICE_RECEIVED_2("Notice Received (Alt 2)", "sounds/voice/someone_notice_you_2.mp3"),
  NOTICE_RECEIVED_3("Notice Received (Alt 3)", "sounds/voice/someone_notice_you_3.mp3"),
  USER_JOINED_1("User Joined", "sounds/voice/watched_online_1.mp3"),
  USER_JOINED_2("User Joined (Alt 2)", "sounds/voice/watched_online_2.mp3"),
  USER_JOINED_3("User Joined (Alt 3)", "sounds/voice/watched_online_3.mp3"),
  USER_JOINED_4("User Joined (Alt 4)", "sounds/voice/watched_user_online_4.mp3"),
  USER_LEFT_1("User Left", "sounds/voice/watched_user_offline_1.mp3"),
  USER_JOINED("User Joined (Alt)", "sounds/voice/user_joined.mp3"),
  USER_LEFT_CHANNEL("User Left Channel", "sounds/voice/user_left_channel.mp3"),
  USER_DISCONNECTED_SERVER("User Disconnected", "sounds/voice/user_disconnected_server.mp3"),
  YOU_DISCONNECTED_1("You Disconnected 1", "sounds/voice/you_disconnected_1.mp3"),
  YOU_DISCONNECTED_2("You Disconnected 2", "sounds/voice/you_disconnected_2.mp3"),
  TOPIC_CHANGED_1("Topic Changed", "sounds/voice/topic_changed.mp3"),
  USER_KLINED_1("User K-Lined", "sounds/voice/user_klined_1.mp3"),
  USER_KLINED_2("User K-Lined 2", "sounds/voice/user_klined_2.mp3"),
  YOU_HALF_OPS("You Half-Opped", "sounds/voice/you_half_ops.mp3"),
  YOU_HALF_OPS_REMOVED("You Half-Op Removed", "sounds/voice/you_half_ops_removed.mp3"),
  YOU_HIGHLIGHTED_1("You Highlighted 1", "sounds/voice/you_highlighted_1.mp3"),
  YOU_HIGHLIGHTED_2("You Highlighted 2", "sounds/voice/you_highlighted_2.mp3"),
  YOU_KICKED_1("You Kicked", "sounds/voice/you_kicked_1.mp3"),
  YOU_KICKED_2("You Kicked 2", "sounds/voice/you_kicked_2.mp3"),
  YOU_KICKED_3("You Kicked 3", "sounds/voice/you_kicked_3.mp3"),
  YOU_BANNED_1("You Banned", "sounds/voice/you_banned_1.mp3"),
  YOU_OPS_1("You Opped", "sounds/voice/you_ops_1.mp3"),
  YOU_KLINED("You K-Lined", "sounds/voice/you_klined.mp3"),
  UNKNOWN_EVENT_1("Unknown Event 1", "sounds/voice/unknown_event_1.mp3"),
  UNKNOWN_EVENT_2("Unknown Event 2", "sounds/voice/unknown_event_2.mp3"),
  UNKNOWN_EVENT_3("Unknown Event 3", "sounds/voice/unknown_event_3.mp3"),
  SOMETHING_SOMETHING_IRCAFE("Something Something IRCafe", "sounds/voice/something_something_ircafe.mp3"),
  WTF("WTF", "sounds/voice/wtf.mp3"),
  OH_SHIT_WTF_1("Oh Shit WTF 1", "sounds/voice/oh_shit_wtf_1.mp3"),
  OH_SHIT_WTF_2("Oh Shit WTF 2", "sounds/voice/oh_shit_wtf_2.mp3");

  private final String displayName;
  private final String resourcePath;
  private static final BuiltInSound[] UI_VALUES = buildUiValues();

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

  public String displayNameForUi() {
    return uiGroupLabel() + " - " + displayName;
  }

  public static BuiltInSound[] valuesForUi() {
    return UI_VALUES.clone();
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

  private String uiGroupLabel() {
    if (resourcePath.startsWith("sounds/notification/notif")) return "Notification";
    if (resourcePath.startsWith("sounds/notification/scifi_")) return "Sci-Fi";
    if (resourcePath.startsWith("sounds/notification/bing_ding_")) return "Bing Ding";
    if (resourcePath.startsWith("sounds/notification/grunt")) return "Grunt";
    return "Voice";
  }

  private int uiGroupOrder() {
    if (resourcePath.startsWith("sounds/notification/notif")) return 0;
    if (resourcePath.startsWith("sounds/notification/scifi_")) return 1;
    if (resourcePath.startsWith("sounds/notification/bing_ding_")) return 2;
    if (resourcePath.startsWith("sounds/notification/grunt")) return 3;
    return 4;
  }

  private int uiOrdinalWithinGroup() {
    if (resourcePath.startsWith("sounds/notification/notif")) {
      return numericSuffix(resourcePath, "sounds/notification/notif");
    }
    if (resourcePath.startsWith("sounds/notification/scifi_")) {
      return numericSuffix(resourcePath, "sounds/notification/scifi_");
    }
    if (resourcePath.startsWith("sounds/notification/bing_ding_")) {
      return numericSuffix(resourcePath, "sounds/notification/bing_ding_");
    }
    if (resourcePath.startsWith("sounds/notification/grunt")) {
      return numericSuffix(resourcePath, "sounds/notification/grunt");
    }
    return 0;
  }

  private static BuiltInSound[] buildUiValues() {
    BuiltInSound[] out = values().clone();
    Arrays.sort(
        out,
        Comparator.comparingInt(BuiltInSound::uiGroupOrder)
            .thenComparingInt(BuiltInSound::uiOrdinalWithinGroup)
            .thenComparing(BuiltInSound::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Enum::name));
    return out;
  }

  private static int numericSuffix(String path, String prefix) {
    if (path == null || prefix == null || !path.startsWith(prefix)) return Integer.MAX_VALUE;
    int dot = path.lastIndexOf('.');
    if (dot <= prefix.length()) return Integer.MAX_VALUE;
    String digits = path.substring(prefix.length(), dot);
    if (digits.isBlank()) return Integer.MAX_VALUE;
    try {
      return Integer.parseInt(digits);
    } catch (Exception ignored) {
      return Integer.MAX_VALUE;
    }
  }
}
