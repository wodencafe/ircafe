package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.irc.soju.PircbotxSojuParsers;
import cafe.woden.ircclient.irc.soju.SojuNetwork;
import cafe.woden.ircclient.irc.znc.ZncNetwork;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.types.GenericCTCPEvent;
import org.pircbotx.snapshot.ChannelSnapshot;
import org.pircbotx.snapshot.UserChannelDaoSnapshot;
import org.pircbotx.snapshot.UserSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Translates PircBotX events into ServerIrcEvent. */
final class PircbotxBridgeListener extends ListenerAdapter {
  private static final Logger log = LoggerFactory.getLogger(PircbotxBridgeListener.class);

  // Helpful for correlating MODE bursts in logs.
  private static final AtomicLong MODE_SEQ = new AtomicLong();

  @FunctionalInterface
  interface CtcpRequestHandler {
    boolean handle(PircBotX bot, String fromNick, String message);
  }

  private final String serverId;
  private final PircbotxConnectionState conn;
  private final FlowableProcessor<ServerIrcEvent> bus;
  private final PlaybackCursorProvider playbackCursorProvider;
  private final Consumer<PircbotxConnectionState> heartbeatStopper;
  private final BiConsumer<PircbotxConnectionState, String> reconnectScheduler;
  private final CtcpRequestHandler ctcpHandler;
  private final boolean disconnectOnSaslFailure;
  private final boolean sojuDiscoveryEnabled;
  private final boolean zncDiscoveryEnabled;

  private final Consumer<SojuNetwork> sojuNetworkDiscovered;

  private final Consumer<ZncNetwork> zncNetworkDiscovered;

  private final Consumer<String> sojuOriginDisconnected;

  private final Consumer<String> zncOriginDisconnected;

  private final Map<String, ChatHistoryBatchBuffer> activeChatHistoryBatches = new HashMap<>();
  private final Ircv3MultilineAccumulator multilineAccumulator = new Ircv3MultilineAccumulator();

  private static final class ChatHistoryBatchBuffer {

    final String target;
    final ArrayList<ChatHistoryEntry> entries = new ArrayList<>();

    ChatHistoryBatchBuffer(String target) {

      this.target = target == null ? "" : target;
    }
  }

  private static final int ERR_SASL_FAIL = 904;
  private static final int ERR_SASL_TOO_LONG = 905;
  private static final int ERR_SASL_ABORTED = 906;
  private static final int ERR_SASL_ALREADY = 907;
  private static final int RPL_MONONLINE = 730;
  private static final int RPL_MONOFFLINE = 731;
  private static final int RPL_MONLIST = 732;
  private static final int RPL_ENDOFMONLIST = 733;
  private static final int ERR_MONLISTFULL = 734;
  private static final String IRCafe_WHOX_TOKEN = "1";
  private static final String TAG_IRCAFE_PM_TARGET = "ircafe/pm-target";
  private static final String TAG_IRCAFE_HOSTMASK = "ircafe/hostmask";

  private static boolean isJoinFailureNumeric(int code) {
    return code == 403
        || code == 405
        || code == 471
        || code == 473
        || code == 474
        || code == 475
        || code == 476
        || code == 477;
  }

  private record ParsedJoinFailure(String channel, String message) {}

  private record ParsedInviteLine(
      String fromNick, String inviteeNick, String channel, String reason) {}

  private record ParsedWallopsLine(String from, String message) {}

  private record ParsedIrcLine(
      String prefix, String command, List<String> params, String trailing) {}

  private static ParsedIrcLine parseIrcLine(String normalizedLine) {
    if (normalizedLine == null) return null;
    String s = normalizedLine.trim();
    if (s.isEmpty()) return null;

    String prefix = null;
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 1) {
        prefix = s.substring(1, sp);
        s = s.substring(sp + 1).trim();
      }
    }

    String trailing = null;
    int idx = s.indexOf(" :");
    if (idx >= 0) {
      trailing = s.substring(idx + 2);
      s = s.substring(0, idx).trim();
    }

    if (s.isEmpty()) return null;

    String[] parts = s.split("\\s+");
    if (parts.length == 0) return null;
    String cmd = parts[0];
    java.util.ArrayList<String> params = new java.util.ArrayList<>();
    for (int i = 1; i < parts.length; i++) {
      if (!parts[i].isBlank()) params.add(parts[i]);
    }

    return new ParsedIrcLine(prefix, cmd, params, trailing);
  }

  private static ParsedInviteLine parseInviteLine(ParsedIrcLine parsed) {
    if (parsed == null) return null;
    String cmd = Objects.toString(parsed.command(), "").trim();
    if (!"INVITE".equalsIgnoreCase(cmd)) return null;

    String from = nickFromPrefix(parsed.prefix());
    String invitee = "";
    String channel = "";
    String reason = "";

    List<String> params = parsed.params();
    if (params != null && !params.isEmpty()) {
      invitee = Objects.toString(params.get(0), "").trim();
      if (params.size() >= 2) {
        channel = Objects.toString(params.get(1), "").trim();
      }
      if (params.size() >= 3) {
        reason = Objects.toString(params.get(2), "").trim();
      }
    }

    String trailing = Objects.toString(parsed.trailing(), "").trim();
    if (!trailing.isEmpty()) {
      if (channel.isEmpty()) {
        int sp = trailing.indexOf(' ');
        if (sp > 0) {
          channel = trailing.substring(0, sp).trim();
          reason = trailing.substring(sp + 1).trim();
        } else {
          channel = trailing;
        }
      } else if (!trailing.equalsIgnoreCase(channel)) {
        if (trailing.startsWith(channel + " ")) {
          reason = trailing.substring(channel.length()).trim();
        } else {
          reason = trailing;
        }
      }
    }

    if (channel.startsWith(":")) channel = channel.substring(1).trim();
    if (reason.startsWith(":")) reason = reason.substring(1).trim();
    if (channel.isBlank()) return null;

    return new ParsedInviteLine(from, invitee, channel, reason);
  }

  private static ParsedWallopsLine parseWallopsLine(ParsedIrcLine parsed) {
    if (parsed == null) return null;
    String cmd = Objects.toString(parsed.command(), "").trim();
    if (!"WALLOPS".equalsIgnoreCase(cmd)) return null;

    String from = Objects.toString(nickFromPrefix(parsed.prefix()), "").trim();
    if (from.isEmpty()) {
      from = Objects.toString(parsed.prefix(), "").trim();
    }
    if (from.isEmpty()) from = "server";

    String message = Objects.toString(parsed.trailing(), "").trim();
    if (message.isEmpty()) {
      List<String> params = parsed.params();
      if (params != null && !params.isEmpty()) {
        message = Objects.toString(params.get(params.size() - 1), "").trim();
      }
    }
    if (message.startsWith(":")) {
      message = message.substring(1).trim();
    }
    if (message.isEmpty()) return null;

    return new ParsedWallopsLine(from, message);
  }

  private static String nickFromPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) return "";
    String p = prefix;
    int bang = p.indexOf('!');
    if (bang > 0) p = p.substring(0, bang);
    int at = p.indexOf('@');
    if (at > 0) p = p.substring(0, at);
    return p;
  }

  /** Best-effort resolve of our current nick (prefers UserBot nick when available). */
  private static String resolveBotNick(PircBotX bot) {
    if (bot == null) return "";

    // Prefer UserBot nick, since it tends to reflect the *current* nick (including alt-nick
    // fallback).
    try {
      if (bot.getUserBot() != null) {
        String ub = bot.getUserBot().getNick();
        if (ub != null && !ub.isBlank()) return ub.trim();
      }
    } catch (Exception ignored) {
    }

    try {
      String n = PircbotxUtil.safeStr(bot::getNick, "");
      if (n != null && !n.isBlank()) return n.trim();
    } catch (Exception ignored) {
    }

    // Last resort: poke configuration via reflection (PircBotX versions differ).
    try {
      Object cfg = reflectCall(bot, "getConfiguration");
      Object n = reflectCall(cfg, "getNick");
      if (n != null) {
        String s = String.valueOf(n);
        if (!s.isBlank()) return s.trim();
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  /** True when an inbound event is actually our own message echoed back (IRCv3 echo-message). */
  private static boolean isSelfEchoed(PircBotX bot, String fromNick) {
    try {
      if (fromNick == null || fromNick.isBlank()) return false;

      String botNick = resolveBotNick(bot);
      if (!botNick.isBlank() && fromNick.equalsIgnoreCase(botNick)) return true;

      // Sometimes UserBot nick is available even when botNick isn't.
      try {
        if (bot != null && bot.getUserBot() != null) {
          String ub = bot.getUserBot().getNick();
          if (ub != null && !ub.isBlank() && fromNick.equalsIgnoreCase(ub)) return true;
        }
      } catch (Exception ignored) {
      }

      return false;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean looksLikeSelfNickHint(String nick) {
    if (nick == null) return false;
    String n = nick.trim();
    if (n.isBlank()) return false;
    if ("*".equals(n)) return false;
    if (PircbotxLineParseUtil.looksNumeric(n)) return false;
    if (PircbotxLineParseUtil.looksLikeChannel(n)) return false;
    if (n.indexOf(' ') >= 0) return false;
    return true;
  }

  private void rememberSelfNickHint(String nick) {
    if (!looksLikeSelfNickHint(nick)) return;
    String n = nick.trim();
    conn.selfNickHint.set(n);
  }

  private boolean nickMatchesSelf(PircBotX bot, String nick) {
    if (nick == null || nick.isBlank()) return false;
    String n = nick.trim();

    String hinted = conn.selfNickHint.get();
    if (hinted != null && !hinted.isBlank() && n.equalsIgnoreCase(hinted.trim())) {
      return true;
    }

    String fromBot = resolveBotNick(bot);
    if (!fromBot.isBlank() && n.equalsIgnoreCase(fromBot.trim())) {
      rememberSelfNickHint(fromBot);
      return true;
    }

    return false;
  }

  private String resolveSelfNick(PircBotX bot) {
    String hinted = conn.selfNickHint.get();
    if (looksLikeSelfNickHint(hinted)) {
      return hinted.trim();
    }
    String fromBot = resolveBotNick(bot);
    if (!fromBot.isBlank()) {
      rememberSelfNickHint(fromBot);
      return fromBot;
    }
    return "";
  }

  private static boolean isCtcpWrapped(String msg) {
    if (msg == null || msg.length() < 2) return false;
    return msg.charAt(0) == 0x01 && msg.charAt(msg.length() - 1) == 0x01;
  }

  private static String rawLineFromEvent(Object event) {
    if (event == null) return null;

    try {
      Object raw = reflectCall(event, "getRawLine");
      String s = toRawIrcLine(raw);
      if (s != null) return s;
    } catch (Exception ignored) {
    }

    try {
      Object line = reflectCall(event, "getLine");
      String s = toRawIrcLine(line);
      if (s != null) return s;
    } catch (Exception ignored) {
    }

    try {
      Object raw = reflectCall(event, "getRaw");
      String s = toRawIrcLine(raw);
      if (s != null) return s;
    } catch (Exception ignored) {
    }

    return null;
  }

  private static String toRawIrcLine(Object maybeLine) {
    if (maybeLine == null) return null;

    if (maybeLine instanceof String s) {
      String t = s.trim();
      return t.isEmpty() ? null : t;
    }

    // PircBotX uses several internal line types; extract the raw line via reflection if possible.
    for (String m : new String[] {"getRawLine", "getLine", "getRaw", "rawLine", "line"}) {
      try {
        Object nested = reflectCall(maybeLine, m);
        if (nested == null || nested == maybeLine) continue;
        String s = toRawIrcLine(nested);
        if (s != null) return s;
      } catch (Exception ignored) {
      }
    }

    // Last resort: only accept toString() if it looks like an IRC line.
    try {
      String t = String.valueOf(maybeLine).trim();
      if (t.startsWith("@") || t.startsWith(":")) return t;
    } catch (Exception ignored) {
    }

    return null;
  }

  private static String privmsgTargetFromEvent(Object event) {
    // Best effort: parse the raw IRC line first.
    try {
      String raw = rawLineFromEvent(event);
      String normalized = PircbotxLineParseUtil.normalizeIrcLineForParsing(raw);
      ParsedIrcLine p = parseIrcLine(normalized);
      if (p != null
          && p.command() != null
          && ("PRIVMSG".equalsIgnoreCase(p.command()) || "NOTICE".equalsIgnoreCase(p.command()))
          && !p.params().isEmpty()) {
        return p.params().get(0);
      }
    } catch (Exception ignored) {
    }

    // Fallback: try reflective access to a target/recipient property.
    try {
      Object t = reflectCall(event, "getTarget");
      if (t == null) t = reflectCall(event, "getRecipient");
      if (t == null) t = reflectCall(event, "getChannelSource");
      if (t == null) t = reflectCall(event, "getMessageTarget");
      if (t == null) t = reflectCall(event, "getMessageTargetName");
      if (t == null) t = reflectCall(event, "getDestination");

      if (t instanceof Channel) {
        return ((Channel) t).getName();
      }
      if (t instanceof User) {
        return ((User) t).getNick();
      }
      if (t != null) {
        String s = String.valueOf(t);
        if (!s.isBlank()) return s.trim();
      }
    } catch (Exception ignored) {
    }

    return null;
  }

  private static String derivePrivateConversationTarget(
      String botNick, String fromNick, String dest) {
    String from = fromNick == null ? "" : fromNick.trim();
    String d = dest == null ? "" : dest.trim();
    String me = botNick == null ? "" : botNick.trim();

    if (d.isBlank()) return from;
    if (me.isBlank()) return from;

    // If the destination is us, this is an inbound PM; the conversation key is the sender.
    if (d.equalsIgnoreCase(me)) return from;

    // Otherwise (including echo-message), the conversation key is the destination.
    return d;
  }

  private String inferPrivateDestinationFromHints(
      String from, String kind, String payload, String messageId) {
    String fromNick = Objects.toString(from, "").trim();
    String k = Objects.toString(kind, "").trim().toUpperCase(Locale.ROOT);
    String body = Objects.toString(payload, "").trim();
    if (fromNick.isBlank() || k.isBlank() || body.isBlank()) return "";
    return conn.findPrivateTargetHint(fromNick, k, body, messageId, System.currentTimeMillis());
  }

  private static boolean isZncPlayStarCursorCommand(String msg) {
    String m = Objects.toString(msg, "").trim();
    if (m.isEmpty()) return false;
    String[] parts = m.split("\\s+");
    if (parts.length < 3) return false;
    if (!"play".equalsIgnoreCase(parts[0])) return false;
    if (!"*".equals(parts[1])) return false;
    String n = parts[2];
    if (n.isEmpty()) return false;
    for (int i = 0; i < n.length(); i++) {
      if (!Character.isDigit(n.charAt(i))) return false;
    }
    return true;
  }

  private static boolean isChatHistoryBatchType(String type) {
    if (type == null) return false;
    String t = type.toLowerCase(java.util.Locale.ROOT);
    return t.contains("chathistory");
  }

  private boolean handleBatchControlLine(String normalizedLine) {
    ParsedIrcLine pl = parseIrcLine(normalizedLine);
    if (pl == null || pl.command() == null) return false;
    if (!"BATCH".equalsIgnoreCase(pl.command())) return false;

    java.util.List<String> params = pl.params();
    String trailing = pl.trailing();
    if (params == null || params.isEmpty()) {
      return true;
    }

    String first = params.get(0);
    if (first == null || first.isBlank()) return true;
    if (first.startsWith("+")) {
      String id = first.substring(1);
      String type = (params.size() >= 2) ? params.get(1) : "";

      if (isChatHistoryBatchType(type)) {
        String target = (params.size() >= 3) ? params.get(2) : "";
        if ((target == null || target.isBlank()) && trailing != null && !trailing.isBlank()) {
          target = trailing;
        }

        activeChatHistoryBatches.put(id, new ChatHistoryBatchBuffer(id, target));
        log.debug(
            "[{}] CHATHISTORY BATCH start id={} target={} raw={}",
            serverId,
            id,
            target,
            normalizedLine);
      }
      return true;
    }
    if (first.startsWith("-")) {
      String id = first.substring(1);
      ChatHistoryBatchBuffer buf = activeChatHistoryBatches.remove(id);
      if (buf != null) {
        int n = buf.entries.size();
        log.info(
            "[{}] CHATHISTORY BATCH end id={} target={} lines={}", serverId, id, buf.target, n);
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.ChatHistoryBatchReceived(
                    Instant.now(), buf.target, id, java.util.List.copyOf(buf.entries))));
      }
      return true;
    }

    return true;
  }

  private boolean collectChatHistoryFromUnknown(
      String originalLineWithTags, String normalizedLine, ChatHistoryBatchBuffer buf) {
    if (buf == null) return false;
    ParsedIrcLine pl = parseIrcLine(normalizedLine);
    if (pl == null || pl.command() == null) return false;
    String cmd = pl.command().toUpperCase(java.util.Locale.ROOT);
    if (!"PRIVMSG".equals(cmd) && !"NOTICE".equals(cmd)) return false;

    Instant at = PircbotxIrcv3ServerTime.parseServerTimeFromRawLine(originalLineWithTags);
    if (at == null) at = Instant.now();

    String from = nickFromPrefix(pl.prefix());
    String text = pl.trailing();
    if (text == null) text = "";

    String target =
        (buf.target == null || buf.target.isBlank())
            ? (pl.params() != null && !pl.params().isEmpty() ? pl.params().get(0) : "")
            : buf.target;

    if ("PRIVMSG".equals(cmd)) {
      String action = PircbotxUtil.parseCtcpAction(text);
      if (action != null) {
        buf.entries.add(
            new ChatHistoryEntry(at, ChatHistoryEntry.Kind.ACTION, target, from, action));
        return true;
      }
      buf.entries.add(new ChatHistoryEntry(at, ChatHistoryEntry.Kind.PRIVMSG, target, from, text));
      return true;
    }

    buf.entries.add(new ChatHistoryEntry(at, ChatHistoryEntry.Kind.NOTICE, target, from, text));
    return true;
  }

  private static ParsedJoinFailure parseJoinFailure(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return null;
    String s = PircbotxLineParseUtil.normalizeIrcLineForParsing(rawLine);
    if (s == null || s.isBlank()) return null;

    String head = s;
    String trailing = null;
    int idx = s.indexOf(" :");
    if (idx >= 0) {
      head = s.substring(0, idx).trim();
      trailing = s.substring(idx + 2).trim();
      if (trailing != null && trailing.isBlank()) trailing = null;
    }

    String[] parts = head.split("\\s+");
    if (parts.length < 3) return null;

    int codeIdx = parts[0].startsWith(":") ? 1 : 0;
    if (parts.length <= codeIdx + 1) return null;
    if (!PircbotxLineParseUtil.looksNumeric(parts[codeIdx])) return null;
    int start = Math.min(parts.length, codeIdx + 2);
    String channel = null;
    for (int i = start; i < parts.length; i++) {
      String p = parts[i];
      if (PircbotxLineParseUtil.looksLikeChannel(p)) {
        channel = p;
        break;
      }
    }
    if (channel == null || channel.isBlank()) return null;

    return new ParsedJoinFailure(channel, trailing);
  }

  PircbotxBridgeListener(
      String serverId,
      PircbotxConnectionState conn,
      FlowableProcessor<ServerIrcEvent> bus,
      Consumer<PircbotxConnectionState> heartbeatStopper,
      BiConsumer<PircbotxConnectionState, String> reconnectScheduler,
      CtcpRequestHandler ctcpHandler,
      boolean disconnectOnSaslFailure,
      boolean sojuDiscoveryEnabled,
      boolean zncDiscoveryEnabled,
      Consumer<ZncNetwork> zncNetworkDiscovered,
      Consumer<SojuNetwork> sojuNetworkDiscovered,
      Consumer<String> sojuOriginDisconnected,
      Consumer<String> zncOriginDisconnected,
      PlaybackCursorProvider playbackCursorProvider) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.bus = Objects.requireNonNull(bus, "bus");
    this.heartbeatStopper = Objects.requireNonNull(heartbeatStopper, "heartbeatStopper");
    this.reconnectScheduler = Objects.requireNonNull(reconnectScheduler, "reconnectScheduler");
    this.ctcpHandler = Objects.requireNonNull(ctcpHandler, "ctcpHandler");
    this.disconnectOnSaslFailure = disconnectOnSaslFailure;
    this.sojuDiscoveryEnabled = sojuDiscoveryEnabled;
    this.zncDiscoveryEnabled = zncDiscoveryEnabled;
    this.zncNetworkDiscovered = (zncNetworkDiscovered == null) ? (n) -> {} : zncNetworkDiscovered;
    this.sojuNetworkDiscovered =
        (sojuNetworkDiscovered == null) ? (n) -> {} : sojuNetworkDiscovered;
    this.sojuOriginDisconnected =
        (sojuOriginDisconnected == null) ? (id) -> {} : sojuOriginDisconnected;
    this.zncOriginDisconnected =
        (zncOriginDisconnected == null) ? (id) -> {} : zncOriginDisconnected;
    this.playbackCursorProvider =
        java.util.Objects.requireNonNull(playbackCursorProvider, "playbackCursorProvider");
  }

  private Instant inboundAt(Object pircbotxEvent) {
    Instant now = Instant.now();
    return PircbotxIrcv3ServerTime.orNow(PircbotxIrcv3ServerTime.fromEvent(pircbotxEvent), now);
  }

  /**
   * If a ZNC playback capture is active for {@code target} and {@code at} falls in-range, add the
   * entry to the capture and return true to indicate the line should be suppressed from the normal
   * live pipeline.
   */
  private boolean maybeCaptureZncPlayback(
      String target, Instant at, ChatHistoryEntry.Kind kind, String from, String text) {
    try {
      if (!conn.zncPlaybackCapture.shouldCapture(target, at)) return false;
      conn.zncPlaybackCapture.addEntry(
          new ChatHistoryEntry(
              at == null ? Instant.now() : at,
              kind == null ? ChatHistoryEntry.Kind.PRIVMSG : kind,
              target,
              from == null ? "" : from,
              text == null ? "" : text));
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  /**
   * While a ZNC ListNetworks capture is active, parse *status output lines and suppress them from
   * the main UI pipeline.
   */
  private boolean maybeCaptureZncListNetworks(String fromNick, String text) {
    if (!zncDiscoveryEnabled) return false;
    if (!conn.zncListNetworksCaptureActive.get()) return false;

    String from = Objects.toString(fromNick, "").trim();
    if (!"*status".equalsIgnoreCase(from)) return false;

    long started = conn.zncListNetworksCaptureStartedMs.get();
    if (started > 0) {
      long age = System.currentTimeMillis() - started;
      // Safety valve: don't keep suppressing *status output forever if ZNC output format changes.
      if (age > 15_000L) {
        conn.zncListNetworksCaptureActive.set(false);
        return false;
      }
    }

    try {
      PircbotxZncParsers.ParsedListNetworksRow row = PircbotxZncParsers.parseListNetworksRow(text);
      if (row != null) {
        ZncNetwork network = new ZncNetwork(serverId, row.name, row.onIrc);
        String key = row.name.toLowerCase(Locale.ROOT);
        ZncNetwork prev = conn.zncNetworksByNameLower.putIfAbsent(key, network);
        if (prev == null || !prev.equals(network)) {
          conn.zncNetworksByNameLower.put(key, network);
          if (prev == null) {
            log.info(
                "[{}] znc: discovered network name={} (onIrc={})",
                serverId,
                network.name(),
                network.onIrc());
          } else {
            log.info(
                "[{}] znc: updated network name={} (onIrc={})",
                serverId,
                network.name(),
                network.onIrc());
          }
          try {
            zncNetworkDiscovered.accept(network);
          } catch (Exception e) {
            log.debug("[{}] znc: network discovered handler threw", serverId, e);
          }
        }
        return true;
      }

      if (PircbotxZncParsers.looksLikeListNetworksDoneLine(text)) {
        conn.zncListNetworksCaptureActive.set(false);
        log.info(
            "[{}] znc: finished ListNetworks capture ({} networks)",
            serverId,
            conn.zncNetworksByNameLower.size());
        return true;
      }

      // Capture in progress: suppress all *status output (borders, headings, etc).
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  @Override
  public void onConnect(ConnectEvent event) {
    touchInbound();
    PircBotX bot = event.getBot();
    conn.reconnectAttempts.set(0);
    conn.manualDisconnect.set(false);

    bus.onNext(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.Connected(
                Instant.now(), bot.getServerHostname(), bot.getServerPort(), bot.getNick())));
  }

  @Override
  public void onDisconnect(DisconnectEvent event) {
    String override = conn.disconnectReasonOverride.getAndSet(null);
    Exception ex = event.getDisconnectException();
    String reason =
        (override != null && !override.isBlank())
            ? override
            : (ex != null && ex.getMessage() != null) ? ex.getMessage() : "Disconnected";
    if (conn.botRef.compareAndSet(event.getBot(), null)) {
      heartbeatStopper.accept(conn);
    }

    try {
      sojuOriginDisconnected.accept(serverId);
    } catch (Exception e2) {
      log.debug("[{}] soju disconnect cleanup failed: {}", serverId, e2.toString());
    }
    try {
      zncOriginDisconnected.accept(serverId);
    } catch (Exception e2) {
      log.debug("[{}] znc disconnect cleanup failed: {}", serverId, e2.toString());
    }

    try {
      conn.sojuNetworksByNetId.clear();
      conn.sojuListNetworksRequestedThisSession.set(false);
      conn.sojuBouncerNetId.set("");
      conn.sojuBouncerNetworksCapAcked.set(false);
    } catch (Exception ignored) {
    }

    try {
      conn.zncListNetworksRequestedThisSession.set(false);
    } catch (Exception ignored) {
    }
    multilineAccumulator.clear();
    activeChatHistoryBatches.clear();

    bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Disconnected(Instant.now(), reason)));
    boolean suppressReconnect = conn.suppressAutoReconnectOnce.getAndSet(false);
    if (!conn.manualDisconnect.get() && !suppressReconnect) {
      reconnectScheduler.accept(conn, reason);
    }
  }

  @Override
  public void onMessage(MessageEvent event) {
    touchInbound();
    Optional<String> batchId = PircbotxIrcv3BatchTag.fromEvent(event);
    if (batchId.isPresent()) {
      ChatHistoryBatchBuffer buf = activeChatHistoryBatches.get(batchId.get());
      if (buf != null) {
        Instant at = inboundAt(event);
        String from = (event.getUser() != null) ? event.getUser().getNick() : "";
        String msg = PircbotxUtil.safeStr(event::getMessage, "");
        String action = PircbotxUtil.parseCtcpAction(msg);

        String target =
            (buf.target == null || buf.target.isBlank())
                ? (event.getChannel() != null ? event.getChannel().getName() : "")
                : buf.target;

        if (action != null) {
          buf.entries.add(
              new ChatHistoryEntry(at, ChatHistoryEntry.Kind.ACTION, target, from, action));
        } else {
          buf.entries.add(
              new ChatHistoryEntry(at, ChatHistoryEntry.Kind.PRIVMSG, target, from, msg));
        }
        return;
      }
    }

    Instant at = inboundAt(event);
    String channel = event.getChannel().getName();
    maybeEmitHostmaskObserved(channel, event.getUser());
    String msg = event.getMessage();
    String from = (event.getUser() == null) ? "" : event.getUser().getNick();
    Map<String, String> ircv3Tags =
        withObservedHostmaskTag(new HashMap<>(ircv3TagsFromEvent(event)), event.getUser());
    String messageId = ircv3MessageId(ircv3Tags);
    Ircv3MultilineAccumulator.FoldResult folded =
        multilineAccumulator.fold("PRIVMSG", from, channel, at, msg, messageId, ircv3Tags);
    if (folded.suppressed()) {
      return;
    }
    if (folded.at() != null) at = folded.at();
    msg = folded.text();
    ircv3Tags = folded.tags();
    if (folded.messageId() != null && !folded.messageId().isBlank()) {
      messageId = folded.messageId();
    } else {
      messageId = ircv3MessageId(ircv3Tags);
    }

    String action = PircbotxUtil.parseCtcpAction(msg);
    if (action != null) {
      if (maybeCaptureZncPlayback(channel, at, ChatHistoryEntry.Kind.ACTION, from, action)) {
        return;
      }
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ChannelAction(at, channel, from, action, messageId, ircv3Tags)));
      return;
    }

    if (maybeCaptureZncPlayback(channel, at, ChatHistoryEntry.Kind.PRIVMSG, from, msg)) {
      return;
    }

    bus.onNext(
        new ServerIrcEvent(
            serverId, new IrcEvent.ChannelMessage(at, channel, from, msg, messageId, ircv3Tags)));
  }

  @Override
  public void onAction(ActionEvent event) {
    touchInbound();

    String botNick = resolveSelfNick(event != null ? event.getBot() : null);
    String pmDest = privmsgTargetFromEvent(event);

    Optional<String> batchId = PircbotxIrcv3BatchTag.fromEvent(event);
    if (batchId.isPresent()) {
      ChatHistoryBatchBuffer buf = activeChatHistoryBatches.get(batchId.get());
      if (buf != null) {
        Instant at = inboundAt(event);
        String from = (event.getUser() != null) ? event.getUser().getNick() : "";
        String action = PircbotxUtil.safeStr(() -> event.getAction(), "");
        boolean fromSelf =
            botNick != null && !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
        String batchMsgId = ircv3MessageId(ircv3TagsFromEvent(event));
        if ((pmDest == null || pmDest.isBlank()) && fromSelf) {
          String hinted = inferPrivateDestinationFromHints(from, "ACTION", action, batchMsgId);
          if (!hinted.isBlank()) {
            pmDest = hinted;
          }
        }

        String fallbackTarget;
        if (event.getChannel() != null) {
          fallbackTarget = event.getChannel().getName();
        } else {
          fallbackTarget = derivePrivateConversationTarget(botNick, from, pmDest);
        }

        String target = (buf.target == null || buf.target.isBlank()) ? fallbackTarget : buf.target;
        buf.entries.add(
            new ChatHistoryEntry(at, ChatHistoryEntry.Kind.ACTION, target, from, action));
        return;
      }
    }

    Instant at = inboundAt(event);
    String from = (event.getUser() != null) ? event.getUser().getNick() : "";
    String action = PircbotxUtil.safeStr(() -> event.getAction(), "");
    boolean fromSelf =
        botNick != null && !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
    Map<String, String> tags = new HashMap<>(ircv3TagsFromEvent(event));
    String messageId = ircv3MessageId(tags);
    if ((pmDest == null || pmDest.isBlank()) && fromSelf) {
      String hinted = inferPrivateDestinationFromHints(from, "ACTION", action, messageId);
      if (!hinted.isBlank()) {
        pmDest = hinted;
      }
    }
    if (pmDest != null && !pmDest.isBlank()) {
      tags.put(TAG_IRCAFE_PM_TARGET, pmDest);
    }
    withObservedHostmaskTag(tags, event.getUser());
    Map<String, String> ircv3Tags = tags;
    messageId = ircv3MessageId(ircv3Tags);

    if (event.getChannel() != null) {
      String channel = event.getChannel().getName();
      maybeEmitHostmaskObserved(channel, event.getUser());

      if (maybeCaptureZncPlayback(channel, at, ChatHistoryEntry.Kind.ACTION, from, action)) {
        return;
      }
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ChannelAction(at, channel, from, action, messageId, ircv3Tags)));
    } else {
      maybeEmitHostmaskObserved("", event.getUser());

      String convTarget = derivePrivateConversationTarget(botNick, from, pmDest);

      // For queries, use the conversation peer nick as the "target" key.
      if (!"*playback".equalsIgnoreCase(from)
          && maybeCaptureZncPlayback(convTarget, at, ChatHistoryEntry.Kind.ACTION, from, action)) {
        return;
      }

      bus.onNext(
          new ServerIrcEvent(
              serverId, new IrcEvent.PrivateAction(at, from, action, messageId, ircv3Tags)));
    }
  }

  @Override
  public void onTopic(TopicEvent event) {
    touchInbound();
    if (event == null || event.getChannel() == null) return;
    String channel = event.getChannel().getName();
    String topic = event.getTopic();
    if (channel == null || channel.isBlank()) return;
    bus.onNext(
        new ServerIrcEvent(
            serverId, new IrcEvent.ChannelTopicUpdated(Instant.now(), channel, topic)));
  }

  @Override
  public void onInvite(InviteEvent event) {
    touchInbound();
    if (event == null) return;

    String channel = "";
    try {
      Object c = reflectCall(event, "getChannel");
      if (c != null) channel = String.valueOf(c);
    } catch (Exception ignored) {
    }
    if (channel == null || channel.isBlank()) {
      try {
        Object c = reflectCall(event, "getChannelName");
        if (c != null) channel = String.valueOf(c);
      } catch (Exception ignored) {
      }
    }
    channel = channel == null ? "" : channel.trim();
    if (channel.isBlank()) return;

    String from = "server";
    String invitee = "";
    String reason = "";
    try {
      if (event.getUser() != null) {
        maybeEmitHostmaskObserved(channel, event.getUser());
        String nick = event.getUser().getNick();
        if (nick != null && !nick.isBlank()) from = nick.trim();
      }
    } catch (Exception ignored) {
    }

    try {
      String raw = rawLineFromEvent(event);
      ParsedIrcLine parsed = parseIrcLine(PircbotxLineParseUtil.normalizeIrcLineForParsing(raw));
      ParsedInviteLine pi = parseInviteLine(parsed);
      if (pi != null) {
        if (from.isBlank()) from = Objects.toString(pi.fromNick(), "").trim();
        if (!Objects.toString(pi.channel(), "").isBlank()) channel = pi.channel().trim();
        invitee = Objects.toString(pi.inviteeNick(), "").trim();
        reason = Objects.toString(pi.reason(), "").trim();
      }
    } catch (Exception ignored) {
    }

    bus.onNext(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.InvitedToChannel(Instant.now(), channel, from, invitee, reason, false)));
  }

  @Override
  public void onPrivateMessage(PrivateMessageEvent event) {
    touchInbound();

    String botNick = resolveSelfNick(event != null ? event.getBot() : null);
    String pmDest = privmsgTargetFromEvent(event);

    Optional<String> batchId = PircbotxIrcv3BatchTag.fromEvent(event);
    if (batchId.isPresent()) {
      ChatHistoryBatchBuffer buf = activeChatHistoryBatches.get(batchId.get());
      if (buf != null) {
        Instant at = inboundAt(event);
        String from = (event.getUser() != null) ? event.getUser().getNick() : "";
        String msg = PircbotxUtil.safeStr(event::getMessage, "");
        String action = PircbotxUtil.parseCtcpAction(msg);
        String kind = action == null ? "PRIVMSG" : "ACTION";
        String hintPayload = action == null ? msg : action;
        String batchMsgId = ircv3MessageId(ircv3TagsFromEvent(event));

        boolean fromSelf =
            botNick != null && !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
        if ((pmDest == null || pmDest.isBlank()) && fromSelf) {
          String hinted = inferPrivateDestinationFromHints(from, kind, hintPayload, batchMsgId);
          if (!hinted.isBlank()) {
            pmDest = hinted;
          }
        }
        if (fromSelf) {
          // Don't show our internal ZNC playback bootstrap line.
          if (isZncPlayStarCursorCommand(msg)) {
            return;
          }
          if (pmDest != null) {
            if ("*playback".equalsIgnoreCase(pmDest)
                && msg != null
                && msg.toLowerCase(Locale.ROOT).startsWith("play ")) {
              return;
            }
            if ("*status".equalsIgnoreCase(pmDest)
                && msg != null
                && msg.equalsIgnoreCase("ListNetworks")) {
              return;
            }
          }
        }

        String convTarget = derivePrivateConversationTarget(botNick, from, pmDest);
        String target = (buf.target == null || buf.target.isBlank()) ? convTarget : buf.target;

        if (action != null) {
          buf.entries.add(
              new ChatHistoryEntry(at, ChatHistoryEntry.Kind.ACTION, target, from, action));
        } else {
          buf.entries.add(
              new ChatHistoryEntry(at, ChatHistoryEntry.Kind.PRIVMSG, target, from, msg));
        }
        return;
      }
    }

    Instant at = inboundAt(event);
    String from = event.getUser().getNick();
    String msg = event.getMessage();
    String actionPayload = PircbotxUtil.parseCtcpAction(msg);
    String hintKind = actionPayload == null ? "PRIVMSG" : "ACTION";
    String hintPayload = actionPayload == null ? msg : actionPayload;

    boolean fromSelf = false;
    try {
      fromSelf =
          botNick != null && !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
    } catch (Exception ignored) {
    }

    Map<String, String> tags = new HashMap<>(ircv3TagsFromEvent(event));
    String messageId = ircv3MessageId(tags);
    if ((pmDest == null || pmDest.isBlank()) && fromSelf) {
      String hinted = inferPrivateDestinationFromHints(from, hintKind, hintPayload, messageId);
      if (!hinted.isBlank()) {
        pmDest = hinted;
      }
    }
    if (pmDest != null && !pmDest.isBlank()) {
      tags.put(TAG_IRCAFE_PM_TARGET, pmDest);
    }
    withObservedHostmaskTag(tags, event.getUser());
    Map<String, String> ircv3Tags = tags;
    messageId = ircv3MessageId(ircv3Tags);

    // Suppress our own internal ZNC control messages that might be echoed back (echo-message)
    // or replayed by servers that emulate ZNC playback (e.g., Ergo).
    if (fromSelf) {
      // "play * <cursor>" is a control line we send to bootstrap playback; never show it as chat.
      if (isZncPlayStarCursorCommand(msg)) {
        return;
      }
      if (pmDest != null) {
        if ("*playback".equalsIgnoreCase(pmDest)
            && msg != null
            && msg.toLowerCase(Locale.ROOT).startsWith("play ")) {
          return;
        }
        if ("*status".equalsIgnoreCase(pmDest)
            && msg != null
            && msg.equalsIgnoreCase("ListNetworks")) {
          return;
        }
      }
    }

    if ("*status".equalsIgnoreCase(from)) {
      maybeMarkZncDetected("private message from *status", null);
    }

    // ZNC multi-network discovery: parse and suppress the *status ListNetworks table.
    if (maybeCaptureZncListNetworks(from, msg)) {
      return;
    }

    if ("*playback".equalsIgnoreCase(from)) {
      conn.zncPlaybackCapture.onPlaybackControlLine(msg);
    }

    // If we're in the middle of an explicit playback range request for this query, capture and
    // suppress replayed lines. (Playback control lines from *playback are kept visible.)
    String convTarget = derivePrivateConversationTarget(botNick, from, pmDest);
    Ircv3MultilineAccumulator.FoldResult folded =
        multilineAccumulator.fold("PRIVMSG", from, convTarget, at, msg, messageId, ircv3Tags);
    if (folded.suppressed()) {
      return;
    }
    if (folded.at() != null) at = folded.at();
    msg = folded.text();
    ircv3Tags = folded.tags();
    if (folded.messageId() != null && !folded.messageId().isBlank()) {
      messageId = folded.messageId();
    } else {
      messageId = ircv3MessageId(ircv3Tags);
    }
    actionPayload = PircbotxUtil.parseCtcpAction(msg);
    if (!"*playback".equalsIgnoreCase(from)) {
      String action = PircbotxUtil.parseCtcpAction(msg);
      if (action != null) {
        if (maybeCaptureZncPlayback(convTarget, at, ChatHistoryEntry.Kind.ACTION, from, action))
          return;
      } else {
        if (maybeCaptureZncPlayback(convTarget, at, ChatHistoryEntry.Kind.PRIVMSG, from, msg))
          return;
      }
    }

    maybeEmitHostmaskObserved("", event.getUser());

    String action = actionPayload;
    if (action != null) {
      bus.onNext(
          new ServerIrcEvent(
              serverId, new IrcEvent.PrivateAction(at, from, action, messageId, ircv3Tags)));
      return;
    }

    // If echo-message is enabled, our own outbound CTCP requests can come back as inbound.
    // Never treat those as inbound requests, and never show them.
    if (fromSelf && isCtcpWrapped(msg)) {
      return;
    }

    // CTCP auto-replies are handled in onGenericCTCP/onFinger so we can safely ignore echo-message
    // copies.

    bus.onNext(
        new ServerIrcEvent(
            serverId, new IrcEvent.PrivateMessage(at, from, msg, messageId, ircv3Tags)));
  }

  @Override
  public void onNotice(NoticeEvent event) {
    touchInbound();
    Optional<String> batchId = PircbotxIrcv3BatchTag.fromEvent(event);
    if (batchId.isPresent()) {
      ChatHistoryBatchBuffer buf = activeChatHistoryBatches.get(batchId.get());
      if (buf != null) {
        Instant at = inboundAt(event);
        String from = (event.getUser() != null) ? event.getUser().getNick() : "server";
        String notice = PircbotxUtil.safeStr(event::getNotice, "");
        String target = (buf.target == null || buf.target.isBlank()) ? "status" : buf.target;
        buf.entries.add(
            new ChatHistoryEntry(at, ChatHistoryEntry.Kind.NOTICE, target, from, notice));
        return;
      }
    }
    Instant at = inboundAt(event);
    String from = (event.getUser() != null) ? event.getUser().getNick() : "server";
    String notice = event.getNotice();
    Map<String, String> ircv3Tags =
        withObservedHostmaskTag(new HashMap<>(ircv3TagsFromEvent(event)), event.getUser());
    String messageId = ircv3MessageId(ircv3Tags);
    String foldTarget = null;
    try {
      Channel ch = event.getChannel();
      if (ch != null) foldTarget = ch.getName();
    } catch (Exception ignored) {
    }
    if (foldTarget == null || foldTarget.isBlank()) foldTarget = from;
    Ircv3MultilineAccumulator.FoldResult folded =
        multilineAccumulator.fold("NOTICE", from, foldTarget, at, notice, messageId, ircv3Tags);
    if (folded.suppressed()) {
      return;
    }
    if (folded.at() != null) at = folded.at();
    notice = folded.text();
    ircv3Tags = folded.tags();
    if (folded.messageId() != null && !folded.messageId().isBlank()) {
      messageId = folded.messageId();
    } else {
      messageId = ircv3MessageId(ircv3Tags);
    }

    // ZNC multi-network discovery: parse and suppress the *status ListNetworks table.
    if (maybeCaptureZncListNetworks(from, notice)) {
      return;
    }

    if ("*playback".equalsIgnoreCase(from)) {
      conn.zncPlaybackCapture.onPlaybackControlLine(notice);
    }

    // Capture and suppress replayed notices for the active playback target.
    if (!"*playback".equalsIgnoreCase(from)) {
      String t = null;
      try {
        Channel ch = event.getChannel();
        if (ch != null) t = ch.getName();
      } catch (Exception ignored) {
      }
      if (t == null || t.isBlank()) t = from;
      if (t != null && maybeCaptureZncPlayback(t, at, ChatHistoryEntry.Kind.NOTICE, from, notice)) {
        return;
      }
    }

    if (event.getUser() != null) {
      maybeEmitHostmaskObserved("", event.getUser());
    }

    String target = null;
    try {
      Channel ch = event.getChannel();
      if (ch != null) target = ch.getName();
    } catch (Exception ignored) {
    }

    bus.onNext(
        new ServerIrcEvent(
            serverId, new IrcEvent.Notice(at, from, target, notice, messageId, ircv3Tags)));
  }

  @Override
  public void onGenericCTCP(GenericCTCPEvent event) throws Exception {
    touchInbound();
    Instant at = inboundAt(event);
    log.info("CTCP: {}", event);

    String botNick = resolveSelfNick(event != null ? event.getBot() : null);
    String botNickDirect = resolveBotNick(event != null ? event.getBot() : null);
    String from = (event != null && event.getUser() != null) ? event.getUser().getNick() : "";
    if (from == null) from = "";
    from = from.trim();
    String rawLine = rawLineFromEvent(event);
    String normalizedRaw = PircbotxLineParseUtil.normalizeIrcLineForParsing(rawLine);
    ParsedIrcLine rawParsed = parseIrcLine(normalizedRaw);
    String rawFrom = (rawParsed == null) ? "" : nickFromPrefix(rawParsed.prefix());
    if (from.isBlank() && !rawFrom.isBlank()) {
      from = rawFrom;
    }
    if (from.isBlank()) from = "server";
    String rawCmd =
        (rawParsed != null && rawParsed.command() != null)
            ? rawParsed.command().toUpperCase(Locale.ROOT)
            : "";
    boolean rawIsNotice = "NOTICE".equals(rawCmd);
    boolean rawIsPrivmsg = "PRIVMSG".equals(rawCmd);
    String selfHintBefore = Objects.toString(conn.selfNickHint.get(), "").trim();
    boolean fromSelf = nickMatchesSelf(event != null ? event.getBot() : null, from);
    boolean rawFromSelf = nickMatchesSelf(event != null ? event.getBot() : null, rawFrom);
    boolean selfEcho = isSelfEchoed(event != null ? event.getBot() : null, from);
    String selfHintAfter = Objects.toString(conn.selfNickHint.get(), "").trim();

    log.debug(
        "[{}] CTCPDBG pre cmdClass={} from={} rawFrom={} rawCmd={} selfHintBefore={} selfHintAfter={} botNick={} botNickDirect={} fromSelf={} rawFromSelf={} selfEcho={} raw={}",
        serverId,
        (event == null) ? "null" : event.getClass().getSimpleName(),
        from,
        rawFrom,
        rawCmd,
        selfHintBefore,
        selfHintAfter,
        botNick,
        botNickDirect,
        fromSelf,
        rawFromSelf,
        selfEcho,
        normalizedRaw);

    // Ignore self-echoed CTCP requests (echo-message), otherwise we can reply to ourselves.
    if (fromSelf) {
      log.debug("[{}] CTCPDBG drop reason=self-from from={}", serverId, from);
      return;
    }
    if (rawFromSelf) {
      log.debug("[{}] CTCPDBG drop reason=self-raw-from rawFrom={}", serverId, rawFrom);
      return;
    }
    if (selfEcho) {
      log.debug("[{}] CTCPDBG drop reason=self-echo from={}", serverId, from);
      return;
    }

    // For private CTCP, only treat it as "incoming to us" if the destination is our nick.
    // This prevents echo-message copies of our own outbound CTCP requests (dest=otherNick) from
    // being processed.
    String pmDest = null;
    if (event != null && event.getChannel() == null) {
      pmDest = privmsgTargetFromEvent(event);
      if ((pmDest == null || pmDest.isBlank())
          && rawParsed != null
          && rawParsed.params() != null
          && !rawParsed.params().isEmpty()) {
        pmDest = rawParsed.params().get(0);
      }
      boolean pmDestKnown = pmDest != null && !pmDest.isBlank();
      boolean destMismatch = pmDestKnown && !botNick.isBlank() && !pmDest.equalsIgnoreCase(botNick);
      log.debug(
          "[{}] CTCPDBG route channel={} pmDest={} pmDestKnown={} botNick={} rawIsPrivmsg={} rawIsNotice={} destMismatch={}",
          serverId,
          event.getChannel(),
          pmDest,
          pmDestKnown,
          botNick,
          rawIsPrivmsg,
          rawIsNotice,
          destMismatch);
      if (log.isDebugEnabled()) {
        log.debug(
            "[{}] CTCP parsed rawCmd={} pmDest={} from={} botNick={} raw={}",
            serverId,
            rawCmd,
            pmDest,
            from,
            botNick,
            normalizedRaw);
      }
      if (destMismatch) {
        log.debug(
            "[{}] CTCPDBG drop reason=dest-not-self pmDest={} botNick={}",
            serverId,
            pmDest,
            botNick);
        return;
      }
    }

    if (event != null && event.getUser() != null) {
      String ch = (event.getChannel() != null) ? event.getChannel().getName() : "";
      maybeEmitHostmaskObserved(ch, event.getUser());
    }

    String channel =
        (event != null && event.getChannel() != null) ? event.getChannel().getName() : null;
    String simple = (event == null) ? "CTCP" : event.getClass().getSimpleName();
    String cmd =
        simple.endsWith("Event") ? simple.substring(0, simple.length() - "Event".length()) : simple;
    cmd = cmd.toUpperCase(Locale.ROOT);

    String arg = null;
    try {
      java.lang.reflect.Method m = event.getClass().getMethod("getPingValue");
      Object v = m.invoke(event);
      if (v != null) arg = v.toString();
    } catch (Exception ignored) {
    }

    bus.onNext(
        new ServerIrcEvent(
            serverId, new IrcEvent.CtcpRequestReceived(at, from, cmd, arg, channel)));

    // Auto-reply only for private CTCP that is actually addressed to us.
    // PircBotX 2.3.1 emits these CTCP events from PRIVMSG parsing.
    // If destination parsing is unavailable (e.g. raw line hidden), treat private non-self CTCP as
    // addressed to us.
    boolean pmDestKnown = pmDest != null && !pmDest.isBlank();
    boolean shouldAutoReply =
        event != null
            && event.getChannel() == null
            && !botNick.isBlank()
            && (!pmDestKnown || pmDest.equalsIgnoreCase(botNick));
    log.debug(
        "[{}] CTCPDBG autoreply-eval shouldAutoReply={} pmDest={} pmDestKnown={} botNick={} rawIsPrivmsg={} rawIsNotice={} cmd={} arg={}",
        serverId,
        shouldAutoReply,
        pmDest,
        pmDestKnown,
        botNick,
        rawIsPrivmsg,
        rawIsNotice,
        cmd,
        arg);
    if (shouldAutoReply) {
      String wrapped =
          "\u0001" + cmd + ((arg != null && !arg.isBlank()) ? (" " + arg) : "") + "\u0001";
      log.debug(
          "[{}] CTCPDBG autoreply-send to={} wrapped={}",
          serverId,
          from,
          wrapped.replace('\u0001', '|'));
      ctcpHandler.handle(event.getBot(), from, wrapped);
    }
  }

  @Override
  public void onFinger(FingerEvent event) throws Exception {
    touchInbound();
    Instant at = inboundAt(event);
    log.info("CTCP: {}", event);

    String botNick = resolveSelfNick(event != null ? event.getBot() : null);
    String from = (event != null && event.getUser() != null) ? event.getUser().getNick() : "";
    if (from == null) from = "";
    from = from.trim();
    String rawLine = rawLineFromEvent(event);
    String normalizedRaw = PircbotxLineParseUtil.normalizeIrcLineForParsing(rawLine);
    ParsedIrcLine rawParsed = parseIrcLine(normalizedRaw);
    String rawFrom = (rawParsed == null) ? "" : nickFromPrefix(rawParsed.prefix());
    if (from.isBlank() && !rawFrom.isBlank()) {
      from = rawFrom;
    }
    if (from.isBlank()) from = "server";

    if (nickMatchesSelf(event != null ? event.getBot() : null, from)) {
      return;
    }
    if (nickMatchesSelf(event != null ? event.getBot() : null, rawFrom)) {
      return;
    }
    if (isSelfEchoed(event != null ? event.getBot() : null, from)) {
      return;
    }

    String pmDest = null;
    if (event != null && event.getChannel() == null) {
      pmDest = privmsgTargetFromEvent(event);
      if ((pmDest == null || pmDest.isBlank())
          && rawParsed != null
          && rawParsed.params() != null
          && !rawParsed.params().isEmpty()) {
        pmDest = rawParsed.params().get(0);
      }
      boolean pmDestKnown = pmDest != null && !pmDest.isBlank();
      if (pmDestKnown && !botNick.isBlank() && !pmDest.equalsIgnoreCase(botNick)) {
        return;
      }
    }

    if (event != null && event.getUser() != null) {
      String ch = (event.getChannel() != null) ? event.getChannel().getName() : "";
      maybeEmitHostmaskObserved(ch, event.getUser());
    }

    String channel =
        (event != null && event.getChannel() != null) ? event.getChannel().getName() : null;
    bus.onNext(
        new ServerIrcEvent(
            serverId, new IrcEvent.CtcpRequestReceived(at, from, "FINGER", null, channel)));

    boolean pmDestKnown = pmDest != null && !pmDest.isBlank();
    if (event != null
        && event.getChannel() == null
        && !botNick.isBlank()
        && (!pmDestKnown || pmDest.equalsIgnoreCase(botNick))) {
      ctcpHandler.handle(event.getBot(), from, "\u0001FINGER\u0001");
    }
  }

  @Override
  public void onWhois(WhoisEvent event) {
    touchInbound();
    if (event == null) return;

    try {
      String nick = PircbotxUtil.safeStr(() -> event.getNick(), "");
      String login = PircbotxUtil.safeStr(() -> event.getLogin(), "");
      String host = PircbotxUtil.safeStr(() -> event.getHostname(), "");
      String real = PircbotxUtil.safeStr(() -> event.getRealname(), "");
      String server = PircbotxUtil.safeStr(() -> event.getServer(), "");
      String serverInfo = PircbotxUtil.safeStr(() -> event.getServerInfo(), "");
      List<String> channels = PircbotxUtil.safeList(() -> event.getChannels());
      long idleSeconds = PircbotxUtil.safeLong(() -> event.getIdleSeconds(), -1);
      long signOnTime = PircbotxUtil.safeLong(() -> event.getSignOnTime(), -1);
      String registeredAs = PircbotxUtil.safeStr(() -> event.getRegisteredAs(), "");
      java.util.ArrayList<String> lines = new java.util.ArrayList<>();

      String ident = login.isBlank() ? "" : login;
      String hostPart = host.isBlank() ? "" : host;
      String userHost =
          (!ident.isBlank() || !hostPart.isBlank())
              ? (ident + "@" + hostPart).replaceAll("^@|@$", "")
              : "";
      if (!nick.isBlank() && !userHost.isBlank() && userHost.contains("@")) {
        String observed = nick + "!" + userHost;
        bus.onNext(
            new ServerIrcEvent(
                serverId, new IrcEvent.UserHostmaskObserved(Instant.now(), "", nick, observed)));
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
      if (channels != null && !channels.isEmpty())
        lines.add("Channels: " + String.join(" ", channels));
      if (lines.isEmpty()) lines.add("(no WHOIS details)");

      String n = nick == null || nick.isBlank() ? "(unknown)" : nick;
      bus.onNext(
          new ServerIrcEvent(
              serverId, new IrcEvent.WhoisResult(Instant.now(), n, List.copyOf(lines))));
    } catch (Exception ex) {
      bus.onNext(
          new ServerIrcEvent(
              serverId, new IrcEvent.Error(Instant.now(), "Whois parse failed", ex)));
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
    if (line == null || line.isBlank()) line = String.valueOf(event);
    String rawLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);
    ParsedIrcLine parsedRawLine = parseIrcLine(rawLine);
    if (parsedRawLine != null
        && parsedRawLine.command() != null
        && PircbotxLineParseUtil.looksNumeric(parsedRawLine.command())
        && parsedRawLine.params() != null
        && !parsedRawLine.params().isEmpty()) {
      // For server numerics, the first param is typically the recipient nick (us).
      rememberSelfNickHint(parsedRawLine.params().get(0));
    }

    ParsedInviteLine parsedInvite = parseInviteLine(parsedRawLine);
    if (parsedInvite != null) {
      Instant at = PircbotxIrcv3ServerTime.parseServerTimeFromRawLine(line);
      if (at == null) at = Instant.now();
      String from = Objects.toString(parsedInvite.fromNick(), "").trim();
      if (from.isEmpty()) from = "server";
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.InvitedToChannel(
                  at,
                  parsedInvite.channel(),
                  from,
                  parsedInvite.inviteeNick(),
                  parsedInvite.reason(),
                  true)));
      return;
    }

    ParsedWallopsLine parsedWallops = parseWallopsLine(parsedRawLine);
    if (parsedWallops != null) {
      Instant at = PircbotxIrcv3ServerTime.parseServerTimeFromRawLine(line);
      if (at == null) at = Instant.now();
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.WallopsReceived(at, parsedWallops.from(), parsedWallops.message())));
      return;
    }

    if (maybeEmitMonitorNumeric(rawLine, line)) {
      return;
    }

    if (handleBatchControlLine(rawLine)) {
      return;
    }

    // R5.2c: As a fallback, try to capture playback PRIVMSG/NOTICE lines that PircBotX surfaced
    // as UnknownEvent (rare, but can happen for echoed/outbound playback lines).
    try {
      ParsedIrcLine pl = parseIrcLine(rawLine);
      if (pl != null && pl.command() != null) {
        String cmd = pl.command().toUpperCase(Locale.ROOT);
        if ("PRIVMSG".equals(cmd) || "NOTICE".equals(cmd)) {
          Instant at = PircbotxIrcv3ServerTime.parseServerTimeFromRawLine(line);
          if (at == null) at = Instant.now();
          String from = nickFromPrefix(pl.prefix());

          String botNick = resolveSelfNick(event != null ? event.getBot() : null);
          if (botNick == null) botNick = "";
          String trailing = pl.trailing();
          if (trailing == null) trailing = "";

          String dest = (pl.params() != null && !pl.params().isEmpty()) ? pl.params().get(0) : "";
          String target = (dest == null) ? "" : dest;
          if (target.isBlank()) target = from;

          String action = PircbotxUtil.parseCtcpAction(trailing);
          ChatHistoryEntry.Kind kind;
          String payload;
          if (action != null) {
            kind = ChatHistoryEntry.Kind.ACTION;
            payload = action;
          } else if ("NOTICE".equals(cmd)) {
            kind = ChatHistoryEntry.Kind.NOTICE;
            payload = trailing;
          } else {
            kind = ChatHistoryEntry.Kind.PRIVMSG;
            payload = trailing;
          }

          // For PMs, derive the conversation target from the destination:
          // - dest == our nick => inbound PM, conversation is the sender
          // - dest != our nick => echo-message/outbound PM, conversation is the destination
          if (!target.startsWith("#") && !target.startsWith("&")) {
            target = derivePrivateConversationTarget(botNick, from, dest);
          }

          boolean fromSelf = !botNick.isBlank() && from != null && from.equalsIgnoreCase(botNick);
          if (fromSelf) {
            if (isZncPlayStarCursorCommand(payload)) {
              return;
            }
            if (dest != null) {
              if ("*playback".equalsIgnoreCase(dest)
                  && payload.toLowerCase(java.util.Locale.ROOT).startsWith("play ")) {
                return;
              }
              if ("*status".equalsIgnoreCase(dest) && payload.equalsIgnoreCase("ListNetworks")) {
                return;
              }
            }
          }

          // ZNC multi-network discovery: parse and suppress the *status ListNetworks table.
          if (maybeCaptureZncListNetworks(from, payload)) {
            return;
          }

          if (maybeCaptureZncPlayback(target, at, kind, from, payload)) {
            return;
          }
        }
      }
    } catch (Exception ignored) {
    }
    Optional<String> maybeBatchId = PircbotxIrcv3BatchTag.fromRawLine(line);
    if (maybeBatchId.isPresent()) {
      ChatHistoryBatchBuffer buf = activeChatHistoryBatches.get(maybeBatchId.get());
      if (buf != null && collectChatHistoryFromUnknown(line, rawLine, buf)) {
        return;
      }
    }

    Integer saslCode = parseSaslNumericCode(rawLine);
    if (saslCode != null) {
      handleSaslNumeric(saslCode, rawLine);
      return;
    }

    if (sojuDiscoveryEnabled) {
      // soju bouncer network discovery: parse BOUNCER NETWORK entries.
      PircbotxSojuParsers.ParsedBouncerNetwork bn =
          PircbotxSojuParsers.parseBouncerNetworkLine(rawLine);
      if (bn != null) {
        SojuNetwork network = new SojuNetwork(serverId, bn.netId(), bn.name(), bn.attrs());
        SojuNetwork prev = conn.sojuNetworksByNetId.putIfAbsent(bn.netId(), network);
        if (prev == null) {
          log.info(
              "[{}] soju: discovered network netId={} name={}",
              serverId,
              network.netId(),
              network.name());
        } else if (!prev.equals(network)) {
          conn.sojuNetworksByNetId.put(bn.netId(), network);
          log.info(
              "[{}] soju: updated network netId={} name={} (attrs changed)",
              serverId,
              network.netId(),
              network.name());
        }
        boolean changed = (prev == null) || !prev.equals(network);
        if (changed) {
          try {
            sojuNetworkDiscovered.accept(network);
          } catch (Exception e) {
            log.debug("[{}] soju: network discovered handler threw", serverId, e);
          }
        }
        return;
      }
    }

    String maybeSojuNetId = PircbotxSojuParsers.parseRpl005BouncerNetId(rawLine);
    if (maybeSojuNetId != null && !maybeSojuNetId.isBlank()) {
      String prev = conn.sojuBouncerNetId.get();
      if (prev == null || prev.isBlank()) {
        conn.sojuBouncerNetId.set(maybeSojuNetId);
        log.info(
            "[{}] soju: BOUNCER_NETID={} (connection is bound to a bouncer network)",
            serverId,
            maybeSojuNetId);
      } else if (!java.util.Objects.equals(prev, maybeSojuNetId)) {
        conn.sojuBouncerNetId.set(maybeSojuNetId);
        log.info("[{}] soju: BOUNCER_NETID changed {} -> {}", serverId, prev, maybeSojuNetId);
      }
    }

    if (PircbotxWhoUserhostParsers.parseRpl005IsupportHasWhox(rawLine)) {
      bus.onNext(
          new ServerIrcEvent(serverId, new IrcEvent.WhoxSupportObserved(Instant.now(), true)));
    }
    maybeApplyMonitorIsupport(rawLine);
    if (rawLine != null && rawLine.contains(" AWAY") && log.isDebugEnabled()) {
      log.debug("[{}] inbound AWAY-ish line received in onUnknown: {}", serverId, rawLine);
    }
    PircbotxAwayParsers.ParsedAwayNotify awayNotify = PircbotxAwayParsers.parseAwayNotify(rawLine);
    if (awayNotify != null && awayNotify.nick() != null && !awayNotify.nick().isBlank()) {
      log.debug(
          "[{}] parsed away-notify: nick={} state={} msg={}",
          serverId,
          awayNotify.nick(),
          awayNotify.awayState(),
          awayNotify.message());
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserAwayStateObserved(
                  Instant.now(), awayNotify.nick(), awayNotify.awayState(), awayNotify.message())));
      return;
    } else if (rawLine != null && rawLine.contains(" AWAY") && log.isDebugEnabled()) {
      log.debug("[{}] inbound AWAY-ish line did NOT parse as away-notify: {}", serverId, rawLine);
    }

    PircbotxChannelModeParsers.ParsedRpl324 parsed =
        PircbotxChannelModeParsers.parseRpl324(rawLine);
    if (parsed != null) {
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ChannelModesListed(Instant.now(), parsed.channel(), parsed.details())));
      return;
    }

    PircbotxAwayParsers.ParsedAwayConfirmation away =
        PircbotxAwayParsers.parseRpl305or306Away(rawLine);
    if (away != null) {
      String msg = away.message();
      if (msg == null || msg.isBlank()) {
        msg =
            away.away()
                ? "You have been marked as being away"
                : "You are no longer marked as being away";
      }
      bus.onNext(
          new ServerIrcEvent(
              serverId, new IrcEvent.AwayStatusChanged(Instant.now(), away.away(), msg)));
      return;
    }
    List<PircbotxWhoUserhostParsers.UserhostEntry> uh =
        PircbotxWhoUserhostParsers.parseRpl302Userhost(rawLine);
    if (uh != null && !uh.isEmpty()) {
      Instant now = Instant.now();
      for (PircbotxWhoUserhostParsers.UserhostEntry e : uh) {
        if (e == null) continue;
        bus.onNext(
            new ServerIrcEvent(
                serverId, new IrcEvent.UserHostmaskObserved(now, "", e.nick(), e.hostmask())));
        IrcEvent.AwayState as =
            (e.awayState() == null) ? IrcEvent.AwayState.UNKNOWN : e.awayState();
        if (as == IrcEvent.AwayState.AWAY || as == IrcEvent.AwayState.HERE) {
          bus.onNext(
              new ServerIrcEvent(serverId, new IrcEvent.UserAwayStateObserved(now, e.nick(), as)));
        }
      }
    }
    PircbotxWhoisParsers.ParsedWhoisAway whoisAway =
        PircbotxWhoisParsers.parseRpl301WhoisAway(rawLine);
    if (whoisAway != null && whoisAway.nick() != null && !whoisAway.nick().isBlank()) {
      String nk = whoisAway.nick().trim();
      String k = nk.toLowerCase(Locale.ROOT);
      conn.whoisSawAwayByNickLower.computeIfPresent(k, (kk, vv) -> Boolean.TRUE);
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserAwayStateObserved(
                  Instant.now(), nk, IrcEvent.AwayState.AWAY, whoisAway.message())));
    }
    PircbotxWhoisParsers.ParsedWhoisAccount whoisAcct =
        PircbotxWhoisParsers.parseRpl330WhoisAccount(rawLine);
    if (whoisAcct != null && whoisAcct.nick() != null && !whoisAcct.nick().isBlank()) {
      String nk = whoisAcct.nick().trim();
      String k = nk.toLowerCase(Locale.ROOT);
      conn.whoisSawAccountByNickLower.computeIfPresent(k, (kk, vv) -> Boolean.TRUE);
      conn.whoisAccountNumericSupported.set(true);
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserAccountStateObserved(
                  Instant.now(), nk, IrcEvent.AccountState.LOGGED_IN, whoisAcct.account())));
    }

    String endWhoisNick = PircbotxWhoisParsers.parseRpl318EndOfWhoisNick(rawLine);
    if (endWhoisNick != null && !endWhoisNick.isBlank()) {
      String nk = endWhoisNick.trim();
      Boolean sawAway = conn.whoisSawAwayByNickLower.remove(nk.toLowerCase(Locale.ROOT));
      if (sawAway != null && !sawAway.booleanValue()) {
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.UserAwayStateObserved(Instant.now(), nk, IrcEvent.AwayState.HERE)));
      }

      Boolean sawAcct = conn.whoisSawAccountByNickLower.remove(nk.toLowerCase(Locale.ROOT));
      if (sawAcct != null && !sawAcct.booleanValue() && conn.whoisAccountNumericSupported.get()) {
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.UserAccountStateObserved(
                    Instant.now(), nk, IrcEvent.AccountState.LOGGED_OUT)));
      }
      if (sawAway != null || sawAcct != null) {
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.WhoisProbeCompleted(
                    Instant.now(),
                    nk,
                    sawAway != null && sawAway.booleanValue(),
                    sawAcct != null && sawAcct.booleanValue(),
                    conn.whoisAccountNumericSupported.get())));
      }
    }

    PircbotxWhoisParsers.ParsedWhoisUser whoisUser =
        PircbotxWhoisParsers.parseRpl311WhoisUser(rawLine);
    if (whoisUser == null) whoisUser = PircbotxWhoisParsers.parseRpl314WhowasUser(rawLine);
    if (whoisUser != null
        && !whoisUser.nick().isBlank()
        && !whoisUser.user().isBlank()
        && !whoisUser.host().isBlank()) {
      String hm = whoisUser.nick() + "!" + whoisUser.user() + "@" + whoisUser.host();
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserHostmaskObserved(Instant.now(), "", whoisUser.nick(), hm)));
    }
    PircbotxWhoUserhostParsers.ParsedWhoReply whoReply =
        PircbotxWhoUserhostParsers.parseRpl352WhoReply(rawLine);
    if (whoReply != null
        && !whoReply.channel().isBlank()
        && !whoReply.nick().isBlank()
        && !whoReply.user().isBlank()
        && !whoReply.host().isBlank()) {
      Instant now = Instant.now();
      String hm = whoReply.nick() + "!" + whoReply.user() + "@" + whoReply.host();
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserHostmaskObserved(now, whoReply.channel(), whoReply.nick(), hm)));
      String flags = whoReply.flags();
      if (flags != null) {
        IrcEvent.AwayState as =
            flags.indexOf('G') >= 0
                ? IrcEvent.AwayState.AWAY
                : (flags.indexOf('H') >= 0 ? IrcEvent.AwayState.HERE : IrcEvent.AwayState.UNKNOWN);
        if (as == IrcEvent.AwayState.AWAY || as == IrcEvent.AwayState.HERE) {
          bus.onNext(
              new ServerIrcEvent(
                  serverId, new IrcEvent.UserAwayStateObserved(now, whoReply.nick(), as)));
        }
      }
    }
    PircbotxWhoUserhostParsers.ParsedWhoxTcuhnaf whoxStrict =
        PircbotxWhoUserhostParsers.parseRpl354WhoxTcuhnaf(rawLine, IRCafe_WHOX_TOKEN);
    if (whoxStrict != null) {
      if (conn.whoxSchemaCompatibleEmitted.compareAndSet(false, true)) {
        conn.whoxSchemaCompatible.set(true);
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.WhoxSchemaCompatibleObserved(Instant.now(), true, "strict-parse-ok")));
      }
      Instant now = Instant.now();
      String hm = whoxStrict.nick() + "!" + whoxStrict.user() + "@" + whoxStrict.host();
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserHostmaskObserved(now, whoxStrict.channel(), whoxStrict.nick(), hm)));

      String flags = whoxStrict.flags();
      if (flags != null) {
        IrcEvent.AwayState as =
            flags.indexOf('G') >= 0
                ? IrcEvent.AwayState.AWAY
                : (flags.indexOf('H') >= 0 ? IrcEvent.AwayState.HERE : IrcEvent.AwayState.UNKNOWN);
        if (as == IrcEvent.AwayState.AWAY || as == IrcEvent.AwayState.HERE) {
          bus.onNext(
              new ServerIrcEvent(
                  serverId, new IrcEvent.UserAwayStateObserved(now, whoxStrict.nick(), as)));
        }
      }

      String acct = whoxStrict.account();
      IrcEvent.AccountState ast =
          acct == null
              ? IrcEvent.AccountState.UNKNOWN
              : (("*".equals(acct) || "0".equals(acct))
                  ? IrcEvent.AccountState.LOGGED_OUT
                  : IrcEvent.AccountState.LOGGED_IN);
      if (ast != IrcEvent.AccountState.UNKNOWN) {
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.UserAccountStateObserved(now, whoxStrict.nick(), ast, acct)));
      }
    } else {
      if (PircbotxWhoUserhostParsers.seemsRpl354WhoxWithToken(rawLine, IRCafe_WHOX_TOKEN)
          && conn.whoxSchemaIncompatibleEmitted.compareAndSet(false, true)) {
        conn.whoxSchemaCompatible.set(false);
        log.debug(
            "[{}] WHOX schema mismatch: strict parse failed for token {}: {}",
            serverId,
            IRCafe_WHOX_TOKEN,
            rawLine);
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.WhoxSchemaCompatibleObserved(
                    Instant.now(), false, "strict-parse-failed")));
      }
      PircbotxWhoUserhostParsers.ParsedWhoxReply whox =
          PircbotxWhoUserhostParsers.parseRpl354WhoxReply(rawLine);
      if (whox != null
          && !whox.nick().isBlank()
          && !whox.user().isBlank()
          && !whox.host().isBlank()) {
        String hm = whox.nick() + "!" + whox.user() + "@" + whox.host();
        String ch = (whox.channel() == null) ? "" : whox.channel();
        bus.onNext(
            new ServerIrcEvent(
                serverId, new IrcEvent.UserHostmaskObserved(Instant.now(), ch, whox.nick(), hm)));
      }
    }
  }

  private void maybeLogNegotiatedCaps() {
    if (conn.capSummaryLogged.getAndSet(true)) return;
    boolean ch = conn.chatHistoryCapAcked.get();
    boolean batch = conn.batchCapAcked.get();
    boolean znc = conn.zncPlaybackCapAcked.get();
    boolean st = conn.serverTimeCapAcked.get();
    boolean standardReplies = conn.standardRepliesCapAcked.get();
    boolean echo = conn.echoMessageCapAcked.get();
    boolean capNotify = conn.capNotifyCapAcked.get();
    boolean labeled = conn.labeledResponseCapAcked.get();
    boolean setname = conn.setnameCapAcked.get();
    boolean chghost = conn.chghostCapAcked.get();
    boolean sts = conn.stsCapAcked.get();
    boolean multiline = conn.multilineCapAcked.get() || conn.draftMultilineCapAcked.get();
    boolean multilineFinal = conn.multilineCapAcked.get();
    boolean multilineDraft = conn.draftMultilineCapAcked.get();
    long multilineFinalMaxBytes = conn.multilineMaxBytes.get();
    long multilineFinalMaxLines = conn.multilineMaxLines.get();
    long multilineDraftMaxBytes = conn.draftMultilineMaxBytes.get();
    long multilineDraftMaxLines = conn.draftMultilineMaxLines.get();
    boolean reply = conn.draftReplyCapAcked.get();
    boolean react = conn.draftReactCapAcked.get();
    boolean edit = conn.draftMessageEditCapAcked.get();
    boolean redaction = conn.draftMessageRedactionCapAcked.get();
    boolean messageTags = conn.messageTagsCapAcked.get();
    boolean typingCap = conn.typingCapAcked.get();
    boolean typingTagPolicyKnown = conn.typingClientTagPolicyKnown.get();
    boolean typingTagAllowed = conn.typingClientTagAllowed.get();
    boolean typingAllowedByPolicy = typingTagPolicyKnown && typingTagAllowed;
    boolean typing = messageTags && (typingCap || typingAllowedByPolicy);
    boolean readMarker = conn.readMarkerCapAcked.get();
    boolean monitorCap = conn.monitorCapAcked.get();
    boolean extendedMonitorCap = conn.extendedMonitorCapAcked.get();
    boolean monitorSupported = conn.monitorSupported.get();
    long monitorMaxTargets = conn.monitorMaxTargets.get();
    log.info(
        "[{}] negotiated caps: server-time={} standard-replies={} echo-message={} cap-notify={} labeled-response={} "
            + "setname={} chghost={} sts={} multiline={} multiline(final)={} multiline(final,max-bytes)={} "
            + "multiline(final,max-lines)={} multiline(draft)={} multiline(draft,max-bytes)={} "
            + "multiline(draft,max-lines)={} "
            + "draft/reply={} draft/react={} draft/message-edit={} draft/message-redaction={} "
            + "message-tags={} typing-policy-known={} typing-allowed={} typing-available={} typing(cap)={} read-marker={} "
            + "monitor(isupport)={} monitor(cap)={} extended-monitor(cap)={} monitor(max-targets)={} "
            + "chathistory={} batch={} znc.in/playback={}",
        serverId,
        st,
        standardReplies,
        echo,
        capNotify,
        labeled,
        setname,
        chghost,
        sts,
        multiline,
        multilineFinal,
        multilineFinalMaxBytes,
        multilineFinalMaxLines,
        multilineDraft,
        multilineDraftMaxBytes,
        multilineDraftMaxLines,
        reply,
        react,
        edit,
        redaction,
        messageTags,
        typingTagPolicyKnown,
        typingTagAllowed,
        typing,
        typingCap,
        readMarker,
        monitorSupported,
        monitorCap,
        extendedMonitorCap,
        monitorMaxTargets,
        ch,
        batch,
        znc);

    if (!st && conn.serverTimeMissingWarned.compareAndSet(false, true)) {
      String msg =
          "IRCv3 server-time was not negotiated; message ordering/timestamps may be less accurate (especially on reconnect/backlog).";
      log.warn("[{}] {}", serverId, msg);
      bus.onNext(
          new ServerIrcEvent(serverId, new IrcEvent.ServerTimeNotNegotiated(Instant.now(), msg)));
    }

    if (!typing && conn.typingMissingWarned.compareAndSet(false, true)) {
      String reason;
      if (!messageTags) {
        reason = "message-tags not negotiated";
      } else if (!typingCap && !typingTagPolicyKnown) {
        reason = "typing capability not negotiated";
      } else if (!typingCap && !typingTagAllowed) {
        reason = "server denies +typing via CLIENTTAGDENY";
      } else {
        reason = "unknown";
      }
      log.warn("[{}] IRCv3 typing indicators are unavailable ({})", serverId, reason);
    }
  }

  private void maybeApplyMonitorIsupport(String rawLine) {
    PircbotxMonitorParsers.ParsedMonitorSupport monitor =
        PircbotxMonitorParsers.parseRpl005MonitorSupport(rawLine);
    if (monitor == null) return;

    boolean prevSupported = conn.monitorSupported.getAndSet(monitor.supported());
    long prevLimit = conn.monitorMaxTargets.getAndSet(Math.max(0L, monitor.limit()));
    if (prevSupported != monitor.supported() || prevLimit != Math.max(0L, monitor.limit())) {
      log.info(
          "[{}] monitor support changed: supported={} max-targets={}",
          serverId,
          monitor.supported(),
          Math.max(0, monitor.limit()));
    }
  }

  private boolean maybeEmitMonitorNumeric(String rawLine, String originalLine) {
    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) return false;
    Instant at = PircbotxIrcv3ServerTime.parseServerTimeFromRawLine(originalLine);
    if (at == null) at = Instant.now();

    List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> onlineEntries =
        PircbotxMonitorParsers.parseRpl730MonitorOnlineEntries(raw);
    List<String> online = monitorNickList(onlineEntries);
    if (!online.isEmpty()) {
      emitMonitorHostmaskObservations(at, onlineEntries);
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.MonitorOnlineObserved(at, online)));
      return true;
    }

    List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> offlineEntries =
        PircbotxMonitorParsers.parseRpl731MonitorOfflineEntries(raw);
    List<String> offline = monitorNickList(offlineEntries);
    if (!offline.isEmpty()) {
      emitMonitorHostmaskObservations(at, offlineEntries);
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.MonitorOfflineObserved(at, offline)));
      return true;
    }

    List<String> list = PircbotxMonitorParsers.parseRpl732MonitorListNicks(raw);
    if (!list.isEmpty()) {
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.MonitorListObserved(at, list)));
      return true;
    }

    if (PircbotxMonitorParsers.isRpl733MonitorListEnd(raw)) {
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.MonitorListEnded(at)));
      return true;
    }

    PircbotxMonitorParsers.ParsedMonitorListFull full =
        PircbotxMonitorParsers.parseErr734MonitorListFull(raw);
    if (full != null) {
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.MonitorListFull(at, full.limit(), full.nicks(), full.message())));
      return true;
    }
    return false;
  }

  private List<String> monitorNickList(
      List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> entries) {
    if (entries == null || entries.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>(entries.size());
    for (PircbotxMonitorParsers.ParsedMonitorStatusEntry entry : entries) {
      if (entry == null) continue;
      String nick = Objects.toString(entry.nick(), "").trim();
      if (!nick.isEmpty()) out.add(nick);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private void emitMonitorHostmaskObservations(
      Instant at, List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> entries) {
    if (entries == null || entries.isEmpty()) return;
    for (PircbotxMonitorParsers.ParsedMonitorStatusEntry entry : entries) {
      if (entry == null) continue;
      String nick = Objects.toString(entry.nick(), "").trim();
      String hostmask = Objects.toString(entry.hostmask(), "").trim();
      if (nick.isEmpty() || !PircbotxUtil.isUsefulHostmask(hostmask)) continue;
      bus.onNext(
          new ServerIrcEvent(serverId, new IrcEvent.UserHostmaskObserved(at, "", nick, hostmask)));
    }
  }

  private void maybeRequestZncPlayback(PircBotX bot) {
    if (bot == null) return;
    if (!conn.zncPlaybackCapAcked.get()) return;
    if (conn.zncPlaybackRequestedThisSession.getAndSet(true)) return;

    long cursor = playbackCursorProvider.lastSeenEpochSeconds(serverId).orElse(0L);
    long request = Math.max(0L, cursor - 1L);

    try {
      bot.sendIRC().message("*playback", "play * " + request);
      log.info("[{}] requested ZNC playback since {} (epoch seconds)", serverId, request);
    } catch (Exception ex) {
      conn.zncPlaybackRequestedThisSession.set(false);
      log.warn("[{}] failed to request ZNC playback", serverId, ex);
    }
  }

  private void maybeRequestZncNetworks(PircBotX bot) {
    if (bot == null) return;
    if (!zncDiscoveryEnabled) return;
    if (!conn.zncDetected.get()) return;

    String net = conn.zncNetwork.get();
    if (net != null && !net.isBlank()) return; // Not the control session.

    if (conn.zncListNetworksRequestedThisSession.getAndSet(true)) return;

    try {
      // Begin capture so we can suppress the noisy *status table output from the main UI.
      conn.zncListNetworksCaptureActive.set(true);
      conn.zncListNetworksCaptureStartedMs.set(System.currentTimeMillis());
      conn.zncNetworksByNameLower.clear();

      bot.sendIRC().message("*status", "ListNetworks");
      log.info("[{}] znc: requested network list (*status ListNetworks)", serverId);
    } catch (Exception ex) {
      conn.zncListNetworksCaptureActive.set(false);
      conn.zncListNetworksRequestedThisSession.set(false);
      log.warn("[{}] znc: failed to request network list", serverId, ex);
    }
  }

  private void maybeRequestSojuNetworks(PircBotX bot) {
    if (bot == null) return;
    if (!sojuDiscoveryEnabled) return;
    if (!conn.sojuBouncerNetworksCapAcked.get()) return;

    String netId = conn.sojuBouncerNetId.get();
    if (netId != null && !netId.isBlank()) return; // Not the bouncer-control session.

    if (conn.sojuListNetworksRequestedThisSession.getAndSet(true)) return;

    try {
      bot.sendRaw().rawLine("BOUNCER LISTNETWORKS");
      log.info("[{}] soju: requested bouncer network list (BOUNCER LISTNETWORKS)", serverId);
    } catch (Exception ex) {
      conn.sojuListNetworksRequestedThisSession.set(false);
      log.warn("[{}] soju: failed to request bouncer network list", serverId, ex);
    }
  }

  private void maybeMarkZncDetected(String via, String detail) {
    if (!conn.zncDetected.compareAndSet(false, true)) return;
    String extra = (detail == null) ? "" : detail;
    log.info("[{}] znc: detected via {} {}", serverId, via, extra);

    // Log a little more context once per connection.
    if (conn.zncDetectedLogged.compareAndSet(false, true)) {
      String baseUser = conn.zncBaseUser.get();
      String client = conn.zncClientId.get();
      String net = conn.zncNetwork.get();
      boolean hasNet = net != null && !net.isBlank();
      log.info(
          "[{}] znc: login parsed baseUser='{}' clientId='{}' network='{}' (controlSession={})",
          serverId,
          baseUser == null ? "" : baseUser,
          client == null ? "" : client,
          net == null ? "" : net,
          !hasNet);
    }
  }

  @Override
  public void onServerResponse(ServerResponseEvent event) {
    touchInbound();

    int code;
    try {
      code = event.getCode();
    } catch (Exception ex) {
      return;
    }
    try {
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) {
        String raw = PircbotxLineParseUtil.normalizeIrcLineForParsing(String.valueOf(l));
        ParsedIrcLine pl = parseIrcLine(raw);
        if (pl != null && pl.params() != null && !pl.params().isEmpty()) {
          // Server numerics are generally addressed to us in param[0].
          rememberSelfNickHint(pl.params().get(0));
        }
      }
    } catch (Exception ignored) {
    }
    if (code == ERR_SASL_FAIL
        || code == ERR_SASL_TOO_LONG
        || code == ERR_SASL_ABORTED
        || code == ERR_SASL_ALREADY) {
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);
      handleSaslNumeric(code, line);
      return;
    }
    if (isJoinFailureNumeric(code)) {
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);

      ParsedJoinFailure pj = parseJoinFailure(line);
      if (pj != null) {
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.JoinFailed(Instant.now(), pj.channel(), code, pj.message())));
      }
      return;
    }

    if (code == RPL_MONONLINE
        || code == RPL_MONOFFLINE
        || code == RPL_MONLIST
        || code == RPL_ENDOFMONLIST
        || code == ERR_MONLISTFULL) {
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);
      String rawLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);
      if (maybeEmitMonitorNumeric(rawLine, line)) {
        return;
      }
    }

    if (code == 4) {
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);

      String rawLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);
      if (PircbotxZncParsers.seemsRpl004Znc(rawLine)) {
        maybeMarkZncDetected("RPL_MYINFO/004", "(" + rawLine + ")");
      }
      // Show the server line in Status so users can see server replies without watching the
      // console.
      emitServerResponseLine(event.getBot(), code, line);
      return;
    }
    if (code == 376 || code == 422) {
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);

      emitServerResponseLine(event.getBot(), code, line);
      maybeLogNegotiatedCaps();
      maybeRequestZncNetworks(event.getBot());
      maybeRequestZncPlayback(event.getBot());
      maybeRequestSojuNetworks(event.getBot());
      return;
    }
    if (code == 5) {
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);

      String rawLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);

      String netId = PircbotxSojuParsers.parseRpl005BouncerNetId(rawLine);
      if (netId != null && !netId.isBlank()) {
        String prev = conn.sojuBouncerNetId.get();
        if (prev == null || prev.isBlank()) {
          conn.sojuBouncerNetId.set(netId);
          log.info(
              "[{}] soju: BOUNCER_NETID={} (connection is bound to a bouncer network)",
              serverId,
              netId);
        } else if (!java.util.Objects.equals(prev, netId)) {
          conn.sojuBouncerNetId.set(netId);
          log.info("[{}] soju: BOUNCER_NETID changed {} -> {}", serverId, prev, netId);
        }
      }

      if (PircbotxWhoUserhostParsers.parseRpl005IsupportHasWhox(rawLine)) {
        bus.onNext(
            new ServerIrcEvent(serverId, new IrcEvent.WhoxSupportObserved(Instant.now(), true)));
      }
      maybeApplyMonitorIsupport(rawLine);

      // IRCv3 client-only tags can be denied/allowed via RPL_ISUPPORT CLIENTTAGDENY.
      // Typing indicators use the +typing client tag (delivered via TAGMSG), so we treat
      // this as part of typing availability.
      String clientTagDeny = PircbotxClientTagParsers.parseRpl005ClientTagDenyValue(rawLine);
      if (clientTagDeny != null) {
        boolean allowed = PircbotxClientTagParsers.isClientOnlyTagAllowed(clientTagDeny, "typing");
        conn.typingClientTagPolicyKnown.set(true);
        boolean prev = conn.typingClientTagAllowed.getAndSet(allowed);
        if (prev != allowed) {
          log.info(
              "[{}] CLIENTTAGDENY -> typing allowed={} (raw={})", serverId, allowed, clientTagDeny);
        }
      }

      emitServerResponseLine(event.getBot(), code, line);
      return;
    }

    if (code == 324) {
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);

      PircbotxChannelModeParsers.ParsedRpl324 parsed = PircbotxChannelModeParsers.parseRpl324(line);
      if (parsed != null) {
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.ChannelModesListed(
                    Instant.now(), parsed.channel(), parsed.details())));
      } else {
        // Defensive fallback: if parsing fails, still surface the raw numeric.
        emitServerResponseLine(event.getBot(), code, line);
      }
      return;
    }

    if (code == 302 || code == 352 || code == 354) {
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);

      Instant now = Instant.now();
      if (code == 302) {
        java.util.List<PircbotxWhoUserhostParsers.UserhostEntry> uh =
            PircbotxWhoUserhostParsers.parseRpl302Userhost(line);
        if (uh != null && !uh.isEmpty()) {
          for (PircbotxWhoUserhostParsers.UserhostEntry e : uh) {
            if (e == null) continue;
            bus.onNext(
                new ServerIrcEvent(
                    serverId, new IrcEvent.UserHostmaskObserved(now, "", e.nick(), e.hostmask())));
            IrcEvent.AwayState as =
                (e.awayState() == null) ? IrcEvent.AwayState.UNKNOWN : e.awayState();
            if (as == IrcEvent.AwayState.AWAY || as == IrcEvent.AwayState.HERE) {
              bus.onNext(
                  new ServerIrcEvent(
                      serverId, new IrcEvent.UserAwayStateObserved(now, e.nick(), as)));
            }
          }
        }
      } else if (code == 352) {
        PircbotxWhoUserhostParsers.ParsedWhoReply whoReply =
            PircbotxWhoUserhostParsers.parseRpl352WhoReply(line);
        if (whoReply != null
            && !whoReply.channel().isBlank()
            && !whoReply.nick().isBlank()
            && !whoReply.user().isBlank()
            && !whoReply.host().isBlank()) {
          String hm = whoReply.nick() + "!" + whoReply.user() + "@" + whoReply.host();
          bus.onNext(
              new ServerIrcEvent(
                  serverId,
                  new IrcEvent.UserHostmaskObserved(now, whoReply.channel(), whoReply.nick(), hm)));

          String flags = whoReply.flags();
          if (flags != null) {
            IrcEvent.AwayState as =
                flags.indexOf('G') >= 0
                    ? IrcEvent.AwayState.AWAY
                    : (flags.indexOf('H') >= 0
                        ? IrcEvent.AwayState.HERE
                        : IrcEvent.AwayState.UNKNOWN);
            if (as == IrcEvent.AwayState.AWAY || as == IrcEvent.AwayState.HERE) {
              bus.onNext(
                  new ServerIrcEvent(
                      serverId, new IrcEvent.UserAwayStateObserved(now, whoReply.nick(), as)));
            }
          }
        }
      } else {
        PircbotxWhoUserhostParsers.ParsedWhoxTcuhnaf whoxStrict =
            PircbotxWhoUserhostParsers.parseRpl354WhoxTcuhnaf(line, IRCafe_WHOX_TOKEN);
        if (whoxStrict != null) {
          if (conn.whoxSchemaCompatibleEmitted.compareAndSet(false, true)) {
            conn.whoxSchemaCompatible.set(true);
            bus.onNext(
                new ServerIrcEvent(
                    serverId,
                    new IrcEvent.WhoxSchemaCompatibleObserved(now, true, "strict-parse-ok")));
          }
          String hm = whoxStrict.nick() + "!" + whoxStrict.user() + "@" + whoxStrict.host();
          bus.onNext(
              new ServerIrcEvent(
                  serverId,
                  new IrcEvent.UserHostmaskObserved(
                      now, whoxStrict.channel(), whoxStrict.nick(), hm)));

          String flags = whoxStrict.flags();
          if (flags != null) {
            IrcEvent.AwayState as =
                flags.indexOf('G') >= 0
                    ? IrcEvent.AwayState.AWAY
                    : (flags.indexOf('H') >= 0
                        ? IrcEvent.AwayState.HERE
                        : IrcEvent.AwayState.UNKNOWN);
            if (as == IrcEvent.AwayState.AWAY || as == IrcEvent.AwayState.HERE) {
              bus.onNext(
                  new ServerIrcEvent(
                      serverId, new IrcEvent.UserAwayStateObserved(now, whoxStrict.nick(), as)));
            }
          }

          String acct = whoxStrict.account();
          IrcEvent.AccountState ast =
              acct == null
                  ? IrcEvent.AccountState.UNKNOWN
                  : (("*".equals(acct) || "0".equals(acct))
                      ? IrcEvent.AccountState.LOGGED_OUT
                      : IrcEvent.AccountState.LOGGED_IN);
          if (ast != IrcEvent.AccountState.UNKNOWN) {
            bus.onNext(
                new ServerIrcEvent(
                    serverId,
                    new IrcEvent.UserAccountStateObserved(now, whoxStrict.nick(), ast, acct)));
          }
        } else {
          if (PircbotxWhoUserhostParsers.seemsRpl354WhoxWithToken(line, IRCafe_WHOX_TOKEN)
              && conn.whoxSchemaIncompatibleEmitted.compareAndSet(false, true)) {
            conn.whoxSchemaCompatible.set(false);
            log.debug(
                "[{}] WHOX schema mismatch: strict parse failed for token {}: {}",
                serverId,
                IRCafe_WHOX_TOKEN,
                line);
            bus.onNext(
                new ServerIrcEvent(
                    serverId,
                    new IrcEvent.WhoxSchemaCompatibleObserved(now, false, "strict-parse-failed")));
          }
          PircbotxWhoUserhostParsers.ParsedWhoxReply whox =
              PircbotxWhoUserhostParsers.parseRpl354WhoxReply(line);
          if (whox != null
              && !whox.nick().isBlank()
              && !whox.user().isBlank()
              && !whox.host().isBlank()) {
            String hm = whox.nick() + "!" + whox.user() + "@" + whox.host();
            String ch = (whox.channel() == null) ? "" : whox.channel();
            bus.onNext(
                new ServerIrcEvent(
                    serverId, new IrcEvent.UserHostmaskObserved(now, ch, whox.nick(), hm)));
          }
        }
      }
      return;
    }
    if (code == 330) {

      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);

      PircbotxWhoisParsers.ParsedWhoisAccount whoisAcct =
          PircbotxWhoisParsers.parseRpl330WhoisAccount(line);
      if (whoisAcct != null && whoisAcct.nick() != null && !whoisAcct.nick().isBlank()) {
        String nk = whoisAcct.nick().trim();
        String k = nk.toLowerCase(Locale.ROOT);
        conn.whoisSawAccountByNickLower.computeIfPresent(k, (kk, vv) -> Boolean.TRUE);
        conn.whoisAccountNumericSupported.set(true);
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.UserAccountStateObserved(
                    Instant.now(), nk, IrcEvent.AccountState.LOGGED_IN, whoisAcct.account())));
      }
      return;
    }
    if (code == 301 || code == 318) {

      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);

      if (code == 301) {
        PircbotxWhoisParsers.ParsedWhoisAway whoisAway =
            PircbotxWhoisParsers.parseRpl301WhoisAway(line);
        if (whoisAway != null && whoisAway.nick() != null && !whoisAway.nick().isBlank()) {
          String nk = whoisAway.nick().trim();
          String k = nk.toLowerCase(Locale.ROOT);
          conn.whoisSawAwayByNickLower.computeIfPresent(k, (kk, vv) -> Boolean.TRUE);
          bus.onNext(
              new ServerIrcEvent(
                  serverId,
                  new IrcEvent.UserAwayStateObserved(
                      Instant.now(), nk, IrcEvent.AwayState.AWAY, whoisAway.message())));
        }
      } else {
        String nk = PircbotxWhoisParsers.parseRpl318EndOfWhoisNick(line);
        if (nk != null && !nk.isBlank()) {
          nk = nk.trim();
          String k = nk.toLowerCase(Locale.ROOT);

          Boolean sawAway = conn.whoisSawAwayByNickLower.remove(k);
          if (sawAway != null && !sawAway.booleanValue()) {
            bus.onNext(
                new ServerIrcEvent(
                    serverId,
                    new IrcEvent.UserAwayStateObserved(
                        Instant.now(), nk, IrcEvent.AwayState.HERE)));
          }

          Boolean sawAcct = conn.whoisSawAccountByNickLower.remove(k);
          if (sawAcct != null
              && !sawAcct.booleanValue()
              && conn.whoisAccountNumericSupported.get()) {
            bus.onNext(
                new ServerIrcEvent(
                    serverId,
                    new IrcEvent.UserAccountStateObserved(
                        Instant.now(), nk, IrcEvent.AccountState.LOGGED_OUT)));
          }
          if (sawAway != null || sawAcct != null) {
            bus.onNext(
                new ServerIrcEvent(
                    serverId,
                    new IrcEvent.WhoisProbeCompleted(
                        Instant.now(),
                        nk,
                        sawAway != null && sawAway.booleanValue(),
                        sawAcct != null && sawAcct.booleanValue(),
                        conn.whoisAccountNumericSupported.get())));
          }
        }
      }
      return;
    }

    // For most numerics we don't have a dedicated high-level event.
    // Surface the raw-ish server response to the Status transcript.
    if (code != 305 && code != 306) {
      String line = null;
      Object l = reflectCall(event, "getLine");
      if (l == null) l = reflectCall(event, "getRawLine");
      if (l != null) line = String.valueOf(l);
      if (line == null || line.isBlank()) line = String.valueOf(event);
      emitServerResponseLine(event.getBot(), code, line);
      return;
    }

    String line = null;
    Object l = reflectCall(event, "getLine");
    if (l == null) l = reflectCall(event, "getRawLine");
    if (l != null) line = String.valueOf(l);
    if (line == null || line.isBlank()) line = String.valueOf(event);

    PircbotxAwayParsers.ParsedAwayConfirmation away =
        PircbotxAwayParsers.parseRpl305or306Away(line);
    boolean isAway = (code == 306);
    String msg = null;
    if (away != null) {
      isAway = away.away();
      msg = away.message();
    }
    if (msg == null || msg.isBlank()) {
      msg =
          isAway ? "You have been marked as being away" : "You are no longer marked as being away";
    }

    bus.onNext(
        new ServerIrcEvent(serverId, new IrcEvent.AwayStatusChanged(Instant.now(), isAway, msg)));
  }

  private void emitServerResponseLine(PircBotX bot, int code, String line) {
    try {
      if (line == null || line.isBlank()) return;
      String originalLine = line.trim();
      Map<String, String> ircv3Tags = PircbotxIrcv3Tags.fromRawLine(originalLine);
      String messageId = PircbotxIrcv3Tags.firstTagValue(ircv3Tags, "msgid", "draft/msgid");
      String normalizedLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);
      ParsedIrcLine pl = parseIrcLine(normalizedLine);

      Instant at = PircbotxIrcv3ServerTime.parseServerTimeFromRawLine(line);
      if (at == null) at = Instant.now();

      String myNick = null;
      try {
        myNick = (bot == null) ? null : bot.getNick();
      } catch (Exception ignored) {
      }

      emitChannelListEvent(at, pl, myNick);

      String message = renderServerResponseMessage(pl, myNick);
      if (message == null || message.isBlank()) {
        message = normalizedLine;
      }
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ServerResponseLine(
                  at, code, message, originalLine, messageId, ircv3Tags)));
    } catch (Exception ignored) {
    }
  }

  private void emitChannelListEvent(Instant at, ParsedIrcLine pl, String myNick) {
    if (pl == null) return;

    String banner = PircbotxListParsers.parseListStartBanner(pl.command(), pl.trailing());
    if (banner != null) {
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.ChannelListStarted(at, banner)));
      return;
    }

    PircbotxListParsers.ListEntry entry =
        PircbotxListParsers.parseListEntry(pl.command(), pl.params(), pl.trailing(), myNick);
    if (entry != null) {
      bus.onNext(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ChannelListEntry(
                  at, entry.channel(), Math.max(0, entry.visibleUsers()), entry.topic())));
      return;
    }

    String summary = PircbotxListParsers.parseListEndSummary(pl.command(), pl.trailing());
    if (summary != null) {
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.ChannelListEnded(at, summary)));
    }
  }

  private String renderServerResponseMessage(ParsedIrcLine pl, String myNick) {
    if (pl == null) return null;
    String listRendered =
        PircbotxListParsers.tryFormatListNumeric(pl.command(), pl.params(), pl.trailing(), myNick);
    if (listRendered != null && !listRendered.isBlank()) return listRendered;

    String trailing = pl.trailing();
    java.util.List<String> params = pl.params();
    String msg = (trailing == null) ? "" : trailing;

    int idx = 0;
    if (params != null && !params.isEmpty()) {
      if (myNick != null
          && !myNick.isBlank()
          && params.get(0) != null
          && params.get(0).equalsIgnoreCase(myNick)) {
        idx = 1;
      } else if ("*".equals(params.get(0))) {
        idx = 1;
      }
    }

    String subject = "";
    if (params != null && idx < params.size()) {
      subject = params.get(idx);
      if (subject == null) subject = "";
    }

    if (msg.isBlank() && params != null && idx < params.size()) {
      msg = String.join(" ", params.subList(idx, params.size()));
    }
    msg = msg == null ? "" : msg.trim();
    subject = subject == null ? "" : subject.trim();

    if (!subject.isBlank() && !msg.isBlank() && !msg.contains(subject)) {
      // Helpful hint for lines like ERR_UNKNOWNCOMMAND: "Unknown command" + subject "test".
      return msg + " (" + subject + ")";
    }
    return msg.isBlank() ? subject : msg;
  }

  @Override
  public void onJoin(JoinEvent event) {
    touchInbound();
    Channel channel = event.getChannel();
    if (channel != null) maybeEmitHostmaskObserved(channel.getName(), event.getUser());

    String nick = event.getUser() == null ? null : event.getUser().getNick();
    boolean selfJoin = isSelf(event.getBot(), nick);
    if (log.isDebugEnabled()) {
      String hint = Objects.toString(conn.selfNickHint.get(), "");
      String botNick = resolveBotNick(event.getBot());
      String channelName = (channel == null) ? "" : channel.getName();
      log.debug(
          "[{}] JOIN route chan={} nick={} selfJoin={} selfHint={} botNick={}",
          serverId,
          channelName,
          nick,
          selfJoin,
          hint,
          botNick);
    }

    if (selfJoin) {
      bus.onNext(
          new ServerIrcEvent(
              serverId, new IrcEvent.JoinedChannel(Instant.now(), channel.getName())));
    } else {
      bus.onNext(
          new ServerIrcEvent(
              serverId, new IrcEvent.UserJoinedChannel(Instant.now(), channel.getName(), nick)));
    }

    emitRoster(channel);
  }

  @Override
  public void onPart(PartEvent event) {
    touchInbound();
    boolean selfPart = false;
    try {
      maybeEmitHostmaskObserved(event.getChannelName(), event.getUser());
    } catch (Exception ignored) {
    }
    try {
      String nick = event.getUser() == null ? null : event.getUser().getNick();
      if (isSelf(event.getBot(), nick)) {
        selfPart = true;
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.LeftChannel(
                    Instant.now(), event.getChannelName(), event.getReason())));
      } else {
        bus.onNext(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.UserPartedChannel(
                    Instant.now(), event.getChannelName(), nick, event.getReason())));
      }
    } catch (Exception ignored) {
    }
    if (!selfPart) {
      refreshRosterByName(event.getBot(), event.getChannelName());
    }
  }

  @Override
  public void onQuit(QuitEvent event) {
    touchInbound();
    PircBotX bot = event.getBot();

    try {
      maybeEmitHostmaskObserved("", event.getUser());
    } catch (Exception ignored) {
    }

    String nick = null;
    try {
      nick = event.getUser() == null ? null : event.getUser().getNick();
    } catch (Exception ignored) {
    }
    String reason = null;
    try {
      reason = event.getReason();
    } catch (Exception ignored) {
    }

    boolean refreshedSome = false;
    try {
      UserChannelDaoSnapshot daoSnap = event.getUserChannelDaoSnapshot();
      UserSnapshot userSnap = event.getUser();

      if (daoSnap != null && userSnap != null) {
        for (ChannelSnapshot cs : daoSnap.getChannels(userSnap)) {
          try {
            bus.onNext(
                new ServerIrcEvent(
                    serverId,
                    new IrcEvent.UserQuitChannel(Instant.now(), cs.getName(), nick, reason)));
          } catch (Exception ignored) {
          }
          refreshRosterByName(bot, cs.getName());
          refreshedSome = true;
        }
      }
    } catch (Exception ignored) {

    }

    if (!refreshedSome) {
      try {
        try {
          if (event.getUser() != null) {
            for (Channel ch : bot.getUserChannelDao().getChannels(event.getUser())) {
              bus.onNext(
                  new ServerIrcEvent(
                      serverId,
                      new IrcEvent.UserQuitChannel(Instant.now(), ch.getName(), nick, reason)));
            }
          }
        } catch (Exception ignored) {
        }

        for (Channel ch : bot.getUserChannelDao().getAllChannels()) {
          emitRoster(ch);
        }
      } catch (Exception ignored) {
      }
    }
  }

  private void touchInbound() {
    conn.lastInboundMs.set(System.currentTimeMillis());
    conn.localTimeoutEmitted.set(false);
  }

  private static Integer parseSaslNumericCode(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return null;
    String s = rawLine.trim();
    String[] parts = s.split("\\s+");
    if (parts.length == 0) return null;

    int codeIdx = parts[0].startsWith(":") ? 1 : 0;
    if (parts.length <= codeIdx) return null;

    String codeStr = parts[codeIdx];
    if (!PircbotxLineParseUtil.looksNumeric(codeStr)) return null;

    int code;
    try {
      code = Integer.parseInt(codeStr);
    } catch (Exception ignored) {
      return null;
    }

    if (code == ERR_SASL_FAIL
        || code == ERR_SASL_TOO_LONG
        || code == ERR_SASL_ABORTED
        || code == ERR_SASL_ALREADY) {
      return code;
    }
    return null;
  }

  private void handleSaslNumeric(int code, String rawLine) {
    String msg = extractTrailingMessage(rawLine);

    String base =
        switch (code) {
          case ERR_SASL_FAIL -> "SASL authentication failed";
          case ERR_SASL_TOO_LONG -> "SASL authentication failed (payload too long)";
          case ERR_SASL_ABORTED -> "SASL authentication aborted";
          case ERR_SASL_ALREADY -> "SASL authentication already completed";
          default -> "SASL authentication failed";
        };

    String detail = base;
    if (msg != null && !msg.isBlank()) {
      String m = msg.trim();
      if (!m.equalsIgnoreCase(base)) {
        detail = base + ": " + m;
      }
    }

    String reason = "Login failed — " + detail;
    String existing = conn.disconnectReasonOverride.get();
    if (existing != null && !existing.isBlank()) {
      conn.suppressAutoReconnectOnce.set(true);
      return;
    }

    conn.disconnectReasonOverride.set(reason);
    conn.suppressAutoReconnectOnce.set(true);
    bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Error(Instant.now(), reason, null)));
    if (disconnectOnSaslFailure) {
      PircBotX bot = conn.botRef.get();
      if (bot != null) {
        try {
          bot.stopBotReconnect();
        } catch (Exception ignored) {
        }
        try {
          bot.sendIRC().quitServer(reason);
        } catch (Exception ignored) {
        }
      }
    }
  }

  private static String extractTrailingMessage(String rawLine) {
    if (rawLine == null) return null;
    String s = PircbotxLineParseUtil.normalizeIrcLineForParsing(rawLine);
    if (s == null) return null;
    int idx = s.indexOf(" :");
    if (idx < 0) return null;
    int start = idx + 2;
    if (start >= s.length()) return null;
    String t = s.substring(start).trim();
    return t.isEmpty() ? null : t;
  }

  private void maybeEmitHostmaskObserved(String channel, User user) {
    if (user == null) return;
    String nick = PircbotxUtil.safeStr(user::getNick, "");
    if (nick == null || nick.isBlank()) return;

    String hm = PircbotxUtil.hostmaskFromUser(user);
    if (!PircbotxUtil.isUsefulHostmask(hm)) return;

    String key = nick.trim().toLowerCase(Locale.ROOT);
    String prev = conn.lastHostmaskByNickLower.put(key, hm);
    if (Objects.equals(prev, hm)) return;

    String ch = (channel == null) ? "" : channel;
    bus.onNext(
        new ServerIrcEvent(
            serverId, new IrcEvent.UserHostmaskObserved(Instant.now(), ch, nick.trim(), hm)));
  }

  @Override
  public void onServerPing(ServerPingEvent event) {
    touchInbound();
  }

  @Override
  public void onKick(KickEvent event) {
    touchInbound();
    if (event == null) return;

    Instant at = Instant.now();
    String channel = "";
    Channel ch = null;
    try {
      ch = event.getChannel();
    } catch (Exception ignored) {
    }
    if (ch != null) {
      channel = Objects.toString(ch.getName(), "").trim();
    } else {
      Object nameObj = reflectCall(event, "getChannelName");
      if (nameObj != null) channel = String.valueOf(nameObj).trim();
    }

    String by = "server";
    try {
      if (event.getUser() != null) {
        maybeEmitHostmaskObserved(channel, event.getUser());
        String nick = event.getUser().getNick();
        if (nick != null && !nick.isBlank()) by = nick.trim();
      }
    } catch (Exception ignored) {
    }

    String kickedNick = "";
    Object rec = reflectCall(event, "getRecipient");
    if (rec != null) {
      Object n = reflectCall(rec, "getNick");
      if (n != null) kickedNick = String.valueOf(n).trim();
    }
    if (kickedNick.isBlank()) {
      Object n = reflectCall(event, "getRecipientNick");
      if (n != null) kickedNick = String.valueOf(n).trim();
    }

    String reason = PircbotxUtil.safeStr(event::getReason, "");
    boolean selfKick = false;
    if (!channel.isBlank() && !kickedNick.isBlank()) {
      if (isSelf(event.getBot(), kickedNick)) {
        selfKick = true;
        bus.onNext(
            new ServerIrcEvent(serverId, new IrcEvent.KickedFromChannel(at, channel, by, reason)));
      } else {
        bus.onNext(
            new ServerIrcEvent(
                serverId, new IrcEvent.UserKickedFromChannel(at, channel, kickedNick, by, reason)));
      }
    }

    if (selfKick) return;

    if (ch != null) {
      emitRoster(ch);
    } else if (!channel.isBlank()) {
      refreshRosterByName(event.getBot(), channel);
    }
  }

  @Override
  public void onNickChange(NickChangeEvent event) {
    touchInbound();
    String oldNick = PircbotxUtil.safeStr(event::getOldNick, "");
    String newNick = PircbotxUtil.safeStr(event::getNewNick, "");
    String hinted = conn.selfNickHint.get();
    if ((!hinted.isBlank() && oldNick.equalsIgnoreCase(hinted))
        || isSelf(event.getBot(), oldNick)
        || isSelf(event.getBot(), newNick)) {
      rememberSelfNickHint(newNick);
    }
    try {
      maybeEmitHostmaskObserved("", event.getUser());
    } catch (Exception ignored) {
    }
    bus.onNext(
        new ServerIrcEvent(serverId, new IrcEvent.NickChanged(Instant.now(), oldNick, newNick)));

    try {
      for (Channel ch : event.getBot().getUserChannelDao().getChannels(event.getUser())) {
        try {
          bus.onNext(
              new ServerIrcEvent(
                  serverId,
                  new IrcEvent.UserNickChangedChannel(
                      Instant.now(), ch.getName(), oldNick, newNick)));
        } catch (Exception ignored) {
        }
        emitRoster(ch);
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  public void onMode(ModeEvent event) {
    touchInbound();
    if (event == null) return;
    if (event.getChannel() == null) return;

    long seq = MODE_SEQ.incrementAndGet();

    emitRoster(event.getChannel());

    String chan = event.getChannel().getName();
    String by = nickFromEvent(event);
    String details = modeDetailsFromEvent(event, chan);

    if (log.isDebugEnabled()) {
      Object rawLine = reflectCall(event, "getRawLine");
      if (rawLine == null) rawLine = reflectCall(event, "getRaw");
      if (rawLine == null) rawLine = reflectCall(event, "getLine");
      Object modeLine = reflectCall(event, "getModeLine");
      if (modeLine == null) modeLine = reflectCall(event, "getModeString");
      Object mode = reflectCall(event, "getMode");

      log.debug(
          "[{}] MODEDBG#{} chan={} by={} eventClass={} rawLine={} modeLine={} mode={} details={}",
          serverId,
          seq,
          chan,
          by,
          event.getClass().getName(),
          clip(rawLine),
          clip(modeLine),
          clip(mode),
          clip(details));
    }

    if (details != null && !details.isBlank()) {
      bus.onNext(
          new ServerIrcEvent(
              serverId, new IrcEvent.ChannelModeChanged(Instant.now(), chan, by, details)));
    }
  }

  @Override
  public void onOp(OpEvent event) {
    emitRoster(event.getChannel());
  }

  @Override
  public void onVoice(VoiceEvent event) {
    emitRoster(event.getChannel());
  }

  @Override
  public void onHalfOp(HalfOpEvent event) {
    emitRoster(event.getChannel());
  }

  @Override
  public void onOwner(OwnerEvent event) {
    emitRoster(event.getChannel());
  }

  @Override
  public void onSuperOp(SuperOpEvent event) {
    emitRoster(event.getChannel());
  }

  private boolean isSelf(PircBotX bot, String nick) {
    return nickMatchesSelf(bot, nick);
  }

  private void refreshRosterByName(PircBotX bot, String channelName) {
    if (channelName == null || channelName.isBlank()) return;
    try {
      if (!bot.getUserChannelDao().containsChannel(channelName)) return;
      Channel ch = bot.getUserChannelDao().getChannel(channelName);
      if (ch != null) emitRoster(ch);
    } catch (Exception ignored) {
    }
  }

  private static java.util.Set<?> setOrEmpty(Channel channel, String method) {
    if (channel == null || method == null) return java.util.Set.of();
    try {
      java.lang.reflect.Method m = channel.getClass().getMethod(method);
      Object v = m.invoke(channel);
      if (v instanceof java.util.Set<?> s) return s;
    } catch (Exception ignored) {
    }
    return java.util.Set.of();
  }

  private static String prefixForUser(
      Object user,
      java.util.Set<?> owners,
      java.util.Set<?> admins,
      java.util.Set<?> ops,
      java.util.Set<?> halfOps,
      java.util.Set<?> voices) {
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

    // Prefer raw MODE line; getMode can reflect current channel modes.
    Object raw = reflectCall(event, "getRawLine");
    if (raw == null) raw = reflectCall(event, "getRaw");
    if (raw == null) raw = reflectCall(event, "getLine");
    if (raw != null) {
      String r = extractModeDetails(String.valueOf(raw), channelName);
      if (r != null && !r.isBlank()) return r;
    }

    // Next-best accessor that includes args.
    Object modeLine = reflectCall(event, "getModeLine");
    if (modeLine == null) modeLine = reflectCall(event, "getModeString");
    if (modeLine != null) {
      String s = String.valueOf(modeLine);
      String reduced = extractModeDetails(s, channelName);
      if (reduced != null && !reduced.isBlank()) return reduced;
      if (s != null && !s.isBlank()) return s.trim();
    }

    // Last resort: getMode() (may be current modes).
    Object mode = reflectCall(event, "getMode");
    if (mode == null) return null;

    String s = String.valueOf(mode);
    if (s == null) return null;

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

    String[] toks = l.split("\\s+");
    for (int i = 0; i < toks.length; i++) {
      if ("MODE".equalsIgnoreCase(toks[i])) {
        int idx = i + 2;
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
    if (channelName != null && !channelName.isBlank()) {
      String ch = channelName.trim();
      if (line.startsWith(ch + " ")) return line.substring(ch.length()).trim();
    }

    return null;
  }

  private static IrcEvent.AwayState awayFromWhoFlags(String flags) {
    String f = (flags == null) ? "" : flags;
    if (f.indexOf('G') >= 0 || f.indexOf('g') >= 0) return IrcEvent.AwayState.AWAY;
    if (f.indexOf('H') >= 0 || f.indexOf('h') >= 0) return IrcEvent.AwayState.HERE;
    return IrcEvent.AwayState.UNKNOWN;
  }

  private static Map<String, String> ircv3TagsFromEvent(Object event) {
    return PircbotxIrcv3Tags.fromEvent(event);
  }

  private static Map<String, String> withObservedHostmaskTag(Map<String, String> tags, User user) {
    Map<String, String> out = (tags == null) ? new HashMap<>() : tags;
    if (user == null) return out;
    String hostmask = PircbotxUtil.hostmaskFromUser(user);
    if (!PircbotxUtil.isUsefulHostmask(hostmask)) return out;
    out.put(TAG_IRCAFE_HOSTMASK, hostmask.trim());
    return out;
  }

  private static String ircv3MessageId(Map<String, String> tags) {
    return PircbotxIrcv3Tags.firstTagValue(tags, "msgid", "draft/msgid");
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

  private static String clip(Object v) {
    if (v == null) return "<null>";
    String s = String.valueOf(v);
    if (s == null) return "<null>";
    s = s.replace('\n', ' ').replace('\r', ' ');
    if (s.length() > 280) return s.substring(0, 277) + "...";
    return s;
  }

  private void emitRoster(Channel channel) {
    if (channel == null) return;

    String channelName = channel.getName();
    java.util.Set<?> owners = setOrEmpty(channel, "getOwners");
    java.util.Set<?> admins = setOrEmpty(channel, "getSuperOps");
    java.util.Set<?> ops = setOrEmpty(channel, "getOps");
    java.util.Set<?> halfOps = setOrEmpty(channel, "getHalfOps");
    java.util.Set<?> voices = setOrEmpty(channel, "getVoices");

    List<IrcEvent.NickInfo> nicks =
        channel.getUsers().stream()
            .map(
                u ->
                    new IrcEvent.NickInfo(
                        u.getNick(),
                        prefixForUser(u, owners, admins, ops, halfOps, voices),
                        PircbotxUtil.hostmaskFromUser(u)))
            .sorted(
                Comparator.comparingInt((IrcEvent.NickInfo n) -> prefixRank(n.prefix()))
                    .thenComparing(IrcEvent.NickInfo::nick, String.CASE_INSENSITIVE_ORDER))
            .toList();

    int totalUsers = nicks.size();
    int operatorCount = (int) nicks.stream().filter(PircbotxBridgeListener::isOperatorLike).count();

    bus.onNext(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.NickListUpdated(
                Instant.now(), channelName, nicks, totalUsers, operatorCount)));
  }
}
