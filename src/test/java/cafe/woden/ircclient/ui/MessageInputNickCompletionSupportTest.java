package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class MessageInputNickCompletionSupportTest {

  @Test
  void armsPendingSuffixWhenTabOnlyExpandsSharedNickPrefix() throws Exception {
    MessageInputNickCompletionSupport support = newSupport(List.of("alice", "alina"));

    assertTrue(shouldArmPendingSuffix(support, "a", 1, "al", 2));
  }

  @Test
  void doesNotArmPendingSuffixWhenCompletionAlreadyResolvedToKnownNick() throws Exception {
    MessageInputNickCompletionSupport support = newSupport(List.of("alice", "alina"));

    assertFalse(shouldArmPendingSuffix(support, "ali", 3, "alice ", 6));
  }

  @Test
  void doesNotArmPendingSuffixWhenCaretIsOutsideFirstWord() throws Exception {
    MessageInputNickCompletionSupport support = newSupport(List.of("alice", "alina"));

    assertFalse(shouldArmPendingSuffix(support, "hello al", 8, "hello al", 8));
  }

  private static MessageInputNickCompletionSupport newSupport(List<String> nicks) {
    JTextField input = new JTextField();
    MessageInputUndoSupport undoSupport = new MessageInputUndoSupport(input, () -> false);
    MessageInputNickCompletionSupport support =
        new MessageInputNickCompletionSupport(new JPanel(), input, undoSupport);
    support.setNickCompletions(nicks);
    return support;
  }

  private static boolean shouldArmPendingSuffix(
      MessageInputNickCompletionSupport support,
      String beforeText,
      int beforeCaret,
      String afterText,
      int afterCaret)
      throws Exception {
    Method method =
        MessageInputNickCompletionSupport.class.getDeclaredMethod(
            "shouldArmPendingNickAddressSuffix", String.class, int.class, String.class, int.class);
    method.setAccessible(true);
    return (boolean) method.invoke(support, beforeText, beforeCaret, afterText, afterCaret);
  }
}
