package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.KickEvent;
import org.pircbotx.hooks.events.NickChangeEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.QuitEvent;
import org.pircbotx.snapshot.ChannelSnapshot;
import org.pircbotx.snapshot.UserChannelDaoSnapshot;
import org.pircbotx.snapshot.UserSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emits membership and nick-change events for a single IRC connection. */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxMembershipEventEmitter {
  private static final Logger log = LoggerFactory.getLogger(PircbotxMembershipEventEmitter.class);

  @NonNull private final String serverId;
  @NonNull private final PircbotxConnectionState conn;
  @NonNull private final PircbotxRosterEmitter rosterEmitter;
  @NonNull private final Consumer<ServerIrcEvent> emit;
  @NonNull private final BiPredicate<PircBotX, String> isSelfNick;
  @NonNull private final Consumer<String> rememberSelfNickHint;
  @NonNull private final Function<PircBotX, String> resolveBotNick;

  public void onJoin(JoinEvent event) {
    Channel channel = event.getChannel();
    if (channel != null)
      rosterEmitter.maybeEmitHostmaskObserved(channel.getName(), event.getUser());

    String nick = event.getUser() == null ? null : event.getUser().getNick();
    boolean selfJoin = isSelfNick.test(event.getBot(), nick);
    if (log.isDebugEnabled()) {
      String hint = Objects.toString(conn.selfNickHint(), "");
      String botNick = resolveBotNick.apply(event.getBot());
      String channelName = (channel == null) ? "" : channel.getName();
      log.debug(
          "[{}] JOIN route chan={} nick={} selfJoin={} selfHint={} botNick={}",
          serverId,
          channelName,
          nick,
          selfJoin,
          hint,
          botNick);
    }

    if (selfJoin) {
      emit.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.JoinedChannel(Instant.now(), channel.getName())));
    } else {
      emit.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.UserJoinedChannel(Instant.now(), channel.getName(), nick)));
    }

    rosterEmitter.emitRoster(channel);
  }

  public void onPart(PartEvent event) {
    boolean selfPart = false;
    try {
      rosterEmitter.maybeEmitHostmaskObserved(event.getChannelName(), event.getUser());
    } catch (Exception ignored) {
    }
    try {
      String nick = event.getUser() == null ? null : event.getUser().getNick();
      if (isSelfNick.test(event.getBot(), nick)) {
        selfPart = true;
        emit.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.LeftChannel(
                    Instant.now(), event.getChannelName(), event.getReason())));
      } else {
        emit.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.UserPartedChannel(
                    Instant.now(), event.getChannelName(), nick, event.getReason())));
      }
    } catch (Exception ignored) {
    }
    if (!selfPart) {
      rosterEmitter.refreshRosterByName(event.getBot(), event.getChannelName());
    }
  }

  public void onQuit(QuitEvent event) {
    PircBotX bot = event.getBot();

    try {
      rosterEmitter.maybeEmitHostmaskObserved("", event.getUser());
    } catch (Exception ignored) {
    }

    String nick = null;
    try {
      nick = event.getUser() == null ? null : event.getUser().getNick();
    } catch (Exception ignored) {
    }
    String reason = null;
    try {
      reason = event.getReason();
    } catch (Exception ignored) {
    }

    boolean refreshedSome = false;
    try {
      UserChannelDaoSnapshot daoSnap = event.getUserChannelDaoSnapshot();
      UserSnapshot userSnap = event.getUser();

      if (daoSnap != null && userSnap != null) {
        for (ChannelSnapshot channelSnapshot : daoSnap.getChannels(userSnap)) {
          try {
            emit.accept(
                new ServerIrcEvent(
                    serverId,
                    new IrcEvent.UserQuitChannel(
                        Instant.now(), channelSnapshot.getName(), nick, reason)));
          } catch (Exception ignored) {
          }
          rosterEmitter.refreshRosterByName(bot, channelSnapshot.getName());
          refreshedSome = true;
        }
      }
    } catch (Exception ignored) {
    }

    if (!refreshedSome) {
      try {
        try {
          if (event.getUser() != null) {
            for (Channel channel : bot.getUserChannelDao().getChannels(event.getUser())) {
              emit.accept(
                  new ServerIrcEvent(
                      serverId,
                      new IrcEvent.UserQuitChannel(
                          Instant.now(), channel.getName(), nick, reason)));
            }
          }
        } catch (Exception ignored) {
        }

        for (Channel channel : bot.getUserChannelDao().getAllChannels()) {
          rosterEmitter.emitRoster(channel);
        }
      } catch (Exception ignored) {
      }
    }
  }

  public void onKick(KickEvent event) {
    if (event == null) return;

    Instant at = Instant.now();
    String channel = "";
    Channel ch = null;
    try {
      ch = event.getChannel();
    } catch (Exception ignored) {
    }
    if (ch != null) {
      channel = Objects.toString(ch.getName(), "").trim();
    } else {
      Object nameObj = reflectCall(event, "getChannelName");
      if (nameObj != null) channel = String.valueOf(nameObj).trim();
    }

    String by = "server";
    try {
      if (event.getUser() != null) {
        rosterEmitter.maybeEmitHostmaskObserved(channel, event.getUser());
        String nick = event.getUser().getNick();
        if (nick != null && !nick.isBlank()) by = nick.trim();
      }
    } catch (Exception ignored) {
    }

    String kickedNick = "";
    Object recipient = reflectCall(event, "getRecipient");
    if (recipient != null) {
      Object nick = reflectCall(recipient, "getNick");
      if (nick != null) kickedNick = String.valueOf(nick).trim();
    }
    if (kickedNick.isBlank()) {
      Object nick = reflectCall(event, "getRecipientNick");
      if (nick != null) kickedNick = String.valueOf(nick).trim();
    }

    String reason = PircbotxUtil.safeStr(event::getReason, "");
    boolean selfKick = false;
    if (!channel.isBlank() && !kickedNick.isBlank()) {
      if (isSelfNick.test(event.getBot(), kickedNick)) {
        selfKick = true;
        emit.accept(
            new ServerIrcEvent(serverId, new IrcEvent.KickedFromChannel(at, channel, by, reason)));
      } else {
        emit.accept(
            new ServerIrcEvent(
                serverId, new IrcEvent.UserKickedFromChannel(at, channel, kickedNick, by, reason)));
      }
    }

    if (selfKick) return;

    if (ch != null) {
      rosterEmitter.emitRoster(ch);
    } else if (!channel.isBlank()) {
      rosterEmitter.refreshRosterByName(event.getBot(), channel);
    }
  }

  public void onNickChange(NickChangeEvent event) {
    String oldNick = PircbotxUtil.safeStr(event::getOldNick, "");
    String newNick = PircbotxUtil.safeStr(event::getNewNick, "");
    String hinted = conn.selfNickHint();
    if ((!hinted.isBlank() && oldNick.equalsIgnoreCase(hinted))
        || isSelfNick.test(event.getBot(), oldNick)
        || isSelfNick.test(event.getBot(), newNick)) {
      rememberSelfNickHint.accept(newNick);
    }
    try {
      rosterEmitter.maybeEmitHostmaskObserved("", event.getUser());
    } catch (Exception ignored) {
    }
    emit.accept(
        new ServerIrcEvent(serverId, new IrcEvent.NickChanged(Instant.now(), oldNick, newNick)));

    try {
      for (Channel channel : event.getBot().getUserChannelDao().getChannels(event.getUser())) {
        try {
          emit.accept(
              new ServerIrcEvent(
                  serverId,
                  new IrcEvent.UserNickChangedChannel(
                      Instant.now(), channel.getName(), oldNick, newNick)));
        } catch (Exception ignored) {
        }
        rosterEmitter.emitRoster(channel);
      }
    } catch (Exception ignored) {
    }
  }

  private static Object reflectCall(Object target, String method) {
    if (target == null || method == null) return null;
    try {
      java.lang.reflect.Method accessor = target.getClass().getMethod(method);
      return accessor.invoke(target);
    } catch (Exception ignored) {
      return null;
    }
  }
}
