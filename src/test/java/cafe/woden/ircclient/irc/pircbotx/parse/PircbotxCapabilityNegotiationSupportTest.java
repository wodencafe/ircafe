package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.pircbotx.capability.BatchedEnableCapHandler;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import com.google.common.collect.ImmutableList;
import java.util.List;
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
        new PircbotxCapabilityNegotiationSupport(
            bot,
            "libera",
            conn,
            (ServerIrcEvent ignored) -> {},
            new PircbotxCapabilityStateSupport("libera", conn));

    support.observe(
        ParsedCapLine.parse("LS", ":message-tags batch draft/chathistory"),
        List.of(pendingHandler));

    verifyNoInteractions(outputCap);
  }
}
