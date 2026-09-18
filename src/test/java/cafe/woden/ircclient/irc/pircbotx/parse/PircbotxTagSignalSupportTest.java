package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3ChannelContextRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundTagSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3MessageMutationRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3ReadMarkerRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeTestFixtures.Runtime;
import cafe.woden.ircclient.irc.ircv3.Ircv3TypingRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PircbotxTagSignalSupportTest {

  @Test
  void emitsTaggedMessageSignalsForChannelTarget() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxTagSignalSupport support = support("libera", out::add);

    support.emitObservedSignals(
        Instant.parse("2026-03-22T12:00:00Z"),
        "bob",
        "#ircafe",
        "TAGMSG",
        List.of("#ircafe"),
        ImmutableMap.of(
            "typing", "active",
            "draft/reply", "abc123",
            "draft/react", ":+1",
            "draft/delete", "abc123"));

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.UserTypingObserved t
                        && "bob".equals(t.from())
                        && "#ircafe".equals(t.target())
                        && "active".equals(t.state())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.MessageReplyObserved r
                        && "bob".equals(r.from())
                        && "#ircafe".equals(r.target())
                        && "abc123".equals(r.replyToMsgId())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.MessageReactObserved r
                        && "bob".equals(r.from())
                        && "#ircafe".equals(r.target())
                        && ":+1".equals(r.reaction())
                        && "abc123".equals(r.messageId())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.MessageRedactionObserved r
                        && "bob".equals(r.from())
                        && "#ircafe".equals(r.target())
                        && "abc123".equals(r.messageId())));
  }

  @Test
  void rejectsProviderDirectMessageReroutingBeforeEmittingSignals() {
    List<ServerIrcEvent> out = new ArrayList<>();
    Ircv3InboundTagSignalProvider provider =
        new Ircv3InboundTagSignalProvider() {
          @Override
          public String providerId() {
            return "channel-context-validation-test";
          }

          @Override
          public Set<Ircv3InboundTagOperation> inboundTagOperations() {
            return Set.of(
                Ircv3InboundTagOperation.CHANNEL_CONTEXT, Ircv3InboundTagOperation.TYPING);
          }

          @Override
          public List<Ircv3InboundTagSignal> parse(
              Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
            return switch (operation) {
              case CHANNEL_CONTEXT ->
                  List.of(
                      Ircv3InboundTagSignal.of(
                          Ircv3InboundTagSignalType.CONVERSATION_TARGET, "mallory"));
              case TYPING ->
                  List.of(Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.TYPING, "active"));
              default -> List.of();
            };
          }
        };
    PircbotxTagSignalSupport support =
        support(
            "libera",
            out::add,
            Ircv3InboundTagSignalRuntimeCatalog.fromProviders(List.of(provider)));

    support.emitObservedSignals(
        Instant.parse("2026-03-22T12:10:00Z"),
        "bob",
        "me",
        "TAGMSG",
        List.of("me"),
        ImmutableMap.of("plugin/typing", "active"));

    IrcEvent.UserTypingObserved typing =
        out.stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.UserTypingObserved.class::isInstance)
            .map(IrcEvent.UserTypingObserved.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals("bob", typing.target());
  }

  @ParameterizedTest
  @ValueSource(strings = {"PRIVMSG", "NOTICE", "TAGMSG"})
  void channelContextOnlyOverridesPrivateMessagesAndTagLookupUnescapesValues(String command) {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxTagSignalSupport support = support("libera", out::add);

    support.emitObservedSignals(
        Instant.parse("2026-03-22T12:05:00Z"),
        "bob",
        "me",
        command,
        List.of("me"),
        ImmutableMap.of(
            "+draft/channel-context", "#ircafe",
            "draft/unreact", ":+1:",
            "+draft/reply", "abc123",
            "+read-marker", "timestamp=2026-03-22T12\\:05\\:00Z"));

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.MessageUnreactObserved r
                        && "bob".equals(r.from())
                        && (command.equals("TAGMSG") ? "bob" : "#ircafe").equals(r.target())
                        && ":+1:".equals(r.reaction())
                        && "abc123".equals(r.messageId())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.ReadMarkerObserved rm
                        && "bob".equals(rm.from())
                        && (command.equals("TAGMSG") ? "bob" : "#ircafe").equals(rm.target())
                        && "timestamp=2026-03-22T12;05;00Z".equals(rm.marker())));
    assertEquals(
        "abc123",
        PircbotxTagSignalSupport.firstTag(
            ImmutableMap.of("+draft/reply", "abc123"), "draft/reply", "+draft/reply"));
  }

  private static PircbotxTagSignalSupport support(String serverId, Consumer<ServerIrcEvent> sink) {
    return PircbotxParserRuntimeTestFixtures.tagSignals(serverId, sink);
  }

  private static PircbotxTagSignalSupport support(
      String serverId,
      Consumer<ServerIrcEvent> sink,
      Ircv3InboundTagSignalRuntimeCatalog inboundTagCatalog) {
    Runtime runtime = PircbotxParserRuntimeTestFixtures.runtime();
    return new PircbotxTagSignalSupport(
        serverId,
        sink,
        new Ircv3ChannelContextRuntimeSupport(inboundTagCatalog),
        new Ircv3MessageMutationRuntimeSupport(
            runtime.catalogs().messageMutations(),
            inboundTagCatalog,
            runtime.catalogs().inboundCommands()),
        new Ircv3ReadMarkerRuntimeSupport(
            runtime.catalogs().outboundCommands(),
            inboundTagCatalog,
            runtime.catalogs().inboundCommands()),
        new Ircv3TypingRuntimeSupport(
            runtime.catalogs().outboundCommands(),
            inboundTagCatalog,
            runtime.catalogs().inboundCommands()));
  }
}
