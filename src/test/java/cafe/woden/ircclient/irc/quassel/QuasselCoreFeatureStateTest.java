package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class QuasselCoreFeatureStateTest {
  private static final Instant AT = Instant.parse("2026-03-03T12:00:00Z");
  private static final QuasselIrcv3RuntimeSupport RUNTIME =
      new QuasselIrcv3RuntimeSupport(Ircv3RuntimeCatalogs.applicationClasspath());
  private final List<IrcEvent> events = new ArrayList<>();
  private final QuasselCoreFeatureState features = new QuasselCoreFeatureState(512, events::add);

  @Test
  void absentAndUnrelatedSnapshotsDoNotClaimCapabilityKnowledge() {
    features.observeCapabilities(1, null);
    features.observeCapabilities(1, Map.of());
    features.observeCapabilities(1, Map.of("networkName", "example"));
    features.observeCapabilities(-1, Map.of("caps", List.of("message-tags")));
    assertFalse(features.hasObservedCapabilities());
    assertFalse(features.hasAnyCapability("message-tags"));
    assertFalse(features.hasAnyCapability((String[]) null));
    assertFalse(features.hasAnyCapability());
    assertTrue(events.isEmpty());
  }

  @Test
  void snapshotNotificationsFollowStateAndLimitsUpdates() {
    AtomicReference<QuasselCoreFeatureState> subject = new AtomicReference<>();
    List<Boolean> availabilityAtNotification = new ArrayList<>();
    List<Long> limitsAtNotification = new ArrayList<>();
    subject.set(
        new QuasselCoreFeatureState(
            512,
            event -> {
              events.add(event);
              availabilityAtNotification.add(subject.get().hasAnyCapability("multiline"));
              limitsAtNotification.add(subject.get().multilineMaxBytes(1));
            }));
    subject
        .get()
        .observeCapabilities(
            1,
            Map.of("capsEnabled", Map.of("multiline", Map.of("max-bytes", 4096, "max-lines", 4))));

    assertEquals(List.of(true, true), availabilityAtNotification);
    assertEquals(List.of(4096L, 4096L), limitsAtNotification);
    var changed = assertInstanceOf(IrcEvent.Ircv3CapabilityChanged.class, events.getFirst());
    assertEquals("SYNC", changed.subcommand());
    assertEquals("multiline", changed.capability());
    assertTrue(changed.enabled());
    assertEquals(
        new IrcEvent.ConnectionFeaturesUpdated(changed.at(), "cap-sync"), events.getLast());
  }

  @Test
  void snapshotsReplaceCapabilitiesAndOnlyEmitChanges() {
    features.observeCapabilities(1, Map.of("CAPSENABLED", List.of("message-tags", "read-marker")));
    assertTrue(features.hasAnyCapability(" :+MESSAGE-TAGS=ignored "));
    events.clear();
    features.observeCapabilities(1, Map.of("capsEnabled", List.of("message-tags", "read-marker")));
    assertTrue(events.isEmpty());
    features.observeCapabilities(1, Map.of("capsEnabled", List.of("message-tags")));
    var changed = assertInstanceOf(IrcEvent.Ircv3CapabilityChanged.class, events.getFirst());
    assertEquals(
        new IrcEvent.Ircv3CapabilityChanged(changed.at(), "SYNC", "read-marker", false), changed);
    assertEquals(2, events.size());
    assertFalse(features.hasAnyCapability("read-marker"));
    features.observeCapabilities(1, Map.of("capsEnabled", List.of()));
    assertTrue(features.hasObservedCapabilities());
    assertFalse(features.hasAnyCapability("message-tags"));
  }

  @Test
  void emptyEnabledSnapshotKeepsExistingFallbackToAvailableCapabilities() {
    features.observeCapabilities(
        1, Map.of("capsEnabled", List.of(), "availableCaps", List.of("message-tags")));
    assertTrue(features.hasAnyCapability("message-tags"));
  }

  @Test
  void capAnnouncementsPrecedeStateChangesAndSummaryFollowsThem() {
    AtomicReference<QuasselCoreFeatureState> subject = new AtomicReference<>();
    List<Boolean> availabilityAtNotification = new ArrayList<>();
    List<Long> limitsAtNotification = new ArrayList<>();
    subject.set(
        new QuasselCoreFeatureState(
            512,
            event -> {
              events.add(event);
              availabilityAtNotification.add(subject.get().hasAnyCapability("multiline"));
              limitsAtNotification.add(subject.get().multilineMaxBytes(1));
            }));
    subject.get().observeCapLine(AT, 1, cap("ACK", "multiline=max-bytes=4096,max-lines=4"));
    assertEquals(List.of(false, true), availabilityAtNotification);
    assertEquals(List.of(0L, 4096L), limitsAtNotification);
    assertEquals(
        List.of(
            new IrcEvent.Ircv3CapabilityChanged(AT, "ACK", "multiline", true),
            new IrcEvent.ConnectionFeaturesUpdated(AT, "cap-ack")),
        events);
  }

  @ParameterizedTest
  @ValueSource(strings = {"LS", "NEW"})
  void advertisedCapabilitiesAreAnnouncedWithoutEnablingThem(String subcommand) {
    features.observeCapLine(AT, 1, cap(subcommand, "message-tags multiline=max-lines=4"));
    assertEquals(
        List.of(
            new IrcEvent.Ircv3CapabilityChanged(AT, subcommand, "message-tags", false),
            new IrcEvent.Ircv3CapabilityChanged(AT, subcommand, "multiline", false),
            new IrcEvent.ConnectionFeaturesUpdated(
                AT, "cap-" + subcommand.toLowerCase(java.util.Locale.ROOT))),
        events);
    assertFalse(features.hasObservedCapabilities());
    assertFalse(features.hasAnyCapability("message-tags", "multiline"));
    assertEquals(0, features.multilineMaxLines(1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"DEL", "ACK"})
  void multilineDeletionAndNegativeAcknowledgmentRemoveLimits(String subcommand) {
    features.observeCapLine(AT, 1, cap("ACK", "multiline=max-bytes=4096,max-lines=4"));
    features.observeCapLine(
        AT, 1, cap(subcommand, "ACK".equals(subcommand) ? "-multiline" : "multiline"));
    assertFalse(features.hasAnyCapability("multiline"));
    assertEquals(0L, features.multilineMaxBytes(1));
    assertEquals(0, features.multilineMaxLines(1));
  }

  @Test
  void nakKeepsExistingMultilineLimitsWhileDisablingTheCapability() {
    features.observeCapLine(AT, 1, cap("ACK", "multiline=max-bytes=4096,max-lines=4"));
    features.observeCapLine(AT, 1, cap("NAK", "multiline"));
    assertFalse(features.hasAnyCapability("multiline"));
    assertEquals(4096L, features.multilineMaxBytes(1));
    assertEquals(4, features.multilineMaxLines(1));
  }

  @Test
  void unknownNetworksStillAnnounceCapLinesWithoutRetainingState() {
    features.observeCapLine(AT, -1, cap("ACK", "message-tags"));
    assertEquals(2, events.size());
    assertFalse(features.hasObservedCapabilities());
    assertFalse(features.hasAnyCapability("message-tags"));
  }

  @Test
  void duplicateCapAcknowledgmentsAreStillAnnounced() {
    features.observeCapLine(AT, 1, cap("ACK", "message-tags"));
    features.observeCapLine(AT, 1, cap("ACK", "message-tags"));
    assertEquals(4, events.size());
    assertEquals(events.get(0), events.get(2));
    assertEquals(events.get(1), events.get(3));
  }

  @Test
  void multilineSnapshotsPreserveLimitsWhenParametersAreOmittedAndRemoveThemWhenDisabled() {
    features.observeCapabilities(1, Map.of("caps", "draft/multiline=max-bytes=4096,max-lines=4"));
    features.observeCapabilities(1, Map.of("caps", "draft/multiline"));
    assertEquals(4096L, features.multilineMaxBytes(1));
    assertEquals(4, features.multilineMaxLines(1));
    features.observeCapabilities(1, Map.of("caps", List.of()));
    assertEquals(0L, features.multilineMaxBytes(1));
    assertEquals(0, features.multilineMaxLines(1));
  }

  @Test
  void preferredNetworkLimitsFallBackOnlyWhenThatNetworkHasNoState() {
    features.observeCapabilities(2, Map.of("caps", "multiline=max-bytes=2048,max-lines=3"));
    features.observeMonitor(2, true, 100);
    assertTrue(features.monitorAvailable(1));
    assertEquals(100, features.monitorLimit(1));
    assertEquals(2048L, features.multilineMaxBytes(1));
    features.observeCapabilities(1, Map.of("caps", "multiline=max-bytes=4096,max-lines=8"));
    features.observeMonitor(1, false, 250);
    assertFalse(features.monitorAvailable(1));
    assertEquals(0, features.monitorLimit(1));
    assertEquals(4096L, features.multilineMaxBytes(1));
    assertEquals(8, features.multilineMaxLines(1));
  }

  @Test
  void negotiatedIntegerLimitsSaturateAndNegativeMonitorLimitsNormalize() {
    features.observeCapabilities(
        1, Map.of("caps", "multiline=max-bytes=9223372036854775807,max-lines=9223372036854775807"));
    features.observeMonitor(1, true, Long.MAX_VALUE);
    assertEquals(Long.MAX_VALUE, features.multilineMaxBytes(1));
    assertEquals(Integer.MAX_VALUE, features.multilineMaxLines(1));
    assertEquals(Integer.MAX_VALUE, features.monitorLimit(1));
    features.observeMonitor(1, true, -1L);
    assertEquals(0, features.monitorLimit(1));
  }

  @Test
  void monitorSnapshotsKeepPriorStateForUnrelatedUpdates() {
    features.observeMonitor(1, Map.of("isupport", List.of("MONITOR=150")));
    features.observeMonitor(1, Map.of("networkName", "example"));
    features.observeMonitor(-1, true, 500);
    assertTrue(features.monitorAvailable(1));
    assertEquals(150, features.monitorLimit(1));
    assertTrue(events.isEmpty());
  }

  @Test
  void removingANetworkClearsItsFeaturesAndPreservesOtherNetworks() {
    features.observeCapabilities(1, Map.of("caps", "multiline=max-bytes=4096,max-lines=4"));
    features.observeMonitor(1, true, 100);
    features.observeCapabilities(2, Map.of("caps", "message-tags"));
    features.removeNetwork(1);
    assertTrue(features.hasObservedCapabilities());
    assertTrue(features.hasAnyCapability("message-tags"));
    assertFalse(features.hasAnyCapability("multiline"));
    assertFalse(features.monitorAvailable(1));
    assertEquals(0, features.monitorLimit(1));
    assertEquals(0L, features.multilineMaxBytes(1));
  }

  @Test
  void snapshotsPruneAllFeatureMapsAndClearResetsSessionKnowledge() {
    var limited = new QuasselCoreFeatureState(2, events::add);
    for (int id = 1; id <= 12; id++) {
      limited.observeCapabilities(id, Map.of("caps", "multiline=max-bytes=4096,max-lines=4"));
      limited.observeMonitor(id, true, 100);
    }
    for (String field : Set.of("capabilities", "monitors", "multilineLimits")) {
      assertEquals(2, ((Map<?, ?>) ReflectionTestUtils.getField(limited, field)).size());
    }
    events.clear();
    limited.clear();
    assertFalse(limited.hasObservedCapabilities());
    assertFalse(limited.hasAnyCapability("multiline"));
    assertEquals(0, limited.multilineMaxLines(1));
    assertEquals(0, limited.monitorLimit(1));
    assertTrue(events.isEmpty());
    for (String field : Set.of("capabilities", "monitors", "multilineLimits")) {
      assertTrue(((Map<?, ?>) ReflectionTestUtils.getField(limited, field)).isEmpty());
    }
  }

  private static QuasselCoreIrcEnvelope cap(String subcommand, String caps) {
    return QuasselCoreIrcEnvelope.parse(":irc.example CAP me " + subcommand + " :" + caps, RUNTIME);
  }
}
