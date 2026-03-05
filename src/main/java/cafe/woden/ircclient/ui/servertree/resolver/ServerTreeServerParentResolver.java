package cafe.woden.ircclient.ui.servertree.resolver;

import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Resolves where a server root should be attached in the tree hierarchy. */
public final class ServerTreeServerParentResolver {

  public interface Context {
    boolean hasServer(String serverId);

    void ensureServerRoot(String serverId);

    DefaultMutableTreeNode ircRoot();

    DefaultMutableTreeNode backendGroupNode(String backendId, String originServerId);
  }

  public static Context context(
      Predicate<String> hasServer,
      Consumer<String> ensureServerRoot,
      Supplier<DefaultMutableTreeNode> ircRoot,
      BiFunction<String, String, DefaultMutableTreeNode> backendGroupNode) {
    Objects.requireNonNull(hasServer, "hasServer");
    Objects.requireNonNull(ensureServerRoot, "ensureServerRoot");
    Objects.requireNonNull(ircRoot, "ircRoot");
    Objects.requireNonNull(backendGroupNode, "backendGroupNode");
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
      public DefaultMutableTreeNode backendGroupNode(String backendId, String originServerId) {
        return backendGroupNode.apply(backendId, originServerId);
      }
    };
  }

  private final Map<String, Map<String, String>> originByServerIdByBackendId;
  private final Context context;

  public ServerTreeServerParentResolver(
      Map<String, Map<String, String>> originByServerIdByBackendId, Context context) {
    this.originByServerIdByBackendId =
        Objects.requireNonNull(originByServerIdByBackendId, "originByServerIdByBackendId");
    this.context = Objects.requireNonNull(context, "context");
  }

  public DefaultMutableTreeNode resolveParentForServer(String serverId) {
    String id = normalize(serverId);
    DefaultMutableTreeNode parent = context.ircRoot();
    if (id.isEmpty()) return parent;

    String backendId = ServerTreeBouncerBackends.backendIdForServerId(id);
    if (backendId == null) {
      return parent;
    }
    String prefix = ServerTreeBouncerBackends.prefixFor(backendId);
    String origin = originByServerIdForBackend(backendId).get(id);
    if (origin == null || origin.isBlank()) {
      origin = ServerTreeNetworkGroupManager.parseOriginFromCompoundServerId(id, prefix);
    }
    if (origin != null && !origin.isBlank()) {
      ensureOriginServerExists(origin);
      DefaultMutableTreeNode group = context.backendGroupNode(backendId, origin);
      if (group != null) parent = group;
    }
    return parent;
  }

  private Map<String, String> originByServerIdForBackend(String backendId) {
    return originByServerIdByBackendId.getOrDefault(backendId, Map.of());
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
