package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxWhoUserhostParsersTest {

  @Test
  void detectsWhoxSupportTokenFromRpl005() {
    assertTrue(
        PircbotxWhoUserhostParsers.parseRpl005IsupportHasWhox(
            ":server 005 me WHOX CHANTYPES=# :are supported"));
    assertFalse(
        PircbotxWhoUserhostParsers.parseRpl005IsupportHasWhox(
            ":server 005 me CHANTYPES=# CASEMAPPING=rfc1459 :are supported"));
  }

  @Test
  void parsesStrictWhoxTcuhnafReply() {
    PircbotxWhoUserhostParsers.ParsedWhoxTcuhnaf parsed =
        PircbotxWhoUserhostParsers.parseRpl354WhoxTcuhnaf(
            ":server 354 me 1 #ircafe ident host.example alice H account :more", "1");

    assertNotNull(parsed);
    assertEquals("1", parsed.token());
    assertEquals("#ircafe", parsed.channel());
    assertEquals("ident", parsed.user());
    assertEquals("host.example", parsed.host());
    assertEquals("alice", parsed.nick());
    assertEquals("H", parsed.flags());
    assertEquals("account", parsed.account());
  }

  @Test
  void malformedWhoxTcuhnafReturnsNull() {
    assertNull(
        PircbotxWhoUserhostParsers.parseRpl354WhoxTcuhnaf(
            ":server 354 me 2 #ircafe ident host.example alice H account", "1"));
    assertNull(
        PircbotxWhoUserhostParsers.parseRpl354WhoxTcuhnaf(
            ":server 354 me 1 #ircafe ident not_a_host alice H account", "1"));
  }

  @Test
  void parsesRpl302UserhostEntriesAndAwayStates() {
    List<PircbotxWhoUserhostParsers.UserhostEntry> entries =
        PircbotxWhoUserhostParsers.parseRpl302Userhost(
            ":server 302 me :alice=+ident@host.example bob*=-user2@host2.example");

    assertNotNull(entries);
    assertEquals(2, entries.size());
    assertEquals("alice", entries.get(0).nick());
    assertEquals("alice!ident@host.example", entries.get(0).hostmask());
    assertEquals(IrcEvent.AwayState.HERE, entries.get(0).awayState());

    assertEquals("bob", entries.get(1).nick());
    assertEquals("bob!user2@host2.example", entries.get(1).hostmask());
    assertEquals(IrcEvent.AwayState.AWAY, entries.get(1).awayState());
  }

  @Test
  void detectsWhoxTokenShapeEvenWhenStrictParsingFails() {
    assertTrue(
        PircbotxWhoUserhostParsers.seemsRpl354WhoxWithToken(
            ":server 354 me 1 #ircafe x y z :weird", "1"));
    assertFalse(
        PircbotxWhoUserhostParsers.seemsRpl354WhoxWithToken(
            ":server 354 me 2 #ircafe x y z :weird", "1"));
  }
}
