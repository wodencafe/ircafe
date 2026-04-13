package cafe.woden.ircclient.ui.servertree.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeApplicationNodesTest {

  @Test
  void initializeAddsBuiltInApplicationLeaves() {
    DefaultMutableTreeNode applicationRoot = new DefaultMutableTreeNode("Application");
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    ServerTreeApplicationNodes nodes = new ServerTreeApplicationNodes(applicationRoot, leaves);

    nodes.initialize();

    assertEquals(8, applicationRoot.getChildCount());
    assertNotNull(leaves.get(TargetRef.applicationPlugins()));
    assertEquals("Plugins", nodes.labelFor(TargetRef.applicationPlugins()));
    assertNotNull(leaves.get(TargetRef.applicationJfr()));
    assertEquals("JFR", nodes.labelFor(TargetRef.applicationJfr()));
  }

  @Test
  void addLeafFallsBackToMappedLabelWhenBlankLabelProvided() {
    DefaultMutableTreeNode applicationRoot = new DefaultMutableTreeNode("Application");
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    ServerTreeApplicationNodes nodes = new ServerTreeApplicationNodes(applicationRoot, leaves);

    nodes.addLeaf(TargetRef.applicationSpring(), " ");

    DefaultMutableTreeNode springNode = leaves.get(TargetRef.applicationSpring());
    ServerTreeNodeData data = (ServerTreeNodeData) springNode.getUserObject();
    assertEquals("Spring", data.label);
  }
}
