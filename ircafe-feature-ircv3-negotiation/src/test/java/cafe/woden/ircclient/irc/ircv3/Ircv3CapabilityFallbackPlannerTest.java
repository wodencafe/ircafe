package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class Ircv3CapabilityFallbackPlannerTest {

  private final Ircv3CapabilityFallbackPlanner planner = new Ircv3CapabilityFallbackPlanner();

  @Test
  void plansMessageTagsBatchAndPublishedDraftHistoryCapability() {
    Ircv3CapabilityFallbackPlanner.Plan plan =
        planner.plan(
            Ircv3CapabilityLine.parse("LS", ":message-tags batch draft/chathistory chathistory"),
            new Ircv3CapabilityFallbackPlanner.State(false, false, false, Set.of()));

    assertTrue(plan.requestMessageTags());
    assertTrue(plan.requestBatch());
    assertEquals("draft/chathistory", plan.historyCapability());
  }

  @Test
  void neverRequestsUnpublishedFinalHistoryNameFromLsOrNew() {
    for (String action : new String[] {"LS", "NEW"}) {
      assertFalse(
          planner
              .plan(
                  Ircv3CapabilityLine.parse(action, ":chathistory"),
                  new Ircv3CapabilityFallbackPlanner.State(false, false, false, Set.of()))
              .requestHistory());
      assertFalse(
          planner
              .plan(
                  Ircv3CapabilityLine.parse(action, ":chathistory draft/chathistory"),
                  new Ircv3CapabilityFallbackPlanner.State(
                      false, false, false, Set.of("draft/chathistory")))
              .requestHistory());
    }
  }

  @Test
  void suppressesAckedAndPendingFallbacks() {
    Ircv3CapabilityFallbackPlanner.Plan plan =
        planner.plan(
            Ircv3CapabilityLine.parse("NEW", ":message-tags batch draft/chathistory"),
            new Ircv3CapabilityFallbackPlanner.State(
                true, false, false, Set.of("BATCH=max-bytes=4096", ":draft/chathistory")));

    assertFalse(plan.requestMessageTags());
    assertFalse(plan.requestBatch());
    assertFalse(plan.requestHistory());
  }

  @Test
  void ignoresNonOfferActionsAndUsesDraftHistoryFallback() {
    Ircv3CapabilityFallbackPlanner.State state =
        new Ircv3CapabilityFallbackPlanner.State(false, false, false, Set.of());

    assertFalse(
        planner
            .plan(Ircv3CapabilityLine.parse("ACK", ":message-tags batch"), state)
            .requestMessageTags());
    assertEquals(
        "draft/chathistory",
        planner
            .plan(Ircv3CapabilityLine.parse("LS", ":draft/chathistory"), state)
            .historyCapability());
  }
}
