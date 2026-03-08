package cafe.woden.ircclient.ui.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class IgnoresPanelFunctionalTest {

  @Test
  void panelRoutesOpenActionForValidServerAndRejectsMalformedInput() throws Exception {
    IgnoresPanel panel = onEdtCall(IgnoresPanel::new);
    List<String> opened = new ArrayList<>();
    onEdt(
        () ->
            panel.setOnOpenIgnoreDialog(
                ref -> opened.add(ref == null ? "" : Objects.toString(ref.serverId(), ""))));

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

  @Test
  void panelShowsQualifiedNetworkInServerLabel() throws Exception {
    IgnoresPanel panel = onEdtCall(IgnoresPanel::new);

    onEdt(() -> panel.setTarget(TargetRef.ignores("quassel", "libera")));

    javax.swing.JLabel label = onEdtCall(() -> findLabelStartingWith(panel, "Server:"));
    assertNotNull(label);
    assertEquals("Server: quassel (network: libera)", onEdtCall(label::getText));
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

  private static javax.swing.JLabel findLabelStartingWith(Component root, String prefix) {
    if (root == null || prefix == null) return null;
    if (root instanceof javax.swing.JLabel label
        && label.getText() != null
        && label.getText().startsWith(prefix)) {
      return label;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      javax.swing.JLabel found = findLabelStartingWith(child, prefix);
      if (found != null) return found;
    }
    return null;
  }

  private static void onEdt(ThrowingRunnable r)
      throws InvocationTargetException, InterruptedException {
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
    java.util.concurrent.atomic.AtomicReference<T> out =
        new java.util.concurrent.atomic.AtomicReference<>();
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
