package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.hooks.events.InviteEvent;

/** Emits structured invite events for a single IRC connection. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PircbotxInviteEventEmitter {
  @NonNull private final String serverId;
  @NonNull private final PircbotxRosterEmitter rosterEmitter;
  @NonNull private final Consumer<ServerIrcEvent> emit;

  void onInvite(InviteEvent event) {
    if (event == null) return;

    String channel = resolveChannel(event);
    if (channel.isBlank()) return;

    String from = "server";
    String invitee = "";
    String reason = "";
    try {
      if (event.getUser() != null) {
        rosterEmitter.maybeEmitHostmaskObserved(channel, event.getUser());
        String nick = event.getUser().getNick();
        if (nick != null && !nick.isBlank()) from = nick.trim();
      }
    } catch (Exception ignored) {
    }

    try {
      String raw = PircbotxEventAccessors.rawLineFromEvent(event);
      ParsedIrcLine parsed =
          PircbotxInboundLineParsers.parseIrcLine(
              PircbotxLineParseUtil.normalizeIrcLineForParsing(raw));
      ParsedInviteLine parsedInvite = PircbotxInboundLineParsers.parseInviteLine(parsed);
      if (parsedInvite != null) {
        if (from.isBlank()) from = Objects.toString(parsedInvite.fromNick(), "").trim();
        if (!Objects.toString(parsedInvite.channel(), "").isBlank()) {
          channel = parsedInvite.channel().trim();
        }
        invitee = Objects.toString(parsedInvite.inviteeNick(), "").trim();
        reason = Objects.toString(parsedInvite.reason(), "").trim();
      }
    } catch (Exception ignored) {
    }

    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.InvitedToChannel(Instant.now(), channel, from, invitee, reason, false)));
  }

  private static String resolveChannel(InviteEvent event) {
    String channel = "";
    try {
      Object directChannel = PircbotxEventAccessors.reflectCall(event, "getChannel");
      if (directChannel != null) channel = String.valueOf(directChannel);
    } catch (Exception ignored) {
    }
    if (channel == null || channel.isBlank()) {
      try {
        Object channelName = PircbotxEventAccessors.reflectCall(event, "getChannelName");
        if (channelName != null) channel = String.valueOf(channelName);
      } catch (Exception ignored) {
      }
    }
    return channel == null ? "" : channel.trim();
  }
}
