package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.SIGNAL_PROXY_HEARTBEAT;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.SIGNAL_PROXY_HEARTBEAT_REPLY;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.SIGNAL_PROXY_INIT_DATA;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.SignalProxyMessage;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes one session's inbound envelopes and handles native transport signals on the caller's
 * thread. Handler failures propagate to the read loop before readiness is published.
 */
final class QuasselCoreSignalProxyDispatcher {
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreSignalProxyDispatcher.class);
  private final QuasselCoreSession session;
  private final Consumer<SignalProxyMessage> rpc;
  private final Consumer<SignalProxyMessage> sync;

  QuasselCoreSignalProxyDispatcher(
      QuasselCoreSession session,
      Consumer<SignalProxyMessage> rpc,
      Consumer<SignalProxyMessage> sync) {
    this.session = Objects.requireNonNull(session, "session");
    this.rpc = Objects.requireNonNull(rpc, "rpc");
    this.sync = Objects.requireNonNull(sync, "sync");
  }

  void dispatch(SignalProxyMessage message) throws IOException {
    if (message == null) return;
    int requestType = message.requestType();
    log.debug(
        "Quassel inbound signal: serverId={}, requestType={}, className={}, objectName={}, slotName={}, paramCount={}",
        session.serverId,
        requestType,
        message.className(),
        message.objectName(),
        message.slotName(),
        message.params() == null ? 0 : message.params().size());
    switch (requestType) {
      case SIGNAL_PROXY_HEARTBEAT -> replyToHeartbeat(message.params());
      case SIGNAL_PROXY_HEARTBEAT_REPLY -> session.lag.observeReply(message.params());
      case SIGNAL_PROXY_RPC_CALL -> rpc.accept(message);
      case SIGNAL_PROXY_SYNC, SIGNAL_PROXY_INIT_DATA -> {
        session.readiness.observeSync();
        sync.accept(message);
        session.readiness.emitIfReady();
      }
      default -> {}
    }
  }

  private void replyToHeartbeat(List<Object> params) throws IOException {
    if (params == null || params.isEmpty()) return;
    if (!(params.getFirst() instanceof QuasselCoreDatastreamCodec.QtDateTimeValue timestamp))
      return;
    Socket socket = session.socketRef.get();
    if (socket == null) return;
    session.outbound.send(
        socket, (codec, out) -> codec.writeSignalProxyHeartBeatReply(out, timestamp));
  }
}
