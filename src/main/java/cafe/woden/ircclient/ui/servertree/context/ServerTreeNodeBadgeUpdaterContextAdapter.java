package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeNodeBadgeUpdater.Context}. */
public final class ServerTreeNodeBadgeUpdaterContextAdapter
    implements ServerTreeNodeBadgeUpdater.Context {

  private final Consumer<DefaultMutableTreeNode> nodeChanged;
  private final Function<String, DefaultMutableTreeNode> serverNodeById;

  public ServerTreeNodeBadgeUpdaterContextAdapter(
      Consumer<DefaultMutableTreeNode> nodeChanged,
      Function<String, DefaultMutableTreeNode> serverNodeById) {
    this.nodeChanged = Objects.requireNonNull(nodeChanged, "nodeChanged");
    this.serverNodeById = Objects.requireNonNull(serverNodeById, "serverNodeById");
  }

  @Override
  public void nodeChanged(DefaultMutableTreeNode node) {
    nodeChanged.accept(node);
  }

  @Override
  public void nodeChangedForServer(String serverId) {
    DefaultMutableTreeNode node = serverNodeById.apply(serverId);
    if (node != null) {
      nodeChanged.accept(node);
    }
  }
}
