package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Initializes {@link NetProxyContext} from configuration properties. */
@Component
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
