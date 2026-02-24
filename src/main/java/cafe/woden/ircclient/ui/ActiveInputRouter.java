package cafe.woden.ircclient.ui;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Tracks which {@link MessageInputPanel} is currently the active typing surface.
 *
 * <p>This is a small helper so future multi-chat layouts can route nick completions (and similar
 * "input-only" updates) to the input the user is actually focused on.
 */
@Component
@Lazy
public class ActiveInputRouter {

  private final AtomicReference<MessageInputPanel> active = new AtomicReference<>();

  public void activate(MessageInputPanel panel) {
    if (panel == null) return;
    active.set(panel);
  }

  public MessageInputPanel active() {
    return active.get();
  }

  public void setNickCompletionsForActive(List<String> nicks) {
    MessageInputPanel p = active.get();
    if (p == null) return;
    p.setNickCompletions(nicks);
  }
}
