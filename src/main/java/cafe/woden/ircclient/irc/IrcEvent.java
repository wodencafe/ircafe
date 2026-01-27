package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.List;

public sealed interface IrcEvent permits
    IrcEvent.Connected,
    IrcEvent.Disconnected,
    IrcEvent.Reconnecting,
    IrcEvent.NickChanged,
    IrcEvent.ChannelMessage,
    IrcEvent.PrivateMessage,
    IrcEvent.Notice,
    IrcEvent.JoinedChannel,
    IrcEvent.NickListUpdated,
    IrcEvent.Error {

  record Connected(Instant at, String serverHost, int serverPort, String nick) implements IrcEvent {}
  record Disconnected(Instant at, String reason) implements IrcEvent {}

  record Reconnecting(Instant at, long attempt, long delayMs, String reason) implements IrcEvent {}

  record NickChanged(Instant at, String oldNick, String newNick) implements IrcEvent {}

  record ChannelMessage(Instant at, String channel, String from, String text) implements IrcEvent {}

  record PrivateMessage(Instant at, String from, String text) implements IrcEvent {}
  record Notice(Instant at, String from, String text) implements IrcEvent {}
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
}
