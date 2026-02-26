package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.ApplicationShutdownCoordinator;
import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.diagnostics.RuntimeJfrService;
import cafe.woden.ircclient.ui.ignore.IgnoreListDialog;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.settings.MemoryUsageDisplayMode;
import cafe.woden.ircclient.ui.settings.PreferencesDialog;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.settings.ThemeSelectionDialog;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class AppMenuBarMemoryWidgetTest {

  @Test
  void longModeShowsGibUnits() throws Exception {
    onEdt(
        () -> {
          AppMenuBar menuBar = newMenuBar();
          setMode(menuBar, MemoryUsageDisplayMode.LONG);

          JButton memoryButton = (JButton) getField(menuBar, "memoryButton");
          String text = memoryButton.getText();
          assertTrue(text.contains("GiB"), "long mode should show memory in GiB");
        });
  }

  @Test
  void shortModeUsesPercentBadgeWithoutEllipsis() throws Exception {
    onEdt(
        () -> {
          AppMenuBar menuBar = newMenuBar();
          setMode(menuBar, MemoryUsageDisplayMode.SHORT);

          JButton memoryButton = (JButton) getField(menuBar, "memoryButton");
          assertTrue(memoryButton.isVisible());
          assertTrue(
              memoryButton.getText().matches("\\d+%|n/a"),
              "short mode should render as a compact percent badge");
          assertNotEquals("...", memoryButton.getText());

          Dimension pref = memoryButton.getPreferredSize();
          assertTrue(pref.height >= 20, "short mode badge should keep menu-bar height");
          assertTrue(pref.width >= 56, "short mode badge should remain legible");
        });
  }

  @Test
  void indicatorModeKeepsProgressBarHeightReadable() throws Exception {
    onEdt(
        () -> {
          AppMenuBar menuBar = newMenuBar();
          setMode(menuBar, MemoryUsageDisplayMode.INDICATOR);

          JProgressBar indicator = (JProgressBar) getField(menuBar, "memoryIndicator");
          assertTrue(indicator.isVisible());
          Dimension pref = indicator.getPreferredSize();
          assertTrue(pref.height >= 18, "indicator mode should not collapse vertically");
          assertTrue(pref.width >= 86, "indicator mode should keep percentage text readable");
        });
  }

  @Test
  void memoryDialogIncludesDialGaugeComponent() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI needs a display");
    onEdt(
        () -> {
          AppMenuBar menuBar = newMenuBar();
          invokeNoArgs(menuBar, "createMemoryDialog");
          JComponent dial = (JComponent) getField(menuBar, "memoryDialogDialGauge");
          assertNotNull(dial, "memory dialog should include a dial-style gauge");
        });
  }

  private static AppMenuBar newMenuBar() {
    PreferencesDialog preferencesDialog = mock(PreferencesDialog.class);
    NickColorOverridesDialog nickColorOverridesDialog = mock(NickColorOverridesDialog.class);
    IgnoreListDialog ignoreListDialog = mock(IgnoreListDialog.class);
    ThemeSelectionDialog themeSelectionDialog = mock(ThemeSelectionDialog.class);
    ThemeManager themeManager = mock(ThemeManager.class);
    when(themeManager.featuredThemes()).thenReturn(new ThemeManager.ThemeOption[0]);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    RuntimeJfrService runtimeJfrService = mock(RuntimeJfrService.class);
    ServerDialogs serverDialogs = mock(ServerDialogs.class);
    ChatDockable chat = mock(ChatDockable.class);
    ServerTreeDockable serverTree = mock(ServerTreeDockable.class);
    UserListDockable users = mock(UserListDockable.class);
    ActiveInputRouter activeInputRouter = new ActiveInputRouter();
    ActiveTargetPort targetCoordinator = mock(ActiveTargetPort.class);
    ApplicationShutdownCoordinator shutdownCoordinator = mock(ApplicationShutdownCoordinator.class);

    AbstractAction noopAction =
        new AbstractAction("noop") {
          @Override
          public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
    when(serverTree.moveNodeUpAction()).thenReturn(noopAction);
    when(serverTree.moveNodeDownAction()).thenReturn(noopAction);
    when(serverTree.closeNodeAction()).thenReturn(noopAction);

    return new AppMenuBar(
        preferencesDialog,
        nickColorOverridesDialog,
        ignoreListDialog,
        themeSelectionDialog,
        themeManager,
        settingsBus,
        null,
        null,
        null,
        null,
        runtimeJfrService,
        serverDialogs,
        null,
        chat,
        serverTree,
        users,
        activeInputRouter,
        targetCoordinator,
        shutdownCoordinator);
  }

  private static void setMode(AppMenuBar menuBar, MemoryUsageDisplayMode mode) throws Exception {
    Method m =
        AppMenuBar.class.getDeclaredMethod(
            "setMemoryUsageDisplayModeFromUi", MemoryUsageDisplayMode.class);
    m.setAccessible(true);
    m.invoke(menuBar, mode);
  }

  private static void invokeNoArgs(AppMenuBar menuBar, String methodName) throws Exception {
    Method m = AppMenuBar.class.getDeclaredMethod(methodName);
    m.setAccessible(true);
    m.invoke(menuBar);
  }

  private static Object getField(AppMenuBar menuBar, String fieldName) throws Exception {
    Field field = AppMenuBar.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(menuBar);
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

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
