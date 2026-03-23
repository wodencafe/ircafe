package cafe.woden.ircclient.irc.pircbotx;

/** Reads capability and session availability state from a connection. */
final class PircbotxAvailabilitySupport {

  boolean isEchoMessageAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.echoMessageCapAcked.get();
  }

  boolean isDraftReplyAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.draftReplyCapAcked.get();
  }

  boolean isDraftReactAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.draftReactCapAcked.get();
  }

  boolean isDraftUnreactAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection)
        && (connection.draftUnreactCapAcked.get() || connection.draftReactCapAcked.get());
  }

  boolean isMultilineAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection)
        && (connection.multilineCapAcked.get() || connection.draftMultilineCapAcked.get());
  }

  long negotiatedMultilineMaxBytes(PircbotxConnectionState connection) {
    return PircbotxMultilineMessageSupport.negotiatedMaxBytes(connection);
  }

  int negotiatedMultilineMaxLines(PircbotxConnectionState connection) {
    long max = PircbotxMultilineMessageSupport.negotiatedMaxLines(connection);
    return toBoundedInt(max);
  }

  boolean isMessageEditAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.draftMessageEditCapAcked.get();
  }

  boolean isMessageRedactionAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.draftMessageRedactionCapAcked.get();
  }

  boolean isLabeledResponseAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.labeledResponseCapAcked.get();
  }

  boolean isStandardRepliesAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection) && connection.standardRepliesCapAcked.get();
  }

  boolean isMonitorAvailable(PircbotxConnectionState connection) {
    return hasLiveBot(connection)
        && (connection.monitorSupported.get() || connection.monitorCapAcked.get());
  }

  int negotiatedMonitorLimit(PircbotxConnectionState connection) {
    if (connection == null) {
      return 0;
    }
    return toBoundedInt(Math.max(0L, connection.monitorMaxTargets.get()));
  }

  boolean isZncPlaybackAvailable(PircbotxConnectionState connection) {
    return connection != null && connection.zncPlaybackCapAcked.get();
  }

  boolean isZncBouncerDetected(PircbotxConnectionState connection) {
    return connection != null
        && (connection.isZncDetected() || connection.zncPlaybackCapAcked.get());
  }

  boolean isSojuBouncerAvailable(PircbotxConnectionState connection) {
    return connection != null && connection.sojuBouncerNetworksCapAcked.get();
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
