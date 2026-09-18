package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Ircv3ChannelContextRuntimeSupportTest {

  @Test
  void acceptsOneSafeChannelContextFromRuntimeProvider() {
    Ircv3ChannelContextRuntimeSupport support = support(signals("#plugin"));

    assertEquals(
        "#plugin",
        support.resolve(
            new Ircv3InboundTagRequest(
                "PRIVMSG", "alice", "me", List.of("me"), Map.of("plugin/context", "#plugin"))));
  }

  @Test
  void fallsBackForMissingUnsafeAmbiguousOrDirectMessageReroutingOutput() {
    Ircv3InboundTagRequest request =
        new Ircv3InboundTagRequest(
            "PRIVMSG", "alice", "me", List.of("me"), Map.of("plugin/context", "ignored"));

    assertEquals("alice", support(List.of()).resolve(request));
    assertEquals("alice", support(signals("bad target")).resolve(request));
    assertEquals("alice", support(signals("#one,#two")).resolve(request));
    assertEquals("alice", support(signals("mallory")).resolve(request));
    assertEquals(
        "alice",
        support(
                List.of(
                    Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.CONVERSATION_TARGET, "#one"),
                    Ircv3InboundTagSignal.of(
                        Ircv3InboundTagSignalType.CONVERSATION_TARGET, "#two")))
            .resolve(request));
  }

  @Test
  void channelContextCannotRerouteChannelMessagesOrTagOnlyMessages() {
    var support = support(signals("#redirect"));
    assertEquals(
        "#original",
        support.resolve(
            new Ircv3InboundTagRequest(
                "PRIVMSG", "alice", "#original", List.of("#original"), Map.of())));
    assertEquals(
        "alice",
        support.resolve(
            new Ircv3InboundTagRequest("TAGMSG", "alice", "me", List.of("me"), Map.of())));
    assertEquals(
        "alice",
        support.resolve(
            new Ircv3InboundTagRequest("JOIN", "alice", "me", List.of("me"), Map.of())));
    assertEquals(
        "#redirect",
        support.resolve(
            new Ircv3InboundTagRequest("NOTICE", "alice", "me", List.of("me"), Map.of())));
  }

  @Test
  void preservesSafeRawChannelFallbackWithoutProviderOutput() {
    assertEquals(
        "#ircafe",
        support(List.of())
            .resolve(
                new Ircv3InboundTagRequest(
                    "PRIVMSG", "alice", "#ircafe", List.of("#ircafe"), Map.of())));
  }

  private static Ircv3ChannelContextRuntimeSupport support(List<Ircv3InboundTagSignal> signals) {
    Ircv3InboundTagSignalProvider provider =
        new Ircv3InboundTagSignalProvider() {
          @Override
          public String providerId() {
            return "channel-context-test";
          }

          @Override
          public Set<Ircv3InboundTagOperation> inboundTagOperations() {
            return Set.of(Ircv3InboundTagOperation.CHANNEL_CONTEXT);
          }

          @Override
          public List<Ircv3InboundTagSignal> parse(
              Ircv3InboundTagOperation operation, Ircv3InboundTagRequest request) {
            return signals;
          }
        };
    return new Ircv3ChannelContextRuntimeSupport(
        Ircv3InboundTagSignalRuntimeCatalog.fromProviders(List.of(provider)));
  }

  private static List<Ircv3InboundTagSignal> signals(String target) {
    return List.of(Ircv3InboundTagSignal.of(Ircv3InboundTagSignalType.CONVERSATION_TARGET, target));
  }
}
