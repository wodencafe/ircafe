package cafe.woden.ircclient.ui.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class IgnoresPanelFunctionalTest {

  @Test
  void panelRoutesOpenActionForValidServerAndRejectsMalformedInput() throws Exception {
    IgnoresPanel panel = onEdtCall(IgnoresPanel::new);
    List<String> opened = new ArrayList<>();
    onEdt(() -> panel.setOnOpenIgnoreDialog(opened::add));

    JButton openButton = onEdtCall(() -> findButtonByText(panel, "Open Ignore Lists..."));
    assertNotNull(openButton);
    assertFalse(onEdtCall(openButton::isEnabled));

    onEdt(() -> panel.setServerId("bad id"));
    assertFalse(onEdtCall(openButton::isEnabled));
    onEdt(openButton::doClick);
    assertEquals(List.of(), opened);

    onEdt(() -> panel.setServerId("libera"));
    assertTrue(onEdtCall(openButton::isEnabled));
    onEdt(openButton::doClick);
    assertEquals(List.of("libera"), opened);

    onEdt(() -> panel.setServerId("oftc"));
    onEdt(openButton::doClick);
    assertEquals(List.of("libera", "oftc"), opened);
  }

  private static JButton findButtonByText(Component root, String text) {
    if (root == null || text == null) return null;
    if (root instanceof JButton button && text.equals(button.getText())) {
      return button;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JButton found = findButtonByText(child, text);
      if (found != null) return found;
    }
    return null;
  }

  private static void onEdt(ThrowingRunnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        r.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            r.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        return supplier.get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    java.util.concurrent.atomic.AtomicReference<T> out = new java.util.concurrent.atomic.AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return out.get();
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}

