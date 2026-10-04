package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreIrcv3InboundTranslatorTest {
  private static final Instant AT = Instant.parse("2026-03-03T12:00:00Z");
  private final QuasselIrcv3RuntimeSupport runtime =
      new QuasselIrcv3RuntimeSupport(Ircv3RuntimeCatalogs.applicationClasspath());
  private final QuasselCoreIrcv3InboundTranslator translator =
      new QuasselCoreIrcv3InboundTranslator(runtime);
  private final List<IrcEvent> events = new ArrayList<>();
  private final Function<String, String> qualify = target -> target + "{net:second}";

  @Test
  void tagObservationsKeepProviderOrderingAndUseTheSessionTargetResolver() {
    var observation =
        observation(
            "@+typing=active;+draft/react=thumbsup;+draft/reply=42;+draft/delete=99;+draft/read-marker=timestamp=2026-03-03T12:00:00.000Z :alice!u@h TAGMSG #cafe",
            "alice");
    translator.observeTags(observation, qualify, events::add);
    assertEquals(
        List.of(
            new IrcEvent.MessageReplyObserved(AT, "alice", "#cafe{net:second}", "42"),
            new IrcEvent.MessageReactObserved(AT, "alice", "#cafe{net:second}", "thumbsup", "42"),
            new IrcEvent.MessageRedactionObserved(AT, "alice", "#cafe{net:second}", "99"),
            new IrcEvent.UserTypingObserved(AT, "alice", "#cafe{net:second}", "active"),
            new IrcEvent.ReadMarkerObserved(
                AT, "alice", "#cafe{net:second}", "timestamp=2026-03-03T12:00:00.000Z")),
        events);
    assertFalse(translator.handleCommand(observation, qualify, events::add));
  }

  @Test
  void reactionWithoutReplyUsesCoreMessageIdAndBlankSenderUsesServer() {
    translator.observeTags(
        observation("@+draft/react=smile TAGMSG #cafe", " "), qualify, events::add);
    assertEquals(
        List.of(
            new IrcEvent.MessageReactObserved(AT, "server", "#cafe{net:second}", "smile", "123")),
        events);
  }

  @ParameterizedTest
  @ValueSource(strings = {"FAIL", "WARN", "NOTE"})
  void standardRepliesPreserveRawTextTagsAndProviderMessageId(String kind) {
    String raw =
        "@msgid=server-id;label=request :irc.example "
            + kind
            + " PRIVMSG INVALID_TARGET #cafe :No such channel";
    assertTrue(translator.handleCommand(observation(raw, "irc.example"), qualify, events::add));
    assertEquals(
        List.of(
            new IrcEvent.StandardReply(
                AT,
                IrcEvent.StandardReplyKind.valueOf(kind),
                "PRIVMSG",
                "INVALID_TARGET",
                "#cafe",
                "No such channel",
                raw,
                "server-id",
                java.util.Map.of("msgid", "server-id", "label", "request"))),
        events);
  }

  @Test
  void readMarkerAndRedactionCommandsResolveTheirOwnTargets() {
    assertTrue(
        translator.handleCommand(
            observation("MARKREAD #read timestamp=2026-03-03T12:00:00.000Z", ""),
            qualify,
            events::add));
    assertTrue(
        translator.handleCommand(
            observation("REDACT #redact 777 :cleanup", "alice"), qualify, events::add));
    assertEquals(
        List.of(
            new IrcEvent.ReadMarkerObserved(
                AT, "server", "#read{net:second}", "timestamp=2026-03-03T12:00:00.000Z"),
            new IrcEvent.MessageRedactionObserved(AT, "alice", "#redact{net:second}", "777")),
        events);
  }

  @Test
  void invalidMutationCommandsAreConsumedWithoutRenderingAChatMessage() {
    assertTrue(translator.handleCommand(observation("MARKREAD", "alice"), qualify, events::add));
    assertTrue(translator.handleCommand(observation("REDACT", "alice"), qualify, events::add));
    assertTrue(events.isEmpty());
  }

  @Test
  void ordinaryMessagesAndCapabilityLinesRemainAvailableToTheirOtherHandlers() {
    for (String raw :
        List.of("ordinary message", "PRIVMSG #cafe :hello", "CAP me ACK :message-tags")) {
      var observation = observation(raw, "alice");
      translator.observeTags(observation, qualify, events::add);
      assertFalse(translator.handleCommand(observation, qualify, events::add));
    }
    assertTrue(events.isEmpty());
  }

  private QuasselCoreIrcv3InboundTranslator.Observation observation(String raw, String from) {
    return new QuasselCoreIrcv3InboundTranslator.Observation(
        AT, from, QuasselCoreIrcEnvelope.parse(raw, runtime), "123");
  }
}
