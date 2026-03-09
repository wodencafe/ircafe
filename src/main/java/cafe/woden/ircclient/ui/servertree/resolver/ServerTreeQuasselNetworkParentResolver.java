package cafe.woden.ircclient.ui.servertree.resolver;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Resolves per-network parent containers for Quassel-qualified targets in the server tree. */
public final class ServerTreeQuasselNetworkParentResolver {

  private static final String EMPTY_STATE_LABEL = "No Quassel networks configured";

  /** Snapshot of one Quassel network used to sync network nodes in the tree. */
  public record NetworkPresentation(
      String token, String label, Boolean connected, Boolean enabled) {
    public NetworkPresentation {
      token = normalizeToken(token);
      label = Objects.toString(label, "").trim();
    }
  }

  private record NetworkNodes(
      DefaultMutableTreeNode networkNode,
      DefaultMutableTreeNode channelListNode,
      DefaultMutableTreeNode privateMessagesNode,
      DefaultMutableTreeNode otherNode,
      DefaultMutableTreeNode monitorNode,
      DefaultMutableTreeNode interceptorsNode,
      DefaultMutableTreeNode ignoresNode) {}

  private final Map<String, Map<String, NetworkNodes>> networkNodesByServer = new HashMap<>();
  private final Map<String, DefaultMutableTreeNode> emptyStateNodesByServer = new HashMap<>();
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final DefaultTreeModel model;
  private final Predicate<String> isQuasselServer;
  private final String channelListLabel;
  private final String privateMessagesLabel;
  private final String otherGroupLabel;
  private final String monitorGroupLabel;
  private final String interceptorsGroupLabel;
  private final String ignoresLabel;

  public ServerTreeQuasselNetworkParentResolver(
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      DefaultTreeModel model,
      Predicate<String> isQuasselServer,
      String channelListLabel,
      String privateMessagesLabel,
      String otherGroupLabel,
      String monitorGroupLabel,
      String interceptorsGroupLabel,
      String ignoresLabel) {
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.model = Objects.requireNonNull(model, "model");
    this.isQuasselServer = Objects.requireNonNull(isQuasselServer, "isQuasselServer");
    this.channelListLabel = Objects.toString(channelListLabel, "Channel List");
    this.privateMessagesLabel = Objects.toString(privateMessagesLabel, "Private Messages");
    this.otherGroupLabel = Objects.toString(otherGroupLabel, "Other");
    this.monitorGroupLabel = Objects.toString(monitorGroupLabel, "Monitor");
    this.interceptorsGroupLabel = Objects.toString(interceptorsGroupLabel, "Interceptors");
    this.ignoresLabel = Objects.toString(ignoresLabel, "Ignores");
  }

  public DefaultMutableTreeNode resolveParent(TargetRef ref, ServerNodes serverNodes) {
    if (ref == null || serverNodes == null) return null;
    String serverId = normalizeServerId(ref.serverId());
    if (serverId.isEmpty() || !isQuasselServer.test(serverId)) return null;

    String token = normalizeToken(ref.networkQualifierToken());
    if (ref.isChannelList() && token.isEmpty()) {
      DefaultMutableTreeNode aliased = aliasServerChannelListToKnownNetwork(serverId);
      if (aliased == null) return null;
      return (DefaultMutableTreeNode) aliased.getParent();
    }
    if (token.isEmpty()) {
      NetworkNodes first = firstNetworkNodes(serverId);
      if (first == null) return null;
      if (ref.isIgnores()) {
        DefaultMutableTreeNode aliased = aliasServerIgnoresToKnownNetwork(serverId);
        if (aliased != null) {
          return (DefaultMutableTreeNode) aliased.getParent();
        }
        return first.otherNode();
      }
      if (ref.isMonitorGroup()) {
        DefaultMutableTreeNode aliased = aliasServerMonitorGroupToKnownNetwork(serverId);
        return aliased != null ? aliased : first.monitorNode();
      }
      if (ref.isInterceptorsGroup()) {
        DefaultMutableTreeNode aliased = aliasServerInterceptorsGroupToKnownNetwork(serverId);
        return aliased != null ? aliased : first.interceptorsNode();
      }
      if (isPrivateMessageTarget(ref)) {
        return first.privateMessagesNode();
      }
      if (ref.isInterceptor()) return first.interceptorsNode();
      return null;
    }

    NetworkNodes networkNodes =
        ensureNetworkNodes(serverId, token, serverNodes, friendlyNetworkLabel(token), null, null);
    aliasServerChannelListToKnownNetwork(serverId);
    aliasServerIgnoresToKnownNetwork(serverId);
    aliasServerMonitorGroupToKnownNetwork(serverId);
    aliasServerInterceptorsGroupToKnownNetwork(serverId);
    syncRootOtherNodeVisibility(serverNodes);
    if (ref.isChannel()) return networkNodes.channelListNode();
    if (isPrivateMessageTarget(ref)) return networkNodes.privateMessagesNode();
    if (ref.isChannelList()) {
      leaves.put(ref, networkNodes.channelListNode());
      return networkNodes.networkNode();
    }
    if (ref.isIgnores()) {
      leaves.put(ref, networkNodes.ignoresNode());
      return networkNodes.otherNode();
    }
    if (ref.isMonitorGroup()) return networkNodes.monitorNode();
    if (ref.isInterceptorsGroup() || ref.isInterceptor()) return networkNodes.interceptorsNode();
    return null;
  }

  public void syncServerNetworks(
      String serverId, ServerNodes serverNodes, List<NetworkPresentation> networks) {
    syncServerNetworks(serverId, serverNodes, networks, true);
  }

  public void syncServerNetworks(
      String serverId,
      ServerNodes serverNodes,
      List<NetworkPresentation> networks,
      boolean connected) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || serverNodes == null || serverNodes.serverNode == null) return;
    if (!isQuasselServer.test(sid)) return;

    LinkedHashMap<String, NetworkPresentation> desiredByToken = new LinkedHashMap<>();
    if (networks != null) {
      for (NetworkPresentation raw : networks) {
        if (raw == null) continue;
        String token = normalizeToken(raw.token());
        if (token.isEmpty()) continue;
        desiredByToken.putIfAbsent(
            token, new NetworkPresentation(token, raw.label(), raw.connected(), raw.enabled()));
      }
    }

    if (desiredByToken.isEmpty()) {
      removeStaleNetworkNodes(sid, Set.of());
      if (connected) {
        maybeEnsureEmptyStateNode(sid, serverNodes.serverNode);
      } else {
        removeEmptyStateNodeIfPresent(sid, serverNodes.serverNode);
      }
      clearLegacyChannelListAlias(sid);
      clearLegacyIgnoresAlias(sid);
      clearLegacyMonitorAlias(sid);
      clearLegacyInterceptorsAlias(sid);
      syncRootOtherNodeVisibility(serverNodes);
      return;
    }

    removeEmptyStateNodeIfPresent(sid, serverNodes.serverNode);
    for (Map.Entry<String, NetworkPresentation> entry : desiredByToken.entrySet()) {
      String token = entry.getKey();
      NetworkPresentation network = entry.getValue();
      ensureNetworkNodes(
          sid, token, serverNodes, network.label(), network.connected(), network.enabled());
    }

    removeStaleNetworkNodes(sid, desiredByToken.keySet());
    Map<String, NetworkNodes> remaining = networkNodesByServer.get(sid);
    if (remaining == null || remaining.isEmpty()) {
      maybeEnsureEmptyStateNode(sid, serverNodes.serverNode);
      clearLegacyChannelListAlias(sid);
      clearLegacyIgnoresAlias(sid);
      clearLegacyMonitorAlias(sid);
      clearLegacyInterceptorsAlias(sid);
      syncRootOtherNodeVisibility(serverNodes);
      return;
    }
    aliasServerChannelListToKnownNetwork(sid);
    aliasServerIgnoresToKnownNetwork(sid);
    aliasServerMonitorGroupToKnownNetwork(sid);
    aliasServerInterceptorsGroupToKnownNetwork(sid);
    syncRootOtherNodeVisibility(serverNodes);
  }

  public void initializeServer(String serverId, ServerNodes serverNodes) {
    initializeServer(serverId, serverNodes, true);
  }

  public void initializeServer(String serverId, ServerNodes serverNodes, boolean connected) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || serverNodes == null || serverNodes.serverNode == null) return;
    if (!isQuasselServer.test(sid)) return;
    if (connected) {
      maybeEnsureEmptyStateNode(sid, serverNodes.serverNode);
    } else {
      removeEmptyStateNodeIfPresent(sid, serverNodes.serverNode);
    }
    syncRootOtherNodeVisibility(serverNodes);
  }

  public boolean isQuasselNetworkNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    if (!(node.getUserObject() instanceof ServerTreeQuasselNetworkNodeData data)) return false;
    if (data.emptyState()) return false;
    return !data.serverId().isBlank() && !data.networkToken().isBlank();
  }

  public boolean isQuasselEmptyStateNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    if (!(node.getUserObject() instanceof ServerTreeQuasselNetworkNodeData data)) return false;
    return data.emptyState();
  }

  public TargetRef channelListRefForNetworkNode(DefaultMutableTreeNode node) {
    if (!isQuasselNetworkNode(node)) return null;
    ServerTreeQuasselNetworkNodeData data = (ServerTreeQuasselNetworkNodeData) node.getUserObject();
    return TargetRef.channelList(data.serverId(), data.networkToken());
  }

  public void forgetServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    networkNodesByServer.remove(sid);
    emptyStateNodesByServer.remove(sid);
    clearLegacyChannelListAlias(sid);
    clearLegacyIgnoresAlias(sid);
    clearLegacyMonitorAlias(sid);
    clearLegacyInterceptorsAlias(sid);
  }

  private NetworkNodes ensureNetworkNodes(
      String serverId,
      String token,
      ServerNodes serverNodes,
      String label,
      Boolean connected,
      Boolean enabled) {
    removeEmptyStateNodeIfPresent(serverId, serverNodes.serverNode);
    Map<String, NetworkNodes> byToken =
        networkNodesByServer.computeIfAbsent(serverId, ignored -> new LinkedHashMap<>());
    NetworkNodes existing = byToken.get(token);
    if (existing != null
        && existing.networkNode().getParent() == serverNodes.serverNode
        && existing.channelListNode().getParent() == existing.networkNode()
        && existing.privateMessagesNode().getParent() == existing.networkNode()
        && existing.otherNode() != null
        && existing.otherNode().getParent() == existing.networkNode()
        && existing.monitorNode() != null
        && existing.monitorNode().getParent() == existing.otherNode()
        && existing.interceptorsNode() != null
        && existing.interceptorsNode().getParent() == existing.otherNode()
        && existing.ignoresNode() != null
        && existing.ignoresNode().getParent() == existing.otherNode()) {
      updateNetworkNodeDataIfNeeded(
          existing.networkNode(), serverId, token, label, connected, enabled);
      leaves.put(TargetRef.channelList(serverId, token), existing.channelListNode());
      leaves.put(TargetRef.ignores(serverId, token), existing.ignoresNode());
      leaves.put(TargetRef.monitorGroup(serverId, token), existing.monitorNode());
      leaves.put(TargetRef.interceptorsGroup(serverId, token), existing.interceptorsNode());
      return existing;
    }

    DefaultMutableTreeNode networkNode =
        new DefaultMutableTreeNode(
            ServerTreeQuasselNetworkNodeData.network(
                serverId, token, resolveNetworkLabel(token, label), connected, enabled));
    int networkInsertIdx = networkInsertIndex(serverNodes);
    serverNodes.serverNode.insert(networkNode, networkInsertIdx);
    model.nodesWereInserted(serverNodes.serverNode, new int[] {networkInsertIdx});

    TargetRef channelListRef = TargetRef.channelList(serverId, token);
    DefaultMutableTreeNode channelListNode = leaves.get(channelListRef);
    if (channelListNode == null) {
      channelListNode =
          new DefaultMutableTreeNode(new ServerTreeNodeData(channelListRef, channelListLabel));
    } else {
      detachNodeIfNeeded(channelListNode);
    }
    int channelListIdx = networkNode.getChildCount();
    networkNode.insert(channelListNode, channelListIdx);
    model.nodesWereInserted(networkNode, new int[] {channelListIdx});
    leaves.put(channelListRef, channelListNode);

    DefaultMutableTreeNode privateMessagesNode = new DefaultMutableTreeNode(privateMessagesLabel);
    int privateMessagesIdx = networkNode.getChildCount();
    networkNode.insert(privateMessagesNode, privateMessagesIdx);
    model.nodesWereInserted(networkNode, new int[] {privateMessagesIdx});

    DefaultMutableTreeNode otherNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(null, otherGroupLabel));
    int otherIdx = networkNode.getChildCount();
    networkNode.insert(otherNode, otherIdx);
    model.nodesWereInserted(networkNode, new int[] {otherIdx});

    TargetRef ignoresRef = TargetRef.ignores(serverId, token);
    DefaultMutableTreeNode ignoresNode = leaves.get(ignoresRef);
    if (ignoresNode == null) {
      ignoresNode = new DefaultMutableTreeNode(new ServerTreeNodeData(ignoresRef, ignoresLabel));
    } else {
      detachNodeIfNeeded(ignoresNode);
    }
    int ignoresIdx = otherNode.getChildCount();
    otherNode.insert(ignoresNode, ignoresIdx);
    model.nodesWereInserted(otherNode, new int[] {ignoresIdx});
    leaves.put(ignoresRef, ignoresNode);

    TargetRef monitorRef = TargetRef.monitorGroup(serverId, token);
    DefaultMutableTreeNode monitorNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(monitorRef, monitorGroupLabel));
    int monitorIdx = otherNode.getChildCount();
    otherNode.insert(monitorNode, monitorIdx);
    model.nodesWereInserted(otherNode, new int[] {monitorIdx});
    leaves.put(monitorRef, monitorNode);

    TargetRef interceptorsRef = TargetRef.interceptorsGroup(serverId, token);
    DefaultMutableTreeNode interceptorsNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(interceptorsRef, interceptorsGroupLabel));
    int interceptorsIdx = otherNode.getChildCount();
    otherNode.insert(interceptorsNode, interceptorsIdx);
    model.nodesWereInserted(otherNode, new int[] {interceptorsIdx});
    leaves.put(interceptorsRef, interceptorsNode);

    NetworkNodes created =
        new NetworkNodes(
            networkNode,
            channelListNode,
            privateMessagesNode,
            otherNode,
            monitorNode,
            interceptorsNode,
            ignoresNode);
    byToken.put(token, created);
    return created;
  }

  private void updateNetworkNodeDataIfNeeded(
      DefaultMutableTreeNode networkNode,
      String serverId,
      String token,
      String label,
      Boolean connected,
      Boolean enabled) {
    if (networkNode == null) return;
    Object userObject = networkNode.getUserObject();
    if (!(userObject instanceof ServerTreeQuasselNetworkNodeData existing)) return;

    String nextLabel = resolveNetworkLabel(token, label);
    boolean unchanged =
        Objects.equals(existing.serverId(), serverId)
            && Objects.equals(existing.networkToken(), token)
            && Objects.equals(existing.label(), nextLabel)
            && Objects.equals(existing.connected(), connected)
            && Objects.equals(existing.enabled(), enabled)
            && !existing.emptyState();
    if (unchanged) return;

    networkNode.setUserObject(
        ServerTreeQuasselNetworkNodeData.network(serverId, token, nextLabel, connected, enabled));
    model.nodeChanged(networkNode);
  }

  private void removeStaleNetworkNodes(String serverId, Set<String> desiredTokens) {
    Map<String, NetworkNodes> byToken = networkNodesByServer.get(serverId);
    if (byToken == null || byToken.isEmpty()) return;

    Set<String> wanted = desiredTokens == null ? Set.of() : new LinkedHashSet<>(desiredTokens);
    LinkedHashMap<String, NetworkNodes> retained = new LinkedHashMap<>();
    for (Map.Entry<String, NetworkNodes> entry : byToken.entrySet()) {
      String token = entry.getKey();
      NetworkNodes nodes = entry.getValue();
      if (wanted.contains(token)) {
        retained.put(token, nodes);
        continue;
      }
      if (nodes != null && nodes.networkNode() != null) {
        pruneLeafMappings(nodes.networkNode());
        detachNodeIfNeeded(nodes.networkNode());
      }
    }

    if (retained.isEmpty()) {
      networkNodesByServer.remove(serverId);
    } else {
      networkNodesByServer.put(serverId, retained);
    }
  }

  private void pruneLeafMappings(DefaultMutableTreeNode node) {
    if (node == null) return;
    Object userObject = node.getUserObject();
    if (userObject instanceof ServerTreeNodeData nodeData && nodeData.ref != null) {
      leaves.remove(nodeData.ref);
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      Object child = node.getChildAt(i);
      if (child instanceof DefaultMutableTreeNode childNode) {
        pruneLeafMappings(childNode);
      }
    }
  }

  private void maybeEnsureEmptyStateNode(String serverId, DefaultMutableTreeNode serverNode) {
    if (serverNode == null) return;
    Map<String, NetworkNodes> byToken = networkNodesByServer.get(serverId);
    if (byToken != null && !byToken.isEmpty()) return;
    DefaultMutableTreeNode existing = emptyStateNodesByServer.get(serverId);
    if (existing != null && existing.getParent() == serverNode) return;
    if (existing != null) {
      detachNodeIfNeeded(existing);
    }

    DefaultMutableTreeNode emptyNode =
        new DefaultMutableTreeNode(
            ServerTreeQuasselNetworkNodeData.emptyState(serverId, EMPTY_STATE_LABEL));
    int idx = networkInsertIndex(serverNode);
    serverNode.insert(emptyNode, idx);
    notifyNodeInserted(serverNode, idx);
    emptyStateNodesByServer.put(serverId, emptyNode);
  }

  private void removeEmptyStateNodeIfPresent(String serverId, DefaultMutableTreeNode serverNode) {
    DefaultMutableTreeNode emptyNode = emptyStateNodesByServer.get(serverId);
    if (emptyNode == null) return;
    if (emptyNode.getParent() != serverNode) {
      emptyStateNodesByServer.remove(serverId);
      return;
    }
    detachNodeIfNeeded(emptyNode);
    emptyStateNodesByServer.remove(serverId);
  }

  private DefaultMutableTreeNode aliasServerChannelListToKnownNetwork(String serverId) {
    Map<String, NetworkNodes> byToken = networkNodesByServer.get(normalizeServerId(serverId));
    TargetRef serverChannelListRef = TargetRef.channelList(serverId);
    if (byToken == null || byToken.isEmpty()) {
      leaves.remove(serverChannelListRef);
      return null;
    }
    NetworkNodes first = byToken.values().iterator().next();
    if (first == null || first.channelListNode() == null) return null;

    DefaultMutableTreeNode existingServerChannelList = leaves.get(serverChannelListRef);
    if (existingServerChannelList != null && existingServerChannelList != first.channelListNode()) {
      detachNodeIfNeeded(existingServerChannelList);
    }
    leaves.put(serverChannelListRef, first.channelListNode());
    return first.channelListNode();
  }

  private void clearLegacyChannelListAlias(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    leaves.remove(TargetRef.channelList(sid));
  }

  private DefaultMutableTreeNode aliasServerIgnoresToKnownNetwork(String serverId) {
    Map<String, NetworkNodes> byToken = networkNodesByServer.get(normalizeServerId(serverId));
    TargetRef serverIgnoresRef = TargetRef.ignores(serverId);
    if (byToken == null || byToken.isEmpty()) {
      leaves.remove(serverIgnoresRef);
      return null;
    }
    NetworkNodes first = firstNetworkNodes(serverId);
    if (first == null || first.ignoresNode() == null) return null;

    DefaultMutableTreeNode existingServerIgnores = leaves.get(serverIgnoresRef);
    if (existingServerIgnores != null && existingServerIgnores != first.ignoresNode()) {
      detachNodeIfNeeded(existingServerIgnores);
    }
    leaves.put(serverIgnoresRef, first.ignoresNode());
    return first.ignoresNode();
  }

  private void clearLegacyIgnoresAlias(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    leaves.remove(TargetRef.ignores(sid));
  }

  private DefaultMutableTreeNode aliasServerMonitorGroupToKnownNetwork(String serverId) {
    Map<String, NetworkNodes> byToken = networkNodesByServer.get(normalizeServerId(serverId));
    TargetRef serverMonitorRef = TargetRef.monitorGroup(serverId);
    if (byToken == null || byToken.isEmpty()) {
      leaves.remove(serverMonitorRef);
      return null;
    }
    NetworkNodes first = firstNetworkNodes(serverId);
    if (first == null || first.monitorNode() == null) return null;

    leaves.put(serverMonitorRef, first.monitorNode());
    return first.monitorNode();
  }

  private void clearLegacyMonitorAlias(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    leaves.remove(TargetRef.monitorGroup(sid));
  }

  private DefaultMutableTreeNode aliasServerInterceptorsGroupToKnownNetwork(String serverId) {
    Map<String, NetworkNodes> byToken = networkNodesByServer.get(normalizeServerId(serverId));
    TargetRef serverInterceptorsRef = TargetRef.interceptorsGroup(serverId);
    if (byToken == null || byToken.isEmpty()) {
      leaves.remove(serverInterceptorsRef);
      return null;
    }
    NetworkNodes first = firstNetworkNodes(serverId);
    if (first == null || first.interceptorsNode() == null) return null;

    leaves.put(serverInterceptorsRef, first.interceptorsNode());
    return first.interceptorsNode();
  }

  private void clearLegacyInterceptorsAlias(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    leaves.remove(TargetRef.interceptorsGroup(sid));
  }

  private NetworkNodes firstNetworkNodes(String serverId) {
    Map<String, NetworkNodes> byToken = networkNodesByServer.get(normalizeServerId(serverId));
    if (byToken == null || byToken.isEmpty()) return null;
    return byToken.values().iterator().next();
  }

  private void syncRootOtherNodeVisibility(ServerNodes serverNodes) {
    if (serverNodes == null || serverNodes.serverNode == null || serverNodes.otherNode == null) {
      return;
    }
    DefaultMutableTreeNode otherNode = serverNodes.otherNode;
    DefaultMutableTreeNode serverNode = serverNodes.serverNode;
    if (otherNode.getParent() == serverNode) return;
    if (otherNode.getParent() != null) {
      detachNodeIfNeeded(otherNode);
    }
    int insertIdx = networkInsertIndex(serverNodes);
    serverNode.insert(otherNode, insertIdx);
    notifyNodeInserted(serverNode, insertIdx);
  }

  private void detachNodeIfNeeded(DefaultMutableTreeNode node) {
    if (!(node.getParent() instanceof DefaultMutableTreeNode oldParent)) return;
    int oldIndex = oldParent.getIndex(node);
    if (oldIndex < 0) return;
    oldParent.remove(node);
    if (isAttachedToTree(oldParent)) {
      model.nodesWereRemoved(oldParent, new int[] {oldIndex}, new Object[] {node});
    }
  }

  private void notifyNodeInserted(DefaultMutableTreeNode parent, int index) {
    if (parent == null || index < 0) return;
    if (isAttachedToTree(parent)) {
      model.nodesWereInserted(parent, new int[] {index});
    }
  }

  private boolean isAttachedToTree(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object root = model.getRoot();
    DefaultMutableTreeNode current = node;
    while (current != null) {
      if (current == root) return true;
      Object parent = current.getParent();
      if (parent instanceof DefaultMutableTreeNode parentNode) {
        current = parentNode;
      } else {
        break;
      }
    }
    return false;
  }

  private static int networkInsertIndex(ServerNodes serverNodes) {
    if (serverNodes == null || serverNodes.serverNode == null) return 0;
    return networkInsertIndex(serverNodes.serverNode, serverNodes.pmNode);
  }

  private static int networkInsertIndex(DefaultMutableTreeNode serverNode) {
    if (serverNode == null) return 0;
    DefaultMutableTreeNode privateMessagesNode = null;
    for (int i = 0; i < serverNode.getChildCount(); i++) {
      Object child = serverNode.getChildAt(i);
      if (!(child instanceof DefaultMutableTreeNode childNode)) continue;
      Object userObject = childNode.getUserObject();
      if (userObject instanceof String label
          && "Private Messages".equalsIgnoreCase(Objects.toString(label, "").trim())) {
        privateMessagesNode = childNode;
        break;
      }
    }
    return networkInsertIndex(serverNode, privateMessagesNode);
  }

  private static int networkInsertIndex(
      DefaultMutableTreeNode serverNode, DefaultMutableTreeNode privateMessagesNode) {
    if (serverNode == null) return 0;
    int insertIdx = serverNode.getChildCount();
    int privateMessagesIdx =
        privateMessagesNode == null ? -1 : serverNode.getIndex(privateMessagesNode);
    if (privateMessagesIdx >= 0) {
      insertIdx = Math.min(insertIdx, privateMessagesIdx);
    }
    return Math.max(0, insertIdx);
  }

  private static boolean isPrivateMessageTarget(TargetRef ref) {
    if (ref == null) return false;
    return !ref.isStatus() && !ref.isChannel() && !ref.isUiOnly();
  }

  private static String normalizeToken(String token) {
    return Objects.toString(token, "").trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String resolveNetworkLabel(String token, String label) {
    String explicit = Objects.toString(label, "").trim();
    if (!explicit.isEmpty()) return explicit;
    return friendlyNetworkLabel(token);
  }

  private static String friendlyNetworkLabel(String token) {
    String raw = normalizeToken(token);
    if (raw.isEmpty()) return "Network";
    if (raw.chars().allMatch(Character::isDigit)) {
      return "Network " + raw;
    }
    String spaced = raw.replace('-', ' ').replace('_', ' ').trim();
    if (spaced.isEmpty()) return raw;
    String[] words = spaced.split("\\s+");
    StringBuilder label = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) continue;
      if (!label.isEmpty()) label.append(' ');
      label.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) {
        label.append(word.substring(1));
      }
    }
    return label.isEmpty() ? raw : label.toString();
  }
}
