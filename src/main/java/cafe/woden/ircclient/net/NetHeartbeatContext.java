package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;

/**
 * Holds the current heartbeat (idle timeout) settings in a globally accessible place.
 *
 * <p>This is used by connection timers to detect silent disconnects (no inbound traffic for a
 * configured duration).
 */
public final class NetHeartbeatContext {

  private static final IrcProperties.Heartbeat DEFAULT =
      new IrcProperties.Heartbeat(true, 15_000, 360_000);

  private static volatile IrcProperties.Heartbeat settings = DEFAULT;

  private NetHeartbeatContext() {}

  public static void configure(IrcProperties.Heartbeat cfg) {
    settings = normalize(cfg);
  }

  public static IrcProperties.Heartbeat normalize(IrcProperties.Heartbeat cfg) {
    return (cfg == null) ? DEFAULT : cfg;
  }

  /** Current heartbeat settings (never null). */
  public static IrcProperties.Heartbeat settings() {
    return settings;
  }
}
