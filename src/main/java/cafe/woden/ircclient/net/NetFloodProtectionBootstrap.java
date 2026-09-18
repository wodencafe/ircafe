package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Initializes outbound pacing preferences from the runtime configuration. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class NetFloodProtectionBootstrap {
  private final IrcProperties props;

  @PostConstruct
  public void init() {
    NetFloodProtectionContext.configure(
        props.client() == null ? null : props.client().floodProtection());
  }
}
