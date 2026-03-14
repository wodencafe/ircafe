package cafe.woden.ircclient.irc.pircbotx.capability;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputCAP;
import org.pircbotx.output.OutputRaw;

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

  @Test
  void authenticatePlusTerminatesBufferedServerChunkForScram() throws Exception {
    MultiSaslCapHandler handler = new MultiSaslCapHandler("user", "secret", "SCRAM-SHA-256", false);
    PircBotX bot = mock(PircBotX.class);
    OutputCAP outputCap = mock(OutputCAP.class);
    OutputRaw outputRaw = mock(OutputRaw.class);
    when(bot.sendCAP()).thenReturn(outputCap);
    when(bot.sendRaw()).thenReturn(outputRaw);

    handler.handleLS(bot, ImmutableList.of("sasl=SCRAM-SHA-256"));
    handler.handleACK(bot, ImmutableList.of("sasl=SCRAM-SHA-256"));
    handler.handleUnknown(bot, "AUTHENTICATE +");

    ArgumentCaptor<String> sentLines = ArgumentCaptor.forClass(String.class);
    verify(outputRaw, atLeastOnce()).rawLine(sentLines.capture());
    String clientFirstB64 =
        sentLines.getAllValues().stream()
            .filter(line -> line.startsWith("AUTHENTICATE "))
            .map(line -> line.substring("AUTHENTICATE ".length()))
            .filter(payload -> !"SCRAM-SHA-256".equals(payload))
            .findFirst()
            .orElseThrow();
    String clientFirst =
        new String(Base64.getDecoder().decode(clientFirstB64), StandardCharsets.UTF_8);
    String clientNonce = extractScramClientNonce(clientFirst);

    String salt = Base64.getEncoder().encodeToString("salt".getBytes(StandardCharsets.UTF_8));
    String prefix = "r=" + clientNonce + "server,s=" + salt + ",i=4096,x=";
    int paddedServerFirstLen = 300;
    String serverFirst = prefix + "a".repeat(paddedServerFirstLen - prefix.length());
    String serverFirstB64 =
        Base64.getEncoder().encodeToString(serverFirst.getBytes(StandardCharsets.UTF_8));
    assertEquals(400, serverFirstB64.length());

    clearInvocations(outputRaw);

    assertDoesNotThrow(() -> handler.handleUnknown(bot, "AUTHENTICATE " + serverFirstB64));
    verifyNoInteractions(outputRaw);

    assertDoesNotThrow(() -> handler.handleUnknown(bot, "AUTHENTICATE +"));
    verify(outputRaw, atLeastOnce()).rawLine(startsWith("AUTHENTICATE "));
  }

  private static String extractScramClientNonce(String clientFirst) {
    for (String part : clientFirst.split(",")) {
      if (part.startsWith("r=")) {
        return part.substring(2);
      }
    }
    throw new IllegalStateException("Missing SCRAM client nonce in client-first message");
  }
}
