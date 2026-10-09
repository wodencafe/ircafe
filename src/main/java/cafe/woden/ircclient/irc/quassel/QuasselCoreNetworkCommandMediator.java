package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreLogSummary.summarizeNetworkInfoForLog;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkCreationCoordinator.RPC_CREATE_NETWORK_SLOT;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkCreationCoordinator.RPC_CREATE_NETWORK_SLOT_LEGACY;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkEnabled;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.parseNetworkIdentityId;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlank;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges network workflows to serialized commands and updates observations after successful sends.
 */
final class QuasselCoreNetworkCommandMediator
    implements QuasselCoreNetworkConnectPreflight.Commands,
        QuasselCoreNetworkCreationCoordinator.Commands {
  private static final Logger log =
      LoggerFactory.getLogger(QuasselCoreNetworkCommandMediator.class);
  private final QuasselCoreSession session;
  private final IntSupplier resolveIdentity;
  private final BiConsumer<Integer, String> networkObserved;

  QuasselCoreNetworkCommandMediator(
      QuasselCoreSession session,
      IntSupplier resolveIdentity,
      BiConsumer<Integer, String> networkObserved) {
    this.session = Objects.requireNonNull(session, "session");
    this.resolveIdentity = Objects.requireNonNull(resolveIdentity, "resolveIdentity");
    this.networkObserved = Objects.requireNonNull(networkObserved, "networkObserved");
  }

  @Override
  public void requestNetworkInitState(int networkId) throws Exception {
    if (networkId < 0) return;
    session.networkCommands.requestInit("Network", Integer.toString(networkId));
  }

  @Override
  public void createIdentity(Map<String, Object> payload) throws Exception {
    session.networkCommands.createIdentity(payload);
  }

  @Override
  public void createNetwork(int identityId, QuasselCoreNetworkCreateRequest request)
      throws Exception {
    createNetwork(identityId, request, false);
  }

  @Override
  public void createNetwork(int identityId, QuasselCoreNetworkCreateRequest request, boolean legacy)
      throws Exception {
    session.networkCommands.createNetwork(
        identityId,
        request,
        legacy ? RPC_CREATE_NETWORK_SLOT_LEGACY : RPC_CREATE_NETWORK_SLOT,
        !legacy,
        this::logCreateNetworkContext);
    if (!legacy) session.networks.rememberCreatedName(request.networkName());
  }

  @Override
  public void updateNetwork(int networkId, QuasselCoreNetworkUpdateRequest request)
      throws Exception {
    String networkName =
        session.networkCommands.updateNetwork(
            networkId, request, () -> resolveNetworkUpdate(networkId, request));
    networkObserved.accept(networkId, networkName);
  }

  private void logCreateNetworkContext(String slot, Map<String, Object> networkInfo) {
    QuasselCoreAuthHandshake.AuthResult auth = session.authResult.get();
    log.debug(
        "Create-network session context: serverId={}, slot={}, knownNetworkIds={}, knownIdentityIds={}, authPrimaryNetworkId={}, authNetworkIds={}, authInitialBufferCount={}, networkStateKeys={}, networkDisplayKeys={}, networkTokenKeys={}, identityStateKeys={}, identityNames={}, payload={}",
        session.serverId,
        slot,
        session.networks.knownIds(auth, session.buffers.values()),
        session.identities.knownIds(),
        auth == null ? -1 : auth.primaryNetworkId(),
        auth == null ? List.of() : auth.networkIds(),
        auth == null || auth.initialBuffers() == null ? 0 : auth.initialBuffers().size(),
        session.networks.stateIds(),
        session.networks.displayNames().keySet(),
        session.networks.tokenIds(),
        session.identities.stateIds(),
        session.identities.names(),
        summarizeNetworkInfoForLog(networkInfo));
  }

  private QuasselCoreNetworkCommandSender.Update resolveNetworkUpdate(
      int networkId, QuasselCoreNetworkUpdateRequest request) {
    Map<String, Object> existing = session.networks.state(networkId);
    String networkName =
        firstNonBlank(
            request.networkName(),
            mapValueIgnoreCase(existing, "networkName"),
            mapValueIgnoreCase(existing, "networkname"),
            mapValueIgnoreCase(existing, "name"),
            session.networks.displayNames().get(networkId));
    if (networkName.isBlank()) {
      networkName = "network-" + networkId;
    }

    int identityId =
        request.identityId() != null
            ? request.identityId().intValue()
            : parseNetworkIdentityId(existing);
    if (identityId < 0) {
      identityId = resolveIdentity.getAsInt();
    }

    boolean enabled =
        request.enabled() != null
            ? request.enabled().booleanValue()
            : parseNetworkEnabled(existing);

    return new QuasselCoreNetworkCommandSender.Update(networkName, identityId, enabled);
  }
}
