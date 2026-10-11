package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.looksLikeChannel;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.sanitizeHistoryTarget;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_READ_MARKER;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.MESSAGE_TAGS;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.READ_MARKER;

import cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.QualifiedTarget;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Routes typing and read markers through native Core commands or IRCv3 runtime providers. The
 * service validates the session and capability availability, then schedules writes on IO workers.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class QuasselCoreOutboundSignalCoordinator {
  private static final int BUFFER_CHANNEL = 0x02;
  private static final int BUFFER_QUERY = 0x04;

  @NonNull private final QuasselIrcv3RuntimeSupport ircv3RuntimeSupport;
  @NonNull private final QuasselCoreTargetResolver targets;
  @NonNull private final QuasselCoreInputCoordinator inputs;
  @NonNull private final QuasselCoreStateObservationCoordinator stateObservations;

  void sendTyping(QuasselCoreSession session, String target, String state) throws IOException {
    QualifiedTarget dest = sanitizeHistoryTarget(target);
    List<String> rawLines = ircv3RuntimeSupport.typingRawLines(dest.rawTarget(), state);
    for (String rawLine : rawLines) {
      inputs.sendRaw(session, "send typing", rawLine);
    }
  }

  void sendReadMarker(QuasselCoreSession session, String target, Instant markerAt)
      throws IOException {
    QualifiedTarget requested = sanitizeHistoryTarget(target);
    int typeBitsHint = looksLikeChannel(requested.baseTarget()) ? BUFFER_CHANNEL : BUFFER_QUERY;
    QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo =
        targets.outboundBuffer(session, typeBitsHint, requested);
    stateObservations.observeTargetNetwork(
        session, requested.baseTarget(), bufferInfo.networkId(), true);

    Instant at = markerAt == null ? Instant.now() : markerAt;
    String markerTarget = targets.targetForBuffer(session, bufferInfo, requested.baseTarget());
    if (markerTarget.isEmpty()) {
      markerTarget = requested.rawTarget();
    }
    long markerMsgId = session.history.readMarkerMsgIdForTimestamp(markerTarget, at);
    if (markerMsgId > 0L && bufferInfo.bufferId() >= 0) {
      session.bufferCommands.updateReadMarker(bufferInfo.bufferId(), markerMsgId);
      return;
    }

    if (!hasCapability(session, READ_MARKER, DRAFT_READ_MARKER)) {
      throw new IllegalStateException(
          "Quassel read marker requires an observed message for " + requested.rawTarget());
    }

    List<String> rawLines = ircv3RuntimeSupport.readMarkerRawLines(requested.rawTarget(), at);
    if (rawLines.isEmpty()) {
      throw new IllegalStateException(
          "Read-marker runtime provider did not render a command for " + requested.rawTarget());
    }
    for (String rawLine : rawLines) {
      inputs.sendRaw(session, "send read marker", rawLine);
    }
  }

  boolean isTypingAvailable(QuasselCoreSession session) {
    return hasCapability(session, MESSAGE_TAGS);
  }

  String typingAvailabilityReason(QuasselCoreSession session) {
    if (hasCapability(session, MESSAGE_TAGS)) {
      return "";
    }
    if (!session.features.hasObservedCapabilities()) {
      return "typing support status is not yet available from Quassel backend state";
    }
    if (!hasCapability(session, MESSAGE_TAGS)) {
      return "message-tags not negotiated in Quassel backend network state";
    }
    return "server may be blocking +typing via CLIENTTAGDENY";
  }

  boolean isReadMarkerAvailable(QuasselCoreSession session) {
    return session != null
        && (session.nativeReadMarkerSupportObserved.get()
            || hasCapability(session, READ_MARKER, DRAFT_READ_MARKER));
  }

  private static boolean hasCapability(QuasselCoreSession session, String... capabilities) {
    return session != null && session.features.hasAnyCapability(capabilities);
  }
}
