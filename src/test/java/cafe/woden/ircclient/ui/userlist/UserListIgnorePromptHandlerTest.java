package cafe.woden.ircclient.ui.userlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import java.awt.Component;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class UserListIgnorePromptHandlerTest {

  @Test
  void promptAddsSoftIgnoreAndShowsConfirmationMessage() {
    IgnoreListService ignoreListService = mock(IgnoreListService.class);
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    when(ignoreStatusService.bestSeedForMask("libera", "alice", "alice!u@h"))
        .thenReturn("alice!u@h");
    when(ignoreListService.addSoftMask("libera", "bad!*@*")).thenReturn(true);

    RecordingDialogs dialogs = new RecordingDialogs("bad!*@*");
    UserListIgnorePromptHandler handler =
        new UserListIgnorePromptHandler(ignoreListService, ignoreStatusService, dialogs);

    boolean handled =
        handler.prompt(
            new JPanel(),
            new TargetRef("libera", "#ircafe"),
            new NickInfo("alice", "", "alice!u@h"),
            "alice",
            false,
            true);

    assertTrue(handled);
    verify(ignoreListService).addSoftMask("libera", "bad!*@*");
    assertEquals("Soft Ignore", dialogs.promptTitle);
    assertEquals("Add soft-ignore mask (per-server):", dialogs.promptText);
    assertEquals("alice!u@h", dialogs.promptSeed);
    assertEquals("Soft Ignore", dialogs.infoTitle);
    assertEquals("Soft ignoring: bad!*@*", dialogs.infoMessage);
  }

  @Test
  void promptReturnsFalseWhenUserCancels() {
    IgnoreListService ignoreListService = mock(IgnoreListService.class);
    IgnoreStatusService ignoreStatusService = mock(IgnoreStatusService.class);
    RecordingDialogs dialogs = new RecordingDialogs(null);
    UserListIgnorePromptHandler handler =
        new UserListIgnorePromptHandler(ignoreListService, ignoreStatusService, dialogs);

    boolean handled =
        handler.prompt(
            new JPanel(),
            new TargetRef("libera", "#ircafe"),
            new NickInfo("alice", "", "alice!u@h"),
            "alice",
            false,
            false);

    assertFalse(handled);
    verifyNoInteractions(ignoreListService);
    assertNull(dialogs.infoTitle);
    assertNull(dialogs.infoMessage);
  }

  private static final class RecordingDialogs implements UserListIgnorePromptHandler.Dialogs {
    private final String inputToReturn;
    private String promptText;
    private String promptTitle;
    private String promptSeed;
    private String infoMessage;
    private String infoTitle;

    private RecordingDialogs(String inputToReturn) {
      this.inputToReturn = inputToReturn;
    }

    @Override
    public String showInput(Component parent, String prompt, String title, String seed) {
      this.promptText = prompt;
      this.promptTitle = title;
      this.promptSeed = seed;
      return inputToReturn;
    }

    @Override
    public void showInfo(Component parent, String message, String title) {
      this.infoMessage = message;
      this.infoTitle = title;
    }
  }
}
