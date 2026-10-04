package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundCommandSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundTagSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3IsupportRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
  @ValueSource(strings = {"PRIVMSG", "NOTICE", "TAGMSG"})
  void blankSenderKeepsDirectMessageRecipientAsTarget(String command) {
    translator.observeTags(
        observation("@+draft/react=smile " + command + " recipient :hello", " "),
        qualify,
        events::add);
    assertEquals(
        List.of(
            new IrcEvent.MessageReactObserved(
                AT, "server", "recipient{net:second}", "smile", "123")),
        events);
  }

  @Test
  void namedSenderReusesOneProviderRequestAndResolvesTargetBeforeReadingSignals() {
    List<Ircv3InboundTagRequest> requests = new ArrayList<>();
    List<String> calls = new ArrayList<>();
    var recording = recordingTranslator(requests, calls);
    String raw = "@+draft/react=smile :alice!u@h PRIVMSG recipient :hello";
    recording.observeTags(
        observation(raw, " alice "),
        target -> {
          calls.add("resolveTarget");
          return qualify.apply(target);
        },
        events::add);

    assertEquals(
        List.of(
            "CHANNEL_CONTEXT",
            "resolveTarget",
            "REPLY",
            "REACTIONS",
            "MESSAGE_REDACTION",
            "TYPING",
            "READ_MARKER"),
        calls);
    assertEquals(
        new Ircv3InboundTagRequest(
            "PRIVMSG",
            "alice",
            "recipient",
            List.of("recipient"),
            Map.of("draft/react", "smile"),
            raw),
        requests.getFirst());
    requests.forEach(request -> assertSame(requests.getFirst(), request));
    assertEquals(
        List.of(
            new IrcEvent.MessageReactObserved(AT, "alice", "alice{net:second}", "smile", "123")),
        events);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void senderFallbackOnlyChangesSignalRequests(String sender) {
    List<Ircv3InboundTagRequest> requests = new ArrayList<>();
    var recording = recordingTranslator(requests, new ArrayList<>());
    recording.observeTags(
        observation("@+draft/react=smile PRIVMSG recipient :hello", sender), qualify, events::add);

    var contextRequest = requests.getFirst();
    var signalRequest = requests.get(1);
    assertEquals("", contextRequest.sourceNick());
    assertEquals("server", signalRequest.sourceNick());
    assertEquals("recipient", signalRequest.rawTarget());
    assertEquals(contextRequest.tags(), signalRequest.tags());
    assertEquals(contextRequest.parameters(), signalRequest.parameters());
    assertEquals(contextRequest.rawLine(), signalRequest.rawLine());
    requests.subList(1, requests.size()).forEach(request -> assertSame(signalRequest, request));
    assertEquals(
        List.of(
            new IrcEvent.MessageReactObserved(
                AT, "server", "recipient{net:second}", "smile", "123")),
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

  @ParameterizedTest
  @ValueSource(strings = {"730", "731"})
  void monitorStatusEmitsHostmasksBeforeOnlineOrOfflineEvents(String numeric) {
    assertTrue(
        translator.handleMonitor(
            AT,
            ":irc.example " + numeric + " me :alice!u@h,bob!x@y,carol",
            support -> fail("Status numerics must not change advertised support"),
            events::add));
    IrcEvent status =
        "730".equals(numeric)
            ? new IrcEvent.MonitorOnlineObserved(AT, List.of("alice", "bob", "carol"))
            : new IrcEvent.MonitorOfflineObserved(AT, List.of("alice", "bob", "carol"));
    assertEquals(
        List.of(
            new IrcEvent.UserHostmaskObserved(AT, "", "alice", "alice!u@h"),
            new IrcEvent.UserHostmaskObserved(AT, "", "bob", "bob!x@y"),
            status),
        events);
  }

  @Test
  void monitorListResponsesPreserveNicksLimitMessageAndTimestamp() {
    for (String raw :
        List.of(
            ":irc.example 732 me :alice,bob",
            ":irc.example 733 me :End of MONITOR list",
            ":irc.example 734 me 100 dave,erin :Monitor list is full")) {
      assertTrue(
          translator.handleMonitor(
              AT, raw, support -> fail("Unexpected support update"), events::add));
    }
    assertEquals(
        List.of(
            new IrcEvent.MonitorListObserved(AT, List.of("alice", "bob")),
            new IrcEvent.MonitorListEnded(AT),
            new IrcEvent.MonitorListFull(AT, 100, List.of("dave", "erin"), "Monitor list is full")),
        events);
  }

  @Test
  void monitorSupportIsObservedWithoutConsumingTheIsupportResponse() {
    List<Ircv3IsupportRuntimeSupport.MonitorSupport> support = new ArrayList<>();
    assertFalse(
        translator.handleMonitor(
            AT,
            ":irc.example 005 me MONITOR=150 CHANTYPES=# :are supported by this server",
            support::add,
            events::add));
    assertEquals(List.of(new Ircv3IsupportRuntimeSupport.MonitorSupport(true, 150)), support);
    assertTrue(events.isEmpty());
  }

  @Test
  void customMonitorProvidersObserveSupportBeforeEventsAndFilterUnusableHostmasks() {
    List<String> calls = new ArrayList<>();
    var custom =
        monitorTranslator(
            (operation, request) -> {
              calls.add(operation.name());
              assertEquals("custom response", request.rawLine());
              if (operation == Ircv3InboundCommandOperation.ISUPPORT_MONITOR) {
                return List.of(new Ircv3InboundCommandSignal.MonitorSupportObserved(true, 321));
              }
              return List.of(
                  new Ircv3InboundCommandSignal.WhoisEndedObserved("ignored"),
                  new Ircv3InboundCommandSignal.MonitorStatusObserved(
                      true,
                      List.of(
                          new Ircv3InboundCommandSignal.MonitorStatusEntry(" ", "!u@h"),
                          new Ircv3InboundCommandSignal.MonitorStatusEntry("alice", "alice!*@*"),
                          new Ircv3InboundCommandSignal.MonitorStatusEntry("bob", "bob!x@y"))));
            });
    assertTrue(
        custom.handleMonitor(
            AT,
            " custom response ",
            support -> {
              assertEquals(new Ircv3IsupportRuntimeSupport.MonitorSupport(true, 321), support);
              calls.add("observeSupport");
            },
            event -> {
              calls.add(event.getClass().getSimpleName());
              events.add(event);
            }));
    assertEquals(
        List.of(
            "ISUPPORT_MONITOR",
            "observeSupport",
            "MONITOR",
            "UserHostmaskObserved",
            "MonitorOnlineObserved"),
        calls);
    assertEquals(
        List.of(
            new IrcEvent.UserHostmaskObserved(AT, "", "bob", "bob!x@y"),
            new IrcEvent.MonitorOnlineObserved(AT, List.of("alice", "bob"))),
        events);
  }

  @ParameterizedTest
  @ValueSource(strings = {"empty", "blank"})
  void recognizedMonitorStatusIsConsumedEvenWithoutUsableNicks(String response) {
    var entries =
        "empty".equals(response)
            ? List.<Ircv3InboundCommandSignal.MonitorStatusEntry>of()
            : List.of(new Ircv3InboundCommandSignal.MonitorStatusEntry(" ", ""));
    var custom =
        monitorTranslator(
            (operation, request) ->
                operation == Ircv3InboundCommandOperation.MONITOR
                    ? List.of(new Ircv3InboundCommandSignal.MonitorStatusObserved(true, entries))
                    : List.of());
    assertTrue(
        custom.handleMonitor(
            AT, response, support -> fail("Unexpected support update"), events::add));
    assertTrue(events.isEmpty());
  }

  @Test
  void unrelatedProviderSignalsRemainAvailableToOtherHandlers() {
    var custom =
        monitorTranslator(
            (operation, request) ->
                List.of(new Ircv3InboundCommandSignal.WhoisEndedObserved("alice")));
    assertFalse(
        custom.handleMonitor(
            AT, "unrelated response", support -> fail("Unexpected support update"), events::add));
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void blankMonitorInputDoesNotInvokeProviders(String raw) {
    var custom =
        monitorTranslator(
            (operation, request) -> {
              fail("Blank input must not invoke a provider");
              return List.of();
            });
    assertFalse(
        custom.handleMonitor(AT, raw, support -> fail("Unexpected support update"), events::add));
    assertTrue(events.isEmpty());
  }

  private static QuasselCoreIrcv3InboundTranslator monitorTranslator(
      BiFunction<
              Ircv3InboundCommandOperation,
              Ircv3InboundCommandRequest,
              List<Ircv3InboundCommandSignal>>
          parse) {
    var catalogs = Ircv3RuntimeCatalogs.applicationClasspath();
    var provider =
        new Ircv3InboundCommandSignalProvider() {
          @Override
          public String providerId() {
            return "custom-monitor";
          }

          @Override
          public Set<Ircv3InboundCommandOperation> inboundCommandOperations() {
            return Set.of(
                Ircv3InboundCommandOperation.ISUPPORT_MONITOR,
                Ircv3InboundCommandOperation.MONITOR);
          }

          @Override
          public List<Ircv3InboundCommandSignal> parse(
              Ircv3InboundCommandOperation operation, Ircv3InboundCommandRequest request) {
            return parse.apply(operation, request);
          }
        };
    return new QuasselCoreIrcv3InboundTranslator(
        new QuasselIrcv3RuntimeSupport(
            catalogs.outboundCommands(),
            catalogs.inboundTags(),
            Ircv3InboundCommandSignalRuntimeCatalog.fromProviders(List.of(provider)),
            catalogs.messageTags()));
  }

  private QuasselCoreIrcv3InboundTranslator.Observation observation(String raw, String from) {
    return new QuasselCoreIrcv3InboundTranslator.Observation(
        AT, from, QuasselCoreIrcEnvelope.parse(raw, runtime), "123");
  }

  private static QuasselCoreIrcv3InboundTranslator recordingTranslator(
      List<Ircv3InboundTagRequest> requests, List<String> calls) {
    var catalogs = Ircv3RuntimeCatalogs.applicationClasspath();
    var provider =
        new Ircv3InboundTagSignalProvider() {
          @Override
          public String providerId() {
            return "recording-tags";
          }

          @Override
          public Set<Ircv3InboundTagOperation> inboundTagOperations() {
            return EnumSet.allOf(Ircv3InboundTagOperation.class);
          }

          @Override
          public List<Ircv3InboundTagSignal> parse(
              Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
            calls.add(operation.name());
            requests.add(request);
            return catalogs.inboundTags().parse(operation, request);
          }
        };
    return new QuasselCoreIrcv3InboundTranslator(
        new QuasselIrcv3RuntimeSupport(
            catalogs.outboundCommands(),
            Ircv3InboundTagSignalRuntimeCatalog.fromProviders(List.of(provider)),
            catalogs.inboundCommands(),
            catalogs.messageTags()));
  }
}
