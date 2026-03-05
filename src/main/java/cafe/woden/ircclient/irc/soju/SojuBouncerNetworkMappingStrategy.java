package cafe.woden.ircclient.irc.soju;

import cafe.woden.ircclient.bouncer.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.bouncer.ResolvedBouncerNetwork;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/** Soju-specific naming and login shaping strategy for bouncer discovery. */
@Component
public class SojuBouncerNetworkMappingStrategy implements BouncerNetworkMappingStrategy {

  public static final String BACKEND_ID = "soju";

  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  @Override
  public ResolvedBouncerNetwork resolveNetwork(
      IrcProperties.Server bouncer, BouncerDiscoveredNetwork network) {
    SojuNetwork sojuNetwork =
        new SojuNetwork(
            network.originServerId(),
            network.networkId(),
            network.displayName(),
            network.attributes());
    SojuEphemeralNaming.Derived d = SojuEphemeralNaming.derive(bouncer, sojuNetwork);
    return new ResolvedBouncerNetwork(
        d.serverId(), d.loginUser(), d.networkName(), network.autoConnectName());
  }

  @Override
  public IrcProperties.Server buildEphemeralServer(
      IrcProperties.Server bouncer,
      ResolvedBouncerNetwork resolved,
      List<String> autoJoinChannels) {
    IrcProperties.Server.Sasl sasl = bouncer.sasl();

    // Always set the username variant (even if SASL is disabled) so later toggles don't require
    // re-importing.
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
    return "netId=" + network.networkId();
  }
}
