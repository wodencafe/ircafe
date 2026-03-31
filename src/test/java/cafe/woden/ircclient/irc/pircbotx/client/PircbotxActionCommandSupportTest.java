package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputIRC;

class PircbotxActionCommandSupportTest {

  private final PircbotxActionCommandSupport support = new PircbotxActionCommandSupport();

  @Test
  void sendActionUsesNativeActionWhenAvailable() throws Exception {
    PircBotX bot = mock(PircBotX.class);
    RecordingOutputIrc outputIrc = new RecordingOutputIrc(bot);
    when(bot.sendIRC()).thenReturn(outputIrc);

    support.sendAction("libera", bot, "#ircafe", "waves");

    assertEquals("#ircafe", outputIrc.actionTarget);
    assertEquals("waves", outputIrc.actionMessage);
    assertEquals(null, outputIrc.ctcpFallbackTarget);
  }

  @Test
  void sendActionFallsBackToCtcpWhenNativeActionThrows() throws Exception {
    PircBotX bot = mock(PircBotX.class);
    ThrowingActionOutputIrc outputIrc = new ThrowingActionOutputIrc(bot);
    when(bot.sendIRC()).thenReturn(outputIrc);

    support.sendAction("libera", bot, "alice", "waves");

    assertEquals("alice", outputIrc.ctcpFallbackTarget);
    assertEquals("\u0001ACTION waves\u0001", outputIrc.ctcpFallbackMessage);
  }

  @Test
  void sendActionRequiresNonBlankTarget() {
    PircBotX bot = mock(PircBotX.class);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> support.sendAction("libera", bot, " ", "waves"));

    assertEquals("target is blank", ex.getMessage());
  }

  private static class RecordingOutputIrc extends OutputIRC {
    String actionTarget;
    String actionMessage;
    String ctcpFallbackTarget;
    String ctcpFallbackMessage;

    RecordingOutputIrc(PircBotX bot) {
      super(bot);
    }

    @Override
    public void action(String target, String action) {
      this.actionTarget = target;
      this.actionMessage = action;
    }

    @Override
    public void message(String target, String message) {
      this.ctcpFallbackTarget = target;
      this.ctcpFallbackMessage = message;
    }
  }

  private static final class ThrowingActionOutputIrc extends RecordingOutputIrc {

    ThrowingActionOutputIrc(PircBotX bot) {
      super(bot);
    }

    @Override
    public void action(String target, String action) {
      throw new IllegalStateException("boom");
    }
  }
}
