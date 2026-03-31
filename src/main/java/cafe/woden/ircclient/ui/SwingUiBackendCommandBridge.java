package cafe.woden.ircclient.ui;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.util.Objects;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Shares app-triggered backend command requests across Swing UI adapters. */
@Component
@InterfaceLayer
@Lazy
final class SwingUiBackendCommandBridge {

  private final FlowableProcessor<String> quasselNetworkManagerRequestsFromApp =
      PublishProcessor.<String>create().toSerialized();

  Flowable<String> quasselNetworkManagerRequestsFromApp() {
    return quasselNetworkManagerRequestsFromApp.onBackpressureLatest();
  }

  void openQuasselNetworkManager(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      return;
    }
    quasselNetworkManagerRequestsFromApp.onNext(sid);
  }
}
