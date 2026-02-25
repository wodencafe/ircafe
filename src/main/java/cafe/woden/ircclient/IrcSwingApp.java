package cafe.woden.ircclient;

import cafe.woden.ircclient.app.IrcMediator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.PushyProperties;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.ui.MainFrame;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.terminal.ConsoleTeeHub;
import cafe.woden.ircclient.ui.tray.TrayService;
import java.awt.Desktop;
import java.awt.Frame;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(
    systemName = "IRCafe",
    sharedModules = {"config", "logging", "model", "notify", "util"})
@EnableConfigurationProperties({
  IrcProperties.class,
  UiProperties.class,
  PushyProperties.class,
  IgnoreProperties.class,
  LogProperties.class,
  SojuProperties.class,
  ZncProperties.class
})
public class IrcSwingApp {
  private static final Logger log = LoggerFactory.getLogger(IrcSwingApp.class);

  public static void main(String[] args) {
    ConsoleTeeHub.install();

    new SpringApplicationBuilder(IrcSwingApp.class).headless(false).run(args);
  }

  @Bean
  public ApplicationRunner run(
      ObjectProvider<MainFrame> frames,
      ObjectProvider<IrcMediator> mediatorProvider,
      ThemeManager themeManager,
      UiSettingsBus settingsBus,
      TrayService trayService,
      UiPort ui) {
    return args ->
        SwingUtilities.invokeLater(
            () -> {
              // Install theme before showing UI.
              String theme = settingsBus.get().theme();
              themeManager.installLookAndFeel(theme);

              MainFrame frame = frames.getObject();
              SwingUtilities.updateComponentTreeUI(frame);
              frame.invalidate();
              frame.validate();
              frame.repaint();

              trayService.installIfEnabled();
              installDesktopHandlers(frame, ui);

              // If we start minimized, don't show the window (tray icon becomes the entry point).
              boolean startMinimized =
                  trayService.isTrayActive()
                      && trayService.isEnabled()
                      && trayService.startMinimized();
              frame.setVisible(!startMinimized);

              IrcMediator mediator = mediatorProvider.getObject();
              if (settingsBus.get().autoConnectOnStart()) {
                mediator.connectAll();
              }
            });
  }

  private static void installDesktopHandlers(MainFrame frame, UiPort ui) {
    if (!Desktop.isDesktopSupported()) return;
    Desktop desktop = Desktop.getDesktop();

    try {
      desktop.addAppEventListener(
          (java.awt.desktop.AppReopenedListener)
              e -> SwingUtilities.invokeLater(() -> focusMainWindow(frame)));
    } catch (Exception e) {
      log.debug("[ircafe] app reopen listener unavailable", e);
    }

    try {
      desktop.setOpenURIHandler(
          e -> SwingUtilities.invokeLater(() -> routeOpenUri(frame, ui, e.getURI())));
    } catch (Exception e) {
      log.debug("[ircafe] open-uri handler unavailable", e);
    }
  }

  private static void routeOpenUri(MainFrame frame, UiPort ui, URI uri) {
    focusMainWindow(frame);

    TargetRef target = parseFocusTarget(uri);
    if (target == null) return;

    try {
      ui.ensureTargetExists(target);
      ui.selectTarget(target);
    } catch (Exception e) {
      log.debug("[ircafe] failed to route URI {}", uri, e);
    }
  }

  private static TargetRef parseFocusTarget(URI uri) {
    if (uri == null || !"focus".equals(uri.getHost())) return null;
    String path = uri.getPath();
    if (path == null || path.isBlank()) return null;

    String[] segments = path.split("/", 4);
    if (segments.length < 3) return null;

    String serverId = segments[1].trim();
    String encodedTarget = segments[2].trim();
    if (serverId.isEmpty() || encodedTarget.isEmpty()) return null;

    try {
      String targetName = URLDecoder.decode(encodedTarget, StandardCharsets.UTF_8);
      if (targetName.isBlank()) return null;
      return new TargetRef(serverId, targetName);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void focusMainWindow(MainFrame frame) {
    frame.setVisible(true);
    frame.setState(Frame.NORMAL);
    frame.toFront();
  }
}
