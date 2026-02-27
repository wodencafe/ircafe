package cafe.woden.ircclient.ui.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class IgnoresPanelTest {

  @Test
  void setServerIdUpdatesDisplayedServerLabel() throws Exception {
    IgnoresPanel panel = onEdtCall(IgnoresPanel::new);

    onEdt(() -> panel.setServerId("  libera  "));

    JLabel label = findLabelStartingWith(panel, "Server:");
    assertNotNull(label);
    assertEquals("Server: libera", onEdtCall(label::getText));
  }

  @Test
  void openButtonInvokesHandlerWithNormalizedServerId() throws Exception {
    IgnoresPanel panel = onEdtCall(IgnoresPanel::new);
    AtomicReference<String> openedFor = new AtomicReference<>("");

    onEdt(
        () -> {
          panel.setOnOpenIgnoreDialog(openedFor::set);
          panel.setServerId("  libera  ");
        });

    JButton openButton = findButtonByText(panel, "Open Ignore Lists...");
    assertNotNull(openButton);
    onEdt(openButton::doClick);

    assertEquals("libera", openedFor.get());
  }

  @Test
  void openButtonDoesNothingWhenServerIdIsBlank() throws Exception {
    IgnoresPanel panel = onEdtCall(IgnoresPanel::new);
    AtomicReference<String> openedFor = new AtomicReference<>("unset");

    onEdt(
        () -> {
          panel.setOnOpenIgnoreDialog(openedFor::set);
          panel.setServerId(" ");
        });

    JButton openButton = findButtonByText(panel, "Open Ignore Lists...");
    assertNotNull(openButton);
    onEdt(openButton::doClick);

    assertEquals("unset", openedFor.get());
  }

  @Test
  void openButtonIsDisabledForBlankOrMalformedServerId() throws Exception {
    IgnoresPanel panel = onEdtCall(IgnoresPanel::new);
    JButton openButton = findButtonByText(panel, "Open Ignore Lists...");
    assertNotNull(openButton);

    onEdt(() -> panel.setServerId(" "));
    assertFalse(onEdtCall(openButton::isEnabled));

    onEdt(() -> panel.setServerId("bad id"));
    assertFalse(onEdtCall(openButton::isEnabled));

    onEdt(() -> panel.setServerId("soju:main/libera"));
    assertTrue(onEdtCall(openButton::isEnabled));
  }

  @Test
  void openButtonDoesNothingWhenServerIdIsMalformed() throws Exception {
    IgnoresPanel panel = onEdtCall(IgnoresPanel::new);
    AtomicReference<String> openedFor = new AtomicReference<>("unset");

    onEdt(
        () -> {
          panel.setOnOpenIgnoreDialog(openedFor::set);
          panel.setServerId("bad id");
        });

    JButton openButton = findButtonByText(panel, "Open Ignore Lists...");
    assertNotNull(openButton);
    onEdt(openButton::doClick);

    assertEquals("unset", openedFor.get());
  }

  private static JButton findButtonByText(Component root, String text)
      throws InvocationTargetException, InterruptedException {
    return onEdtCall(() -> findByText(root, text, JButton.class));
  }

  private static JLabel findLabelStartingWith(Component root, String prefix)
      throws InvocationTargetException, InterruptedException {
    return onEdtCall(() -> findLabelPrefix(root, prefix));
  }

  private static JLabel findLabelPrefix(Component root, String prefix) {
    if (root == null || prefix == null) return null;
    if (root instanceof JLabel label
        && label.getText() != null
        && label.getText().startsWith(prefix)) {
      return label;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JLabel found = findLabelPrefix(child, prefix);
      if (found != null) return found;
    }
    return null;
  }

  private static <T extends Component> T findByText(Component root, String text, Class<T> type) {
    if (root == null || text == null || type == null) return null;
    if (type.isInstance(root) && root instanceof javax.swing.AbstractButton button) {
      if (text.equals(button.getText())) return type.cast(root);
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      T found = findByText(child, text, type);
      if (found != null) return found;
    }
    return null;
  }

  private static void onEdt(ThrowingRunnable runnable)
      throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      try {
        runnable.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
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
    AtomicReference<T> out = new AtomicReference<>();
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
