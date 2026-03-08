package cafe.woden.ircclient.ui.servertree.model;

import java.util.Locale;
import java.util.Objects;

/** Node metadata for Quassel per-network containers in the server tree. */
public final class ServerTreeQuasselNetworkNodeData {
  private final String serverId;
  private final String networkToken;
  private final String label;
  private final boolean emptyState;

  public static ServerTreeQuasselNetworkNodeData network(
      String serverId, String networkToken, String label) {
    return new ServerTreeQuasselNetworkNodeData(serverId, networkToken, label, false);
  }

  public static ServerTreeQuasselNetworkNodeData emptyState(String serverId, String label) {
    return new ServerTreeQuasselNetworkNodeData(serverId, "", label, true);
  }

  private ServerTreeQuasselNetworkNodeData(
      String serverId, String networkToken, String label, boolean emptyState) {
    this.serverId = normalizeServerId(serverId);
    this.networkToken = normalizeToken(networkToken);
    this.label = Objects.toString(label, "").trim();
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
