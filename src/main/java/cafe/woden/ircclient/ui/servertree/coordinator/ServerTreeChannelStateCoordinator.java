package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Coordinates channel ordering, persistence, and per-channel runtime state in the tree model. */
public final class ServerTreeChannelStateCoordinator {

  public interface Context {
    String normalizeServerId(String serverId);

    DefaultMutableTreeNode channelListNode(String serverId);

    Set<TreePath> snapshotExpandedTreePaths();

    void restoreExpandedTreePaths(Set<TreePath> expanded);

    void emitManagedChannelsChanged(String serverId);
  }

  private final RuntimeConfigStore runtimeConfig;
  private final Map<String, ServerTreeDockable.ChannelSortMode> channelSortModeByServer;
  private final Map<String, ArrayList<String>> channelCustomOrderByServer;
  private final Map<String, Map<String, Boolean>> channelAutoReattachByServer;
  private final Map<String, Map<String, Long>> channelActivityRankByServer;
  private final DefaultTreeModel model;
  private final Context context;
  private long channelActivityRankCounter = 0L;

  public ServerTreeChannelStateCoordinator(
      RuntimeConfigStore runtimeConfig,
      Map<String, ServerTreeDockable.ChannelSortMode> channelSortModeByServer,
      Map<String, ArrayList<String>> channelCustomOrderByServer,
      Map<String, Map<String, Boolean>> channelAutoReattachByServer,
      Map<String, Map<String, Long>> channelActivityRankByServer,
      DefaultTreeModel model,
      Context context) {
    this.runtimeConfig = runtimeConfig;
    this.channelSortModeByServer = Objects.requireNonNull(channelSortModeByServer, "sortModes");
    this.channelCustomOrderByServer = Objects.requireNonNull(channelCustomOrderByServer, "custom");
    this.channelAutoReattachByServer =
        Objects.requireNonNull(channelAutoReattachByServer, "autoReattach");
    this.channelActivityRankByServer =
        Objects.requireNonNull(channelActivityRankByServer, "activityRanks");
    this.model = Objects.requireNonNull(model, "model");
    this.context = Objects.requireNonNull(context, "context");
  }

  public ServerTreeDockable.ChannelSortMode channelSortModeForServer(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return ServerTreeDockable.ChannelSortMode.CUSTOM;
    return channelSortModeByServer.getOrDefault(sid, ServerTreeDockable.ChannelSortMode.CUSTOM);
  }

  public void setChannelSortModeForServer(
      String serverId, ServerTreeDockable.ChannelSortMode requestedMode) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerTreeDockable.ChannelSortMode next =
        requestedMode == null ? ServerTreeDockable.ChannelSortMode.CUSTOM : requestedMode;
    ServerTreeDockable.ChannelSortMode prev =
        channelSortModeByServer.getOrDefault(sid, ServerTreeDockable.ChannelSortMode.CUSTOM);
    if (prev == next) return;
    channelSortModeByServer.put(sid, next);
    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeChannelSortMode(sid, runtimeChannelSortMode(next));
    }
    sortChannelsUnderChannelList(sid);
    context.emitManagedChannelsChanged(sid);
  }

  public void setChannelCustomOrderForServer(String serverId, List<String> channels) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ArrayList<String> normalized = normalizeCustomOrderList(channels);
    channelCustomOrderByServer.put(sid, normalized);
    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeChannelCustomOrder(sid, normalized);
    }
    if (channelSortModeByServer.getOrDefault(sid, ServerTreeDockable.ChannelSortMode.CUSTOM)
        == ServerTreeDockable.ChannelSortMode.CUSTOM) {
      sortChannelsUnderChannelList(sid);
    }
    context.emitManagedChannelsChanged(sid);
  }

  public boolean isChannelAutoReattach(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return true;
    String sid = context.normalizeServerId(ref.serverId());
    String key = foldChannelKey(ref.target());
    if (sid.isEmpty() || key.isEmpty()) return true;
    return channelAutoReattachByServer.getOrDefault(sid, Map.of()).getOrDefault(key, Boolean.TRUE);
  }

  public void setChannelAutoReattach(TargetRef ref, boolean autoReattach) {
    if (ref == null || !ref.isChannel()) return;
    String sid = context.normalizeServerId(ref.serverId());
    String channel = Objects.toString(ref.target(), "").trim();
    if (sid.isEmpty() || channel.isEmpty()) return;

    Map<String, Boolean> byChannel =
        channelAutoReattachByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    byChannel.put(foldChannelKey(channel), autoReattach);
    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeChannelAutoReattach(sid, channel, autoReattach);
    }
    context.emitManagedChannelsChanged(sid);
  }

  public void ensureChannelKnownInConfig(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return;
    String sid = context.normalizeServerId(ref.serverId());
    String channel = Objects.toString(ref.target(), "").trim();
    if (sid.isEmpty() || channel.isEmpty()) return;

    Map<String, Boolean> autoByChannel =
        channelAutoReattachByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    String key = foldChannelKey(channel);
    boolean known = autoByChannel.containsKey(key);
    if (!known) {
      boolean autoReattach =
          runtimeConfig == null
              ? true
              : runtimeConfig.readServerTreeChannelAutoReattach(sid, channel, true);
      autoByChannel.put(key, autoReattach);
      if (runtimeConfig != null) {
        runtimeConfig.rememberServerTreeChannel(sid, channel);
      }
    }

    ArrayList<String> customOrder =
        channelCustomOrderByServer.computeIfAbsent(sid, __ -> new ArrayList<>());
    if (!containsIgnoreCase(customOrder, channel)) {
      customOrder.add(channel);
      if (runtimeConfig != null) {
        runtimeConfig.rememberServerTreeChannelCustomOrder(sid, customOrder);
      }
    }
    channelActivityRankByServer.computeIfAbsent(sid, __ -> new HashMap<>()).putIfAbsent(key, 0L);
  }

  public List<ServerTreeDockable.ManagedChannelEntry> snapshotManagedChannelsForServer(
      String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    DefaultMutableTreeNode channelListNode = context.channelListNode(sid);
    if (channelListNode == null) return List.of();

    Map<String, Boolean> autoByChannel = channelAutoReattachByServer.getOrDefault(sid, Map.of());
    ArrayList<ServerTreeDockable.ManagedChannelEntry> out = new ArrayList<>();

    for (int i = 0; i < channelListNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) channelListNode.getChildAt(i);
      Object userObject = child.getUserObject();
      if (!(userObject instanceof ServerTreeNodeData nodeData)) continue;
      if (nodeData.ref == null || !nodeData.ref.isChannel()) continue;
      String channel = Objects.toString(nodeData.ref.target(), "").trim();
      if (channel.isEmpty()) continue;
      boolean autoReattach = autoByChannel.getOrDefault(foldChannelKey(channel), Boolean.TRUE);
      int notifications = Math.max(0, nodeData.unread) + Math.max(0, nodeData.highlightUnread);
      out.add(
          new ServerTreeDockable.ManagedChannelEntry(
              channel, nodeData.detached, autoReattach, notifications));
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  public void sortChannelsUnderChannelList(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    DefaultMutableTreeNode channelListNode = context.channelListNode(sid);
    if (channelListNode == null) return;

    ArrayList<DefaultMutableTreeNode> channelNodes = new ArrayList<>();
    for (int i = 0; i < channelListNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) channelListNode.getChildAt(i);
      Object userObject = child.getUserObject();
      if (!(userObject instanceof ServerTreeNodeData nodeData)
          || nodeData.ref == null
          || !nodeData.ref.isChannel()) {
        continue;
      }
      channelNodes.add(child);
    }
    if (channelNodes.size() <= 1) {
      if (channelSortModeByServer.getOrDefault(sid, ServerTreeDockable.ChannelSortMode.CUSTOM)
          == ServerTreeDockable.ChannelSortMode.CUSTOM) {
        persistCustomOrderFromTree(sid);
      }
      return;
    }

    ServerTreeDockable.ChannelSortMode sortMode =
        channelSortModeByServer.getOrDefault(sid, ServerTreeDockable.ChannelSortMode.CUSTOM);

    ArrayList<DefaultMutableTreeNode> sorted = new ArrayList<>(channelNodes);
    if (sortMode == ServerTreeDockable.ChannelSortMode.ALPHABETICAL) {
      sorted.sort((a, b) -> compareChannelLabels(channelLabelForNode(a), channelLabelForNode(b)));
    } else if (sortMode == ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY) {
      Map<String, Long> byKey = channelActivityRankByServer.getOrDefault(sid, Map.of());
      sorted.sort(
          (a, b) -> {
            String ac = channelLabelForNode(a);
            String bc = channelLabelForNode(b);
            long ai = byKey.getOrDefault(foldChannelKey(ac), 0L);
            long bi = byKey.getOrDefault(foldChannelKey(bc), 0L);
            if (ai != bi) return Long.compare(bi, ai);
            return compareChannelLabels(ac, bc);
          });
    } else {
      ArrayList<String> customOrder =
          channelCustomOrderByServer.getOrDefault(sid, new ArrayList<>());
      Map<String, Integer> byKey = new HashMap<>();
      for (int i = 0; i < customOrder.size(); i++) {
        String channel = Objects.toString(customOrder.get(i), "").trim();
        if (channel.isEmpty()) continue;
        byKey.putIfAbsent(foldChannelKey(channel), i);
      }
      sorted.sort(
          (a, b) -> {
            String ac = channelLabelForNode(a);
            String bc = channelLabelForNode(b);
            int ai = byKey.getOrDefault(foldChannelKey(ac), Integer.MAX_VALUE);
            int bi = byKey.getOrDefault(foldChannelKey(bc), Integer.MAX_VALUE);
            if (ai != bi) return Integer.compare(ai, bi);
            return compareChannelLabels(ac, bc);
          });
    }

    boolean changed = false;
    for (int i = 0; i < channelNodes.size(); i++) {
      if (channelNodes.get(i) != sorted.get(i)) {
        changed = true;
        break;
      }
    }

    if (changed) {
      Set<TreePath> expanded = context.snapshotExpandedTreePaths();
      for (DefaultMutableTreeNode node : channelNodes) {
        model.removeNodeFromParent(node);
      }
      for (int i = 0; i < sorted.size(); i++) {
        model.insertNodeInto(sorted.get(i), channelListNode, i);
      }
      context.restoreExpandedTreePaths(expanded);
    }

    if (sortMode == ServerTreeDockable.ChannelSortMode.CUSTOM) {
      persistCustomOrderFromTree(sid);
    }
  }

  public void persistCustomOrderFromTreeIfCustom(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    if (channelSortModeByServer.getOrDefault(sid, ServerTreeDockable.ChannelSortMode.CUSTOM)
        == ServerTreeDockable.ChannelSortMode.CUSTOM) {
      persistCustomOrderFromTree(sid);
    }
  }

  public void persistCustomOrderFromTree(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    DefaultMutableTreeNode channelListNode = context.channelListNode(sid);
    if (channelListNode == null) return;

    ArrayList<String> customOrder = new ArrayList<>();
    for (int i = 0; i < channelListNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) channelListNode.getChildAt(i);
      Object userObject = child.getUserObject();
      if (!(userObject instanceof ServerTreeNodeData nodeData)) continue;
      if (nodeData.ref == null || !nodeData.ref.isChannel()) continue;
      String channel = Objects.toString(nodeData.ref.target(), "").trim();
      if (channel.isEmpty()) continue;
      if (containsIgnoreCase(customOrder, channel)) continue;
      customOrder.add(channel);
    }
    channelCustomOrderByServer.put(sid, customOrder);
    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeChannelCustomOrder(sid, customOrder);
    }
  }

  public void loadChannelStateForServer(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    ServerTreeDockable.ChannelSortMode sortMode = ServerTreeDockable.ChannelSortMode.CUSTOM;
    ArrayList<String> customOrder = new ArrayList<>();
    Map<String, Boolean> autoByChannel = new HashMap<>();

    if (runtimeConfig != null) {
      RuntimeConfigStore.ServerTreeChannelState state =
          runtimeConfig.readServerTreeChannelState(sid);
      if (state != null && state.sortMode() != null) {
        sortMode = uiChannelSortMode(state.sortMode());
      }
      if (state != null && state.customOrder() != null) {
        customOrder.addAll(normalizeCustomOrderList(state.customOrder()));
      }
      if (state != null && state.channels() != null) {
        for (RuntimeConfigStore.ServerTreeChannelPreference pref : state.channels()) {
          if (pref == null) continue;
          String channel = Objects.toString(pref.channel(), "").trim();
          if (channel.isEmpty()) continue;
          autoByChannel.put(foldChannelKey(channel), pref.autoReattach());
        }
      }
    }

    channelSortModeByServer.put(sid, sortMode);
    channelCustomOrderByServer.put(sid, customOrder);
    channelAutoReattachByServer.put(sid, autoByChannel);
    channelActivityRankByServer.put(sid, new HashMap<>());
  }

  public void noteChannelActivity(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return;
    String sid = context.normalizeServerId(ref.serverId());
    String channel = Objects.toString(ref.target(), "").trim();
    String key = foldChannelKey(channel);
    if (sid.isEmpty() || key.isEmpty()) return;

    Map<String, Long> byChannel =
        channelActivityRankByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    byChannel.put(key, ++channelActivityRankCounter);

    if (channelSortModeByServer.getOrDefault(sid, ServerTreeDockable.ChannelSortMode.CUSTOM)
        == ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY) {
      sortChannelsUnderChannelList(sid);
    }
  }

  private static RuntimeConfigStore.ServerTreeChannelSortMode runtimeChannelSortMode(
      ServerTreeDockable.ChannelSortMode mode) {
    return switch (mode) {
      case ALPHABETICAL -> RuntimeConfigStore.ServerTreeChannelSortMode.ALPHABETICAL;
      case MOST_RECENT_ACTIVITY ->
          RuntimeConfigStore.ServerTreeChannelSortMode.MOST_RECENT_ACTIVITY;
      case CUSTOM -> RuntimeConfigStore.ServerTreeChannelSortMode.CUSTOM;
    };
  }

  private static ServerTreeDockable.ChannelSortMode uiChannelSortMode(
      RuntimeConfigStore.ServerTreeChannelSortMode mode) {
    if (mode == null) return ServerTreeDockable.ChannelSortMode.CUSTOM;
    return switch (mode) {
      case ALPHABETICAL -> ServerTreeDockable.ChannelSortMode.ALPHABETICAL;
      case MOST_RECENT_ACTIVITY -> ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY;
      case CUSTOM -> ServerTreeDockable.ChannelSortMode.CUSTOM;
    };
  }

  private static ArrayList<String> normalizeCustomOrderList(List<String> channels) {
    ArrayList<String> out = new ArrayList<>();
    if (channels == null || channels.isEmpty()) return out;
    for (String channel : channels) {
      String candidate = Objects.toString(channel, "").trim();
      if (!(candidate.startsWith("#") || candidate.startsWith("&"))) continue;
      if (containsIgnoreCase(out, candidate)) continue;
      out.add(candidate);
    }
    return out;
  }

  private static String channelLabelForNode(DefaultMutableTreeNode node) {
    if (node == null) return "";
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData) || nodeData.ref == null) return "";
    return Objects.toString(nodeData.ref.target(), "").trim();
  }

  private static int compareChannelLabels(String left, String right) {
    int cmp = left.compareToIgnoreCase(right);
    if (cmp != 0) return cmp;
    return left.compareTo(right);
  }

  private static String foldChannelKey(String channel) {
    return Objects.toString(channel, "").trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static boolean containsIgnoreCase(List<String> values, String needle) {
    if (values == null || values.isEmpty()) return false;
    String n = Objects.toString(needle, "").trim();
    if (n.isEmpty()) return false;
    for (String value : values) {
      if (value == null) continue;
      if (value.equalsIgnoreCase(n)) return true;
    }
    return false;
  }
}
