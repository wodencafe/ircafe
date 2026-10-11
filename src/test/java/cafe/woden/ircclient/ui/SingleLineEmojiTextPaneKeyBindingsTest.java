package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SingleLineEmojiTextPaneKeyBindingsTest {
  @ParameterizedTest
  @CsvSource({
    "0, 11, LEFT, 0",
    "0, 11, RIGHT, 11",
    "2, 8, LEFT, 2",
    "8, 2, LEFT, 2",
    "2, 8, RIGHT, 8",
    "8, 2, RIGHT, 8"
  })
  void arrowCollapsesSelectionToItsEdge(int anchor, int caret, String direction, int expected)
      throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var editor = new SingleLineEmojiTextPane();
          editor.setText("hello world");
          editor.setSize(240, 32);
          editor.setCaretPosition(anchor);
          editor.moveCaretPosition(caret);
          int key = "LEFT".equals(direction) ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
          Object binding =
              editor.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
          Action action = editor.getActionMap().get(binding);
          assertNotNull(action);
          action.actionPerformed(new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, ""));
          assertEquals(expected, editor.getCaretPosition());
          assertEquals(expected, editor.getSelectionStart());
          assertEquals(expected, editor.getSelectionEnd());
          assertEquals("hello world", editor.getText());
        });
  }

  @ParameterizedTest
  @ValueSource(ints = {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT})
  void arrowWithoutSelectionUsesCurrentLookAndFeelAction(int key) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var editor = new SingleLineEmojiTextPane();
          editor.setText("hello world");
          editor.setCaretPosition(5);
          Object binding =
              editor.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
          String delegateName =
              key == KeyEvent.VK_LEFT
                  ? DefaultEditorKit.backwardAction
                  : DefaultEditorKit.forwardAction;
          for (int refresh = 0; refresh < 2; refresh++) {
            var received = new AtomicReference<ActionEvent>();
            var themeMap = new ActionMapUIResource();
            themeMap.put(
                delegateName,
                new AbstractAction() {
                  @Override
                  public void actionPerformed(ActionEvent event) {
                    received.set(event);
                  }
                });
            SwingUtilities.replaceUIActionMap(editor, themeMap);
            var event = new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "");
            editor.getActionMap().get(binding).actionPerformed(event);
            assertSame(event, received.get());
          }
        });
  }

  @Test
  void lookAndFeelAndApplicationActionsTakePrecedenceOverFallbacks() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var editor = new SingleLineEmojiTextPane();
          Action themeAction =
              new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {}
              };
          themeAction.setEnabled(false);
          var themeMap = new ActionMapUIResource();
          themeMap.put(DefaultEditorKit.deletePrevCharAction, themeAction);
          SwingUtilities.replaceUIActionMap(editor, themeMap);
          assertSame(themeAction, editor.getActionMap().get(DefaultEditorKit.deletePrevCharAction));
          assertFalse(editor.getActionMap().get(DefaultEditorKit.deletePrevCharAction).isEnabled());
          Action applicationAction =
              new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {}
              };
          editor.getActionMap().put(DefaultEditorKit.deletePrevCharAction, applicationAction);
          assertSame(
              applicationAction, editor.getActionMap().get(DefaultEditorKit.deletePrevCharAction));
        });
  }
}
