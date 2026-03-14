package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Initializes {@link NetHeartbeatContext} from configuration properties. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class NetHeartbeatBootstrap {

  private final IrcProperties props;

  @PostConstruct
  public void init() {
    if (props == null || props.client() == null) {
      NetHeartbeatContext.configure(null);
      return;
    }
    NetHeartbeatContext.configure(props.client().heartbeat());
  }
}
