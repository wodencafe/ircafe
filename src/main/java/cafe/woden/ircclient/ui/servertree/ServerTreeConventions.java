package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.model.TargetRef;
import java.util.Locale;
import java.util.Objects;

/** Shared normalization and target-shape conventions for server-tree collaborators. */
public final class ServerTreeConventions {

  private ServerTreeConventions() {}

  public static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  public static String normalizeServerId(String serverId) {
    return normalize(serverId);
  }

  public static boolean isChannelTarget(TargetRef ref) {
    return ref != null && ref.isChannel();
  }

  public static String foldChannelKey(String channel) {
    return normalize(channel).toLowerCase(Locale.ROOT);
  }
}
