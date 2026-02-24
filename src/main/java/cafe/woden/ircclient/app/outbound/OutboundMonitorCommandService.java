package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.ConnectionCoordinator;
import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.monitor.MonitorIsonFallbackService;
import cafe.woden.ircclient.app.monitor.MonitorListService;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Handles {@code /monitor} command family. */
@Component
public class OutboundMonitorCommandService {
  private static final int DEFAULT_MONITOR_CHUNK = 100;

  private final IrcClientService irc;
  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final ConnectionCoordinator connectionCoordinator;
  private final MonitorListService monitorListService;
  private final MonitorIsonFallbackService monitorIsonFallbackService;

  public OutboundMonitorCommandService(
      IrcClientService irc,
      UiPort ui,
      TargetCoordinator targetCoordinator,
      ConnectionCoordinator connectionCoordinator,
      MonitorListService monitorListService,
      MonitorIsonFallbackService monitorIsonFallbackService) {
    this.irc = irc;
    this.ui = ui;
    this.targetCoordinator = targetCoordinator;
    this.connectionCoordinator = connectionCoordinator;
    this.monitorListService = monitorListService;
    this.monitorIsonFallbackService = monitorIsonFallbackService;
  }

  public void handleMonitor(CompositeDisposable disposables, String args) {
    TargetRef active = targetCoordinator.getActiveTarget();
    TargetRef fallback = (active != null) ? active : targetCoordinator.safeStatusTarget();
    String sid = Objects.toString(fallback.serverId(), "").trim();
    if (sid.isEmpty()) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(monitor)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    TargetRef monitorTarget = TargetRef.monitorGroup(sid);
    ui.ensureTargetExists(status);
    ui.ensureTargetExists(monitorTarget);

    String raw = Objects.toString(args, "").trim();
    if (raw.isEmpty()) {
      appendUsage(monitorTarget);
      return;
    }

    String[] headTail = raw.split("\\s+", 2);
    String opRaw = headTail[0].trim();
    String rest = headTail.length > 1 ? headTail[1].trim() : "";

    char sigil = extractSigil(opRaw);
    if (sigil == '+' || sigil == '-') {
      String nickSpec = opRaw.length() > 1 ? opRaw.substring(1) : "";
      if (!rest.isEmpty()) {
        nickSpec = nickSpec.isEmpty() ? rest : (nickSpec + " " + rest);
      }
      handleSigned(disposables, sid, monitorTarget, status, sigil, nickSpec);
      return;
    }

    String op = opRaw.toLowerCase(Locale.ROOT);
    switch (op) {
      case "list", "l" -> handleList(disposables, sid, monitorTarget, status);
      case "status", "s" -> {
        if (monitorIsonFallbackService.isFallbackActive(sid)) {
          requestFallbackRefresh(sid, status, true);
        } else {
          sendMonitorRaw(disposables, sid, status, monitorTarget, "MONITOR S", true);
        }
      }
      case "clear", "c" -> handleClear(disposables, sid, monitorTarget, status);
      case "help" -> appendUsage(monitorTarget);
      default -> {
        // Shorthand: treat "/monitor nick1 nick2" as add/update.
        String nickSpec = raw;
        handleSigned(disposables, sid, monitorTarget, status, '+', nickSpec);
      }
    }
  }

  private void handleList(
      CompositeDisposable disposables, String serverId, TargetRef monitorTarget, TargetRef status) {
    List<String> local = monitorListService.listNicks(serverId);
    if (local.isEmpty()) {
      ui.appendStatus(monitorTarget, "(monitor)", "Monitored nicks: (none)");
    } else {
      ui.appendStatus(
          monitorTarget,
          "(monitor)",
          "Monitored nicks (" + local.size() + "): " + String.join(", ", local));
    }
    if (monitorIsonFallbackService.isFallbackActive(serverId)) {
      requestFallbackRefresh(serverId, status, true);
      return;
    }
    sendMonitorRaw(disposables, serverId, status, monitorTarget, "MONITOR L", true);
  }

  private void handleClear(
      CompositeDisposable disposables, String serverId, TargetRef monitorTarget, TargetRef status) {
    int removed = monitorListService.clearNicks(serverId);
    ui.appendStatus(
        monitorTarget,
        "(monitor)",
        removed <= 0
            ? "Cleared monitor list (already empty)."
            : ("Cleared monitor list (" + removed + " removed)."));
    if (monitorIsonFallbackService.isFallbackActive(serverId)) {
      requestFallbackRefresh(serverId, status, false);
      return;
    }
    sendMonitorRaw(disposables, serverId, status, monitorTarget, "MONITOR C", false);
  }

  private void handleSigned(
      CompositeDisposable disposables,
      String serverId,
      TargetRef monitorTarget,
      TargetRef status,
      char sigil,
      String nickSpec) {
    List<String> nicks = MonitorListService.tokenizeNickInput(nickSpec);
    if (nicks.isEmpty()) {
      appendUsage(monitorTarget);
      return;
    }

    int changed =
        sigil == '+'
            ? monitorListService.addNicks(serverId, nicks)
            : monitorListService.removeNicks(serverId, nicks);
    if (sigil == '+') {
      ui.appendStatus(
          monitorTarget,
          "(monitor)",
          changed <= 0
              ? "No monitor nicks added."
              : ("Added "
                  + changed
                  + " monitor nick"
                  + (changed == 1 ? "" : "s")
                  + ": "
                  + String.join(", ", nicks)));
    } else {
      ui.appendStatus(
          monitorTarget,
          "(monitor)",
          changed <= 0
              ? "No monitor nicks removed."
              : ("Removed "
                  + changed
                  + " monitor nick"
                  + (changed == 1 ? "" : "s")
                  + ": "
                  + String.join(", ", nicks)));
    }

    int chunkSize = irc.negotiatedMonitorLimit(serverId);
    if (chunkSize <= 0) chunkSize = DEFAULT_MONITOR_CHUNK;
    if (monitorIsonFallbackService.isFallbackActive(serverId)) {
      requestFallbackRefresh(serverId, status, false);
      return;
    }
    for (int i = 0; i < nicks.size(); i += chunkSize) {
      int end = Math.min(i + chunkSize, nicks.size());
      List<String> chunk = nicks.subList(i, end);
      if (chunk.isEmpty()) continue;
      String line = "MONITOR " + sigil + String.join(",", chunk);
      sendMonitorRaw(disposables, serverId, status, monitorTarget, line, false);
    }
  }

  private void sendMonitorRaw(
      CompositeDisposable disposables,
      String serverId,
      TargetRef status,
      TargetRef monitorTarget,
      String line,
      boolean requireConnection) {
    String sid = Objects.toString(serverId, "").trim();
    String rawLine = Objects.toString(line, "").trim();
    if (sid.isEmpty() || rawLine.isEmpty()) return;
    if (containsCrlf(rawLine)) {
      ui.appendStatus(status, "(monitor)", "Refusing to send multi-line /monitor input.");
      return;
    }

    if (!connectionCoordinator.isConnected(sid)) {
      if (requireConnection) {
        ui.appendStatus(status, "(conn)", "Not connected");
      }
      return;
    }

    ui.appendStatus(monitorTarget, "(monitor)", "→ " + rawLine);
    disposables.add(
        irc.sendRaw(sid, rawLine)
            .subscribe(
                () -> {}, err -> ui.appendError(status, "(monitor-error)", String.valueOf(err))));
  }

  private void requestFallbackRefresh(
      String serverId, TargetRef status, boolean requireConnection) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    if (!connectionCoordinator.isConnected(sid)) {
      if (requireConnection) {
        ui.appendStatus(status, "(conn)", "Not connected");
      }
      return;
    }
    monitorIsonFallbackService.requestImmediateRefresh(sid);
  }

  private void appendUsage(TargetRef out) {
    ui.appendStatus(out, "(monitor)", "Usage: /monitor <+|-|list|status|clear> [nicks]");
    ui.appendStatus(
        out, "(monitor)", "Aliases: /mon, /monitor +nick1 nick2, /monitor -nick1,nick2");
    ui.appendStatus(
        out, "(monitor)", "Examples: /monitor +alice,bob  |  /monitor list  |  /monitor clear");
  }

  private static char extractSigil(String op) {
    String s = Objects.toString(op, "").trim();
    if (s.isEmpty()) return 0;
    char c = s.charAt(0);
    return (c == '+' || c == '-') ? c : 0;
  }

  private static boolean containsCrlf(String s) {
    if (s == null || s.isEmpty()) return false;
    return s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
  }
}
