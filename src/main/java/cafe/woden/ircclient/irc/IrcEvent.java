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
    IrcEvent.SoftChannelMessage,
    IrcEvent.SoftChannelAction,
    IrcEvent.ChannelModeChanged,
    IrcEvent.ChannelModesListed,
    IrcEvent.ChannelTopicUpdated,
    IrcEvent.PrivateMessage,
    IrcEvent.PrivateAction,
    IrcEvent.SoftPrivateMessage,
    IrcEvent.SoftPrivateAction,
    IrcEvent.Notice,
    IrcEvent.SoftNotice,
    IrcEvent.UserJoinedChannel,
    IrcEvent.UserPartedChannel,
    IrcEvent.UserQuitChannel,
    IrcEvent.UserNickChangedChannel,
    IrcEvent.JoinedChannel,
    IrcEvent.NickListUpdated,
    IrcEvent.WhoisResult,
    IrcEvent.Error {

  record Connected(Instant at, String serverHost, int serverPort, String nick) implements IrcEvent {}

  record Connecting(Instant at, String serverHost, int serverPort, String nick) implements IrcEvent {}
  record Disconnected(Instant at, String reason) implements IrcEvent {}

  record Reconnecting(Instant at, long attempt, long delayMs, String reason) implements IrcEvent {}

  record NickChanged(Instant at, String oldNick, String newNick) implements IrcEvent {}

  record ChannelMessage(Instant at, String channel, String from, String text) implements IrcEvent {}

  /** A CTCP ACTION (/me) sent to a channel. */
  record ChannelAction(Instant at, String channel, String from, String action) implements IrcEvent {}

  /** A channel message from a soft-ignored hostmask; UI should render as a spoiler. */
  record SoftChannelMessage(Instant at, String channel, String from, String text) implements IrcEvent {}

  /** A CTCP ACTION (/me) sent to a channel from a soft-ignored hostmask; UI should render as a spoiler. */
  record SoftChannelAction(Instant at, String channel, String from, String action) implements IrcEvent {}

  /** A channel MODE change (e.g. +o nick, +m). */
  record ChannelModeChanged(Instant at, String channel, String by, String details) implements IrcEvent {}
  record ChannelModesListed(Instant at, String channel, String details) implements IrcEvent {}

  /**
   * Channel topic update.
   *
   * <p>Emitted when we learn a channel's topic (including on join) or when it changes.
   */
  record ChannelTopicUpdated(Instant at, String channel, String topic) implements IrcEvent {}

  record PrivateMessage(Instant at, String from, String text) implements IrcEvent {}
  /** A CTCP ACTION (/me) sent as a private message. */
  record PrivateAction(Instant at, String from, String action) implements IrcEvent {}

  /** A private message from a soft-ignored hostmask; UI should render as a spoiler. */
  record SoftPrivateMessage(Instant at, String from, String text) implements IrcEvent {}

  /** A CTCP ACTION (/me) sent as a private message from a soft-ignored hostmask; UI should render as a spoiler. */
  record SoftPrivateAction(Instant at, String from, String action) implements IrcEvent {}
  record Notice(Instant at, String from, String text) implements IrcEvent {}
  /** A NOTICE from a soft-ignored hostmask; UI should render as a spoiler. */
  record SoftNotice(Instant at, String from, String text) implements IrcEvent {}

  /** Another user joined a channel we're in. */
  record UserJoinedChannel(Instant at, String channel, String nick) implements IrcEvent {}

  /** Another user left (PART) a channel we're in. */
  record UserPartedChannel(Instant at, String channel, String nick, String reason) implements IrcEvent {}

  /** A user QUIT and we emit one event per channel where they were present. */
  record UserQuitChannel(Instant at, String channel, String nick, String reason) implements IrcEvent {}

  /** A user changed nick and we emit one event per channel where they were present. */
  record UserNickChangedChannel(Instant at, String channel, String oldNick, String newNick) implements IrcEvent {}

  record JoinedChannel(Instant at, String channel) implements IrcEvent {}
  record Error(Instant at, String message, Throwable cause) implements IrcEvent {}

  record NickInfo(String nick, String prefix) {} // prefix like "@", "+", "~", etc.

  record NickListUpdated(
      Instant at,
      String channel,
      List<NickInfo> nicks,
      int totalUsers,
      int operatorCount
  ) implements IrcEvent {}

  /** Completed WHOIS response */
  record WhoisResult(Instant at, String nick, List<String> lines) implements IrcEvent {}
}
