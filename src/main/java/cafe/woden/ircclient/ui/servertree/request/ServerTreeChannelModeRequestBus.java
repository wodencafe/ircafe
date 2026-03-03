package cafe.woden.ircclient.ui.servertree.request;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable.ChannelModeSetRequest;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.Objects;

/** Owns channel-mode request streams and guarded emission for channel targets. */
public final class ServerTreeChannelModeRequestBus {

  private final FlowableProcessor<TargetRef> detailsRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<TargetRef> refreshRequests =
      PublishProcessor.<TargetRef>create().toSerialized();

  private final FlowableProcessor<ChannelModeSetRequest> setRequests =
      PublishProcessor.<ChannelModeSetRequest>create().toSerialized();

  public Flowable<TargetRef> detailsRequests() {
    return detailsRequests.onBackpressureLatest();
  }

  public Flowable<TargetRef> refreshRequests() {
    return refreshRequests.onBackpressureLatest();
  }

  public Flowable<ChannelModeSetRequest> setRequests() {
    return setRequests.onBackpressureLatest();
  }

  public void emitDetailsRequest(TargetRef target) {
    if (!ServerTreeConventions.isChannelTarget(target)) return;
    detailsRequests.onNext(target);
  }

  public void emitRefreshRequest(TargetRef target) {
    if (!ServerTreeConventions.isChannelTarget(target)) return;
    refreshRequests.onNext(target);
  }

  public void emitSetRequest(TargetRef target, String modeSpec) {
    if (!ServerTreeConventions.isChannelTarget(target)) return;
    String spec = Objects.toString(modeSpec, "").trim();
    if (spec.isEmpty()) return;
    setRequests.onNext(new ChannelModeSetRequest(target, spec));
  }
}
