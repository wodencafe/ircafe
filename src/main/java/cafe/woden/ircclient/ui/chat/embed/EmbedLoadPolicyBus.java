package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.config.api.EmbedLoadPolicyConfigPort;
import cafe.woden.ircclient.config.api.EmbedLoadPolicyConfigPort.EmbedLoadPolicySnapshot;
import java.util.Objects;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Holds the active embed/link loading policy snapshot for runtime checks. */
@Component
@InterfaceLayer
@Lazy
public class EmbedLoadPolicyBus {

  private volatile EmbedLoadPolicySnapshot current;

  public EmbedLoadPolicyBus(EmbedLoadPolicyConfigPort runtimeConfig) {
    EmbedLoadPolicySnapshot initial =
        runtimeConfig != null ? runtimeConfig.readEmbedLoadPolicy() : null;
    this.current = initial == null ? EmbedLoadPolicySnapshot.defaults() : initial;
  }

  public EmbedLoadPolicySnapshot get() {
    return current;
  }

  public void set(EmbedLoadPolicySnapshot next) {
    EmbedLoadPolicySnapshot normalized = next == null ? EmbedLoadPolicySnapshot.defaults() : next;
    this.current = Objects.requireNonNull(normalized, "normalized");
  }
}
