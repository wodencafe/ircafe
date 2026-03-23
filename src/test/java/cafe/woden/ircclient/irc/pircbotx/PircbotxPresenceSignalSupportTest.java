package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxPresenceSignalSupportTest {

  @Test
  void awayNotifyEmitsAwayStateAndHostmask() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxPresenceSignalSupport support = new PircbotxPresenceSignalSupport("libera", out::add);

    support.observe(
        Instant.parse("2026-03-23T12:10:00Z"),
        "alice",
        "AWAY",
        ":alice!u@h AWAY :Gone away",
        List.of(":Gone away"));

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.UserHostmaskObserved hm
                        && "alice".equals(hm.nick())
                        && "alice!u@h".equals(hm.hostmask())));
    IrcEvent.UserAwayStateObserved away =
        out.stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.UserAwayStateObserved.class::isInstance)
            .map(IrcEvent.UserAwayStateObserved.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(IrcEvent.AwayState.AWAY, away.awayState());
    assertEquals("Gone away", away.awayMessage());
  }

  @Test
  void accountNotifyMapsStarToLoggedOut() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxPresenceSignalSupport support = new PircbotxPresenceSignalSupport("libera", out::add);

    support.observe(
        Instant.parse("2026-03-23T12:15:00Z"),
        "alice",
        "ACCOUNT",
        ":alice!u@h ACCOUNT *",
        List.of("*"));

    IrcEvent.UserAccountStateObserved account =
        out.stream()
            .map(ServerIrcEvent::event)
            .filter(IrcEvent.UserAccountStateObserved.class::isInstance)
            .map(IrcEvent.UserAccountStateObserved.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(IrcEvent.AccountState.LOGGED_OUT, account.accountState());
    assertNull(account.accountName());
  }

  @Test
  void extendedJoinEmitsAccountAndRealNameSignals() {
    List<ServerIrcEvent> out = new ArrayList<>();
    PircbotxPresenceSignalSupport support = new PircbotxPresenceSignalSupport("libera", out::add);

    support.observe(
        Instant.parse("2026-03-23T12:20:00Z"),
        "alice",
        "JOIN",
        ":alice!u@h JOIN #ircafe alice-account :Alice Liddell",
        List.of("#ircafe", "alice-account", ":Alice Liddell"));

    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.UserAccountStateObserved ac
                        && "alice".equals(ac.nick())
                        && IrcEvent.AccountState.LOGGED_IN == ac.accountState()
                        && "alice-account".equals(ac.accountName())));
    assertTrue(
        out.stream()
            .map(ServerIrcEvent::event)
            .anyMatch(
                e ->
                    e instanceof IrcEvent.UserSetNameObserved sn
                        && "alice".equals(sn.nick())
                        && "Alice Liddell".equals(sn.realName())
                        && sn.source() == IrcEvent.UserSetNameObserved.Source.EXTENDED_JOIN));
  }
}
