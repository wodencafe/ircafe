package cafe.woden.ircclient;

import cafe.woden.ircclient.app.IrcMediator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.PushyProperties;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.ui.MainFrame;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.ui.terminal.ConsoleTeeHub;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import javax.swing.SwingUtilities;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
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
  public static void main(String[] args) {
    ConsoleTeeHub.install();

    new SpringApplicationBuilder(IrcSwingApp.class)
        .headless(false)
        .run(args);
  }

  @Bean
  public ApplicationRunner run(ObjectProvider<MainFrame> frames,
                               ObjectProvider<IrcMediator> mediatorProvider,
                               ThemeManager themeManager,
                               UiSettingsBus settingsBus,
                               TrayService trayService) {
    return args -> SwingUtilities.invokeLater(() -> {
      // Install theme before showing UI.
      String theme = settingsBus.get().theme();
      themeManager.installLookAndFeel(theme);

      MainFrame frame = frames.getObject();
      SwingUtilities.updateComponentTreeUI(frame);
      frame.invalidate();
      frame.validate();
      frame.repaint();

      trayService.installIfEnabled();

      // If we start minimized, don't show the window (tray icon becomes the entry point).
      boolean startMinimized = trayService.isTrayActive() && trayService.isEnabled() && trayService.startMinimized();
      frame.setVisible(!startMinimized);

      IrcMediator mediator = mediatorProvider.getObject();
      if (settingsBus.get().autoConnectOnStart()) {
        mediator.connectAll();
      }
    });
  }
}
