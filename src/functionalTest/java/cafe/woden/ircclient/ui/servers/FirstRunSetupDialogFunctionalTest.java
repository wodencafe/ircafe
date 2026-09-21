package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.FirstRunSetupConfigPort;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class FirstRunSetupDialogFunctionalTest {
  private final FirstRunSetupConfigPort config = mock(FirstRunSetupConfigPort.class);
  private FirstRunSetupDialog dialog;

  @AfterEach
  void dispose() throws Exception {
    if (dialog != null)
      onEdt(
          () -> {
            dialog.dispose();
            return null;
          });
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void skipSetupWorksFromEveryPageWithoutValidatingOrSavingEdits(int page) throws Exception {
    open();
    for (int i = 0; i < page; i++) click("setup.next");
    text(page == 0 ? "setup.port" : page == 1 ? "setup.account" : "setup.channels", "invalid");
    click("setup.skipAll");
    awaitClosed();
    verify(config).finishSetup(null);
    verifyNoMoreInteractions(config);
  }

  @Test
  void eachStepCanBeSkippedEvenWithInvalidInput() throws Exception {
    open();
    text("setup.host", "");
    text("setup.port", "invalid");
    click("setup.skipStep");
    text("setup.account", "incomplete-account");
    click("setup.skipStep");
    text("setup.channels", "invalid-channel");
    click("setup.skipStep");
    awaitClosed();

    var server = savedServer();
    assertEquals("irc.libera.chat", server.host());
    assertEquals(6697, server.port());
    assertFalse(server.sasl().enabled());
    assertTrue(server.autoJoin().isEmpty());
  }

  @Test
  void savesTheOptionalAccountAndChannels() throws Exception {
    open();
    text("setup.id", "my-network");
    text("setup.host", "irc.example.org");
    text("setup.nick", "my-nick");
    click("setup.next");
    text("setup.account", "account");
    text("setup.password", "secret");
    click("setup.next");
    text("setup.channels", "#one, #two\n#one");
    click("setup.next");
    awaitClosed();

    var server = savedServer();
    assertEquals("my-network", server.id());
    assertEquals("my-nick", server.nick());
    assertTrue(server.sasl().enabled());
    assertEquals("secret", server.sasl().password());
    assertEquals(List.of("#one", "#two"), server.autoJoin());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void escapeAndWindowCloseSkipAllSetup(boolean escape) throws Exception {
    open();
    onEdt(
        () -> {
          if (escape)
            dialog
                .getRootPane()
                .getActionMap()
                .get("skipSetup")
                .actionPerformed(new ActionEvent(dialog, ActionEvent.ACTION_PERFORMED, "escape"));
          else dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
          return null;
        });
    awaitClosed();
    verify(config).finishSetup(null);
  }

  @Test
  void persistenceRunsOffEdtAndRepeatedClicksDoNotDuplicateSaves() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              assertFalse(SwingUtilities.isEventDispatchThread());
              entered.countDown();
              assertTrue(release.await(5, TimeUnit.SECONDS));
              return null;
            })
        .when(config)
        .finishSetup(null);
    open();
    try {
      click("setup.skipAll");
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      CountDownLatch heartbeat = new CountDownLatch(1);
      SwingUtilities.invokeLater(heartbeat::countDown);
      assertTrue(heartbeat.await(2, TimeUnit.SECONDS), "EDT must stay responsive during saving");
      click("setup.skipAll");
    } finally {
      release.countDown();
    }
    awaitClosed();
    verify(config, times(1)).finishSetup(null);
  }

  @Test
  void failedSaveKeepsSkipSetupAvailable() throws Exception {
    doThrow(new IllegalStateException("disk full")).when(config).finishSetup(notNull());
    open();
    click("setup.next");
    click("setup.next");
    click("setup.next");
    await(
        () ->
            onEdt(
                () ->
                    ((JLabel) component(dialog, "setup.status"))
                        .getText()
                        .startsWith("Could not save")));
    assertTrue(onEdt(dialog::isShowing));
    click("setup.skipAll");
    awaitClosed();
    verify(config).finishSetup(null);
  }

  private void open() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");
    dialog = onEdt(() -> new FirstRunSetupDialog(null, config, null));
    SwingUtilities.invokeLater(() -> dialog.setVisible(true));
    await(() -> onEdt(dialog::isShowing));
  }

  private void click(String name) throws Exception {
    onEdt(
        () -> {
          ((JButton) component(dialog, name)).doClick();
          return null;
        });
  }

  private void text(String name, String value) throws Exception {
    onEdt(
        () -> {
          ((JTextComponent) component(dialog, name)).setText(value);
          return null;
        });
  }

  private IrcProperties.Server savedServer() {
    var capture = ArgumentCaptor.forClass(IrcProperties.Server.class);
    verify(config).finishSetup(capture.capture());
    return capture.getValue();
  }

  private void awaitClosed() throws Exception {
    await(() -> onEdt(() -> !dialog.isDisplayable()));
  }

  private static Component component(Container container, String name) {
    for (Component child : container.getComponents()) {
      if (name.equals(child.getName())) return child;
      if (child instanceof Container nested) {
        Component found = component(nested, name);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static <T> T onEdt(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    SwingUtilities.invokeLater(task);
    return task.get(5, TimeUnit.SECONDS);
  }

  private static void await(Callable<Boolean> condition) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.call()) return;
      Thread.sleep(20);
    }
    fail("Timed out waiting for Swing setup state");
  }
}
