package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import jakarta.annotation.PostConstruct;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Initializes {@link NetProxyContext} from configuration properties. */
@Component
@ApplicationLayer
public class NetProxyBootstrap {

  private final IrcProperties props;

  public NetProxyBootstrap(IrcProperties props) {
    this.props = props;
  }

  @PostConstruct
  public void init() {
    NetProxyContext.configure(props.client().proxy());
  }
}
