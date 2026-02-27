package cafe.woden.ircclient.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.config.IgnoreProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
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
                    Map.of(),
                    Map.of(),
                    Map.of(),
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
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    List.of())));

    IgnoreListService lists = new IgnoreListService(props, runtimeConfig);
    IgnoreStatusService status = new IgnoreStatusService(lists, mock(UserListStore.class));
    InboundIgnorePolicy policy = new InboundIgnorePolicy(lists, status);

    InboundIgnorePolicyPort.Decision publicMessage =
        policy.decide("libera", "alice", "alice!id@bad.host", false, List.of("MSGS", "PUBLIC"));
    InboundIgnorePolicyPort.Decision notice =
        policy.decide("libera", "alice", "alice!id@bad.host", false, List.of("NOTICES"));

    assertEquals(InboundIgnorePolicyPort.Decision.HARD_DROP, publicMessage);
    assertEquals(InboundIgnorePolicyPort.Decision.ALLOW, notice);
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
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    List.of())));

    IgnoreListService lists = new IgnoreListService(props, runtimeConfig);
    IgnoreStatusService status = new IgnoreStatusService(lists, mock(UserListStore.class));
    InboundIgnorePolicy policy = new InboundIgnorePolicy(lists, status);

    InboundIgnorePolicyPort.Decision inScopedChannel =
        policy.decide(
            "libera", "alice", "alice!id@bad.host", false, List.of("MSGS", "PUBLIC"), "#ircafe");
    InboundIgnorePolicyPort.Decision inOtherChannel =
        policy.decide(
            "libera", "alice", "alice!id@bad.host", false, List.of("MSGS", "PUBLIC"), "#other");
    InboundIgnorePolicyPort.Decision inPrivateMessage =
        policy.decide("libera", "alice", "alice!id@bad.host", false, List.of("MSGS"), "");

    assertEquals(InboundIgnorePolicyPort.Decision.HARD_DROP, inScopedChannel);
    assertEquals(InboundIgnorePolicyPort.Decision.ALLOW, inOtherChannel);
    assertEquals(InboundIgnorePolicyPort.Decision.ALLOW, inPrivateMessage);
  }

  @Test
  void expiredHardIgnoreDoesNotMatchAndIsPruned() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    long expiredAt = System.currentTimeMillis() - 1_000L;
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
                    Map.of("*!*@bad.host", expiredAt),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    List.of())));

    IgnoreListService lists = new IgnoreListService(props, runtimeConfig);
    IgnoreStatusService status = new IgnoreStatusService(lists, mock(UserListStore.class));
    InboundIgnorePolicy policy = new InboundIgnorePolicy(lists, status);

    InboundIgnorePolicyPort.Decision decision =
        policy.decide("libera", "alice", "alice!id@bad.host", false, List.of("MSGS", "PUBLIC"));

    assertEquals(InboundIgnorePolicyPort.Decision.ALLOW, decision);
    assertTrue(lists.listMasks("libera").isEmpty());
  }

  @Test
  void regexpPatternMustMatchInboundTextToApplyHardIgnore() {
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
                    Map.of(),
                    Map.of("*!*@bad.host", "afk|brb"),
                    Map.of("*!*@bad.host", "regexp"),
                    Map.of(),
                    List.of())));

    IgnoreListService lists = new IgnoreListService(props, runtimeConfig);
    IgnoreStatusService status = new IgnoreStatusService(lists, mock(UserListStore.class));
    InboundIgnorePolicy policy = new InboundIgnorePolicy(lists, status);

    InboundIgnorePolicyPort.Decision matching =
        policy.decide(
            "libera",
            "alice",
            "alice!id@bad.host",
            false,
            List.of("MSGS", "PUBLIC"),
            "#ircafe",
            "brb, back later");
    InboundIgnorePolicyPort.Decision nonMatching =
        policy.decide(
            "libera",
            "alice",
            "alice!id@bad.host",
            false,
            List.of("MSGS", "PUBLIC"),
            "#ircafe",
            "hello everyone");

    assertEquals(InboundIgnorePolicyPort.Decision.HARD_DROP, matching);
    assertEquals(InboundIgnorePolicyPort.Decision.ALLOW, nonMatching);
  }

  @Test
  void repliesFlagIgnoresChannelRepliesToMaskedNick() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    IgnoreProperties props =
        new IgnoreProperties(
            true,
            false,
            Map.of(
                "libera",
                new IgnoreProperties.ServerIgnore(
                    List.of("alice!*@*"),
                    Map.of("alice!*@*", List.of("MSGS")),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of("alice!*@*", true),
                    List.of())));

    IgnoreListService lists = new IgnoreListService(props, runtimeConfig);
    IgnoreStatusService status = new IgnoreStatusService(lists, mock(UserListStore.class));
    InboundIgnorePolicy policy = new InboundIgnorePolicy(lists, status);

    InboundIgnorePolicyPort.Decision replyInChannel =
        policy.decide(
            "libera",
            "bob",
            "bob!id@good.host",
            false,
            List.of("MSGS", "PUBLIC"),
            "#ircafe",
            "alice: ping");
    InboundIgnorePolicyPort.Decision nonReplyInChannel =
        policy.decide(
            "libera",
            "bob",
            "bob!id@good.host",
            false,
            List.of("MSGS", "PUBLIC"),
            "#ircafe",
            "hello all");
    InboundIgnorePolicyPort.Decision replyInPrivateMessage =
        policy.decide(
            "libera", "bob", "bob!id@good.host", false, List.of("MSGS"), "", "alice: ping");

    assertEquals(InboundIgnorePolicyPort.Decision.HARD_DROP, replyInChannel);
    assertEquals(InboundIgnorePolicyPort.Decision.ALLOW, nonReplyInChannel);
    assertEquals(InboundIgnorePolicyPort.Decision.ALLOW, replyInPrivateMessage);
  }
}
