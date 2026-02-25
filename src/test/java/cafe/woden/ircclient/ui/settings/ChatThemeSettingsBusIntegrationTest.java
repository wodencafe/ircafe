package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ChatThemeSettingsBusIntegrationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(ChatThemeSettingsBusTestConfig.class);

  @TempDir Path tempDir;

  @Test
  void bindsMessageColorOverridesIntoCurrentSettings() {
    runner
        .withPropertyValues(
            "ircafe.ui.chatThemePreset=SOFT",
            "ircafe.ui.chatTimestampColor=#101010",
            "ircafe.ui.chatSystemColor=#202020",
            "ircafe.ui.chatMentionBgColor=#303030",
            "ircafe.ui.chatMentionStrength=47",
            "ircafe.ui.chatMessageColor=#404040",
            "ircafe.ui.chatNoticeColor=#505050",
            "ircafe.ui.chatActionColor=#606060",
            "ircafe.ui.chatErrorColor=#707070",
            "ircafe.ui.chatPresenceColor=#808080")
        .run(
            ctx -> {
              ChatThemeSettingsBus bus = ctx.getBean(ChatThemeSettingsBus.class);
              ChatThemeSettings settings = bus.get();

              assertEquals(ChatThemeSettings.Preset.SOFT, settings.preset());
              assertEquals("#101010", settings.timestampColor());
              assertEquals("#202020", settings.systemColor());
              assertEquals("#303030", settings.mentionBgColor());
              assertEquals(47, settings.mentionStrength());
              assertEquals("#404040", settings.messageColor());
              assertEquals("#505050", settings.noticeColor());
              assertEquals("#606060", settings.actionColor());
              assertEquals("#707070", settings.errorColor());
              assertEquals("#808080", settings.presenceColor());
            });
  }

  @Test
  void defaultsUseNullOptionalMessageColors() {
    runner.run(
        ctx -> {
          ChatThemeSettings settings = ctx.getBean(ChatThemeSettingsBus.class).get();
          assertEquals(ChatThemeSettings.Preset.DEFAULT, settings.preset());
          assertNull(settings.messageColor());
          assertNull(settings.noticeColor());
          assertNull(settings.actionColor());
          assertNull(settings.errorColor());
          assertNull(settings.presenceColor());
        });
  }

  @Test
  void loadsMessageColorOverridesIntoBusFromImportedRuntimeConfig() {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberChatMessageColor("#404040");
    store.rememberChatNoticeColor("#505050");
    store.rememberChatActionColor("#606060");
    store.rememberChatErrorColor("#707070");
    store.rememberChatPresenceColor("#808080");

    try (ConfigurableApplicationContext ctx =
        new SpringApplicationBuilder(ChatThemeSettingsBusTestConfig.class)
            .web(WebApplicationType.NONE)
            .properties("spring.config.import=optional:" + cfg.toUri())
            .run()) {
      ChatThemeSettings settings = ctx.getBean(ChatThemeSettingsBus.class).get();
      assertEquals("#404040", settings.messageColor());
      assertEquals("#505050", settings.noticeColor());
      assertEquals("#606060", settings.actionColor());
      assertEquals("#707070", settings.errorColor());
      assertEquals("#808080", settings.presenceColor());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(UiProperties.class)
  static class ChatThemeSettingsBusTestConfig {

    @Bean
    ChatThemeSettingsBus chatThemeSettingsBus(UiProperties props) {
      return new ChatThemeSettingsBus(props);
    }
  }
}
