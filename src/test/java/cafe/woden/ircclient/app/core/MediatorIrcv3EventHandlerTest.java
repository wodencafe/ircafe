package cafe.woden.ircclient.app.core;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.TargetRef;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MediatorIrcv3EventHandlerTest {

  private final UiPort ui = mock(UiPort.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final MediatorIrcv3EventHandler handler =
      new MediatorIrcv3EventHandler(ui, targetCoordinator);
  private final MediatorIrcv3EventHandler.Callbacks callbacks =
      (sid, target, from, status) -> new TargetRef(sid, target);

  @Test
  void messageReactObservedAppliesReactionWithoutNegotiatedCapability() {
    Instant at = Instant.parse("2026-04-01T21:10:00Z");
    TargetRef status = new TargetRef("libera", "status");
    IrcEvent.MessageReactObserved event =
        new IrcEvent.MessageReactObserved(at, "alice", "#ircafe", ":wave:", "msg-1");

    handler.handleMessageReactObserved(callbacks, "libera", status, event);

    verify(ui)
        .applyMessageReaction(new TargetRef("libera", "#ircafe"), at, "alice", "msg-1", ":wave:");
  }

  @Test
  void messageUnreactObservedRemovesReactionWithoutNegotiatedCapability() {
    Instant at = Instant.parse("2026-04-01T21:11:00Z");
    TargetRef status = new TargetRef("libera", "status");
    IrcEvent.MessageUnreactObserved event =
        new IrcEvent.MessageUnreactObserved(at, "alice", "#ircafe", ":wave:", "msg-1");

    handler.handleMessageUnreactObserved(callbacks, "libera", status, event);

    verify(ui)
        .removeMessageReaction(new TargetRef("libera", "#ircafe"), at, "alice", "msg-1", ":wave:");
  }

  @Test
  void messageRedactionObservedAppliesRedactionWithoutNegotiatedCapability() {
    Instant at = Instant.parse("2026-04-01T21:12:00Z");
    TargetRef status = new TargetRef("libera", "status");
    IrcEvent.MessageRedactionObserved event =
        new IrcEvent.MessageRedactionObserved(at, "alice", "#ircafe", "msg-1");

    handler.handleMessageRedactionObserved(callbacks, "libera", status, event);

    verify(ui)
        .applyMessageRedaction(
            new TargetRef("libera", "#ircafe"),
            at,
            "alice",
            "msg-1",
            "",
            Map.of("draft/delete", "msg-1"));
  }
}
