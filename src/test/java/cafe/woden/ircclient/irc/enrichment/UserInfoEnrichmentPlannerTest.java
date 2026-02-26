package cafe.woden.ircclient.irc.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentPlanner.PlannedCommand;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentPlanner.ProbeKind;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentPlanner.Settings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserInfoEnrichmentPlannerTest {

  private final UserInfoEnrichmentPlanner planner = new UserInfoEnrichmentPlanner();

  @Test
  void userhostCommandsAreBatchedByConfiguredNickLimit() {
    Instant now = Instant.parse("2026-02-26T00:00:00Z");
    Settings cfg = settings(2, Duration.ofMinutes(1), false);

    planner.enqueueUserhost("libera", List.of("alice", "bob", "carol"));

    PlannedCommand cmd = planner.pollNext("libera", now, cfg).orElseThrow();
    assertEquals(ProbeKind.USERHOST, cmd.kind());
    assertEquals(List.of("alice", "bob"), cmd.nicks());
    assertEquals("USERHOST alice bob", cmd.rawLine());
  }

  @Test
  void prioritizedWhoisNicksArePolledBeforeExistingQueueEntries() {
    Instant now = Instant.parse("2026-02-26T00:00:00Z");
    Settings cfg = settings(5, Duration.ofMinutes(1), true);

    planner.enqueueWhois("libera", List.of("old1", "old2"));
    planner.enqueueWhoisPrioritized("libera", List.of("new1"));

    PlannedCommand cmd = planner.pollNext("libera", now, cfg).orElseThrow();
    assertEquals(ProbeKind.WHOIS, cmd.kind());
    assertEquals(List.of("new1"), cmd.nicks());
  }

  @Test
  void nextReadyDelayReflectsPerNickCooldownWhenQueueContainsOnlyCoolingNicks() {
    Instant now = Instant.parse("2026-02-26T00:00:00Z");
    Settings cfg = settings(5, Duration.ofMinutes(10), false);

    planner.enqueueUserhost("libera", List.of("alice"));
    assertTrue(planner.pollNext("libera", now, cfg).isPresent());

    planner.enqueueUserhost("libera", List.of("alice"));
    assertTrue(planner.pollNext("libera", now, cfg).isEmpty());

    long delayMs = planner.nextReadyDelayMs("libera", now, cfg);
    assertTrue(delayMs > 0, "cooling nick should produce a future wake-up delay");
  }

  @Test
  void periodicRefreshUsesRoundRobinRosterSlices() {
    Instant now = Instant.parse("2026-02-26T00:00:00Z");
    Settings cfg =
        new Settings(
            true,
            Duration.ofSeconds(1),
            10,
            Duration.ofMinutes(1),
            5,
            false,
            Duration.ofSeconds(45),
            Duration.ofMinutes(2),
            true,
            Duration.ofSeconds(10),
            2);

    planner.setRosterSnapshot("libera", List.of("alice", "bob", "carol"));

    planner.maybeEnqueuePeriodicRefresh("libera", now, cfg);
    PlannedCommand first = planner.pollNext("libera", now.plusSeconds(1), cfg).orElseThrow();

    planner.maybeEnqueuePeriodicRefresh("libera", now.plusSeconds(11), cfg);
    PlannedCommand second = planner.pollNext("libera", now.plusSeconds(12), cfg).orElseThrow();

    assertEquals(ProbeKind.USERHOST, first.kind());
    assertEquals(List.of("alice", "bob"), first.nicks());
    assertEquals(List.of("carol"), second.nicks());
    assertFalse(second.nicks().isEmpty());
  }

  private static Settings settings(int maxNicks, Duration nickCooldown, boolean whoisFallback) {
    return new Settings(
        true,
        Duration.ofSeconds(1),
        10,
        nickCooldown,
        maxNicks,
        whoisFallback,
        Duration.ofSeconds(45),
        Duration.ofMinutes(2),
        false,
        Duration.ofSeconds(30),
        2);
  }
}
