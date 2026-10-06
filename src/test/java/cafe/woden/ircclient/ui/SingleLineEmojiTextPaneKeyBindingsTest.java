package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;

class SingleLineEmojiTextPaneKeyBindingsTest {
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
