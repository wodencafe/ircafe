package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.IrcEvent.AccountState;
import cafe.woden.ircclient.irc.IrcEvent.AwayState;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.UserListStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmbedLoadPolicyMatcherTest {

  @Test
  void validatePatternSyntaxAcceptsGlobRegexAndUserPrefixes() {
    assertTrue(EmbedLoadPolicyMatcher.validatePatternSyntax("*.example.org").isEmpty());
    assertTrue(EmbedLoadPolicyMatcher.validatePatternSyntax("glob:*.example.org").isEmpty());
    assertTrue(EmbedLoadPolicyMatcher.validatePatternSyntax("re:^https://example").isEmpty());
    assertTrue(EmbedLoadPolicyMatcher.validatePatternSyntax("nick:re:^alice$").isEmpty());
    assertTrue(EmbedLoadPolicyMatcher.validatePatternSyntax("host:*.trusted.net").isEmpty());
  }

  @Test
  void validatePatternSyntaxRejectsBadRegex() {
    assertTrue(EmbedLoadPolicyMatcher.validatePatternSyntax("re:[unterminated").isPresent());
    assertTrue(EmbedLoadPolicyMatcher.validatePatternSyntax("nick:re:(").isPresent());
    assertTrue(EmbedLoadPolicyMatcher.validatePatternSyntax("host:regex:").isPresent());
  }

  @Test
  void blocksBlacklistedDomainsAndAllowsOthers() {
    EmbedLoadPolicyBus bus = mock(EmbedLoadPolicyBus.class);
    UserListStore users = new UserListStore();
    users.put(
        "libera",
        "#chat",
        List.of(
            new NickInfo(
                "alice",
                "@",
                "alice!ident@a.trusted.net",
                AwayState.UNKNOWN,
                null,
                AccountState.LOGGED_IN,
                "alice")));

    RuntimeConfigStore.EmbedLoadPolicyScope scope =
        new RuntimeConfigStore.EmbedLoadPolicyScope(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false,
            false,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of("*.evil.example"));
    when(bus.get()).thenReturn(new RuntimeConfigStore.EmbedLoadPolicySnapshot(scope, Map.of()));

    EmbedLoadPolicyMatcher matcher = new EmbedLoadPolicyMatcher(bus, users);
    TargetRef ref = new TargetRef("libera", "#chat");

    assertTrue(matcher.allow(ref, "alice", Map.of(), "https://safe.example/image.png"));
    assertFalse(matcher.allow(ref, "alice", Map.of(), "https://cdn.evil.example/image.png"));
  }

  @Test
  void enforcesVoiceOrOpAndLoggedInChecks() {
    EmbedLoadPolicyBus bus = mock(EmbedLoadPolicyBus.class);
    UserListStore users = new UserListStore();
    users.put(
        "libera",
        "#chat",
        List.of(
            new NickInfo(
                "alice",
                "@",
                "alice!ident@a.trusted.net",
                AwayState.UNKNOWN,
                null,
                AccountState.LOGGED_IN,
                "alice"),
            new NickInfo(
                "bob",
                "",
                "bob!ident@trusted.net",
                AwayState.UNKNOWN,
                null,
                AccountState.LOGGED_IN,
                "bob"),
            new NickInfo(
                "carol",
                "+",
                "carol!ident@trusted.net",
                AwayState.UNKNOWN,
                null,
                AccountState.LOGGED_OUT,
                null)));

    RuntimeConfigStore.EmbedLoadPolicyScope scope =
        new RuntimeConfigStore.EmbedLoadPolicyScope(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            true,
            true,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of());
    when(bus.get()).thenReturn(new RuntimeConfigStore.EmbedLoadPolicySnapshot(scope, Map.of()));

    EmbedLoadPolicyMatcher matcher = new EmbedLoadPolicyMatcher(bus, users);
    TargetRef ref = new TargetRef("libera", "#chat");

    assertTrue(matcher.allow(ref, "alice", Map.of(), "https://example.com/page"));
    assertFalse(matcher.allow(ref, "bob", Map.of(), "https://example.com/page"));
    assertFalse(matcher.allow(ref, "carol", Map.of(), "https://example.com/page"));
    assertTrue(
        matcher.allow(ref, "carol", Map.of("account", "carol"), "https://example.com/page"));
  }

  @Test
  void appliesPerServerOverrideBeforeGlobal() {
    EmbedLoadPolicyBus bus = mock(EmbedLoadPolicyBus.class);
    UserListStore users = new UserListStore();
    users.put(
        "libera",
        "#chat",
        List.of(
            new NickInfo(
                "alice",
                "@",
                "alice!ident@a.trusted.net",
                AwayState.UNKNOWN,
                null,
                AccountState.LOGGED_IN,
                "alice")));
    users.put(
        "oftc",
        "#chat",
        List.of(
            new NickInfo(
                "alice",
                "@",
                "alice!ident@a.trusted.net",
                AwayState.UNKNOWN,
                null,
                AccountState.LOGGED_IN,
                "alice")));

    RuntimeConfigStore.EmbedLoadPolicyScope global =
        new RuntimeConfigStore.EmbedLoadPolicyScope(
            List.of("host:*.trusted.net"),
            List.of(),
            List.of(),
            List.of(),
            false,
            false,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of());
    RuntimeConfigStore.EmbedLoadPolicyScope oftcOverride =
        new RuntimeConfigStore.EmbedLoadPolicyScope(
            List.of("host:*.oftc.net"),
            List.of(),
            List.of(),
            List.of(),
            false,
            false,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of());

    when(bus.get())
        .thenReturn(
            new RuntimeConfigStore.EmbedLoadPolicySnapshot(global, Map.of("oftc", oftcOverride)));

    EmbedLoadPolicyMatcher matcher = new EmbedLoadPolicyMatcher(bus, users);

    assertTrue(
        matcher.allow(new TargetRef("libera", "#chat"), "alice", Map.of(), "https://example.com"));
    assertFalse(
        matcher.allow(new TargetRef("oftc", "#chat"), "alice", Map.of(), "https://example.com"));
  }
}
