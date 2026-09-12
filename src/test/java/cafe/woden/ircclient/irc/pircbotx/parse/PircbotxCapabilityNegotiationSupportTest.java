package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityLine;
import cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityNegotiationRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.Ircv3InboundCommandSignalRuntimeCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3ZncPlaybackRuntimeSupport;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundCommandSignalProvider;
import cafe.woden.ircclient.irc.pircbotx.capability.BatchedEnableCapHandler;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputCAP;

class PircbotxCapabilityNegotiationSupportTest {

  @Test
  void doesNotSendFallbackCapReqWhenBatchedHandlerAlreadyHasPendingRequests() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputCAP outputCap = mock(OutputCAP.class);
    when(bot.sendCAP()).thenReturn(outputCap);

    BatchedEnableCapHandler pendingHandler =
        new BatchedEnableCapHandler(List.of("message-tags", "batch", "draft/chathistory"));
    pendingHandler.handleLS(bot, ImmutableList.of("message-tags", "batch", "draft/chathistory"));
    clearInvocations(outputCap);

    PircbotxCapabilityNegotiationSupport support =
        PircbotxParserRuntimeTestFixtures.capabilityNegotiation(
            bot,
            "libera",
            conn,
            (ServerIrcEvent ignored) -> {},
            new PircbotxCapabilityStateSupport("libera", conn));

    support.observe(
        Ircv3CapabilityLine.parse("LS", ":message-tags batch draft/chathistory"),
        List.of(pendingHandler));

    verifyNoInteractions(outputCap);
  }

  @Test
  void installedProviderCanOverrideFallbackPlanningWithoutOwningTransport() throws Exception {
    PircbotxConnectionState conn = new PircbotxConnectionState("libera");
    PircBotX bot = mock(PircBotX.class);
    OutputCAP outputCap = mock(OutputCAP.class);
    when(bot.sendCAP()).thenReturn(outputCap);
    Ircv3InboundCommandSignalProvider provider =
        new Ircv3InboundCommandSignalProvider() {
          @Override
          public String providerId() {
            return "fallback-override";
          }

          @Override
          public Set<Ircv3InboundCommandOperation> inboundCommandOperations() {
            return Set.of(Ircv3InboundCommandOperation.CAP_NEGOTIATION);
          }

          @Override
          public List<Ircv3InboundCommandSignal> parse(
              Ircv3InboundCommandOperation operation, Ircv3InboundCommandRequest request) {
            return List.of(
                new Ircv3InboundCommandSignal.CapabilityFallbackPlanned(false, true, ""));
          }
        };

    Ircv3InboundCommandSignalRuntimeCatalog commandCatalog =
        Ircv3InboundCommandSignalRuntimeCatalog.fromProviders(List.of(provider));
    PircbotxCapabilityNegotiationSupport support =
        new PircbotxCapabilityNegotiationSupport(
            bot,
            "libera",
            conn,
            (ServerIrcEvent ignored) -> {},
            new PircbotxCapabilityStateSupport("libera", conn),
            new Ircv3CapabilityNegotiationRuntimeSupport(commandCatalog),
            new Ircv3ZncPlaybackRuntimeSupport(
                commandCatalog,
                PircbotxParserRuntimeTestFixtures.runtime().catalogs().inboundTags()));

    support.observe(Ircv3CapabilityLine.parse("LS", ":batch"));

    verify(outputCap).request("batch");
  }
}
