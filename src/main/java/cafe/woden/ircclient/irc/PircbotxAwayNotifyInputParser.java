package cafe.woden.ircclient.irc;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.pircbotx.UserHostmask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputParser hook for a few low-cost IRCv3 capabilities (away-notify, account-notify, extended-join, account-tag).
 *
 * <p>Away-notify arrives as raw lines like:
 * <ul>
 *   <li><code>:nick!user@host AWAY :Gone away for now</code></li>
 *   <li><code>:nick!user@host AWAY</code></li>
 * </ul>
 */
final class PircbotxAwayNotifyInputParser extends InputParser {

  private static final Logger log = LoggerFactory.getLogger(PircbotxAwayNotifyInputParser.class);

  private final String serverId;
  private final Consumer<ServerIrcEvent> sink;

  private final PircbotxConnectionState conn;
  private final Ircv3StsPolicyService stsPolicies;

  // Deduplicate high-frequency account-tag observations (which can appear on every PRIVMSG).
  private final Map<String, String> lastAccountTagByNickLower = new HashMap<>();

  PircbotxAwayNotifyInputParser(
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
  }

  @Override
  public void processCommand(
      String target,
      UserHostmask source,
      String command,
      String line,
      List<String> parsedLine,
      ImmutableMap<String, String> tags
  ) throws IOException {
    Instant now = Instant.now();
    String sourceNick = source != null ? Objects.toString(source.getNick(), "").trim() : "";

    // Capture self-query target hints *before* default dispatch so onPrivateMessage/onAction can
    // resolve the destination even when PircBotX doesn't expose recipient accessors.
    captureSelfPrivateMessageTargetHint(now, sourceNick, target, command, line, parsedLine, tags);

    // Preserve default behavior first (this keeps User.isAway()/getAwayMessage() accurate).
    super.processCommand(target, source, command, line, parsedLine, tags);
    if (command == null) return;

    // Detect CAP state changes for capabilities we care about.
    if ("CAP".equalsIgnoreCase(command) && parsedLine != null && parsedLine.size() >= 2) {
      String sub = parsedLine.get(1);
      String capList = capListFrom(parsedLine);
      if (sub != null && !capList.isBlank()) {
        if ("LS".equalsIgnoreCase(sub) || "NEW".equalsIgnoreCase(sub) || "ACK".equalsIgnoreCase(sub)) {
          stsPolicies.observeFromCapList(
              serverId,
              conn.connectedHost.get(),
              conn.connectedWithTls.get(),
              capList);
        }
        if ("LS".equalsIgnoreCase(sub)
            || "NEW".equalsIgnoreCase(sub)
            || "ACK".equalsIgnoreCase(sub)
            || "DEL".equalsIgnoreCase(sub)) {
          observeMultilineCapValuesFromCapLine(sub, capList);
        }
        if ("ACK".equalsIgnoreCase(sub) || "DEL".equalsIgnoreCase(sub)) {
          applyCapStateFromCapLine(sub, capList);
        } else if ("NEW".equalsIgnoreCase(sub)) {
          emitCapAvailabilityFromCapLine(sub, capList);
        }
      }
      return;
    }

    if ("MARKREAD".equalsIgnoreCase(command)) {
      String markerTarget = firstParam(parsedLine);
      String marker = secondParam(parsedLine);
      String from = source != null ? Objects.toString(source.getNick(), "").trim() : "server";
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.ReadMarkerObserved(now, from, markerTarget, marker)));
      return;
    }

    if (isStandardReplyCommand(command)) {
      emitStandardReply(now, command, line, parsedLine, tags);
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
          log.trace("[{}] account-tag observed via tags: nick={} cmd={} target={} state={} account={}",
              serverId, nick, command, target, st, name);
          sink.accept(new ServerIrcEvent(serverId,
              new IrcEvent.UserAccountStateObserved(now, nick, st, name)));
        }
      }
    }

    emitTagSignals(now, nick, target, command, parsedLine, tags);

    if ("SETNAME".equalsIgnoreCase(command)) {
      String realName = firstParam(parsedLine);
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserSetNameObserved(now, nick, realName)));
      return;
    }

    if ("CHGHOST".equalsIgnoreCase(command)) {
      String user = firstParam(parsedLine);
      String host = secondParam(parsedLine);
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserHostChanged(now, nick, user, host)));

      if (!user.isBlank() && !host.isBlank()) {
        String hm = nick + "!" + user + "@" + host;
        sink.accept(new ServerIrcEvent(serverId,
            new IrcEvent.UserHostmaskObserved(now, "", nick, hm)));
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
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserHostmaskObserved(now, "", nick, observedHostmask)));
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
      log.debug("[{}] away-notify observed via InputParser: nick={} state={} msg={} params={} raw={}",
          serverId, nick, state, msg, parsedLine, line);

      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserAwayStateObserved(now, nick, state, msg)));
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

      log.debug("[{}] account-notify observed via InputParser: nick={} state={} account={} params={} raw={}",
          serverId, nick, st, account, parsedLine, line);

      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserAccountStateObserved(now, nick, st, account)));
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

    log.debug("[{}] extended-join observed via InputParser: nick={} channel={} state={} account={} realName={} params={} raw={}",
        serverId, nick, channel, st, account, realName, parsedLine, line);

    // We treat this as another best-effort signal for account state.
    sink.accept(new ServerIrcEvent(serverId,
        new IrcEvent.UserAccountStateObserved(now, nick, st, account)));
    if (realName != null) {
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserSetNameObserved(now, nick, realName)));
    }
  }

  @Override
  public void processServerResponse(int code, String line, List<String> parsedLine) {
    if (code != 324) {
      super.processServerResponse(code, line, parsedLine);
      return;
    }

    try {
      super.processServerResponse(code, line, parsedLine);
    } catch (NumberFormatException ex) {
      PircbotxChannelModeParsers.ParsedRpl324 parsed = parseRpl324Fallback(line, parsedLine);
      if (parsed != null) {
        log.warn(
            "[{}] recovered from PircBotX RPL 324 parse failure: channel={} details={} line={}",
            serverId,
            parsed.channel(),
            parsed.details(),
            Objects.toString(line, ""));
        sink.accept(new ServerIrcEvent(serverId,
            new IrcEvent.ChannelModesListed(Instant.now(), parsed.channel(), parsed.details())));
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

  private static PircbotxChannelModeParsers.ParsedRpl324 parseRpl324Fallback(
      String line,
      List<String> parsedLine
  ) {
    PircbotxChannelModeParsers.ParsedRpl324 fromLine = PircbotxChannelModeParsers.parseRpl324(line);
    if (fromLine != null) return fromLine;

    if (parsedLine == null || parsedLine.size() < 3) return null;
    String channel = stripLeadingColon(parsedLine.get(1));
    if (channel.isBlank()) return null;

    StringBuilder details = new StringBuilder();
    for (int i = 2; i < parsedLine.size(); i++) {
      String token = stripLeadingColon(parsedLine.get(i));
      if (token.isBlank()) continue;
      if (details.length() > 0) details.append(' ');
      details.append(token);
    }
    if (details.length() == 0) return null;
    return new PircbotxChannelModeParsers.ParsedRpl324(channel, details.toString());
  }

  private void observeMultilineCapValuesFromCapLine(String sub, String capList) {
    String action = Objects.toString(sub, "").trim().toUpperCase(Locale.ROOT);
    boolean fromLs = "LS".equals(action);
    boolean fromNew = "NEW".equals(action);
    boolean fromAck = "ACK".equals(action);
    boolean fromDel = "DEL".equals(action);
    if (!fromLs && !fromNew && !fromAck && !fromDel) return;

    String caps = Objects.toString(capList, "").trim();
    if (caps.startsWith(":")) caps = caps.substring(1).trim();
    if (caps.isEmpty()) return;

    for (String token : caps.split("\\s+")) {
      String t = Objects.toString(token, "").trim();
      if (t.isEmpty()) continue;

      boolean tokenDisable = t.startsWith("-");
      String capName = canonicalCapName(t);
      if (!isMultilineCapability(capName)) continue;

      String capValue = capValueFromToken(t);
      MultilineCapLimits parsed = parseMultilineCapLimits(capValue);

      if (fromLs || fromNew) {
        if (tokenDisable) {
          setMultilineOfferedMaxBytes(capName, 0L);
          setMultilineOfferedMaxLines(capName, 0L);
        } else {
          if (parsed.maxBytes() > 0L) {
            setMultilineOfferedMaxBytes(capName, parsed.maxBytes());
          }
          if (parsed.maxLines() > 0L) {
            setMultilineOfferedMaxLines(capName, parsed.maxLines());
          }
        }
      }

      if (fromAck) {
        if (tokenDisable) {
          setMultilineNegotiatedMaxBytes(capName, 0L);
          setMultilineNegotiatedMaxLines(capName, 0L);
          setMultilineOfferedMaxBytes(capName, 0L);
          setMultilineOfferedMaxLines(capName, 0L);
        } else {
          if (parsed.maxBytes() > 0L) {
            setMultilineOfferedMaxBytes(capName, parsed.maxBytes());
          }
          if (parsed.maxLines() > 0L) {
            setMultilineOfferedMaxLines(capName, parsed.maxLines());
          }
          long effectiveMaxBytes = parsed.maxBytes() > 0L ? parsed.maxBytes() : multilineOfferedMaxBytes(capName);
          long effectiveMaxLines = parsed.maxLines() > 0L ? parsed.maxLines() : multilineOfferedMaxLines(capName);
          setMultilineNegotiatedMaxBytes(capName, effectiveMaxBytes);
          setMultilineNegotiatedMaxLines(capName, effectiveMaxLines);
        }
      }

      if (fromDel) {
        setMultilineNegotiatedMaxBytes(capName, 0L);
        setMultilineNegotiatedMaxLines(capName, 0L);
        setMultilineOfferedMaxBytes(capName, 0L);
        setMultilineOfferedMaxLines(capName, 0L);
      }
    }
  }

  private void applyCapStateFromCapLine(String sub, String capList) {
    if (sub == null) return;
    String action = sub.trim().toUpperCase(Locale.ROOT);
    boolean fromAck = "ACK".equals(action);
    boolean fromDel = "DEL".equals(action);
    if (!fromAck && !fromDel) return;

    String caps = Objects.toString(capList, "").trim();
    if (caps.startsWith(":")) caps = caps.substring(1).trim();
    if (caps.isEmpty()) return;

    for (String token : caps.split("\\s+")) {
      String t = Objects.toString(token, "").trim();
      if (t.isEmpty()) continue;

      boolean tokenDisable = t.startsWith("-");
      String capName = canonicalCapName(t);
      if (capName == null) continue;

      boolean enabled = fromAck && !tokenDisable;
      if (fromDel || tokenDisable) enabled = false;

      if (enabled && PircbotxZncParsers.seemsZncCap(capName)) {
        if (conn.zncDetected.compareAndSet(false, true)) {
          log.info("[{}] detected ZNC via CAP {}: {}", serverId, action, capName);
        }
      }

      setCapState(capName, enabled, action);
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.Ircv3CapabilityChanged(Instant.now(), action, capName, enabled)));
    }
  }

  private void emitCapAvailabilityFromCapLine(String sub, String capList) {
    String action = Objects.toString(sub, "").trim().toUpperCase(Locale.ROOT);
    if (!"NEW".equals(action)) return;
    String caps = Objects.toString(capList, "").trim();
    if (caps.startsWith(":")) caps = caps.substring(1).trim();
    if (caps.isEmpty()) return;

    for (String token : caps.split("\\s+")) {
      String capName = canonicalCapName(token);
      if (capName == null || capName.isBlank()) continue;
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.Ircv3CapabilityChanged(Instant.now(), action, capName, false)));
    }
  }

  private void emitTagSignals(
      Instant at,
      String nick,
      String rawTarget,
      String command,
      List<String> parsedLine,
      ImmutableMap<String, String> tags
  ) {
    if (tags == null || tags.isEmpty()) return;

    String cmd = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    String firstParam = firstParam(parsedLine);
    String msgTarget = !firstParam.isBlank() ? firstParam : stripLeadingColon(rawTarget);
    String convTarget = resolveConversationTarget(msgTarget, nick);

    if (cmd.equals("PRIVMSG") || cmd.equals("NOTICE") || cmd.equals("TAGMSG")) {
      String replyTo = firstTag(tags, "draft/reply", "+draft/reply");
      if (!replyTo.isBlank()) {
        sink.accept(new ServerIrcEvent(serverId,
            new IrcEvent.MessageReplyObserved(at, nick, convTarget, replyTo)));
      }

      String react = firstTag(tags, "draft/react", "+draft/react");
      if (!react.isBlank()) {
        String msgId = firstTag(tags, "draft/reply", "+draft/reply");
        if (msgId.isBlank()) {
          msgId = firstTag(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
        }
        sink.accept(new ServerIrcEvent(serverId,
            new IrcEvent.MessageReactObserved(at, nick, convTarget, react, msgId)));
      }

      String redactMsgId = firstTag(
          tags,
          "draft/delete",
          "+draft/delete",
          "draft/redact",
          "+draft/redact");
      if (!redactMsgId.isBlank()) {
        sink.accept(new ServerIrcEvent(serverId,
            new IrcEvent.MessageRedactionObserved(at, nick, convTarget, redactMsgId)));
      }

      String typing = firstTag(tags, "typing", "+typing");
      if (!typing.isBlank()) {
        if (log.isDebugEnabled()) {
          log.debug("[{}] IRCv3 +typing tag: from={} target={} state={} cmd={}", serverId, nick, convTarget, typing, cmd);
        }
        sink.accept(new ServerIrcEvent(serverId,
            new IrcEvent.UserTypingObserved(at, nick, convTarget, typing)));
      }
    }

    String readMarker = firstTag(tags, "draft/read-marker", "+draft/read-marker", "read-marker", "+read-marker");
    if (!readMarker.isBlank()) {
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.ReadMarkerObserved(at, nick, convTarget, readMarker)));
    }
  }

  private void setCapState(String capName, boolean enabled, String sourceAction) {
    String c = capName.toLowerCase(Locale.ROOT);
    switch (c) {
      case "znc.in/playback" -> {
        boolean prev = conn.zncPlaybackCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: znc.in/playback {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "batch" -> {
        boolean prev = conn.batchCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: batch {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "draft/chathistory", "chathistory" -> {
        boolean prev = conn.chatHistoryCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: {} {}", serverId, sourceAction, c, enabled ? "enabled" : "disabled");
        }
      }
      case "soju.im/bouncer-networks" -> {
        boolean prev = conn.sojuBouncerNetworksCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: soju.im/bouncer-networks {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "server-time" -> {
        boolean prev = conn.serverTimeCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: server-time {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "standard-replies" -> {
        boolean prev = conn.standardRepliesCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: standard-replies {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "echo-message" -> {
        boolean prev = conn.echoMessageCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: echo-message {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "cap-notify" -> {
        boolean prev = conn.capNotifyCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: cap-notify {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "labeled-response" -> {
        boolean prev = conn.labeledResponseCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: labeled-response {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "setname" -> {
        boolean prev = conn.setnameCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: setname {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "chghost" -> {
        boolean prev = conn.chghostCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: chghost {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "sts" -> {
        boolean prev = conn.stsCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: sts {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "multiline" -> {
        boolean prev = conn.multilineCapAcked.getAndSet(enabled);
        if (!enabled) {
          conn.multilineMaxBytes.set(0L);
          conn.multilineMaxLines.set(0L);
        }
        if (prev != enabled) {
          log.info("[{}] CAP {}: multiline {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "draft/multiline" -> {
        boolean prev = conn.draftMultilineCapAcked.getAndSet(enabled);
        if (!enabled) {
          conn.draftMultilineMaxBytes.set(0L);
          conn.draftMultilineMaxLines.set(0L);
        }
        if (prev != enabled) {
          log.info("[{}] CAP {}: draft/multiline {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "draft/reply" -> {
        boolean prev = conn.draftReplyCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: draft/reply {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "draft/react" -> {
        boolean prev = conn.draftReactCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: draft/react {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "draft/message-edit", "message-edit" -> {
        boolean prev = conn.draftMessageEditCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: {} {}", serverId, sourceAction, c, enabled ? "enabled" : "disabled");
        }
      }
      case "draft/message-redaction", "message-redaction" -> {
        boolean prev = conn.draftMessageRedactionCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: {} {}", serverId, sourceAction, c, enabled ? "enabled" : "disabled");
        }
      }
      case "message-tags" -> {
        boolean prev = conn.messageTagsCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: message-tags {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "typing" -> {
        boolean prev = conn.typingCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: typing {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      case "read-marker" -> {
        boolean prev = conn.readMarkerCapAcked.getAndSet(enabled);
        if (prev != enabled) {
          log.info("[{}] CAP {}: read-marker {}", serverId, sourceAction, enabled ? "enabled" : "disabled");
        }
      }
      default -> {
        // Ignore capabilities we don't currently track.
      }
    }
  }

  private static String canonicalCapName(String rawToken) {
    String s = Objects.toString(rawToken, "").trim();
    if (s.isEmpty()) return null;
    if (s.startsWith(":")) s = s.substring(1).trim();
    if (s.startsWith("-")) s = s.substring(1).trim();
    int eq = s.indexOf('=');
    if (eq >= 0) s = s.substring(0, eq).trim();
    return s.isEmpty() ? null : s;
  }

  private static String capValueFromToken(String rawToken) {
    String s = Objects.toString(rawToken, "").trim();
    if (s.isEmpty()) return "";
    if (s.startsWith(":")) s = s.substring(1).trim();
    if (s.startsWith("-")) s = s.substring(1).trim();
    int eq = s.indexOf('=');
    if (eq < 0 || eq + 1 >= s.length()) return "";
    return s.substring(eq + 1).trim();
  }

  private static boolean isMultilineCapability(String capName) {
    if (capName == null || capName.isBlank()) return false;
    String c = capName.trim().toLowerCase(Locale.ROOT);
    return "multiline".equals(c) || "draft/multiline".equals(c);
  }

  private long multilineOfferedMaxBytes(String capName) {
    String c = Objects.toString(capName, "").trim().toLowerCase(Locale.ROOT);
    if ("multiline".equals(c)) return Math.max(0L, conn.multilineOfferedMaxBytes.get());
    if ("draft/multiline".equals(c)) return Math.max(0L, conn.draftMultilineOfferedMaxBytes.get());
    return 0L;
  }

  private long multilineOfferedMaxLines(String capName) {
    String c = Objects.toString(capName, "").trim().toLowerCase(Locale.ROOT);
    if ("multiline".equals(c)) return Math.max(0L, conn.multilineOfferedMaxLines.get());
    if ("draft/multiline".equals(c)) return Math.max(0L, conn.draftMultilineOfferedMaxLines.get());
    return 0L;
  }

  private void setMultilineOfferedMaxBytes(String capName, long maxBytes) {
    long normalized = Math.max(0L, maxBytes);
    String c = Objects.toString(capName, "").trim().toLowerCase(Locale.ROOT);
    if ("multiline".equals(c)) {
      conn.multilineOfferedMaxBytes.set(normalized);
    } else if ("draft/multiline".equals(c)) {
      conn.draftMultilineOfferedMaxBytes.set(normalized);
    }
  }

  private void setMultilineOfferedMaxLines(String capName, long maxLines) {
    long normalized = Math.max(0L, maxLines);
    String c = Objects.toString(capName, "").trim().toLowerCase(Locale.ROOT);
    if ("multiline".equals(c)) {
      conn.multilineOfferedMaxLines.set(normalized);
    } else if ("draft/multiline".equals(c)) {
      conn.draftMultilineOfferedMaxLines.set(normalized);
    }
  }

  private void setMultilineNegotiatedMaxBytes(String capName, long maxBytes) {
    long normalized = Math.max(0L, maxBytes);
    String c = Objects.toString(capName, "").trim().toLowerCase(Locale.ROOT);
    if ("multiline".equals(c)) {
      conn.multilineMaxBytes.set(normalized);
    } else if ("draft/multiline".equals(c)) {
      conn.draftMultilineMaxBytes.set(normalized);
    }
  }

  private void setMultilineNegotiatedMaxLines(String capName, long maxLines) {
    long normalized = Math.max(0L, maxLines);
    String c = Objects.toString(capName, "").trim().toLowerCase(Locale.ROOT);
    if ("multiline".equals(c)) {
      conn.multilineMaxLines.set(normalized);
    } else if ("draft/multiline".equals(c)) {
      conn.draftMultilineMaxLines.set(normalized);
    }
  }

  private static MultilineCapLimits parseMultilineCapLimits(String capValueRaw) {
    String raw = Objects.toString(capValueRaw, "").trim();
    if (raw.isEmpty()) return new MultilineCapLimits(0L, 0L);

    long maxBytes = 0L;
    long maxLines = 0L;

    for (String partRaw : raw.split(",")) {
      String part = Objects.toString(partRaw, "").trim();
      if (part.isEmpty()) continue;
      int eq = part.indexOf('=');
      if (eq <= 0 || eq + 1 >= part.length()) continue;

      String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      String value = part.substring(eq + 1).trim();
      if (value.isEmpty()) continue;
      try {
        long parsed = Long.parseLong(value);
        if (parsed <= 0L) continue;
        if ("max-bytes".equals(key)) {
          maxBytes = parsed;
        } else if ("max-lines".equals(key)) {
          maxLines = parsed;
        }
      } catch (NumberFormatException ignored) {
        // Ignore invalid attribute values while keeping any other valid limits.
      }
    }

    return new MultilineCapLimits(maxBytes, maxLines);
  }

  private record MultilineCapLimits(long maxBytes, long maxLines) {}

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
      ImmutableMap<String, String> tags
  ) {
    String cmd = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    if (!"PRIVMSG".equals(cmd)) return;
    if (!isSelfNick(fromNick)) return;

    String messageTarget = stripLeadingColon(rawTarget);
    if (messageTarget.isBlank()) {
      messageTarget = firstParam(parsedLine);
    }
    if (messageTarget.isBlank() || isChannelName(messageTarget) || looksLikeSelfTarget(messageTarget)) {
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
      ImmutableMap<String, String> tags
  ) {
    IrcEvent.StandardReplyKind kind = toStandardReplyKind(command);
    if (kind == null) return;

    ParsedStandardReply parsed = parseStandardReply(parsedLine);
    String msgId = firstTag(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
    Map<String, String> ircv3Tags = (tags == null) ? Map.of() : tags;
    String line = Objects.toString(rawLine, "").trim();
    sink.accept(new ServerIrcEvent(serverId, new IrcEvent.StandardReply(
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

  private record ParsedStandardReply(String command, String code, String context, String description) {}

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
