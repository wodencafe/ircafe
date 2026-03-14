package cafe.woden.ircclient.irc.pircbotx.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pircbotx.hooks.events.WhoisEvent;

class PircbotxWhoisResultEmitterTest {

  @Test
  void onWhoisEmitsObservedHostmaskAndWhoisSummary() {
    List<ServerIrcEvent> seen = new ArrayList<>();
    PircbotxWhoisResultEmitter emitter = new PircbotxWhoisResultEmitter("libera", seen::add);
    WhoisEvent event = mock(WhoisEvent.class);
    when(event.getNick()).thenReturn("alice");
    when(event.getLogin()).thenReturn("ident");
    when(event.getHostname()).thenReturn("host.example");
    when(event.getRealname()).thenReturn("Alice Example");
    when(event.getServer()).thenReturn("irc.example");
    when(event.getServerInfo()).thenReturn("Example IRC");
    when(event.getChannels()).thenReturn(ImmutableList.of("#ircafe", "#woden"));
    when(event.getIdleSeconds()).thenReturn(12L);
    when(event.getSignOnTime()).thenReturn(34L);
    when(event.getRegisteredAs()).thenReturn("aliceAccount");

    emitter.onWhois(event);

    assertEquals(2, seen.size());
    IrcEvent.UserHostmaskObserved hostmask =
        assertInstanceOf(IrcEvent.UserHostmaskObserved.class, seen.get(0).event());
    assertEquals("alice", hostmask.nick());
    assertEquals("alice!ident@host.example", hostmask.hostmask());

    IrcEvent.WhoisResult whois = assertInstanceOf(IrcEvent.WhoisResult.class, seen.get(1).event());
    assertEquals("alice", whois.nick());
    assertEquals(
        List.of(
            "User: ident@host.example",
            "Realname: Alice Example",
            "Server: irc.example (Example IRC)",
            "Account: aliceAccount",
            "Idle: 12s",
            "Sign-on: 34",
            "Channels: #ircafe #woden"),
        whois.lines());
  }

  @Test
  void onWhoisEmitsNoDetailsFallbackWhenFieldsAreEmpty() {
    List<ServerIrcEvent> seen = new ArrayList<>();
    PircbotxWhoisResultEmitter emitter = new PircbotxWhoisResultEmitter("libera", seen::add);
    WhoisEvent event = mock(WhoisEvent.class);
    when(event.getNick()).thenReturn("");
    when(event.getChannels()).thenReturn(ImmutableList.of());
    when(event.getIdleSeconds()).thenReturn(-1L);
    when(event.getSignOnTime()).thenReturn(-1L);

    emitter.onWhois(event);

    assertEquals(1, seen.size());
    IrcEvent.WhoisResult whois =
        assertInstanceOf(IrcEvent.WhoisResult.class, seen.getFirst().event());
    assertEquals("(unknown)", whois.nick());
    assertEquals(List.of("(no WHOIS details)"), whois.lines());
  }
}
