package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Initializes {@link NetProxyContext} from configuration properties. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class NetProxyBootstrap {

  private final IrcProperties props;

  @PostConstruct
  public void init() {
    NetProxyContext.configure(props.client().proxy());
  }
}
