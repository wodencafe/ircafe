package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerRegistry;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.pircbotx.PircBotX;
import org.springframework.stereotype.Service;

@Service
public class PircbotxIrcClientService implements IrcClientService {

  private static final Logger log = LoggerFactory.getLogger(PircbotxIrcClientService.class);

  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();

  private final Map<String, PircbotxConnectionState> connections = new ConcurrentHashMap<>();
  private final ServerRegistry serverRegistry;
  private final PircbotxInputParserHookInstaller inputParserHookInstaller;
  private final PircbotxBotFactory botFactory;
  private final PircbotxConnectionTimersRx timers;
  private String version;
  public PircbotxIrcClientService(IrcProperties props,
                                 ServerRegistry serverRegistry,
                                 PircbotxInputParserHookInstaller inputParserHookInstaller,
                                 PircbotxBotFactory botFactory,
                                 PircbotxConnectionTimersRx timers) {
    this.serverRegistry = serverRegistry;
    version = props.client().version();
    this.inputParserHookInstaller = inputParserHookInstaller;
    this.botFactory = botFactory;
    this.timers = timers;
  }

  @Override
  public Flowable<ServerIrcEvent> events() {
    return bus.onBackpressureBuffer();
  }

  @Override
  public Optional<String> currentNick(String serverId) {
    PircBotX bot = conn(serverId).botRef.get();
    return bot == null ? Optional.empty() : Optional.ofNullable(bot.getNick());
  }

  @Override
  public Completable connect(String serverId) {
    return Completable.fromAction(() -> {
          PircbotxConnectionState c = conn(serverId);
          if (c.botRef.get() != null) return;

          // Any explicit connect cancels pending reconnect work and resets attempts.
          cancelReconnect(c);
          c.manualDisconnect.set(false);
          c.reconnectAttempts.set(0);

          IrcProperties.Server s = serverRegistry.require(serverId);

          // Surface a "connecting" state to the app/UI immediately.
          bus.onNext(new ServerIrcEvent(
              serverId, new IrcEvent.Connecting(Instant.now(), s.host(), s.port(), s.nick())));

          PircbotxBridgeListener listener = new PircbotxBridgeListener(
              serverId,
              c,
              bus,
              timers::stopHeartbeat,
              this::scheduleReconnect,
              this::handleCtcpIfPresent
          );

          PircBotX bot = botFactory.build(s, version, listener);
          c.botRef.set(bot);

          // PircBotX tracks away-notify on the User model but doesn't publish a dedicated event.
          // We install a tiny InputParser hook so we can surface AWAY state changes into our own event bus.
          inputParserHookInstaller.installAwayNotifyHook(bot, serverId, bus::onNext);

          timers.startHeartbeat(c);
          // Run bot on IO thread; connect() completes immediately
          Schedulers.io().scheduleDirect(() -> {
            boolean crashed = false;
            try {
              bot.startBot();
            } catch (Exception e) {
              crashed = true;
              bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Error(Instant.now(), "Bot crashed", e)));
            } finally {
              // Only clear state if this is still the current bot (avoid racing a reconnect).
              if (c.botRef.compareAndSet(bot, null)) {
                timers.stopHeartbeat(c);
              }

              // If the bot thread died unexpectedly, attempt reconnect (if enabled).
              if (crashed && !c.manualDisconnect.get()) {
                scheduleReconnect(c, "Bot crashed");
              }
            }
          });
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable disconnect(String serverId) {
    return Completable.fromAction(() -> {
          PircbotxConnectionState c = conn(serverId);

          // Mark as intentional and cancel any reconnect.
          c.manualDisconnect.set(true);
          cancelReconnect(c);

          // If we clear botRef here, the DisconnectEvent handler won't see the old bot as "current".
          // Stop heartbeat explicitly.
          timers.stopHeartbeat(c);

          PircBotX bot = c.botRef.getAndSet(null);
          if (bot == null) return;

          try {
            bot.stopBotReconnect();
            try {
              bot.sendIRC().quitServer("Client disconnect");
            } catch (Exception ignored) {}
            try {
              bot.close();
            } catch (Exception ignored) {}
          } finally {
            bus.onNext(new ServerIrcEvent(serverId,
                new IrcEvent.Disconnected(Instant.now(), "Client requested disconnect")));
          }
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable changeNick(String serverId, String newNick) {
    return Completable.fromAction(() -> {
          String nick = PircbotxUtil.sanitizeNick(newNick);
          requireBot(serverId).sendIRC().changeNick(nick);
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable setAway(String serverId, String awayMessage) {
    return Completable.fromAction(() -> {
          String msg = awayMessage == null ? "" : awayMessage.trim();
          if (msg.contains("\r") || msg.contains("\n")) {
            throw new IllegalArgumentException("away message contains CR/LF");
          }
          if (msg.isEmpty()) {
            requireBot(serverId).sendRaw().rawLine("AWAY");
          } else {
            requireBot(serverId).sendRaw().rawLine("AWAY :" + msg);
          }
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return Completable.fromAction(() -> requireBot(serverId).sendIRC().joinChannel(channel))
        .subscribeOn(Schedulers.io());
  }

@Override
  public Completable partChannel(String serverId, String channel, String reason) {
    return Completable.fromAction(() -> {
          String chan = PircbotxUtil.sanitizeChannel(channel);
          String msg = reason == null ? "" : reason.trim();
          if (msg.isEmpty()) {
            requireBot(serverId).sendRaw().rawLine("PART " + chan);
          } else {
            requireBot(serverId).sendRaw().rawLine("PART " + chan + " :" + msg);
          }
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable sendToChannel(String serverId, String channel, String message) {
    return Completable.fromAction(() -> requireBot(serverId).sendIRC().message(channel, message))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String nick, String message) {
    return Completable.fromAction(() -> requireBot(serverId).sendIRC().message(PircbotxUtil.sanitizeNick(nick), message))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable sendRaw(String serverId, String rawLine) {
    return Completable.fromAction(() -> {
          String line = rawLine == null ? "" : rawLine.trim();
          if (line.isEmpty()) return;
          requireBot(serverId).sendRaw().rawLine(line);
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable sendAction(String serverId, String target, String action) {
    return Completable.fromAction(() -> {
          String t = target == null ? "" : target.trim();
          String a = action == null ? "" : action;
          if (t.isEmpty()) throw new IllegalArgumentException("target is blank");

          // Prefer a native PircBotX action(...) API if present. Fall back to manual CTCP wrapper.
          Object out = requireBot(serverId).sendIRC();

          String dest;
          if (t.startsWith("#") || t.startsWith("&")) {
            dest = PircbotxUtil.sanitizeChannel(t);
          } else {
            dest = PircbotxUtil.sanitizeNick(t);
          }

          boolean sent = false;
          try {
            Method m = out.getClass().getMethod("action", String.class, String.class);
            m.invoke(out, dest, a);
            sent = true;
          } catch (NoSuchMethodException ignored) {
            // Older/newer OutputIRC API shape; we'll fall back.
          } catch (Exception e) {
            // Reflection failure: fall back to raw CTCP wrapper.
            log.debug("sendAction: native action() invoke failed, falling back to CTCP wrapper", e);
          }

          if (!sent) {
            requireBot(serverId).sendIRC().message(dest, "\u0001ACTION " + a + "\u0001");
          }
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return Completable.fromAction(() -> {
          String chan = PircbotxUtil.sanitizeChannel(channel);
          requireBot(serverId).sendRaw().rawLine("NAMES " + chan);
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return Completable.fromAction(() -> {
          String n = PircbotxUtil.sanitizeNick(nick);

          // Track this WHOIS so we can infer HERE on 318 if no 301 is received.
          conn(serverId).whoisSawAwayByNickLower.putIfAbsent(n.toLowerCase(Locale.ROOT), Boolean.FALSE);

          requireBot(serverId).sendRaw().rawLine("WHOIS " + n);
        })
        .subscribeOn(Schedulers.io());
  }

  private PircbotxConnectionState conn(String serverId) {
    String id = Objects.requireNonNull(serverId, "serverId").trim();

    return connections.computeIfAbsent(id, k -> new PircbotxConnectionState(id));
  }

  private PircBotX requireBot(String serverId) {
    PircBotX bot = conn(serverId).botRef.get();
    if (bot == null) throw new IllegalStateException("Not connected: " + serverId);
    return bot;
  }

  // (B3.6) Heartbeat + reconnect timers extracted to PircbotxConnectionTimersRx.

  // (B1) Small, mostly-pure helper methods moved into PircbotxUtil.

  private boolean handleCtcpIfPresent(PircBotX bot, String fromNick, String message) {
    if (message == null || message.length() < 2) return false;
    if (message.charAt(0) != 0x01 || message.charAt(message.length() - 1) != 0x01) return false;

    String inner = message.substring(1, message.length() - 1).trim();
    if (inner.isEmpty()) return false;

    String cmd = inner;
    int sp = inner.indexOf(' ');
    if (sp >= 0) cmd = inner.substring(0, sp);

    cmd = cmd.trim().toUpperCase(Locale.ROOT);

    if ("VERSION".equals(cmd)) {
      String v = (version == null) ? "IRCafe" : version;
      bot.sendIRC().notice(PircbotxUtil.sanitizeNick(fromNick), "\u0001VERSION " + v + "\u0001");
      return true;
    }

    if ("PING".equals(cmd)) {
      // Reply with the same token/payload (if any).
      String payload = "";
      int sp2 = inner.indexOf(' ');
      if (sp2 >= 0 && sp2 + 1 < inner.length()) payload = inner.substring(sp2 + 1).trim();
      String body = payload.isEmpty() ? "\u0001PING\u0001" : "\u0001PING " + payload + "\u0001";
      bot.sendIRC().notice(PircbotxUtil.sanitizeNick(fromNick), body);
      return true;
    }

    if ("TIME".equals(cmd)) {
      // Best-effort local time string; servers/clients vary here.
      String now = java.time.ZonedDateTime.now().toString();
      bot.sendIRC().notice(PircbotxUtil.sanitizeNick(fromNick), "\u0001TIME " + now + "\u0001");
      return true;
    }

    return false;
  }

  private void cancelReconnect(PircbotxConnectionState c) {
    timers.cancelReconnect(c);
  }

  private void scheduleReconnect(PircbotxConnectionState c, String reason) {
    timers.scheduleReconnect(c, reason, this::connect, bus::onNext);
  }

@PreDestroy
  void shutdown() {
    // Stop reconnection/heartbeat timers and shut down bots.
    for (PircbotxConnectionState c : connections.values()) {
      if (c == null) continue;
      try {
        c.manualDisconnect.set(true);
        cancelReconnect(c);
        timers.stopHeartbeat(c);

        PircBotX bot = c.botRef.getAndSet(null);
        if (bot != null) {
          try {
            if (bot.isConnected()) {
              try { bot.sendIRC().quitServer("Client shutdown"); } catch (Exception ignored) {}
            }
          } catch (Exception ignored) {}

          try { bot.stopBotReconnect(); } catch (Exception ignored) {}
          try { bot.close(); } catch (Exception ignored) {}
        }
      } catch (Exception e) {
        log.debug("[ircafe] Error during shutdown for {}", c.serverId, e);
      }
    }
  }

}
