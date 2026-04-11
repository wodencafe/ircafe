package cafe.woden.ircclient.ui.servertree.resolver;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeServerParentResolverTest {

  private final ServerTreeServerParentResolver resolver = new ServerTreeServerParentResolver();

  @Test
  void returnsIrcRootForRegularServers() {
    DefaultMutableTreeNode ircRoot = new DefaultMutableTreeNode("IRC");
    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode("Group");

    ServerTreeServerParentResolver.Context context =
        ServerTreeServerParentResolver.context(
            Map.of(), __ -> true, __ -> {}, () -> ircRoot, (__1, __2) -> groupNode);

    assertSame(ircRoot, resolver.resolveParentForServer(context, "libera"));
  }

  @Test
  void routesBouncerChildServersUnderTheirOriginGroup() {
    DefaultMutableTreeNode ircRoot = new DefaultMutableTreeNode("IRC");
    DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode("Group");
    Map<String, Map<String, String>> origins = new HashMap<>();
    origins.put("soju", Map.of("soju:work:libera", "work"));

    ServerTreeServerParentResolver.Context context =
        ServerTreeServerParentResolver.context(
            origins,
            serverId -> "work".equals(serverId),
            __ -> {},
            () -> ircRoot,
            (backendId, originServerId) -> groupNode);

    assertSame(groupNode, resolver.resolveParentForServer(context, "soju:work:libera"));
  }
}
