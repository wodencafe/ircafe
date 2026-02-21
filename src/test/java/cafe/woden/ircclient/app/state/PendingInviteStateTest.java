package cafe.woden.ircclient.app.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PendingInviteStateTest {

  @Test
  void collapsesRepeatedInvitesWithinWindow() {
    PendingInviteState state = new PendingInviteState(mock(RuntimeConfigStore.class));

    Instant t0 = Instant.parse("2026-02-21T00:00:00Z");
    PendingInviteState.RecordResult first =
        state.record(t0, "libera", "#ircafe", "alice", "me", "", false);
    PendingInviteState.RecordResult second =
        state.record(t0.plusSeconds(5), "libera", "#ircafe", "alice", "me", "", true);

    assertFalse(first.collapsed());
    assertTrue(second.collapsed());
    assertEquals(first.invite().id(), second.invite().id());
    assertEquals(2, second.invite().repeatCount());
    assertTrue(second.invite().inviteNotify());
  }

  @Test
  void returnsNewestFirstForServer() {
    PendingInviteState state = new PendingInviteState(mock(RuntimeConfigStore.class));

    state.record(Instant.parse("2026-02-21T00:00:00Z"), "libera", "#a", "alice", "me", "", false);
    state.record(Instant.parse("2026-02-21T00:01:00Z"), "libera", "#b", "bob", "me", "", false);

    List<PendingInviteState.PendingInvite> invites = state.listForServer("libera");
    assertEquals(2, invites.size());
    assertEquals("#b", invites.get(0).channel());
    assertEquals("#a", invites.get(1).channel());
  }

  @Test
  void initializesAutoJoinFromRuntimeConfig() {
    RuntimeConfigStore runtime = mock(RuntimeConfigStore.class);
    when(runtime.readInviteAutoJoinEnabled(false)).thenReturn(true);

    PendingInviteState state = new PendingInviteState(runtime);

    assertTrue(state.inviteAutoJoinEnabled());
    state.setInviteAutoJoinEnabled(false);
    assertFalse(state.inviteAutoJoinEnabled());
  }
}
