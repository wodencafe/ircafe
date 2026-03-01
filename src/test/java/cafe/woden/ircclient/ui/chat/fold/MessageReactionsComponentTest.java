package cafe.woden.ircclient.ui.chat.fold;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class MessageReactionsComponentTest {

  @Test
  void leftClickRequestsReactForChipToken() {
    MessageReactionsComponent component = new MessageReactionsComponent();
    AtomicReference<String> requested = new AtomicReference<>();
    component.setOnReactRequested(requested::set);
    component.setReactions(Map.of(":+1:", List.of("alice")));

    JLabel chip = firstChip(component);
    MouseEvent click =
        new MouseEvent(
            chip,
            MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(),
            0,
            4,
            4,
            1,
            false,
            MouseEvent.BUTTON1);
    for (MouseListener listener : chip.getMouseListeners()) {
      listener.mouseReleased(click);
    }

    assertEquals(":+1:", requested.get());
  }

  @Test
  void rightClickRequestsUnreactForChipToken() {
    MessageReactionsComponent component = new MessageReactionsComponent();
    AtomicReference<String> requested = new AtomicReference<>();
    component.setOnUnreactRequested(requested::set);
    component.setReactions(Map.of(":heart:", List.of("alice")));

    JLabel chip = firstChip(component);
    MouseEvent click =
        new MouseEvent(
            chip,
            MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(),
            0,
            4,
            4,
            1,
            false,
            MouseEvent.BUTTON3);
    for (MouseListener listener : chip.getMouseListeners()) {
      listener.mouseReleased(click);
    }

    assertEquals(":heart:", requested.get());
  }

  private static JLabel firstChip(MessageReactionsComponent component) {
    Component child = component.getComponent(0);
    return (JLabel) child;
  }
}
