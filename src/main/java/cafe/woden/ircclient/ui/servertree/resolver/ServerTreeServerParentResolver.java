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
import org.springframework.stereotype.Component;

/** Resolves where a server root should be attached in the tree hierarchy. */
@Component
public final class ServerTreeServerParentResolver {

  public interface Context {
    Map<String, String> originByServerIdForBackend(String backendId);

    boolean hasServer(String serverId);

    void ensureServerRoot(String serverId);

    DefaultMutableTreeNode ircRoot();

    DefaultMutableTreeNode backendGroupNode(String backendId, String originServerId);
  }

  public static Context context(
      Map<String, Map<String, String>> originByServerIdByBackendId,
      Predicate<String> hasServer,
      Consumer<String> ensureServerRoot,
      Supplier<DefaultMutableTreeNode> ircRoot,
      BiFunction<String, String, DefaultMutableTreeNode> backendGroupNode) {
    Objects.requireNonNull(originByServerIdByBackendId, "originByServerIdByBackendId");
    Objects.requireNonNull(hasServer, "hasServer");
    Objects.requireNonNull(ensureServerRoot, "ensureServerRoot");
    Objects.requireNonNull(ircRoot, "ircRoot");
    Objects.requireNonNull(backendGroupNode, "backendGroupNode");
    return new Context() {
      @Override
      public Map<String, String> originByServerIdForBackend(String backendId) {
        return originByServerIdByBackendId.getOrDefault(backendId, Map.of());
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
      public DefaultMutableTreeNode backendGroupNode(String backendId, String originServerId) {
        return backendGroupNode.apply(backendId, originServerId);
      }
    };
  }

  public DefaultMutableTreeNode resolveParentForServer(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    String id = normalize(serverId);
    DefaultMutableTreeNode parent = in.ircRoot();
    if (id.isEmpty()) return parent;

    String backendId = ServerTreeBouncerBackends.backendIdForServerId(id);
    if (backendId == null) {
      return parent;
    }
    String prefix = ServerTreeBouncerBackends.prefixFor(backendId);
    String origin = in.originByServerIdForBackend(backendId).get(id);
    if (origin == null || origin.isBlank()) {
      origin = ServerTreeNetworkGroupManager.parseOriginFromCompoundServerId(id, prefix);
    }
    if (origin != null && !origin.isBlank()) {
      ensureOriginServerExists(in, origin);
      DefaultMutableTreeNode group = in.backendGroupNode(backendId, origin);
      if (group != null) parent = group;
    }
    return parent;
  }

  private void ensureOriginServerExists(Context context, String originServerId) {
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
