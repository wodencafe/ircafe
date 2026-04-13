package cafe.woden.ircclient.ui.servertree.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeInterceptorActionsTest {

  @Test
  void refreshInterceptorNodeLabelUpdatesChildBadgeFromInterceptorHitCount() {
    InterceptorStore store = mock(InterceptorStore.class);
    TargetRef ref = TargetRef.interceptor("libera", "audit");
    ServerTreeNodeData existing = new ServerTreeNodeData(ref, "Old label");
    existing.unread = 9;
    existing.highlightUnread = 2;
    existing.detached = true;
    existing.detachedWarning = "Detached";
    existing.typingPulseUntilMs = 1234L;
    existing.typingDoneFadeStartMs = 5678L;
    DefaultMutableTreeNode interceptorNode = new DefaultMutableTreeNode(existing);
    AtomicReference<DefaultMutableTreeNode> changedNode = new AtomicReference<>();

    when(store.interceptorName("libera", "audit")).thenReturn("Audit");
    when(store.hitCount("libera", "audit")).thenReturn(3);

    ServerTreeInterceptorActions actions = new ServerTreeInterceptorActions();
    ServerTreeInterceptorActions.Context context =
        ServerTreeInterceptorActions.context(
            new JPanel(),
            store,
            "Interceptors",
            ref1 -> {},
            ref1 -> {},
            ref1 -> {},
            ref1 -> Map.of(ref, interceptorNode).get(ref1),
            serverId -> null,
            changedNode::set);

    actions.refreshInterceptorNodeLabel(context, "libera", "audit");

    assertSame(interceptorNode, changedNode.get());
    assertTrue(interceptorNode.getUserObject() instanceof ServerTreeNodeData);
    ServerTreeNodeData updated = (ServerTreeNodeData) interceptorNode.getUserObject();
    assertEquals("Audit", updated.label);
    assertEquals(3, updated.unread);
    assertEquals(0, updated.highlightUnread);
    assertTrue(updated.detached);
    assertEquals("Detached", updated.detachedWarning);
    assertEquals(1234L, updated.typingPulseUntilMs);
    assertEquals(5678L, updated.typingDoneFadeStartMs);
  }

  @Test
  void refreshInterceptorGroupCountClearsLegacyAggregateBadgeFromParentNode() {
    InterceptorStore store = mock(InterceptorStore.class);
    TargetRef groupRef = TargetRef.interceptorsGroup("libera");
    DefaultMutableTreeNode groupNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(groupRef, "Interceptors"));
    AtomicReference<DefaultMutableTreeNode> changedNode = new AtomicReference<>();

    assertTrue(groupNode.getUserObject() instanceof ServerTreeNodeData);
    ServerTreeNodeData nodeData = (ServerTreeNodeData) groupNode.getUserObject();
    nodeData.unread = 7;
    nodeData.highlightUnread = 1;

    ServerTreeInterceptorActions actions = new ServerTreeInterceptorActions();
    ServerTreeInterceptorActions.Context context =
        ServerTreeInterceptorActions.context(
            new JPanel(),
            store,
            "Interceptors",
            ref -> {},
            ref -> {},
            ref -> {},
            ref -> Map.of(groupRef, groupNode).get(ref),
            serverId -> groupNode,
            changedNode::set);

    actions.refreshInterceptorGroupCount(context, "libera");

    assertSame(groupNode, changedNode.get());
    assertEquals(0, nodeData.unread);
    assertEquals(0, nodeData.highlightUnread);
  }
}
