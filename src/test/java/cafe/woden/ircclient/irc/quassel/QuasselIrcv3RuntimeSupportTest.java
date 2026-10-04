package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.irc.ircv3.Ircv3InboundCommandSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundTagSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageMutationRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageTagsRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3OutboundCommandRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3MessageTagParseRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3MessageTagParseResult;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3MessageTagParserProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3OutboundCommandRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuasselIrcv3RuntimeSupportTest {

  @Test
  void routesOutboundTypingAndReadMarkerRenderingThroughRuntimeProviders() {
    QuasselIrcv3RuntimeSupport support = support();

    assertEquals(
        List.of("@+typing=paused TAGMSG #ircafe"), support.typingRawLines("#ircafe", "composing"));
    assertEquals(
        List.of("MARKREAD #ircafe timestamp=2026-07-13T13:00:00Z"),
        support.readMarkerRawLines("#ircafe", Instant.ofEpochSecond(1_700_000_000L)));
  }

  @Test
  void routesChatHistoryPlanningThroughRuntimeProviders() {
    QuasselIrcv3RuntimeSupport support = support();

    var latest = support.chatHistoryLatest("#ircafe", "*", 50);
    var between = support.chatHistoryBetween("#ircafe", "msgid=a", "msgid=b", 40);

    assertEquals("msgid=plugin", latest.primarySelector());
    assertEquals(17, latest.limit());
    assertEquals("msgid=left", between.primarySelector());
    assertEquals("msgid=right", between.secondarySelector());
  }

  @Test
  void routesConversationContextAndTagSignalsThroughRuntimeProviders() {
    QuasselIrcv3RuntimeSupport support = support();
    Map<String, String> tags =
        Map.of(
            "plugin/context", "#plugin",
            "plugin/typing", "active",
            "plugin/read-marker", "timestamp=plugin");

    assertEquals(
        "#plugin",
        support.channelContext("PRIVMSG", "alice", "quassel", List.of("quassel"), tags, "raw"));
    assertEquals(
        "alice",
        support.channelContext("TAGMSG", "alice", "quassel", List.of("quassel"), tags, "raw"));
    assertEquals(
        List.of(
            Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.REPLY, "plugin-reply"),
            new Ircv3InboundTagSignal(Ircv3InboundTagSignalType.REACT, "sparkle", "plugin-message"),
            Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.TYPING, "active"),
            Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.READ_MARKER, "timestamp=plugin")),
        support.conversationSignals("TAGMSG", "alice", "quassel", List.of("quassel"), tags, "raw"));
  }

  @Test
  void preparedRequestPreservesObservationMetadataAcrossProviderOperations() {
    List<Ircv3InboundTagRequest> observedRequests = new ArrayList<>();
    var delegate = new TagProvider();
    var recordingProvider =
        new Ircv3InboundTagSignalProvider() {
          @Override
          public String providerId() {
            return "recording-tags";
          }

          @Override
          public Set<Ircv3InboundTagOperation> inboundTagOperations() {
            return delegate.inboundTagOperations();
          }

          @Override
          public List<Ircv3InboundTagSignal> parse(
              Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
            observedRequests.add(request);
            return delegate.parse(operation, request);
          }
        };
    var support =
        new QuasselIrcv3RuntimeSupport(
            Ircv3OutboundCommandRuntimeCatalog.fromProviders(List.of(new OutboundProvider())),
            Ircv3InboundTagSignalRuntimeCatalog.fromProviders(List.of(recordingProvider)),
            Ircv3InboundCommandSignalRuntimeCatalog.fromProviders(List.of(new CommandProvider())),
            Ircv3MessageTagsRuntimeCatalog.fromProviders(List.of(new MessageTagProvider())));
    var request =
        new Ircv3InboundTagRequest(
            "PRIVMSG",
            "alice",
            "quassel",
            List.of("quassel"),
            Map.of("plugin/context", "#plugin"),
            "@plugin/context=#plugin :alice PRIVMSG quassel :hello",
            Instant.parse("2026-03-03T12:00:00Z").toEpochMilli(),
            List.of("quassel", "quassel_"),
            true);

    assertEquals("#plugin", support.channelContext(request));
    assertEquals(
        List.of(
            Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.REPLY, "plugin-reply"),
            new Ircv3InboundTagSignal(
                Ircv3InboundTagSignalType.REACT, "sparkle", "plugin-message")),
        support.conversationSignals(request));
    assertEquals(5, observedRequests.size());
    observedRequests.forEach(observed -> assertSame(request, observed));
  }

  @Test
  void routesRawMessageTagParsingThroughRuntimeProvider() {
    QuasselIrcv3RuntimeSupport support = support();

    assertEquals(
        Map.of("plugin", "raw-line"), support.messageTags("@wire=1 :server NOTICE me :hi"));
  }

  @Test
  void routesMonitorNumericsThroughRuntimeProviders() {
    QuasselIrcv3RuntimeSupport support = support();

    assertEquals(
        List.of(new Ircv3InboundCommandSignal.MonitorListObserved(List.of("alice", "bob"))),
        support.monitorSignals(":server 732 me :alice,bob"));
  }

  @Test
  void routesMonitorIsupportThroughRuntimeProviders() {
    QuasselIrcv3RuntimeSupport support = support();

    assertEquals(
        new cafe.woden.ircclient.irc.ircv3.Ircv3IsupportRuntimeSupport.MonitorSupport(true, 321),
        support.monitorSupport(":server 005 me MONITOR=100 :supported").orElseThrow());
  }

  @Test
  void routesStandardRepliesThroughRuntimeProviders() {
    QuasselIrcv3RuntimeSupport support = support();

    var reply =
        support
            .standardReply(
                "FAIL",
                "@msgid=wire-1 :server FAIL CHATHISTORY INVALID_PARAMS timestamp=bad :Invalid selector",
                List.of("CHATHISTORY", "INVALID_PARAMS", "timestamp=bad"),
                "Invalid selector",
                Map.of("msgid", "wire-1"),
                "fallback")
            .orElseThrow();

    assertEquals(
        cafe.woden.ircclient.irc.ircv3.Ircv3StandardReplyRuntimeSupport.Kind.FAIL, reply.kind());
    assertEquals("PLUGIN", reply.command());
    assertEquals("OVERRIDE", reply.code());
    assertEquals("plugin-context", reply.context());
    assertEquals("plugin description", reply.description());
    assertEquals("wire-1", reply.messageId());
  }

  @Test
  void routesReadMarkerCommandsThroughRuntimeProvider() {
    QuasselIrcv3RuntimeSupport support = support();

    assertEquals(
        new cafe.woden.ircclient.irc.ircv3.Ircv3ReadMarkerRuntimeSupport.CommandObservation(
            "#plugin", "timestamp=plugin-command"),
        support
            .readMarkerFromCommand(
                "server",
                "MARKREAD",
                ":server MARKREAD #ircafe timestamp=wire",
                List.of("#ircafe", "timestamp=wire"),
                Map.of())
            .orElseThrow());
  }

  @Test
  void routesDirectRedactionCommandsThroughValidatedRuntimeProvider() {
    QuasselIrcv3RuntimeSupport support = support();

    assertEquals(
        new Ircv3MessageMutationRuntimeSupport.CommandRedactionObservation(
            "#plugin", "plugin-message"),
        support
            .redactionFromCommand(
                "server",
                "REDACT",
                ":server REDACT #ircafe wire-message",
                List.of("#ircafe", "wire-message"),
                Map.of())
            .orElseThrow());
  }

  private static QuasselIrcv3RuntimeSupport support() {
    return new QuasselIrcv3RuntimeSupport(
        Ircv3OutboundCommandRuntimeCatalog.fromProviders(List.of(new OutboundProvider())),
        Ircv3InboundTagSignalRuntimeCatalog.fromProviders(List.of(new TagProvider())),
        Ircv3InboundCommandSignalRuntimeCatalog.fromProviders(List.of(new CommandProvider())),
        Ircv3MessageTagsRuntimeCatalog.fromProviders(List.of(new MessageTagProvider())));
  }

  private static final class OutboundProvider implements Ircv3OutboundCommandProvider {

    @Override
    public String providerId() {
      return "test-outbound";
    }

    @Override
    public Set<Ircv3OutboundCommandOperation> operations() {
      return Set.of(
          Ircv3OutboundCommandOperation.TYPING,
          Ircv3OutboundCommandOperation.READ_MARKER,
          Ircv3OutboundCommandOperation.CHAT_HISTORY_BEFORE,
          Ircv3OutboundCommandOperation.CHAT_HISTORY_LATEST,
          Ircv3OutboundCommandOperation.CHAT_HISTORY_BETWEEN,
          Ircv3OutboundCommandOperation.CHAT_HISTORY_AROUND);
    }

    @Override
    public List<String> build(
        Ircv3OutboundCommandOperation operation, Ircv3OutboundCommandRequest request) {
      return switch (operation) {
        case TYPING -> List.of("@+typing=paused TAGMSG " + request.target());
        case READ_MARKER ->
            List.of("MARKREAD " + request.target() + " timestamp=2026-07-13T13:00:00Z");
        case CHAT_HISTORY_BEFORE ->
            List.of("CHATHISTORY BEFORE " + request.target() + " msgid=before 19");
        case CHAT_HISTORY_LATEST ->
            List.of("CHATHISTORY LATEST " + request.target() + " msgid=plugin 17");
        case CHAT_HISTORY_BETWEEN ->
            List.of("CHATHISTORY BETWEEN " + request.target() + " msgid=left msgid=right 23");
        case CHAT_HISTORY_AROUND ->
            List.of("CHATHISTORY AROUND " + request.target() + " msgid=around 21");
        default -> List.of();
      };
    }
  }

  private static final class TagProvider implements Ircv3InboundTagSignalProvider {

    @Override
    public String providerId() {
      return "test-tags";
    }

    @Override
    public Set<Ircv3InboundTagOperation> inboundTagOperations() {
      return Set.of(
          Ircv3InboundTagOperation.CHANNEL_CONTEXT,
          Ircv3InboundTagOperation.REPLY,
          Ircv3InboundTagOperation.REACTIONS,
          Ircv3InboundTagOperation.TYPING,
          Ircv3InboundTagOperation.READ_MARKER,
          Ircv3InboundTagOperation.MESSAGE_ID);
    }

    @Override
    public List<Ircv3InboundTagSignal> parse(
        Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
      return switch (operation) {
        case CHANNEL_CONTEXT ->
            List.of(
                Ircv3InboundTagSignal.of(
                    Ircv3InboundTagSignalType.CONVERSATION_TARGET,
                    request.tags().get("plugin/context")));
        case REPLY ->
            List.of(Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.REPLY, "plugin-reply"));
        case REACTIONS ->
            List.of(
                new Ircv3InboundTagSignal(
                    Ircv3InboundTagSignalType.REACT, "sparkle", "plugin-message"));
        case TYPING ->
            request.tags().containsKey("plugin/typing")
                ? List.of(
                    Ircv3InboundTagSignal.of(
                        Ircv3InboundTagSignalType.TYPING, request.tags().get("plugin/typing")))
                : List.of();
        case READ_MARKER ->
            request.tags().containsKey("plugin/read-marker")
                ? List.of(
                    Ircv3InboundTagSignal.of(
                        Ircv3InboundTagSignalType.READ_MARKER,
                        request.tags().get("plugin/read-marker")))
                : List.of();
        case MESSAGE_ID ->
            List.of(
                Ircv3InboundTagSignal.of(
                    Ircv3InboundTagSignalType.MESSAGE_ID, request.tags().get("msgid")));
        default -> List.of();
      };
    }
  }

  private static final class MessageTagProvider implements Ircv3MessageTagParserProvider {

    @Override
    public String providerId() {
      return "test-message-tags";
    }

    @Override
    public Ircv3MessageTagParseResult parse(Ircv3MessageTagParseRequest request) {
      return new Ircv3MessageTagParseResult(Map.of("plugin", "raw-line"));
    }
  }

  private static final class CommandProvider implements Ircv3InboundCommandSignalProvider {

    @Override
    public String providerId() {
      return "test-monitor";
    }

    @Override
    public Set<Ircv3InboundCommandOperation> inboundCommandOperations() {
      return Set.of(
          Ircv3InboundCommandOperation.MONITOR,
          Ircv3InboundCommandOperation.ISUPPORT_MONITOR,
          Ircv3InboundCommandOperation.STANDARD_REPLY,
          Ircv3InboundCommandOperation.READ_MARKER,
          Ircv3InboundCommandOperation.MESSAGE_REDACTION);
    }

    @Override
    public List<Ircv3InboundCommandSignal> parse(
        Ircv3InboundCommandOperation operation, Ircv3InboundCommandRequest request) {
      return switch (operation) {
        case MONITOR ->
            List.of(new Ircv3InboundCommandSignal.MonitorListObserved(List.of("alice", "bob")));
        case ISUPPORT_MONITOR ->
            List.of(new Ircv3InboundCommandSignal.MonitorSupportObserved(true, 321));
        case STANDARD_REPLY ->
            List.of(
                new Ircv3InboundCommandSignal.StandardReplyObserved(
                    Ircv3InboundCommandSignal.StandardReplyKind.FAIL,
                    "PLUGIN",
                    "OVERRIDE",
                    "plugin-context",
                    "plugin description"));
        case READ_MARKER ->
            List.of(
                new Ircv3InboundCommandSignal.ReadMarkerObserved(
                    "#plugin", "timestamp=plugin-command"));
        case MESSAGE_REDACTION ->
            List.of(
                new Ircv3InboundCommandSignal.MessageRedactionObserved(
                    "#plugin", "plugin-message"));
        default -> List.of();
      };
    }
  }
}
