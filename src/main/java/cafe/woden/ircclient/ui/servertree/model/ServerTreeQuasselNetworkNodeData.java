package cafe.woden.ircclient.ui.servertree.model;

import java.util.Locale;
import java.util.Objects;

/** Node metadata for Quassel per-network containers in the server tree. */
public final class ServerTreeQuasselNetworkNodeData {
  private final String serverId;
  private final String networkToken;
  private final String label;
  private final Boolean connected;
  private final Boolean enabled;
  private final boolean emptyState;

  public static ServerTreeQuasselNetworkNodeData network(
      String serverId, String networkToken, String label) {
    return network(serverId, networkToken, label, null, null);
  }

  public static ServerTreeQuasselNetworkNodeData network(
      String serverId, String networkToken, String label, Boolean connected, Boolean enabled) {
    return new ServerTreeQuasselNetworkNodeData(
        serverId, networkToken, label, connected, enabled, false);
  }

  public static ServerTreeQuasselNetworkNodeData emptyState(String serverId, String label) {
    return new ServerTreeQuasselNetworkNodeData(serverId, "", label, null, null, true);
  }

  private ServerTreeQuasselNetworkNodeData(
      String serverId,
      String networkToken,
      String label,
      Boolean connected,
      Boolean enabled,
      boolean emptyState) {
    this.serverId = normalizeServerId(serverId);
    this.networkToken = normalizeToken(networkToken);
    this.label = Objects.toString(label, "").trim();
    this.connected = connected;
    this.enabled = enabled;
    this.emptyState = emptyState;
  }

  public String serverId() {
    return serverId;
  }

  public String networkToken() {
    return networkToken;
  }

  public String label() {
    return label;
  }

  public Boolean connected() {
    return connected;
  }

  public Boolean enabled() {
    return enabled;
  }

  public boolean emptyState() {
    return emptyState;
  }

  @Override
  public String toString() {
    return label;
  }

  private static String normalizeServerId(String value) {
    return Objects.toString(value, "").trim();
  }

  private static String normalizeToken(String value) {
    return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
  }
}
