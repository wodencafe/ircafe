package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;

/** Emits roster snapshots and opportunistic hostmask observations for a single connection. */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxRosterEmitter {
  @NonNull private final String serverId;
  @NonNull private final PircbotxConnectionState conn;
  @NonNull private final ServerIsupportStatePort serverIsupportState;
  @NonNull private final Consumer<ServerIrcEvent> emit;

  public void maybeEmitHostmaskObserved(String channel, User user) {
    if (user == null) return;
    String nick = PircbotxUtil.safeStr(user::getNick, "");
    if (nick == null || nick.isBlank()) return;

    String hostmask = PircbotxUtil.hostmaskFromUser(user);
    if (!PircbotxUtil.isUsefulHostmask(hostmask)) return;
    if (!conn.rememberHostmaskIfChanged(nick, hostmask)) return;

    String observedChannel = (channel == null) ? "" : channel;
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.UserHostmaskObserved(
                Instant.now(), observedChannel, nick.trim(), hostmask)));
  }

  public void refreshRosterByName(PircBotX bot, String channelName) {
    if (bot == null || channelName == null || channelName.isBlank()) return;
    try {
      if (!bot.getUserChannelDao().containsChannel(channelName)) return;
      Channel channel = bot.getUserChannelDao().getChannel(channelName);
      if (channel != null) emitRoster(channel);
    } catch (Exception ignored) {
    }
  }

  public void emitRoster(Channel channel) {
    if (channel == null) return;

    String channelName = channel.getName();
    ModeVocabulary vocabulary = vocabulary();
    Set<?> owners = setOrEmpty(channel, "getOwners");
    Set<?> admins = setOrEmpty(channel, "getSuperOps");
    Set<?> ops = setOrEmpty(channel, "getOps");
    Set<?> halfOps = setOrEmpty(channel, "getHalfOps");
    Set<?> voices = setOrEmpty(channel, "getVoices");

    List<IrcEvent.NickInfo> nicks =
        channel.getUsers().stream()
            .map(
                user ->
                    new IrcEvent.NickInfo(
                        user.getNick(),
                        prefixForUser(vocabulary, user, owners, admins, ops, halfOps, voices),
                        PircbotxUtil.hostmaskFromUser(user)))
            .sorted(
                Comparator.comparingInt(
                        (IrcEvent.NickInfo nick) -> prefixRank(vocabulary, nick.prefix()))
                    .thenComparing(IrcEvent.NickInfo::nick, String.CASE_INSENSITIVE_ORDER))
            .toList();

    int totalUsers = nicks.size();
    int operatorCount =
        (int) nicks.stream().filter(nick -> isOperatorLike(vocabulary, nick)).count();

    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.NickListUpdated(
                Instant.now(), channelName, nicks, totalUsers, operatorCount)));
  }

  private ModeVocabulary vocabulary() {
    ModeVocabulary vocabulary = serverIsupportState.vocabularyForServer(serverId);
    return vocabulary == null ? ModeVocabulary.fallback() : vocabulary;
  }

  private static Set<?> setOrEmpty(Channel channel, String method) {
    if (channel == null || method == null) return Set.of();
    try {
      java.lang.reflect.Method accessor = channel.getClass().getMethod(method);
      Object value = accessor.invoke(channel);
      if (value instanceof Set<?> set) return set;
    } catch (Exception ignored) {
    }
    return Set.of();
  }

  private static String prefixForUser(
      ModeVocabulary vocabulary,
      Object user,
      Set<?> owners,
      Set<?> admins,
      Set<?> ops,
      Set<?> halfOps,
      Set<?> voices) {
    if (user == null) return "";
    if (owners != null && owners.contains(user))
      return String.valueOf(prefixForMode(vocabulary, 'q', '~'));
    if (admins != null && admins.contains(user))
      return String.valueOf(prefixForMode(vocabulary, 'a', '&'));
    if (ops != null && ops.contains(user))
      return String.valueOf(prefixForMode(vocabulary, 'o', '@'));
    if (halfOps != null && halfOps.contains(user))
      return String.valueOf(prefixForMode(vocabulary, 'h', '%'));
    if (voices != null && voices.contains(user))
      return String.valueOf(prefixForMode(vocabulary, 'v', '+'));
    return "";
  }

  private static char prefixForMode(ModeVocabulary vocabulary, char mode, char fallback) {
    if (vocabulary == null) return fallback;
    return vocabulary.prefixForMode(mode).orElse(fallback);
  }

  private static int prefixRank(ModeVocabulary vocabulary, String prefix) {
    if (vocabulary == null) return 99;
    return vocabulary.prefixRank(prefix);
  }

  private static boolean isOperatorLike(ModeVocabulary vocabulary, IrcEvent.NickInfo nick) {
    if (nick == null) return false;
    String prefixes = Objects.toString(nick.prefix(), "").trim();
    if (prefixes.isEmpty() || vocabulary == null) return false;
    for (int i = 0; i < prefixes.length(); i++) {
      Optional<Character> mode = vocabulary.modeForPrefix(prefixes.charAt(i));
      if (mode.isPresent()
          && (mode.get().charValue() == 'q'
              || mode.get().charValue() == 'a'
              || mode.get().charValue() == 'o')) {
        return true;
      }
    }
    return false;
  }
}
