package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelPreference;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelSortMode;
import cafe.woden.ircclient.config.api.ServerTreeChannelStateConfigPort.ServerTreeChannelState;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeChannelStateStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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

  public static Context context(
      Function<String, String> normalizeServerId,
      Function<String, DefaultMutableTreeNode> channelListNode,
      Supplier<Set<TreePath>> snapshotExpandedTreePaths,
      Consumer<Set<TreePath>> restoreExpandedTreePaths,
      Consumer<String> emitManagedChannelsChanged) {
    Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    Objects.requireNonNull(channelListNode, "channelListNode");
    Objects.requireNonNull(snapshotExpandedTreePaths, "snapshotExpandedTreePaths");
    Objects.requireNonNull(restoreExpandedTreePaths, "restoreExpandedTreePaths");
    Objects.requireNonNull(emitManagedChannelsChanged, "emitManagedChannelsChanged");
    return new Context() {
      @Override
      public String normalizeServerId(String serverId) {
        return normalizeServerId.apply(serverId);
      }

      @Override
      public DefaultMutableTreeNode channelListNode(String serverId) {
        return channelListNode.apply(serverId);
      }

      @Override
      public Set<TreePath> snapshotExpandedTreePaths() {
        return snapshotExpandedTreePaths.get();
      }

      @Override
      public void restoreExpandedTreePaths(Set<TreePath> expanded) {
        restoreExpandedTreePaths.accept(expanded);
      }

      @Override
      public void emitManagedChannelsChanged(String serverId) {
        emitManagedChannelsChanged.accept(serverId);
      }
    };
  }

  private final ServerTreeChannelStateConfigPort runtimeConfig;
  private final Map<String, ServerTreeDockable.ChannelSortMode> channelSortModeByServer;
  private final Map<String, ArrayList<String>> channelCustomOrderByServer;
  private final Map<String, Map<String, Boolean>> channelAutoReattachByServer;
  private final Map<String, Map<String, Long>> channelActivityRankByServer;
  private final Map<String, Map<String, Boolean>> channelPinnedByServer;
  private final Map<String, Map<String, Boolean>> channelMutedByServer;
  private final DefaultTreeModel model;
  private final Context context;
  private long channelActivityRankCounter = 0L;

  public ServerTreeChannelStateCoordinator(
      ServerTreeChannelStateConfigPort runtimeConfig,
      ServerTreeChannelStateStore channelStateStore,
      DefaultTreeModel model,
      Context context) {
    ServerTreeChannelStateStore stateStore =
        Objects.requireNonNull(channelStateStore, "channelStateStore");
    this.runtimeConfig = runtimeConfig;
    this.channelSortModeByServer = stateStore.channelSortModeByServer();
    this.channelCustomOrderByServer = stateStore.channelCustomOrderByServer();
    this.channelAutoReattachByServer = stateStore.channelAutoReattachByServer();
    this.channelActivityRankByServer = stateStore.channelActivityRankByServer();
    this.channelPinnedByServer = stateStore.channelPinnedByServer();
    this.channelMutedByServer = stateStore.channelMutedByServer();
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
    sortChannelsUnderChannelList(sid);
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

  public boolean isChannelPinned(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return false;
    String sid = context.normalizeServerId(ref.serverId());
    String key = foldChannelKey(ref.target());
    if (sid.isEmpty() || key.isEmpty()) return false;
    return channelPinnedByServer.getOrDefault(sid, Map.of()).getOrDefault(key, Boolean.FALSE);
  }

  public void setChannelPinned(TargetRef ref, boolean pinned) {
    if (ref == null || !ref.isChannel()) return;
    String sid = context.normalizeServerId(ref.serverId());
    String channel = Objects.toString(ref.target(), "").trim();
    String key = foldChannelKey(channel);
    if (sid.isEmpty() || key.isEmpty()) return;

    ensureChannelKnownInConfig(ref);
    persistCustomOrderFromTree(sid);

    Map<String, Boolean> pinnedByChannel =
        channelPinnedByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    if (pinned) {
      pinnedByChannel.put(key, true);
    } else {
      pinnedByChannel.remove(key);
    }

    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeChannelPinned(sid, channel, pinned);
    }

    sortChannelsUnderChannelList(sid);
    context.emitManagedChannelsChanged(sid);
  }

  public boolean isChannelMuted(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return false;
    String sid = context.normalizeServerId(ref.serverId());
    String key = foldChannelKey(ref.target());
    if (sid.isEmpty() || key.isEmpty()) return false;
    return channelMutedByServer.getOrDefault(sid, Map.of()).getOrDefault(key, Boolean.FALSE);
  }

  public void setChannelMuted(TargetRef ref, boolean muted) {
    if (ref == null || !ref.isChannel()) return;
    String sid = context.normalizeServerId(ref.serverId());
    String channel = Objects.toString(ref.target(), "").trim();
    String key = foldChannelKey(channel);
    if (sid.isEmpty() || key.isEmpty()) return;

    ensureChannelKnownInConfig(ref);

    Map<String, Boolean> mutedByChannel =
        channelMutedByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    if (muted) {
      mutedByChannel.put(key, true);
    } else {
      mutedByChannel.remove(key);
    }

    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeChannelMuted(sid, channel, muted);
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

    Map<String, Boolean> pinnedByChannel =
        channelPinnedByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    if (!pinnedByChannel.containsKey(key)) {
      boolean pinned =
          runtimeConfig != null && runtimeConfig.readServerTreeChannelPinned(sid, channel, false);
      if (pinned) {
        pinnedByChannel.put(key, true);
      }
    }

    Map<String, Boolean> mutedByChannel =
        channelMutedByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    if (!mutedByChannel.containsKey(key)) {
      boolean muted =
          runtimeConfig != null && runtimeConfig.readServerTreeChannelMuted(sid, channel, false);
      if (muted) {
        mutedByChannel.put(key, true);
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
    List<DefaultMutableTreeNode> channelListNodes = channelListNodesForServer(sid);
    if (channelListNodes.isEmpty()) return List.of();

    Map<String, Boolean> autoByChannel = channelAutoReattachByServer.getOrDefault(sid, Map.of());
    ArrayList<ServerTreeDockable.ManagedChannelEntry> out = new ArrayList<>();

    for (DefaultMutableTreeNode channelListNode : channelListNodes) {
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
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  public void sortChannelsUnderChannelList(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    List<DefaultMutableTreeNode> channelListNodes = channelListNodesForServer(sid);
    if (channelListNodes.isEmpty()) return;

    for (DefaultMutableTreeNode channelListNode : channelListNodes) {
      sortChannelsUnderChannelListNode(sid, channelListNode);
    }
  }

  private void sortChannelsUnderChannelListNode(
      String sid, DefaultMutableTreeNode channelListNode) {
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
      return;
    }

    ServerTreeDockable.ChannelSortMode sortMode =
        channelSortModeByServer.getOrDefault(sid, ServerTreeDockable.ChannelSortMode.CUSTOM);

    ArrayList<String> customOrder = channelCustomOrderByServer.getOrDefault(sid, new ArrayList<>());
    Map<String, Integer> customIndexByKey = new HashMap<>();
    for (int i = 0; i < customOrder.size(); i++) {
      String channel = Objects.toString(customOrder.get(i), "").trim();
      if (channel.isEmpty()) continue;
      customIndexByKey.putIfAbsent(foldChannelKey(channel), i);
    }

    ArrayList<DefaultMutableTreeNode> pinned = new ArrayList<>();
    ArrayList<DefaultMutableTreeNode> unpinned = new ArrayList<>();
    for (DefaultMutableTreeNode node : channelNodes) {
      String label = channelLabelForNode(node);
      String key = foldChannelKey(label);
      if (channelPinnedByServer.getOrDefault(sid, Map.of()).getOrDefault(key, Boolean.FALSE)) {
        pinned.add(node);
      } else {
        unpinned.add(node);
      }
    }

    java.util.Comparator<DefaultMutableTreeNode> customComparator =
        (a, b) -> {
          String ac = channelLabelForNode(a);
          String bc = channelLabelForNode(b);
          int ai = customIndexByKey.getOrDefault(foldChannelKey(ac), Integer.MAX_VALUE);
          int bi = customIndexByKey.getOrDefault(foldChannelKey(bc), Integer.MAX_VALUE);
          if (ai != bi) return Integer.compare(ai, bi);
          return compareChannelLabels(ac, bc);
        };

    pinned.sort(customComparator);

    if (sortMode == ServerTreeDockable.ChannelSortMode.ALPHABETICAL) {
      unpinned.sort((a, b) -> compareChannelLabels(channelLabelForNode(a), channelLabelForNode(b)));
    } else if (sortMode == ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY) {
      Map<String, Long> byKey = channelActivityRankByServer.getOrDefault(sid, Map.of());
      unpinned.sort(
          (a, b) -> {
            String ac = channelLabelForNode(a);
            String bc = channelLabelForNode(b);
            long ai = byKey.getOrDefault(foldChannelKey(ac), 0L);
            long bi = byKey.getOrDefault(foldChannelKey(bc), 0L);
            if (ai != bi) return Long.compare(bi, ai);
            return compareChannelLabels(ac, bc);
          });
    } else if (sortMode == ServerTreeDockable.ChannelSortMode.MOST_UNREAD_MESSAGES) {
      unpinned.sort(
          (a, b) -> {
            int au = unreadCountForNode(a);
            int bu = unreadCountForNode(b);
            if (au != bu) return Integer.compare(bu, au);
            return compareChannelLabels(channelLabelForNode(a), channelLabelForNode(b));
          });
    } else if (sortMode == ServerTreeDockable.ChannelSortMode.MOST_UNREAD_NOTIFICATIONS) {
      unpinned.sort(
          (a, b) -> {
            int ah = highlightCountForNode(a);
            int bh = highlightCountForNode(b);
            if (ah != bh) return Integer.compare(bh, ah);
            return compareChannelLabels(channelLabelForNode(a), channelLabelForNode(b));
          });
    } else {
      unpinned.sort(customComparator);
    }

    ArrayList<DefaultMutableTreeNode> sorted = new ArrayList<>(channelNodes.size());
    sorted.addAll(pinned);
    sorted.addAll(unpinned);

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
    List<DefaultMutableTreeNode> channelListNodes = channelListNodesForServer(sid);
    if (channelListNodes.isEmpty()) return;

    ArrayList<String> customOrder = new ArrayList<>();
    for (DefaultMutableTreeNode channelListNode : channelListNodes) {
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
    }
    channelCustomOrderByServer.put(sid, customOrder);
    if (runtimeConfig != null) {
      runtimeConfig.rememberServerTreeChannelCustomOrder(sid, customOrder);
    }
  }

  private List<DefaultMutableTreeNode> channelListNodesForServer(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return List.of();
    DefaultMutableTreeNode primary = context.channelListNode(sid);
    if (primary == null) return List.of();

    DefaultMutableTreeNode serverNode = resolveOwningServerNode(primary, sid);
    if (serverNode == null) return List.of(primary);

    ArrayList<DefaultMutableTreeNode> out = new ArrayList<>();
    collectChannelListNodes(serverNode, out);
    if (out.isEmpty()) out.add(primary);
    return List.copyOf(out);
  }

  private static DefaultMutableTreeNode resolveOwningServerNode(
      DefaultMutableTreeNode node, String serverId) {
    DefaultMutableTreeNode current = node;
    String sid = Objects.toString(serverId, "").trim();
    while (current != null) {
      Object userObject = current.getUserObject();
      if (userObject instanceof String s && s.trim().equalsIgnoreCase(sid)) {
        return current;
      }
      current =
          current.getParent() instanceof DefaultMutableTreeNode parentNode ? parentNode : null;
    }
    return null;
  }

  private static void collectChannelListNodes(
      DefaultMutableTreeNode parent, List<DefaultMutableTreeNode> out) {
    if (parent == null || out == null) return;
    Object userObject = parent.getUserObject();
    if (userObject instanceof ServerTreeNodeData nodeData
        && nodeData.ref != null
        && nodeData.ref.isChannelList()) {
      out.add(parent);
    }
    for (int i = 0; i < parent.getChildCount(); i++) {
      collectChannelListNodes((DefaultMutableTreeNode) parent.getChildAt(i), out);
    }
  }

  public void persistOrderAndResortAfterManualMove(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    persistCustomOrderFromTree(sid);
    sortChannelsUnderChannelList(sid);
    context.emitManagedChannelsChanged(sid);
  }

  public void loadChannelStateForServer(String serverId) {
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    ServerTreeDockable.ChannelSortMode sortMode = ServerTreeDockable.ChannelSortMode.CUSTOM;
    ArrayList<String> customOrder = new ArrayList<>();
    Map<String, Boolean> autoByChannel = new HashMap<>();
    Map<String, Boolean> pinnedByChannel = new HashMap<>();
    Map<String, Boolean> mutedByChannel = new HashMap<>();

    if (runtimeConfig != null) {
      ServerTreeChannelState state = runtimeConfig.readServerTreeChannelState(sid);
      if (state != null && state.sortMode() != null) {
        sortMode = uiChannelSortMode(state.sortMode());
      }
      if (state != null && state.customOrder() != null) {
        customOrder.addAll(normalizeCustomOrderList(state.customOrder()));
      }
      if (state != null && state.channels() != null) {
        for (ServerTreeChannelPreference pref : state.channels()) {
          if (pref == null) continue;
          String channel = Objects.toString(pref.channel(), "").trim();
          if (channel.isEmpty()) continue;
          String key = foldChannelKey(channel);
          autoByChannel.put(key, pref.autoReattach());
          if (pref.pinned()) {
            pinnedByChannel.put(key, true);
          }
          if (pref.muted()) {
            mutedByChannel.put(key, true);
          }
        }
      }
    }

    channelSortModeByServer.put(sid, sortMode);
    channelCustomOrderByServer.put(sid, customOrder);
    channelAutoReattachByServer.put(sid, autoByChannel);
    channelPinnedByServer.put(sid, pinnedByChannel);
    channelMutedByServer.put(sid, mutedByChannel);
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

  public void onChannelUnreadCountsChanged(TargetRef ref) {
    if (ref == null || !ref.isChannel()) return;
    String sid = context.normalizeServerId(ref.serverId());
    if (sid.isEmpty()) return;
    ServerTreeDockable.ChannelSortMode mode =
        channelSortModeByServer.getOrDefault(sid, ServerTreeDockable.ChannelSortMode.CUSTOM);
    if (mode == ServerTreeDockable.ChannelSortMode.MOST_UNREAD_MESSAGES
        || mode == ServerTreeDockable.ChannelSortMode.MOST_UNREAD_NOTIFICATIONS) {
      sortChannelsUnderChannelList(sid);
    }
  }

  private static ServerTreeChannelSortMode runtimeChannelSortMode(
      ServerTreeDockable.ChannelSortMode mode) {
    return switch (mode) {
      case ALPHABETICAL -> ServerTreeChannelSortMode.ALPHABETICAL;
      case MOST_RECENT_ACTIVITY -> ServerTreeChannelSortMode.MOST_RECENT_ACTIVITY;
      case MOST_UNREAD_MESSAGES -> ServerTreeChannelSortMode.MOST_UNREAD_MESSAGES;
      case MOST_UNREAD_NOTIFICATIONS -> ServerTreeChannelSortMode.MOST_UNREAD_NOTIFICATIONS;
      case CUSTOM -> ServerTreeChannelSortMode.CUSTOM;
    };
  }

  private static ServerTreeDockable.ChannelSortMode uiChannelSortMode(
      ServerTreeChannelSortMode mode) {
    if (mode == null) return ServerTreeDockable.ChannelSortMode.CUSTOM;
    return switch (mode) {
      case ALPHABETICAL -> ServerTreeDockable.ChannelSortMode.ALPHABETICAL;
      case MOST_RECENT_ACTIVITY -> ServerTreeDockable.ChannelSortMode.MOST_RECENT_ACTIVITY;
      case MOST_UNREAD_MESSAGES -> ServerTreeDockable.ChannelSortMode.MOST_UNREAD_MESSAGES;
      case MOST_UNREAD_NOTIFICATIONS ->
          ServerTreeDockable.ChannelSortMode.MOST_UNREAD_NOTIFICATIONS;
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

  private static int unreadCountForNode(DefaultMutableTreeNode node) {
    if (node == null) return 0;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return 0;
    return Math.max(0, nodeData.unread);
  }

  private static int highlightCountForNode(DefaultMutableTreeNode node) {
    if (node == null) return 0;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return 0;
    return Math.max(0, nodeData.highlightUnread);
  }

  private static int compareChannelLabels(String left, String right) {
    int cmp = left.compareToIgnoreCase(right);
    if (cmp != 0) return cmp;
    return left.compareTo(right);
  }

  private static String foldChannelKey(String channel) {
    return ServerTreeConventions.foldChannelKey(channel);
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
