package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxMonitorParsersTest {

  @Test
  void parsesMononlineEntriesWithHostmask() {
    List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> entries =
        PircbotxMonitorParsers.parseRpl730MonitorOnlineEntries(
            ":server 730 me :Alice!u@host,bob!ident@host");

    assertEquals(2, entries.size());
    assertEquals("Alice", entries.get(0).nick());
    assertEquals("Alice!u@host", entries.get(0).hostmask());
    assertEquals("bob", entries.get(1).nick());
    assertEquals("bob!ident@host", entries.get(1).hostmask());
  }

  @Test
  void parsesMonofflineEntriesWithoutHostmask() {
    List<PircbotxMonitorParsers.ParsedMonitorStatusEntry> entries =
        PircbotxMonitorParsers.parseRpl731MonitorOfflineEntries(
            ":server 731 me alice,bob");

    assertEquals(2, entries.size());
    assertEquals("alice", entries.get(0).nick());
    assertTrue(entries.get(0).hostmask().isEmpty());
    assertEquals("bob", entries.get(1).nick());
    assertTrue(entries.get(1).hostmask().isEmpty());
  }

  @Test
  void parsesMononlineNickListFromTrailingHostmasks() {
    List<String> nicks =
        PircbotxMonitorParsers.parseRpl730MonitorOnlineNicks(
            ":server 730 me :Alice!u@host,bob!ident@host");

    assertEquals(List.of("Alice", "bob"), nicks);
  }

  @Test
  void parsesMonofflineNickListFromParam() {
    List<String> nicks =
        PircbotxMonitorParsers.parseRpl731MonitorOfflineNicks(
            ":server 731 me alice,bob");

    assertEquals(List.of("alice", "bob"), nicks);
  }

  @Test
  void parsesMonlistNickList() {
    List<String> nicks =
        PircbotxMonitorParsers.parseRpl732MonitorListNicks(
            ":server 732 me :alice,bob,charlie");

    assertEquals(List.of("alice", "bob", "charlie"), nicks);
  }

  @Test
  void detectsEndOfMonitorList() {
    assertTrue(PircbotxMonitorParsers.isRpl733MonitorListEnd(":server 733 me :End of MONITOR list"));
    assertFalse(PircbotxMonitorParsers.isRpl733MonitorListEnd(":server 732 me :alice,bob"));
  }

  @Test
  void parsesMonlistfullLimitAndNicks() {
    PircbotxMonitorParsers.ParsedMonitorListFull parsed =
        PircbotxMonitorParsers.parseErr734MonitorListFull(
            ":server 734 me 100 alice,bob :Monitor list is full");

    assertNotNull(parsed);
    assertEquals(100, parsed.limit());
    assertEquals(List.of("alice", "bob"), parsed.nicks());
    assertEquals("Monitor list is full", parsed.message());
  }

  @Test
  void parsesIsupportMonitorSupportAndLimit() {
    PircbotxMonitorParsers.ParsedMonitorSupport parsed =
        PircbotxMonitorParsers.parseRpl005MonitorSupport(
            ":server 005 me MONITOR=250 WHOX :are supported by this server");

    assertNotNull(parsed);
    assertTrue(parsed.supported());
    assertEquals(250, parsed.limit());
  }

  @Test
  void parsesIsupportMonitorWithoutLimitAsSupported() {
    PircbotxMonitorParsers.ParsedMonitorSupport parsed =
        PircbotxMonitorParsers.parseRpl005MonitorSupport(
            ":server 005 me MONITOR CASEMAPPING=rfc1459 :are supported");

    assertNotNull(parsed);
    assertTrue(parsed.supported());
    assertEquals(0, parsed.limit());
  }

  @Test
  void parsesIsupportMonitorRemoval() {
    PircbotxMonitorParsers.ParsedMonitorSupport parsed =
        PircbotxMonitorParsers.parseRpl005MonitorSupport(
            ":server 005 me -MONITOR CASEMAPPING=rfc1459 :are supported");

    assertNotNull(parsed);
    assertFalse(parsed.supported());
    assertEquals(0, parsed.limit());
  }

  @Test
  void returnsNullWhenMonitorTokenMissing() {
    assertNull(
        PircbotxMonitorParsers.parseRpl005MonitorSupport(
            ":server 005 me WHOX CASEMAPPING=rfc1459 :are supported"));
  }
}
