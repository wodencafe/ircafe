package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AutoJoinEntryCodecTest {

  @Test
  void privateMessageEntriesRoundTrip() {
    String encoded = AutoJoinEntryCodec.encodePrivateMessageNick("Alice");
    assertEquals("query:Alice", encoded);
    assertTrue(AutoJoinEntryCodec.isPrivateMessageEntry(encoded));
    assertEquals("Alice", AutoJoinEntryCodec.decodePrivateMessageNick(encoded));
  }

  @Test
  void splitsChannelsAndPrivateMessages() {
    List<String> raw = List.of("#java", "query:Alice", "#java", "QUERY:bob", "  ", "&help");

    assertEquals(List.of("#java", "&help"), AutoJoinEntryCodec.channelEntries(raw));
    assertEquals(List.of("Alice", "bob"), AutoJoinEntryCodec.privateMessageNicks(raw));
  }

  @Test
  void nonPrefixedEntriesAreNotPrivateMessages() {
    assertFalse(AutoJoinEntryCodec.isPrivateMessageEntry("#chan"));
    assertFalse(AutoJoinEntryCodec.isPrivateMessageEntry("nick"));
    assertFalse(AutoJoinEntryCodec.isPrivateMessageEntry("query:"));
  }
}
