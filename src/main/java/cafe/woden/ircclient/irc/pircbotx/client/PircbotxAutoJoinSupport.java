package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.util.VirtualThreads;
import java.util.List;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.ServerResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One bounded startup plan per bot, run off the reader/listener/EDT and cancelled with the bot. */
final class PircbotxAutoJoinSupport extends ListenerAdapter implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(PircbotxAutoJoinSupport.class);
  private final String serverId;
  private final List<String> channels;
  private final long delayMs;
  private boolean ready;
  private boolean closed;
  private Thread worker;

  PircbotxAutoJoinSupport(String serverId, List<String> channels, long delayMs) {
    this.serverId = serverId;
    this.channels = List.copyOf(channels);
    this.delayMs = delayMs;
  }

  @Override
  public synchronized void onServerResponse(ServerResponseEvent event) {
    if (event.getCode() != 376 && event.getCode() != 422) return;
    ready = true;
    maybeStart(event.getBot());
  }

  synchronized void maybeStart(PircBotX bot) {
    // Do not acquire PircBotX's state lock while holding this monitor: shutdown can call close()
    // with that lock held. The worker checks connectivity immediately before sending each join.
    if (!ready || closed || worker != null || channels.isEmpty()) return;
    if (bot.getConfiguration().isNickservDelayJoin()
        && bot.getConfiguration().getNickservPassword() != null
        && !bot.isNickservIdentified()) return;
    worker = VirtualThreads.unstarted("ircafe-autojoin-" + serverId, () -> join(bot));
    worker.start();
  }

  private void join(PircBotX bot) {
    try {
      log.info(
          "[{}] auto-join plan: channels={}, delayMs={}, commandIntervalMs={}",
          serverId,
          channels.size(),
          delayMs,
          bot.getConfiguration().getMessageDelay().getDelay());
      Thread.sleep(delayMs);
      for (String channel : channels) {
        if (Thread.currentThread().isInterrupted() || !bot.isConnected()) return;
        bot.sendIRC().joinChannel(channel);
      }
      log.debug("[{}] auto-join plan sent ({} channels)", serverId, channels.size());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException e) {
      if (!Thread.currentThread().isInterrupted() && bot.isConnected()) {
        log.warn("[{}] auto-join plan failed", serverId, e);
      }
    }
  }

  @Override
  public void onDisconnect(DisconnectEvent event) {
    close();
  }

  @Override
  public synchronized void close() {
    closed = true;
    if (worker != null) worker.interrupt();
  }
}
