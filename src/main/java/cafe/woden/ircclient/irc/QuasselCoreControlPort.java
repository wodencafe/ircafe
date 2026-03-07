package cafe.woden.ircclient.irc;

import io.reactivex.rxjava3.core.Completable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Quassel Core setup/network administration extension operations. */
public interface QuasselCoreControlPort {
  /** Quassel Core initial-setup metadata exposed when the core reports setup is required. */
  record QuasselCoreSetupPrompt(
      String serverId,
      String detail,
      List<String> storageBackends,
      List<String> authenticators,
      Map<String, Object> rawSetupFields) {}

  /** User-provided values for completing Quassel Core initial setup. */
  record QuasselCoreSetupRequest(
      String adminUser,
      String adminPassword,
      String storageBackend,
      String authenticator,
      Map<String, Object> storageSetupData,
      Map<String, Object> authSetupData) {}

  /** Snapshot of a Quassel upstream network as observed from core sync state. */
  record QuasselCoreNetworkSummary(
      int networkId,
      String networkName,
      boolean connected,
      boolean enabled,
      int identityId,
      String serverHost,
      int serverPort,
      boolean useTls,
      Map<String, Object> rawState) {}

  /** User-provided values for creating a Quassel upstream network entry. */
  record QuasselCoreNetworkCreateRequest(
      String networkName,
      String serverHost,
      int serverPort,
      boolean useTls,
      String serverPassword,
      boolean verifyTls,
      Integer identityId,
      List<String> autoJoinChannels) {}

  /** User-provided values for updating a Quassel upstream network entry. */
  record QuasselCoreNetworkUpdateRequest(
      String networkName,
      String serverHost,
      int serverPort,
      boolean useTls,
      String serverPassword,
      boolean verifyTls,
      Integer identityId,
      Boolean enabled) {}

  /**
   * @return true when Quassel Core initial setup is pending for this server.
   */
  default boolean isQuasselCoreSetupPending(String serverId) {
    return false;
  }

  /**
   * @return setup metadata for Quassel Core initial setup, when available.
   */
  default Optional<QuasselCoreSetupPrompt> quasselCoreSetupPrompt(String serverId) {
    return Optional.empty();
  }

  /**
   * Submit Quassel Core initial setup values (admin user/password and storage/auth backend
   * selections).
   */
  default Completable submitQuasselCoreSetup(String serverId, QuasselCoreSetupRequest request) {
    return Completable.error(new UnsupportedOperationException("Quassel core setup not supported"));
  }

  /**
   * @return observed Quassel upstream networks for this server session.
   */
  default List<QuasselCoreNetworkSummary> quasselCoreNetworks(String serverId) {
    return List.of();
  }

  /** Request an upstream Quassel network connect by id or network name/token. */
  default Completable quasselCoreConnectNetwork(String serverId, String networkIdOrName) {
    return Completable.error(
        new UnsupportedOperationException("Quassel network connect not supported"));
  }

  /** Request an upstream Quassel network disconnect by id or network name/token. */
  default Completable quasselCoreDisconnectNetwork(String serverId, String networkIdOrName) {
    return Completable.error(
        new UnsupportedOperationException("Quassel network disconnect not supported"));
  }

  /** Create a new upstream Quassel network entry. */
  default Completable quasselCoreCreateNetwork(
      String serverId, QuasselCoreNetworkCreateRequest request) {
    return Completable.error(
        new UnsupportedOperationException("Quassel network create not supported"));
  }

  /** Update an upstream Quassel network by id or network name/token. */
  default Completable quasselCoreUpdateNetwork(
      String serverId, String networkIdOrName, QuasselCoreNetworkUpdateRequest request) {
    return Completable.error(
        new UnsupportedOperationException("Quassel network update not supported"));
  }

  /** Remove an upstream Quassel network by id or network name/token. */
  default Completable quasselCoreRemoveNetwork(String serverId, String networkIdOrName) {
    return Completable.error(
        new UnsupportedOperationException("Quassel network remove not supported"));
  }

  static QuasselCoreControlPort from(IrcClientService irc) {
    if (irc instanceof QuasselCoreControlPort port) {
      return port;
    }
    return new QuasselCoreControlPort() {};
  }
}
