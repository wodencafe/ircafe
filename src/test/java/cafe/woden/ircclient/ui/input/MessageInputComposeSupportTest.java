package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class MessageInputComposeSupportTest {

  @Test
  void beginReplyComposeShowsPreviewAndRunsJumpAction() {
    AtomicInteger focusCalls = new AtomicInteger();
    MessageInputComposeSupport support = newComposeSupport(focusCalls);
    AtomicInteger jumpCalls = new AtomicInteger();

    support.beginReplyCompose(
        "#ircafe", "msg-123", "alice: original line for preview", jumpCalls::incrementAndGet);

    JComponent banner = support.banner();
    JLabel label = findLabel(banner);
    JButton jump = findButton(banner, "Jump");
    assertTrue(banner.isVisible());
    assertTrue(label.getText().contains("alice: original line for preview"));
    assertTrue(jump.isVisible());

    jump.doClick();

    assertTrue(jumpCalls.get() > 0);
    assertTrue(focusCalls.get() > 0);
  }

  @Test
  void beginReplyComposeWithoutJumpHidesJumpButton() {
    MessageInputComposeSupport support = newComposeSupport(new AtomicInteger());
    support.beginReplyCompose("#ircafe", "msg-123");

    JComponent banner = support.banner();
    JButton jump = findButton(banner, "Jump");

    assertTrue(banner.isVisible());
    assertFalse(jump.isVisible());
  }

  @Test
  void emitQuickReactionUsesConfiguredResolver() {
    AtomicReference<String> outbound = new AtomicReference<>();
    MessageInputComposeSupport support = newComposeSupport(new AtomicInteger(), outbound);
    support.setQuickReactionCommandResolver(
        (target, messageId, reactionToken) -> "/unreact " + messageId + " " + reactionToken);

    support.emitQuickReaction("#ircafe", "msg-123", ":+1:");

    assertEquals("/unreact msg-123 :+1:", outbound.get());
  }

  private static MessageInputComposeSupport newComposeSupport(AtomicInteger focusCalls) {
    return newComposeSupport(focusCalls, new AtomicReference<>());
  }

  private static MessageInputComposeSupport newComposeSupport(
      AtomicInteger focusCalls, AtomicReference<String> outbound) {
    return new MessageInputComposeSupport(
        new JPanel(),
        new JPanel(),
        new JTextField(),
        new JButton(),
        new MessageInputUiHooks() {
          @Override
          public void updateHint() {}

          @Override
          public void markCompletionUiDirty() {}

          @Override
          public void runProgrammaticEdit(Runnable r) {
            if (r != null) r.run();
          }

          @Override
          public void focusInput() {
            focusCalls.incrementAndGet();
          }

          @Override
          public void flushTypingDone() {}

          @Override
          public void fireDraftChanged() {}

          @Override
          public void sendOutbound(String line) {
            outbound.set(line);
          }
        });
  }

  private static JLabel findLabel(Container root) {
    JLabel label = findLabelOrNull(root);
    if (label == null) fail("expected banner label");
    return label;
  }

  private static JButton findButton(Container root, String text) {
    JButton button = findButtonOrNull(root, text);
    if (button == null) fail("expected button: " + text);
    return button;
  }

  private static JLabel findLabelOrNull(Container root) {
    for (Component c : root.getComponents()) {
      if (c instanceof JLabel l) return l;
      if (c instanceof Container nested) {
        JLabel found = findLabelOrNull(nested);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static JButton findButtonOrNull(Container root, String text) {
    for (Component c : root.getComponents()) {
      if (c instanceof JButton b && text.equals(b.getText())) return b;
      if (c instanceof Container nested) {
        JButton found = findButtonOrNull(nested, text);
        if (found != null) return found;
      }
    }
    return null;
  }
}
