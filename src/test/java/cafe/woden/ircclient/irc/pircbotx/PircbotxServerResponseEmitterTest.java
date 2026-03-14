package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxServerResponseEmitterTest {

  @Test
  void emitServerResponseLinePublishesListEntryAndStatusLine() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxServerResponseEmitter emitter =
        new PircbotxServerResponseEmitter("libera", events::add);

    emitter.emitServerResponseLine(null, 322, ":server 322 me #ircafe 42 :Topic here");

    assertEquals(2, events.size());
    IrcEvent.ChannelListEntry entry =
        assertInstanceOf(IrcEvent.ChannelListEntry.class, events.get(0).event());
    IrcEvent.ServerResponseLine line =
        assertInstanceOf(IrcEvent.ServerResponseLine.class, events.get(1).event());

    assertEquals("#ircafe", entry.channel());
    assertEquals(42, entry.visibleUsers());
    assertEquals("Topic here", entry.topic());
    assertEquals("#ircafe (42): Topic here", line.message());
  }

  @Test
  void emitChannelBanListEventTracksStartEntryAndEnd() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxServerResponseEmitter emitter =
        new PircbotxServerResponseEmitter("libera", events::add);

    emitter.emitChannelBanListEvent(
        Instant.parse("2026-03-13T12:30:00Z"),
        PircbotxInboundLineParsers.parseIrcLine(":server 367 me #ircafe bad!*@* oper 1710000000"));
    emitter.emitChannelBanListEvent(
        Instant.parse("2026-03-13T12:31:00Z"),
        PircbotxInboundLineParsers.parseIrcLine(":server 368 me #ircafe :End of Channel Ban List"));

    assertEquals(3, events.size());
    assertInstanceOf(IrcEvent.ChannelBanListStarted.class, events.get(0).event());
    IrcEvent.ChannelBanListEntry entry =
        assertInstanceOf(IrcEvent.ChannelBanListEntry.class, events.get(1).event());
    IrcEvent.ChannelBanListEnded ended =
        assertInstanceOf(IrcEvent.ChannelBanListEnded.class, events.get(2).event());

    assertEquals("#ircafe", entry.channel());
    assertEquals("bad!*@*", entry.mask());
    assertEquals("oper", entry.setBy());
    assertEquals("#ircafe", ended.channel());
    assertEquals("End of Channel Ban List", ended.summary());
  }

  @Test
  void maybeEmitAlisChannelListEntryHandlesEntriesAndEndSummary() {
    List<ServerIrcEvent> events = new ArrayList<>();
    PircbotxServerResponseEmitter emitter =
        new PircbotxServerResponseEmitter("libera", events::add);

    assertTrue(
        emitter.maybeEmitAlisChannelListEntry(
            Instant.parse("2026-03-13T12:32:00Z"), "alis", "#java 1200 :Java discussion"));
    assertTrue(
        emitter.maybeEmitAlisChannelListEntry(
            Instant.parse("2026-03-13T12:33:00Z"), "alis", "End of output."));

    assertEquals(2, events.size());
    IrcEvent.ChannelListEntry entry =
        assertInstanceOf(IrcEvent.ChannelListEntry.class, events.get(0).event());
    IrcEvent.ChannelListEnded ended =
        assertInstanceOf(IrcEvent.ChannelListEnded.class, events.get(1).event());

    assertEquals("#java", entry.channel());
    assertEquals(1200, entry.visibleUsers());
    assertEquals("Java discussion", entry.topic());
    assertEquals("End of output.", ended.summary());
  }
}
