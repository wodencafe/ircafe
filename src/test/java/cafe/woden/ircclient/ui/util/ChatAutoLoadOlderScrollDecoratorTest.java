package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ChatAutoLoadOlderScrollDecoratorTest {

  @Test
  void upwardWheelAtTopDebouncesLoadRequests() throws Exception {
    AtomicInteger loads = new AtomicInteger();

    onEdt(
        () -> {
          JTextPane transcript = new JTextPane();
          JScrollPane scroll = new JScrollPane(transcript);

          LoadOlderMessagesComponent control = new LoadOlderMessagesComponent();
          control.setState(LoadOlderMessagesComponent.State.READY);
          control.setOnLoadRequested(
              () -> {
                loads.incrementAndGet();
                return true;
              });

          JPanel root = new JPanel();
          root.add(control);

          ChatAutoLoadOlderScrollDecorator decorator =
              ChatAutoLoadOlderScrollDecorator.decorate(scroll, root);
          try {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setMaximum(200);
            bar.setVisibleAmount(20);
            bar.setValue(0);

            fireWheel(scroll, -1);
            fireWheel(scroll, -1);

            assertEquals(1, loads.get(), "second immediate wheel-up should be debounced");
          } finally {
            if (decorator != null) {
              decorator.close();
            }
          }
        });
  }

  @Test
  void doesNotTriggerWhenNotAtTopOrControlNotReady() throws Exception {
    AtomicInteger loads = new AtomicInteger();

    onEdt(
        () -> {
          JTextPane transcript = new JTextPane();
          JScrollPane scroll = new JScrollPane(transcript);

          LoadOlderMessagesComponent control = new LoadOlderMessagesComponent();
          control.setOnLoadRequested(
              () -> {
                loads.incrementAndGet();
                return true;
              });
          control.setState(LoadOlderMessagesComponent.State.READY);

          JPanel root = new JPanel();
          root.add(control);

          ChatAutoLoadOlderScrollDecorator decorator =
              ChatAutoLoadOlderScrollDecorator.decorate(scroll, root);
          try {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setMaximum(200);
            bar.setVisibleAmount(20);
            bar.setValue(25);

            fireWheel(scroll, -1);
            assertEquals(0, loads.get(), "wheel-up away from top should not auto-load");

            bar.setValue(0);
            control.setState(LoadOlderMessagesComponent.State.LOADING);
            fireWheel(scroll, -1);
            assertEquals(0, loads.get(), "non-ready control should not auto-load");
          } finally {
            if (decorator != null) {
              decorator.close();
            }
          }
        });
  }

  private static void fireWheel(JScrollPane scroll, int rotation) {
    MouseWheelEvent event =
        new MouseWheelEvent(
            scroll,
            MouseEvent.MOUSE_WHEEL,
            System.currentTimeMillis(),
            0,
            10,
            10,
            0,
            false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL,
            1,
            rotation);

    for (MouseWheelListener listener : scroll.getMouseWheelListeners()) {
      listener.mouseWheelMoved(event);
    }
  }

  private static void onEdt(Runnable task) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      task.run();
      return;
    }
    SwingUtilities.invokeAndWait(task);
  }
}
