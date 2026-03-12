package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MessageInputPanelFocusRegressionTest {

  @Test
  void clickingInputViewportTransfersFocusToEditor() throws Exception {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    CommandHistoryStore historyStore = mock(CommandHistoryStore.class);
    FocusTrackingMessageInputPanel panel =
        new FocusTrackingMessageInputPanel(settingsBus, historyStore);

    SwingUtilities.invokeAndWait(
        () -> {
          panel.setSize(320, 72);
          panel.doLayout();

          JScrollPane scroll = findFirst(panel, JScrollPane.class);
          assertNotNull(scroll, "message input scroll pane should be present");

          JViewport viewport = scroll.getViewport();
          assertNotNull(viewport, "message input viewport should be present");

          viewport.dispatchEvent(
              new MouseEvent(
                  viewport,
                  MouseEvent.MOUSE_PRESSED,
                  System.currentTimeMillis(),
                  0,
                  Math.max(1, viewport.getWidth() - 2),
                  Math.max(1, viewport.getHeight() / 2),
                  1,
                  false,
                  MouseEvent.BUTTON1));
        });

    assertEquals(1, panel.focusRequestCount(), "viewport clicks should hand focus to the editor");
  }

  private static <T extends Component> T findFirst(Component root, Class<T> type) {
    if (root == null || type == null) return null;
    if (type.isInstance(root)) return type.cast(root);
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      T found = findFirst(child, type);
      if (found != null) return found;
    }
    return null;
  }

  private static final class FocusTrackingMessageInputPanel extends MessageInputPanel {
    private final AtomicInteger focusRequests = new AtomicInteger();

    private FocusTrackingMessageInputPanel(
        UiSettingsBus settingsBus, CommandHistoryStore historyStore) {
      super(settingsBus, historyStore);
    }

    @Override
    public void focusInput() {
      focusRequests.incrementAndGet();
    }

    private int focusRequestCount() {
      return focusRequests.get();
    }
  }
}
