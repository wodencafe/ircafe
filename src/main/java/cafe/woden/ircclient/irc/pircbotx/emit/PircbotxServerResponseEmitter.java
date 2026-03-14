package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;

/**
 * Emits structured events from generic server numerics, channel list numerics, and ban-list
 * numerics.
 */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxServerResponseEmitter {
  @NonNull private final String serverId;
  @NonNull private final Consumer<ServerIrcEvent> emit;
  private final Set<String> activeBanListChannels = new HashSet<>();

  public void clear() {
    activeBanListChannels.clear();
  }

  public void emitServerResponseLine(PircBotX bot, int code, String line) {
    try {
      if (line == null || line.isBlank()) return;
      String originalLine = line.trim();
      Map<String, String> ircv3Tags = Ircv3Tags.fromRawLine(originalLine);
      String messageId = Ircv3Tags.firstTagValue(ircv3Tags, "msgid", "draft/msgid");
      String normalizedLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);
      ParsedIrcLine pl = PircbotxInboundLineParsers.parseIrcLine(normalizedLine);

      Instant at = Ircv3ServerTime.parseServerTimeFromRawLine(line);
      if (at == null) at = Instant.now();

      String myNick = null;
      try {
        myNick = (bot == null) ? null : bot.getNick();
      } catch (Exception ignored) {
      }

      emitChannelListEvent(at, pl, myNick);
      emitChannelBanListEvent(at, pl);

      String message = renderServerResponseMessage(pl, myNick);
      if (message == null || message.isBlank()) {
        message = normalizedLine;
      }
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ServerResponseLine(
                  at, code, message, originalLine, messageId, ircv3Tags)));
    } catch (Exception ignored) {
    }
  }

  public void emitChannelListEvent(Instant at, ParsedIrcLine pl, String myNick) {
    if (pl == null) return;

    String banner = PircbotxListParsers.parseListStartBanner(pl.command(), pl.trailing());
    if (banner != null) {
      emit.accept(new ServerIrcEvent(serverId, new IrcEvent.ChannelListStarted(at, banner)));
      return;
    }

    PircbotxListParsers.ListEntry entry =
        PircbotxListParsers.parseListEntry(pl.command(), pl.params(), pl.trailing(), myNick);
    if (entry != null) {
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ChannelListEntry(
                  at, entry.channel(), Math.max(0, entry.visibleUsers()), entry.topic())));
      return;
    }

    String summary = PircbotxListParsers.parseListEndSummary(pl.command(), pl.trailing());
    if (summary != null) {
      emit.accept(new ServerIrcEvent(serverId, new IrcEvent.ChannelListEnded(at, summary)));
    }
  }

  public boolean maybeEmitAlisChannelListEntry(Instant at, String fromNick, String noticeText) {
    PircbotxListParsers.ListEntry entry =
        PircbotxListParsers.parseAlisNoticeEntry(fromNick, noticeText);
    if (entry != null) {
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ChannelListEntry(
                  at, entry.channel(), Math.max(0, entry.visibleUsers()), entry.topic())));
      return true;
    }

    String summary = PircbotxListParsers.parseAlisNoticeEndSummary(fromNick, noticeText);
    if (summary == null) return false;
    emit.accept(new ServerIrcEvent(serverId, new IrcEvent.ChannelListEnded(at, summary)));
    return true;
  }

  public void emitChannelBanListEvent(Instant at, ParsedIrcLine pl) {
    if (pl == null) return;

    PircbotxListParsers.BanListEntry entry =
        PircbotxListParsers.parseBanListEntry(pl.command(), pl.params());
    if (entry != null) {
      String key = entry.channel().toLowerCase(Locale.ROOT);
      if (activeBanListChannels.add(key)) {
        emit.accept(
            new ServerIrcEvent(serverId, new IrcEvent.ChannelBanListStarted(at, entry.channel())));
      }
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ChannelBanListEntry(
                  at, entry.channel(), entry.mask(), entry.setBy(), entry.setAtEpochSeconds())));
      return;
    }

    String channel = PircbotxListParsers.parseBanListEndChannel(pl.command(), pl.params());
    if (channel == null || channel.isBlank()) return;
    String key = channel.toLowerCase(Locale.ROOT);
    if (!activeBanListChannels.remove(key)) {
      emit.accept(new ServerIrcEvent(serverId, new IrcEvent.ChannelBanListStarted(at, channel)));
    }
    String summary = PircbotxListParsers.parseBanListEndSummary(pl.command(), pl.trailing());
    emit.accept(
        new ServerIrcEvent(serverId, new IrcEvent.ChannelBanListEnded(at, channel, summary)));
  }

  private String renderServerResponseMessage(ParsedIrcLine pl, String myNick) {
    if (pl == null) return null;
    String listRendered =
        PircbotxListParsers.tryFormatListNumeric(pl.command(), pl.params(), pl.trailing(), myNick);
    if (listRendered != null && !listRendered.isBlank()) return listRendered;

    String trailing = pl.trailing();
    java.util.List<String> params = pl.params();
    String msg = (trailing == null) ? "" : trailing;

    int idx = 0;
    if (params != null && !params.isEmpty()) {
      if (myNick != null
          && !myNick.isBlank()
          && params.getFirst() != null
          && params.getFirst().equalsIgnoreCase(myNick)) {
        idx = 1;
      } else if ("*".equals(params.getFirst())) {
        idx = 1;
      }
    }

    String subject = "";
    if (params != null && idx < params.size()) {
      subject = params.get(idx);
      if (subject == null) subject = "";
    }

    if (msg.isBlank() && params != null && idx < params.size()) {
      msg = String.join(" ", params.subList(idx, params.size()));
    }
    msg = msg == null ? "" : msg.trim();
    subject = subject == null ? "" : subject.trim();

    if (!subject.isBlank() && !msg.isBlank() && !msg.contains(subject)) {
      return msg + " (" + subject + ")";
    }
    return msg.isBlank() ? subject : msg;
  }
}
