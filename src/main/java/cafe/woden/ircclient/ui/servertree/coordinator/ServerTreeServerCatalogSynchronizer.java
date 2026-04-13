package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.springframework.stereotype.Component;

/** Synchronizes the server tree structure with the latest server catalog snapshot. */
@Component
public final class ServerTreeServerCatalogSynchronizer {

  public interface Context {
    boolean treeHasSelectionPath();

    void markStartupSelectionCompleted();

    boolean startupSelectionCompleted();

    TargetRef selectedTargetRef();

    boolean hasServer(String serverId);

    Set<String> currentServerIds();

    void addServerRoot(String serverId);

    void removeServerRoot(String serverId);

    void updateBouncerControlLabels(Map<String, Set<String>> nextBouncerControlByBackendId);

    void nodeChangedForServer(String serverId);

    Set<TreePath> snapshotExpandedTreePaths();

    void reloadTreeModel();

    void restoreExpandedTreePaths(Set<TreePath> expanded);

    void runLater(Runnable task);

    boolean hasValidTreeSelection();

    boolean hasLeaf(TargetRef ref);

    void selectTarget(TargetRef ref);

    String firstServerId();

    void selectStartupDefaultForServer(String serverId);

    void selectDefaultPath();

    Map<String, String> serverDisplayNames();

    Set<String> ephemeralServerIds();

    Map<String, Set<String>> bouncerControlServerIdsByBackendId();

    Map<String, Map<String, String>> originByServerIdByBackendId();
  }

  public static Context context(
      JTree tree,
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Map<String, String> serverDisplayNames,
      Set<String> ephemeralServerIds,
      Map<String, Set<String>> bouncerControlServerIdsByBackendId,
      Map<String, Map<String, String>> originByServerIdByBackendId,
      DefaultTreeModel model,
      DefaultMutableTreeNode root,
      BooleanSupplier startupSelectionCompleted,
      Runnable markStartupSelectionCompleted,
      Supplier<TargetRef> selectedTargetRef,
      Consumer<String> addServerRoot,
      Consumer<String> removeServerRoot,
      Consumer<Map<String, Set<String>>> updateBouncerControlLabels,
      Supplier<Set<TreePath>> snapshotExpandedTreePaths,
      Consumer<Set<TreePath>> restoreExpandedTreePaths,
      BooleanSupplier hasValidTreeSelection,
      Consumer<TargetRef> selectTarget,
      Supplier<String> firstServerId,
      Consumer<String> selectStartupDefaultForServer,
      Supplier<TreePath> defaultSelectionPath) {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(servers, "servers");
    Objects.requireNonNull(leaves, "leaves");
    Objects.requireNonNull(serverDisplayNames, "serverDisplayNames");
    Objects.requireNonNull(ephemeralServerIds, "ephemeralServerIds");
    Objects.requireNonNull(
        bouncerControlServerIdsByBackendId, "bouncerControlServerIdsByBackendId");
    Objects.requireNonNull(originByServerIdByBackendId, "originByServerIdByBackendId");
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(startupSelectionCompleted, "startupSelectionCompleted");
    Objects.requireNonNull(markStartupSelectionCompleted, "markStartupSelectionCompleted");
    Objects.requireNonNull(selectedTargetRef, "selectedTargetRef");
    Objects.requireNonNull(addServerRoot, "addServerRoot");
    Objects.requireNonNull(removeServerRoot, "removeServerRoot");
    Objects.requireNonNull(updateBouncerControlLabels, "updateBouncerControlLabels");
    Objects.requireNonNull(snapshotExpandedTreePaths, "snapshotExpandedTreePaths");
    Objects.requireNonNull(restoreExpandedTreePaths, "restoreExpandedTreePaths");
    Objects.requireNonNull(hasValidTreeSelection, "hasValidTreeSelection");
    Objects.requireNonNull(selectTarget, "selectTarget");
    Objects.requireNonNull(firstServerId, "firstServerId");
    Objects.requireNonNull(selectStartupDefaultForServer, "selectStartupDefaultForServer");
    Objects.requireNonNull(defaultSelectionPath, "defaultSelectionPath");
    return new Context() {
      @Override
      public boolean treeHasSelectionPath() {
        return tree.getSelectionPath() != null;
      }

      @Override
      public void markStartupSelectionCompleted() {
        markStartupSelectionCompleted.run();
      }

      @Override
      public boolean startupSelectionCompleted() {
        return startupSelectionCompleted.getAsBoolean();
      }

      @Override
      public TargetRef selectedTargetRef() {
        return selectedTargetRef.get();
      }

      @Override
      public boolean hasServer(String serverId) {
        return servers.containsKey(serverId);
      }

      @Override
      public Set<String> currentServerIds() {
        return servers.keySet();
      }

      @Override
      public void addServerRoot(String serverId) {
        addServerRoot.accept(serverId);
      }

      @Override
      public void removeServerRoot(String serverId) {
        removeServerRoot.accept(serverId);
      }

      @Override
      public void updateBouncerControlLabels(
          Map<String, Set<String>> nextBouncerControlByBackendId) {
        updateBouncerControlLabels.accept(nextBouncerControlByBackendId);
      }

      @Override
      public void nodeChangedForServer(String serverId) {
        ServerNodes serverNodes = servers.get(serverId);
        if (serverNodes != null) {
          model.nodeChanged(serverNodes.serverNode);
        }
      }

      @Override
      public Set<TreePath> snapshotExpandedTreePaths() {
        return snapshotExpandedTreePaths.get();
      }

      @Override
      public void reloadTreeModel() {
        model.reload(root);
      }

      @Override
      public void restoreExpandedTreePaths(Set<TreePath> expanded) {
        restoreExpandedTreePaths.accept(expanded);
      }

      @Override
      public void runLater(Runnable task) {
        SwingUtilities.invokeLater(task);
      }

      @Override
      public boolean hasValidTreeSelection() {
        return hasValidTreeSelection.getAsBoolean();
      }

      @Override
      public boolean hasLeaf(TargetRef ref) {
        return leaves.containsKey(ref);
      }

      @Override
      public void selectTarget(TargetRef ref) {
        selectTarget.accept(ref);
      }

      @Override
      public String firstServerId() {
        return firstServerId.get();
      }

      @Override
      public void selectStartupDefaultForServer(String serverId) {
        selectStartupDefaultForServer.accept(serverId);
      }

      @Override
      public void selectDefaultPath() {
        tree.setSelectionPath(defaultSelectionPath.get());
      }

      @Override
      public Map<String, String> serverDisplayNames() {
        return serverDisplayNames;
      }

      @Override
      public Set<String> ephemeralServerIds() {
        return ephemeralServerIds;
      }

      @Override
      public Map<String, Set<String>> bouncerControlServerIdsByBackendId() {
        return bouncerControlServerIdsByBackendId;
      }

      @Override
      public Map<String, Map<String, String>> originByServerIdByBackendId() {
        return originByServerIdByBackendId;
      }
    };
  }

  public void syncServers(Context context, List<ServerEntry> latest) {
    Context in = Objects.requireNonNull(context, "context");
    Map<String, String> serverDisplayNames = in.serverDisplayNames();
    Set<String> ephemeralServerIds = in.ephemeralServerIds();
    Map<String, Set<String>> bouncerControlServerIdsByBackendId =
        in.bouncerControlServerIdsByBackendId();
    Map<String, Map<String, String>> originByServerIdByBackendId = in.originByServerIdByBackendId();

    if (in.treeHasSelectionPath()) {
      in.markStartupSelectionCompleted();
    }
    TargetRef selectedRefBeforeReload = in.selectedTargetRef();

    Set<String> newIds = new HashSet<>();
    Map<String, String> nextDisplay = new HashMap<>();
    Set<String> nextEphemeral = new HashSet<>();
    Map<String, Set<String>> nextBouncerControlByBackendId = new HashMap<>();
    Map<String, Map<String, String>> nextOriginByServerIdByBackendId = new HashMap<>();
    for (String backendId : ServerTreeBouncerBackends.orderedIds()) {
      nextBouncerControlByBackendId.put(backendId, new HashSet<>());
      nextOriginByServerIdByBackendId.put(backendId, new HashMap<>());
    }

    if (latest != null) {
      for (ServerEntry entry : latest) {
        if (entry == null || entry.server() == null) continue;
        String id = normalize(entry.server().id());
        if (id.isEmpty()) continue;

        newIds.add(id);
        nextDisplay.put(id, computeServerDisplayName(entry));
        if (entry.ephemeral()) nextEphemeral.add(id);

        String backendId = ServerTreeBouncerBackends.backendIdForServerId(id);
        if (backendId != null) {
          String prefix = ServerTreeBouncerBackends.prefixFor(backendId);
          String origin = normalize(entry.originId());
          if (origin.isEmpty()) {
            origin = parseOriginFromCompoundServerId(id, prefix);
          }
          if (!origin.isBlank()) {
            nextBouncerControlByBackendId
                .computeIfAbsent(backendId, ignored -> new HashSet<>())
                .add(origin);
            nextOriginByServerIdByBackendId
                .computeIfAbsent(backendId, ignored -> new HashMap<>())
                .put(id, origin);
          }
        }
      }
    }

    for (Map<String, String> originsByServerId : originByServerIdByBackendId.values()) {
      if (originsByServerId != null) {
        originsByServerId.clear();
      }
    }
    for (Map.Entry<String, Map<String, String>> entry :
        nextOriginByServerIdByBackendId.entrySet()) {
      Map<String, String> target =
          originByServerIdByBackendId.computeIfAbsent(entry.getKey(), ignored -> new HashMap<>());
      target.putAll(entry.getValue());
    }

    for (String id : newIds) {
      if (ServerTreeBouncerBackends.isBouncerServerId(id)) continue;
      if (!in.hasServer(id)) {
        in.addServerRoot(id);
      }
    }
    for (String id : newIds) {
      if (!in.hasServer(id)) {
        in.addServerRoot(id);
      }
    }

    for (String existing : List.copyOf(in.currentServerIds())) {
      if (!newIds.contains(existing)) {
        in.removeServerRoot(existing);
        serverDisplayNames.remove(existing);
        ephemeralServerIds.remove(existing);
        for (Set<String> controlServerIds : bouncerControlServerIdsByBackendId.values()) {
          if (controlServerIds != null) {
            controlServerIds.remove(existing);
          }
        }
      }
    }

    in.updateBouncerControlLabels(nextBouncerControlByBackendId);

    for (String id : newIds) {
      String next = nextDisplay.getOrDefault(id, id);
      String prev = serverDisplayNames.put(id, next);

      boolean eph = nextEphemeral.contains(id);
      boolean prevEph = ephemeralServerIds.contains(id);
      if (eph) {
        ephemeralServerIds.add(id);
      } else {
        ephemeralServerIds.remove(id);
      }

      if (!Objects.equals(prev, next) || eph != prevEph) {
        in.nodeChangedForServer(id);
      }
    }

    Set<TreePath> expandedBeforeReload = in.snapshotExpandedTreePaths();
    in.reloadTreeModel();
    in.restoreExpandedTreePaths(expandedBeforeReload);

    in.runLater(
        () -> {
          if (in.hasValidTreeSelection()) {
            return;
          }

          if (selectedRefBeforeReload != null && in.hasLeaf(selectedRefBeforeReload)) {
            in.selectTarget(selectedRefBeforeReload);
            return;
          }

          String firstServerId = normalize(in.firstServerId());
          if (!firstServerId.isBlank()) {
            if (!in.startupSelectionCompleted() || selectedRefBeforeReload == null) {
              in.selectStartupDefaultForServer(firstServerId);
              in.markStartupSelectionCompleted();
            } else {
              in.selectTarget(new TargetRef(firstServerId, "status"));
            }
          } else {
            in.selectDefaultPath();
          }
        });
  }

  private static String computeServerDisplayName(ServerEntry entry) {
    if (entry == null || entry.server() == null) return "";
    String id = normalize(entry.server().id());
    if (id.isEmpty()) return id;
    if (!entry.ephemeral()) return id;

    String login = normalize(entry.server().login());
    if (!login.isEmpty()) {
      int slash = login.indexOf('/');
      if (slash >= 0 && slash + 1 < login.length()) {
        String after = login.substring(slash + 1);
        int at = after.indexOf('@');
        if (at >= 0) after = after.substring(0, at);
        after = after.trim();
        if (!after.isEmpty()) return after;
      }
    }

    return id;
  }

  private static String parseOriginFromCompoundServerId(String serverId, String prefix) {
    String id = normalize(serverId);
    String p = normalize(prefix);
    if (id.isEmpty() || p.isEmpty() || !id.startsWith(p)) return "";
    int start = p.length();
    int nextColon = id.indexOf(':', start);
    if (nextColon <= start) return "";
    return id.substring(start, nextColon).trim();
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
