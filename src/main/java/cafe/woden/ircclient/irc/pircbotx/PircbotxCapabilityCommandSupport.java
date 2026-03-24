package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryCommandBuilder;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles IRCv3 capability-gated outbound commands for a live IRC connection. */
final class PircbotxCapabilityCommandSupport {

  private static final Logger log = LoggerFactory.getLogger(PircbotxCapabilityCommandSupport.class);
  private static final DateTimeFormatter MARKREAD_TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  void sendTyping(
      String serverId, PircbotxConnectionState connection, String target, String state) {
    if (connection == null || !connection.hasBot()) {
      throw new IllegalStateException("Not connected: " + serverId);
    }
    if (!isTypingAvailable(connection)) {
      String reason = typingAvailabilityReason(connection);
      String suffix = (reason == null || reason.isBlank()) ? "" : (" (" + reason + ")");
      throw new IllegalStateException(
          "Typing indicators not available (requires message-tags and server allowing +typing)"
              + suffix
              + ": "
              + serverId);
    }

    String normalizedState = normalizeTypingState(state);
    if (normalizedState.isEmpty()) {
      return;
    }

    String dest = sanitizeTarget(target);
    String line = "@+typing=" + normalizedState + " TAGMSG " + dest;
    if (log.isDebugEnabled()) {
      log.debug("[{}] -> typing {} TAGMSG {}", serverId, normalizedState, dest);
    }
    requireConnectedBot(serverId, connection).sendRaw().rawLine(line);
  }

  void sendReadMarker(
      String serverId, PircbotxConnectionState connection, String target, Instant markerAt) {
    if (connection == null || !connection.capabilitySnapshot().readMarkerCapAcked()) {
      throw new IllegalStateException(
          "read-marker capability not negotiated (requires read-marker or draft/read-marker): "
              + serverId);
    }

    String dest = sanitizeTarget(target);
    Instant at = markerAt == null ? Instant.now() : markerAt;
    String ts = MARKREAD_TS_FMT.format(at);
    requireConnectedBot(serverId, connection)
        .sendRaw()
        .rawLine("MARKREAD " + dest + " timestamp=" + ts);
  }

  void requestChatHistoryBefore(
      String serverId,
      PircbotxConnectionState connection,
      String target,
      String selector,
      int limit) {
    ensureChatHistoryNegotiated(serverId, connection);
    requireConnectedBot(serverId, connection)
        .sendRaw()
        .rawLine(Ircv3ChatHistoryCommandBuilder.buildBefore(target, selector, limit));
  }

  void requestChatHistoryLatest(
      String serverId,
      PircbotxConnectionState connection,
      String target,
      String selector,
      int limit) {
    ensureChatHistoryNegotiated(serverId, connection);
    requireConnectedBot(serverId, connection)
        .sendRaw()
        .rawLine(Ircv3ChatHistoryCommandBuilder.buildLatest(target, selector, limit));
  }

  void requestChatHistoryBetween(
      String serverId,
      PircbotxConnectionState connection,
      String target,
      String startSelector,
      String endSelector,
      int limit) {
    ensureChatHistoryNegotiated(serverId, connection);
    requireConnectedBot(serverId, connection)
        .sendRaw()
        .rawLine(
            Ircv3ChatHistoryCommandBuilder.buildBetween(target, startSelector, endSelector, limit));
  }

  void requestChatHistoryAround(
      String serverId,
      PircbotxConnectionState connection,
      String target,
      String selector,
      int limit) {
    ensureChatHistoryNegotiated(serverId, connection);
    requireConnectedBot(serverId, connection)
        .sendRaw()
        .rawLine(Ircv3ChatHistoryCommandBuilder.buildAround(target, selector, limit));
  }

  boolean isTypingAvailable(PircbotxConnectionState connection) {
    if (connection == null || !connection.hasBot()) {
      return false;
    }
    return connection.capabilitySnapshot().typingAvailable();
  }

  String typingAvailabilityReason(PircbotxConnectionState connection) {
    if (connection == null) {
      return "no connection state";
    }
    if (!connection.hasBot()) {
      return "not connected";
    }

    PircbotxConnectionState.CapabilitySnapshot caps = connection.capabilitySnapshot();
    if (!caps.messageTagsCapAcked()) {
      return "message-tags not negotiated";
    }

    if (!caps.typingCapAcked() && !caps.typingClientTagPolicyKnown()) {
      return "typing capability not negotiated";
    }
    if (!caps.typingCapAcked() && !caps.typingClientTagAllowed()) {
      return "server denies +typing via CLIENTTAGDENY";
    }
    return "";
  }

  boolean isReadMarkerAvailable(PircbotxConnectionState connection) {
    return connection != null
        && connection.hasBot()
        && connection.capabilitySnapshot().readMarkerCapAcked();
  }

  boolean isChatHistoryAvailable(PircbotxConnectionState connection) {
    return connection != null && connection.capabilitySnapshot().chatHistoryAvailable();
  }

  private void ensureChatHistoryNegotiated(String serverId, PircbotxConnectionState connection) {
    PircbotxConnectionState.CapabilitySnapshot caps =
        connection == null ? null : connection.capabilitySnapshot();
    if (caps == null || !caps.chatHistoryCapAcked()) {
      throw new IllegalStateException(
          "CHATHISTORY not negotiated (chathistory or draft/chathistory): " + serverId);
    }
    if (!caps.batchCapAcked()) {
      throw new IllegalStateException(
          "CHATHISTORY requires IRCv3 batch to be negotiated: " + serverId);
    }
  }

  private static PircBotX requireConnectedBot(String serverId, PircbotxConnectionState connection) {
    PircBotX bot = connection == null ? null : connection.currentBot();
    if (bot == null) {
      throw new IllegalStateException("Not connected: " + serverId);
    }
    return bot;
  }

  private static String sanitizeTarget(String target) {
    String renderedTarget = Objects.toString(target, "").trim();
    if (renderedTarget.isEmpty()) {
      throw new IllegalArgumentException("target is blank");
    }
    if (renderedTarget.startsWith("#") || renderedTarget.startsWith("&")) {
      return PircbotxUtil.sanitizeChannel(renderedTarget);
    }
    return PircbotxUtil.sanitizeNick(renderedTarget);
  }

  private static String normalizeTypingState(String state) {
    String renderedState = Objects.toString(state, "").trim().toLowerCase(Locale.ROOT);
    if (renderedState.isEmpty()) {
      return "";
    }
    return switch (renderedState) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "";
    };
  }
}
