package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.backend.OutboundBackendCapabilityPolicy;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.Application;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles {@code /monitor} command family. */
@Component
@Application
@ApplicationLayer
public class OutboundMonitorCommandService {
  private static final int DEFAULT_MONITOR_CHUNK = 100;

  private final MonitorRosterPort monitorRosterPort;
  private final OutboundMonitorCommandSupport monitorCommandSupport;

  public OutboundMonitorCommandService(
      IrcClientService irc,
      UiPort ui,
      TargetCoordinator targetCoordinator,
      ConnectionCoordinator connectionCoordinator,
      MonitorRosterPort monitorRosterPort,
      MonitorFallbackPort monitorFallbackPort,
      OutboundBackendCapabilityPolicy backendCapabilityPolicy) {
    this.monitorRosterPort = Objects.requireNonNull(monitorRosterPort, "monitorRosterPort");
    this.monitorCommandSupport =
        new OutboundMonitorCommandSupport(
            Objects.requireNonNull(irc, "irc"),
            Objects.requireNonNull(ui, "ui"),
            Objects.requireNonNull(targetCoordinator, "targetCoordinator"),
            Objects.requireNonNull(connectionCoordinator, "connectionCoordinator"),
            Objects.requireNonNull(monitorFallbackPort, "monitorFallbackPort"),
            Objects.requireNonNull(backendCapabilityPolicy, "backendCapabilityPolicy"));
  }

  public void handleMonitor(CompositeDisposable disposables, String args) {
    MonitorCommandContext context = monitorCommandSupport.resolveContextOrNull();
    if (context == null) return;

    String raw = Objects.toString(args, "").trim();
    if (raw.isEmpty()) {
      appendUsage(context.monitorTarget());
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
      handleSigned(disposables, context, sigil, nickSpec);
      return;
    }

    String op = opRaw.toLowerCase(Locale.ROOT);
    switch (op) {
      case "list", "l" -> handleList(disposables, context);
      case "status", "s" -> {
        if (monitorCommandSupport.isFallbackActive(context.serverId())) {
          monitorCommandSupport.requestFallbackRefresh(
              context.serverId(), context.statusTarget(), true);
        } else {
          monitorCommandSupport.sendMonitorRaw(disposables, context, "MONITOR S", true);
        }
      }
      case "clear", "c" -> handleClear(disposables, context);
      case "help" -> appendUsage(context.monitorTarget());
      default -> {
        // Shorthand: treat "/monitor nick1 nick2" as add/update.
        String nickSpec = raw;
        handleSigned(disposables, context, '+', nickSpec);
      }
    }
  }

  private void handleList(CompositeDisposable disposables, MonitorCommandContext context) {
    List<String> local = monitorRosterPort.listNicks(context.serverId());
    if (local.isEmpty()) {
      monitorCommandSupport.appendStatus(context.monitorTarget(), "Monitored nicks: (none)");
    } else {
      monitorCommandSupport.appendStatus(
          context.monitorTarget(),
          "Monitored nicks (" + local.size() + "): " + String.join(", ", local));
    }
    if (monitorCommandSupport.isFallbackActive(context.serverId())) {
      monitorCommandSupport.requestFallbackRefresh(
          context.serverId(), context.statusTarget(), true);
      return;
    }
    monitorCommandSupport.sendMonitorRaw(disposables, context, "MONITOR L", true);
  }

  private void handleClear(CompositeDisposable disposables, MonitorCommandContext context) {
    int removed = monitorRosterPort.clearNicks(context.serverId());
    monitorCommandSupport.appendStatus(
        context.monitorTarget(),
        removed <= 0
            ? "Cleared monitor list (already empty)."
            : ("Cleared monitor list (" + removed + " removed)."));
    if (monitorCommandSupport.isFallbackActive(context.serverId())) {
      monitorCommandSupport.requestFallbackRefresh(
          context.serverId(), context.statusTarget(), false);
      return;
    }
    monitorCommandSupport.sendMonitorRaw(disposables, context, "MONITOR C", false);
  }

  private void handleSigned(
      CompositeDisposable disposables, MonitorCommandContext context, char sigil, String nickSpec) {
    List<String> nicks = monitorRosterPort.parseNickInput(nickSpec);
    if (nicks.isEmpty()) {
      appendUsage(context.monitorTarget());
      return;
    }

    int changed =
        sigil == '+'
            ? monitorRosterPort.addNicks(context.serverId(), nicks)
            : monitorRosterPort.removeNicks(context.serverId(), nicks);
    if (sigil == '+') {
      monitorCommandSupport.appendStatus(
          context.monitorTarget(),
          changed <= 0
              ? "No monitor nicks added."
              : ("Added "
                  + changed
                  + " monitor nick"
                  + (changed == 1 ? "" : "s")
                  + ": "
                  + String.join(", ", nicks)));
    } else {
      monitorCommandSupport.appendStatus(
          context.monitorTarget(),
          changed <= 0
              ? "No monitor nicks removed."
              : ("Removed "
                  + changed
                  + " monitor nick"
                  + (changed == 1 ? "" : "s")
                  + ": "
                  + String.join(", ", nicks)));
    }

    int chunkSize =
        monitorCommandSupport.negotiatedChunkSize(context.serverId(), DEFAULT_MONITOR_CHUNK);
    if (monitorCommandSupport.isFallbackActive(context.serverId())) {
      monitorCommandSupport.requestFallbackRefresh(
          context.serverId(), context.statusTarget(), false);
      return;
    }
    for (int i = 0; i < nicks.size(); i += chunkSize) {
      int end = Math.min(i + chunkSize, nicks.size());
      List<String> chunk = nicks.subList(i, end);
      if (chunk.isEmpty()) continue;
      String line = "MONITOR " + sigil + String.join(",", chunk);
      monitorCommandSupport.sendMonitorRaw(disposables, context, line, false);
    }
  }

  private void appendUsage(TargetRef out) {
    monitorCommandSupport.appendStatus(out, "Usage: /monitor <+|-|list|status|clear> [nicks]");
    monitorCommandSupport.appendStatus(
        out, "Aliases: /mon, /monitor +nick1 nick2, /monitor -nick1,nick2");
    monitorCommandSupport.appendStatus(
        out, "Examples: /monitor +alice,bob  |  /monitor list  |  /monitor clear");
  }

  private static char extractSigil(String op) {
    String s = Objects.toString(op, "").trim();
    if (s.isEmpty()) return 0;
    char c = s.charAt(0);
    return (c == '+' || c == '-') ? c : 0;
  }
}
