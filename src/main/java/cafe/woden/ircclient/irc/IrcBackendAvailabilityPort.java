package cafe.woden.ircclient.irc;

/** Backend-specific availability diagnostics. */
public interface IrcBackendAvailabilityPort {

  /**
   * Human-readable reason why the backend cannot currently provide features for a server.
   *
   * <p>Returns empty when backend availability is normal and capability checks should be
   * interpreted as regular protocol-negotiation outcomes.
   */
  default String backendAvailabilityReason(String serverId) {
    return "";
  }

  static IrcBackendAvailabilityPort from(IrcClientService irc) {
    if (irc instanceof IrcBackendAvailabilityPort port) {
      return port;
    }
    return new IrcBackendAvailabilityPort() {};
  }
}
