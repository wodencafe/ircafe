package cafe.woden.ircclient.irc.pircbotx.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxAccountTagSupportTest {

  @Test
  void emitsAccountStateChangesButSuppressesDuplicates() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAccountTagSupport support = new PircbotxAccountTagSupport("libera", out::add);
    Instant now = Instant.parse("2026-03-23T12:00:00Z");

    support.observe(now, "alice", "PRIVMSG", "#ircafe", ImmutableMap.of("account", "alice"));
    support.observe(
        now.plusSeconds(1), "alice", "NOTICE", "#ircafe", ImmutableMap.of("account", "alice"));
    support.observe(
        now.plusSeconds(2), "alice", "PRIVMSG", "#ircafe", ImmutableMap.of("account", "*"));
    support.observe(
        now.plusSeconds(3), "alice", "PRIVMSG", "#ircafe", ImmutableMap.of("account", "0"));

    List<IrcEvent.UserAccountStateObserved> events =
        out.stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.UserAccountStateObserved.class::isInstance)
            .map(IrcEvent.UserAccountStateObserved.class::cast)
            .toList();

    assertEquals(3, events.size());
    assertEquals(IrcEvent.AccountState.LOGGED_IN, events.get(0).accountState());
    assertEquals("alice", events.get(0).accountName());
    assertEquals(IrcEvent.AccountState.LOGGED_OUT, events.get(1).accountState());
    assertNull(events.get(1).accountName());
    assertEquals(IrcEvent.AccountState.LOGGED_OUT, events.get(2).accountState());
    assertNull(events.get(2).accountName());
  }

  @Test
  void ignoresMissingAccountTag() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxAccountTagSupport support = new PircbotxAccountTagSupport("libera", out::add);

    support.observe(
        Instant.parse("2026-03-23T12:05:00Z"),
        "alice",
        "PRIVMSG",
        "#ircafe",
        ImmutableMap.of("msgid", "123"));

    assertTrue(out.isEmpty());
  }
}
