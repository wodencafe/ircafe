package cafe.woden.ircclient.ui.servertree.resolver;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
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

  private record NetworkNodes(
      DefaultMutableTreeNode networkNode,
      DefaultMutableTreeNode channelListNode,
      DefaultMutableTreeNode privateMessagesNode) {}

  private final Map<String, Map<String, NetworkNodes>> networkNodesByServer = new HashMap<>();
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

  public void forgetServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    networkNodesByServer.remove(sid);
  }

  private NetworkNodes ensureNetworkNodes(String serverId, String token, ServerNodes serverNodes) {
    Map<String, NetworkNodes> byToken =
        networkNodesByServer.computeIfAbsent(serverId, ignored -> new LinkedHashMap<>());
    NetworkNodes existing = byToken.get(token);
    if (existing != null
        && existing.networkNode().getParent() == serverNodes.serverNode
        && existing.channelListNode().getParent() == existing.networkNode()
        && existing.privateMessagesNode().getParent() == existing.networkNode()) {
      return existing;
    }

    DefaultMutableTreeNode networkNode = new DefaultMutableTreeNode(token);
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
    int insertIdx = serverNodes.serverNode.getChildCount();
    int privateMessagesIdx = serverNodes.serverNode.getIndex(serverNodes.pmNode);
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
}
