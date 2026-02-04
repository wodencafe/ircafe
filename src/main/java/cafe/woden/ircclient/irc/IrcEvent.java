package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.List;

public sealed interface IrcEvent permits
    IrcEvent.Connected,
    IrcEvent.Connecting,
    IrcEvent.Disconnected,
    IrcEvent.Reconnecting,
    IrcEvent.NickChanged,
    IrcEvent.ChannelMessage,
    IrcEvent.ChannelAction,

    IrcEvent.ChannelModeChanged,
    IrcEvent.ChannelModesListed,
    IrcEvent.ChannelTopicUpdated,
    IrcEvent.PrivateMessage,
    IrcEvent.PrivateAction,

    IrcEvent.Notice,

    IrcEvent.AwayStatusChanged,
    IrcEvent.UserJoinedChannel,
    IrcEvent.UserPartedChannel,
    IrcEvent.UserQuitChannel,
    IrcEvent.UserNickChangedChannel,
    IrcEvent.JoinedChannel,
    IrcEvent.NickListUpdated,
    IrcEvent.UserHostmaskObserved,
    IrcEvent.UserAwayStateObserved,
    IrcEvent.WhoisResult,
    IrcEvent.Error,
    IrcEvent.CtcpRequestReceived
 {

  /**
   * Best-effort away status for a user.
   *
   * <p>Most networks do not provide away state in NAMES. This is intended to be updated later via
   * IRCv3 {@code away-notify} (or similar mechanisms). Until then, entries will generally be
   * {@link #UNKNOWN}.
   */
  enum AwayState {
    UNKNOWN,
    HERE,
    AWAY
  }

  record Connected(Instant at, String serverHost, int serverPort, String nick) implements IrcEvent {}

  record Connecting(Instant at, String serverHost, int serverPort, String nick) implements IrcEvent {}
  record Disconnected(Instant at, String reason) implements IrcEvent {}

  record Reconnecting(Instant at, long attempt, long delayMs, String reason) implements IrcEvent {}

  record NickChanged(Instant at, String oldNick, String newNick) implements IrcEvent {}

  record ChannelMessage(Instant at, String channel, String from, String text) implements IrcEvent {}

  record ChannelAction(Instant at, String channel, String from, String action) implements IrcEvent {}

  record ChannelModeChanged(Instant at, String channel, String by, String details) implements IrcEvent {}
  record ChannelModesListed(Instant at, String channel, String details) implements IrcEvent {}

  record ChannelTopicUpdated(Instant at, String channel, String topic) implements IrcEvent {}

  record PrivateMessage(Instant at, String from, String text) implements IrcEvent {}
  record PrivateAction(Instant at, String from, String action) implements IrcEvent {}

  record Notice(Instant at, String from, String text) implements IrcEvent {}
  record CtcpRequestReceived(Instant at, String from, String command, String argument, String channel) implements IrcEvent {}

  record AwayStatusChanged(Instant at, boolean away, String message) implements IrcEvent {}

  
  record UserJoinedChannel(Instant at, String channel, String nick) implements IrcEvent {}

  
  record UserPartedChannel(Instant at, String channel, String nick, String reason) implements IrcEvent {}

  
  record UserQuitChannel(Instant at, String channel, String nick, String reason) implements IrcEvent {}

  record UserNickChangedChannel(Instant at, String channel, String oldNick, String newNick) implements IrcEvent {}

  record JoinedChannel(Instant at, String channel) implements IrcEvent {}
  record Error(Instant at, String message, Throwable cause) implements IrcEvent {}

  record NickInfo(String nick, String prefix, String hostmask, AwayState awayState, String awayMessage) { // prefix like "@", "+", "~", etc.
    public NickInfo {
      // Normalize null to UNKNOWN to keep downstream UI / stores simple.
      if (awayState == null) awayState = AwayState.UNKNOWN;
      // Keep empty/blank away messages as null (we use this for tooltip display).
      if (awayMessage != null && awayMessage.isBlank()) awayMessage = null;
    }

    
    public NickInfo(String nick, String prefix, String hostmask, AwayState awayState) {
      this(nick, prefix, hostmask, awayState, null);
    }

    
    public NickInfo(String nick, String prefix, String hostmask) {
      this(nick, prefix, hostmask, AwayState.UNKNOWN, null);
    }
  }

  record NickListUpdated(
      Instant at,
      String channel,
      List<NickInfo> nicks,
      int totalUsers,
      int operatorCount
  ) implements IrcEvent {}

  /** Opportunistically observed a user's hostmask in the wild (e.g. */
record UserHostmaskObserved(Instant at, String channel, String nick, String hostmask) implements IrcEvent {}

  /** Opportunistically observed a user's away state (e.g. */
record UserAwayStateObserved(Instant at, String nick, AwayState awayState, String awayMessage) implements IrcEvent {
    public UserAwayStateObserved {
      if (awayState == null) awayState = AwayState.UNKNOWN;
      if (awayMessage != null && awayMessage.isBlank()) awayMessage = null;
      // If the user is not away, we don't keep a reason.
      if (awayState != AwayState.AWAY) awayMessage = null;
    }

    
    public UserAwayStateObserved(Instant at, String nick, AwayState awayState) {
      this(at, nick, awayState, null);
    }
  }

  record WhoisResult(Instant at, String nick, List<String> lines) implements IrcEvent {}
}
