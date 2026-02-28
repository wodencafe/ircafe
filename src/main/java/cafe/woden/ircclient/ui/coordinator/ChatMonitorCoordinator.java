package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.monitor.MonitorPanel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Owns monitor row projection and private-message online status snapshots for {@link ChatDockable}.
 */
public final class ChatMonitorCoordinator {

  private final MonitorPanel monitorPanel;
  private final MonitorListService monitorListService;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final Map<String, Map<String, Boolean>> privateMessageOnlineByServer = new HashMap<>();

  public ChatMonitorCoordinator(
      MonitorPanel monitorPanel,
      MonitorListService monitorListService,
      Supplier<TargetRef> activeTargetSupplier) {
    this.monitorPanel = Objects.requireNonNull(monitorPanel, "monitorPanel");
    this.monitorListService = Objects.requireNonNull(monitorListService, "monitorListService");
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
  }

  public void bind(CompositeDisposable disposables) {
    Objects.requireNonNull(disposables, "disposables");
    disposables.add(
        monitorListService
            .changes()
            .subscribe(
                change -> {
                  if (change == null) return;
                  TargetRef activeTarget = activeTargetSupplier.get();
                  if (activeTarget == null || !activeTarget.isMonitorGroup()) return;
                  if (!Objects.equals(activeTarget.serverId(), change.serverId())) return;
                  SwingUtilities.invokeLater(() -> refreshMonitorRows(change.serverId()));
                },
                err -> {
                  // Keep chat UI usable even if monitor updates fail.
                }));
  }

  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    String sid = Objects.toString(serverId, "").trim();
    String key = normalizeNickKey(nick);
    if (sid.isEmpty() || key.isEmpty()) return;

    Map<String, Boolean> byNick =
        privateMessageOnlineByServer.computeIfAbsent(sid, __ -> new HashMap<>());
    byNick.put(key, online);
    if (isActiveMonitorForServer(sid)) {
      refreshMonitorRows(sid);
    }
  }

  public void clearPrivateMessageOnlineStates(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    privateMessageOnlineByServer.remove(sid);
    if (isActiveMonitorForServer(sid)) {
      refreshMonitorRows(sid);
    }
  }

  public void refreshMonitorRows(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      monitorPanel.setRows(java.util.List.of());
      return;
    }

    java.util.List<String> nicks = monitorListService.listNicks(sid);
    Map<String, Boolean> onlineByNick = privateMessageOnlineByServer.get(sid);

    ArrayList<MonitorPanel.Row> rows = new ArrayList<>(nicks.size());
    for (String nick : nicks) {
      String normalizedNick = Objects.toString(nick, "").trim();
      if (normalizedNick.isEmpty()) continue;
      Boolean online =
          (onlineByNick == null) ? null : onlineByNick.get(normalizeNickKey(normalizedNick));
      rows.add(new MonitorPanel.Row(normalizedNick, online));
    }
    monitorPanel.setRows(rows);
  }

  private boolean isActiveMonitorForServer(String serverId) {
    TargetRef activeTarget = activeTargetSupplier.get();
    return activeTarget != null
        && activeTarget.isMonitorGroup()
        && Objects.equals(activeTarget.serverId(), serverId);
  }

  private static String normalizeNickKey(String nick) {
    String normalized = Objects.toString(nick, "").trim();
    if (normalized.isEmpty()) return "";
    return normalized.toLowerCase(Locale.ROOT);
  }
}
