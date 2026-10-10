package cafe.woden.ircclient.irc.quassel;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sends buffer input, history requests, and paired read-marker updates through one session
 * transport.
 */
final class QuasselCoreBufferCommandSender {
  private static final String BACKLOG_MANAGER_CLASS = "BacklogManager";
  private static final String BACKLOG_MANAGER_OBJECT = "";
  private static final String BACKLOG_REQUEST_SLOT = "requestBacklog";
  private static final String BUFFER_SYNCER_CLASS = "BufferSyncer";
  private static final String BUFFER_SYNCER_OBJECT = "";
  private static final String BUFFER_SYNCER_MARKER_SLOT = "requestSetMarkerLine";
  private static final String BUFFER_SYNCER_LAST_SEEN_SLOT = "requestSetLastSeenMsg";
  private final Supplier<Socket> socketSupplier;
  private final QuasselCoreSignalProxySender outbound;

  QuasselCoreBufferCommandSender(
      Supplier<Socket> socketSupplier, QuasselCoreSignalProxySender outbound) {
    this.socketSupplier = Objects.requireNonNull(socketSupplier, "socketSupplier");
    this.outbound = Objects.requireNonNull(outbound, "outbound");
  }

  private Socket requireSocket() {
    Socket socket = socketSupplier.get();
    if (socket == null) throw new IllegalStateException("Quassel socket is closed");
    return socket;
  }

  void requestBacklog(
      QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo,
      int firstMsgId,
      int lastMsgId,
      int limit)
      throws IOException {
    Socket socket = requireSocket();
    if (bufferInfo == null || bufferInfo.bufferId() < 0) {
      throw new IllegalArgumentException("buffer info is missing a valid buffer id");
    }

    List<Object> params =
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", bufferInfo.bufferId()),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", firstMsgId),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", lastMsgId),
            limit,
            0);
    outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxySync(
              out, BACKLOG_MANAGER_CLASS, BACKLOG_MANAGER_OBJECT, BACKLOG_REQUEST_SLOT, params);
        });
  }

  void sendInput(QuasselCoreDatastreamCodec.BufferInfoValue bufferInfo, String userInput)
      throws IOException {
    Socket socket = requireSocket();

    outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyRpcCall(
              out, "2sendInput(BufferInfo,QString)", List.of(bufferInfo, userInput));
        });
  }

  void updateReadMarker(int bufferId, long markerMsgId) throws IOException {
    if (bufferId < 0 || markerMsgId <= 0L) return;

    Socket socket = requireSocket();
    int msgId = QuasselCoreHistorySupport.clampMsgId(markerMsgId);
    if (msgId <= 0) return;

    List<Object> params =
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", bufferId),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", msgId));
    outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxySync(
              out, BUFFER_SYNCER_CLASS, BUFFER_SYNCER_OBJECT, BUFFER_SYNCER_MARKER_SLOT, params);
          codec.writeSignalProxySync(
              out, BUFFER_SYNCER_CLASS, BUFFER_SYNCER_OBJECT, BUFFER_SYNCER_LAST_SEEN_SLOT, params);
        });
  }
}
