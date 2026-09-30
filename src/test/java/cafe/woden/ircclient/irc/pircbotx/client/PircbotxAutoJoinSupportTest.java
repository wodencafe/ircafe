package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.UserChannelDao;
import org.pircbotx.hooks.events.ServerResponseEvent;
import org.pircbotx.output.OutputIRC;

class PircbotxAutoJoinSupportTest {
  @Test
  void waitsForRegistrationAndNickservAndRunsOnlyOnceOffListenerThread() throws Exception {
    PircBotX bot = mock(PircBotX.class);
    Configuration config =
        new Configuration.Builder()
            .setName("probe")
            .addServer("localhost", 6667)
            .setNickservPassword("secret")
            .setNickservDelayJoin(true)
            .buildConfiguration();
    when(bot.getConfiguration()).thenReturn(config);
    when(bot.isConnected()).thenReturn(true);
    when(bot.getUserChannelDao()).thenReturn(mock(UserChannelDao.class));
    OutputIRC output = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(output);
    CountDownLatch joined = new CountDownLatch(2);
    Thread caller = Thread.currentThread();
    doAnswer(
            invocation -> {
              assertNotSame(caller, Thread.currentThread());
              joined.countDown();
              return null;
            })
        .when(output)
        .joinChannel(anyString());
    try (PircbotxAutoJoinSupport support =
        new PircbotxAutoJoinSupport("test", List.of("#one", "#two key"), 0)) {
      support.maybeStart(bot);
      verifyNoInteractions(output);
      support.onServerResponse(response(bot, 376));
      verifyNoInteractions(output);
      when(bot.isNickservIdentified()).thenReturn(true);
      support.maybeStart(bot);
      assertTrue(joined.await(2, TimeUnit.SECONDS));
      support.onServerResponse(response(bot, 422));
      support.maybeStart(bot);
      verify(output, times(1)).joinChannel("#one");
      verify(output, times(1)).joinChannel("#two key");
    }
  }

  @Test
  void skipsAlreadyJoinedChannelsIncludingKeyedEntriesButStillJoinsMissingChannels()
      throws Exception {
    PircBotX bot = mock(PircBotX.class);
    when(bot.getConfiguration())
        .thenReturn(
            new Configuration.Builder()
                .setName("probe")
                .addServer("localhost", 6667)
                .buildConfiguration());
    when(bot.isConnected()).thenReturn(true);
    UserChannelDao dao = mock(UserChannelDao.class);
    when(bot.getUserChannelDao()).thenReturn(dao);
    when(dao.containsChannel("#one")).thenReturn(true);
    when(dao.containsChannel("#two")).thenReturn(true);
    OutputIRC output = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(output);
    CountDownLatch joined = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              joined.countDown();
              return null;
            })
        .when(output)
        .joinChannel("#three secret-key");
    try (PircbotxAutoJoinSupport support =
        new PircbotxAutoJoinSupport(
            "test", List.of("#one", "#two old-key", "#three secret-key"), 0)) {
      support.onServerResponse(response(bot, 376));
      assertTrue(joined.await(2, TimeUnit.SECONDS));
      verify(output).joinChannel("#three secret-key");
      verifyNoMoreInteractions(output);
    }
  }

  @Test
  void closingCancelsDelayedPlanAndDoesNotAllowRestart() throws Exception {
    PircBotX bot = mock(PircBotX.class);
    when(bot.getConfiguration())
        .thenReturn(
            new Configuration.Builder()
                .setName("probe")
                .addServer("localhost", 6667)
                .buildConfiguration());
    when(bot.isConnected()).thenReturn(true);
    OutputIRC output = mock(OutputIRC.class);
    when(bot.sendIRC()).thenReturn(output);
    PircbotxAutoJoinSupport support = new PircbotxAutoJoinSupport("test", List.of("#one"), 150);
    support.onServerResponse(response(bot, 422));
    support.close();
    support.onServerResponse(response(bot, 376));
    Thread.sleep(250);
    verifyNoInteractions(output);
  }

  private static ServerResponseEvent response(PircBotX bot, int code) {
    ServerResponseEvent event = mock(ServerResponseEvent.class);
    when(event.getBot()).thenReturn(bot);
    when(event.getCode()).thenReturn(code);
    return event;
  }
}
