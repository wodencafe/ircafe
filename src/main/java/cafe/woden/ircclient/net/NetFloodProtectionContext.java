package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Current flood settings, snapshotted by each new native IRC connection. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NetFloodProtectionContext {
  private static volatile IrcProperties.FloodProtection settings =
      IrcProperties.FloodProtection.defaults();

  public static void configure(IrcProperties.FloodProtection value) {
    settings = value == null ? IrcProperties.FloodProtection.defaults() : value;
  }

  public static IrcProperties.FloodProtection settings() {
    return settings;
  }
}
