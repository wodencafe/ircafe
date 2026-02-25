package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputCAP;

class MultiSaslCapHandlerTest {

  @Test
  void waitsForFinalLsWhenServerSendsContinuationMarker() throws Exception {
    MultiSaslCapHandler handler = new MultiSaslCapHandler("user", "secret", "PLAIN", false);
    PircBotX bot = mock(PircBotX.class);
    OutputCAP outputCap = mock(OutputCAP.class);
    when(bot.sendCAP()).thenReturn(outputCap);

    boolean finished = handler.handleLS(bot, ImmutableList.of("*"));

    assertFalse(finished);
    verifyNoInteractions(outputCap);
  }
}
