package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Encapsulates display-label and ephemeral server badge policy. */
public final class ServerTreeServerLabelPolicy {

  private final Map<String, String> serverDisplayNames;
  private final Set<String> ephemeralServerIds;
  private final Map<String, String> sojuOriginByServerId;
  private final Map<String, String> zncOriginByServerId;
  private final SojuAutoConnectStore sojuAutoConnect;
  private final ZncAutoConnectStore zncAutoConnect;

  public ServerTreeServerLabelPolicy(
      Map<String, String> serverDisplayNames,
      Set<String> ephemeralServerIds,
      Map<String, String> sojuOriginByServerId,
      Map<String, String> zncOriginByServerId,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect) {
    this.serverDisplayNames = Objects.requireNonNull(serverDisplayNames, "serverDisplayNames");
    this.ephemeralServerIds = Objects.requireNonNull(ephemeralServerIds, "ephemeralServerIds");
    this.sojuOriginByServerId =
        Objects.requireNonNull(sojuOriginByServerId, "sojuOriginByServerId");
    this.zncOriginByServerId = Objects.requireNonNull(zncOriginByServerId, "zncOriginByServerId");
    this.sojuAutoConnect = sojuAutoConnect;
    this.zncAutoConnect = zncAutoConnect;
  }

  public String prettyServerLabel(String serverId) {
    String id = normalize(serverId);
    if (id.isEmpty()) return id;

    String display = serverDisplayNames.getOrDefault(id, id);
    if (isSojuEphemeralServer(id)) {
      String origin = sojuOriginByServerId.get(id);
      if (origin != null && sojuAutoConnect != null && sojuAutoConnect.isEnabled(origin, display)) {
        return display + " (auto)";
      }
      return display;
    }

    if (isZncEphemeralServer(id)) {
      String origin = zncOriginByServerId.get(id);
      if (origin != null && zncAutoConnect != null && zncAutoConnect.isEnabled(origin, display)) {
        return display + " (auto)";
      }
      return display;
    }

    return display;
  }

  public boolean isSojuEphemeralServer(String serverId) {
    String id = normalize(serverId);
    return !id.isEmpty() && id.startsWith("soju:") && ephemeralServerIds.contains(id);
  }

  public boolean isZncEphemeralServer(String serverId) {
    String id = normalize(serverId);
    return !id.isEmpty() && id.startsWith("znc:") && ephemeralServerIds.contains(id);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
