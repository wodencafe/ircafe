package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.config.ZncProperties;
import cafe.woden.ircclient.irc.znc.ZncLoginParts;
import cafe.woden.ircclient.irc.znc.ZncEphemeralNetworkImporter;
import cafe.woden.ircclient.irc.soju.SojuEphemeralNetworkImporter;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Method;
import java.util.OptionalLong;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.pircbotx.PircBotX;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class PircbotxIrcClientService implements IrcClientService {

  private static final Logger log = LoggerFactory.getLogger(PircbotxIrcClientService.class);
  private static final DateTimeFormatter MARKREAD_TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
          .withZone(ZoneOffset.UTC);

  private final FlowableProcessor<ServerIrcEvent> bus =
      PublishProcessor.<ServerIrcEvent>create().toSerialized();

  private final Map<String, PircbotxConnectionState> connections = new ConcurrentHashMap<>();
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
  private final ServerCatalog serverCatalog;
  private final PircbotxInputParserHookInstaller inputParserHookInstaller;
  private final PircbotxBotFactory botFactory;
  private final PircbotxConnectionTimersRx timers;
  private final SojuEphemeralNetworkImporter sojuImporter;
  private final ZncEphemeralNetworkImporter zncImporter;
  private final SojuProperties sojuProps;
  private final ZncProperties zncProps;
  private final RuntimeConfigStore runtimeConfig;
  private final Ircv3StsPolicyService stsPolicies;
  private final PlaybackCursorProvider playbackCursorProvider;
  private String version;
  public PircbotxIrcClientService(IrcProperties props,
                                 ServerCatalog serverCatalog,
                                 PircbotxInputParserHookInstaller inputParserHookInstaller,
                                 PircbotxBotFactory botFactory,
                                 SojuProperties sojuProps,
                                 ZncProperties zncProps,
                                 RuntimeConfigStore runtimeConfig,
                                 Ircv3StsPolicyService stsPolicies,
                                 SojuEphemeralNetworkImporter sojuImporter,
                                 ZncEphemeralNetworkImporter zncImporter,
                                 PircbotxConnectionTimersRx timers,
                                 ObjectProvider<PlaybackCursorProvider> playbackCursorProviderProvider) {
    this.serverCatalog = serverCatalog;
    version = props.client().version();
    this.inputParserHookInstaller = inputParserHookInstaller;
    this.botFactory = botFactory;
    this.sojuProps = sojuProps;
    this.zncProps = zncProps;
    this.runtimeConfig = runtimeConfig;
    this.stsPolicies = Objects.requireNonNull(stsPolicies, "stsPolicies");
    this.sojuImporter = Objects.requireNonNull(sojuImporter, "sojuImporter");
    this.zncImporter = Objects.requireNonNull(zncImporter, "zncImporter");
    this.timers = timers;
    this.playbackCursorProvider = playbackCursorProviderProvider.getIfAvailable(
        () -> (String sid) -> OptionalLong.empty()
    );
  }

  /**
   * Reschedules heartbeat tickers for all currently-active connections.
   *
   * <p>This is used when the user changes heartbeat settings in Preferences and clicks Apply.
   * We rebuild the Rx interval so the new check period/timeout takes effect immediately.
   */
  public void rescheduleActiveHeartbeats() {
    if (shuttingDown.get()) return;
    for (PircbotxConnectionState c : connections.values()) {
      try {
        if (c != null && c.botRef.get() != null) {
          timers.rescheduleHeartbeat(c);
        }
      } catch (Exception ignored) {
      }
    }
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
          if (shuttingDown.get()) return;
          PircbotxConnectionState c = conn(serverId);
          if (c.botRef.get() != null) return;
          c.resetNegotiatedCaps();
          // soju discovery state is per-session; reset before starting a new connection.
          try {
            c.sojuNetworksByNetId.clear();
            c.sojuListNetworksRequestedThisSession.set(false);
            c.sojuBouncerNetId.set("");
          } catch (Exception ignored) {}
          cancelReconnect(c);
          c.manualDisconnect.set(false);
          c.reconnectAttempts.set(0);

          IrcProperties.Server configured = serverCatalog.require(serverId);
          IrcProperties.Server s = stsPolicies.applyPolicy(configured);
          c.connectedHost.set(Objects.toString(s.host(), "").trim());
          c.connectedWithTls.set(s.tls());
          c.selfNickHint.set(Objects.toString(s.nick(), "").trim());

          // ZNC detection uses CAP/004/*status heuristics, but we can still parse the
          // configured login now (user[@client]/network) so logs and discovery logic have
          // context once ZNC is detected.
          try {
            c.zncDetected.set(false);
            c.zncDetectedLogged.set(false);
            c.zncBaseUser.set("");
            c.zncClientId.set("");
            c.zncNetwork.set("");

            // Reset per-connection ZNC playback flags (negotiated each connect).
            c.zncPlaybackRequestedThisSession.set(false);
            c.zncListNetworksRequestedThisSession.set(false);
            c.zncPlaybackCapture.cancelActive("reconnect");

            ZncLoginParts loginParts = ZncLoginParts.parse(s.login());
            ZncLoginParts saslParts = (s.sasl() != null && s.sasl().enabled())
                ? ZncLoginParts.parse(s.sasl().username())
                : new ZncLoginParts("", "", "");

            ZncLoginParts merged = loginParts.mergePreferThis(saslParts);
            c.zncBaseUser.set(merged.baseUser());
            c.zncClientId.set(merged.clientId());
            c.zncNetwork.set(merged.network());
          } catch (Exception ignored) {}

          boolean disconnectOnSaslFailure = s.sasl() != null
              && s.sasl().enabled()
              && Boolean.TRUE.equals(s.sasl().disconnectOnFailure());
          bus.onNext(new ServerIrcEvent(
              serverId, new IrcEvent.Connecting(Instant.now(), s.host(), s.port(), s.nick())));

          PircbotxBridgeListener listener = new PircbotxBridgeListener(
              serverId,
              c,
              bus,
              timers::stopHeartbeat,
              this::scheduleReconnect,
              this::handleCtcpIfPresent,
              disconnectOnSaslFailure,
              sojuProps.discovery().enabled(),
              zncProps.discovery().enabled(),
              zncImporter::onNetworkDiscovered,
              sojuImporter::onNetworkDiscovered,
              sojuImporter::onOriginDisconnected,
              zncImporter::onOriginDisconnected,
              playbackCursorProvider
          );

          PircBotX bot = botFactory.build(s, version, listener);
          c.botRef.set(bot);
          inputParserHookInstaller.installAwayNotifyHook(bot, serverId, c, bus::onNext);

          timers.startHeartbeat(c);
          RxVirtualSchedulers.io().scheduleDirect(() -> {
            boolean crashed = false;
            try {
              bot.startBot();
            } catch (Exception e) {
              crashed = true;
              bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Error(Instant.now(), "Bot crashed", e)));
            } finally {
              if (c.botRef.compareAndSet(bot, null)) {
                timers.stopHeartbeat(c);
              }
              if (crashed && !c.manualDisconnect.get()) {
                scheduleReconnect(c, "Bot crashed");
              }
            }
          });
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable disconnect(String serverId) {
    return disconnect(serverId, null);
  }

  @Override
  public Completable disconnect(String serverId, String reason) {
    return Completable.fromAction(() -> {
          PircbotxConnectionState c = conn(serverId);
          c.manualDisconnect.set(true);
          cancelReconnect(c);
          timers.stopHeartbeat(c);

          // If this server was acting as a bouncer origin, drop any discovered ephemeral networks.
          try {
            sojuImporter.onOriginDisconnected(serverId);
          } catch (Exception ignored) {}
          try {
            zncImporter.onOriginDisconnected(serverId);
          } catch (Exception ignored) {}


          PircBotX bot = c.botRef.getAndSet(null);
          if (bot == null) return;

          String quitReason = reason == null ? "" : reason.trim();
          if (quitReason.contains("\r") || quitReason.contains("\n")) {
            throw new IllegalArgumentException("quit reason contains CR/LF");
          }
          if (quitReason.isEmpty()) quitReason = "Client disconnect";

          try {
            bot.stopBotReconnect();
            try {
              bot.sendIRC().quitServer(quitReason);
            } catch (Exception ignored) {}
            try {
              bot.close();
            } catch (Exception ignored) {}
          } finally {
            bus.onNext(new ServerIrcEvent(serverId,
                new IrcEvent.Disconnected(Instant.now(), "Client requested disconnect")));
          }
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable changeNick(String serverId, String newNick) {
    return Completable.fromAction(() -> {
          String nick = PircbotxUtil.sanitizeNick(newNick);
          requireBot(serverId).sendIRC().changeNick(nick);
        })
        .subscribeOn(RxVirtualSchedulers.io());
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
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable joinChannel(String serverId, String channel) {
    return Completable.fromAction(() -> requireBot(serverId).sendIRC().joinChannel(channel))
        .subscribeOn(RxVirtualSchedulers.io());
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
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendToChannel(String serverId, String channel, String message) {
    return Completable.fromAction(() -> {
          String chan = PircbotxUtil.sanitizeChannel(channel);
          sendMessageWithMultiline(serverId, chan, message, false);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendNoticeToChannel(String serverId, String channel, String message) {
    return Completable.fromAction(() -> {
          String chan = PircbotxUtil.sanitizeChannel(channel);
          sendMessageWithMultiline(serverId, chan, message, true);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendPrivateMessage(String serverId, String nick, String message) {
    return Completable.fromAction(() -> {
          String target = PircbotxUtil.sanitizeNick(nick);
          sendMessageWithMultiline(serverId, target, message, false);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendNoticePrivate(String serverId, String nick, String message) {
    return Completable.fromAction(() -> {
          String target = PircbotxUtil.sanitizeNick(nick);
          sendMessageWithMultiline(serverId, target, message, true);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendRaw(String serverId, String rawLine) {
    return Completable.fromAction(() -> {
          String line = rawLine == null ? "" : rawLine.trim();
          if (line.isEmpty()) return;
          requireBot(serverId).sendRaw().rawLine(line);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private void sendMessageWithMultiline(String serverId, String target, String message, boolean notice) {
    String dest = sanitizeTarget(target);
    String payload = Objects.toString(message, "");
    if (payload.isEmpty()) return;

    List<String> lines = normalizeMessageLines(payload);
    if (lines.isEmpty()) return;

    PircbotxConnectionState c = conn(serverId);
    PircBotX bot = requireBot(serverId);
    String command = notice ? "NOTICE" : "PRIVMSG";

    if (lines.size() == 1) {
      sendRawMessageLine(bot, command, dest, lines.get(0));
      return;
    }

    String batchType = multilineBatchType(c);
    String concatTag = multilineConcatTag(c);
    if (batchType.isEmpty() || concatTag.isEmpty()) {
      throw new IllegalArgumentException(
          "Message contains line breaks, but IRCv3 multiline is not negotiated: " + serverId);
    }
    long maxLines = multilineMaxLines(c);
    requireWithinMultilineMaxLines(maxLines, lines, serverId);
    long maxBytes = multilineMaxBytes(c);
    requireWithinMultilineMaxBytes(maxBytes, lines, serverId);

    String batchId = "ml" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
    bot.sendRaw().rawLine("BATCH +" + batchId + " " + batchType + " " + dest);
    for (int i = 0; i < lines.size(); i++) {
      String line = Objects.toString(lines.get(i), "");
      boolean concat = i < lines.size() - 1;
      String tagPrefix = "@batch=" + batchId;
      if (concat) {
        tagPrefix = tagPrefix + ";+" + concatTag + "=1";
      }
      bot.sendRaw().rawLine(tagPrefix + " " + command + " " + dest + " :" + line);
    }
    bot.sendRaw().rawLine("BATCH -" + batchId);
  }

  private static void sendRawMessageLine(PircBotX bot, String command, String target, String line) {
    String cmd = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    if (!"PRIVMSG".equals(cmd) && !"NOTICE".equals(cmd)) {
      throw new IllegalArgumentException("Unsupported message command: " + command);
    }
    String dest = sanitizeTarget(target);
    String payload = Objects.toString(line, "");
    if (payload.indexOf('\r') >= 0 || payload.indexOf('\n') >= 0) {
      throw new IllegalArgumentException("message line contains CR/LF");
    }
    bot.sendRaw().rawLine(cmd + " " + dest + " :" + payload);
  }

  private static List<String> normalizeMessageLines(String raw) {
    String input = Objects.toString(raw, "");
    if (input.isEmpty()) return List.of();
    String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
    if (normalized.indexOf('\n') < 0) {
      return List.of(normalized);
    }
    String[] parts = normalized.split("\n", -1);
    List<String> out = new ArrayList<>(parts.length);
    for (String part : parts) {
      out.add(Objects.toString(part, ""));
    }
    return out;
  }

  private static String multilineBatchType(PircbotxConnectionState c) {
    if (c == null) return "";
    if (c.multilineCapAcked.get()) return "multiline";
    if (c.draftMultilineCapAcked.get()) return "draft/multiline";
    return "";
  }

  private static String multilineConcatTag(PircbotxConnectionState c) {
    if (c == null) return "";
    if (c.multilineCapAcked.get()) return "multiline-concat";
    if (c.draftMultilineCapAcked.get()) return "draft/multiline-concat";
    return "";
  }

  private static long multilineMaxBytes(PircbotxConnectionState c) {
    if (c == null) return 0L;
    if (c.multilineCapAcked.get()) {
      return Math.max(0L, c.multilineMaxBytes.get());
    }
    if (c.draftMultilineCapAcked.get()) {
      return Math.max(0L, c.draftMultilineMaxBytes.get());
    }
    return 0L;
  }

  private static long multilineMaxLines(PircbotxConnectionState c) {
    if (c == null) return 0L;
    if (c.multilineCapAcked.get()) {
      return Math.max(0L, c.multilineMaxLines.get());
    }
    if (c.draftMultilineCapAcked.get()) {
      return Math.max(0L, c.draftMultilineMaxLines.get());
    }
    return 0L;
  }

  static long multilinePayloadUtf8Bytes(List<String> lines) {
    if (lines == null || lines.isEmpty()) return 0L;
    long total = 0L;
    for (int i = 0; i < lines.size(); i++) {
      String line = Objects.toString(lines.get(i), "");
      total = addSaturated(total, utf8Length(line));
      if (i < lines.size() - 1) {
        total = addSaturated(total, 1L); // \n separator between logical lines
      }
    }
    return total;
  }

  static void requireWithinMultilineMaxBytes(long maxBytes, List<String> lines, String serverId) {
    if (maxBytes <= 0L) return;
    long payloadBytes = multilinePayloadUtf8Bytes(lines);
    if (payloadBytes <= maxBytes) return;
    throw new IllegalArgumentException(
        "Message exceeds negotiated IRCv3 multiline max-bytes "
            + payloadBytes
            + " > "
            + maxBytes
            + " for "
            + Objects.toString(serverId, "").trim());
  }

  static void requireWithinMultilineMaxLines(long maxLines, List<String> lines, String serverId) {
    if (maxLines <= 0L) return;
    long lineCount = (lines == null) ? 0L : lines.size();
    if (lineCount <= maxLines) return;
    throw new IllegalArgumentException(
        "Message exceeds negotiated IRCv3 multiline max-lines "
            + lineCount
            + " > "
            + maxLines
            + " for "
            + Objects.toString(serverId, "").trim());
  }

  private static long utf8Length(String value) {
    return Objects.toString(value, "").getBytes(StandardCharsets.UTF_8).length;
  }

  private static long addSaturated(long left, long right) {
    if (right <= 0L) return left;
    if (left >= Long.MAX_VALUE - right) return Long.MAX_VALUE;
    return left + right;
  }

  @Override
  public Completable sendTyping(String serverId, String target, String state) {
    return Completable.fromAction(() -> {
          PircbotxConnectionState c = conn(serverId);
          if (c == null || c.botRef.get() == null) {
            throw new IllegalStateException("Not connected: " + serverId);
          }
          if (!isTypingAvailable(serverId)) {
            String reason = typingAvailabilityReason(serverId);
            String suffix = (reason == null || reason.isBlank()) ? "" : (" (" + reason + ")");
            throw new IllegalStateException(
                "Typing indicators not available (requires message-tags and server allowing +typing)" + suffix + ": " + serverId);
          }

          String normalizedState = normalizeTypingState(state);
          if (normalizedState.isEmpty()) return;

          String dest = sanitizeTarget(target);
          String line = "@+typing=" + normalizedState + " TAGMSG " + dest;
          if (log.isDebugEnabled()) {
            log.debug("[{}] -> typing {} TAGMSG {}", serverId, normalizedState, dest);
          }
          requireBot(serverId).sendRaw().rawLine(line);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable sendReadMarker(String serverId, String target, Instant markerAt) {
    return Completable.fromAction(() -> {
          PircbotxConnectionState c = conn(serverId);
          if (c == null || !c.readMarkerCapAcked.get()) {
            throw new IllegalStateException("read-marker capability not negotiated: " + serverId);
          }
          if (c.botRef.get() == null) {
            throw new IllegalStateException("Not connected: " + serverId);
          }

          String dest = sanitizeTarget(target);
          Instant at = (markerAt == null) ? Instant.now() : markerAt;
          String ts = MARKREAD_TS_FMT.format(at);
          requireBot(serverId).sendRaw().rawLine("MARKREAD " + dest + " :" + ts);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBefore(String serverId, String target, java.time.Instant beforeExclusive, int limit) {
    java.time.Instant before = beforeExclusive == null ? java.time.Instant.now() : beforeExclusive;
    String selector = Ircv3ChatHistoryCommandBuilder.timestampSelector(before);
    return requestChatHistoryBefore(serverId, target, selector, limit);
  }

  @Override
  public Completable requestChatHistoryBefore(String serverId, String target, String selector, int limit) {
    return io.reactivex.rxjava3.core.Completable.fromAction(() -> {
          ensureChatHistoryNegotiated(serverId);

          String line = Ircv3ChatHistoryCommandBuilder.buildBefore(target, selector, limit);
          requireBot(serverId).sendRaw().rawLine(line);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryLatest(String serverId, String target, String selector, int limit) {
    return io.reactivex.rxjava3.core.Completable.fromAction(() -> {
          ensureChatHistoryNegotiated(serverId);

          String line = Ircv3ChatHistoryCommandBuilder.buildLatest(target, selector, limit);
          requireBot(serverId).sendRaw().rawLine(line);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryBetween(
      String serverId,
      String target,
      String startSelector,
      String endSelector,
      int limit
  ) {
    return io.reactivex.rxjava3.core.Completable.fromAction(() -> {
          ensureChatHistoryNegotiated(serverId);

          String line = Ircv3ChatHistoryCommandBuilder.buildBetween(target, startSelector, endSelector, limit);
          requireBot(serverId).sendRaw().rawLine(line);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestChatHistoryAround(String serverId, String target, String selector, int limit) {
    return io.reactivex.rxjava3.core.Completable.fromAction(() -> {
          ensureChatHistoryNegotiated(serverId);

          String line = Ircv3ChatHistoryCommandBuilder.buildAround(target, selector, limit);
          requireBot(serverId).sendRaw().rawLine(line);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  private void ensureChatHistoryNegotiated(String serverId) {
    PircbotxConnectionState c = conn(serverId);
    if (!c.chatHistoryCapAcked.get()) {
      throw new IllegalStateException("CHATHISTORY not negotiated (chathistory or draft/chathistory): " + serverId);
    }
    if (!c.batchCapAcked.get()) {
      throw new IllegalStateException("CHATHISTORY requires IRCv3 batch to be negotiated: " + serverId);
    }
  }


  @Override
  public boolean isChatHistoryAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.chatHistoryCapAcked.get() && c.batchCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isEchoMessageAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.echoMessageCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isDraftReplyAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.draftReplyCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isDraftReactAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.draftReactCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isMultilineAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null
          && c.botRef.get() != null
          && (c.multilineCapAcked.get() || c.draftMultilineCapAcked.get());
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public long negotiatedMultilineMaxBytes(String serverId) {
    try {
      return multilineMaxBytes(conn(serverId));
    } catch (Exception e) {
      return 0L;
    }
  }

  @Override
  public int negotiatedMultilineMaxLines(String serverId) {
    try {
      long max = multilineMaxLines(conn(serverId));
      if (max <= 0L) return 0;
      if (max >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
      return (int) max;
    } catch (Exception e) {
      return 0;
    }
  }

  @Override
  public boolean isMessageEditAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.draftMessageEditCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isMessageRedactionAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.draftMessageRedactionCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isTypingAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      if (c == null || c.botRef.get() == null) return false;

      // Typing is a client-only tag (+typing) delivered via message-tags + TAGMSG.
      // Networks may block specific client-only tags via RPL_ISUPPORT CLIENTTAGDENY.
      boolean messageTags = c.messageTagsCapAcked.get();
      boolean typingAllowed = c.typingClientTagAllowed.get() || c.typingCapAcked.get(); // legacy fallback
      return messageTags && typingAllowed;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public String typingAvailabilityReason(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      if (c == null) return "no connection state";
      if (c.botRef.get() == null) return "not connected";

      boolean messageTags = c.messageTagsCapAcked.get();
      if (!messageTags) {
        return "message-tags not negotiated";
      }

      boolean typingAllowed = c.typingClientTagAllowed.get();
      boolean typingCap = c.typingCapAcked.get();
      if (!(typingAllowed || typingCap)) {
        return "server denies +typing via CLIENTTAGDENY";
      }

      // Available.
      return "";
    } catch (Exception e) {
      return "error determining typing availability: " + e.getClass().getSimpleName();
    }
  }

  @Override
  public boolean isReadMarkerAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.readMarkerCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isLabeledResponseAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.labeledResponseCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isStandardRepliesAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.botRef.get() != null && c.standardRepliesCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isMonitorAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null
          && c.botRef.get() != null
          && (c.monitorSupported.get() || c.monitorCapAcked.get());
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public int negotiatedMonitorLimit(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      if (c == null) return 0;
      long limit = Math.max(0L, c.monitorMaxTargets.get());
      if (limit <= 0L) return 0;
      if (limit >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
      return (int) limit;
    } catch (Exception e) {
      return 0;
    }
  }

  @Override
  public boolean isZncPlaybackAvailable(String serverId) {
    try {
      PircbotxConnectionState c = conn(serverId);
      return c != null && c.zncPlaybackCapAcked.get();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public Completable requestZncPlaybackRange(String serverId, String target, Instant fromInclusive, Instant toInclusive) {
    return Completable.fromAction(() -> {
          PircbotxConnectionState c = conn(serverId);
          if (!c.zncPlaybackCapAcked.get()) {
            throw new IllegalStateException("ZNC playback not negotiated (znc.in/playback): " + serverId);
          }

          String t = target == null ? "" : target.trim();
          if (t.isEmpty()) throw new IllegalArgumentException("target is blank");

          // R5.2b: Track an in-flight playback request so we can group replayed lines into a batch.
          Instant fromCap = (fromInclusive == null ? Instant.EPOCH : fromInclusive);
          Instant toCap = (toInclusive == null ? Instant.now() : toInclusive);
          c.zncPlaybackCapture.start(serverId, t, fromCap, toCap, bus::onNext);

          try {

          String buf;
          if (t.startsWith("#") || t.startsWith("&")) {
            buf = PircbotxUtil.sanitizeChannel(t);
          } else {
            buf = PircbotxUtil.sanitizeNick(t);
          }

          long from = (fromInclusive == null ? Instant.EPOCH : fromInclusive).getEpochSecond();
          long to = (toInclusive == null ? 0L : toInclusive.getEpochSecond());

          // ZNC playback module takes epoch-seconds. If 'to' is omitted/0, it replays until now.
          String cmd;
          if (to > 0L) {
            cmd = "play " + buf + " " + from + " " + to;
          } else {
            cmd = "play " + buf + " " + from;
          }
          requireBot(serverId).sendIRC().message("*playback", cmd);
          } catch (Exception ex) {
            c.zncPlaybackCapture.cancelActive("send-failed");
            throw ex;
          }
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }


  @Override
  public Completable sendAction(String serverId, String target, String action) {
    return Completable.fromAction(() -> {
          String t = target == null ? "" : target.trim();
          String a = action == null ? "" : action;
          if (t.isEmpty()) throw new IllegalArgumentException("target is blank");
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
          } catch (Exception e) {
            log.debug("sendAction: native action() invoke failed, falling back to CTCP wrapper", e);
          }

          if (!sent) {
            requireBot(serverId).sendIRC().message(dest, "\u0001ACTION " + a + "\u0001");
          }
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable requestNames(String serverId, String channel) {
    return Completable.fromAction(() -> {
          String chan = PircbotxUtil.sanitizeChannel(channel);
          requireBot(serverId).sendRaw().rawLine("NAMES " + chan);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable whois(String serverId, String nick) {
    return Completable.fromAction(() -> {
          String n = PircbotxUtil.sanitizeNick(nick);
          conn(serverId).whoisSawAwayByNickLower.putIfAbsent(n.toLowerCase(Locale.ROOT), Boolean.FALSE);
          conn(serverId).whoisSawAccountByNickLower.putIfAbsent(n.toLowerCase(Locale.ROOT), Boolean.FALSE);

          requireBot(serverId).sendRaw().rawLine("WHOIS " + n);
        })
        .subscribeOn(RxVirtualSchedulers.io());
  }

  @Override
  public Completable whowas(String serverId, String nick, int count) {
    return Completable.fromAction(() -> {
          String n = PircbotxUtil.sanitizeNick(nick);
          if (count > 0) {
            requireBot(serverId).sendRaw().rawLine("WHOWAS " + n + " " + count);
          } else {
            requireBot(serverId).sendRaw().rawLine("WHOWAS " + n);
          }
        })
        .subscribeOn(RxVirtualSchedulers.io());
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

  private boolean handleCtcpIfPresent(PircBotX bot, String fromNick, String message) {
    if (message == null || message.length() < 2) return false;
    if (message.charAt(0) != 0x01 || message.charAt(message.length() - 1) != 0x01) return false;

    String n1 = null;
    String n2 = null;
    String n3 = null;

    // Some servers may echo our own outbound CTCP requests back to us (IRCv3 echo-message).
    // Never treat those as inbound CTCP requests, or we'll reply to ourselves.
    try {
      if (fromNick != null && !fromNick.isBlank()) {
        String from = fromNick.trim();

        try {
          n1 = PircbotxUtil.safeStr(bot::getNick, null);
        } catch (Exception ignored) {
        }
        try {
          if (bot.getUserBot() != null) n2 = bot.getUserBot().getNick();
        } catch (Exception ignored) {
        }
        try {
          Object cfg = bot.getConfiguration();
          if (cfg != null) {
            try {
              java.lang.reflect.Method m = cfg.getClass().getMethod("getNick");
              Object n = m.invoke(cfg);
              if (n != null) n3 = String.valueOf(n);
            } catch (Exception ignored) {
            }
          }
        } catch (Exception ignored) {
        }

        boolean selfEcho = (n1 != null && !n1.isBlank() && from.equalsIgnoreCase(n1.trim()))
            || (n2 != null && !n2.isBlank() && from.equalsIgnoreCase(n2.trim()))
            || (n3 != null && !n3.isBlank() && from.equalsIgnoreCase(n3.trim()));
        if (selfEcho) {
          log.debug(
              "[ircafe] CTCPDBG service-drop-self from={} n1={} n2={} n3={} message={}",
              from,
              Objects.toString(n1, ""),
              Objects.toString(n2, ""),
              Objects.toString(n3, ""),
              message.replace('\u0001', '|'));
          return true;
        }
      }
    } catch (Exception ignored) {
    }

    String inner = message.substring(1, message.length() - 1).trim();
    if (inner.isEmpty()) return false;

    String cmd = inner;
    int sp = inner.indexOf(' ');
    if (sp >= 0) cmd = inner.substring(0, sp);

    cmd = cmd.trim().toUpperCase(Locale.ROOT);
    log.debug(
        "[ircafe] CTCPDBG service-eval from={} cmd={} inner={} n1={} n2={} n3={}",
        Objects.toString(fromNick, ""),
        cmd,
        inner,
        Objects.toString(n1, ""),
        Objects.toString(n2, ""),
        Objects.toString(n3, ""));
    if (!isCtcpAutoReplyEnabled(cmd)) {
      log.debug(
          "[ircafe] CTCPDBG service-drop-disabled from={} cmd={}",
          Objects.toString(fromNick, ""),
          cmd);
      // Treat known CTCP requests as handled even when auto-replies are disabled.
      return "VERSION".equals(cmd) || "PING".equals(cmd) || "TIME".equals(cmd);
    }
    if ("VERSION".equals(cmd)) {
      String v = (version == null) ? "IRCafe" : version;
      log.debug("[ircafe] CTCPDBG service-send cmd=VERSION to={} payload={}",
          Objects.toString(fromNick, ""),
          ("VERSION " + v));
      bot.sendIRC().notice(PircbotxUtil.sanitizeNick(fromNick), "VERSION " + v + "");
      return true;
    }

    if ("PING".equals(cmd)) {
      String payload = "";
      int sp2 = inner.indexOf(' ');
      if (sp2 >= 0 && sp2 + 1 < inner.length()) payload = inner.substring(sp2 + 1).trim();
      String body = payload.isEmpty() ? "PING" : "PING " + payload + "";
      log.debug("[ircafe] CTCPDBG service-send cmd=PING to={} payload={}",
          Objects.toString(fromNick, ""),
          body.replace('\u0001', '|'));
      bot.sendIRC().notice(PircbotxUtil.sanitizeNick(fromNick), body);
      return true;
    }

    if ("TIME".equals(cmd)) {
      String now = java.time.ZonedDateTime.now().toString();
      log.debug("[ircafe] CTCPDBG service-send cmd=TIME to={} payload=TIME {}", Objects.toString(fromNick, ""), now);
      bot.sendIRC().notice(PircbotxUtil.sanitizeNick(fromNick), "TIME " + now + "");
      return true;
    }

    return false;
  }

  private boolean isCtcpAutoReplyEnabled(String command) {
    if (runtimeConfig == null) return true;
    String cmd = (command == null) ? "" : command.trim().toUpperCase(Locale.ROOT);
    if (!"VERSION".equals(cmd) && !"PING".equals(cmd) && !"TIME".equals(cmd)) {
      return true;
    }
    if (!runtimeConfig.readCtcpAutoRepliesEnabled(true)) return false;
    return switch (cmd) {
      case "VERSION" -> runtimeConfig.readCtcpAutoReplyVersionEnabled(true);
      case "PING" -> runtimeConfig.readCtcpAutoReplyPingEnabled(true);
      case "TIME" -> runtimeConfig.readCtcpAutoReplyTimeEnabled(true);
      default -> true;
    };
  }

  private void cancelReconnect(PircbotxConnectionState c) {
    timers.cancelReconnect(c);
  }

  private void scheduleReconnect(PircbotxConnectionState c, String reason) {
    if (c == null) return;
    if (shuttingDown.get()) return;
    if (c.manualDisconnect.get()) return;
    timers.scheduleReconnect(c, reason, this::connect, bus::onNext);
  }

  @Override
  public void shutdownNow() {
    shuttingDown.set(true);
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

  private static String sanitizeTarget(String target) {
    String t = target == null ? "" : target.trim();
    if (t.isEmpty()) throw new IllegalArgumentException("target is blank");
    if (t.startsWith("#") || t.startsWith("&")) {
      return PircbotxUtil.sanitizeChannel(t);
    }
    return PircbotxUtil.sanitizeNick(t);
  }

  private static String normalizeTypingState(String state) {
    String s = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return "";
    return switch (s) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "";
    };
  }

  @PreDestroy
  void shutdown() {
    shutdownNow();
  }

}
