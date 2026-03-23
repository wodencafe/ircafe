package cafe.woden.ircclient.irc.pircbotx;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Applies tracked CAP ACK/DEL state changes to a connection. */
final class PircbotxCapabilityStateSupport {

  private static final Logger log = LoggerFactory.getLogger(PircbotxCapabilityStateSupport.class);

  private final String serverId;
  private final PircbotxConnectionState conn;

  PircbotxCapabilityStateSupport(String serverId, PircbotxConnectionState conn) {
    this.serverId = serverId;
    this.conn = conn;
  }

  void apply(String capName, boolean enabled, String sourceAction) {
    String normalized = capName.toLowerCase(Locale.ROOT);
    switch (normalized) {
      case "znc.in/playback" ->
          update(conn.zncPlaybackCapAcked, enabled, sourceAction, "znc.in/playback");
      case "batch" -> update(conn.batchCapAcked, enabled, sourceAction, "batch");
      case "draft/chathistory", "chathistory" ->
          update(conn.chatHistoryCapAcked, enabled, sourceAction, normalized);
      case "soju.im/bouncer-networks" ->
          update(
              conn.sojuBouncerNetworksCapAcked, enabled, sourceAction, "soju.im/bouncer-networks");
      case "server-time" -> update(conn.serverTimeCapAcked, enabled, sourceAction, "server-time");
      case "standard-replies" ->
          update(conn.standardRepliesCapAcked, enabled, sourceAction, "standard-replies");
      case "echo-message" ->
          update(conn.echoMessageCapAcked, enabled, sourceAction, "echo-message");
      case "cap-notify" -> update(conn.capNotifyCapAcked, enabled, sourceAction, "cap-notify");
      case "labeled-response" ->
          update(conn.labeledResponseCapAcked, enabled, sourceAction, "labeled-response");
      case "setname" -> update(conn.setnameCapAcked, enabled, sourceAction, "setname");
      case "chghost" -> update(conn.chghostCapAcked, enabled, sourceAction, "chghost");
      case "sts" -> update(conn.stsCapAcked, enabled, sourceAction, "sts");
      case "multiline" ->
          updateWithLimitReset(
              conn.multilineCapAcked,
              conn.multilineMaxBytes,
              conn.multilineMaxLines,
              enabled,
              sourceAction,
              "multiline");
      case "draft/multiline" ->
          updateWithLimitReset(
              conn.draftMultilineCapAcked,
              conn.draftMultilineMaxBytes,
              conn.draftMultilineMaxLines,
              enabled,
              sourceAction,
              "draft/multiline");
      case "draft/reply" -> update(conn.draftReplyCapAcked, enabled, sourceAction, "draft/reply");
      case "draft/channel-context" ->
          update(conn.draftChannelContextCapAcked, enabled, sourceAction, "draft/channel-context");
      case "draft/react" -> update(conn.draftReactCapAcked, enabled, sourceAction, "draft/react");
      case "draft/unreact" ->
          update(conn.draftUnreactCapAcked, enabled, sourceAction, "draft/unreact");
      case "draft/message-edit", "message-edit" ->
          update(conn.draftMessageEditCapAcked, enabled, sourceAction, normalized);
      case "draft/message-redaction", "message-redaction" ->
          update(conn.draftMessageRedactionCapAcked, enabled, sourceAction, normalized);
      case "message-tags" ->
          update(conn.messageTagsCapAcked, enabled, sourceAction, "message-tags");
      case "typing", "draft/typing" ->
          update(conn.typingCapAcked, enabled, sourceAction, normalized);
      case "draft/read-marker", "read-marker" ->
          update(conn.readMarkerCapAcked, enabled, sourceAction, normalized);
      case "monitor" -> update(conn.monitorCapAcked, enabled, sourceAction, "monitor");
      case "extended-monitor", "draft/extended-monitor" ->
          update(conn.extendedMonitorCapAcked, enabled, sourceAction, normalized);
      default -> {
        // Ignore capabilities we don't currently track.
      }
    }
  }

  private void update(
      AtomicBoolean state, boolean enabled, String sourceAction, String capabilityName) {
    boolean previous = state.getAndSet(enabled);
    if (previous != enabled) {
      log.debug(
          "[{}] CAP {}: {} {}",
          serverId,
          sourceAction,
          capabilityName,
          enabled ? "enabled" : "disabled");
    }
  }

  private void updateWithLimitReset(
      AtomicBoolean state,
      AtomicLong maxBytes,
      AtomicLong maxLines,
      boolean enabled,
      String sourceAction,
      String capabilityName) {
    boolean previous = state.getAndSet(enabled);
    if (!enabled) {
      maxBytes.set(0L);
      maxLines.set(0L);
    }
    if (previous != enabled) {
      log.debug(
          "[{}] CAP {}: {} {}",
          serverId,
          sourceAction,
          capabilityName,
          enabled ? "enabled" : "disabled");
    }
  }
}
