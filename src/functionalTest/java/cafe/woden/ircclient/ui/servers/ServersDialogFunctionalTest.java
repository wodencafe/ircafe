package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServersDialogFunctionalTest {

  @TempDir Path tempDir;

  @Test
  void addEditAndRemoveServerWorkflowsUpdateRegistry() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");

    IrcProperties initial = new IrcProperties(null, List.of(server("libera", "irc.libera.chat")));
    RuntimeConfigStore runtimeConfig =
        new RuntimeConfigStore(tempDir.resolve("ircafe.yml").toString(), initial);
    ServerRegistry registry = new ServerRegistry(initial, runtimeConfig);

    ServersDialog dialog = onEdtCall(() -> new ServersDialog(null, registry, runtimeConfig));
    JButton addBtn = readField(dialog, "addBtn", JButton.class);
    JButton editBtn = readField(dialog, "editBtn", JButton.class);
    JButton removeBtn = readField(dialog, "removeBtn", JButton.class);
    JList<?> list = readField(dialog, "list", JList.class);

    try {
      Automation addFlow =
          automate(
              "Add Server",
              window -> fillServerEditorAndSave(window, "oftc", "irc.oftc.net", "oftcNick"));
      onEdt(addBtn::doClick);
      joinFlow(addFlow);

      waitFor(() -> registry.containsId("oftc"), Duration.ofSeconds(4));
      waitFor(() -> onEdtBoolean(() -> listContainsId(list, "oftc")), Duration.ofSeconds(4));

      onEdt(() -> selectById(list, "oftc"));
      Automation editFlow =
          automate(
              "Edit Server",
              window ->
                  fillServerEditorAndSave(window, "oftc-renamed", "irc.oftc.net", "renamedNick"));
      onEdt(editBtn::doClick);
      joinFlow(editFlow);

      waitFor(() -> registry.containsId("oftc-renamed"), Duration.ofSeconds(4));
      assertFalse(registry.containsId("oftc"));
      waitFor(
          () -> onEdtBoolean(() -> listContainsId(list, "oftc-renamed")), Duration.ofSeconds(4));

      onEdt(() -> selectById(list, "oftc-renamed"));
      Automation removeFlow = automate("Remove server", window -> clickButton(window, "OK"));
      onEdt(removeBtn::doClick);
      joinFlow(removeFlow);

      waitFor(() -> !registry.containsId("oftc-renamed"), Duration.ofSeconds(4));
    } finally {
      onEdt(dialog::dispose);
      flushEdt();
    }
  }

  private static Automation automate(String dialogTitle, WindowAction action) {
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try {
                JDialog window = waitForDialog(dialogTitle, Duration.ofSeconds(8));
                onEdt(() -> action.run(window));
              } catch (Throwable t1) {
                error.set(t1);
              }
            },
            "servers-dialog-automation-" + dialogTitle.replace(' ', '-'));
    t.setUncaughtExceptionHandler((thread, throwable) -> error.compareAndSet(null, throwable));
    t.setDaemon(true);
    t.start();
    return new Automation(t, error);
  }

  private static void joinFlow(Automation flow) throws Exception {
    flow.thread.join(Duration.ofSeconds(10));
    if (flow.thread.isAlive()) {
      throw new AssertionError("Automation thread timed out");
    }
    Throwable err = flow.error.get();
    if (err != null) {
      throw new AssertionError("Automation thread failed", err);
    }
  }

  private static void fillServerEditorAndSave(Window window, String id, String host, String nick)
      throws Exception {
    JTextField idField = readField(window, "idField", JTextField.class);
    JTextField hostField = readField(window, "hostField", JTextField.class);
    JTextField nickField = readField(window, "nickField", JTextField.class);
    JButton saveBtn = readField(window, "saveBtn", JButton.class);

    idField.setText(id);
    hostField.setText(host);
    nickField.setText(nick);
    saveBtn.doClick();
  }

  private static JDialog waitForDialog(String title, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      Window[] windows = Window.getWindows();
      for (Window window : windows) {
        if (!(window instanceof JDialog dialog)) continue;
        if (!dialog.isShowing()) continue;
        if (title.equals(dialog.getTitle())) return dialog;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("Timed out waiting for dialog: " + title);
  }

  private static void clickButton(Container root, String text) {
    JButton button = findButton(root, text);
    if (button == null) {
      throw new AssertionError("Could not find button: " + text);
    }
    button.doClick();
  }

  private static JButton findButton(Component root, String text) {
    if (root == null || text == null) return null;
    if (root instanceof JButton button && text.equals(button.getText())) {
      return button;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JButton found = findButton(child, text);
      if (found != null) return found;
    }
    return null;
  }

  private static void selectById(JList<?> list, String id) {
    for (int i = 0; i < list.getModel().getSize(); i++) {
      Object value = list.getModel().getElementAt(i);
      if (value instanceof IrcProperties.Server server && id.equals(server.id())) {
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
        return;
      }
    }
    throw new AssertionError("Server id not found in list: " + id);
  }

  private static boolean listContainsId(JList<?> list, String id) {
    for (int i = 0; i < list.getModel().getSize(); i++) {
      Object value = list.getModel().getElementAt(i);
      if (value instanceof IrcProperties.Server server && id.equals(server.id())) {
        return true;
      }
    }
    return false;
  }

  private static IrcProperties.Server server(String id, String host) {
    return new IrcProperties.Server(
        id,
        host,
        6697,
        true,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        List.of(),
        List.of(),
        null);
  }

  private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static void onEdt(ThrowingRunnable runnable) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
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

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
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

  private static boolean onEdtBoolean(ThrowingBooleanSupplier supplier) {
    try {
      return onEdtCall(supplier::getAsBoolean);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  private interface WindowAction {
    void run(JDialog dialog) throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }

  private record Automation(Thread thread, AtomicReference<Throwable> error) {}
}
