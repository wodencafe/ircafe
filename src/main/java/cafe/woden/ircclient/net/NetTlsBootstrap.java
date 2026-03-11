package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import jakarta.annotation.PostConstruct;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Initializes {@link NetTlsContext} from configuration properties. */
@Component
@ApplicationLayer
public class NetTlsBootstrap {

  private final IrcProperties props;

  public NetTlsBootstrap(IrcProperties props) {
    this.props = props;
  }

  @PostConstruct
  public void init() {
    if (props == null || props.client() == null) {
      NetTlsContext.configure(false);
      return;
    }
    NetTlsContext.configure(props.client().tls());
  }
}
