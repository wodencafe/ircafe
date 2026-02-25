package cafe.woden.ircclient.app.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.app.api.TargetRef;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatHistoryRequestRoutingStateTest {

  private final ChatHistoryRequestRoutingState state = new ChatHistoryRequestRoutingState();

  @Test
  void rememberThenConsumeReturnsPendingRequestOnce() {
    TargetRef origin = new TargetRef("libera", "#ircafe");
    state.remember("libera", "#ircafe", origin, 50, "timestamp=now", Instant.now());

    ChatHistoryRequestRoutingState.PendingRequest first =
        state.consumeIfFresh("libera", "#IRCAFE", Duration.ofMinutes(5));
    ChatHistoryRequestRoutingState.PendingRequest second =
        state.consumeIfFresh("libera", "#ircafe", Duration.ofMinutes(5));

    assertNotNull(first);
    assertEquals(origin, first.originTarget());
    assertEquals(50, first.limit());
    assertEquals("timestamp=now", first.selector());
    assertEquals(ChatHistoryRequestRoutingState.QueryMode.BEFORE, first.queryMode());
    assertNull(second);
  }

  @Test
  void rememberCanStoreExplicitQueryMode() {
    TargetRef origin = new TargetRef("libera", "#ircafe");
    state.remember(
        "libera",
        "#ircafe",
        origin,
        40,
        "msgid=abc",
        Instant.now(),
        ChatHistoryRequestRoutingState.QueryMode.LATEST);

    ChatHistoryRequestRoutingState.PendingRequest pending =
        state.consumeIfFresh("libera", "#ircafe", Duration.ofMinutes(1));

    assertNotNull(pending);
    assertEquals(ChatHistoryRequestRoutingState.QueryMode.LATEST, pending.queryMode());
  }

  @Test
  void consumeIfFreshDropsStaleRequests() {
    TargetRef origin = new TargetRef("libera", "#ircafe");
    Instant old = Instant.now().minus(Duration.ofMinutes(10));
    state.remember("libera", "#ircafe", origin, 25, "msgid=abc", old);

    ChatHistoryRequestRoutingState.PendingRequest pending =
        state.consumeIfFresh("libera", "#ircafe", Duration.ofSeconds(5));

    assertNull(pending);
  }

  @Test
  void clearServerRemovesOnlyMatchingServerEntries() {
    TargetRef libera = new TargetRef("libera", "#ircafe");
    TargetRef oftc = new TargetRef("oftc", "#chat");
    state.remember("libera", "#ircafe", libera, 10, "timestamp=a", Instant.now());
    state.remember("oftc", "#chat", oftc, 10, "timestamp=b", Instant.now());

    state.clearServer("libera");

    assertNull(state.consumeIfFresh("libera", "#ircafe", Duration.ofMinutes(1)));
    assertNotNull(state.consumeIfFresh("oftc", "#chat", Duration.ofMinutes(1)));
  }
}
