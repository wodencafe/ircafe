package cafe.woden.ircclient.ui.util;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;

public final class DialogCloseableScopeDecorator {

  private DialogCloseableScopeDecorator() {}

  public static CloseableScope install(JDialog dialog) {
    CloseableScope scope = new CloseableScope();
    if (dialog == null) return scope;

    WindowAdapter adapter = new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        scope.closeQuietly();
      }

      @Override
      public void windowClosed(WindowEvent e) {
        scope.closeQuietly();
      }
    };

    dialog.addWindowListener(adapter);

    // Ensure listener removal happens even if scope is closed manually.
    scope.addCleanup(() -> dialog.removeWindowListener(adapter));

    return scope;
  }
}
