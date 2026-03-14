package cafe.woden.ircclient.irc.mode;

import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;

/** Factory for normalized channel mode observations across IRC backends. */
public final class ChannelModeObservationFactory {
  private ChannelModeObservationFactory() {}

  public static IrcEvent.ChannelModeObserved fromLiveMode(
      Instant at, String channel, String by, String details) {
    return new IrcEvent.ChannelModeObserved(
        at,
        channel,
        by,
        details,
        ChannelModeObservationClassifier.classifyLiveModeKind(by, details),
        IrcEvent.ChannelModeProvenance.LIVE_MODE_EVENT);
  }

  public static IrcEvent.ChannelModeObserved fromNumeric324(
      Instant at, String channel, String details) {
    return new IrcEvent.ChannelModeObserved(
        at,
        channel,
        "",
        details,
        IrcEvent.ChannelModeKind.SNAPSHOT,
        IrcEvent.ChannelModeProvenance.NUMERIC_324);
  }

  public static IrcEvent.ChannelModeObserved fromNumeric324Fallback(
      Instant at, String channel, String details) {
    return new IrcEvent.ChannelModeObserved(
        at,
        channel,
        "",
        details,
        IrcEvent.ChannelModeKind.SNAPSHOT,
        IrcEvent.ChannelModeProvenance.NUMERIC_324_FALLBACK);
  }

  public static IrcEvent.ChannelModeObserved fromQuasselDisplayMessage(
      Instant at, String channel, String by, String details) {
    return new IrcEvent.ChannelModeObserved(
        at,
        channel,
        by,
        details,
        IrcEvent.ChannelModeKind.DELTA,
        IrcEvent.ChannelModeProvenance.QUASSEL_DISPLAY_MESSAGE);
  }
}
