package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.List;

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

    IrcEvent.AwayStatusChanged,
    IrcEvent.UserJoinedChannel,
    IrcEvent.UserPartedChannel,
    IrcEvent.UserQuitChannel,
    IrcEvent.UserNickChangedChannel,
    IrcEvent.JoinedChannel,
    IrcEvent.JoinFailed,
    IrcEvent.NickListUpdated,
    IrcEvent.UserHostmaskObserved,
    IrcEvent.UserAwayStateObserved,
    IrcEvent.UserAccountStateObserved,
    IrcEvent.WhoisResult,
    IrcEvent.WhoisProbeCompleted,
    IrcEvent.WhoxSupportObserved,
    IrcEvent.WhoxSchemaCompatibleObserved,
    IrcEvent.ServerTimeNotNegotiated,
    IrcEvent.ChatHistoryBatchReceived,
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

  record ChannelMessage(Instant at, String channel, String from, String text) implements IrcEvent {}

  record ChannelAction(Instant at, String channel, String from, String action) implements IrcEvent {}

  record ChannelModeChanged(Instant at, String channel, String by, String details) implements IrcEvent {}
  record ChannelModesListed(Instant at, String channel, String details) implements IrcEvent {}

  record ChannelTopicUpdated(Instant at, String channel, String topic) implements IrcEvent {}

  record PrivateMessage(Instant at, String from, String text) implements IrcEvent {}
  record PrivateAction(Instant at, String from, String action) implements IrcEvent {}

  record Notice(Instant at, String from, String text) implements IrcEvent {}

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
  record CtcpRequestReceived(Instant at, String from, String command, String argument, String channel) implements IrcEvent {}

  record AwayStatusChanged(Instant at, boolean away, String message) implements IrcEvent {}

  
  record UserJoinedChannel(Instant at, String channel, String nick) implements IrcEvent {}

  
  record UserPartedChannel(Instant at, String channel, String nick, String reason) implements IrcEvent {}

  
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
        String accountName
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
      }

      public NickInfo(String nick, String prefix, String hostmask, AwayState awayState, String awayMessage) {
        this(nick, prefix, hostmask, awayState, awayMessage, AccountState.UNKNOWN, null);
      }

      public NickInfo(String nick, String prefix, String hostmask, AwayState awayState) {
        this(nick, prefix, hostmask, awayState, null, AccountState.UNKNOWN, null);
      }

      public NickInfo(String nick, String prefix, String hostmask) {
        this(nick, prefix, hostmask, AwayState.UNKNOWN, null, AccountState.UNKNOWN, null);
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
}
