package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreBacklogTranslator.isHistoryTextMessage;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.extractNick;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.MessageValue;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Coordinates history, IRCv3 interception, and native display translation for one read loop. */
final class QuasselCoreInboundMessageCoordinator {
  private static final int MESSAGE_FLAG_BACKLOG = 0x80;

  interface SessionPort {
    BufferInfoValue resolveBuffer(BufferInfoValue incoming);

    String currentNick(int networkId);

    boolean isSelfNick(String nick, int networkId);

    void observeTargetNetwork(String target, int networkId);

    void observeNick(int networkId, Instant at, String nick);

    void emit(IrcEvent event);
  }

  private final QuasselCoreSession session;
  private final QuasselCoreReadMarkerCoordinator markers;
  private final QuasselCoreTargetResolver targets;
  private final QuasselIrcv3RuntimeSupport ircv3RuntimeSupport;
  private final QuasselCoreIrcv3InboundTranslator inboundTranslator;
  private final SessionPort port;

  QuasselCoreInboundMessageCoordinator(
      QuasselCoreSession session,
      QuasselCoreReadMarkerCoordinator markers,
      QuasselCoreTargetResolver targets,
      QuasselIrcv3RuntimeSupport ircv3RuntimeSupport,
      SessionPort port) {
    this.session = Objects.requireNonNull(session, "session");
    this.markers = Objects.requireNonNull(markers, "markers");
    this.targets = Objects.requireNonNull(targets, "targets");
    this.ircv3RuntimeSupport = Objects.requireNonNull(ircv3RuntimeSupport, "ircv3RuntimeSupport");
    this.inboundTranslator = new QuasselCoreIrcv3InboundTranslator(ircv3RuntimeSupport);
    this.port = Objects.requireNonNull(port, "port");
  }

  void handle(MessageValue message) {
    if (message == null) return;

    BufferInfoValue bufferInfo = port.resolveBuffer(message.bufferInfo());
    Instant at =
        message.timestampEpochSeconds() > 0
            ? Instant.ofEpochSecond(message.timestampEpochSeconds())
            : Instant.now();
    String messageId = message.messageId() > 0 ? Long.toString(message.messageId()) : "";
    String senderHostmask = Objects.toString(message.sender(), "").trim();
    String from = extractNick(senderHostmask);
    int networkId = bufferInfo == null ? -1 : bufferInfo.networkId();
    String fromDisplay = from.isEmpty() ? port.currentNick(networkId) : from;
    String content = Objects.toString(message.content(), "");
    QuasselCoreIrcEnvelope ircEnvelope = QuasselCoreIrcEnvelope.parse(content, ircv3RuntimeSupport);
    Map<String, String> ircv3Tags = ircEnvelope.ircv3Tags();
    String payloadText = ircEnvelope.payloadText(content);
    String target = targets.targetForBuffer(session, bufferInfo, fromDisplay);
    String historyTarget = targets.targetForBuffer(session, bufferInfo, fromDisplay);
    int historyNetworkId = networkId;
    port.observeTargetNetwork(historyTarget, historyNetworkId);
    // Pending markers and provider observers must see this message's history before delivery.
    markers.observeHistory(historyTarget, message.messageId(), at);
    int typeBits = message.typeBits();
    String fallbackSignalTarget = target;
    Function<String, String> resolveSignalTarget =
        rawTarget ->
            targets.signalTarget(session, fromDisplay, fallbackSignalTarget, networkId, rawTarget);
    Consumer<IrcEvent> emit = port::emit;
    QuasselCoreIrcv3InboundTranslator.Observation observation =
        new QuasselCoreIrcv3InboundTranslator.Observation(at, fromDisplay, ircEnvelope, messageId);
    inboundTranslator.observeTags(observation, resolveSignalTarget, emit);

    String envelopeCommand = ircEnvelope.command();
    if ("CAP".equals(envelopeCommand)) {
      emitCapabilityChangesFromCapLine(at, networkId, ircEnvelope);
    }
    if (inboundTranslator.handleCommand(observation, resolveSignalTarget, emit)) {
      return;
    }
    if ("TAGMSG".equals(envelopeCommand) && payloadText.isBlank()) {
      return;
    }

    if (inboundTranslator.handleMonitor(
        at,
        content,
        support -> {
          int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId();
          session.features.observeMonitor(resolvedNetworkId, support.supported(), support.limit());
        },
        emit)) {
      return;
    }

    if (isBacklogMessage(message.flags()) && isHistoryTextMessage(typeBits)) {
      emitBacklogHistoryBatch(at, target, message, messageId);
      return;
    }

    QuasselCoreDisplayMessageTranslator.translate(
        new QuasselCoreDisplayMessageTranslator.Observation(
            at,
            bufferInfo,
            target,
            fromDisplay,
            senderHostmask,
            payloadText,
            messageId,
            ircv3Tags,
            message),
        new QuasselCoreDisplayMessageTranslator.SessionPort() {
          @Override
          public boolean isSelfNick(String nick) {
            return port.isSelfNick(nick, networkId);
          }

          @Override
          public String currentNick() {
            return port.currentNick(networkId);
          }

          @Override
          public String queryTarget() {
            return targets.targetForBuffer(session, bufferInfo, fromDisplay);
          }

          @Override
          public void observeJoin(Instant joinedAt, String channel) {
            session.membership.observeJoin(joinedAt, channel, networkId);
          }

          @Override
          public void leave(String channel) {
            session.membership.leave(channel, networkId);
          }

          @Override
          public void observeNick(Instant changedAt, String nick) {
            port.observeNick(networkId, changedAt, nick);
          }
        },
        emit);
  }

  private void emitCapabilityChangesFromCapLine(
      Instant at, int networkId, QuasselCoreIrcEnvelope envelope) {
    int resolvedNetworkId = networkId >= 0 ? networkId : firstKnownNetworkId();
    session.features.observeCapLine(at, resolvedNetworkId, envelope);
  }

  private void emitBacklogHistoryBatch(
      Instant at, String targetFromBuffer, MessageValue message, String messageId) {
    IrcEvent.ChatHistoryBatchReceived batch =
        session.backlog.display(
            at,
            targetFromBuffer,
            message,
            messageId,
            (info, from) -> targets.targetForBuffer(session, info, from));
    if (batch != null) port.emit(batch);
  }

  private static boolean isBacklogMessage(int flags) {
    return (flags & MESSAGE_FLAG_BACKLOG) != 0;
  }

  private int firstKnownNetworkId() {
    return session.networks.firstKnownNetworkId(session.authResult.get(), session.buffers.values());
  }
}
