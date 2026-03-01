package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.ServerEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.tree.TreePath;

/** Synchronizes the server tree structure with the latest server catalog snapshot. */
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

    void updateBouncerControlLabels(
        Set<String> nextSojuBouncerControl, Set<String> nextZncBouncerControl);

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

  private final Map<String, String> serverDisplayNames;
  private final Set<String> ephemeralServerIds;
  private final Set<String> sojuBouncerControlServerIds;
  private final Set<String> zncBouncerControlServerIds;
  private final Map<String, String> sojuOriginByServerId;
  private final Map<String, String> zncOriginByServerId;
  private final Context context;

  public ServerTreeServerCatalogSynchronizer(
      Map<String, String> serverDisplayNames,
      Set<String> ephemeralServerIds,
      Set<String> sojuBouncerControlServerIds,
      Set<String> zncBouncerControlServerIds,
      Map<String, String> sojuOriginByServerId,
      Map<String, String> zncOriginByServerId,
      Context context) {
    this.serverDisplayNames = Objects.requireNonNull(serverDisplayNames, "serverDisplayNames");
    this.ephemeralServerIds = Objects.requireNonNull(ephemeralServerIds, "ephemeralServerIds");
    this.sojuBouncerControlServerIds =
        Objects.requireNonNull(sojuBouncerControlServerIds, "sojuBouncerControlServerIds");
    this.zncBouncerControlServerIds =
        Objects.requireNonNull(zncBouncerControlServerIds, "zncBouncerControlServerIds");
    this.sojuOriginByServerId =
        Objects.requireNonNull(sojuOriginByServerId, "sojuOriginByServerId");
    this.zncOriginByServerId = Objects.requireNonNull(zncOriginByServerId, "zncOriginByServerId");
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
      }
    }

    sojuOriginByServerId.clear();
    sojuOriginByServerId.putAll(nextSojuOrigins);
    zncOriginByServerId.clear();
    zncOriginByServerId.putAll(nextZncOrigins);

    for (String id : newIds) {
      if (id.startsWith("soju:") || id.startsWith("znc:")) continue;
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
      }
    }

    context.updateBouncerControlLabels(nextSojuBouncerControl, nextZncBouncerControl);

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
