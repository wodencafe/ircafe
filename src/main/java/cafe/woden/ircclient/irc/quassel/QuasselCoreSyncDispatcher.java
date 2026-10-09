package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkId;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.SignalProxyMessage;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes sync/init classes through session translators while preserving their observation order.
 */
final class QuasselCoreSyncDispatcher {
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreSyncDispatcher.class);

  interface SessionPort {
    void applyBufferInfoSnapshot(List<Object> values);

    void observeReadMarkers(String slotName, List<Object> values);

    void receiveBacklog(List<Object> values);

    void observeNetwork(int networkId, String name);

    String qualifyTarget(String target, int networkId);

    void emit(IrcEvent event);
  }

  private final QuasselCoreSession session;
  private final QuasselCoreNetworkSyncTranslator networks;
  private final SessionPort port;

  QuasselCoreSyncDispatcher(
      QuasselCoreSession session, QuasselCoreNetworkSyncTranslator networks, SessionPort port) {
    this.session = Objects.requireNonNull(session, "session");
    this.networks = Objects.requireNonNull(networks, "networks");
    this.port = Objects.requireNonNull(port, "port");
  }

  void dispatch(SignalProxyMessage message) {
    int requestType = message.requestType();
    String className = message.className();
    String objectName = message.objectName();
    String slotName = message.slotName();
    List<Object> params = message.params();
    String classToken = Objects.toString(className, "").trim();
    String slotToken = Objects.toString(slotName, "").trim();
    List<Object> values = params == null ? List.of() : params;
    log.debug(
        "Received Quassel sync/init envelope: serverId={}, requestType={}, className={}, objectName={}, slotName={}, paramCount={}",
        session.serverId,
        requestType,
        classToken,
        objectName,
        slotToken,
        values.size());
    if (classToken.toLowerCase(Locale.ROOT).contains("network")) {
      log.debug(
          "Received network-related sync/init envelope: serverId={}, requestType={}, className={}, objectName={}, slotName={}, params={}",
          session.serverId,
          requestType,
          classToken,
          objectName,
          slotToken,
          values);
    }

    if ("BufferSyncer".equals(classToken)) {
      if (session.nativeReadMarkerSupportObserved.compareAndSet(false, true)) {
        port.emit(new IrcEvent.ConnectionFeaturesUpdated(Instant.now(), "quassel-buffer-syncer"));
      }
      port.applyBufferInfoSnapshot(values);
      port.observeReadMarkers(slotToken, values);
      return;
    }

    if ("BufferViewConfig".equals(classToken)) {
      port.applyBufferInfoSnapshot(values);
      return;
    }

    if ("BacklogManager".equals(classToken)
        && requestType == QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC
        && slotToken.contains("receiveBacklog")) {
      port.receiveBacklog(values);
      return;
    }

    if ("CoreInfo".equals(classToken)) {
      session.identities.handleCoreInfoSync(objectName, slotToken, values);
      return;
    }

    if ("Identity".equals(classToken)) {
      session.identities.handleSync(objectName, values);
      return;
    }

    if ("Network".equals(classToken)) {
      networks.handleProperty(objectName, slotToken, values);
      session.membership.observeNetworkLifecycle(
          objectName, slotToken, values, port::qualifyTarget);
      networks.observeNetworkState(objectName, values);
      networks.observeUnknownState(classToken, objectName, slotToken, values);
      return;
    }

    if ("IrcUser".equals(classToken)) {
      QuasselCoreStateSyncTranslator.userState(
          Instant.now(), objectName, values, port::observeNetwork, port::emit);
      return;
    }

    if ("IrcChannel".equals(classToken)) {
      QuasselCoreStateSyncTranslator.channelState(
          Instant.now(), objectName, values, port::observeNetwork, port::qualifyTarget, port::emit);
      return;
    }

    if ("NetworkInfo".equals(classToken)) {
      networks.observeNetworkInfo(objectName, values);
      return;
    }

    if (classToken.toLowerCase(Locale.ROOT).contains("identity")) {
      session.identities.observeUnknownState(
          values, parseNetworkId(objectName), classToken, objectName, slotToken);
    }
    if (classToken.toLowerCase(Locale.ROOT).contains("network")) {
      networks.observeUnknownState(classToken, objectName, slotToken, values);
    }
  }
}
