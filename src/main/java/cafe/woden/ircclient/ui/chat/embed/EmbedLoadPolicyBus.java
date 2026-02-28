package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Holds the active embed/link loading policy snapshot for runtime checks. */
@Component
@Lazy
public class EmbedLoadPolicyBus {

  private volatile RuntimeConfigStore.EmbedLoadPolicySnapshot current;

  public EmbedLoadPolicyBus(RuntimeConfigStore runtimeConfig) {
    RuntimeConfigStore.EmbedLoadPolicySnapshot initial =
        runtimeConfig != null ? runtimeConfig.readEmbedLoadPolicy() : null;
    this.current =
        initial == null ? RuntimeConfigStore.EmbedLoadPolicySnapshot.defaults() : initial;
  }

  public RuntimeConfigStore.EmbedLoadPolicySnapshot get() {
    return current;
  }

  public void set(RuntimeConfigStore.EmbedLoadPolicySnapshot next) {
    RuntimeConfigStore.EmbedLoadPolicySnapshot normalized =
        next == null ? RuntimeConfigStore.EmbedLoadPolicySnapshot.defaults() : next;
    this.current = Objects.requireNonNull(normalized, "normalized");
  }
}
