package cafe.woden.ircclient.ui.chat.fold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.PresenceEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PresenceFoldComponentTest {

  @Test
  void summaryMarkerIsAsciiAndNickDetailsUseReadableText() throws Exception {
    PresenceFoldComponent component =
        onEdt(
            () ->
                new PresenceFoldComponent(
                    List.of(
                        new PresenceFoldComponent.Entry(
                            "[20:55:43] ", PresenceEvent.nick("potter", "potter_")))));

    JLabel summary = summaryLabel(component);
    JPanel details = detailsPanel(component);

    assertEquals("> 1 nick change", summary.getText());
    assertFalse(details.isVisible());

    JLabel detailLine = (JLabel) details.getComponent(0);
    assertTrue(detailLine.getText().contains("potter is now known as potter_"));

    click(summary);

    assertEquals("v 1 nick change", summary.getText());
    assertTrue(details.isVisible());
  }

  private static JLabel summaryLabel(PresenceFoldComponent component) {
    return (JLabel) component.getComponent(0);
  }

  private static JPanel detailsPanel(PresenceFoldComponent component) {
    return (JPanel) component.getComponent(1);
  }

  private static void click(JLabel label) {
    MouseEvent click =
        new MouseEvent(
            label,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            0,
            4,
            4,
            1,
            false,
            MouseEvent.BUTTON1);
    for (MouseListener listener : label.getMouseListeners()) {
      listener.mouseClicked(click);
    }
  }

  private static <T> T onEdt(ThrowingSupplier<T> supplier)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }
    final Object[] holder = new Object[1];
    SwingUtilities.invokeAndWait(() -> holder[0] = supplier.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get();
  }
}
