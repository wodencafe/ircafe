package cafe.woden.ircclient.irc.adapter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.port.IrcShutdownPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IrcShutdownPortAdapterTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(IrcShutdownPortAdapter.class)
          .withBean("ircClientService", IrcClientService.class, () -> mock(IrcClientService.class));

  @Test
  void createsNamedShutdownPortBeanFromIrcClientService() {
    runner.run(
        ctx -> {
          IrcClientService irc = ctx.getBean("ircClientService", IrcClientService.class);
          IrcShutdownPort port = ctx.getBean("ircShutdownPort", IrcShutdownPort.class);

          assertNotNull(port);
          port.shutdownNow();

          verify(irc).shutdownNow();
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void delegatesShutdownNow(boolean nativePort) {
    IrcClientService irc = nativePort ? mock(NativeClient.class) : mock(IrcClientService.class);
    IrcShutdownPort port = new IrcShutdownPortAdapter(irc);

    port.shutdownNow();

    verify(irc).shutdownNow();
  }

  @Test
  void rejectsMissingClient() {
    assertThrows(NullPointerException.class, () -> new IrcShutdownPortAdapter(null));
  }

  private interface NativeClient extends IrcClientService, IrcShutdownPort {
    @Override
    void shutdownNow();
  }
}
