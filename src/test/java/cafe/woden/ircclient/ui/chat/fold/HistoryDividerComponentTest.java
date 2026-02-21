package cafe.woden.ircclient.ui.chat.fold;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class HistoryDividerComponentTest {

  @Test
  void constructorIsSafeDuringUiInitialization() throws Exception {
    onEdt(() -> assertDoesNotThrow(() -> new HistoryDividerComponent("History")));
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}
