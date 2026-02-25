package cafe.woden.ircclient.app.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;

class UiPortDecoratorTest {

  @Test
  void forwardsChannelListAndBanListMethodsToDelegate() {
    UiPort delegate = mock(UiPort.class);
    UiPortDecorator decorator = new PassthroughUiPortDecorator(delegate);

    decorator.beginChannelList("libera", "Loading channel list...");
    decorator.appendChannelListEntry("libera", "#ircafe", 42, "IRCafe topic");
    decorator.endChannelList("libera", "End of output.");

    decorator.beginChannelBanList("libera", "#ircafe");
    decorator.appendChannelBanListEntry(
        "libera", "#ircafe", "*!*@bad.host", "ChanOp", 1_739_900_000L);
    decorator.endChannelBanList("libera", "#ircafe", "End of channel ban list");

    verify(delegate).beginChannelList("libera", "Loading channel list...");
    verify(delegate).appendChannelListEntry("libera", "#ircafe", 42, "IRCafe topic");
    verify(delegate).endChannelList("libera", "End of output.");

    verify(delegate).beginChannelBanList("libera", "#ircafe");
    verify(delegate)
        .appendChannelBanListEntry(
            "libera", "#ircafe", "*!*@bad.host", "ChanOp", 1_739_900_000L);
    verify(delegate).endChannelBanList("libera", "#ircafe", "End of channel ban list");

    verifyNoMoreInteractions(delegate);
  }

  private static final class PassthroughUiPortDecorator extends UiPortDecorator {
    private PassthroughUiPortDecorator(UiPort delegate) {
      super(delegate);
    }
  }
}

