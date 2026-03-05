package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.ReplayProcessor;

/** Owns selection broadcast state and suppress-within-block behavior for tree interactions. */
public final class ServerTreeSelectionBroadcastCoordinator {
  private final FlowableProcessor<TargetRef> selections =
      ReplayProcessor.<TargetRef>createWithSize(1).toSerialized();
  private volatile TargetRef lastBroadcastSelectionRef = null;
  private boolean suppressSelectionBroadcast = false;

  public Flowable<TargetRef> selectionStream() {
    return selections.onBackpressureLatest();
  }

  public TargetRef lastBroadcastSelectionRef() {
    return lastBroadcastSelectionRef;
  }

  public boolean suppressSelectionBroadcast() {
    return suppressSelectionBroadcast;
  }

  public void publishSelection(TargetRef ref) {
    if (ref == null) return;
    lastBroadcastSelectionRef = ref;
    selections.onNext(ref);
  }

  public void withSuppressedSelectionBroadcast(Runnable task) {
    if (task == null) return;
    suppressSelectionBroadcast = true;
    try {
      task.run();
    } finally {
      suppressSelectionBroadcast = false;
    }
  }
}
