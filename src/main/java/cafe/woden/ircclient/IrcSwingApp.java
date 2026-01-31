package cafe.woden.ircclient;

import cafe.woden.ircclient.app.IrcMediator;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.ui.MainFrame;
import cafe.woden.ircclient.ui.settings.ThemeManager;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import javax.swing.SwingUtilities;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({IrcProperties.class, UiProperties.class, IgnoreProperties.class})
public class IrcSwingApp {
  public static void main(String[] args) {
    new SpringApplicationBuilder(IrcSwingApp.class)
        .headless(false)
        .run(args);
  }

  @Bean
  public ApplicationRunner run(ObjectProvider<MainFrame> frames,
                               ObjectProvider<IrcMediator> mediatorProvider,
                               ThemeManager themeManager,
                               UiSettingsBus settingsBus) {
    return args -> SwingUtilities.invokeLater(() -> {
      // Install the desired theme BEFORE creating any UI beans.
      themeManager.installLookAndFeel(settingsBus.get().theme());

      frames.getObject().setVisible(true);

      IrcMediator mediator = mediatorProvider.getObject();
      mediator.connectAll();
    });
  }
}
