package cafe.woden.ircclient.irc.znc;

import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.bouncer.AbstractBouncerEphemeralNetworkImporter;
import cafe.woden.ircclient.irc.bouncer.ResolvedBouncerNetwork;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Creates/updates ephemeral server entries for ZNC-discovered networks.
 *
 * <p>These servers are not persisted to the runtime YAML; they exist only for the duration of the
 * bouncer control session.
 */
@Component
public class ZncEphemeralNetworkImporter extends AbstractBouncerEphemeralNetworkImporter<ZncNetwork> {

  private static final Logger log = LoggerFactory.getLogger(ZncEphemeralNetworkImporter.class);

  public ZncEphemeralNetworkImporter(
      ServerRegistry serverRegistry,
      EphemeralServerRegistry ephemeralServers,
      ZncAutoConnectStore autoConnect,
      RuntimeConfigStore runtimeConfig,
      @Lazy IrcClientService irc) {
    super(
        log,
        "znc",
        ZncEphemeralNaming.EPHEMERAL_ID_PREFIX,
        serverRegistry,
        ephemeralServers,
        autoConnect,
        runtimeConfig,
        irc);
  }

  @Override
  protected String bouncerServerId(ZncNetwork network) {
    return network.bouncerServerId();
  }

  @Override
  protected String networkDisplayName(ZncNetwork network) {
    return network.name();
  }

  @Override
  protected ResolvedBouncerNetwork resolveNetwork(IrcProperties.Server bouncer, ZncNetwork network) {
    ZncEphemeralNaming.Derived d = ZncEphemeralNaming.derive(bouncer, network);
    return new ResolvedBouncerNetwork(d.serverId(), d.loginUser(), network.name(), network.name());
  }

  @Override
  protected IrcProperties.Server buildEphemeralServer(
      IrcProperties.Server bouncer, ResolvedBouncerNetwork resolved, List<String> autoJoinChannels) {
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
        autoJoinChannels == null ? List.of() : List.copyOf(autoJoinChannels),
        List.of(),
        bouncer.proxy());
  }
}
