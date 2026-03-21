package cafe.woden.ircclient.config.api;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted server-tree per-channel state. */
@ApplicationLayer
public interface ServerTreeChannelStateConfigPort {

  @ApplicationLayer
  enum ServerTreeChannelSortMode {
    ALPHABETICAL("alphabetical"),
    MOST_RECENT_ACTIVITY("most-recent-activity"),
    MOST_UNREAD_MESSAGES("most-unread-messages"),
    MOST_UNREAD_NOTIFICATIONS("most-unread-notifications"),
    CUSTOM("custom");

    private final String token;

    ServerTreeChannelSortMode(String token) {
      this.token = token;
    }

    public String token() {
      return token;
    }

    public static ServerTreeChannelSortMode fromToken(String token) {
      String raw = Objects.toString(token, "").trim().toLowerCase(Locale.ROOT);
      if ("alphabetical".equals(raw) || "alpha".equals(raw) || "a-z".equals(raw)) {
        return ALPHABETICAL;
      }
      if ("most-recent-activity".equals(raw)
          || "recent-activity".equals(raw)
          || "recent".equals(raw)
          || "activity".equals(raw)) {
        return MOST_RECENT_ACTIVITY;
      }
      if ("most-unread-messages".equals(raw)
          || "most-unread-message".equals(raw)
          || "unread-messages".equals(raw)
          || "unread-message".equals(raw)
          || "unread".equals(raw)) {
        return MOST_UNREAD_MESSAGES;
      }
      if ("most-unread-notifications".equals(raw)
          || "most-unread-notification".equals(raw)
          || "unread-notifications".equals(raw)
          || "unread-notification".equals(raw)
          || "mentions".equals(raw)
          || "highlights".equals(raw)) {
        return MOST_UNREAD_NOTIFICATIONS;
      }
      return CUSTOM;
    }
  }

  @ApplicationLayer
  record ServerTreeChannelPreference(
      String channel, boolean autoReattach, boolean pinned, boolean muted) {
    public ServerTreeChannelPreference(String channel, boolean autoReattach) {
      this(channel, autoReattach, false, false);
    }

    public ServerTreeChannelPreference(String channel, boolean autoReattach, boolean pinned) {
      this(channel, autoReattach, pinned, false);
    }
  }

  @ApplicationLayer
  record ServerTreeChannelState(
      ServerTreeChannelSortMode sortMode,
      List<String> customOrder,
      List<ServerTreeChannelPreference> channels) {
    public static ServerTreeChannelState defaults() {
      return new ServerTreeChannelState(ServerTreeChannelSortMode.CUSTOM, List.of(), List.of());
    }
  }

  void rememberServerTreeChannel(String serverId, String channel);

  boolean readServerTreeChannelAutoReattach(String serverId, String channel, boolean defaultValue);

  void rememberServerTreeChannelAutoReattach(String serverId, String channel, boolean autoReattach);

  boolean readServerTreeChannelPinned(String serverId, String channel, boolean defaultValue);

  void rememberServerTreeChannelPinned(String serverId, String channel, boolean pinned);

  boolean readServerTreeChannelMuted(String serverId, String channel, boolean defaultValue);

  void rememberServerTreeChannelMuted(String serverId, String channel, boolean muted);

  void rememberServerTreeChannelSortMode(String serverId, ServerTreeChannelSortMode mode);

  void rememberServerTreeChannelCustomOrder(String serverId, List<String> channels);

  ServerTreeChannelState readServerTreeChannelState(String serverId);
}
