package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.looksLikeChannel;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.networkIdFromStateMap;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.parseBoolean;

import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Translates native user/channel snapshots without owning transport or session state. */
final class QuasselCoreStateSyncTranslator {
  private QuasselCoreStateSyncTranslator() {}

  static void userState(
      Instant at,
      String objectName,
      List<Object> values,
      BiConsumer<Integer, String> observeNetwork,
      Consumer<IrcEvent> emit) {
    if (values == null || values.isEmpty()) return;
    int objectNetworkId = parseNetworkId(objectName);
    String objectNick = parseObjectLeafToken(objectName);
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map)) continue;
      int networkId = networkIdFromStateMap(map, objectNetworkId);
      String networkName =
          firstNonBlank(map.get("networkName"), map.get("networkname"), map.get("name"));
      observeNetwork.accept(networkId, networkName);

      String nick = firstNonBlank(map.get("nick"), map.get("nickname"), objectNick);
      if (nick.isEmpty()) continue;

      String user = firstNonBlank(map.get("user"), map.get("username"));
      String host = firstNonBlank(map.get("host"), map.get("hostname"));
      if (!user.isEmpty() && !host.isEmpty()) {
        emit.accept(new IrcEvent.UserHostChanged(at, nick, user, host));
      }

      String realName =
          firstNonBlank(map.get("realName"), map.get("realname"), map.get("real_name"));
      if (!realName.isEmpty()) {
        emit.accept(
            new IrcEvent.UserSetNameObserved(
                at, nick, realName, IrcEvent.UserSetNameObserved.Source.SETNAME));
      }

      String account = firstNonBlank(map.get("account"), map.get("accountName"));
      if (!account.isEmpty()) {
        emit.accept(
            new IrcEvent.UserAccountStateObserved(
                at, nick, IrcEvent.AccountState.LOGGED_IN, account));
      } else if (map.containsKey("account") || map.containsKey("accountName")) {
        emit.accept(
            new IrcEvent.UserAccountStateObserved(at, nick, IrcEvent.AccountState.LOGGED_OUT));
      }

      Boolean awayFlag = parseBoolean(map.get("away"));
      String awayMessage =
          firstNonBlank(map.get("awayMessage"), map.get("awayMsg"), map.get("awayReason"));
      if (awayFlag != null) {
        emit.accept(
            new IrcEvent.UserAwayStateObserved(
                at,
                nick,
                awayFlag ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE,
                awayMessage));
      } else if (!awayMessage.isEmpty()) {
        emit.accept(
            new IrcEvent.UserAwayStateObserved(at, nick, IrcEvent.AwayState.AWAY, awayMessage));
      }
    }
  }

  static void channelState(
      Instant at,
      String objectName,
      List<Object> values,
      BiConsumer<Integer, String> observeNetwork,
      BiFunction<String, Integer, String> qualifyTarget,
      Consumer<IrcEvent> emit) {
    if (values == null || values.isEmpty()) return;
    int objectNetworkId = parseNetworkId(objectName);
    String objectChannel = parseObjectLeafToken(objectName);
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> map)) continue;
      int networkId = networkIdFromStateMap(map, objectNetworkId);
      String networkName =
          firstNonBlank(map.get("networkName"), map.get("networkname"), map.get("network"));
      observeNetwork.accept(networkId, networkName);

      String channel =
          firstNonBlank(map.get("name"), map.get("channel"), map.get("bufferName"), objectChannel);
      if (!looksLikeChannel(channel)) continue;
      String topic = firstNonBlank(map.get("topic"), map.get("topicText"), map.get("topicString"));
      if (topic.isEmpty()) continue;
      String qualifiedChannel = qualifyTarget.apply(channel, networkId);
      emit.accept(new IrcEvent.ChannelTopicUpdated(at, qualifiedChannel, topic));
    }
  }

  private static String parseObjectLeafToken(String objectName) {
    String token = Objects.toString(objectName, "").trim();
    if (token.isEmpty()) return "";
    int slash = token.lastIndexOf('/');
    if (slash >= 0 && slash < token.length() - 1) {
      token = token.substring(slash + 1).trim();
    }
    return token;
  }
}
