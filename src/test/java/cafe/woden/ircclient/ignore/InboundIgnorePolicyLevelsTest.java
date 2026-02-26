package cafe.woden.ircclient.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.UserListStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InboundIgnorePolicyLevelsTest {

  @Test
  void hardIgnoreLevelsRestrictMatchesByInboundKind() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    IgnoreProperties props =
        new IgnoreProperties(
            true,
            false,
            Map.of(
                "libera",
                new IgnoreProperties.ServerIgnore(
                    List.of("*!*@bad.host"),
                    Map.of("*!*@bad.host", List.of("NOTICES")),
                    Map.of(),
                    List.of())));

    IgnoreListService lists = new IgnoreListService(props, runtimeConfig);
    IgnoreStatusService status = new IgnoreStatusService(lists, mock(UserListStore.class));

    IgnoreStatusService.Status notice =
        status.status("libera", "alice", "alice!id@bad.host", List.of("NOTICES"));
    IgnoreStatusService.Status msg =
        status.status("libera", "alice", "alice!id@bad.host", List.of("MSGS"));

    assertTrue(notice.hard());
    assertFalse(msg.hard());
  }

  @Test
  void msgsLevelAppliesToPublicMessagesButNotNotices() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    IgnoreProperties props =
        new IgnoreProperties(
            true,
            false,
            Map.of(
                "libera",
                new IgnoreProperties.ServerIgnore(
                    List.of("*!*@bad.host"),
                    Map.of("*!*@bad.host", List.of("MSGS")),
                    Map.of(),
                    List.of())));

    IgnoreListService lists = new IgnoreListService(props, runtimeConfig);
    IgnoreStatusService status = new IgnoreStatusService(lists, mock(UserListStore.class));
    InboundIgnorePolicy policy = new InboundIgnorePolicy(lists, status);

    InboundIgnorePolicy.Decision publicMessage =
        policy.decide("libera", "alice", "alice!id@bad.host", false, List.of("MSGS", "PUBLIC"));
    InboundIgnorePolicy.Decision notice =
        policy.decide("libera", "alice", "alice!id@bad.host", false, List.of("NOTICES"));

    assertEquals(InboundIgnorePolicy.Decision.HARD_DROP, publicMessage);
    assertEquals(InboundIgnorePolicy.Decision.ALLOW, notice);
  }

  @Test
  void channelScopedHardIgnoreOnlyAppliesInsideConfiguredChannels() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    IgnoreProperties props =
        new IgnoreProperties(
            true,
            false,
            Map.of(
                "libera",
                new IgnoreProperties.ServerIgnore(
                    List.of("*!*@bad.host"),
                    Map.of("*!*@bad.host", List.of("MSGS")),
                    Map.of("*!*@bad.host", List.of("#ircafe")),
                    List.of())));

    IgnoreListService lists = new IgnoreListService(props, runtimeConfig);
    IgnoreStatusService status = new IgnoreStatusService(lists, mock(UserListStore.class));
    InboundIgnorePolicy policy = new InboundIgnorePolicy(lists, status);

    InboundIgnorePolicy.Decision inScopedChannel =
        policy.decide(
            "libera", "alice", "alice!id@bad.host", false, List.of("MSGS", "PUBLIC"), "#ircafe");
    InboundIgnorePolicy.Decision inOtherChannel =
        policy.decide(
            "libera", "alice", "alice!id@bad.host", false, List.of("MSGS", "PUBLIC"), "#other");
    InboundIgnorePolicy.Decision inPrivateMessage =
        policy.decide("libera", "alice", "alice!id@bad.host", false, List.of("MSGS"), "");

    assertEquals(InboundIgnorePolicy.Decision.HARD_DROP, inScopedChannel);
    assertEquals(InboundIgnorePolicy.Decision.ALLOW, inOtherChannel);
    assertEquals(InboundIgnorePolicy.Decision.ALLOW, inPrivateMessage);
  }
}
