package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.model.TargetRef;
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

/** Synchronizes the server tree structure with the latest server catalog snapshot. */
public final class ServerTreeServerCatalogSynchronizer {

  @FunctionalInterface
  public interface TripleSetConsumer {
    void accept(Set<String> first, Set<String> second, Set<String> third);
  }

  public interface Context {
    boolean treeHasSelectionPath();

    void markStartupSelectionCompleted();

    boolean startupSelectionCompleted();

    TargetRef selectedTargetRef();

    boolean hasServer(String serverId);

    Set<String> currentServerIds();

    void addServerRoot(String serverId);

    void removeServerRoot(String serverId);

    void updateBouncerControlLabels(
        Set<String> nextSojuBouncerControl,
        Set<String> nextZncBouncerControl,
        Set<String> nextGenericBouncerControl);

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
  }

  public static Context context(
      JTree tree,
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      DefaultTreeModel model,
      DefaultMutableTreeNode root,
      BooleanSupplier startupSelectionCompleted,
      Runnable markStartupSelectionCompleted,
      Supplier<TargetRef> selectedTargetRef,
      Consumer<String> addServerRoot,
      Consumer<String> removeServerRoot,
      TripleSetConsumer updateBouncerControlLabels,
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
          Set<String> nextSojuBouncerControl,
          Set<String> nextZncBouncerControl,
          Set<String> nextGenericBouncerControl) {
        updateBouncerControlLabels.accept(
            nextSojuBouncerControl, nextZncBouncerControl, nextGenericBouncerControl);
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
    };
  }

  private final Map<String, String> serverDisplayNames;
  private final Set<String> ephemeralServerIds;
  private final Set<String> sojuBouncerControlServerIds;
  private final Set<String> zncBouncerControlServerIds;
  private final Set<String> genericBouncerControlServerIds;
  private final Map<String, String> sojuOriginByServerId;
  private final Map<String, String> zncOriginByServerId;
  private final Map<String, String> genericOriginByServerId;
  private final Context context;

  public ServerTreeServerCatalogSynchronizer(
      Map<String, String> serverDisplayNames,
      Set<String> ephemeralServerIds,
      Set<String> sojuBouncerControlServerIds,
      Set<String> zncBouncerControlServerIds,
      Set<String> genericBouncerControlServerIds,
      Map<String, String> sojuOriginByServerId,
      Map<String, String> zncOriginByServerId,
      Map<String, String> genericOriginByServerId,
      Context context) {
    this.serverDisplayNames = Objects.requireNonNull(serverDisplayNames, "serverDisplayNames");
    this.ephemeralServerIds = Objects.requireNonNull(ephemeralServerIds, "ephemeralServerIds");
    this.sojuBouncerControlServerIds =
        Objects.requireNonNull(sojuBouncerControlServerIds, "sojuBouncerControlServerIds");
    this.zncBouncerControlServerIds =
        Objects.requireNonNull(zncBouncerControlServerIds, "zncBouncerControlServerIds");
    this.genericBouncerControlServerIds =
        Objects.requireNonNull(genericBouncerControlServerIds, "genericBouncerControlServerIds");
    this.sojuOriginByServerId =
        Objects.requireNonNull(sojuOriginByServerId, "sojuOriginByServerId");
    this.zncOriginByServerId = Objects.requireNonNull(zncOriginByServerId, "zncOriginByServerId");
    this.genericOriginByServerId =
        Objects.requireNonNull(genericOriginByServerId, "genericOriginByServerId");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void syncServers(List<ServerEntry> latest) {
    if (context.treeHasSelectionPath()) {
      context.markStartupSelectionCompleted();
    }
    TargetRef selectedRefBeforeReload = context.selectedTargetRef();

    Set<String> newIds = new HashSet<>();
    Map<String, String> nextDisplay = new HashMap<>();
    Set<String> nextEphemeral = new HashSet<>();
    Set<String> nextSojuBouncerControl = new HashSet<>();
    Map<String, String> nextSojuOrigins = new HashMap<>();
    Set<String> nextZncBouncerControl = new HashSet<>();
    Map<String, String> nextZncOrigins = new HashMap<>();
    Set<String> nextGenericBouncerControl = new HashSet<>();
    Map<String, String> nextGenericOrigins = new HashMap<>();

    if (latest != null) {
      for (ServerEntry entry : latest) {
        if (entry == null || entry.server() == null) continue;
        String id = normalize(entry.server().id());
        if (id.isEmpty()) continue;

        newIds.add(id);
        nextDisplay.put(id, computeServerDisplayName(entry));
        if (entry.ephemeral()) nextEphemeral.add(id);

        if (id.startsWith("soju:")) {
          String origin = normalize(entry.originId());
          if (origin.isEmpty()) {
            origin = parseOriginFromCompoundServerId(id, "soju:");
          }
          if (!origin.isBlank()) {
            nextSojuBouncerControl.add(origin);
            nextSojuOrigins.put(id, origin);
          }
        }

        if (id.startsWith("znc:")) {
          String origin = normalize(entry.originId());
          if (origin.isEmpty()) {
            origin = parseOriginFromCompoundServerId(id, "znc:");
          }
          if (!origin.isBlank()) {
            nextZncBouncerControl.add(origin);
            nextZncOrigins.put(id, origin);
          }
        }

        if (id.startsWith("bouncer:")) {
          String origin = normalize(entry.originId());
          if (origin.isEmpty()) {
            origin = parseOriginFromCompoundServerId(id, "bouncer:");
          }
          if (!origin.isBlank()) {
            nextGenericBouncerControl.add(origin);
            nextGenericOrigins.put(id, origin);
          }
        }
      }
    }

    sojuOriginByServerId.clear();
    sojuOriginByServerId.putAll(nextSojuOrigins);
    zncOriginByServerId.clear();
    zncOriginByServerId.putAll(nextZncOrigins);
    genericOriginByServerId.clear();
    genericOriginByServerId.putAll(nextGenericOrigins);

    for (String id : newIds) {
      if (id.startsWith("soju:") || id.startsWith("znc:") || id.startsWith("bouncer:")) continue;
      if (!context.hasServer(id)) {
        context.addServerRoot(id);
      }
    }
    for (String id : newIds) {
      if (!context.hasServer(id)) {
        context.addServerRoot(id);
      }
    }

    for (String existing : List.copyOf(context.currentServerIds())) {
      if (!newIds.contains(existing)) {
        context.removeServerRoot(existing);
        serverDisplayNames.remove(existing);
        ephemeralServerIds.remove(existing);
        sojuBouncerControlServerIds.remove(existing);
        zncBouncerControlServerIds.remove(existing);
        genericBouncerControlServerIds.remove(existing);
      }
    }

    context.updateBouncerControlLabels(
        nextSojuBouncerControl, nextZncBouncerControl, nextGenericBouncerControl);

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
        context.nodeChangedForServer(id);
      }
    }

    Set<TreePath> expandedBeforeReload = context.snapshotExpandedTreePaths();
    context.reloadTreeModel();
    context.restoreExpandedTreePaths(expandedBeforeReload);

    context.runLater(
        () -> {
          if (context.hasValidTreeSelection()) {
            return;
          }

          if (selectedRefBeforeReload != null && context.hasLeaf(selectedRefBeforeReload)) {
            context.selectTarget(selectedRefBeforeReload);
            return;
          }

          String firstServerId = normalize(context.firstServerId());
          if (!firstServerId.isBlank()) {
            if (!context.startupSelectionCompleted() || selectedRefBeforeReload == null) {
              context.selectStartupDefaultForServer(firstServerId);
              context.markStartupSelectionCompleted();
            } else {
              context.selectTarget(new TargetRef(firstServerId, "status"));
            }
          } else {
            context.selectDefaultPath();
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
