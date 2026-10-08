package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreLogSummary.summarizeNetworkInfoForLog;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Constructs network commands and sends each through the session's serialized transport. */
final class QuasselCoreNetworkCommandSender {
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreNetworkCommandSender.class);
  private static final String NETWORK_CLASS = "Network";
  private static final String NETWORK_SET_INFO_SLOT = "requestSetNetworkInfo";
  private static final String RPC_CREATE_IDENTITY_SLOT = "2createIdentity(Identity,QVariantMap)";
  private static final String RPC_REMOVE_NETWORK_SLOT = "2removeNetwork(NetworkId)";
  private final String serverId;
  private final String initialNick;
  private final Supplier<Socket> socketSupplier;
  private final QuasselCoreSignalProxySender outbound;

  QuasselCoreNetworkCommandSender(
      String serverId,
      String initialNick,
      Supplier<Socket> socketSupplier,
      QuasselCoreSignalProxySender outbound) {
    this.serverId = serverId;
    this.initialNick = initialNick;
    this.socketSupplier = Objects.requireNonNull(socketSupplier, "socketSupplier");
    this.outbound = Objects.requireNonNull(outbound, "outbound");
  }

  private Socket requireSocket() {
    Socket socket = socketSupplier.get();
    if (socket == null) throw new IllegalStateException("Quassel socket is closed");
    return socket;
  }

  record Update(String networkName, int identityId, boolean enabled) {}

  void syncNetwork(int networkId, String slotName) throws IOException {
    if (networkId < 0) {
      throw new IllegalArgumentException("network id is invalid");
    }
    Socket socket = requireSocket();
    String slot = Objects.toString(slotName, "").trim();
    if (slot.isEmpty()) {
      throw new IllegalArgumentException("slot name is blank");
    }

    log.debug(
        "Sending Quassel network sync call: serverId={}, className={}, objectName={}, slotName={}, paramCount=0",
        serverId,
        NETWORK_CLASS,
        Integer.toString(networkId),
        slot);
    outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxySync(
              out, NETWORK_CLASS, Integer.toString(networkId), slot, List.of());
        });
  }

  void requestInit(String className, String objectName) throws IOException {
    String clazz = Objects.toString(className, "").trim();
    String object = Objects.toString(objectName, "").trim();
    if (clazz.isEmpty()) return;
    Socket socket = socketSupplier.get();
    if (socket == null) return;
    log.debug(
        "Sending Quassel init request: serverId={}, className={}, objectName={}",
        serverId,
        clazz,
        object);
    outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyInitRequest(out, clazz, object, List.of());
        });
  }

  void rpcNetwork(int networkId, String slotName) throws IOException {
    if (networkId < 0) {
      throw new IllegalArgumentException("network id is invalid");
    }
    Socket socket = requireSocket();
    String slot = Objects.toString(slotName, "").trim();
    if (slot.isEmpty()) {
      throw new IllegalArgumentException("rpc slot name is blank");
    }

    outbound.send(
        socket,
        (codec, out) -> {
          // Quassel's NetworkId is a type alias over Int on the wire.
          codec.writeSignalProxyRpcCall(out, slot, List.of(networkId));
        });
  }

  void createIdentity(Map<String, Object> identityPayload) throws IOException {
    Socket socket = requireSocket();
    Map<String, Object> payload =
        identityPayload == null || identityPayload.isEmpty()
            ? QuasselCoreIdentityRequests.defaultPayload(initialNick, null)
            : identityPayload;
    List<Object> params =
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("Identity", payload),
            Collections.emptyMap());
    outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyRpcCall(out, RPC_CREATE_IDENTITY_SLOT, params);
        });
  }

  void createNetwork(
      int identityId,
      QuasselCoreNetworkCreateRequest request,
      String rpcSlot,
      boolean includeAutoJoinChannels,
      BiConsumer<String, Map<String, Object>> beforeSend)
      throws IOException {
    Socket socket = requireSocket();
    String slot = Objects.toString(rpcSlot, "").trim();
    if (slot.isEmpty()) {
      throw new IllegalArgumentException("create-network rpc slot is blank");
    }
    Map<String, Object> networkInfo =
        QuasselCoreNetworkRequests.networkInfoPayload(
            -1, identityId, request, true, /* includeLegacyAliases= */ true);
    ArrayList<Object> params = new ArrayList<>(2);
    params.add(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkInfo", networkInfo));
    if (includeAutoJoinChannels) {
      params.add(request.autoJoinChannels());
    }

    log.debug(
        "Sending Quassel create network RPC: slot={}, networkName={}, host={}, port={}, tls={}, verifyTls={}, autoJoinChannels={}",
        slot,
        request.networkName(),
        request.serverHost(),
        request.serverPort(),
        request.useTls(),
        request.verifyTls(),
        request.autoJoinChannels());
    beforeSend.accept(slot, networkInfo);

    outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyRpcCall(out, slot, params);
        });
  }

  String updateNetwork(
      int networkId, QuasselCoreNetworkUpdateRequest request, Supplier<Update> resolveUpdate)
      throws IOException {
    if (networkId < 0) {
      throw new IllegalArgumentException("network id is invalid");
    }
    Socket socket = requireSocket();

    // Resolve session state only after validation, and keep the captured socket for this write.
    Update update = resolveUpdate.get();
    Map<String, Object> networkInfo =
        QuasselCoreNetworkRequests.networkInfoUpdatePayload(
            networkId, update.identityId(), update.networkName(), request, update.enabled());
    List<Object> params =
        List.of(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkInfo", networkInfo));

    outbound.send(
        socket,
        (codec, out) -> {
          log.debug(
              "Sending Quassel network sync call: serverId={}, className={}, objectName={}, slotName={}, paramCount=1, payload=NetworkInfo(identityId={}, summary={})",
              serverId,
              NETWORK_CLASS,
              Integer.toString(networkId),
              NETWORK_SET_INFO_SLOT,
              update.identityId(),
              summarizeNetworkInfoForLog(networkInfo));
          codec.writeSignalProxySync(
              out, NETWORK_CLASS, Integer.toString(networkId), NETWORK_SET_INFO_SLOT, params);
        });

    return update.networkName();
  }

  void removeNetwork(int networkId) throws IOException {
    Socket socket = requireSocket();
    outbound.send(
        socket,
        (codec, out) -> {
          codec.writeSignalProxyRpcCall(
              out,
              RPC_REMOVE_NETWORK_SLOT,
              List.of(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", networkId)));
        });
  }
}
