package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Stores mutable per-server channel tree state shared across server-tree collaborators. */
public final class ServerTreeChannelStateStore {

  private final Map<String, ServerTreeDockable.ChannelSortMode> channelSortModeByServer =
      new HashMap<>();
  private final Map<String, ArrayList<String>> channelCustomOrderByServer = new HashMap<>();
  private final Map<String, Map<String, Boolean>> channelAutoReattachByServer = new HashMap<>();
  private final Map<String, Map<String, Long>> channelActivityRankByServer = new HashMap<>();
  private final Map<String, Map<String, Boolean>> channelPinnedByServer = new HashMap<>();

  public Map<String, ServerTreeDockable.ChannelSortMode> channelSortModeByServer() {
    return channelSortModeByServer;
  }

  public Map<String, ArrayList<String>> channelCustomOrderByServer() {
    return channelCustomOrderByServer;
  }

  public Map<String, Map<String, Boolean>> channelAutoReattachByServer() {
    return channelAutoReattachByServer;
  }

  public Map<String, Map<String, Long>> channelActivityRankByServer() {
    return channelActivityRankByServer;
  }

  public Map<String, Map<String, Boolean>> channelPinnedByServer() {
    return channelPinnedByServer;
  }

  public void clearServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    channelSortModeByServer.remove(sid);
    channelCustomOrderByServer.remove(sid);
    channelAutoReattachByServer.remove(sid);
    channelActivityRankByServer.remove(sid);
    channelPinnedByServer.remove(sid);
  }
}
