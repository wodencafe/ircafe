package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerRegistry;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.pircbotx.Channel;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.snapshot.ChannelSnapshot;
import org.pircbotx.snapshot.UserChannelDaoSnapshot;
import org.pircbotx.snapshot.UserSnapshot;
import org.springframework.stereotype.Service;

@Service
public class PircbotxIrcClientService implements IrcClientService {

  private static final Logger log = LoggerFactory.getLogger(PircbotxIrcClientService.class);

  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();

  private final Map<String, Connection> connections = new ConcurrentHashMap<>();
  private final ServerRegistry serverRegistry;
  private final String clientVersion;
  private final IrcProperties.Reconnect reconnectPolicy;

  public PircbotxIrcClientService(IrcProperties props, ServerRegistry serverRegistry) {
    this.serverRegistry = serverRegistry;
    this.clientVersion = props.client().version();
    this.reconnectPolicy = props.client().reconnect();
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
          Connection c = conn(serverId);
          if (c.botRef.get() != null) return;

          // Any explicit connect cancels pending reconnect work and resets attempts.
          cancelReconnect(c);
          c.manualDisconnect.set(false);
          c.reconnectAttempts.set(0);

          IrcProperties.Server s = serverRegistry.require(serverId);

          SocketFactory socketFactory = s.tls()
              ? SSLSocketFactory.getDefault()
              : SocketFactory.getDefault();

          Configuration.Builder builder = new Configuration.Builder()
              .setName(s.nick())
              .setLogin(s.login())
              .setRealName(s.realName())
              .addServer(s.host(), s.port())
              .setSocketFactory(socketFactory)
              .setCapEnabled(false) // we may enable below
              .setAutoNickChange(true)
              // We manage reconnects ourselves so we can surface status + use backoff.
              .setAutoReconnect(false)
              .addListener(new BridgeListener(serverId, c));

           if (s.serverPassword() != null && !s.serverPassword().isBlank()) {
            builder.setServerPassword(s.serverPassword());
          }

          // Auto-join channels from config
          for (String chan : s.autoJoin()) {
            String ch = chan == null ? "" : chan.trim();
            if (!ch.isEmpty()) builder.addAutoJoinChannel(ch);
          }

          // SASL (PLAIN)
          if (s.sasl() != null && s.sasl().enabled()) {
            if (!"PLAIN".equalsIgnoreCase(s.sasl().mechanism())) {
              throw new IllegalStateException(
                  "Only SASL mechanism PLAIN is supported for now (got: " + s.sasl().mechanism() + ")"
              );
            }
            if (s.sasl().username().isBlank() || s.sasl().password().isBlank()) {
              throw new IllegalStateException("SASL enabled but username/password not set");
            }
            builder.setCapEnabled(true);
            builder.addCapHandler(new SASLCapHandler(s.sasl().username(), s.sasl().password()));
          }

          PircBotX bot = new PircBotX(builder.buildConfiguration());
          c.botRef.set(bot);

          startHeartbeat(c);
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
                ScheduledFuture<?> f = c.heartbeatFuture.getAndSet(null);
                if (f != null) f.cancel(false);
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
          Connection c = conn(serverId);

          // Mark as intentional and cancel any reconnect.
          c.manualDisconnect.set(true);
          cancelReconnect(c);

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
          String nick = sanitizeNick(newNick);
          requireBot(serverId).sendIRC().changeNick(nick);
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
          String chan = sanitizeChannel(channel);
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
    return Completable.fromAction(() -> requireBot(serverId).sendIRC().message(sanitizeNick(nick), message))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return Completable.fromAction(() -> {
          String chan = sanitizeChannel(channel);
          requireBot(serverId).sendRaw().rawLine("NAMES " + chan);
        })
        .subscribeOn(Schedulers.io());
  }

  private Connection conn(String serverId) {
    String id = Objects.requireNonNull(serverId, "serverId").trim();

    return connections.computeIfAbsent(id, k -> new Connection(id));
  }

  private PircBotX requireBot(String serverId) {
    PircBotX bot = conn(serverId).botRef.get();
    if (bot == null) throw new IllegalStateException("Not connected: " + serverId);
    return bot;
  }

  private static final class Connection {
    private final String serverId;
    private final AtomicReference<PircBotX> botRef = new AtomicReference<>();

    final AtomicLong lastInboundMs = new AtomicLong(0);
    final AtomicBoolean localTimeoutEmitted = new AtomicBoolean(false);
    final AtomicReference<ScheduledFuture<?>> heartbeatFuture = new AtomicReference<>();

    final AtomicBoolean manualDisconnect = new AtomicBoolean(false);
    final AtomicLong reconnectAttempts = new AtomicLong(0);
    final AtomicReference<ScheduledFuture<?>> reconnectFuture = new AtomicReference<>();
    final AtomicReference<String> disconnectReasonOverride = new AtomicReference<>();

    private Connection(String serverId) {
      this.serverId = serverId;
    }
  }
  private final ScheduledExecutorService heartbeatExec =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ircafe-heartbeat");
        t.setDaemon(true);
        return t;
      });

  private final ScheduledExecutorService reconnectExec =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ircafe-reconnect");
        t.setDaemon(true);
        return t;
      });

  private static final long HEARTBEAT_CHECK_PERIOD_MS = 15_000;   // 15s
  private static final long HEARTBEAT_TIMEOUT_MS      = 360_000;  // 6 min (tune)

  private static String sanitizeNick(String nick) {
    String n = Objects.requireNonNull(nick, "nick").trim();
    if (n.isEmpty()) throw new IllegalArgumentException("nick is blank");
    if (n.contains("\r") || n.contains("\n"))
      throw new IllegalArgumentException("nick contains CR/LF");
    if (n.contains(" "))
      throw new IllegalArgumentException("nick contains spaces");
    return n;
  }

  private static String sanitizeChannel(String channel) {
    String c = Objects.requireNonNull(channel, "channel").trim();
    if (c.isEmpty()) throw new IllegalArgumentException("channel is blank");
    if (c.contains("\r") || c.contains("\n"))
      throw new IllegalArgumentException("channel contains CR/LF");
    if (c.contains(" "))
      throw new IllegalArgumentException("channel contains spaces");
    if (!(c.startsWith("#") || c.startsWith("&")))
      throw new IllegalArgumentException("channel must start with # or & (got: " + c + ")");
    return c;
  }

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
      String v = (clientVersion == null) ? "" : clientVersion.trim();
      if (v.isEmpty()) v = "IRCafe";
      bot.sendIRC().notice(sanitizeNick(fromNick), "\u0001VERSION " + v + "\u0001");
      return true;
    }

    return false;
  }


  private void startHeartbeat(Connection c) {
    c.lastInboundMs.set(System.currentTimeMillis());
    c.localTimeoutEmitted.set(false);

    ScheduledFuture<?> prev = c.heartbeatFuture.getAndSet(
        heartbeatExec.scheduleAtFixedRate(() -> checkHeartbeat(c),
            HEARTBEAT_CHECK_PERIOD_MS,
            HEARTBEAT_CHECK_PERIOD_MS,
            TimeUnit.MILLISECONDS)
    );
    if (prev != null) prev.cancel(false);
  }

  private void checkHeartbeat(Connection c) {
    PircBotX bot = c.botRef.get();
    if (bot == null) return;

    long idleMs = System.currentTimeMillis() - c.lastInboundMs.get();
    if (idleMs > HEARTBEAT_TIMEOUT_MS && c.localTimeoutEmitted.compareAndSet(false, true)) {
      // Don't emit Disconnected here (DisconnectEvent will fire). Instead, stash a reason override.
      c.disconnectReasonOverride.set(
          "Ping timeout (no inbound traffic for " + (idleMs / 1000) + "s)"
      );
      try { bot.close(); } catch (Exception ignored) {}
    }
  }

  private void cancelReconnect(Connection c) {
    if (c == null) return;
    ScheduledFuture<?> f = c.reconnectFuture.getAndSet(null);
    if (f != null) f.cancel(false);
  }

  private void scheduleReconnect(Connection c, String reason) {
    if (c == null) return;
    IrcProperties.Reconnect p = reconnectPolicy;
    if (p == null || !p.enabled()) return;
    if (c.manualDisconnect.get()) return;

    long attempt = c.reconnectAttempts.incrementAndGet();
    if (p.maxAttempts() > 0 && attempt > p.maxAttempts()) {
      bus.onNext(new ServerIrcEvent(c.serverId, new IrcEvent.Error(
          Instant.now(),
          "Reconnect aborted (max attempts reached)",
          null
      )));
      return;
    }

    long delayMs = computeBackoffDelayMs(p, attempt);
    bus.onNext(new ServerIrcEvent(c.serverId, new IrcEvent.Reconnecting(
        Instant.now(),
        attempt,
        delayMs,
        Objects.toString(reason, "Disconnected")
    )));

    ScheduledFuture<?> prev = c.reconnectFuture.getAndSet(
        reconnectExec.schedule(() -> {
              if (c.manualDisconnect.get()) return;
              // If the server was removed while waiting to reconnect, abort.
              if (!serverRegistry.containsId(c.serverId)) {
                bus.onNext(new ServerIrcEvent(c.serverId, new IrcEvent.Error(
                    Instant.now(),
                    "Reconnect cancelled (server removed)",
                    null
                )));
                return;
              }
              // Connect is idempotent per-server; it will no-op if already connected.
              connect(c.serverId).subscribe(
                  () -> {},
                  err -> {
                    bus.onNext(new ServerIrcEvent(c.serverId, new IrcEvent.Error(
                        Instant.now(),
                        "Reconnect attempt failed",
                        err
                    )));
                    scheduleReconnect(c, "Reconnect attempt failed");
                  }
              );
            },
            delayMs,
            TimeUnit.MILLISECONDS)
    );
    if (prev != null) prev.cancel(false);
  }

  private static long computeBackoffDelayMs(IrcProperties.Reconnect p, long attempt) {
    long base = p.initialDelayMs();
    double mult = Math.pow(p.multiplier(), Math.max(0, attempt - 1));
    double raw = base * mult;
    long capped = (long) Math.min(raw, (double) p.maxDelayMs());

    double jitter = p.jitterPct();
    if (jitter <= 0) return capped;

    double factor = 1.0 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
    long withJitter = (long) Math.max(0, capped * factor);
    return Math.max(250, withJitter); // don't go crazy-small
  }

  private final class BridgeListener extends ListenerAdapter {
    private final String serverId;
    private final Connection conn;
    private BridgeListener(String serverId, Connection conn) {
      this.serverId = serverId;
      this.conn = conn;
    }

    @Override
    public void onConnect(ConnectEvent event) {
      touchInbound();
      PircBotX bot = event.getBot();

      // Successful reconnect resets counters.
      conn.reconnectAttempts.set(0);
      conn.manualDisconnect.set(false);

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Connected(
          Instant.now(),
          bot.getServerHostname(),
          bot.getServerPort(),
          bot.getNick()
      )));
    }

    @Override
    public void onDisconnect(DisconnectEvent event) {
      // Prefer any locally-detected reason (e.g., heartbeat timeout).
      String override = conn.disconnectReasonOverride.getAndSet(null);
      Exception ex = event.getDisconnectException();
      String reason = (override != null && !override.isBlank())
          ? override
          : (ex != null && ex.getMessage() != null) ? ex.getMessage() : "Disconnected";

      // Clear the bot reference if this disconnect belongs to the current bot.
      if (conn.botRef.compareAndSet(event.getBot(), null)) {
        ScheduledFuture<?> f = conn.heartbeatFuture.getAndSet(null);
        if (f != null) f.cancel(false);
      }

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Disconnected(Instant.now(), reason)));

      // Only auto-reconnect on non-manual disconnects.
      if (!conn.manualDisconnect.get()) {
        scheduleReconnect(conn, reason);
      }
    }

    @Override
    public void onMessage(MessageEvent event) {
      touchInbound();
      String channel = event.getChannel().getName();
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.ChannelMessage(
          Instant.now(), channel, event.getUser().getNick(), event.getMessage()
      )));
    }

    @Override
    public void onTopic(TopicEvent event) {
      touchInbound();
      if (event == null || event.getChannel() == null) return;
      String channel = event.getChannel().getName();
      String topic = event.getTopic();
      if (channel == null || channel.isBlank()) return;
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.ChannelTopicUpdated(
          Instant.now(), channel, topic
      )));
    }

    @Override
    public void onPrivateMessage(PrivateMessageEvent event) {
      touchInbound();
      String from = event.getUser().getNick();
      String msg = event.getMessage();
      if (handleCtcpIfPresent(event.getBot(), from, msg)) return;
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.PrivateMessage(Instant.now(), from, msg)));
    }

    @Override
    public void onNotice(NoticeEvent event) {
      touchInbound();
      String from = (event.getUser() != null) ? event.getUser().getNick() : "server";
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Notice(Instant.now(), from, event.getNotice())));
    }

    @Override
    public void onUserList(UserListEvent event) {
      emitRoster(event.getChannel());
    }

    @Override
    public void onUnknown(UnknownEvent event) {
      touchInbound();
    }

    @Override
    public void onJoin(JoinEvent event) {
      touchInbound();
      Channel channel = event.getChannel();

      if (isSelf(event.getBot(), event.getUser().getNick())) {
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.JoinedChannel(Instant.now(), channel.getName())));
      }

      emitRoster(channel);
    }

    @Override
    public void onPart(PartEvent event) {
      touchInbound();
      refreshRosterByName(event.getBot(), event.getChannelName());
    }

    @Override
    public void onQuit(QuitEvent event) {
      touchInbound();
      PircBotX bot = event.getBot();

      boolean refreshedSome = false;
      try {
        UserChannelDaoSnapshot daoSnap = event.getUserChannelDaoSnapshot();
        UserSnapshot userSnap = event.getUser();

        if (daoSnap != null && userSnap != null) {
          for (ChannelSnapshot cs : daoSnap.getChannels(userSnap)) {
            refreshRosterByName(bot, cs.getName());
            refreshedSome = true;
          }
        }
      } catch (Exception ignored) {

      }

      if (!refreshedSome) {
        try {
          for (Channel ch : bot.getUserChannelDao().getAllChannels()) {
            emitRoster(ch);
          }
        } catch (Exception ignored) {}
      }
    }

    private void touchInbound() {
      conn.lastInboundMs.set(System.currentTimeMillis());
      conn.localTimeoutEmitted.set(false);
    }


    @Override
    public void onServerPing(ServerPingEvent event) {
      touchInbound();
    }


    @Override
    public void onKick(KickEvent event) {
      touchInbound();
      emitRoster(event.getChannel());
    }

    @Override
    public void onNickChange(NickChangeEvent event) {
      touchInbound();
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.NickChanged(
          Instant.now(),
          event.getOldNick(),
          event.getNewNick()
      )));

      try {
        for (Channel ch : event.getBot().getUserChannelDao().getChannels(event.getUser())) {
          emitRoster(ch);
        }
      } catch (Exception ignored) {}
    }

    @Override
    public void onMode(ModeEvent event) {
      touchInbound();
      if (event.getChannel() != null) emitRoster(event.getChannel());
    }

    @Override public void onOp(OpEvent event) { emitRoster(event.getChannel()); }
    @Override public void onVoice(VoiceEvent event) { emitRoster(event.getChannel()); }
    @Override public void onHalfOp(HalfOpEvent event) { emitRoster(event.getChannel()); }
    @Override public void onOwner(OwnerEvent event) { emitRoster(event.getChannel()); }
    @Override public void onSuperOp(SuperOpEvent event) { emitRoster(event.getChannel()); }

    private boolean isSelf(PircBotX bot, String nick) {
      return nick != null && nick.equalsIgnoreCase(bot.getNick());
    }

    private void refreshRosterByName(PircBotX bot, String channelName) {
      if (channelName == null || channelName.isBlank()) return;
      try {
        if (!bot.getUserChannelDao().containsChannel(channelName)) return;
        Channel ch = bot.getUserChannelDao().getChannel(channelName);
        if (ch != null) emitRoster(ch);
      } catch (Exception ignored) {}
    }

    private void emitRoster(Channel channel) {
      if (channel == null) return;

      String channelName = channel.getName();
      var opsSet = channel.getOps();

      List<IrcEvent.NickInfo> nicks = channel.getUsers().stream()
          .map(u -> new IrcEvent.NickInfo(
              u.getNick(),
              opsSet.contains(u) ? "@" : ""
          ))
          .sorted(Comparator
              .comparing((IrcEvent.NickInfo n) -> n.prefix().equals("@") ? 0 : 1)
              .thenComparing(IrcEvent.NickInfo::nick, String.CASE_INSENSITIVE_ORDER))
          .toList();

      int totalUsers = channel.getUsers().size();
      int operatorCount = opsSet.size();

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.NickListUpdated(
          Instant.now(),
          channelName,
          nicks,
          totalUsers,
          operatorCount
      )));
    }
  }

  
  @PreDestroy
  void shutdown() {
    // Stop reconnection/heartbeat timers and shut down executors.
    for (Connection c : connections.values()) {
      if (c == null) continue;
      try {
        c.manualDisconnect.set(true);
        cancelReconnect(c);
        ScheduledFuture<?> h = c.heartbeatFuture.getAndSet(null);
        if (h != null) h.cancel(false);

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

    reconnectExec.shutdownNow();
    heartbeatExec.shutdownNow();
  }

}
