package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.processors.FlowableProcessor;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.types.GenericCTCPEvent;
import org.pircbotx.snapshot.ChannelSnapshot;
import org.pircbotx.snapshot.UserChannelDaoSnapshot;
import org.pircbotx.snapshot.UserSnapshot;

/**
 * PircBotX listener that translates raw events into IRCafe's {@link ServerIrcEvent} stream.
 *
 * <p>Extracted from {@link PircbotxIrcClientService} to keep the service focused on connection lifecycle
 * and configuration.
 */
final class PircbotxBridgeListener extends ListenerAdapter {
  private static final Logger log = LoggerFactory.getLogger(PircbotxBridgeListener.class);

  @FunctionalInterface
  interface CtcpRequestHandler {
    boolean handle(PircBotX bot, String fromNick, String message);
  }

  private final String serverId;
  private final PircbotxConnectionState conn;
  private final FlowableProcessor<ServerIrcEvent> bus;
  private final Consumer<PircbotxConnectionState> heartbeatStopper;
  private final BiConsumer<PircbotxConnectionState, String> reconnectScheduler;
  private final CtcpRequestHandler ctcpHandler;
  private final boolean disconnectOnSaslFailure;

  // SASL-related numerics per IRCv3:
  // 903 = RPL_SASLSUCCESS
  // 904 = ERR_SASLFAIL
  // 905 = ERR_SASLTOOLONG
  // 906 = ERR_SASLABORTED
  // 907 = ERR_SASLALREADY
  private static final int RPL_SASL_SUCCESS = 903;
  private static final int ERR_SASL_FAIL = 904;
  private static final int ERR_SASL_TOO_LONG = 905;
  private static final int ERR_SASL_ABORTED = 906;
  private static final int ERR_SASL_ALREADY = 907;

  // Join failure numerics (common across networks):
  // 403 = ERR_NOSUCHCHANNEL
  // 405 = ERR_TOOMANYCHANNELS
  // 471 = ERR_CHANNELISFULL
  // 473 = ERR_INVITEONLYCHAN
  // 474 = ERR_BANNEDFROMCHAN
  // 475 = ERR_BADCHANNELKEY
  // 476 = ERR_BADCHANMASK
  // 477 = ERR_NEEDREGGEDNICK (often used as "+r" join restriction)
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

    // The typical numeric shape is:
    //   :server <code> <yourNick> <channel> :<message>
    // Be permissive: find the first channel-like token after the nick.
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
      boolean disconnectOnSaslFailure
  ) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.bus = Objects.requireNonNull(bus, "bus");
    this.heartbeatStopper = Objects.requireNonNull(heartbeatStopper, "heartbeatStopper");
    this.reconnectScheduler = Objects.requireNonNull(reconnectScheduler, "reconnectScheduler");
    this.ctcpHandler = Objects.requireNonNull(ctcpHandler, "ctcpHandler");
    this.disconnectOnSaslFailure = disconnectOnSaslFailure;
  }

    // Parsing helpers have been extracted into small, pure helper classes to keep this listener readable:
    // - PircbotxLineParseUtil: normalization + token heuristics
    // - PircbotxAwayParsers: IRCv3 away-notify + 305/306 confirmations
    // - PircbotxWhoisParsers: WHOIS/WHOWAS numerics (311/314/301/318)
    // - PircbotxWhoUserhostParsers: WHO/WHOX + USERHOST numerics (352/354/302)
    // - PircbotxChannelModeParsers: channel mode listings (324)

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
        heartbeatStopper.accept(conn);
      }

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Disconnected(Instant.now(), reason)));

      // Some failures are not transient (e.g. authentication failures). In those cases, do not
      // enter an auto-reconnect loop.
      boolean suppressReconnect = conn.suppressAutoReconnectOnce.getAndSet(false);

      // Only auto-reconnect on non-manual disconnects.
      if (!conn.manualDisconnect.get() && !suppressReconnect) {
        reconnectScheduler.accept(conn, reason);
      }
    }


    @Override
    public void onMessage(MessageEvent event) {
      touchInbound();
      String channel = event.getChannel().getName();
      // Passive capture: learn hostmask for userlist/ignore visualization.
      maybeEmitHostmaskObserved(channel, event.getUser());
      String msg = event.getMessage();

      // No ignore filtering here (handled centrally in the app layer).

      String action = PircbotxUtil.parseCtcpAction(msg);
      if (action != null) {
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.ChannelAction(Instant.now(), channel, event.getUser().getNick(), action)
        ));
        return;
      }

      bus.onNext(new ServerIrcEvent(serverId,
          new IrcEvent.ChannelMessage(Instant.now(), channel, event.getUser().getNick(), msg)
      ));
    }


    @Override
    public void onAction(ActionEvent event) {
      // PircBotX parses CTCP ACTION (/me) into ActionEvent for us.
      touchInbound();
      String from = (event.getUser() != null) ? event.getUser().getNick() : "";
      String action = PircbotxUtil.safeStr(() -> event.getAction(), "");

      // No ignore filtering here (handled centrally in the app layer).

      if (event.getChannel() != null) {
        String channel = event.getChannel().getName();
        maybeEmitHostmaskObserved(channel, event.getUser());
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.ChannelAction(Instant.now(), channel, from, action)));
      } else {
        maybeEmitHostmaskObserved("", event.getUser());

        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.PrivateAction(Instant.now(), from, action)));
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

      // Passive capture: learn hostmask even for PMs (channel may be blank).
      maybeEmitHostmaskObserved("", event.getUser());
      // No ignore filtering here (handled centrally in the app layer).

      String action = PircbotxUtil.parseCtcpAction(msg);
      if (action != null) {
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.PrivateAction(Instant.now(), from, action)));
        return;
      }

      if (ctcpHandler.handle(event.getBot(), from, msg)) return;

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.PrivateMessage(Instant.now(), from, msg)));
    }

    @Override
    public void onNotice(NoticeEvent event) {
      touchInbound();
      String from = (event.getUser() != null) ? event.getUser().getNick() : "server";
      String notice = event.getNotice();

      if (event.getUser() != null) {
        // Passive capture: learn hostmask from NOTICE prefixes (incl. CTCP replies).
        maybeEmitHostmaskObserved("", event.getUser());
      }
      // No ignore filtering here (handled centrally in the app layer).

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Notice(Instant.now(), from, notice)));
    }

    @Override
    public void onGenericCTCP(GenericCTCPEvent event) throws Exception {
      touchInbound();
      log.info("CTCP: {}", event);

      String from = (event != null && event.getUser() != null) ? event.getUser().getNick() : "server";
      String hostmask = (event != null && event.getUser() != null) ? PircbotxUtil.hostmaskFromUser(event.getUser()) : "";

      if (event != null && event.getUser() != null) {
        String ch = (event.getChannel() != null) ? event.getChannel().getName() : "";
        // Passive capture: learn hostmask from CTCP request prefixes.
        maybeEmitHostmaskObserved(ch, event.getUser());
      }
      // No ignore filtering here (handled centrally in the app layer).

      String channel = (event != null && event.getChannel() != null) ? event.getChannel().getName() : null;

      // In PircBotX, the CTCP command is implied by the event type (PingEvent, VersionEvent, TimeEvent, etc).
      String simple = (event == null) ? "CTCP" : event.getClass().getSimpleName();
      String cmd = simple.endsWith("Event") ? simple.substring(0, simple.length() - "Event".length()) : simple;
      cmd = cmd.toUpperCase(Locale.ROOT);

      // Some CTCP types carry a value (e.g. PING has a ping value).
      String arg = null;
      try {
        java.lang.reflect.Method m = event.getClass().getMethod("getPingValue");
        Object v = m.invoke(event);
        if (v != null) arg = v.toString();
      } catch (Exception ignored) {
        // Ignore: not all CTCP events expose a value.
      }

      bus.onNext(new ServerIrcEvent(serverId,
          new IrcEvent.CtcpRequestReceived(Instant.now(), from, cmd, arg, channel)
      ));

      super.onGenericCTCP(event);
    }

    // FingerEvent is a CTCP request, but in PircBotX it is not part of the GenericCTCPEvent hierarchy.
    @Override
    public void onFinger(FingerEvent event) throws Exception {
      touchInbound();
      log.info("CTCP (FINGER): {}", event);

      String from = (event != null && event.getUser() != null) ? event.getUser().getNick() : "server";
      String hostmask = (event != null && event.getUser() != null) ? PircbotxUtil.hostmaskFromUser(event.getUser()) : "";

      if (event != null && event.getUser() != null) {
        String ch = (event.getChannel() != null) ? event.getChannel().getName() : "";
        // Passive capture: learn hostmask from CTCP request prefixes.
        maybeEmitHostmaskObserved(ch, event.getUser());
      }
      // No ignore filtering here (handled centrally in the app layer).

      String channel = (event != null && event.getChannel() != null) ? event.getChannel().getName() : null;
      bus.onNext(new ServerIrcEvent(serverId,
          new IrcEvent.CtcpRequestReceived(Instant.now(), from, "FINGER", null, channel)
      ));

      super.onFinger(event);
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
        bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.WhoisResult(Instant.now(), n, List.copyOf(lines))));
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
      String rawLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);

      // Surface SASL login failures (wrong password, etc.). These often arrive as raw numerics and
      // otherwise look like "nothing happened" to the user.
      Integer saslCode = parseSaslNumericCode(rawLine);
      if (saslCode != null) {
        handleSaslNumeric(saslCode, rawLine);
        return;
      }

      // Targeted diagnostics: confirm where IRCv3 away-notify lines are landing.
      // (Users broadcast AWAY state changes as raw AWAY commands.)
      if (rawLine != null && rawLine.contains(" AWAY") && log.isDebugEnabled()) {
        log.debug("[{}] inbound AWAY-ish line received in onUnknown: {}", serverId, rawLine);
      }

      // IRCv3 away-notify: users broadcast AWAY state changes as raw AWAY commands.
      // Example: ":nick!user@host AWAY :Gone away for now"  (sets AWAY)
      //          ":nick!user@host AWAY"                    (clears AWAY)
      PircbotxAwayParsers.ParsedAwayNotify awayNotify = PircbotxAwayParsers.parseAwayNotify(rawLine);
      if (awayNotify != null && awayNotify.nick() != null && !awayNotify.nick().isBlank()) {
        log.debug("[{}] parsed away-notify: nick={} state={} msg={}", serverId, awayNotify.nick(), awayNotify.awayState(), awayNotify.message());
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.UserAwayStateObserved(Instant.now(), awayNotify.nick(), awayNotify.awayState(), awayNotify.message())));
        return;
      } else if (rawLine != null && rawLine.contains(" AWAY") && log.isDebugEnabled()) {
        // If we see an AWAY-ish line here but fail to parse it, that's the smoking gun.
        log.debug("[{}] inbound AWAY-ish line did NOT parse as away-notify: {}", serverId, rawLine);
      }

      PircbotxChannelModeParsers.ParsedRpl324 parsed = PircbotxChannelModeParsers.parseRpl324(rawLine);
      if (parsed != null) {
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.ChannelModesListed(Instant.now(), parsed.channel(), parsed.details())));
        return;
      }

      PircbotxAwayParsers.ParsedAwayConfirmation away = PircbotxAwayParsers.parseRpl305or306Away(rawLine);
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
      List<PircbotxWhoUserhostParsers.UserhostEntry> uh = PircbotxWhoUserhostParsers.parseRpl302Userhost(rawLine);
      if (uh != null && !uh.isEmpty()) {
        Instant now = Instant.now();
        for (PircbotxWhoUserhostParsers.UserhostEntry e : uh) {
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
      PircbotxWhoisParsers.ParsedWhoisAway whoisAway = PircbotxWhoisParsers.parseRpl301WhoisAway(rawLine);
      if (whoisAway != null && whoisAway.nick() != null && !whoisAway.nick().isBlank()) {
        String nk = whoisAway.nick().trim();
        String k = nk.toLowerCase(Locale.ROOT);
        // Only mark the WHOIS probe as "saw away" if we actually initiated a WHOIS for this nick.
        conn.whoisSawAwayByNickLower.computeIfPresent(k, (kk, vv) -> Boolean.TRUE);
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.UserAwayStateObserved(Instant.now(), nk, IrcEvent.AwayState.AWAY, whoisAway.message())));
      }

      String endWhoisNick = PircbotxWhoisParsers.parseRpl318EndOfWhoisNick(rawLine);
      if (endWhoisNick != null && !endWhoisNick.isBlank()) {
        String nk = endWhoisNick.trim();
        Boolean sawAway = conn.whoisSawAwayByNickLower.remove(nk.toLowerCase(Locale.ROOT));
        // Only infer HERE when this 318 completes a WHOIS probe we initiated.
        if (sawAway != null && !sawAway.booleanValue()) {
          bus.onNext(new ServerIrcEvent(serverId,
              new IrcEvent.UserAwayStateObserved(Instant.now(), nk, IrcEvent.AwayState.HERE)));
        }
      }

      PircbotxWhoisParsers.ParsedWhoisUser whoisUser = PircbotxWhoisParsers.parseRpl311WhoisUser(rawLine);
      if (whoisUser == null) whoisUser = PircbotxWhoisParsers.parseRpl314WhowasUser(rawLine);
      if (whoisUser != null && !whoisUser.nick().isBlank() && !whoisUser.user().isBlank() && !whoisUser.host().isBlank()) {
        String hm = whoisUser.nick() + "!" + whoisUser.user() + "@" + whoisUser.host();
        bus.onNext(new ServerIrcEvent(serverId,
            new IrcEvent.UserHostmaskObserved(Instant.now(), "", whoisUser.nick(), hm)));
      }

      // WHO fallback: parse WHOREPLY (352) lines. We don't actively issue WHO queries by default
      // (to keep traffic low), but if a user runs WHO manually, or a future resolver uses WHO/WHOX,
      // we can still learn hostmasks from the replies.
      PircbotxWhoUserhostParsers.ParsedWhoReply whoReply = PircbotxWhoUserhostParsers.parseRpl352WhoReply(rawLine);
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
      PircbotxWhoUserhostParsers.ParsedWhoxReply whox = PircbotxWhoUserhostParsers.parseRpl354WhoxReply(rawLine);
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

      // SASL login failures (wrong password, etc.) often arrive as numerics.
      // Surface them as a user-visible disconnect reason and suppress auto-reconnect loops.
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

      // Join failures (e.g. +r requires NickServ auth) are typically delivered as numerics.
      // We surface them so the UI can show the reason in both status and the buffer
      // where the user initiated /join.
      if (isJoinFailureNumeric(code)) {
        String line = null;
        Object l = reflectCall(event, "getLine");
        if (l == null) l = reflectCall(event, "getRawLine");
        if (l != null) line = String.valueOf(l);
        if (line == null || line.isBlank()) line = String.valueOf(event);

        ParsedJoinFailure pj = parseJoinFailure(line);
        if (pj != null) {
          bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.JoinFailed(
              Instant.now(),
              pj.channel(),
              code,
              pj.message()
          )));
        }
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
          PircbotxWhoisParsers.ParsedWhoisAway whoisAway = PircbotxWhoisParsers.parseRpl301WhoisAway(line);
          if (whoisAway != null && whoisAway.nick() != null && !whoisAway.nick().isBlank()) {
            String nk = whoisAway.nick().trim();
            String k = nk.toLowerCase(Locale.ROOT);
            conn.whoisSawAwayByNickLower.computeIfPresent(k, (kk, vv) -> Boolean.TRUE);
            bus.onNext(new ServerIrcEvent(serverId,
                new IrcEvent.UserAwayStateObserved(Instant.now(), nk, IrcEvent.AwayState.AWAY, whoisAway.message())));
          }
        } else {
          String nk = PircbotxWhoisParsers.parseRpl318EndOfWhoisNick(line);
          if (nk != null && !nk.isBlank()) {
            nk = nk.trim();
            Boolean sawAway = conn.whoisSawAwayByNickLower.remove(nk.toLowerCase(Locale.ROOT));
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

      PircbotxAwayParsers.ParsedAwayConfirmation away = PircbotxAwayParsers.parseRpl305or306Away(line);
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

      try {
        maybeEmitHostmaskObserved("", event.getUser());
      } catch (Exception ignored) {}

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

    private static Integer parseSaslNumericCode(String rawLine) {
      if (rawLine == null || rawLine.isBlank()) return null;

      // Most numeric lines look like: ":server.name 904 nick :SASL authentication failed"
      // Be permissive: sometimes the prefix may be missing in stringified forms.
      String s = rawLine.trim();
      String[] parts = s.split("\\s+");
      if (parts.length == 0) return null;

      int codeIdx = (parts[0].startsWith(":")) ? 1 : 0;
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
      // Best-effort extract a human message.
      String msg = extractTrailingMessage(rawLine);

      String base = switch (code) {
        case ERR_SASL_FAIL -> "SASL authentication failed";
        case ERR_SASL_TOO_LONG -> "SASL authentication failed (payload too long)";
        case ERR_SASL_ABORTED -> "SASL authentication aborted";
        case ERR_SASL_ALREADY -> "SASL authentication already completed";
        default -> "SASL authentication failed";
      };

      String detail = base;
      if (msg != null && !msg.isBlank()) {
        String m = msg.trim();
        // Avoid duplicating the same phrase when the server already provided it.
        if (!m.equalsIgnoreCase(base)) {
          detail = base + ": " + m;
        }
      }

      String reason = "Login failed — " + detail;

      // Avoid spamming duplicate lines if PircBotX delivers the numeric via multiple paths.
      String existing = conn.disconnectReasonOverride.get();
      if (existing != null && !existing.isBlank()) {
        // Still suppress auto-reconnect for auth failures.
        conn.suppressAutoReconnectOnce.set(true);
        return;
      }

      conn.disconnectReasonOverride.set(reason);
      conn.suppressAutoReconnectOnce.set(true);

      // Also surface an immediate UI-visible error (disconnect will follow soon after).
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.Error(Instant.now(), reason, null)));

      // If configured, treat SASL failure as a hard connect failure and disconnect immediately.
      if (disconnectOnSaslFailure) {
        PircBotX bot = conn.botRef.get();
        if (bot != null) {
          try {
            bot.stopBotReconnect();
          } catch (Exception ignored) {}
          try {
            bot.sendIRC().quitServer(reason);
          } catch (Exception ignored) {}
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
      if (Objects.equals(prev, hm)) return; // no change

      String ch = (channel == null) ? "" : channel;
      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.UserHostmaskObserved(
          Instant.now(), ch, nick.trim(), hm
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
      try {
        maybeEmitHostmaskObserved("", event.getUser());
      } catch (Exception ignored) {}
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
              PircbotxUtil.hostmaskFromUser(u)
          ))
          .sorted(Comparator
              .comparingInt((IrcEvent.NickInfo n) -> prefixRank(n.prefix()))
              .thenComparing(IrcEvent.NickInfo::nick, String.CASE_INSENSITIVE_ORDER))
          .toList();

      int totalUsers = nicks.size();
      int operatorCount = (int) nicks.stream().filter(PircbotxBridgeListener::isOperatorLike).count();

      bus.onNext(new ServerIrcEvent(serverId, new IrcEvent.NickListUpdated(
          Instant.now(),
          channelName,
          nicks,
          totalUsers,
          operatorCount
      )));

    }
}