package cafe.woden.ircclient.ui.servertree.resolver;

import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayoutNode;
import cafe.woden.ircclient.model.TargetRef;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeEnsureNodeParentResolverTest {

  @Test
  void resolveParentPlacesInterceptorTargetsUnderInterceptorsGroupWhenPresent() {
    ServerTreeEnsureNodeParentResolver resolver = new ServerTreeEnsureNodeParentResolver();
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("server");
    DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("interceptors");

    DefaultMutableTreeNode resolved =
        resolver.resolveParent(
            TargetRef.interceptor("libera", "audit"),
            new ServerTreeEnsureNodeParentResolver.ParentNodes(
                serverNode, null, null, null, interceptorsNode),
            ServerTreeBuiltInLayoutNode.INTERCEPTORS,
            ServerTreeBuiltInLayout.defaults(),
            () -> null);

    assertSame(interceptorsNode, resolved);
  }

  @Test
  void resolveParentUsesChannelListNodeForChannelTargetsWhenAvailable() {
    ServerTreeEnsureNodeParentResolver resolver = new ServerTreeEnsureNodeParentResolver();
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("server");
    DefaultMutableTreeNode channelListNode = new DefaultMutableTreeNode("channels");

    DefaultMutableTreeNode resolved =
        resolver.resolveParent(
            new TargetRef("libera", "#ircafe"),
            new ServerTreeEnsureNodeParentResolver.ParentNodes(serverNode, null, null, null, null),
            null,
            ServerTreeBuiltInLayout.defaults(),
            () -> channelListNode);

    assertSame(channelListNode, resolved);
  }
}
