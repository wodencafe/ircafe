package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class PircbotxIsonParsersTest {

  @Test
  void parsesOnlineNicksFromTrailingList() {
    List<String> nicks =
        PircbotxIsonParsers.parseRpl303IsonOnlineNicks(":server 303 me :Alice bob");

    assertEquals(List.of("Alice", "bob"), nicks);
  }

  @Test
  void returnsEmptyListWhenNoNicksOnline() {
    List<String> nicks = PircbotxIsonParsers.parseRpl303IsonOnlineNicks(":server 303 me :");

    assertEquals(List.of(), nicks);
  }

  @Test
  void returnsNullForNon303Lines() {
    assertNull(
        PircbotxIsonParsers.parseRpl303IsonOnlineNicks(":server 005 me MONITOR=100 :supported"));
  }
}
