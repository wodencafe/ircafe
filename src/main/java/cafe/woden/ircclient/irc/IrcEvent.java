package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public sealed interface IrcEvent permits
    IrcEvent.Connected,
    IrcEvent.Connecting,
    IrcEvent.Disconnected,
    IrcEvent.Reconnecting,
    IrcEvent.NickChanged,
    IrcEvent.ChannelMessage,
    IrcEvent.ChannelAction,

    IrcEvent.ChannelModeChanged,
    IrcEvent.ChannelModesListed,
    IrcEvent.ChannelTopicUpdated,
    IrcEvent.PrivateMessage,
    IrcEvent.PrivateAction,

    IrcEvent.Notice,
    IrcEvent.WallopsReceived,

    IrcEvent.AwayStatusChanged,
    IrcEvent.UserJoinedChannel,
    IrcEvent.UserPartedChannel,
    IrcEvent.LeftChannel,
    IrcEvent.UserKickedFromChannel,
    IrcEvent.KickedFromChannel,
    IrcEvent.InvitedToChannel,
    IrcEvent.UserQuitChannel,
    IrcEvent.UserNickChangedChannel,
    IrcEvent.JoinedChannel,
    IrcEvent.JoinFailed,
    IrcEvent.NickListUpdated,
    IrcEvent.UserHostmaskObserved,
    IrcEvent.UserHostChanged,
    IrcEvent.UserAwayStateObserved,
    IrcEvent.UserAccountStateObserved,
    IrcEvent.UserSetNameObserved,
    IrcEvent.MonitorOnlineObserved,
    IrcEvent.MonitorOfflineObserved,
    IrcEvent.MonitorListObserved,
    IrcEvent.MonitorListEnded,
    IrcEvent.MonitorListFull,
    IrcEvent.UserTypingObserved,
    IrcEvent.ReadMarkerObserved,
    IrcEvent.MessageReplyObserved,
    IrcEvent.MessageReactObserved,
    IrcEvent.MessageRedactionObserved,
    IrcEvent.Ircv3CapabilityChanged,
    IrcEvent.WhoisResult,
    IrcEvent.WhoisProbeCompleted,
    IrcEvent.WhoxSupportObserved,
    IrcEvent.WhoxSchemaCompatibleObserved,
    IrcEvent.ServerTimeNotNegotiated,
    IrcEvent.ChatHistoryBatchReceived,
    IrcEvent.ZncPlaybackBatchReceived,
    IrcEvent.ServerResponseLine,
    IrcEvent.ChannelListStarted,
    IrcEvent.ChannelListEntry,
    IrcEvent.ChannelListEnded,
    IrcEvent.StandardReply,
    IrcEvent.Error,
    IrcEvent.CtcpRequestReceived
 {

  /**
   * Best-effort away status for a user.
   *
   * <p>Most networks do not provide away state in NAMES. This is intended to be updated later via
   * IRCv3 {@code away-notify} (or similar mechanisms). Until then, entries will generally be
   * {@link #UNKNOWN}.
   */
  enum AwayState {
    UNKNOWN,
    HERE,
    AWAY
  }

  /**
   * Best-effort account/login status for a user.
   *
   * <p>This is primarily populated via IRCv3 {@code account-notify} and
   * IRCv3 {@code extended-join}.
   */
  enum AccountState {
    UNKNOWN,
    LOGGED_OUT,
    LOGGED_IN
  }

  record Connected(Instant at, String serverHost, int serverPort, String nick) implements IrcEvent {}

  record Connecting(Instant at, String serverHost, int serverPort, String nick) implements IrcEvent {}
  record Disconnected(Instant at, String reason) implements IrcEvent {}

  record Reconnecting(Instant at, long attempt, long delayMs, String reason) implements IrcEvent {}

  record NickChanged(Instant at, String oldNick, String newNick) implements IrcEvent {}

  record ChannelMessage(
      Instant at,
      String channel,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags
  ) implements IrcEvent {
    public ChannelMessage {
      messageId = normalizeMessageId(messageId);
      ircv3Tags = normalizeIrcv3Tags(ircv3Tags);
    }

    public ChannelMessage(Instant at, String channel, String from, String text) {
      this(at, channel, from, text, "", Map.of());
    }
  }

  record ChannelAction(
      Instant at,
      String channel,
      String from,
      String action,
      String messageId,
      Map<String, String> ircv3Tags
  ) implements IrcEvent {
    public ChannelAction {
      messageId = normalizeMessageId(messageId);
      ircv3Tags = normalizeIrcv3Tags(ircv3Tags);
    }

    public ChannelAction(Instant at, String channel, String from, String action) {
      this(at, channel, from, action, "", Map.of());
    }
  }

  record ChannelModeChanged(Instant at, String channel, String by, String details) implements IrcEvent {}
  record ChannelModesListed(Instant at, String channel, String details) implements IrcEvent {}

  record ChannelTopicUpdated(Instant at, String channel, String topic) implements IrcEvent {}

  record PrivateMessage(
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags
  ) implements IrcEvent {
    public PrivateMessage {
      messageId = normalizeMessageId(messageId);
      ircv3Tags = normalizeIrcv3Tags(ircv3Tags);
    }

    public PrivateMessage(Instant at, String from, String text) {
      this(at, from, text, "", Map.of());
    }
  }
  record PrivateAction(
      Instant at,
      String from,
      String action,
      String messageId,
      Map<String, String> ircv3Tags
  ) implements IrcEvent {
    public PrivateAction {
      messageId = normalizeMessageId(messageId);
      ircv3Tags = normalizeIrcv3Tags(ircv3Tags);
    }

    public PrivateAction(Instant at, String from, String action) {
      this(at, from, action, "", Map.of());
    }
  }

  record Notice(
      Instant at,
      String from,
      String target,
      String text,
      String messageId,
      Map<String, String> ircv3Tags
  ) implements IrcEvent {
    public Notice {
      messageId = normalizeMessageId(messageId);
      ircv3Tags = normalizeIrcv3Tags(ircv3Tags);
    }

    public Notice(Instant at, String from, String target, String text) {
      this(at, from, target, text, "", Map.of());
    }
  }

  record WallopsReceived(Instant at, String from, String text) implements IrcEvent {}

  /**
   * A server response line (usually a numeric like 421/433/etc) that doesn't map cleanly onto
   * a higher-level event in IRCafe.
   *
   * <p>This is primarily intended for the per-server <em>Status</em> transcript, so users can
   * see replies to raw commands (and other server numerics) without having to watch the console.
   */
  record ServerResponseLine(
      Instant at,
      int code,
      String message,
      String rawLine,
      String messageId,
      Map<String, String> ircv3Tags
  ) implements IrcEvent {
    public ServerResponseLine {
      messageId = normalizeMessageId(messageId);
      ircv3Tags = normalizeIrcv3Tags(ircv3Tags);
    }

    public ServerResponseLine(Instant at, int code, String message, String rawLine) {
      this(at, code, message, rawLine, "", Map.of());
    }
  }

  /** Start of an IRC channel list (/LIST) response stream. */
  record ChannelListStarted(Instant at, String banner) implements IrcEvent {}

  /** One row from an IRC channel list (/LIST) response stream. */
  record ChannelListEntry(Instant at, String channel, int visibleUsers, String topic) implements IrcEvent {}

  /** End of an IRC channel list (/LIST) response stream. */
  record ChannelListEnded(Instant at, String summary) implements IrcEvent {}

  enum StandardReplyKind {
    FAIL,
    WARN,
    NOTE
  }

  /**
   * IRCv3 standard reply line (FAIL/WARN/NOTE).
   *
   * <p>Format is typically:
   * {@code :server FAIL <command> <code> [context] :description}
   */
  record StandardReply(
      Instant at,
      StandardReplyKind kind,
      String command,
      String code,
      String context,
      String description,
      String rawLine,
      String messageId,
      Map<String, String> ircv3Tags
  ) implements IrcEvent {
    public StandardReply {
      if (kind == null) kind = StandardReplyKind.NOTE;
      command = normalizeToken(command);
      code = normalizeToken(code);
      context = normalizeToken(context);
      description = Objects.toString(description, "").trim();
      rawLine = Objects.toString(rawLine, "").trim();
      messageId = normalizeMessageId(messageId);
      ircv3Tags = normalizeIrcv3Tags(ircv3Tags);
    }

    public StandardReply(
        Instant at,
        StandardReplyKind kind,
        String command,
        String code,
        String context,
        String description,
        String rawLine
    ) {
      this(at, kind, command, code, context, description, rawLine, "", Map.of());
    }
  }

  /** Warns the UI once when IRCv3 {@code server-time} is not negotiated on this connection. */
  record ServerTimeNotNegotiated(Instant at, String message) implements IrcEvent {}

  /**
   * A collected IRCv3 {@code CHATHISTORY} batch.
   *
   */
  record ChatHistoryBatchReceived(
      Instant at,
      String target,
      String batchId,
      List<ChatHistoryEntry> entries
  ) implements IrcEvent {}

  /**
   * A collected ZNC playback window.
   *
   * <p>ZNC playback replays normal PRIVMSG/NOTICE/ACTION lines. We capture them
   * into {@link ChatHistoryEntry} records so the scrollback pipeline can treat
   * them similarly to IRCv3 CHATHISTORY.
   */
  record ZncPlaybackBatchReceived(
      Instant at,
      String target,
      Instant fromInclusive,
      Instant toInclusive,
      List<ChatHistoryEntry> entries
  ) implements IrcEvent {}
  record CtcpRequestReceived(Instant at, String from, String command, String argument, String channel) implements IrcEvent {}

  record AwayStatusChanged(Instant at, boolean away, String message) implements IrcEvent {}

  
  record UserJoinedChannel(Instant at, String channel, String nick) implements IrcEvent {}

  
  record UserPartedChannel(Instant at, String channel, String nick, String reason) implements IrcEvent {}

  /** Local user parted a channel (server-confirmed). */
  record LeftChannel(Instant at, String channel, String reason) implements IrcEvent {}

  /** Another user was kicked from a channel. */
  record UserKickedFromChannel(Instant at, String channel, String nick, String by, String reason) implements IrcEvent {}

  /** Local user was kicked from a channel. */
  record KickedFromChannel(Instant at, String channel, String by, String reason) implements IrcEvent {}

  /**
   * Local user (or invite-notify observer) received an INVITE event.
   *
   * <p>{@code invitee} is usually empty for classic library-level invite events, and set for raw
   * invite-notify lines.
   */
  record InvitedToChannel(
      Instant at,
      String channel,
      String from,
      String invitee,
      String reason,
      boolean inviteNotify
  ) implements IrcEvent {
    public InvitedToChannel {
      channel = Objects.toString(channel, "").trim();
      from = Objects.toString(from, "").trim();
      invitee = Objects.toString(invitee, "").trim();
      reason = Objects.toString(reason, "").trim();
    }

    public InvitedToChannel(Instant at, String channel, String from) {
      this(at, channel, from, "", "", false);
    }
  }

  
  record UserQuitChannel(Instant at, String channel, String nick, String reason) implements IrcEvent {}

  record UserNickChangedChannel(Instant at, String channel, String oldNick, String newNick) implements IrcEvent {}

  record JoinedChannel(Instant at, String channel) implements IrcEvent {}

  /**
   * Server rejected a join attempt (e.g. +r requires NickServ auth).
   *
   * <p>Most servers respond to /JOIN failures using numerics (e.g. 471-477).
   * We surface those as an event so the UI can route the message back to where
   * the user initiated the join (and also to the server status window).
   */
  record JoinFailed(Instant at, String channel, int code, String message) implements IrcEvent {}
  record Error(Instant at, String message, Throwable cause) implements IrcEvent {}

    record NickInfo(
        String nick,
        String prefix,
        String hostmask,
        AwayState awayState,
        String awayMessage,
        AccountState accountState,
        String accountName,
        String realName
    ) { // prefix like "@", "+", "~", etc.
      public NickInfo {
        // Normalize null to UNKNOWN to keep downstream UI / stores simple.
        if (awayState == null) awayState = AwayState.UNKNOWN;
        // Keep empty/blank away messages as null (we use this for tooltip display).
        if (awayMessage != null && awayMessage.isBlank()) awayMessage = null;
        // If the user is not away, we don't keep a reason.
        if (awayState != AwayState.AWAY) awayMessage = null;

        if (accountState == null) accountState = AccountState.UNKNOWN;
        if (accountName != null) {
          accountName = accountName.trim();
          if (accountName.isEmpty() || "*".equals(accountName) || "0".equals(accountName)) accountName = null;
        }
        // Only keep an account name if we are sure they're logged in.
        if (accountState != AccountState.LOGGED_IN) accountName = null;

        if (realName != null) {
          realName = realName.trim();
          if (realName.isEmpty()) realName = null;
        }
      }

      public NickInfo(String nick, String prefix, String hostmask, AwayState awayState, String awayMessage) {
        this(nick, prefix, hostmask, awayState, awayMessage, AccountState.UNKNOWN, null, null);
      }

      public NickInfo(
          String nick,
          String prefix,
          String hostmask,
          AwayState awayState,
          String awayMessage,
          AccountState accountState,
          String accountName
      ) {
        this(nick, prefix, hostmask, awayState, awayMessage, accountState, accountName, null);
      }

      public NickInfo(String nick, String prefix, String hostmask, AwayState awayState) {
        this(nick, prefix, hostmask, awayState, null, AccountState.UNKNOWN, null, null);
      }

      public NickInfo(String nick, String prefix, String hostmask) {
        this(nick, prefix, hostmask, AwayState.UNKNOWN, null, AccountState.UNKNOWN, null, null);
      }
    }

  record NickListUpdated(
      Instant at,
      String channel,
      List<NickInfo> nicks,
      int totalUsers,
      int operatorCount
  ) implements IrcEvent {}

  /** Opportunistically observed a user's hostmask in the wild (e.g. */
record UserHostmaskObserved(Instant at, String channel, String nick, String hostmask) implements IrcEvent {}

  /** Observed IRCv3 CHGHOST for a nick. */
  record UserHostChanged(Instant at, String nick, String user, String host) implements IrcEvent {}

  /** Opportunistically observed a user's away state (e.g. */
record UserAwayStateObserved(Instant at, String nick, AwayState awayState, String awayMessage) implements IrcEvent {
    public UserAwayStateObserved {
      if (awayState == null) awayState = AwayState.UNKNOWN;
      if (awayMessage != null && awayMessage.isBlank()) awayMessage = null;
      // If the user is not away, we don't keep a reason.
      if (awayState != AwayState.AWAY) awayMessage = null;
    }

    
    public UserAwayStateObserved(Instant at, String nick, AwayState awayState) {
      this(at, nick, awayState, null);
    }
  }

  /** Opportunistically observed a user's account state (IRCv3 account-notify / extended-join). */
  record UserAccountStateObserved(Instant at, String nick, AccountState accountState, String accountName) implements IrcEvent {
    public UserAccountStateObserved {
      if (accountState == null) accountState = AccountState.UNKNOWN;
      if (accountName != null && accountName.isBlank()) accountName = null;
      if ("*".equals(accountName) || "0".equals(accountName)) accountName = null;
      if (accountState != AccountState.LOGGED_IN) accountName = null;
    }

    public UserAccountStateObserved(Instant at, String nick, AccountState accountState) {
      this(at, nick, accountState, null);
    }
  }

  /** Observed IRCv3 SETNAME for a nick. */
  record UserSetNameObserved(Instant at, String nick, String realName) implements IrcEvent {}

  /** Observed IRC MONITOR online numerics (RPL_MONONLINE 730). */
  record MonitorOnlineObserved(Instant at, List<String> nicks) implements IrcEvent {
    public MonitorOnlineObserved {
      nicks = normalizeNickList(nicks);
    }
  }

  /** Observed IRC MONITOR offline numerics (RPL_MONOFFLINE 731). */
  record MonitorOfflineObserved(Instant at, List<String> nicks) implements IrcEvent {
    public MonitorOfflineObserved {
      nicks = normalizeNickList(nicks);
    }
  }

  /** Observed IRC MONITOR list numerics (RPL_MONLIST 732). */
  record MonitorListObserved(Instant at, List<String> nicks) implements IrcEvent {
    public MonitorListObserved {
      nicks = normalizeNickList(nicks);
    }
  }

  /** Observed IRC MONITOR list end (RPL_ENDOFMONLIST 733). */
  record MonitorListEnded(Instant at) implements IrcEvent {}

  /** Observed IRC MONITOR list-full error (ERR_MONLISTFULL 734). */
  record MonitorListFull(
      Instant at,
      int limit,
      List<String> nicks,
      String message
  ) implements IrcEvent {
    public MonitorListFull {
      if (limit < 0) limit = 0;
      nicks = normalizeNickList(nicks);
      message = Objects.toString(message, "").trim();
    }
  }

  /** Observed IRCv3 typing indicator (typically from +typing tag). */
  record UserTypingObserved(Instant at, String from, String target, String state) implements IrcEvent {}

  /** Observed IRCv3 read marker signal (draft/read-marker or MARKREAD). */
  record ReadMarkerObserved(Instant at, String from, String target, String marker) implements IrcEvent {}

  /** Observed IRCv3 draft/reply tag. */
  record MessageReplyObserved(Instant at, String from, String target, String replyToMsgId) implements IrcEvent {}

  /** Observed IRCv3 draft/react tag. */
  record MessageReactObserved(Instant at, String from, String target, String reaction, String messageId) implements IrcEvent {}

  /** Observed IRCv3 message redaction tag (for example draft/delete). */
  record MessageRedactionObserved(Instant at, String from, String target, String messageId) implements IrcEvent {}

  /** Observed CAP change line (ACK/NEW/DEL) for a capability. */
  record Ircv3CapabilityChanged(Instant at, String subcommand, String capability, boolean enabled) implements IrcEvent {}

  record WhoisResult(Instant at, String nick, List<String> lines) implements IrcEvent {}

  /**
   * Indicates a WHOIS probe initiated by the client has completed (RPL_ENDOFWHOIS 318).
   *
   * <p>This is used internally for conservative rate limiting / backoff when WHOIS-based enrichment
   * does not yield useful account info (e.g. network does not surface RPL_WHOISACCOUNT 330).
   */
  record WhoisProbeCompleted(
      Instant at,
      String nick,
      boolean sawAwayNumeric,
      boolean sawAccountNumeric,
      boolean whoisAccountNumericSupported
  ) implements IrcEvent {}


  /**
   * Indicates the server supports WHOX (RPL_WHOSPCRPL 354 with extended WHO fields).
   *
   * <p>This is observed via RPL_ISUPPORT (005) token "WHOX". Used internally to decide whether
   * channel-wide enrichment can use WHOX instead of WHOIS for account info.
   */
  record WhoxSupportObserved(Instant at, boolean supported) implements IrcEvent {}

  /**
   * Indicates whether IRCafe's expected WHOX reply schema appears compatible.
   *
   * <p>Some networks advertise WHOX but return a 354 field layout that does not match the fields
   * IRCafe requests (or omits account/flags). When we detect a schema mismatch for IRCafe's
   * channel-scan WHOX token, enrichment can fall back to plain WHO/USERHOST to avoid silently
   * "working" while not actually producing account updates.
   */
  record WhoxSchemaCompatibleObserved(Instant at, boolean compatible, String detail) implements IrcEvent {
    public WhoxSchemaCompatibleObserved {
      if (detail != null && detail.isBlank()) detail = null;
    }
  }

  private static String normalizeMessageId(String raw) {
    String s = (raw == null) ? "" : raw.trim();
    return s;
  }

  private static String normalizeToken(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static Map<String, String> normalizeIrcv3Tags(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      String key = normalizeTagKey(e.getKey());
      if (key.isEmpty()) continue;
      String val = (e.getValue() == null) ? "" : e.getValue();
      out.put(key, val);
    }
    if (out.isEmpty()) return Map.of();
    return java.util.Collections.unmodifiableMap(out);
  }

  private static String normalizeTagKey(String rawKey) {
    String k = (rawKey == null) ? "" : rawKey.trim();
    if (k.startsWith("@")) k = k.substring(1).trim();
    if (k.startsWith("+")) k = k.substring(1).trim();
    if (k.isEmpty()) return "";
    return k.toLowerCase(Locale.ROOT);
  }

  private static List<String> normalizeNickList(List<String> rawNicks) {
    if (rawNicks == null || rawNicks.isEmpty()) return List.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (String raw : rawNicks) {
      String nick = Objects.toString(raw, "").trim();
      if (nick.isEmpty()) continue;
      if (nick.startsWith(":")) nick = nick.substring(1).trim();
      int bang = nick.indexOf('!');
      if (bang > 0) nick = nick.substring(0, bang).trim();
      if (nick.isEmpty()) continue;
      String key = nick.toLowerCase(Locale.ROOT);
      out.putIfAbsent(key, nick);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out.values());
  }
}
