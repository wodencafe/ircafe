package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;

/** Reads capability and session availability state from a connection. */
final class PircbotxAvailabilitySupport {

  boolean isEchoMessageAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().echoMessageCapAcked();
  }

  boolean isMessageTagsAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().messageTagsCapAcked();
  }

  boolean isDraftReplyAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().messageTagsCapAcked();
  }

  boolean isDraftReactAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().messageTagsCapAcked();
  }

  boolean isDraftUnreactAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().messageTagsCapAcked();
  }

  boolean isMultilineAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().multilineAvailable();
  }

  long negotiatedMultilineMaxBytes(PircbotxConnectionState connection) {
    return PircbotxMultilineMessageSupport.negotiatedMaxBytes(connection);
  }

  int negotiatedMultilineMaxLines(PircbotxConnectionState connection) {
    long max = PircbotxMultilineMessageSupport.negotiatedMaxLines(connection);
    return toBoundedInt(max);
  }

  boolean isMessageEditAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().draftMessageEditCapAcked();
  }

  boolean isMessageRedactionAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection)
        && connection.capabilitySnapshot().draftMessageRedactionCapAcked();
  }

  boolean isLabeledResponseAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().labeledResponseCapAcked();
  }

  boolean isStandardRepliesAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().standardRepliesCapAcked();
  }

  boolean isMonitorAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.capabilitySnapshot().monitorAvailable();
  }

  int negotiatedMonitorLimit(PircbotxConnectionState connection) {
    if (connection == null) {
      return 0;
    }
    return toBoundedInt(Math.max(0L, connection.capabilitySnapshot().monitorMaxTargets()));
  }

  boolean isZncPlaybackAvailable(PircbotxConnectionState connection) {
    return connection != null && connection.capabilitySnapshot().zncPlaybackCapAcked();
  }

  boolean isZncBouncerDetected(PircbotxConnectionState connection) {
    return connection != null
        && (connection.isZncDetected() || connection.capabilitySnapshot().zncPlaybackCapAcked());
  }

  boolean isSojuBouncerAvailable(PircbotxConnectionState connection) {
    return connection != null && connection.capabilitySnapshot().sojuBouncerNetworksCapAcked();
  }

  private static boolean hasLiveBot(PircbotxConnectionState connection) {
    return connection != null && connection.hasBot();
  }

  private static int toBoundedInt(long value) {
    if (value <= 0L) {
      return 0;
    }
    if (value >= Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) value;
  }
}
