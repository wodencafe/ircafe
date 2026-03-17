package cafe.woden.ircclient.irc.pircbotx.emit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxMonitorEventEmitterTest {

  @Test
  void maybeEmitNumericEmitsOnlineAndHostmaskEvents() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxMonitorEventEmitter emitter = new PircbotxMonitorEventEmitter("libera", events::add);

    assertTrue(
        emitter.maybeEmitNumeric(
            ":server 730 me :Alice!u@host",
            "@time=2026-03-13T12:15:00Z :server 730 me :Alice!u@host"));

    assertEquals(2, events.size());
    IrcEvent.UserHostmaskObserved hostmask =
        assertInstanceOf(IrcEvent.UserHostmaskObserved.class, events.get(0).event());
    IrcEvent.MonitorOnlineObserved online =
        assertInstanceOf(IrcEvent.MonitorOnlineObserved.class, events.get(1).event());

    assertEquals("Alice", hostmask.nick());
    assertEquals("Alice!u@host", hostmask.hostmask());
    assertEquals(List.of("Alice"), online.nicks());
  }

  @Test
  void maybeEmitNumericEmitsMonitorListFull() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxMonitorEventEmitter emitter = new PircbotxMonitorEventEmitter("libera", events::add);

    assertTrue(
        emitter.maybeEmitNumeric(
            ":server 734 me 100 alice,bob :Monitor list is full",
            ":server 734 me 100 alice,bob :Monitor list is full"));

    assertEquals(1, events.size());
    IrcEvent.MonitorListFull full =
        assertInstanceOf(IrcEvent.MonitorListFull.class, events.getFirst().event());
    assertEquals(100, full.limit());
    assertEquals(List.of("alice", "bob"), full.nicks());
    assertEquals("Monitor list is full", full.message());
  }
}
