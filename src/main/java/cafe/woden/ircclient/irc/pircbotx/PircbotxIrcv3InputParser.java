package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.mode.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.pircbotx.UserHostmask;
import org.pircbotx.exception.DaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputParser hook for a few low-cost IRCv3 capabilities (away-notify, account-notify,
 * extended-join, account-tag).
 *
 * <p>Away-notify arrives as raw lines like:
 *
 * <ul>
 *   <li><code>:nick!user@host AWAY :Gone away for now</code>
 *   <li><code>:nick!user@host AWAY</code>
 * </ul>
 */
final class PircbotxIrcv3InputParser extends InputParser {

  private static final Logger log = LoggerFactory.getLogger(PircbotxIrcv3InputParser.class);
  private static final int MAX_ACCOUNT_TAG_KEYS = 8_192;

  private final String serverId;
  private final Consumer<ServerIrcEvent> sink;

  private final PircbotxConnectionState conn;
  private final Ircv3StsPolicyService stsPolicies;
  private final PircbotxCapabilityStateSupport capabilityStateSupport;
  private final PircbotxMultilineCapStateSupport multilineCapStateSupport =
      new PircbotxMultilineCapStateSupport();

  // Deduplicate high-frequency account-tag observations (which can appear on every PRIVMSG).
  private final Map<String, String> lastAccountTagByNickLower =
      Collections.synchronizedMap(
          new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
              return size() > MAX_ACCOUNT_TAG_KEYS;
            }
          });

  PircbotxIrcv3InputParser(
      PircBotX bot,
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> sink,
      Ircv3StsPolicyService stsPolicies) {
    super(bot);
    this.serverId = serverId;
    this.sink = Objects.requireNonNull(sink, "sink");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.stsPolicies = Objects.requireNonNull(stsPolicies, "stsPolicies");
    this.capabilityStateSupport = new PircbotxCapabilityStateSupport(this.serverId, this.conn);
  }

  @Override
  public void processCommand(
      String target,
      UserHostmask source,
      String command,
      String line,
      List<String> parsedLine,
      ImmutableMap<String, String> tags)
      throws IOException {
    Instant now = Instant.now();
    String sourceNick = source != null ? Objects.toString(source.getNick(), "").trim() : "";

    // Capture self-query target hints *before* default dispatch so onPrivateMessage/onAction can
    // resolve the destination even when PircBotX doesn't expose recipient accessors.
    captureSelfPrivateMessageTargetHint(now, sourceNick, target, command, line, parsedLine, tags);

    // Preserve default behavior first (this keeps User.isAway()/getAwayMessage() accurate).
    super.processCommand(target, source, command, line, parsedLine, tags);
    if (command == null) return;

    if ("PONG".equalsIgnoreCase(command)) {
      String lagToken = extractTrailingParamToken(parsedLine, line);
      if (conn.observeLagProbePong(lagToken, System.currentTimeMillis())) {
        return;
      }
      observePassiveLagSampleFromServerTime(tags, line);
      return;
    }

    if ("PING".equalsIgnoreCase(command)) {
      observePassiveLagSampleFromServerTime(tags, line);
      return;
    }

    // Detect CAP state changes for capabilities we care about.
    if ("CAP".equalsIgnoreCase(command) && parsedLine != null && parsedLine.size() >= 2) {
      ParsedCapLine capLine = ParsedCapLine.parse(parsedLine.get(1), capListFrom(parsedLine));
      if (capLine.hasTokens()) {
        if (capLine.isAction("LS", "NEW", "ACK")) {
          stsPolicies.observeFromCapList(
              serverId,
              conn.connectedHost.get(),
              conn.connectedWithTls.get(),
              capLine.normalizedCaps());
        }
        if (capLine.isAction("LS", "NEW", "ACK", "DEL")) {
          multilineCapStateSupport.observe(capLine, conn);
        }
        if (capLine.isAction("ACK", "DEL")) {
          applyCapStateFromCapLine(capLine);
        } else if (capLine.isAction("NEW", "LS")) {
          emitCapAvailabilityFromCapLine(capLine);
        } else if (capLine.isAction("NAK")) {
          emitCapNakFromCapLine(capLine);
        }
        maybeRequestMessageTagsFallback(capLine);
        maybeRequestHistoryCapabilityFallback(capLine);
      }
      return;
    }

    if ("MARKREAD".equalsIgnoreCase(command)) {
      String markerTarget = firstParam(parsedLine);
      String marker = secondParam(parsedLine);
      String from = source != null ? Objects.toString(source.getNick(), "").trim() : "server";
      sink.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.ReadMarkerObserved(now, from, markerTarget, marker)));
      return;
    }

    if (isStandardReplyCommand(command)) {
      emitStandardReply(now, command, line, parsedLine, tags);
      return;
    }

    if ("REDACT".equalsIgnoreCase(command)) {
      String redactTarget = firstParam(parsedLine);
      String redactMsgId = secondParam(parsedLine);
      if (!redactMsgId.isBlank()) {
        String from = sourceNick.isBlank() ? "server" : sourceNick;
        String convTarget = resolveConversationTarget(redactTarget, sourceNick);
        sink.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.MessageRedactionObserved(now, from, convTarget, redactMsgId)));
      }
      return;
    }

    if (source == null) return;
    String nick = sourceNick;
    if (nick.isEmpty()) return;

    // IRCv3 account-tag: message tags include @account=<name> (or @account=* / @account=0).
    // This can arrive on many commands (PRIVMSG/NOTICE/etc), so we treat it as a best-effort signal
    // for account state and deduplicate per-nick to avoid excessive downstream work.
    if (tags != null) {
      String tagged = tags.get("account");
      if (tagged != null) {
        String account = tagged.trim();
        String key = nick.toLowerCase(Locale.ROOT);
        String prev = lastAccountTagByNickLower.put(key, account);
        if (!java.util.Objects.equals(prev, account)) {
          IrcEvent.AccountState st;
          String name = null;
          if (account.isEmpty() || "*".equals(account) || "0".equals(account)) {
            st = IrcEvent.AccountState.LOGGED_OUT;
          } else {
            st = IrcEvent.AccountState.LOGGED_IN;
            name = account;
          }
          // Keep this at TRACE: account-tag can be very chatty.
          log.trace(
              "[{}] account-tag observed via tags: nick={} cmd={} target={} state={} account={}",
              serverId,
              nick,
              command,
              target,
              st,
              name);
          sink.accept(
              new ServerIrcEvent(
                  serverId, new IrcEvent.UserAccountStateObserved(now, nick, st, name)));
        }
      }
    }

    emitTagSignals(now, nick, target, command, parsedLine, tags);

    if ("SETNAME".equalsIgnoreCase(command)) {
      String realName = firstParam(parsedLine);
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserSetNameObserved(
                  now, nick, realName, IrcEvent.UserSetNameObserved.Source.SETNAME)));
      return;
    }

    if ("CHGHOST".equalsIgnoreCase(command)) {
      String user = firstParam(parsedLine);
      String host = secondParam(parsedLine);
      sink.accept(
          new ServerIrcEvent(serverId, new IrcEvent.UserHostChanged(now, nick, user, host)));

      if (!user.isBlank() && !host.isBlank()) {
        String hm = nick + "!" + user + "@" + host;
        sink.accept(
            new ServerIrcEvent(serverId, new IrcEvent.UserHostmaskObserved(now, "", nick, hm)));
      }
      return;
    }

    // The remaining handlers are specific to a small set of IRCv3 capabilities that arrive as
    // distinct commands. (account-tag above can arrive on many commands and is handled first.)
    boolean isAway = "AWAY".equalsIgnoreCase(command);
    boolean isAccount = "ACCOUNT".equalsIgnoreCase(command);
    boolean isJoin = "JOIN".equalsIgnoreCase(command);
    if (!isAway && !isAccount && !isJoin) return;

    // Opportunistic hostmask capture: away-notify + account-notify lines include a full prefix
    // (nick!user@host), which we can use to enrich the roster hostmask cache.
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

    // Hostmask capture is most valuable for passive state updates (away/account-notify), since
    // JOIN/PART/QUIT already produce hostmask observations via the bridge listener.
    if ((isAway || isAccount) && PircbotxUtil.isUsefulHostmask(observedHostmask)) {
      sink.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.UserHostmaskObserved(now, "", nick, observedHostmask)));
    }

    if (isAway) {
      // AWAY with a parameter means "now away". AWAY with no parameter means "back".
      // NOTE: In this callback, `command` is already the IRC command and `parsedLine` is the
      // *parameter* list (e.g. [":Gone away"]), not the entire tokenized line.
      boolean nowAway = (parsedLine != null && !parsedLine.isEmpty());
      IrcEvent.AwayState state = nowAway ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE;

      String msg = null;
      if (nowAway && parsedLine != null && !parsedLine.isEmpty()) {
        // For AWAY, the only parameter is the away message (if present).
        msg = parsedLine.get(0);
        if (msg != null && msg.startsWith(":")) msg = msg.substring(1);
      }

      // Per-event away-notify logs can get noisy; keep at DEBUG.
      log.debug(
          "[{}] away-notify observed via InputParser: nick={} state={} msg={} params={} raw={}",
          serverId,
          nick,
          state,
          msg,
          parsedLine,
          line);

      sink.accept(
          new ServerIrcEvent(serverId, new IrcEvent.UserAwayStateObserved(now, nick, state, msg)));
      return;
    }

    if (isAccount) {
      // IRCv3 account-notify:
      //  - ACCOUNT <account>  (logged in)
      //  - ACCOUNT *          (logged out)
      String account = null;
      if (parsedLine != null && !parsedLine.isEmpty()) {
        account = parsedLine.get(0);
        if (account != null && account.startsWith(":")) account = account.substring(1);
      }

      account = (account == null) ? null : account.trim();
      IrcEvent.AccountState st;
      if (account == null || account.isEmpty() || "*".equals(account) || "0".equals(account)) {
        st = IrcEvent.AccountState.LOGGED_OUT;
        account = null;
      } else {
        st = IrcEvent.AccountState.LOGGED_IN;
      }

      log.debug(
          "[{}] account-notify observed via InputParser: nick={} state={} account={} params={} raw={}",
          serverId,
          nick,
          st,
          account,
          parsedLine,
          line);

      sink.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.UserAccountStateObserved(now, nick, st, account)));
      return;
    }

    // IRCv3 extended-join:
    //  :nick!user@host JOIN #channel <account> :<realname>
    // Account may be "*" (or "0") to indicate logged out.
    if (!isJoin) return;

    if (parsedLine == null || parsedLine.size() < 2) {
      // Not an extended-join payload (server may not support it or didn't accept CAP).
      return;
    }

    String channel = parsedLine.get(0);
    String account = parsedLine.get(1);
    if (account != null && account.startsWith(":")) account = account.substring(1);
    account = (account == null) ? null : account.trim();
    String realName = null;
    if (parsedLine.size() >= 3) {
      realName = parsedLine.get(2);
      if (realName != null && realName.startsWith(":")) realName = realName.substring(1);
      if (realName != null) {
        realName = realName.trim();
        if (realName.isEmpty()) realName = null;
      }
    }

    IrcEvent.AccountState st;
    if (account == null || account.isEmpty() || "*".equals(account) || "0".equals(account)) {
      st = IrcEvent.AccountState.LOGGED_OUT;
      account = null;
    } else {
      st = IrcEvent.AccountState.LOGGED_IN;
    }

    log.debug(
        "[{}] extended-join observed via InputParser: nick={} channel={} state={} account={} realName={} params={} raw={}",
        serverId,
        nick,
        channel,
        st,
        account,
        realName,
        parsedLine,
        line);

    // We treat this as another best-effort signal for account state.
    sink.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.UserAccountStateObserved(now, nick, st, account)));
    if (realName != null) {
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserSetNameObserved(
                  now, nick, realName, IrcEvent.UserSetNameObserved.Source.EXTENDED_JOIN)));
    }
  }

  @Override
  public void processServerResponse(int code, String line, List<String> parsedLine) {
    if (code == 1) {
      conn.registrationComplete.set(true);
    }
    try {
      super.processServerResponse(code, line, parsedLine);
      return;
    } catch (RuntimeException ex) {
      if (isIgnorableMissingChannelNumeric(code, parsedLine, ex)) {
        log.debug(
            "[{}] ignoring late numeric {} for channel already removed from DAO: channel={} line={}",
            serverId,
            code,
            channelForIgnorableMissingChannelNumeric(code, parsedLine),
            Objects.toString(line, ""));
        return;
      }
      if (code != 324) {
        throw ex;
      }
      PircbotxChannelModeParsers.ParsedRpl324 parsed =
          PircbotxChannelModeParsers.parseRpl324Fallback(line, parsedLine);
      if (parsed != null) {
        log.warn(
            "[{}] recovered from PircBotX RPL 324 parse failure ({}): channel={} details={} line={}",
            serverId,
            ex.getClass().getSimpleName(),
            parsed.channel(),
            parsed.details(),
            Objects.toString(line, ""));
        sink.accept(
            new ServerIrcEvent(
                serverId,
                ChannelModeObservationFactory.fromNumeric324Fallback(
                    Instant.now(), parsed.channel(), parsed.details())));
      } else {
        log.warn(
            "[{}] recovered from PircBotX RPL 324 parse failure but could not parse mode line: line={} parsed={}",
            serverId,
            Objects.toString(line, ""),
            parsedLine,
            ex);
      }
    }
  }

  private static boolean isIgnorableMissingChannelNumeric(
      int code, List<String> parsedLine, RuntimeException ex) {
    if (!(ex instanceof DaoException dao)
        || dao.getReason() != DaoException.Reason.UNKNOWN_CHANNEL) {
      return false;
    }
    return !channelForIgnorableMissingChannelNumeric(code, parsedLine).isBlank();
  }

  private static String channelForIgnorableMissingChannelNumeric(
      int code, List<String> parsedLine) {
    if (parsedLine == null || parsedLine.isEmpty()) return "";
    int channelIndex =
        switch (code) {
          case 353 -> 2;
          case 366, 367, 368, 728, 729 -> 1;
          default -> -1;
        };
    if (channelIndex < 0 || parsedLine.size() <= channelIndex) return "";
    return stripLeadingColon(parsedLine.get(channelIndex)).trim();
  }

  private void observePassiveLagSampleFromServerTime(
      ImmutableMap<String, String> tags, String rawLine) {
    Instant serverTaggedAt = serverTimeFromTagsOrLine(tags, rawLine);
    if (serverTaggedAt == null) return;
    long nowMs = System.currentTimeMillis();
    conn.observePassiveLagSample(nowMs - serverTaggedAt.toEpochMilli(), nowMs);
  }

  private static Instant serverTimeFromTagsOrLine(
      ImmutableMap<String, String> tags, String rawLine) {
    String tagged = Ircv3Tags.firstTagValue(tags, "time");
    if (!tagged.isBlank()) {
      try {
        return Instant.parse(tagged);
      } catch (Exception ignored) {
      }
    }
    return Ircv3ServerTime.parseServerTimeFromRawLine(rawLine);
  }

  private static String extractTrailingParamToken(List<String> parsedLine, String rawLine) {
    if (parsedLine != null) {
      for (int i = parsedLine.size() - 1; i >= 0; i--) {
        String token = stripLeadingColon(parsedLine.get(i)).trim();
        if (!token.isEmpty()) return token;
      }
    }

    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) return "";
    int tailStart = raw.lastIndexOf(' ');
    String tail = tailStart >= 0 ? raw.substring(tailStart + 1) : raw;
    return stripLeadingColon(tail).trim();
  }

  private void applyCapStateFromCapLine(ParsedCapLine capLine) {
    String action = capLine.action();
    boolean fromAck = capLine.isAction("ACK");
    boolean fromDel = capLine.isAction("DEL");
    if (!fromAck && !fromDel) return;

    boolean emittedAny = false;
    for (String t : capLine.tokens()) {
      boolean tokenDisable = t.startsWith("-");
      String capName = canonicalCapName(t);
      if (capName == null) continue;

      boolean enabled = fromAck && !tokenDisable;
      if (fromDel || tokenDisable) enabled = false;

      if (enabled && PircbotxZncParsers.seemsZncCap(capName)) {
        if (conn.zncDetected.compareAndSet(false, true)) {
          log.debug("[{}] detected ZNC via CAP {}: {}", serverId, action, capName);
        }
      }

      capabilityStateSupport.apply(capName, enabled, action);
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.Ircv3CapabilityChanged(Instant.now(), action, capName, enabled)));
      emittedAny = true;
    }
    if (emittedAny) {
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.ConnectionFeaturesUpdated(
                  Instant.now(), "cap-" + action.toLowerCase(Locale.ROOT))));
    }
  }

  private void emitCapAvailabilityFromCapLine(ParsedCapLine capLine) {
    String action = capLine.action();
    if (!capLine.isAction("NEW", "LS")) return;

    for (String token : capLine.tokens()) {
      String capName = canonicalCapName(token);
      if (capName == null || capName.isBlank()) continue;
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.Ircv3CapabilityChanged(Instant.now(), action, capName, false)));
    }
  }

  private void maybeRequestMessageTagsFallback(ParsedCapLine capLine) {
    if (!capLine.isAction("LS", "NEW")) return;
    if (conn.messageTagsCapAcked.get()) return;
    if (!conn.messageTagsFallbackReqSent.compareAndSet(false, true)) return;

    if (!capLine.hasTokens()) {
      conn.messageTagsFallbackReqSent.set(false);
      return;
    }

    boolean offered = false;
    for (String token : capLine.tokens()) {
      String capName = canonicalCapName(token);
      if ("message-tags".equalsIgnoreCase(capName)) {
        offered = true;
        break;
      }
    }
    if (!offered) {
      conn.messageTagsFallbackReqSent.set(false);
      return;
    }

    try {
      bot.sendCAP().request("message-tags");
      log.debug(
          "[{}] fallback CAP REQ sent for message-tags (downstream capability remained unenabled)",
          serverId);
    } catch (Exception ex) {
      conn.messageTagsFallbackReqSent.set(false);
      log.debug("[{}] fallback CAP REQ for message-tags failed", serverId, ex);
    }
  }

  private void maybeRequestHistoryCapabilityFallback(ParsedCapLine capLine) {
    if (!capLine.isAction("LS", "NEW")) return;
    if (!capLine.hasTokens()) return;

    boolean offeredBatch = false;
    boolean offeredChatHistory = false;
    boolean offeredDraftChatHistory = false;
    for (String token : capLine.tokens()) {
      String capName = canonicalCapName(token);
      if ("batch".equalsIgnoreCase(capName)) {
        offeredBatch = true;
      } else if ("chathistory".equalsIgnoreCase(capName)) {
        offeredChatHistory = true;
      } else if ("draft/chathistory".equalsIgnoreCase(capName)) {
        offeredDraftChatHistory = true;
      }
    }

    java.util.ArrayList<String> req = new java.util.ArrayList<>(2);
    boolean requestedBatch = false;
    boolean requestedHistory = false;

    if (offeredBatch
        && !conn.batchCapAcked.get()
        && conn.batchFallbackReqSent.compareAndSet(false, true)) {
      req.add("batch");
      requestedBatch = true;
    }

    String historyCapToRequest = "";
    if (!conn.chatHistoryCapAcked.get()) {
      if (offeredChatHistory) {
        historyCapToRequest = "chathistory";
      } else if (offeredDraftChatHistory) {
        historyCapToRequest = "draft/chathistory";
      }
    }
    if (!historyCapToRequest.isEmpty()
        && conn.chatHistoryFallbackReqSent.compareAndSet(false, true)) {
      req.add(historyCapToRequest);
      requestedHistory = true;
    }

    if (req.isEmpty()) return;

    try {
      bot.sendCAP().request(req.toArray(new String[0]));
      log.debug("[{}] fallback CAP REQ sent for {}", serverId, String.join(", ", req));
    } catch (Exception ex) {
      if (requestedBatch) {
        conn.batchFallbackReqSent.set(false);
      }
      if (requestedHistory) {
        conn.chatHistoryFallbackReqSent.set(false);
      }
      log.debug("[{}] fallback CAP REQ for history capabilities failed", serverId, ex);
    }
  }

  private void emitCapNakFromCapLine(ParsedCapLine capLine) {
    String action = capLine.action();
    if (!capLine.isAction("NAK")) return;

    for (String token : capLine.tokens()) {
      String capName = canonicalCapName(token);
      if (capName == null || capName.isBlank()) continue;
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.Ircv3CapabilityChanged(Instant.now(), action, capName, false)));
    }
  }

  private void emitTagSignals(
      Instant at,
      String nick,
      String rawTarget,
      String command,
      List<String> parsedLine,
      ImmutableMap<String, String> tags) {
    if (tags == null || tags.isEmpty()) return;

    String cmd = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    String firstParam = firstParam(parsedLine);
    String msgTarget = !firstParam.isBlank() ? firstParam : stripLeadingColon(rawTarget);
    String channelContext =
        firstTag(
            tags,
            "draft/channel-context",
            "+draft/channel-context",
            "channel-context",
            "+channel-context");
    String convTarget = resolveSignalTarget(msgTarget, nick, channelContext);

    if (cmd.equals("PRIVMSG") || cmd.equals("NOTICE") || cmd.equals("TAGMSG")) {
      String replyTo = firstTag(tags, "draft/reply", "+draft/reply");
      if (!replyTo.isBlank()) {
        sink.accept(
            new ServerIrcEvent(
                serverId, new IrcEvent.MessageReplyObserved(at, nick, convTarget, replyTo)));
      }

      String react = firstTag(tags, "draft/react", "+draft/react");
      if (!react.isBlank()) {
        String msgId = firstTag(tags, "draft/reply", "+draft/reply");
        if (msgId.isBlank()) {
          msgId = firstTag(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
        }
        sink.accept(
            new ServerIrcEvent(
                serverId, new IrcEvent.MessageReactObserved(at, nick, convTarget, react, msgId)));
      }

      String unreact = firstTag(tags, "draft/unreact", "+draft/unreact");
      if (!unreact.isBlank()) {
        String msgId = firstTag(tags, "draft/reply", "+draft/reply");
        if (msgId.isBlank()) {
          msgId = firstTag(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
        }
        sink.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.MessageUnreactObserved(at, nick, convTarget, unreact, msgId)));
      }

      String redactMsgId =
          firstTag(tags, "draft/delete", "+draft/delete", "draft/redact", "+draft/redact");
      if (!redactMsgId.isBlank()) {
        sink.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.MessageRedactionObserved(at, nick, convTarget, redactMsgId)));
      }

      String typing = firstTag(tags, "typing", "+typing");
      if (!typing.isBlank()) {
        if (log.isDebugEnabled()) {
          log.debug(
              "[{}] IRCv3 +typing tag: from={} target={} state={} cmd={}",
              serverId,
              nick,
              convTarget,
              typing,
              cmd);
        }
        sink.accept(
            new ServerIrcEvent(
                serverId, new IrcEvent.UserTypingObserved(at, nick, convTarget, typing)));
      }
    }

    String readMarker =
        firstTag(tags, "draft/read-marker", "+draft/read-marker", "read-marker", "+read-marker");
    if (!readMarker.isBlank()) {
      sink.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.ReadMarkerObserved(at, nick, convTarget, readMarker)));
    }
  }

  private static String canonicalCapName(String rawToken) {
    String s = Objects.toString(rawToken, "").trim();
    if (s.isEmpty()) return null;
    if (s.startsWith(":")) s = s.substring(1).trim();
    while (!s.isEmpty()) {
      char leading = s.charAt(0);
      // CAP v3 tokens may be prefixed by '-', '~', or '=' modifiers.
      if (leading == '-' || leading == '~' || leading == '=') {
        s = s.substring(1).trim();
        continue;
      }
      break;
    }
    int eq = s.indexOf('=');
    if (eq >= 0) s = s.substring(0, eq).trim();
    return s.isEmpty() ? null : s;
  }

  private static String capListFrom(List<String> parsedLine) {
    if (parsedLine == null || parsedLine.size() < 3) return "";
    int start = 2;
    if ("*".equals(parsedLine.get(start)) && parsedLine.size() > start + 1) {
      start++;
    }

    StringBuilder out = new StringBuilder();
    for (int i = start; i < parsedLine.size(); i++) {
      String token = Objects.toString(parsedLine.get(i), "").trim();
      if (token.isEmpty()) continue;
      if (out.length() > 0) out.append(' ');
      out.append(token);
    }
    return out.toString().trim();
  }

  private static String firstParam(List<String> parsedLine) {
    if (parsedLine == null || parsedLine.isEmpty()) return "";
    return stripLeadingColon(parsedLine.get(0));
  }

  private static String secondParam(List<String> parsedLine) {
    if (parsedLine == null || parsedLine.size() < 2) return "";
    return stripLeadingColon(parsedLine.get(1));
  }

  private static String stripLeadingColon(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.startsWith(":")) s = s.substring(1).trim();
    return s;
  }

  private static String resolveConversationTarget(String rawTarget, String fromNick) {
    String t = Objects.toString(rawTarget, "").trim();
    if (t.startsWith("#") || t.startsWith("&")) return t;
    String from = Objects.toString(fromNick, "").trim();
    return from.isBlank() ? t : from;
  }

  private static String resolveSignalTarget(
      String rawTarget, String fromNick, String channelContextTag) {
    String context = Objects.toString(channelContextTag, "").trim();
    if (isChannelName(context)) return context;
    return resolveConversationTarget(rawTarget, fromNick);
  }

  private static String firstTag(ImmutableMap<String, String> tags, String... keys) {
    if (tags == null || tags.isEmpty() || keys == null) return "";
    for (String k : keys) {
      if (k == null || k.isBlank()) continue;
      String want = normalizeTagKey(k);
      for (Map.Entry<String, String> e : tags.entrySet()) {
        String got = normalizeTagKey(e.getKey());
        if (!want.equals(got)) continue;
        String v = Objects.toString(e.getValue(), "").trim();
        if (v.isEmpty()) continue;
        return unescapeTagValue(v);
      }
    }
    return "";
  }

  private void captureSelfPrivateMessageTargetHint(
      Instant at,
      String fromNick,
      String rawTarget,
      String command,
      String rawLine,
      List<String> parsedLine,
      ImmutableMap<String, String> tags) {
    String cmd = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    if (!"PRIVMSG".equals(cmd)) return;
    if (!isSelfNick(fromNick)) return;

    String messageTarget = stripLeadingColon(rawTarget);
    if (messageTarget.isBlank()) {
      messageTarget = firstParam(parsedLine);
    }
    if (messageTarget.isBlank()
        || isChannelName(messageTarget)
        || looksLikeSelfTarget(messageTarget)) {
      return;
    }

    String first = firstParam(parsedLine);
    String second = secondParam(parsedLine);
    String payload = second;
    if (payload.isBlank() && !first.isBlank() && !first.equalsIgnoreCase(messageTarget)) {
      payload = first;
    }
    if (payload.isBlank()) {
      payload = trailingParam(rawLine);
    }
    String action = PircbotxUtil.parseCtcpAction(payload);
    String kind = action == null ? "PRIVMSG" : "ACTION";
    String normalizedPayload = action == null ? payload : action;
    String msgId = firstTag(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
    conn.rememberPrivateTargetHint(
        fromNick,
        messageTarget,
        kind,
        normalizedPayload,
        msgId,
        at == null ? System.currentTimeMillis() : at.toEpochMilli());
  }

  private boolean isSelfNick(String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return false;

    String hinted = Objects.toString(conn.selfNickHint.get(), "").trim();
    if (!hinted.isEmpty() && n.equalsIgnoreCase(hinted)) {
      return true;
    }

    try {
      PircBotX liveBot = this.bot;
      String fromBot = liveBot == null ? "" : Objects.toString(liveBot.getNick(), "").trim();
      return !fromBot.isEmpty() && n.equalsIgnoreCase(fromBot);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean looksLikeSelfTarget(String target) {
    String t = Objects.toString(target, "").trim();
    if (t.isEmpty()) return false;
    String hinted = Objects.toString(conn.selfNickHint.get(), "").trim();
    if (!hinted.isEmpty() && t.equalsIgnoreCase(hinted)) return true;
    try {
      PircBotX liveBot = this.bot;
      String fromBot = liveBot == null ? "" : Objects.toString(liveBot.getNick(), "").trim();
      return !fromBot.isEmpty() && t.equalsIgnoreCase(fromBot);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String trailingParam(String rawLine) {
    String line = Objects.toString(rawLine, "");
    int idx = line.indexOf(" :");
    if (idx < 0 || idx + 2 >= line.length()) return "";
    return line.substring(idx + 2).trim();
  }

  private static boolean isChannelName(String target) {
    String t = Objects.toString(target, "").trim();
    if (t.isEmpty()) return false;
    char c = t.charAt(0);
    return c == '#' || c == '&' || c == '!' || c == '+';
  }

  private void emitStandardReply(
      Instant at,
      String command,
      String rawLine,
      List<String> parsedLine,
      ImmutableMap<String, String> tags) {
    IrcEvent.StandardReplyKind kind = toStandardReplyKind(command);
    if (kind == null) return;

    ParsedStandardReply parsed = parseStandardReply(parsedLine);
    String msgId = firstTag(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
    Map<String, String> ircv3Tags = (tags == null) ? Map.of() : tags;
    String line = Objects.toString(rawLine, "").trim();
    sink.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.StandardReply(
                at,
                kind,
                parsed.command(),
                parsed.code(),
                parsed.context(),
                parsed.description(),
                line,
                msgId,
                ircv3Tags)));
  }

  private static ParsedStandardReply parseStandardReply(List<String> parsedLine) {
    String command = paramAt(parsedLine, 0);
    String code = paramAt(parsedLine, 1);
    String context = "";
    String description = "";
    if (parsedLine == null || parsedLine.size() <= 2) {
      return new ParsedStandardReply(command, code, context, description);
    }

    int trailingIdx = -1;
    for (int i = 2; i < parsedLine.size(); i++) {
      String token = Objects.toString(parsedLine.get(i), "");
      if (token.startsWith(":")) {
        trailingIdx = i;
        break;
      }
    }
    if (trailingIdx < 0) {
      trailingIdx = parsedLine.size() - 1;
    }

    description = stripLeadingColon(parsedLine.get(trailingIdx));
    if (trailingIdx > 2) {
      context = joinParams(parsedLine, 2, trailingIdx);
    }
    return new ParsedStandardReply(command, code, context, description);
  }

  private static String paramAt(List<String> parsedLine, int index) {
    if (parsedLine == null || index < 0 || index >= parsedLine.size()) return "";
    return stripLeadingColon(parsedLine.get(index));
  }

  private static String joinParams(List<String> parsedLine, int fromInclusive, int toExclusive) {
    if (parsedLine == null) return "";
    int from = Math.max(0, fromInclusive);
    int to = Math.min(parsedLine.size(), toExclusive);
    if (from >= to) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = from; i < to; i++) {
      String part = stripLeadingColon(parsedLine.get(i));
      if (part.isBlank()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(part);
    }
    return sb.toString().trim();
  }

  private static boolean isStandardReplyCommand(String command) {
    if (command == null || command.isBlank()) return false;
    String c = command.trim().toUpperCase(Locale.ROOT);
    return "FAIL".equals(c) || "WARN".equals(c) || "NOTE".equals(c);
  }

  private static IrcEvent.StandardReplyKind toStandardReplyKind(String command) {
    if (command == null || command.isBlank()) return null;
    return switch (command.trim().toUpperCase(Locale.ROOT)) {
      case "FAIL" -> IrcEvent.StandardReplyKind.FAIL;
      case "WARN" -> IrcEvent.StandardReplyKind.WARN;
      case "NOTE" -> IrcEvent.StandardReplyKind.NOTE;
      default -> null;
    };
  }

  private record ParsedStandardReply(
      String command, String code, String context, String description) {}

  private static String normalizeTagKey(String raw) {
    String k = Objects.toString(raw, "").trim();
    if (k.startsWith("@")) k = k.substring(1).trim();
    if (k.startsWith("+")) k = k.substring(1).trim();
    return k.toLowerCase(Locale.ROOT);
  }

  private static String unescapeTagValue(String raw) {
    if (raw == null || raw.isEmpty() || raw.indexOf('\\') < 0) return raw == null ? "" : raw;
    StringBuilder sb = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c != '\\') {
        sb.append(c);
        continue;
      }
      if (i + 1 >= raw.length()) break;
      char n = raw.charAt(++i);
      switch (n) {
        case ':' -> sb.append(';');
        case 's' -> sb.append(' ');
        case 'r' -> sb.append('\r');
        case 'n' -> sb.append('\n');
        case '\\' -> sb.append('\\');
        default -> sb.append(n);
      }
    }
    return sb.toString();
  }
}
