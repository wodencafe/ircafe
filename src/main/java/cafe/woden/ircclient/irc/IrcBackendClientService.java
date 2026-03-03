package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;

/** Backend-specific IRC transport adapter contract (IRC, Quassel Core, etc). */
public interface IrcBackendClientService extends IrcClientService {

  /** Backend kind implemented by this service. */
  IrcProperties.Server.Backend backend();

  /** Re-schedule heartbeats for active connections (best effort). */
  default void rescheduleActiveHeartbeats() {}
}
