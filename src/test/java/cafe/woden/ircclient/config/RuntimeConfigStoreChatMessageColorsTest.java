package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class RuntimeConfigStoreChatMessageColorsTest {

  @TempDir Path tempDir;

  @Test
  void chatMessageColorOverridesPersistAndCanBeCleared() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberChatMessageColor("#112233");
    store.rememberChatNoticeColor("#223344");
    store.rememberChatActionColor("#334455");
    store.rememberChatErrorColor("#445566");
    store.rememberChatPresenceColor("#556677");

    String persisted = Files.readString(cfg);
    assertTrue(persisted.contains("chatMessageColor"));
    assertTrue(persisted.contains("chatNoticeColor"));
    assertTrue(persisted.contains("chatActionColor"));
    assertTrue(persisted.contains("chatErrorColor"));
    assertTrue(persisted.contains("chatPresenceColor"));
    assertTrue(persisted.contains("#112233"));
    assertTrue(persisted.contains("#223344"));
    assertTrue(persisted.contains("#334455"));
    assertTrue(persisted.contains("#445566"));
    assertTrue(persisted.contains("#556677"));

    store.rememberChatMessageColor(" ");
    store.rememberChatNoticeColor(" ");
    store.rememberChatActionColor(" ");
    store.rememberChatErrorColor(" ");
    store.rememberChatPresenceColor(" ");

    String cleared = Files.readString(cfg);
    assertFalse(cleared.contains("chatMessageColor"));
    assertFalse(cleared.contains("chatNoticeColor"));
    assertFalse(cleared.contains("chatActionColor"));
    assertFalse(cleared.contains("chatErrorColor"));
    assertFalse(cleared.contains("chatPresenceColor"));
  }

  @Test
  void chatMessageColorOverridesReloadIntoUiPropertiesOnSpringStartup() {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store =
        new RuntimeConfigStore(cfg.toString(), new IrcProperties(null, List.of()));

    store.rememberChatMessageColor("#112233");
    store.rememberChatNoticeColor("#223344");
    store.rememberChatActionColor("#334455");
    store.rememberChatErrorColor("#445566");
    store.rememberChatPresenceColor("#556677");

    try (ConfigurableApplicationContext ctx =
        new SpringApplicationBuilder(UiPropertiesReloadTestConfig.class)
            .web(WebApplicationType.NONE)
            .properties("spring.config.import=optional:" + cfg.toUri())
            .run()) {
      UiProperties props = ctx.getBean(UiProperties.class);
      assertEquals("#112233", props.chatMessageColor());
      assertEquals("#223344", props.chatNoticeColor());
      assertEquals("#334455", props.chatActionColor());
      assertEquals("#445566", props.chatErrorColor());
      assertEquals("#556677", props.chatPresenceColor());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(UiProperties.class)
  static class UiPropertiesReloadTestConfig {}
}
