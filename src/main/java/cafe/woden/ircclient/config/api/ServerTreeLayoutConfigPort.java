package cafe.woden.ircclient.config.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Runtime-config contract for persisted server-tree layout and sibling ordering. */
@ApplicationLayer
public interface ServerTreeLayoutConfigPort {

  enum ServerTreeBuiltInLayoutNode {
    SERVER("server"),
    NOTIFICATIONS("notifications"),
    LOG_VIEWER("logViewer"),
    FILTERS("filters"),
    IGNORES("ignores"),
    MONITOR("monitor"),
    INTERCEPTORS("interceptors");

    private final String token;

    ServerTreeBuiltInLayoutNode(String token) {
      this.token = token;
    }

    public String token() {
      return token;
    }

    public static ServerTreeBuiltInLayoutNode fromToken(String token) {
      String raw = Objects.toString(token, "").trim().toLowerCase(Locale.ROOT);
      return switch (raw) {
        case "server", "status" -> SERVER;
        case "notifications", "notification" -> NOTIFICATIONS;
        case "logviewer", "log-viewer", "log_viewer", "logviewernode", "log_viewer_node" ->
            LOG_VIEWER;
        case "filters", "weechatfilters", "weechat-filters", "weechat_filters" -> FILTERS;
        case "ignores", "ignore" -> IGNORES;
        case "monitor" -> MONITOR;
        case "interceptors", "interceptor" -> INTERCEPTORS;
        default -> null;
      };
    }
  }

  record ServerTreeBuiltInLayout(
      List<ServerTreeBuiltInLayoutNode> rootOrder, List<ServerTreeBuiltInLayoutNode> otherOrder) {
    public static ServerTreeBuiltInLayout defaults() {
      return new ServerTreeBuiltInLayout(
          List.of(),
          List.of(
              ServerTreeBuiltInLayoutNode.SERVER,
              ServerTreeBuiltInLayoutNode.NOTIFICATIONS,
              ServerTreeBuiltInLayoutNode.LOG_VIEWER,
              ServerTreeBuiltInLayoutNode.FILTERS,
              ServerTreeBuiltInLayoutNode.IGNORES,
              ServerTreeBuiltInLayoutNode.MONITOR,
              ServerTreeBuiltInLayoutNode.INTERCEPTORS));
    }

    public boolean isDefaultLayout() {
      return this.equals(defaults());
    }
  }

  enum ServerTreeRootSiblingNode {
    CHANNEL_LIST("channelList"),
    NOTIFICATIONS("notifications"),
    OTHER("other"),
    PRIVATE_MESSAGES("privateMessages");

    private final String token;

    ServerTreeRootSiblingNode(String token) {
      this.token = token;
    }

    public String token() {
      return token;
    }

    public static ServerTreeRootSiblingNode fromToken(String token) {
      String raw = Objects.toString(token, "").trim().toLowerCase(Locale.ROOT);
      return switch (raw) {
        case "channellist", "channel-list", "channel_list" -> CHANNEL_LIST;
        case "notifications", "notification" -> NOTIFICATIONS;
        case "other" -> OTHER;
        case "privatemessages", "private-messages", "private_messages", "pm" -> PRIVATE_MESSAGES;
        default -> null;
      };
    }
  }

  record ServerTreeRootSiblingOrder(List<ServerTreeRootSiblingNode> order) {
    public static ServerTreeRootSiblingOrder defaults() {
      return new ServerTreeRootSiblingOrder(
          List.of(
              ServerTreeRootSiblingNode.CHANNEL_LIST,
              ServerTreeRootSiblingNode.NOTIFICATIONS,
              ServerTreeRootSiblingNode.OTHER,
              ServerTreeRootSiblingNode.PRIVATE_MESSAGES));
    }

    public boolean isDefaultOrder() {
      return this.equals(defaults());
    }
  }

  Map<String, ServerTreeBuiltInLayout> readServerTreeBuiltInLayoutByServer();

  void rememberServerTreeBuiltInLayout(String serverId, ServerTreeBuiltInLayout layout);

  Map<String, ServerTreeRootSiblingOrder> readServerTreeRootSiblingOrderByServer();

  void rememberServerTreeRootSiblingOrder(String serverId, ServerTreeRootSiblingOrder order);
}
