package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputCAP;

class BatchedEnableCapHandlerTest {

  @Test
  void requestsSupportedCapabilitiesInSingleBatch() throws Exception {
    BatchedEnableCapHandler handler =
        new BatchedEnableCapHandler(List.of("multi-prefix", "away-notify", "server-time"));
    PircBotX bot = mock(PircBotX.class);
    OutputCAP outputCap = mock(OutputCAP.class);
    when(bot.sendCAP()).thenReturn(outputCap);

    boolean finished = handler.handleLS(bot, ImmutableList.of("multi-prefix", "server-time", "sasl"));

    assertFalse(finished);
    verify(outputCap).request("multi-prefix", "server-time");

    assertFalse(handler.handleACK(bot, ImmutableList.of("multi-prefix")));
    assertTrue(handler.handleACK(bot, ImmutableList.of("server-time")));
  }

  @Test
  void finishesImmediatelyWhenNoDesiredCapabilitiesAreOffered() throws Exception {
    BatchedEnableCapHandler handler = new BatchedEnableCapHandler(List.of("away-notify", "server-time"));
    PircBotX bot = mock(PircBotX.class);
    OutputCAP outputCap = mock(OutputCAP.class);
    when(bot.sendCAP()).thenReturn(outputCap);

    boolean finished = handler.handleLS(bot, ImmutableList.of("batch", "sasl"));

    assertTrue(finished);
    verifyNoInteractions(outputCap);
  }

  @Test
  void normalizesAndDeduplicatesCapabilityTokens() throws Exception {
    BatchedEnableCapHandler handler =
        new BatchedEnableCapHandler(List.of(" away-notify ", "AWAY-NOTIFY", "batch"));
    PircBotX bot = mock(PircBotX.class);
    OutputCAP outputCap = mock(OutputCAP.class);
    when(bot.sendCAP()).thenReturn(outputCap);

    boolean finished = handler.handleLS(bot, ImmutableList.of("away-notify", "batch"));

    assertFalse(finished);
    verify(outputCap).request("away-notify", "batch");

    assertFalse(handler.handleACK(bot, ImmutableList.of(":away-notify")));
    assertTrue(handler.handleNAK(bot, ImmutableList.of("-batch")));
  }

  @Test
  void matchesValueCapabilitiesUsingCanonicalName() throws Exception {
    BatchedEnableCapHandler handler = new BatchedEnableCapHandler(List.of("sts", "multiline"));
    PircBotX bot = mock(PircBotX.class);
    OutputCAP outputCap = mock(OutputCAP.class);
    when(bot.sendCAP()).thenReturn(outputCap);

    boolean finished = handler.handleLS(
        bot,
        ImmutableList.of("sts=duration=86400,port=6697,preload", "multiline=max-bytes=4096"));

    assertFalse(finished);
    verify(outputCap).request("sts", "multiline");
    assertFalse(handler.handleACK(bot, ImmutableList.of("sts=duration=86400,port=6697,preload")));
    assertTrue(handler.handleACK(bot, ImmutableList.of("multiline=max-bytes=4096")));
  }
}
