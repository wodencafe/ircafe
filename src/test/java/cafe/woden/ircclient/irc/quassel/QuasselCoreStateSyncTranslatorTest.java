package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreStateSyncTranslatorTest {
  private static final Instant AT = Instant.parse("2026-10-07T12:00:00Z");
  private final List<Object> observations = new ArrayList<>();

  @Test
  void userSnapshotObservesNetworkBeforeOrderedEventsWithOneTimestamp() {
    user(
        "3/fallback",
        List.of(
            Map.of(
                "networkId",
                7,
                "networkName",
                " second ",
                "nick",
                " alice ",
                "user",
                " auser ",
                "host",
                " example.net ",
                "realName",
                " Alice Liddell ",
                "account",
                " alice-account ",
                "away",
                true,
                "awayMessage",
                " coffee ")));
    assertEquals(
        List.of(
            new Network(7, "second"),
            new IrcEvent.UserHostChanged(AT, "alice", "auser", "example.net"),
            new IrcEvent.UserSetNameObserved(
                AT, "alice", "Alice Liddell", IrcEvent.UserSetNameObserved.Source.SETNAME),
            new IrcEvent.UserAccountStateObserved(
                AT, "alice", IrcEvent.AccountState.LOGGED_IN, "alice-account"),
            new IrcEvent.UserAwayStateObserved(AT, "alice", IrcEvent.AwayState.AWAY, "coffee")),
        observations);
  }

  @ParameterizedTest
  @CsvSource({
    "nickname, username, hostname, realname, accountName, awayMsg",
    "nick, user, host, real_name, account, awayReason"
  })
  void userFieldAliasesKeepStructuredEvents(
      String nickKey,
      String userKey,
      String hostKey,
      String realNameKey,
      String accountKey,
      String awayKey) {
    user(
        null,
        List.of(
            Map.of(
                nickKey,
                "alice",
                userKey,
                "auser",
                hostKey,
                "example.net",
                realNameKey,
                "Alice",
                accountKey,
                "account",
                awayKey,
                "coffee")));
    assertEquals(
        List.of(
            new Network(-1, ""),
            new IrcEvent.UserHostChanged(AT, "alice", "auser", "example.net"),
            new IrcEvent.UserSetNameObserved(
                AT, "alice", "Alice", IrcEvent.UserSetNameObserved.Source.SETNAME),
            new IrcEvent.UserAccountStateObserved(
                AT, "alice", IrcEvent.AccountState.LOGGED_IN, "account"),
            new IrcEvent.UserAwayStateObserved(AT, "alice", IrcEvent.AwayState.AWAY, "coffee")),
        observations);
  }

  @Test
  void firstNonBlankUserFieldsTakePrecedenceOverAliasesAndObjectName() {
    user(
        "other",
        List.of(
            Map.of(
                "nick",
                "alice",
                "nickname",
                "other",
                "realName",
                " ",
                "realname",
                "Alice",
                "real_name",
                "Other",
                "account",
                " ",
                "accountName",
                "account",
                "awayMessage",
                "coffee",
                "awayMsg",
                "other")));
    assertEquals(
        List.of(
            new Network(-1, ""),
            new IrcEvent.UserSetNameObserved(
                AT, "alice", "Alice", IrcEvent.UserSetNameObserved.Source.SETNAME),
            new IrcEvent.UserAccountStateObserved(
                AT, "alice", IrcEvent.AccountState.LOGGED_IN, "account"),
            new IrcEvent.UserAwayStateObserved(AT, "alice", IrcEvent.AwayState.AWAY, "coffee")),
        observations);
  }

  @ParameterizedTest
  @ValueSource(strings = {"account", "accountName"})
  void explicitlyEmptyOrNullAccountEmitsLogout(String key) {
    for (String value : Arrays.asList(null, "", " ")) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put(key, value);
      user("alice", List.of(map));
    }
    assertEquals(
        List.of(
            new Network(-1, ""),
                new IrcEvent.UserAccountStateObserved(
                    AT, "alice", IrcEvent.AccountState.LOGGED_OUT),
            new Network(-1, ""),
                new IrcEvent.UserAccountStateObserved(
                    AT, "alice", IrcEvent.AccountState.LOGGED_OUT),
            new Network(-1, ""),
                new IrcEvent.UserAccountStateObserved(
                    AT, "alice", IrcEvent.AccountState.LOGGED_OUT)),
        observations);
  }

  @Test
  void absentOrDifferentlyCasedAccountDoesNotInventLogout() {
    user("alice", List.of(Map.of(), Map.of("Account", "")));
    assertEquals(List.of(new Network(-1, ""), new Network(-1, "")), observations);
  }

  @ParameterizedTest
  @MethodSource("awayFlags")
  void explicitAwayFlagWinsOverNonemptyMessage(Object flag, IrcEvent.AwayState expected) {
    user("alice", List.of(Map.of("away", flag, "awayMessage", "coffee")));
    assertEquals(
        List.of(
            new Network(-1, ""),
            new IrcEvent.UserAwayStateObserved(AT, "alice", expected, "coffee")),
        observations);
  }

  private static Stream<Arguments> awayFlags() {
    return Stream.of(
        Arguments.of(true, IrcEvent.AwayState.AWAY),
        Arguments.of(0, IrcEvent.AwayState.HERE),
        Arguments.of("off", IrcEvent.AwayState.HERE));
  }

  @Test
  void unknownAwayFlagUsesMessageAndAbsentAwayStateEmitsNothing() {
    user(
        "alice",
        List.of(
            Map.of("away", "unknown", "awayReason", "coffee"),
            Map.of("away", "unknown"),
            Map.of()));
    assertEquals(
        List.of(
            new Network(-1, ""),
            new IrcEvent.UserAwayStateObserved(AT, "alice", IrcEvent.AwayState.AWAY, "coffee"),
            new Network(-1, ""),
            new Network(-1, "")),
        observations);
  }

  @Test
  void partialHostmaskAndBlankNickDoNotProduceUserEvents() {
    user(
        "",
        List.of(
            Map.of("nick", "alice", "user", "auser"),
            Map.of("nick", "alice", "host", "example.net"),
            Map.of("nick", " ", "realName", "Alice", "networkId", 7, "networkName", "second")));
    assertEquals(
        List.of(new Network(-1, ""), new Network(-1, ""), new Network(7, "second")), observations);
  }

  @Test
  void objectLeafSuppliesNickWithoutChangingCurrentNetworkIdParsing() {
    user(" 7/alice ", List.of(Map.of("realName", "Alice")));
    assertEquals(
        List.of(
            new Network(-1, ""),
            new IrcEvent.UserSetNameObserved(
                AT, "alice", "Alice", IrcEvent.UserSetNameObserved.Source.SETNAME)),
        observations);
  }

  @ParameterizedTest
  @CsvSource({"networkId, 7", "network, 8", "id, 9"})
  void mapNetworkIdAliasesOverrideNumericObjectFallback(String key, int networkId) {
    user("Network/3", List.of(Map.of(key, networkId, "nick", "alice")));
    assertEquals(List.of(new Network(networkId, "")), observations);
  }

  @Test
  void unknownMapNetworkIdKeepsNumericObjectFallbackAndInputOrder() {
    user(
        "Network/3",
        List.of(
            Map.of("networkId", -1, "realName", "First"),
            Map.of("networkId", 7, "nick", "alice", "realName", "Second")));
    assertEquals(
        List.of(
            new Network(3, ""),
            new IrcEvent.UserSetNameObserved(
                AT, "3", "First", IrcEvent.UserSetNameObserved.Source.SETNAME),
            new Network(7, ""),
            new IrcEvent.UserSetNameObserved(
                AT, "alice", "Second", IrcEvent.UserSetNameObserved.Source.SETNAME)),
        observations);
  }

  @ParameterizedTest
  @CsvSource({"name, topic", "channel, topicText", "bufferName, topicString"})
  void channelAliasesObserveNetworkBeforeQualificationAndTopic(String channelKey, String topicKey) {
    channel(
        "3/fallback",
        List.of(
            Map.of(
                "networkId",
                7,
                "networkName",
                " second ",
                channelKey,
                " #cafe ",
                topicKey,
                " Coffee ")));
    assertEquals(
        List.of(
            new Network(7, "second"),
            new Qualification("#cafe", 7),
            new IrcEvent.ChannelTopicUpdated(AT, "#cafe{net:7}", "Coffee")),
        observations);
  }

  @Test
  void channelLeafAndNumericNetworkFallbackKeepExistingParsing() {
    channel("7/#cafe", List.of(Map.of("topic", "Coffee")));
    channel("Network/3", List.of(Map.of("name", "#other", "topic", "Tea")));
    assertEquals(
        List.of(
            new Network(-1, ""),
            new Qualification("#cafe", -1),
            new IrcEvent.ChannelTopicUpdated(AT, "#cafe{net:-1}", "Coffee"),
            new Network(3, ""),
            new Qualification("#other", 3),
            new IrcEvent.ChannelTopicUpdated(AT, "#other{net:3}", "Tea")),
        observations);
  }

  @ParameterizedTest
  @ValueSource(strings = {"#cafe", "&cafe", "+cafe", "!cafe"})
  void allSupportedChannelPrefixesReachTargetQualifier(String target) {
    channel(null, List.of(Map.of("channel", target, "topic", "Coffee")));
    assertEquals(
        List.of(
            new Network(-1, ""),
            new Qualification(target, -1),
            new IrcEvent.ChannelTopicUpdated(AT, target + "{net:-1}", "Coffee")),
        observations);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void blankChannelTopicDoesNotQualifyOrEmit(String topic) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("topic", topic);
    channel("#cafe", List.of(map));
    assertEquals(List.of(new Network(-1, "")), observations);
  }

  @Test
  void invalidChannelStillObservesNetworkWithoutEmittingTopic() {
    channel(
        null,
        List.of(
            Map.of("name", "#", "topic", "Coffee"),
            Map.of("name", "alice", "topic", "Coffee"),
            Map.of("topic", "Coffee")));
    assertEquals(
        List.of(new Network(-1, ""), new Network(-1, ""), new Network(-1, "")), observations);
  }

  @Test
  void unsupportedValuesAndEmptyPayloadsAreIgnoredWithoutRecursiveTranslation() {
    List<Object> values =
        Arrays.asList(
            null,
            "ignored",
            7,
            List.of(Map.of("realName", "Nested", "topic", "Nested")),
            new QuasselCoreDatastreamCodec.UserTypeValue("IrcUser", Map.of("realName", "Nested")));
    user("alice", values);
    channel("#cafe", values);
    user("alice", null);
    channel("#cafe", null);
    user("alice", List.of());
    channel("#cafe", List.of());
    assertTrue(observations.isEmpty());
  }

  private void user(String objectName, List<Object> values) {
    QuasselCoreStateSyncTranslator.userState(
        AT,
        objectName,
        values,
        (id, name) -> observations.add(new Network(id, name)),
        observations::add);
  }

  private void channel(String objectName, List<Object> values) {
    QuasselCoreStateSyncTranslator.channelState(
        AT,
        objectName,
        values,
        (id, name) -> observations.add(new Network(id, name)),
        (target, id) -> {
          observations.add(new Qualification(target, id));
          return target + "{net:" + id + "}";
        },
        observations::add);
  }

  private record Network(int id, String name) {}

  private record Qualification(String target, int id) {}
}
