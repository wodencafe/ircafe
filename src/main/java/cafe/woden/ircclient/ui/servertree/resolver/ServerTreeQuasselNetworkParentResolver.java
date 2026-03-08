package cafe.woden.ircclient.ui.servertree.resolver;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Resolves per-network parent containers for Quassel-qualified targets in the server tree. */
public final class ServerTreeQuasselNetworkParentResolver {

  private static final String EMPTY_STATE_LABEL = "No Quassel networks configured";

  private record NetworkNodes(
      DefaultMutableTreeNode networkNode,
      DefaultMutableTreeNode channelListNode,
      DefaultMutableTreeNode privateMessagesNode) {}

  private final Map<String, Map<String, NetworkNodes>> networkNodesByServer = new HashMap<>();
  private final Map<String, DefaultMutableTreeNode> emptyStateNodesByServer = new HashMap<>();
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final DefaultTreeModel model;
  private final Predicate<String> isQuasselServer;
  private final String channelListLabel;
  private final String privateMessagesLabel;

  public ServerTreeQuasselNetworkParentResolver(
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      DefaultTreeModel model,
      Predicate<String> isQuasselServer,
      String channelListLabel,
      String privateMessagesLabel) {
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.model = Objects.requireNonNull(model, "model");
    this.isQuasselServer = Objects.requireNonNull(isQuasselServer, "isQuasselServer");
    this.channelListLabel = Objects.toString(channelListLabel, "Channel List");
    this.privateMessagesLabel = Objects.toString(privateMessagesLabel, "Private Messages");
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
    if (token.isEmpty()) return null;

    NetworkNodes networkNodes = ensureNetworkNodes(serverId, token, serverNodes);
    aliasServerChannelListToKnownNetwork(serverId);
    if (ref.isChannel()) return networkNodes.channelListNode();
    if (isPrivateMessageTarget(ref)) return networkNodes.privateMessagesNode();
    if (ref.isChannelList()) {
      leaves.put(ref, networkNodes.channelListNode());
      return networkNodes.networkNode();
    }
    return null;
  }

  public void initializeServer(String serverId, ServerNodes serverNodes) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || serverNodes == null || serverNodes.serverNode == null) return;
    if (!isQuasselServer.test(sid)) return;
    maybeEnsureEmptyStateNode(sid, serverNodes.serverNode);
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
  }

  private NetworkNodes ensureNetworkNodes(String serverId, String token, ServerNodes serverNodes) {
    removeEmptyStateNodeIfPresent(serverId, serverNodes.serverNode);
    Map<String, NetworkNodes> byToken =
        networkNodesByServer.computeIfAbsent(serverId, ignored -> new LinkedHashMap<>());
    NetworkNodes existing = byToken.get(token);
    if (existing != null
        && existing.networkNode().getParent() == serverNodes.serverNode
        && existing.channelListNode().getParent() == existing.networkNode()
        && existing.privateMessagesNode().getParent() == existing.networkNode()) {
      return existing;
    }

    DefaultMutableTreeNode networkNode =
        new DefaultMutableTreeNode(
            ServerTreeQuasselNetworkNodeData.network(serverId, token, friendlyNetworkLabel(token)));
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

    NetworkNodes created = new NetworkNodes(networkNode, channelListNode, privateMessagesNode);
    byToken.put(token, created);
    return created;
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
    model.nodesWereInserted(serverNode, new int[] {idx});
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
    if (byToken == null || byToken.isEmpty()) return null;
    NetworkNodes first = byToken.values().iterator().next();
    if (first == null || first.channelListNode() == null) return null;

    TargetRef serverChannelListRef = TargetRef.channelList(serverId);
    DefaultMutableTreeNode existingServerChannelList = leaves.get(serverChannelListRef);
    if (existingServerChannelList != null && existingServerChannelList != first.channelListNode()) {
      detachNodeIfNeeded(existingServerChannelList);
    }
    leaves.put(serverChannelListRef, first.channelListNode());
    return first.channelListNode();
  }

  private void detachNodeIfNeeded(DefaultMutableTreeNode node) {
    if (!(node.getParent() instanceof DefaultMutableTreeNode oldParent)) return;
    int oldIndex = oldParent.getIndex(node);
    if (oldIndex < 0) return;
    oldParent.remove(node);
    model.nodesWereRemoved(oldParent, new int[] {oldIndex}, new Object[] {node});
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
    int privateMessagesIdx = serverNode.getIndex(privateMessagesNode);
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
    String value = Objects.toString(token, "").trim().toLowerCase(Locale.ROOT);
    return value;
  }

  private static String normalizeServerId(String serverId) {
    String value = Objects.toString(serverId, "").trim();
    return value;
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
