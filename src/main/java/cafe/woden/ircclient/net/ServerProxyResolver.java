package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Resolves the effective proxy configuration for a given server.
 *
 * <p>Precedence:
 *
 * <ol>
 *   <li>If the server has an explicit proxy override (including "enabled=false"), use it.
 *   <li>Otherwise, fall back to the current default proxy configured via {@link NetProxyContext}.
 * </ol>
 */
@Component
public class ServerProxyResolver {

  private final ServerCatalog serverCatalog;

  public ServerProxyResolver(ServerCatalog serverCatalog) {
    this.serverCatalog = serverCatalog;
  }

  /** Returns the current default proxy settings (never null). */
  public IrcProperties.Proxy defaultProxy() {
    return NetProxyContext.settings();
  }

  /** Returns the effective proxy settings for the given server id (never null). */
  public IrcProperties.Proxy effectiveProxy(String serverId) {
    String id = Objects.toString(serverId, "").trim();
    if (!id.isEmpty()) {
      IrcProperties.Proxy override =
          serverCatalog.find(id).map(IrcProperties.Server::proxy).orElse(null);
      if (override != null) {
        return NetProxyContext.normalize(override);
      }
    }
    return defaultProxy();
  }

  /** Returns a resolved proxy plan for the given server id. */
  public ProxyPlan planForServer(String serverId) {
    return ProxyPlan.from(effectiveProxy(serverId));
  }

  /** Returns a resolved proxy plan for the given proxy config. */
  public ProxyPlan plan(IrcProperties.Proxy cfg) {
    return ProxyPlan.from(cfg);
  }
}
