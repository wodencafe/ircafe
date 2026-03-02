package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeServerParentResolver;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeServerParentResolver.Context}. */
public final class ServerTreeServerParentResolverContextAdapter
    implements ServerTreeServerParentResolver.Context {

  private final Predicate<String> hasServer;
  private final Consumer<String> ensureServerRoot;
  private final Supplier<DefaultMutableTreeNode> ircRoot;
  private final Function<String, DefaultMutableTreeNode> sojuGroupNode;
  private final Function<String, DefaultMutableTreeNode> zncGroupNode;

  public ServerTreeServerParentResolverContextAdapter(
      Predicate<String> hasServer,
      Consumer<String> ensureServerRoot,
      Supplier<DefaultMutableTreeNode> ircRoot,
      Function<String, DefaultMutableTreeNode> sojuGroupNode,
      Function<String, DefaultMutableTreeNode> zncGroupNode) {
    this.hasServer = Objects.requireNonNull(hasServer, "hasServer");
    this.ensureServerRoot = Objects.requireNonNull(ensureServerRoot, "ensureServerRoot");
    this.ircRoot = Objects.requireNonNull(ircRoot, "ircRoot");
    this.sojuGroupNode = Objects.requireNonNull(sojuGroupNode, "sojuGroupNode");
    this.zncGroupNode = Objects.requireNonNull(zncGroupNode, "zncGroupNode");
  }

  @Override
  public boolean hasServer(String serverId) {
    return hasServer.test(serverId);
  }

  @Override
  public void ensureServerRoot(String serverId) {
    ensureServerRoot.accept(serverId);
  }

  @Override
  public DefaultMutableTreeNode ircRoot() {
    return ircRoot.get();
  }

  @Override
  public DefaultMutableTreeNode sojuGroupNode(String originServerId) {
    return sojuGroupNode.apply(originServerId);
  }

  @Override
  public DefaultMutableTreeNode zncGroupNode(String originServerId) {
    return zncGroupNode.apply(originServerId);
  }
}
