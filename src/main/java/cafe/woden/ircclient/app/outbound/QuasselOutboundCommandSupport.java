package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class QuasselOutboundCommandSupport {
  private final ServerCatalog serverCatalog;

  QuasselOutboundCommandSupport(ServerCatalog serverCatalog) {
    this.serverCatalog = serverCatalog;
  }

  boolean ensureQuasselServerBackend(String serverId, TargetRef out, String statusTag, UiPort ui) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      ui.appendStatus(out, statusTag, "Select a Quassel server first.");
      return false;
    }
    if (serverCatalog == null) {
      return true;
    }
    Optional<IrcProperties.Server> server = serverCatalog.find(sid);
    if (server.isEmpty()) {
      ui.appendStatus(out, statusTag, "Unknown server '" + sid + "'.");
      return false;
    }
    IrcProperties.Server.Backend backend = server.orElseThrow().backend();
    if (backend == IrcProperties.Server.Backend.QUASSEL_CORE) {
      return true;
    }
    ui.appendStatus(
        out,
        statusTag,
        "Server '"
            + sid
            + "' does not use the Quassel Core backend. Quassel actions are only available on Quassel Core servers.");
    return false;
  }

  String networkUsage() {
    return "Usage: /quasselnet [serverId] list | connect <network> | disconnect <network> | remove <network> | add <name> <host> [port] [tls|plain] | edit <network> <host> [port] [tls|plain]";
  }

  boolean isNetworkVerb(String token) {
    String normalized = Objects.toString(token, "").trim().toLowerCase(Locale.ROOT);
    return normalized.equals("list")
        || normalized.equals("ls")
        || normalized.equals("connect")
        || normalized.equals("disconnect")
        || normalized.equals("add")
        || normalized.equals("create")
        || normalized.equals("edit")
        || normalized.equals("update")
        || normalized.equals("set")
        || normalized.equals("remove")
        || normalized.equals("rm");
  }

  Integer tryParseInt(String value) {
    String token = Objects.toString(value, "").trim();
    if (token.isEmpty()) return null;
    try {
      return Integer.parseInt(token);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  Boolean parseTlsToken(String raw) {
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return null;
    return switch (token) {
      case "1", "true", "yes", "on", "tls", "ssl" -> Boolean.TRUE;
      case "0", "false", "no", "off", "plain", "notls" -> Boolean.FALSE;
      default -> null;
    };
  }

  String renderNetworkSummary(IrcClientService.QuasselCoreNetworkSummary network) {
    if (network == null) return "(unknown network)";
    String name = Objects.toString(network.networkName(), "").trim();
    if (name.isBlank()) name = "network-" + network.networkId();
    String host = Objects.toString(network.serverHost(), "").trim();
    int port = network.serverPort();

    StringBuilder line = new StringBuilder();
    line.append("[").append(network.networkId()).append("] ").append(name).append(" - ");
    line.append(network.connected() ? "connected" : "disconnected");
    if (!network.enabled()) line.append(", disabled");
    if (!host.isBlank() && port > 0) {
      line.append(" @ ").append(host).append(":").append(port);
      line.append(network.useTls() ? " tls" : " plain");
    }
    if (network.identityId() >= 0) {
      line.append(" identity=").append(network.identityId());
    }
    return line.toString();
  }
}
