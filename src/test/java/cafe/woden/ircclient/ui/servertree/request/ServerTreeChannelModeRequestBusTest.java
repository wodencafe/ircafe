package cafe.woden.ircclient.ui.servertree.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable.ChannelModeSetRequest;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ServerTreeChannelModeRequestBusTest {

  @Test
  void emitsDetailsOnlyForChannelTargets() {
    ServerTreeChannelModeRequestBus bus = new ServerTreeChannelModeRequestBus();
    AtomicReference<TargetRef> seen = new AtomicReference<>();
    Disposable sub = bus.detailsRequests().subscribe(seen::set);
    try {
      TargetRef channel = new TargetRef("libera", "#ircafe");
      bus.emitDetailsRequest(TargetRef.notifications("libera"));
      assertNull(seen.get());

      bus.emitDetailsRequest(channel);
      assertEquals(channel, seen.get());
    } finally {
      sub.dispose();
    }
  }

  @Test
  void emitsSetRequestsWithTrimmedModeSpecs() {
    ServerTreeChannelModeRequestBus bus = new ServerTreeChannelModeRequestBus();
    AtomicReference<ChannelModeSetRequest> seen = new AtomicReference<>();
    Disposable sub = bus.setRequests().subscribe(seen::set);
    try {
      TargetRef channel = new TargetRef("libera", "#ircafe");
      bus.emitSetRequest(channel, "   ");
      assertNull(seen.get());

      bus.emitSetRequest(channel, "  +m  ");
      ChannelModeSetRequest emitted = seen.get();
      assertEquals(channel, emitted.target());
      assertEquals("+m", emitted.modeSpec());
    } finally {
      sub.dispose();
    }
  }
}
