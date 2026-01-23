package cafe.woden.ircclient;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.UIManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import javax.swing.SwingUtilities;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IrcSwingApp {
	public static void main(String[] args) {
		// TODO: Interactive console window, someday.
		new SpringApplicationBuilder(IrcSwingApp.class)
				.headless(false)
				.run(args);
	}

	@ConditionalOnProperty(name = "ircafe.ui.enabled", havingValue = "true", matchIfMissing = true)
	@Bean
	ApplicationRunner run(ObjectProvider<MainFrame> frames, IrcClientService irc) {
		return args -> SwingUtilities.invokeLater(() -> {

			// TODO: Dynamic theme changes.
			FlatDarkLaf.setup(); // or new FlatDarculaLaf()
			UIManager.put("Component.arc", 10);
			UIManager.put("Button.arc", 10);
			UIManager.put("TextComponent.arc", 10);

			// UI beans get created on EDT
			MainFrame frame = frames.getObject();
			frame.setVisible(true);
		});
	}
}
