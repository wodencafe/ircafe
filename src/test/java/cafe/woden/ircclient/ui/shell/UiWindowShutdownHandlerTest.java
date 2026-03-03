package cafe.woden.ircclient.ui.shell;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.awt.Window;
import org.junit.jupiter.api.Test;

class UiWindowShutdownHandlerTest {

  @Test
  void closeWindowsAttemptsToHideAndDisposeEachWindow() {
    UiWindowShutdownHandler handler = new UiWindowShutdownHandler();
    Window first = mock(Window.class);
    Window second = mock(Window.class);

    doThrow(new RuntimeException("hide failed")).when(second).setVisible(false);
    doThrow(new RuntimeException("dispose failed")).when(second).dispose();

    handler.closeWindows(new Window[] {first, null, second});

    verify(first).setVisible(false);
    verify(first).dispose();
    verify(second).setVisible(false);
    verify(second).dispose();
  }
}
