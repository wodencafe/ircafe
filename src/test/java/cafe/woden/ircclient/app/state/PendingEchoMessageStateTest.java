package cafe.woden.ircclient.app.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.TargetRef;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PendingEchoMessageStateTest {

  private final PendingEchoMessageState state = new PendingEchoMessageState();

  @Test
  void consumeByTargetAndTextRemovesMatchingEntry() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    var pending = state.register(chan, "me", "hello", Instant.parse("2026-02-16T00:00:00Z"));

    var consumed = state.consumeByTargetAndText(chan, "me", "hello");
    assertTrue(consumed.isPresent());
    assertEquals(pending.pendingId(), consumed.get().pendingId());
    assertTrue(state.consumeByTargetAndText(chan, "me", "hello").isEmpty());
  }

  @Test
  void consumePrivateFallbackFindsPrivateTargetOnSameServer() {
    TargetRef chan = new TargetRef("libera", "#ircafe");
    TargetRef pm = new TargetRef("libera", "friend");
    state.register(chan, "me", "hello", Instant.parse("2026-02-16T00:00:00Z"));
    var pendingPm = state.register(pm, "me", "hello", Instant.parse("2026-02-16T00:00:01Z"));

    var consumed = state.consumePrivateFallback("libera", "me", "hello");
    assertTrue(consumed.isPresent());
    assertEquals(pendingPm.pendingId(), consumed.get().pendingId());
    assertFalse(consumed.get().target().isChannel());
  }

  @Test
  void drainServerReturnsAndRemovesAllEntriesForServer() {
    TargetRef a = new TargetRef("libera", "#a");
    TargetRef b = new TargetRef("oftc", "#b");
    state.register(a, "me", "one", Instant.parse("2026-02-16T00:00:00Z"));
    state.register(b, "me", "two", Instant.parse("2026-02-16T00:00:01Z"));

    var drained = state.drainServer("libera");
    assertEquals(1, drained.size());
    assertEquals("#a", drained.get(0).target().target());
    assertTrue(state.consumeByTargetAndText(a, "me", "one").isEmpty());
    assertTrue(state.consumeByTargetAndText(b, "me", "two").isPresent());
  }
}
