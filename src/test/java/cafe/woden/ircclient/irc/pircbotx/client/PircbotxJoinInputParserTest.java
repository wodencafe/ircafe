package cafe.woden.ircclient.irc.pircbotx.client;

import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.ServerResponseEvent;
import org.pircbotx.hooks.managers.ListenerManager;

class PircbotxJoinInputParserTest {
  @Test
  void whoRateLimitBacksOffBeforeDispatchAndStillPublishesNumeric() {
    PircBotX bot = mock(PircBotX.class);
    PircbotxPacedOutput output = mock(PircbotxPacedOutput.class);
    ListenerManager listeners = mock(ListenerManager.class);
    when(listeners.getListeners()).thenReturn(ImmutableSet.of());
    Configuration configuration =
        new Configuration.Builder()
            .setName("probe")
            .addServer("localhost")
            .setListenerManager(listeners)
            .buildConfiguration();
    when(bot.getConfiguration()).thenReturn(configuration);
    when(bot.sendRaw()).thenReturn(output);
    var parser = new PircbotxJoinInputParser(bot);
    parser.processServerResponse(
        263, ":server 263 probe WHO :rate limited", List.of("probe", "WHO", "rate limited"));
    var order = inOrder(output, listeners);
    order.verify(output).observeWhoRateLimit();
    order.verify(listeners).onEvent(any(ServerResponseEvent.class));
    parser.processServerResponse(
        263, ":server 263 probe WHOIS :rate limited", List.of("probe", "WHOIS", "rate limited"));
    verify(output, times(1)).observeWhoRateLimit();
  }
}
