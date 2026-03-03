package cafe.woden.ircclient.ui.servertree.resolver;

import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Resolves where a server root should be attached in the tree hierarchy. */
public final class ServerTreeServerParentResolver {

  public interface Context {
    boolean hasServer(String serverId);

    void ensureServerRoot(String serverId);

    DefaultMutableTreeNode ircRoot();

    DefaultMutableTreeNode sojuGroupNode(String originServerId);

    DefaultMutableTreeNode zncGroupNode(String originServerId);
  }

  public static Context context(
      Predicate<String> hasServer,
      Consumer<String> ensureServerRoot,
      Supplier<DefaultMutableTreeNode> ircRoot,
      Function<String, DefaultMutableTreeNode> sojuGroupNode,
      Function<String, DefaultMutableTreeNode> zncGroupNode) {
    Objects.requireNonNull(hasServer, "hasServer");
    Objects.requireNonNull(ensureServerRoot, "ensureServerRoot");
    Objects.requireNonNull(ircRoot, "ircRoot");
    Objects.requireNonNull(sojuGroupNode, "sojuGroupNode");
    Objects.requireNonNull(zncGroupNode, "zncGroupNode");
    return new Context() {
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
    };
  }

  private final Map<String, String> sojuOriginByServerId;
  private final Map<String, String> zncOriginByServerId;
  private final Context context;

  public ServerTreeServerParentResolver(
      Map<String, String> sojuOriginByServerId,
      Map<String, String> zncOriginByServerId,
      Context context) {
    this.sojuOriginByServerId =
        Objects.requireNonNull(sojuOriginByServerId, "sojuOriginByServerId");
    this.zncOriginByServerId = Objects.requireNonNull(zncOriginByServerId, "zncOriginByServerId");
    this.context = Objects.requireNonNull(context, "context");
  }

  public DefaultMutableTreeNode resolveParentForServer(String serverId) {
    String id = normalize(serverId);
    DefaultMutableTreeNode parent = context.ircRoot();
    if (id.isEmpty()) return parent;

    if (id.startsWith("soju:")) {
      String origin = sojuOriginByServerId.get(id);
      if (origin == null || origin.isBlank()) {
        origin = ServerTreeNetworkGroupManager.parseOriginFromCompoundServerId(id, "soju:");
      }
      if (origin != null && !origin.isBlank()) {
        ensureOriginServerExists(origin);
        DefaultMutableTreeNode group = context.sojuGroupNode(origin);
        if (group != null) parent = group;
      }
      return parent;
    }

    if (id.startsWith("znc:")) {
      String origin = zncOriginByServerId.get(id);
      if (origin == null || origin.isBlank()) {
        origin = ServerTreeNetworkGroupManager.parseOriginFromCompoundServerId(id, "znc:");
      }
      if (origin != null && !origin.isBlank()) {
        ensureOriginServerExists(origin);
        DefaultMutableTreeNode group = context.zncGroupNode(origin);
        if (group != null) parent = group;
      }
    }
    return parent;
  }

  private void ensureOriginServerExists(String originServerId) {
    String origin = normalize(originServerId);
    if (origin.isEmpty()) return;
    if (!context.hasServer(origin)) {
      context.ensureServerRoot(origin);
    }
  }

  private static String normalize(String value) {
    return ServerTreeConventions.normalize(value);
  }
}
