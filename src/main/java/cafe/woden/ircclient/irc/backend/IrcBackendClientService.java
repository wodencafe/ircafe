package cafe.woden.ircclient.irc.backend;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;

/** Backend-specific IRC transport adapter contract (IRC, Quassel Core, etc). */
public interface IrcBackendClientService
    extends IrcClientService,
        IrcBackendAvailabilityPort,
        QuasselCoreControlPort,
        IrcBouncerPlaybackPort {

  /** Backend kind implemented by this service. */
  IrcProperties.Server.Backend backend();

  /** Re-schedule heartbeats for active connections (best effort). */
  default void rescheduleActiveHeartbeats() {}
}
