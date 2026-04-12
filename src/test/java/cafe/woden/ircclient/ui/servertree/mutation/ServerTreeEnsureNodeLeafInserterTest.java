package cafe.woden.ircclient.ui.servertree.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeEnsureNodeLeafInserterTest {

  @Test
  void insertLeafTracksLeafAndInitializesPrivateMessageOnlineState() {
    DefaultMutableTreeNode parent = new DefaultMutableTreeNode("parent");
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore =
        new ServerTreePrivateMessageOnlineStateStore();
    TargetRef ref = new TargetRef("libera", "tester");

    ServerTreeEnsureNodeLeafInserter inserter = new ServerTreeEnsureNodeLeafInserter();
    ServerTreeEnsureNodeLeafInserter.Context context =
        ServerTreeEnsureNodeLeafInserter.context(
            leaves, new DefaultTreeModel(parent), privateMessageOnlineStateStore, __ -> true);

    inserter.insertLeaf(context, parent, ref, "tester");

    assertEquals(1, parent.getChildCount());
    assertSame(parent.getChildAt(0), leaves.get(ref));
    assertEquals(List.of(ref), privateMessageOnlineStateStore.clearServer("libera"));
  }
}
