package cafe.woden.ircclient.ui;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Shared UI-to-app outbound line stream.
 *
 * <p>This exists so non-input UI surfaces (e.g. clicking a #channel in chat) can emit
 * normal IRC command lines.
 */
@Component
@Lazy
public class OutboundLineBus {

  private final FlowableProcessor<String> lines = PublishProcessor.<String>create().toSerialized();

  public void emit(String line) {
    if (line == null) return;
    String s = line.trim();
    if (s.isEmpty()) return;
    lines.onNext(s);
  }

  public Flowable<String> stream() {
    return lines.onBackpressureBuffer();
  }
}
