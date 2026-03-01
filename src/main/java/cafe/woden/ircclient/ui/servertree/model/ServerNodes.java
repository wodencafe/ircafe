package cafe.woden.ircclient.ui.servertree.model;

import cafe.woden.ircclient.app.api.TargetRef;
import javax.swing.tree.DefaultMutableTreeNode;

public final class ServerNodes {
  public final DefaultMutableTreeNode serverNode;
  public final DefaultMutableTreeNode pmNode;
  public final DefaultMutableTreeNode otherNode;
  public final DefaultMutableTreeNode monitorNode;
  public final DefaultMutableTreeNode interceptorsNode;
  public final TargetRef statusRef;
  public final TargetRef notificationsRef;
  public final TargetRef logViewerRef;
  public final TargetRef channelListRef;
  public final TargetRef weechatFiltersRef;
  public final TargetRef ignoresRef;
  public final TargetRef dccTransfersRef;

  public ServerNodes(
      DefaultMutableTreeNode serverNode,
      DefaultMutableTreeNode pmNode,
      DefaultMutableTreeNode otherNode,
      DefaultMutableTreeNode monitorNode,
      DefaultMutableTreeNode interceptorsNode,
      TargetRef statusRef,
      TargetRef notificationsRef,
      TargetRef logViewerRef,
      TargetRef channelListRef,
      TargetRef weechatFiltersRef,
      TargetRef ignoresRef,
      TargetRef dccTransfersRef) {
    this.serverNode = serverNode;
    this.pmNode = pmNode;
    this.otherNode = otherNode;
    this.monitorNode = monitorNode;
    this.interceptorsNode = interceptorsNode;
    this.statusRef = statusRef;
    this.notificationsRef = notificationsRef;
    this.logViewerRef = logViewerRef;
    this.channelListRef = channelListRef;
    this.weechatFiltersRef = weechatFiltersRef;
    this.ignoresRef = ignoresRef;
    this.dccTransfersRef = dccTransfersRef;
  }
}
