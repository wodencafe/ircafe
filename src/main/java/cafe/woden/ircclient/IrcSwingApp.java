package cafe.woden.ircclient;

import cafe.woden.ircclient.app.api.MediatorControlPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.PushyProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.ui.settings.ThemeIdUtils;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.shell.MainFrame;
import cafe.woden.ircclient.ui.terminal.ConsoleTeeHub;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.util.VirtualThreads;
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
    sharedModules = {"config", "model", "notify", "util"})
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
    installSwingSafetyDefaults();

    new SpringApplicationBuilder(IrcSwingApp.class).headless(false).run(args);
  }

  private static void installSwingSafetyDefaults() {
    // Prevent a known Swing repaint edge-case where volatile offscreen buffers can be requested
    // with zero dimensions, which throws IllegalArgumentException on the EDT.
    if (System.getProperty("swing.volatileImageBufferEnabled") == null) {
      System.setProperty("swing.volatileImageBufferEnabled", "false");
    }
  }

  @Bean
  public ApplicationRunner run(
      ObjectProvider<MainFrame> frames,
      ObjectProvider<MediatorControlPort> mediatorProvider,
      ThemeManager themeManager,
      UiSettingsBus settingsBus,
      RuntimeConfigStore runtimeConfig,
      TrayService trayService,
      UiPort ui) {
    return args -> {
      String startupTheme = determineStartupTheme(settingsBus, runtimeConfig);
      runtimeConfig.rememberStartupThemePending(startupTheme);

      SwingUtilities.invokeLater(
          () -> {
            boolean startupCompleted = false;
            try {
              reconcileThemeSettingIfNeeded(startupTheme, settingsBus, runtimeConfig);

              // Install theme before showing UI.
              themeManager.installLookAndFeel(startupTheme);

              MainFrame frame = frames.getObject();
              SwingUtilities.updateComponentTreeUI(frame);
              frame.invalidate();
              frame.validate();
              frame.repaint();

              installDesktopHandlers(frame, ui);
              // Show the window before any optional native integrations that may block.
              frame.setVisible(true);

              boolean startMinimizedRequested =
                  trayService.isEnabled() && trayService.startMinimized();
              installTrayAsync(frame, trayService, startMinimizedRequested);

              MediatorControlPort mediator = mediatorProvider.getObject();
              if (settingsBus.get().autoConnectOnStart()) {
                mediator.connectAutoConnectOnStartServers();
              }
              startupCompleted = true;
            } finally {
              if (startupCompleted) {
                runtimeConfig.clearStartupThemePending();
              }
            }
          });
    };
  }

  private static void installTrayAsync(
      MainFrame frame, TrayService trayService, boolean startMinimizedRequested) {
    VirtualThreads.start(
        "ircafe-tray-install",
        () -> {
          try {
            trayService.installIfEnabled();
          } catch (Throwable t) {
            log.warn("[ircafe] tray install failed during startup", t);
          }

          if (!startMinimizedRequested) return;

          SwingUtilities.invokeLater(
              () -> {
                // Honor start-minimized only when tray is truly active.
                if (trayService.isEnabled() && trayService.isTrayActive()) {
                  frame.setVisible(false);
                } else {
                  log.warn(
                      "[ircafe] start-minimized requested but tray is unavailable; "
                          + "keeping main window visible");
                }
              });
        });
  }

  private static String determineStartupTheme(
      UiSettingsBus settingsBus, RuntimeConfigStore runtimeConfig) {
    var current = settingsBus != null ? settingsBus.get() : null;
    String configuredTheme =
        ThemeIdUtils.normalizeThemeId(current != null ? current.theme() : null);

    String pendingTheme = runtimeConfig.readStartupThemePending().orElse(null);
    if (pendingTheme == null || pendingTheme.isBlank()) {
      return configuredTheme;
    }
    if (!ThemeIdUtils.sameTheme(configuredTheme, pendingTheme)) {
      return configuredTheme;
    }

    String fallbackTheme = UiProperties.DEFAULT_THEME;
    log.warn(
        "[ircafe] startup recovery: theme '{}' was still pending from previous launch; "
            + "falling back to '{}'",
        configuredTheme,
        fallbackTheme);
    return fallbackTheme;
  }

  private static void reconcileThemeSettingIfNeeded(
      String startupTheme, UiSettingsBus settingsBus, RuntimeConfigStore runtimeConfig) {
    if (settingsBus == null) return;
    var current = settingsBus.get();
    if (current == null) return;
    if (ThemeIdUtils.sameTheme(current.theme(), startupTheme)) return;

    var updated = current.withTheme(startupTheme);
    settingsBus.set(updated);
    runtimeConfig.rememberUiSettings(
        updated.theme(), updated.chatFontFamily(), updated.chatFontSize());
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
