package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class UiPropertiesBindingTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(UiPropertiesTestConfig.class);

  @Test
  void defaultsLeaveOptionalMessageColorOverridesUnset() {
    runner.run(
        ctx -> {
          UiProperties props = ctx.getBean(UiProperties.class);
          assertNull(props.chatMessageColor());
          assertNull(props.chatNoticeColor());
          assertNull(props.chatActionColor());
          assertNull(props.chatErrorColor());
          assertNull(props.chatPresenceColor());
        });
  }

  @Test
  void messageColorOverridesNormalizeAndInvalidValuesClearToNull() {
    runner
        .withPropertyValues(
            "ircafe.ui.chatMessageColor=#aabbcc",
            "ircafe.ui.chatNoticeColor=0x223344",
            "ircafe.ui.chatActionColor=#334455",
            "ircafe.ui.chatErrorColor=#GG4455",
            "ircafe.ui.chatPresenceColor=   ")
        .run(
            ctx -> {
              UiProperties props = ctx.getBean(UiProperties.class);
              assertEquals("#AABBCC", props.chatMessageColor());
              assertEquals("#223344", props.chatNoticeColor());
              assertEquals("#334455", props.chatActionColor());
              assertNull(props.chatErrorColor());
              assertNull(props.chatPresenceColor());
            });
  }

  @Test
  void memoryRefreshIntervalBindsFromConfig() {
    runner
        .withPropertyValues("ircafe.ui.memoryUsageRefreshIntervalMs=1500")
        .run(
            ctx -> {
              UiProperties props = ctx.getBean(UiProperties.class);
              assertEquals(1500, props.memoryUsageRefreshIntervalMs());
            });
  }

  @Test
  void dockLayoutPreserveDefaultsEnabled() {
    runner.run(
        ctx -> {
          UiProperties props = ctx.getBean(UiProperties.class);
          assertTrue(props.layout().preserveDockLayout());
        });
  }

  @Test
  void dockLayoutPreserveCanBeDisabledExplicitly() {
    runner
        .withPropertyValues("ircafe.ui.layout.preserveDockLayout=false")
        .run(
            ctx -> {
              UiProperties props = ctx.getBean(UiProperties.class);
              assertFalse(props.layout().preserveDockLayout());
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(UiProperties.class)
  static class UiPropertiesTestConfig {}
}
