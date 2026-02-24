package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.ui.SwingUiPort;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LoggingUiPortConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(LoggingUiPortConfig.class)
          .withBean(
              SwingUiPort.class, () -> Mockito.mock(SwingUiPort.class, Mockito.withSettings().stubOnly()))
          .withBean(ChatLogWriter.class, () -> line -> {})
          .withBean(LogLineFactory.class, LogLineFactory::new)
          .withBean(
              LogProperties.class,
              () -> new LogProperties(true, true, true, true, true, 0, null));

  @Test
  void loggingUiPortBeanCreatedWhenLoggingEnabled() {
    runner
        .withPropertyValues("ircafe.logging.enabled=true")
        .run(
            ctx -> {
              UiPort ui = ctx.getBean(UiPort.class);
              assertInstanceOf(LoggingUiPortDecorator.class, ui);
              // SwingUiPort + primary LoggingUiPortDecorator
              assertEquals(2, ctx.getBeansOfType(UiPort.class).size());
            });
  }

  @Test
  void loggingUiPortBeanBacksOffWhenLoggingDisabled() {
    runner
        .withPropertyValues("ircafe.logging.enabled=false")
        .run(
            ctx ->
                // Only SwingUiPort remains when the conditional logging decorator is disabled.
                assertEquals(1, ctx.getBeansOfType(UiPort.class).size()));
  }

  @Test
  void loggingUiPortIsPrimaryWhenOtherUiPortBeansExist() {
    runner
        .withBean("baseUiPort", UiPort.class, () -> Mockito.mock(UiPort.class))
        .withPropertyValues("ircafe.logging.enabled=true")
        .run(
            ctx -> {
              UiPort selected = ctx.getBean(UiPort.class);
              assertInstanceOf(LoggingUiPortDecorator.class, selected);
              // baseUiPort + SwingUiPort + primary LoggingUiPortDecorator
              assertEquals(3, ctx.getBeansOfType(UiPort.class).size());
            });
  }
}
