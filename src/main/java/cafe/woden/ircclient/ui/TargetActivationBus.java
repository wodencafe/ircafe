package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.TargetRef;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * UI-local event bus for activating a target (for input/status/users) without changing
 * which transcript the main Chat dockable is currently displaying.
 *
 * <p>This is primarily used by pinned chat dockables so you can read multiple targets
 * simultaneously.
 */
@Component
@Lazy
public class TargetActivationBus {

  private final FlowableProcessor<TargetRef> activations =
      PublishProcessor.<TargetRef>create().toSerialized();

  public void activate(TargetRef ref) {
    if (ref == null) return;
    activations.onNext(ref);
  }

  public Flowable<TargetRef> stream() {
    return activations.onBackpressureLatest();
  }
}
