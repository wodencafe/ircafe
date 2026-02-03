package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.IgnoreListService;
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
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.pircbotx.Channel;
import org.pircbotx.Configuration;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.cap.EnableCapHandler;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.Event;
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
  private final IrcProperties.Reconnect reconnectPolicy;

  private final IgnoreListService ignoreListService;
  private String version;
  public PircbotxIrcClientService(IrcProperties props,
                                 ServerRegistry serverRegistry,
                                 IgnoreListService ignoreListService,
                                 ClientIdentityService identity) {
    this.serverRegistry = serverRegistry;
    this.identity = identity;
    this.reconnectPolicy = props.client().reconnect();
    version = props.client().version();
    this.ignoreListService = ignoreListService;
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

          // Surface a "connecting" state to the app/UI immediately.
          bus.onNext(new ServerIrcEvent(
              serverId, new IrcEvent.Connecting(Instant.now(), s.host(), s.port(), s.nick())));

          SocketFactory socketFactory = s.tls()
              ? SSLSocketFactory.getDefault()
              : SocketFactory.getDefault();

          Configuration.Builder builder = new Configuration.Builder()
              .setName(s.nick())
              .setLogin(s.login())
              .setRealName(s.realName())
              .setVersion(version)
              .addServer(s.host(), s.port())

              .setSocketFactory(socketFactory)
              // Enable CAP so we can request low-cost IRCv3 capabilities (e.g. userhost-in-names).
              .setCapEnabled(true)
              // Prefer hostmasks in the initial NAMES list (when supported). If unsupported, ignore.
              .addCapHandler(new EnableCapHandler("userhost-in-names", true))
              // IRCv3 away-notify: server will send user AWAY state changes as raw AWAY commands.
              .addCapHandler(new EnableCapHandler("away-notify", true))
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

          // PircBotX tracks away-notify on the User model but doesn't publish a dedicated event.
          // We install a tiny InputParser hook so we can surface AWAY state changes into our own event bus.
          installAwayNotifyInputParserHook(bot, serverId);

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
            dest = sanitizeChannel(t);
          } else {
            dest = sanitizeNick(t);
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
          String chan = sanitizeChannel(channel);
          requireBot(serverId).sendRaw().rawLine("NAMES " + chan);
        })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return Completable.fromAction(() -> {
          String n = sanitizeNick(nick);

          // Track this WHOIS so we can infer HERE on 318 if no 301 is received.
          conn(serverId).whoisSawAwayByNickLower.putIfAbsent(n.toLowerCase(java.util.Locale.ROOT), Boolean.FALSE);

          requireBot(serverId).sendRaw().rawLine("WHOIS " + n);
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

    /**
     * Best-effort, passive hostmask cache learned from server prefixes (JOIN/PRIVMSG/etc.).
     * Keyed by lowercase nick. Used to avoid spamming the app layer with redundant observations.
     */
    final Map<String, String> lastHostmaskByNickLower = new ConcurrentHashMap<>();

    /** Tracks whether a WHOIS for a nick reported RPL_AWAY (301) before RPL_ENDOFWHOIS (318). */
    final Map<String, Boolean> whoisSawAwayByNickLower = new ConcurrentHashMap<>();

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

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingLongSupplier {
    long getAsLong() throws Exception;
  }

  private static String safeStr(ThrowingSupplier<String> s, String def) {
    try {
      String v = s.get();
      return v == null ? def : v;
    } catch (Exception ignored) {
      return def;
    }
  }

  private static long safeLong(ThrowingLongSupplier s, long def) {
    try {
      return s.getAsLong();
    } catch (Exception ignored) {
      return def;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> safeList(ThrowingSupplier<List<T>> s) {
    try {
      List<T> v = s.get();
      return v == null ? java.util.List.of() : v;
    } catch (Exception ignored) {
      return java.util.List.of();
    }
  }


  private static String parseCtcpAction(String message) {
    if (message == null || message.length() < 2) return null;
    if (message.charAt(0) != 0x01 || message.charAt(message.length() - 1) != 0x01) return null;
    String inner = message.substring(1, message.length() - 1).trim();
    if (inner.isEmpty()) return null;

    // CTCP ACTION: "ACTION <text>"
    if (inner.regionMatches(true, 0, "ACTION", 0, 6)) {
      String rest = inner.length() > 6 ? inner.substring(6).trim() : "";
      return rest;
    }
    return null;
  }

  private static boolean isCtcpWrapped(String message) {
    if (message == null || message.length() < 2) return false;
    return message.charAt(0) == 0x01 && message.charAt(message.length() - 1) == 0x01;
  }

  private static String hostmaskFromUser(User user) {
    if (user == null) return "";

    String hm = safeStr(user::getHostmask, "");
    if (hm != null && !hm.isBlank()) return hm;

    String nick = safeStr(user::getNick, "");
    String login = safeStr(user::getLogin, "");
    String host = safeStr(user::getHostname, "");

    if (nick == null) nick = "";
    if (login == null) login = "";
    if (host == null) host = "";

    nick = nick.trim();
    login = login.trim();
    host = host.trim();

    if (!nick.isEmpty()) {
      String ident = login.isEmpty() ? "*" : login;
      String h = host.isEmpty() ? "*" : host;
      return nick + "!" + ident + "@" + h;
    }

    return "";
  }

  private static boolean isUsefulHostmask(String hostmask) {
    if (hostmask == null) return false;
    String hm = hostmask.trim();
    if (hm.isEmpty()) return false;

    int bang = hm.indexOf('!');
    int at = hm.indexOf('@');
    if (bang <= 0 || at <= bang + 1 || at >= hm.length() - 1) return false;

    String ident = hm.substring(bang + 1, at).trim();
    String host = hm.substring(at + 1).trim();

    // If both are unknown wildcards, this is just a placeholder derived from NAMES and isn't useful.
    boolean identUnknown = ident.isEmpty() || "*".equals(ident);
    boolean hostUnknown = host.isEmpty() || "*".equals(host);
    return !(identUnknown && hostUnknown);
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
      String v = (identity == null) ? "IRCafe" : identity.version();
      bot.sendIRC().notice(sanitizeNick(fromNick), "\u0001VERSION " + v + "\u0001");
      return true;
    }

    if ("PING".equals(cmd)) {
      // Reply with the same token/payload (if any).
      String payload = "";
      int sp2 = inner.indexOf(' ');
      if (sp2 >= 0 && sp2 + 1 < inner.length()) payload = inner.substring(sp2 + 1).trim();
      String body = payload.isEmpty() ? "\u0001PING\u0001" : "\u0001PING " + payload + "\u0001";
      bot.sendIRC().notice(sanitizeNick(fromNick), body);
      return true;
    }

    if ("TIME".equals(cmd)) {
      // Best-effort local time string; servers/clients vary here.
      String now = java.time.ZonedDateTime.now().toString();
      bot.sendIRC().notice(sanitizeNick(fromNick), "\u0001TIME " + now + "\u0001");
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

  /**
   * PircBotX updates {@link User#isAway()} / {@link User#getAwayMessage()} on IRCv3 away-notify,
   * but does not publish a dedicated hook event for it in the stock event model.
   *
   * <p>We solve this by swapping the bot's {@link InputParser} with a tiny subclass that
   * intercepts the "AWAY" command (as broadcast by away-notify) and emits our own
   * {@link IrcEvent.UserAwayStateObserved} into the app bus.
   */
  private void installAwayNotifyInputParserHook(PircBotX bot, String serverId) {
    if (bot == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    try {
      InputParser replacement = new AwayNotifyInputParser(bot, sid);
      boolean swapped = swapInputParser(bot, replacement);
      if (swapped) {
        log.info("[{}] installed away-notify InputParser hook", sid);
      } else {
        log.warn("[{}] could not install away-notify InputParser hook (no compatible field found)", sid);
      }
    } catch (Exception ex) {
      log.warn("[{}] failed to install away-notify InputParser hook", sid, ex);
    }
  }

  private boolean swapInputParser(PircBotX bot, InputParser replacement) throws Exception {
    Field target = null;
    Class<?> c = bot.getClass();
    while (c != null) {
      for (Field f : c.getDeclaredFields()) {
        if (InputParser.class.isAssignableFrom(f.getType())) {
          target = f;
          break;
        }
      }
      if (target != null) break;
      c = c.getSuperclass();
    }
    if (target == null) return false;

    target.setAccessible(true);
    target.set(bot, replacement);
    return true;
  }

  /**
   * InputParser hook for IRCv3 away-notify.
   *
   * <p>Away-notify arrives as raw lines like:
   * <ul>
   *   <li><code>:nick!user@host AWAY :Gone away for now</code></li>
   *   <li><code>:nick!user@host AWAY</code></li>
   * </ul>
   */
  private final class AwayNotifyInputParser extends InputParser {
    private final String serverId;

    AwayNotifyInputParser(PircBotX bot, String serverId) {
      super(bot);
      this.serverId = serverId;
    }

    @Override
    public void processCommand(
        String target,
        org.pircbotx.UserHostmask source,
        String command,
        String line,
        java.util.List<String> parsedLine,
        com.google.common.collect.ImmutableMap<String, String> tags
    ) throws java.io.IOException {
      // Preserve default behavior first (this keeps User.isAway()/getAwayMessage() accurate).
      super.processCommand(target, source, command, line, parsedLine, tags);

      if (source == null || command == null) return;
      if (!"AWAY".equalsIgnoreCase(command)) return;

      String nick = Objects.toString(source.getNick(), "").trim();
      if (nick.isEmpty()) return;

      // AWAY with a parameter means "now away". AWAY with no parameter means "back".
      // NOTE: In this callback, `command` is already the IRC command and `parsedLine` is the
      // *parameter* list (e.g. [":Gone away"]), not the entire tokenized line. Our earlier
      // size>=3 check was wrong and caused every AWAY-with-reason to be treated as HERE.
      boolean nowAway = (parsedLine != null && !parsedLine.isEmpty());
      IrcEvent.AwayState state = nowAway ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE;

      // Extra diagnostics for this specific gap in PircBotX.
      String msg = null;
      if (nowAway && parsedLine != null && !parsedLine.isEmpty()) {
        // For AWAY, the only parameter is the away message (if present).
        msg = parsedLine.get(0);
        if (msg != null && msg.startsWith(":")) msg = msg.substring(1);
      }
      // Per-event away-notify logs can get noisy; keep at DEBUG.
      log.debug("[{}] away-notify observed via InputParser: nick={} state={} msg={} params={} raw={}",
          serverId, nick, state, msg, parsedLine, line);
      // Opportunistic hostmask capture: away-notify lines include a full prefix (nick!user@host),
      // which we can use to enrich the roster hostmask cache (same propagation path as WHOIS).
      String observedHostmask = null;
      if (line != null && !line.isBlank()) {
        String norm = line;
        // Strip IRCv3 message tags if present (e.g. "@time=...;... :nick!user@host AWAY ...")
        if (norm.startsWith("@")) {
          int sp = norm.indexOf(' ');
          if (sp > 0 && sp < norm.length() - 1) norm = norm.substring(sp + 1);
        }
        if (norm.startsWith(":")) {
          int sp = norm.indexOf(' ');
          if (sp > 1) {
            observedHostmask = norm.substring(1, sp).trim();
          }
        }
      }
      if (isUsefulHostmask(observedHostmask)) {
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.UserHostmaskObserved(Instant.now(), "", nick, observedHostmask)));
      }



      bus.onNext(new ServerIrcEvent(serverId,
          new IrcEvent.UserAwayStateObserved(Instant.now(), nick, state, msg)));
    }
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
      // Passive capture: learn hostmask for userlist/ignore visualization.
      maybeEmitHostmaskObserved(channel, event.getUser());
      String msg = event.getMessage();

      // Hard ignore: optionally includes CTCP depending on config.
      String hostmask = hostmaskFromUser(event.getUser());
      boolean ctcp = isCtcpWrapped(msg);
      if (!hostmask.isEmpty()
          && ignoreListService.isHardIgnored(serverId, hostmask)
          && (ignoreListService.hardIgnoreIncludesCtcp() || !ctcp)) {
        return;
      }

      boolean softIgnored = !hostmask.isEmpty() && ignoreListService.isSoftIgnored(serverId, hostmask);

      String action = parseCtcpAction(msg);
      if (action != null) {
        bus.onNext(new ServerIrcEvent(serverId, softIgnored
            ? new IrcEvent.SoftChannelAction(Instant.now(), channel, event.getUser().getNick(), action)
            : new IrcEvent.ChannelAction(Instant.now(), channel, event.getUser().getNick(), action)
        ));
        return;
      }

      bus.onNext(new ServerIrcEvent(serverId, softIgnored
          ? new IrcEvent.SoftChannelMessage(Instant.now(), channel, event.getUser().getNick(), msg)
          : new IrcEvent.ChannelMessage(Instant.now(), channel, event.getUser().getNick(), msg)
      ));
    }

    @Override
    public void onAction(ActionEvent event) {
      // PircBotX parses CTCP ACTION (/me) into ActionEvent for us.
      touchInbound();
      String from = (event.getUser() != null) ? event.getUser().getNick() : "";
      String action = safeStr(() -> event.getAction(), "");

      String hostmask = (event.getUser() != null) ? hostmaskFromUser(event.getUser()) : "";

      // Hard ignore (CTCP): only applies if configured to include CTCP.
      if (!hostmask.isEmpty() && ignoreListService.hardIgnoreIncludesCtcp()
          && ignoreListService.isHardIgnored(serverId, hostmask)) {
        return;
      }

      boolean softIgnored = !hostmask.isEmpty() && ignoreListService.isSoftIgnored(serverId, hostmask);

      if (event.getChannel() != null) {
        String channel = event.getChannel().getName();
        maybeEmitHostmaskObserved(channel, event.getUser());
        bus.onNext(new ServerIrcEvent(serverId, softIgnored
            ? new IrcEvent.SoftChannelAction(Instant.now(), channel, from, action)
            : new IrcEvent.ChannelAction(Instant.now(), channel, from, action)
        ));
      } else {
        bus.onNext(new ServerIrcEvent(serverId, softIgnored
            ? new IrcEvent.SoftPrivateAction(Instant.now(), from, action)
            : new IrcEvent.PrivateAction(Instant.now(), from, action)
        ));
      }
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

      // Hard ignore: optionally includes CTCP depending on config.
      String hostmask = hostmaskFromUser(event.getUser());
      boolean ctcp = isCtcpWrapped(msg);
      if (!hostmask.isEmpty()
          && ignoreListService.isHardIgnored(serverId, hostmask)
          && (ignoreListService.hardIgnoreIncludesCtcp() || !ctcp)) {
        return;
      }

      boolean softIgnored = !hostmask.isEmpty() && ignoreListService.isSoftIgnored(serverId, hostmask);

      String action = parseCtcpAction(msg);
      if (action != null) {
        bus.onNext(new ServerIrcEvent(serverId, softIgnored
            ? new IrcEvent.SoftPrivateAction(Instant.now(), from, action)
            : new IrcEvent.PrivateAction(Instant.now(), from, action)
        ));
        return;
      }

      if (handleCtcpIfPresent(event.getBot(), from, msg)) return;

      bus.onNext(new ServerIrcEvent(serverId, softIgnored
          ? new IrcEvent.SoftPrivateMessage(Instant.now(), from, msg)
          : new IrcEvent.PrivateMessage(Instant.now(), from, msg)
      ));
    }

    @Override
    public void onNotice(NoticeEvent event) {
      touchInbound();
      String from = (event.getUser() != null) ? event.getUser().getNick() : "server";
      String notice = event.getNotice();

      boolean softIgnored = false;

      // Hard ignore: optionally includes CTCP depending on config.
      if (event.getUser() != null) {
        String hostmask = hostmaskFromUser(event.getUser());
        boolean ctcp = isCtcpWrapped(notice);
        if (!hostmask.isEmpty()
            && ignoreListService.isHardIgnored(serverId, hostmask)
            && (ignoreListService.hardIgnoreIncludesCtcp() || !ctcp)) {
          return;
        }
        softIgnored = !hostmask.isEmpty() && ignoreListService.isSoftIgnored(serverId, hostmask);
      }

      bus.onNext(new ServerIrcEvent(serverId, softIgnored
          ? new IrcEvent.SoftNotice(Instant.now(), from, notice)
          : new IrcEvent.Notice(Instant.now(), from, notice)
      ));
    }



    @Override
    public void onWhois(WhoisEvent event) {
      touchInbound();
      if (event == null) return;

      try {
        String nick = safeStr(() -> event.getNick(), "");
        String login = safeStr(() -> event.getLogin(), "");
        String host = safeStr(() -> event.getHostname(), "");
        String real = safeStr(() -> event.getRealname(), "");
        String server = safeStr(() -> event.getServer(), "");
        String serverInfo = safeStr(() -> event.getServerInfo(), "");
        List<String> channels = safeList(() -> event.getChannels());
        long idleSeconds = safeLong(() -> event.getIdleSeconds(), -1);
        long signOnTime = safeLong(() -> event.getSignOnTime(), -1);
        String registeredAs = safeStr(() -> event.getRegisteredAs(), "");

        // Pre-format into nice, compact lines so the app layer can just print them.
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();

        String ident = login.isBlank() ? "" : login;
        String hostPart = host.isBlank() ? "" : host;
        String userHost = (!ident.isBlank() || !hostPart.isBlank())
            ? (ident + "@" + hostPart).replaceAll("^@|@$", "")
            : "";

        // Passive hostmask enrichment from WHOIS results.
        // WHOIS is user-initiated, so this generates no additional IRC traffic.
        // If we have a useful user@host, treat it as authoritative and push it into the roster cache.
        if (!nick.isBlank() && !userHost.isBlank() && userHost.contains("@")) {
          String observed = nick + "!" + userHost;
          bus.onNext(new ServerIrcEvent(serverId,
              new IrcEvent.UserHostmaskObserved(Instant.now(), "", nick, observed)));
        }

        if (!userHost.isBlank()) lines.add("User: " + userHost);
        if (!real.isBlank()) lines.add("Realname: " + real);
        if (!server.isBlank()) {
          if (!serverInfo.isBlank()) lines.add("Server: " + server + " (" + serverInfo + ")");
          else lines.add("Server: " + server);
        }
        if (!registeredAs.isBlank()) lines.add("Account: " + registeredAs);
        if (idleSeconds >= 0) lines.add("Idle: " + idleSeconds + "s");
        if (signOnTime > 0) lines.add("Sign-on: " + signOnTime);
        if (channels != null && !channels.isEmpty()) lines.add("Channels: " + String.join(" ", channels));

        // Even if we couldn't collect much, still emit something.
        if (lines.isEmpty()) lines.add("(no WHOIS details)");

        String n = nick == null || nick.isBlank() ? "(unknown)" : nick;
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.WhoisResult(Instant.now(), n, java.util.List.copyOf(lines))));
      } catch (Exception ex) {
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Error(Instant.now(), "Whois parse failed", ex)));
      }
    }

    @Override
    public void onUserList(UserListEvent event) {
      emitRoster(event.getChannel());
    }

    @Override
    public void onUnknown(UnknownEvent event) {
      touchInbound();

      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);

      // If we can't fetch the raw line, fall back to toString().
      if (line == null || line.isBlank()) line = String.valueOf(event);

      // Normalize for parsing:
      // - strip IRCv3 message tags ("@tag=value ... ")
      // - unwrap some toString() formats like "UnknownEvent(line=...)"
      String rawLine = normalizeIrcLineForParsing(line);

      // Targeted diagnostics: confirm where IRCv3 away-notify lines are landing.
      // (Users broadcast AWAY state changes as raw AWAY commands.)
      if (rawLine != null && rawLine.contains(" AWAY") && log.isDebugEnabled()) {
        log.debug("[{}] inbound AWAY-ish line received in onUnknown: {}", serverId, rawLine);
      }

      // IRCv3 away-notify: users broadcast AWAY state changes as raw AWAY commands.
      // Example: ":nick!user@host AWAY :Gone away for now"  (sets AWAY)
      //          ":nick!user@host AWAY"                    (clears AWAY)
      ParsedAwayNotify awayNotify = parseAwayNotify(rawLine);
      if (awayNotify != null && awayNotify.nick() != null && !awayNotify.nick().isBlank()) {
        log.debug("[{}] parsed away-notify: nick={} state={} msg={}", serverId, awayNotify.nick(), awayNotify.awayState(), awayNotify.message());
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.UserAwayStateObserved(Instant.now(), awayNotify.nick(), awayNotify.awayState(), awayNotify.message())));
        return;
      } else if (rawLine != null && rawLine.contains(" AWAY") && log.isDebugEnabled()) {
        // If we see an AWAY-ish line here but fail to parse it, that's the smoking gun.
        log.debug("[{}] inbound AWAY-ish line did NOT parse as away-notify: {}", serverId, rawLine);
      }

      ParsedRpl324 parsed = parseRpl324(rawLine);
      if (parsed != null) {
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.ChannelModesListed(Instant.now(), parsed.channel(), parsed.details())));
        return;
      }

      ParsedAwayConfirmation away = parseRpl305or306Away(rawLine);
      if (away != null) {
        // RPL_UNAWAY (305), RPL_NOWAWAY (306)
        String msg = away.message();
        if (msg == null || msg.isBlank()) {
          msg = away.away() ? "You have been marked as being away" : "You are no longer marked as being away";
        }
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.AwayStatusChanged(Instant.now(), away.away(), msg)));
        return;
      }

      // RPL_USERHOST (302): used by our low-traffic hostmask resolver.
      java.util.List<UserhostEntry> uh = parseRpl302Userhost(rawLine);
      if (uh != null && !uh.isEmpty()) {
        Instant now = Instant.now();
        for (UserhostEntry e : uh) {
          if (e == null) continue;
          // Channel-agnostic; TargetCoordinator will propagate across channels.
          bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.UserHostmaskObserved(now, "", e.nick(), e.hostmask())));

          // USERHOST also carries an away marker (+/-). Opportunistically propagate away state too,
          // so a hostmask lookup can update the roster state without requiring WHOIS.
          IrcEvent.AwayState as = (e.awayState() == null) ? IrcEvent.AwayState.UNKNOWN : e.awayState();
          if (as == IrcEvent.AwayState.AWAY || as == IrcEvent.AwayState.HERE) {
            bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.UserAwayStateObserved(now, e.nick(), as)));
          }
        }
      }

      // WHOIS fallback: some networks/bots may not surface a complete WhoisEvent through PircBotX,
      // but the numerics still arrive. Parse the key ones here so manual WHOIS can still populate
      // the user list hostmask cache.
      // WHOIS away-state capture: parse RPL_AWAY (301) and RPL_ENDOFWHOIS (318) so a manual WHOIS
      // can enrich the user list with best-effort away markers.
      ParsedWhoisAway whoisAway = parseRpl301WhoisAway(rawLine);
      if (whoisAway != null && whoisAway.nick() != null && !whoisAway.nick().isBlank()) {
        String nk = whoisAway.nick().trim();
        String k = nk.toLowerCase(java.util.Locale.ROOT);
        // Only mark the WHOIS probe as "saw away" if we actually initiated a WHOIS for this nick.
        conn.whoisSawAwayByNickLower.computeIfPresent(k, (kk, vv) -> Boolean.TRUE);
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.UserAwayStateObserved(Instant.now(), nk, IrcEvent.AwayState.AWAY, whoisAway.message())));
      }

      String endWhoisNick = parseRpl318EndOfWhoisNick(rawLine);
      if (endWhoisNick != null && !endWhoisNick.isBlank()) {
        String nk = endWhoisNick.trim();
        Boolean sawAway = conn.whoisSawAwayByNickLower.remove(nk.toLowerCase(java.util.Locale.ROOT));
        // Only infer HERE when this 318 completes a WHOIS probe we initiated.
        if (sawAway != null && !sawAway.booleanValue()) {
          bus.onNext(new ServerIrcEvent(serverId,
              new IrcEvent.UserAwayStateObserved(Instant.now(), nk, IrcEvent.AwayState.HERE)));
        }
      }

      ParsedWhoisUser whoisUser = parseRpl311WhoisUser(rawLine);
      if (whoisUser == null) whoisUser = parseRpl314WhowasUser(rawLine);
      if (whoisUser != null && !whoisUser.nick().isBlank() && !whoisUser.user().isBlank() && !whoisUser.host().isBlank()) {
        String hm = whoisUser.nick() + "!" + whoisUser.user() + "@" + whoisUser.host();
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.UserHostmaskObserved(Instant.now(), "", whoisUser.nick(), hm)));
      }

      // WHO fallback: parse WHOREPLY (352) lines. We don't actively issue WHO queries by default
      // (to keep traffic low), but if a user runs WHO manually, or a future resolver uses WHO/WHOX,
      // we can still learn hostmasks from the replies.
      ParsedWhoReply whoReply = parseRpl352WhoReply(rawLine);
      if (whoReply != null
          && !whoReply.channel().isBlank()
          && !whoReply.nick().isBlank()
          && !whoReply.user().isBlank()
          && !whoReply.host().isBlank()) {
        String hm = whoReply.nick() + "!" + whoReply.user() + "@" + whoReply.host();
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.UserHostmaskObserved(Instant.now(), whoReply.channel(), whoReply.nick(), hm)));
      }
      // WHOX fallback: parse RPL_WHOSPCRPL (354) lines (WHOX). Format varies based on requested fields,
      // so we apply a conservative heuristic to extract channel/user/host/nick when present.
      ParsedWhoxReply whox = parseRpl354WhoxReply(rawLine);
      if (whox != null
          && !whox.nick().isBlank()
          && !whox.user().isBlank()
          && !whox.host().isBlank()) {
        String hm = whox.nick() + "!" + whox.user() + "@" + whox.host();
        String ch = (whox.channel() == null) ? "" : whox.channel();
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.UserHostmaskObserved(Instant.now(), ch, whox.nick(), hm)));
      }

    }

    @Override
    public void onServerResponse(ServerResponseEvent event) {
      // Some numerics are handled by PircBotX as ServerResponseEvent (not UnknownEvent).
      // We specifically care about away confirmations (305/306).
      touchInbound();

      int code;
      try {
        code = event.getCode();
      } catch (Exception ex) {
        // Extremely defensive: if we can't get a code, bail.
        return;
      }

      if (code == 301 || code == 318) {
        // WHOIS away-state capture:
        // - 301 indicates the user is AWAY
        // - 318 indicates end-of-WHOIS; if we never saw 301 for this nick, infer HERE

        String line = null;
        Object l = reflectCall(event, "getLine");
        if (l == null) l = reflectCall(event, "getRawLine");
        if (l != null) line = String.valueOf(l);
        if (line == null || line.isBlank()) line = String.valueOf(event);

        if (code == 301) {
          ParsedWhoisAway whoisAway = parseRpl301WhoisAway(line);
          if (whoisAway != null && whoisAway.nick() != null && !whoisAway.nick().isBlank()) {
            String nk = whoisAway.nick().trim();
            String k = nk.toLowerCase(java.util.Locale.ROOT);
            conn.whoisSawAwayByNickLower.computeIfPresent(k, (kk, vv) -> Boolean.TRUE);
            bus.onNext(new ServerIrcEvent(serverId,
                new IrcEvent.UserAwayStateObserved(Instant.now(), nk, IrcEvent.AwayState.AWAY, whoisAway.message())));
          }
        } else {
          String nk = parseRpl318EndOfWhoisNick(line);
          if (nk != null && !nk.isBlank()) {
            nk = nk.trim();
            Boolean sawAway = conn.whoisSawAwayByNickLower.remove(nk.toLowerCase(java.util.Locale.ROOT));
            if (sawAway != null && !sawAway.booleanValue()) {
              bus.onNext(new ServerIrcEvent(serverId,
                  new IrcEvent.UserAwayStateObserved(Instant.now(), nk, IrcEvent.AwayState.HERE)));
            }
          }
        }
        return;
      }

      if (code != 305 && code != 306) return;

      // Prefer a raw line when available so we can reuse the same parser.
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);

      ParsedAwayConfirmation away = parseRpl305or306Away(line);
      boolean isAway = (code == 306);
      String msg = null;
      if (away != null) {
        isAway = away.away();
        msg = away.message();
      }
      if (msg == null || msg.isBlank()) {
        msg = isAway ? "You have been marked as being away" : "You are no longer marked as being away";
      }

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.AwayStatusChanged(Instant.now(), isAway, msg)));
    }

    private record ParsedRpl324(String channel, String details) {}

    /** Parsed IRCv3 away-notify line (AWAY command from another user). */
    private record ParsedAwayNotify(String nick, IrcEvent.AwayState awayState, String message) {}

  /**
   * Best-effort normalization for server lines we parse ourselves.
   * <ul>
   *   <li>Strips IRCv3 message tags (leading "@tag=value;... ")</li>
   *   <li>Unwraps some toString() formats like "UnknownEvent(line=...)"</li>
   * </ul>
   */
  private static String normalizeIrcLineForParsing(String raw) {
    if (raw == null) return null;
    String s = raw.trim();

    // Some PircBotX/ListenerManager implementations (or fallbacks to toString())
    // may format unknown lines like: "UnknownEvent(line=:nick!u@h AWAY :msg)".
    // Try to unwrap to the actual IRC line.
    int idx = s.indexOf("line=");
    int keyLen = 5;
    if (idx < 0) {
      idx = s.indexOf("rawLine=");
      keyLen = 8;
    }
    if (idx >= 0) {
      String sub = s.substring(idx + keyLen).trim();
      // Strip trailing wrapper chars, but keep whitespace inside the IRC line intact.
      while (!sub.isEmpty()) {
        char last = sub.charAt(sub.length() - 1);
        if (last == ')' || last == '}' || last == ']') sub = sub.substring(0, sub.length() - 1).trim();
        else break;
      }
      if (sub.startsWith("\"") && sub.endsWith("\"") && sub.length() >= 2) {
        sub = sub.substring(1, sub.length() - 1);
      }
      s = sub;
    }

    // IRCv3 message tags: "@aaa=bbb;ccc :prefix COMMAND ..."
    if (s.startsWith("@")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) {
        s = s.substring(sp + 1).trim();
      }
    }

    return s;
  }

    private record ParsedAwayConfirmation(boolean away, String server, String message) {}

    private record ParsedWhoisUser(String nick, String user, String host) {}

    private record ParsedWhoisAway(String nick, String message) {}

    private record ParsedWhoReply(String channel, String nick, String user, String host) {}

    private record ParsedWhoxReply(String channel, String nick, String user, String host) {}

    private record UserhostEntry(String nick, String hostmask, IrcEvent.AwayState awayState) {}

    /**
     * Parse RPL_WHOISUSER (311) lines.
     *
     * <p>Format: ":server 311 <me> <nick> <user> <host> * :<realname>"
     */
    private static ParsedWhoisUser parseRpl311WhoisUser(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (s.isEmpty()) return null;

      // Drop prefix (e.g., ":server ")
      if (s.startsWith(":")) {
        int sp = s.indexOf(' ');
        if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
      }

      String[] toks = s.split("\\s+");
      if (toks.length < 5) return null;
      if (!"311".equals(toks[0])) return null;

      // 311 <me> <nick> <user> <host> ...
      String nick = toks[2];
      String user = toks[3];
      String host = toks[4];
      if (nick == null || nick.isBlank() || user == null || user.isBlank() || host == null || host.isBlank()) return null;
      return new ParsedWhoisUser(nick, user, host);
    }

    /**
     * Parse RPL_WHOWASUSER (314) lines.
     *
     * <p>Format: ":server 314 <me> <nick> <user> <host> * :<realname>"
     */
    private static ParsedWhoisUser parseRpl314WhowasUser(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (s.isEmpty()) return null;

      // Drop prefix (e.g., ":server ")
      if (s.startsWith(":")) {
        int sp = s.indexOf(' ');
        if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
      }

      String[] toks = s.split("\\s+");
      if (toks.length < 5) return null;
      if (!"314".equals(toks[0])) return null;

      // 314 <me> <nick> <user> <host> ...
      String nick = toks[2];
      String user = toks[3];
      String host = toks[4];
      if (nick == null || nick.isBlank() || user == null || user.isBlank() || host == null || host.isBlank()) return null;
      return new ParsedWhoisUser(nick, user, host);
    }



    /**
     * Parse RPL_AWAY (301) lines received during WHOIS.
     *
     * <p>Format: ":server 301 <me> <nick> :<away message>"
     */
    private static ParsedWhoisAway parseRpl301WhoisAway(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (s.isEmpty()) return null;

      // Drop prefix (e.g., ":server ")
      if (s.startsWith(":")) {
        int sp = s.indexOf(' ');
        if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
      }

      if (!s.startsWith("301 ") && !s.equals("301") && !s.startsWith("301	")) {
        // Token-based check below will handle most cases; this is just a small fast-path.
      }

      String[] toks = s.split("\\s+");
      if (toks.length < 3) return null;
      if (!"301".equals(toks[0])) return null;

      String nick = toks[2];
      if (nick == null || nick.isBlank()) return null;

      String msg = null;
      int colon = s.indexOf(" :");
      if (colon >= 0 && colon + 2 < s.length()) {
        msg = s.substring(colon + 2).trim();
      }
      return new ParsedWhoisAway(nick, msg);
    }

    /**
     * Parse RPL_ENDOFWHOIS (318) lines.
     *
     * <p>Format: ":server 318 <me> <nick> :End of /WHOIS list."
     */
    private static String parseRpl318EndOfWhoisNick(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (s.isEmpty()) return null;

      // Drop prefix (e.g., ":server ")
      if (s.startsWith(":")) {
        int sp = s.indexOf(' ');
        if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
      }

      String[] toks = s.split("\\s+");
      if (toks.length < 3) return null;
      if (!"318".equals(toks[0])) return null;

      String nick = toks[2];
      if (nick == null || nick.isBlank()) return null;
      return nick;
    }
    /**
     * Parse RPL_WHOREPLY (352) lines.
     *
     * <p>Common format: ":server 352 <me> <channel> <user> <host> <server> <nick> <flags> :<hopcount> <realname>"
     */
    private static ParsedWhoReply parseRpl352WhoReply(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (s.isEmpty()) return null;

      // Drop prefix (e.g., ":server ")
      if (s.startsWith(":")) {
        int sp = s.indexOf(' ');
        if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
      }

      String[] toks = s.split("\\s+");
      if (toks.length < 8) return null;
      if (!"352".equals(toks[0])) return null;

      // 352 <me> <channel> <user> <host> <server> <nick> <flags> ...
      String channel = toks[2];
      String user = toks[3];
      String host = toks[4];
      String nick = toks[6];

      if (channel == null || channel.isBlank()) return null;
      if (nick == null || nick.isBlank()) return null;
      if (user == null || user.isBlank()) return null;
      if (host == null || host.isBlank()) return null;

      return new ParsedWhoReply(channel, nick, user, host);
    }


    /**
     * Parse RPL_WHOSPCRPL (354) / WHOX lines.
     *
     * <p>Format varies based on the requested WHOX fields (see the WHOX extension). We use a conservative heuristic:
     * <ul>
     *   <li>Strip the server prefix</li>
     *   <li>Tokenize up to the trailing ":" parameter (realname)</li>
     *   <li>Capture an optional querytype (integer) and optional channel token</li>
     *   <li>Find a (user, host) pair (adjacent or separated by an IP field)</li>
     *   <li>Then pick the first plausible nick token after that host (skipping host-like tokens such as server names)</li>
     * </ul>
     *
     * <p>If we cannot confidently extract user/host/nick, returns null.
     */
    private static ParsedWhoxReply parseRpl354WhoxReply(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (s.isEmpty()) return null;

      // Drop prefix (e.g., ":server ")
      if (s.startsWith(":")) {
        int sp = s.indexOf(' ');
        if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
      }

      // Remove trailing ":" parameter (usually realname)
      int colon = s.indexOf(" :");
      String head = colon >= 0 ? s.substring(0, colon).trim() : s;

      String[] toks = head.split("\\s+");
      if (toks.length < 3) return null;
      if (!"354".equals(toks[0])) return null;

      // toks: 354 <me> <fields...>
      java.util.List<String> fields = new java.util.ArrayList<>();
      for (int i = 2; i < toks.length; i++) {
        String t = toks[i];
        if (t == null || t.isBlank()) continue;
        fields.add(t);
      }
      if (fields.isEmpty()) return null;

      // Optional querytype at start
      int idx = 0;
      if (looksNumeric(fields.get(0))) idx++;

      String channel = "";
      if (idx < fields.size() && looksLikeChannel(fields.get(idx))) {
        channel = fields.get(idx);
      } else {
        // Channel might appear later depending on requested fields.
        for (String f : fields) {
          if (looksLikeChannel(f)) {
            channel = f;
            break;
          }
        }
      }

      // Find a user/host pair.
      int userIdx = -1;
      int hostIdx = -1;
      for (int i = 0; i < fields.size(); i++) {
        String a = fields.get(i);
        if (!looksLikeUser(a)) continue;

        if (i + 1 < fields.size()) {
          String b = fields.get(i + 1);
          if (looksLikeHost(b) && !looksLikeChannel(b) && !looksNumeric(b)) {
            userIdx = i;
            hostIdx = i + 1;
            break;
          }
        }
        if (i + 2 < fields.size()) {
          String b = fields.get(i + 1);
          String c = fields.get(i + 2);
          if (looksLikeIp(b) && looksLikeHost(c) && !looksLikeChannel(c) && !looksNumeric(c)) {
            userIdx = i;
            hostIdx = i + 2;
            break;
          }
        }
      }
      if (userIdx < 0 || hostIdx < 0) return null;

      String user = fields.get(userIdx);
      String host = fields.get(hostIdx);
      if (user == null || user.isBlank() || host == null || host.isBlank()) return null;

      // Find nick after host, skipping server/host-like tokens, flags/hops, etc.
      String nick = null;
      for (int j = hostIdx + 1; j < fields.size(); j++) {
        String t = fields.get(j);
        if (t == null || t.isBlank()) continue;
        if (looksNumeric(t)) continue;
        if (looksLikeChannel(t)) continue;
        if (looksLikeHost(t) || looksLikeIp(t)) continue; // likely server name / IP
        if (!looksLikeNick(t)) continue;
        nick = t;
        break;
      }
      if (nick == null || nick.isBlank()) return null;

      String hm = nick + "!" + user + "@" + host;
      if (!isUsefulHostmask(hm)) return null;

      return new ParsedWhoxReply(channel, nick, user, host);
    }

    private static boolean looksNumeric(String s) {
      if (s == null || s.isBlank()) return false;
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c < '0' || c > '9') return false;
      }
      return true;
    }

    private static boolean looksLikeChannel(String s) {
      if (s == null || s.isBlank()) return false;
      char c = s.charAt(0);
      return c == '#' || c == '&';
    }

    private static boolean looksLikeUser(String s) {
      if (s == null || s.isBlank()) return false;
      if (looksLikeChannel(s)) return false;
      if (looksNumeric(s)) return false;
      if (s.indexOf('!') >= 0 || s.indexOf('@') >= 0) return false;
      // Usernames are typically short-ish and don't contain spaces or colons.
      if (s.indexOf(':') >= 0) return false;
      if (s.length() > 64) return false;
      return true;
    }

    private static boolean looksLikeHost(String s) {
      if (s == null || s.isBlank()) return false;
      if (looksLikeChannel(s)) return false;
      if (s.indexOf('!') >= 0 || s.indexOf('@') >= 0) return false;
      // Hostnames (or vhost/gateway strings) usually contain '.', ':', or '/'.
      return (s.indexOf('.') >= 0) || (s.indexOf(':') >= 0) || (s.indexOf('/') >= 0);
    }

    private static boolean looksLikeIp(String s) {
      if (s == null || s.isBlank()) return false;
      // IPv4
      if (s.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) return true;
      // IPv6 (very loose)
      return s.indexOf(':') >= 0 && s.matches("[0-9A-Fa-f:]+");
    }

    private static boolean looksLikeNick(String s) {
      if (s == null || s.isBlank()) return false;
      if (looksLikeChannel(s)) return false;
      if (looksNumeric(s)) return false;
      // Loose IRC nick pattern; allow '.' and '-' for permissive networks.
      return s.matches("[A-Za-z\\[\\]\\\\`_\\^\\{\\|\\}][A-Za-z0-9\\-\\.\\[\\]\\\\`_\\^\\{\\|\\}]*");
    }


    private static ParsedRpl324 parseRpl324(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (s.isEmpty()) return null;

      // Drop prefix (e.g., ":server ")
      if (s.startsWith(":")) {
        int sp = s.indexOf(' ');
        if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
      }

      String[] toks = s.split("\\s+");
      if (toks.length < 4) return null;

      // Format: 324 <me> <#chan> <modes> [args...]
      if (!"324".equals(toks[0])) return null;

      String channel = toks[2];
      if (channel == null || channel.isBlank()) return null;

      StringBuilder details = new StringBuilder();
      for (int i = 3; i < toks.length; i++) {
        if (i > 3) details.append(' ');
        details.append(toks[i]);
      }

      return new ParsedRpl324(channel, details.toString());
    }

    /**
     * Parse an IRCv3 away-notify line, which is delivered as a raw {@code AWAY} command
     * from another user.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code :nick!user@host AWAY :Gone away for now} (sets away)</li>
     *   <li>{@code :nick!user@host AWAY} (clears away)</li>
     * </ul>
     */
    private static ParsedAwayNotify parseAwayNotify(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (!s.startsWith(":")) return null;

      // Extract prefix up to first space.
      int sp = s.indexOf(' ');
      if (sp <= 1 || sp + 1 >= s.length()) return null;
      String prefix = s.substring(1, sp);
      String rest = s.substring(sp + 1).trim();

      if (!(rest.startsWith("AWAY") && (rest.length() == 4 || Character.isWhitespace(rest.charAt(4))))) {
        return null;
      }

      String nick = prefix;
      int bang = nick.indexOf('!');
      if (bang > 0) nick = nick.substring(0, bang);
      nick = nick.trim();
      if (nick.isBlank()) return null;

      // RFC: AWAY with a parameter sets away; AWAY with no parameter clears it.
      // away-notify SHOULD follow that; however, be resilient to weird formatting.
      boolean hasParam = rest.length() > 4;
      IrcEvent.AwayState state = hasParam ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE;

      String msg = null;
      if (state == IrcEvent.AwayState.AWAY) {
        String rem = rest.substring(4).trim();
        if (rem.startsWith(":")) rem = rem.substring(1).trim();
        msg = rem.isEmpty() ? null : rem;
      }

      return new ParsedAwayNotify(nick, state, msg);
    }


    /**
     * Parse RPL_UNAWAY (305) / RPL_NOWAWAY (306) lines and extract the trailing message.
     *
     * <p>Typical format: ":server 305 <me> :You are no longer marked as being away"
     * <br>Typical format: ":server 306 <me> :You have been marked as being away"
     */
    private static ParsedAwayConfirmation parseRpl305or306Away(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (s.isEmpty()) return null;

      String server = "";
      // Capture and drop prefix (e.g., ":server ")
      if (s.startsWith(":")) {
        int sp = s.indexOf(' ');
        if (sp > 1) {
          server = s.substring(1, sp);
          if (sp + 1 < s.length()) s = s.substring(sp + 1).trim();
        }
      }

      boolean is305 = s.startsWith("305 ") || s.startsWith("305\t") || s.equals("305");
      boolean is306 = s.startsWith("306 ") || s.startsWith("306\t") || s.equals("306");
      if (!is305 && !is306) return null;

      boolean away = is306;

      // Prefer IRC trailing parameter extraction (space-colon).
      String msg = null;
      int trail = s.indexOf(" :");
      if (trail >= 0 && trail + 2 < s.length()) {
        msg = s.substring(trail + 2).trim();
      } else {
        // Fallback: join tokens after the numeric + target.
        String[] toks = s.split("\\s+");
        if (toks.length >= 3) {
          msg = String.join(" ", java.util.Arrays.copyOfRange(toks, 2, toks.length)).trim();
        }
      }

      return new ParsedAwayConfirmation(away, server, msg);
    }

    /**
     * Parse RPL_USERHOST (302) lines.
     *
     * <p>Format: ":server 302 <me> :nick[\*]=[+|-]user@host ..."
     */
    private static java.util.List<UserhostEntry> parseRpl302Userhost(String line) {
      if (line == null) return null;
      String s = line.trim();
      if (s.isEmpty()) return null;

      // Drop prefix (e.g., ":server ")
      if (s.startsWith(":")) {
        int sp = s.indexOf(' ');
        if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
      }

      // Quick check
      if (!s.startsWith("302 ") && !s.startsWith("302\t") && !s.startsWith("302\n")) {
        // Sometimes the numeric is not at offset 0 due to stray formatting; fall back to token check.
      }

      String[] toks = s.split("\\s+");
      if (toks.length < 4) return null;
      if (!"302".equals(toks[0])) return null;

      int colon = s.indexOf(" :");
      if (colon < 0 || colon + 2 >= s.length()) return null;
      String payload = s.substring(colon + 2).trim();
      if (payload.isEmpty()) return null;

      java.util.List<UserhostEntry> out = new java.util.ArrayList<>();
      for (String part : payload.split("\\s+")) {
        if (part == null || part.isBlank()) continue;
        String p = part.trim();
        if (p.startsWith(":")) p = p.substring(1);

        int eq = p.indexOf('=');
        if (eq <= 0 || eq >= p.length() - 1) continue;

        String nickPart = p.substring(0, eq).trim();
        if (nickPart.endsWith("*")) nickPart = nickPart.substring(0, nickPart.length() - 1);
        String nick = nickPart.trim();
        if (nick.isEmpty()) continue;

        String rhs = p.substring(eq + 1).trim();
        if (rhs.isEmpty()) continue;

        // Strip away/available marker.
        // Per RPL_USERHOST (302), '+' means not away and '-' means away.
        IrcEvent.AwayState as = IrcEvent.AwayState.UNKNOWN;
        if (rhs.charAt(0) == '+' || rhs.charAt(0) == '-') {
          as = (rhs.charAt(0) == '-') ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE;
          rhs = rhs.substring(1);
        }

        int at = rhs.indexOf('@');
        if (at <= 0 || at >= rhs.length() - 1) continue;
        String user = rhs.substring(0, at).trim();
        String host = rhs.substring(at + 1).trim();
        if (user.isEmpty() || host.isEmpty()) continue;

        String hm = nick + "!" + user + "@" + host;
        if (!isUsefulHostmask(hm)) continue;
        out.add(new UserhostEntry(nick, hm, as));
      }

      return out.isEmpty() ? null : java.util.List.copyOf(out);
    }

    @Override
    public void onJoin(JoinEvent event) {
      touchInbound();
      Channel channel = event.getChannel();
      if (channel != null) maybeEmitHostmaskObserved(channel.getName(), event.getUser());

      String nick = event.getUser() == null ? null : event.getUser().getNick();

      if (isSelf(event.getBot(), nick)) {
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.JoinedChannel(Instant.now(), channel.getName())));
      } else {
        // Show other users joining inline in the channel transcript.
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.UserJoinedChannel(
            Instant.now(),
            channel.getName(),
            nick
        )));
      }

      emitRoster(channel);
    }

    @Override
    public void onPart(PartEvent event) {
      touchInbound();
      try {
        maybeEmitHostmaskObserved(event.getChannelName(), event.getUser());
      } catch (Exception ignored) {}
      try {
        String nick = event.getUser() == null ? null : event.getUser().getNick();
        if (!isSelf(event.getBot(), nick)) {
          bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.UserPartedChannel(
              Instant.now(),
              event.getChannelName(),
              nick,
              event.getReason()
          )));
        }
      } catch (Exception ignored) {}
      refreshRosterByName(event.getBot(), event.getChannelName());
    }

    @Override
    public void onQuit(QuitEvent event) {
      touchInbound();
      PircBotX bot = event.getBot();

      String nick = null;
      try {
        nick = event.getUser() == null ? null : event.getUser().getNick();
      } catch (Exception ignored) {}
      String reason = null;
      try {
        reason = event.getReason();
      } catch (Exception ignored) {}

      boolean refreshedSome = false;
      try {
        UserChannelDaoSnapshot daoSnap = event.getUserChannelDaoSnapshot();
        UserSnapshot userSnap = event.getUser();

        if (daoSnap != null && userSnap != null) {
          for (ChannelSnapshot cs : daoSnap.getChannels(userSnap)) {
            try {
              bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.UserQuitChannel(
                  Instant.now(),
                  cs.getName(),
                  nick,
                  reason
              )));
            } catch (Exception ignored) {}
            refreshRosterByName(bot, cs.getName());
            refreshedSome = true;
          }
        }
      } catch (Exception ignored) {

      }

      if (!refreshedSome) {
        try {
          // Best-effort: if we can still see the user's channels in the DAO, emit per-channel quit events.
          try {
            if (event.getUser() != null) {
              for (Channel ch : bot.getUserChannelDao().getChannels(event.getUser())) {
                bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.UserQuitChannel(
                    Instant.now(),
                    ch.getName(),
                    nick,
                    reason
                )));
              }
            }
          } catch (Exception ignored) {}

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

    private void maybeEmitHostmaskObserved(String channel, User user) {
      if (channel == null || channel.isBlank() || user == null) return;
      String nick = safeStr(user::getNick, "");
      if (nick == null || nick.isBlank()) return;

      String hm = hostmaskFromUser(user);
      if (!isUsefulHostmask(hm)) return;

      String key = nick.trim().toLowerCase(Locale.ROOT);
      String prev = conn.lastHostmaskByNickLower.put(key, hm);
      if (Objects.equals(prev, hm)) return; // no change

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.UserHostmaskObserved(
          Instant.now(), channel, nick.trim(), hm
      )));
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
          // Emit per-channel nick-change lines so the transcript can fold them.
          try {
            bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.UserNickChangedChannel(
                Instant.now(),
                ch.getName(),
                event.getOldNick(),
                event.getNewNick()
            )));
          } catch (Exception ignored) {}
          emitRoster(ch);
        }
      } catch (Exception ignored) {}
    }

    @Override
    public void onMode(ModeEvent event) {
      touchInbound();
      if (event == null) return;
      if (event.getChannel() == null) return;

      emitRoster(event.getChannel());

      String chan = event.getChannel().getName();
      String by = nickFromEvent(event);
      String details = modeDetailsFromEvent(event, chan);

      if (details != null && !details.isBlank()) {
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.ChannelModeChanged(
            Instant.now(),
            chan,
            by,
            details
        )));
      }
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


    private static java.util.Set<?> setOrEmpty(Channel channel, String method) {
      if (channel == null || method == null) return java.util.Set.of();
      try {
        java.lang.reflect.Method m = channel.getClass().getMethod(method);
        Object v = m.invoke(channel);
        if (v instanceof java.util.Set<?> s) return s;
      } catch (Exception ignored) {}
      return java.util.Set.of();
    }

    private static String prefixForUser(Object user, java.util.Set<?> owners, java.util.Set<?> admins,
                                        java.util.Set<?> ops, java.util.Set<?> halfOps, java.util.Set<?> voices) {
      if (user == null) return "";
      if (owners != null && owners.contains(user)) return "~";
      if (admins != null && admins.contains(user)) return "&";
      if (ops != null && ops.contains(user)) return "@";
      if (halfOps != null && halfOps.contains(user)) return "%";
      if (voices != null && voices.contains(user)) return "+";
      return "";
    }

    private static int prefixRank(String prefix) {
      if (prefix == null || prefix.isBlank()) return 99;
      return switch (prefix.charAt(0)) {
        case '~' -> 0;
        case '&' -> 1;
        case '@' -> 2;
        case '%' -> 3;
        case '+' -> 4;
        default -> 10;
      };
    }

    private static boolean isOperatorLike(IrcEvent.NickInfo n) {
      if (n == null) return false;
      String p = n.prefix();
      if (p == null || p.isBlank()) return false;
      return p.indexOf('~') >= 0 || p.indexOf('&') >= 0 || p.indexOf('@') >= 0;
    }

    private static String nickFromEvent(Object event) {
      if (event == null) return null;

      Object user = reflectCall(event, "getUser");
      if (user == null) user = reflectCall(event, "getSource");
      if (user == null) user = reflectCall(event, "getSetter");

      if (user != null) {
        Object nick = reflectCall(user, "getNick");
        if (nick != null) return String.valueOf(nick);
      }

      // Some services (e.g. ChanServ) may not be represented as a User; recover from raw line.
      String rawNick = nickFromRawLine(event);
      if (rawNick != null && !rawNick.isBlank()) return rawNick;

      return null;
    }

    private static String nickFromRawLine(Object event) {
      if (event == null) return null;
      Object raw = reflectCall(event, "getRawLine");
      if (raw == null) raw = reflectCall(event, "getRaw");
      if (raw == null) raw = reflectCall(event, "getLine");
      if (raw == null) return null;

      String line = String.valueOf(raw).trim();
      if (!line.startsWith(":")) return null;

      int sp = line.indexOf(' ');
      if (sp <= 1) return null;

      String prefix = line.substring(1, sp);
      int bang = prefix.indexOf('!');
      if (bang >= 0) prefix = prefix.substring(0, bang);
      return prefix;
    }

    private static String modeDetailsFromEvent(Object event, String channelName) {
      if (event == null) return null;

      // Common method names across libraries/versions.
      Object mode = reflectCall(event, "getMode");
      if (mode == null) mode = reflectCall(event, "getModeLine");
      if (mode == null) mode = reflectCall(event, "getModeString");
      String s = (mode != null) ? String.valueOf(mode) : null;

      // Last-resort: try raw line.
      if (s == null) {
        Object raw = reflectCall(event, "getRawLine");
        if (raw != null) s = String.valueOf(raw);
      }

      if (s == null) return null;

      // If this is a raw MODE line, reduce it to just "<modes> [args...]"
      String reduced = extractModeDetails(s, channelName);
      return (reduced != null) ? reduced : s;
    }

    private static String extractModeDetails(String rawOrLine, String channelName) {
      if (rawOrLine == null) return null;
      String line = rawOrLine.trim();
      if (line.isEmpty()) return null;

      String l = line;
      if (l.startsWith(":")) {
        int sp = l.indexOf(' ');
        if (sp > 0) l = l.substring(sp + 1).trim();
      }

      String[] toks = l.split("\s+");
      for (int i = 0; i < toks.length; i++) {
        if ("MODE".equalsIgnoreCase(toks[i])) {
          int idx = i + 2; // MODE <chan> <modes...>
          if (idx <= toks.length - 1) {
            StringBuilder sb = new StringBuilder();
            for (int j = idx; j < toks.length; j++) {
              if (j > idx) sb.append(' ');
              sb.append(toks[j]);
            }
            String r = sb.toString().trim();
            return r.isEmpty() ? null : r;
          }
          return null;
        }
      }

      // Sometimes the library returns "<chan> <modes...>"
      if (channelName != null && !channelName.isBlank()) {
        String ch = channelName.trim();
        if (line.startsWith(ch + " ")) return line.substring(ch.length()).trim();
      }

      return null;
    }

    private static Object reflectCall(Object target, String method) {
      if (target == null || method == null) return null;
      try {
        java.lang.reflect.Method m = target.getClass().getMethod(method);
        return m.invoke(target);
      } catch (Exception ignored) {
        return null;
      }
    }

    private void emitRoster(Channel channel) {
      if (channel == null) return;

      String channelName = channel.getName();

      // Try to gather privilege sets via reflection so we support multiple pircbotx versions gracefully.
      java.util.Set<?> owners = setOrEmpty(channel, "getOwners");
      java.util.Set<?> admins = setOrEmpty(channel, "getSuperOps");
      java.util.Set<?> ops = setOrEmpty(channel, "getOps");
      java.util.Set<?> halfOps = setOrEmpty(channel, "getHalfOps");
      java.util.Set<?> voices = setOrEmpty(channel, "getVoices");

      List<IrcEvent.NickInfo> nicks = channel.getUsers().stream()
          .map(u -> new IrcEvent.NickInfo(
              u.getNick(),
              prefixForUser(u, owners, admins, ops, halfOps, voices),
              hostmaskFromUser(u)
          ))
          .sorted(Comparator
              .comparingInt((IrcEvent.NickInfo n) -> prefixRank(n.prefix()))
              .thenComparing(IrcEvent.NickInfo::nick, String.CASE_INSENSITIVE_ORDER))
          .toList();

      int totalUsers = nicks.size();
      int operatorCount = (int) nicks.stream().filter(BridgeListener::isOperatorLike).count();

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
