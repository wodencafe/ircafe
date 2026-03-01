package cafe.woden.ircclient.ui.servertree.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mutable runtime metadata shown in the server tree "Network Info" surfaces. */
public final class ServerRuntimeMetadata {
  public String connectedHost = "";
  public int connectedPort = 0;
  public String nick = "";
  public Instant connectedAt;

  public String serverName = "";
  public String serverVersion = "";
  public String userModes = "";
  public String channelModes = "";

  public final Map<String, CapabilityState> ircv3Caps = new LinkedHashMap<>();
  public final Map<String, String> ircv3CapLastSubcommand = new LinkedHashMap<>();
  public final List<CapabilityTransition> ircv3CapTransitions = new ArrayList<>();
  public final Map<String, String> isupport = new LinkedHashMap<>();

  public enum CapabilityState {
    AVAILABLE("available"),
    ENABLED("enabled"),
    DISABLED("disabled"),
    REMOVED("removed");

    public final String label;

    CapabilityState(String label) {
      this.label = label;
    }
  }

  public record CapabilityTransition(
      Instant at, String subcommand, String capability, CapabilityState state) {}
}
