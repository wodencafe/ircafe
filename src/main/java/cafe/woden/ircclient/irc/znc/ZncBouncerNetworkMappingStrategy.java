package cafe.woden.ircclient.irc.znc;

import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.bouncer.ResolvedBouncerNetwork;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** ZNC-specific naming and login shaping strategy for bouncer discovery. */
@Component
public class ZncBouncerNetworkMappingStrategy implements BouncerNetworkMappingStrategy {

  public static final String BACKEND_ID = "znc";
  public static final String NETWORKS_GROUP_LABEL = "ZNC Networks";
  public static final String DISCOVERY_CAPABILITY = "znc.in/playback";

  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  @Override
  public String ephemeralIdPrefix() {
    return ZncEphemeralNaming.EPHEMERAL_ID_PREFIX;
  }

  @Override
  public String networksGroupLabel() {
    return NETWORKS_GROUP_LABEL;
  }

  @Override
  public Set<String> capabilityHints() {
    return Set.of(DISCOVERY_CAPABILITY);
  }

  @Override
  public ResolvedBouncerNetwork resolveNetwork(
      IrcProperties.Server bouncer, BouncerDiscoveredNetwork network) {
    ZncNetwork zncNetwork = new ZncNetwork(network.originServerId(), network.displayName(), null);
    ZncEphemeralNaming.Derived d = ZncEphemeralNaming.derive(bouncer, zncNetwork);
    return new ResolvedBouncerNetwork(
        d.serverId(), d.loginUser(), network.displayName(), network.autoConnectName());
  }

  @Override
  public IrcProperties.Server buildEphemeralServer(
      IrcProperties.Server bouncer,
      ResolvedBouncerNetwork resolved,
      List<String> autoJoinChannels) {
    IrcProperties.Server.Sasl sasl = bouncer.sasl();

    // Always set the derived username variant (even if SASL is disabled) so later toggles
    // don't require re-importing.
    IrcProperties.Server.Sasl updatedSasl =
        new IrcProperties.Server.Sasl(
            sasl.enabled(),
            resolved.loginUser(),
            sasl.password(),
            sasl.mechanism(),
            sasl.disconnectOnFailure());

    return new IrcProperties.Server(
        resolved.serverId(),
        bouncer.host(),
        bouncer.port(),
        bouncer.tls(),
        bouncer.serverPassword(),
        bouncer.nick(),
        resolved.loginUser(),
        bouncer.realName(),
        updatedSasl,
        bouncer.nickserv(),
        autoJoinChannels == null ? List.of() : List.copyOf(autoJoinChannels),
        List.of(),
        bouncer.proxy(),
        bouncer.backend());
  }

  @Override
  public String networkDebugId(BouncerDiscoveredNetwork network) {
    return "networkId=" + network.networkId();
  }
}
