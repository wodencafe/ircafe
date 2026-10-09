package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.extractNumericCode;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.serverResponse;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.MessageValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.SignalProxyMessage;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes RPC slots and applies buffer mutations before publishing session observations. */
final class QuasselCoreRpcDispatcher {
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreRpcDispatcher.class);

  interface SessionPort {
    void displayMessage(MessageValue message);

    void observeBuffer(BufferInfoValue merged);

    void emit(IrcEvent event);
  }

  private final QuasselCoreSession session;
  private final QuasselCoreNetworkLifecycleTranslator networks;
  private final SessionPort port;

  QuasselCoreRpcDispatcher(
      QuasselCoreSession session,
      QuasselCoreNetworkLifecycleTranslator networks,
      SessionPort port) {
    this.session = Objects.requireNonNull(session, "session");
    this.networks = Objects.requireNonNull(networks, "networks");
    this.port = Objects.requireNonNull(port, "port");
  }

  void dispatch(SignalProxyMessage message) {
    String slotName = message.slotName();
    List<Object> params = message.params();
    String slot = Objects.toString(slotName, "").trim();
    if (slot.isEmpty()) return;
    log.debug(
        "Received Quassel RPC slot: serverId={}, slot={}, paramCount={}",
        session.serverId,
        slot,
        params == null ? 0 : params.size());
    if (slot.toLowerCase(Locale.ROOT).contains("network")) {
      log.debug(
          "Received Quassel network-related RPC slot: serverId={}, slot={}, params={}",
          session.serverId,
          slot,
          params);
    }

    if ("2displayMsg(Message)".equals(slot)) {
      Object first = (params == null || params.isEmpty()) ? null : params.get(0);
      if (first instanceof QuasselCoreDatastreamCodec.MessageValue msg) {
        port.displayMessage(msg);
      }
      return;
    }

    if ("2displayStatusMsg(QString,QString)".equals(slot)) {
      String network =
          (params == null || params.isEmpty()) ? "" : Objects.toString(params.get(0), "");
      String text =
          (params == null || params.size() < 2) ? "" : Objects.toString(params.get(1), "");
      displayStatusMessage(network, text);
      return;
    }

    if ("2bufferInfoUpdated(BufferInfo)".equals(slot)) {
      Object first = (params == null || params.isEmpty()) ? null : params.get(0);
      if (first instanceof QuasselCoreDatastreamCodec.BufferInfoValue info
          && info.bufferId() >= 0) {
        QuasselCoreDatastreamCodec.BufferInfoValue merged = session.buffers.merge(info);
        port.observeBuffer(merged);
      }
      return;
    }

    if ("2bufferInfoRemoved(BufferInfo)".equals(slot)) {
      Object first = (params == null || params.isEmpty()) ? null : params.get(0);
      if (first instanceof QuasselCoreDatastreamCodec.BufferInfoValue info
          && info.bufferId() >= 0) {
        session.buffers.remove(info.bufferId());
        session.pendingReadMarkers.forgetBuffer(info.bufferId());
      }
    }

    networks.handleRpc(slot, params);
    session.identities.handleRpc(slot, params);
  }

  private void displayStatusMessage(String network, String text) {
    String net = Objects.toString(network, "").trim();
    String rawLine = Objects.toString(text, "").trim();
    log.debug(
        "Quassel display status message: serverId={}, network={}, text={}",
        session.serverId,
        net,
        rawLine);
    String messageLine = rawLine;
    if (messageLine.isEmpty()) {
      messageLine = net;
    } else if (!net.isEmpty() && extractNumericCode(rawLine) == 0) {
      messageLine = net + ": " + messageLine;
    }
    if (messageLine.isEmpty()) return;
    port.emit(serverResponse(Instant.now(), messageLine, rawLine, "", Map.of()));
  }
}
