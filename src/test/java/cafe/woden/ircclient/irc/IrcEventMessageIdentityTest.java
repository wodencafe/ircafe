package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IrcEventMessageIdentityTest {

  @Test
  void oldChannelMessageConstructorDefaultsIdentityMetadata() {
    IrcEvent.ChannelMessage ev =
        new IrcEvent.ChannelMessage(Instant.parse("2026-02-16T00:00:00Z"), "#ircafe", "alice", "hello");

    assertTrue(ev.messageId().isEmpty());
    assertTrue(ev.ircv3Tags().isEmpty());
  }

  @Test
  void identityMetadataIsNormalizedAndImmutable() {
    Map<String, String> raw = new LinkedHashMap<>();
    raw.put("@MsgId", "abc123");
    raw.put("+Draft/Reply", "xyz");
    raw.put("typing", "active");

    IrcEvent.PrivateMessage ev =
        new IrcEvent.PrivateMessage(
            Instant.parse("2026-02-16T00:00:00Z"),
            "bob",
            "hi",
            "  abc123  ",
            raw);

    assertEquals("abc123", ev.messageId());
    assertEquals("abc123", ev.ircv3Tags().get("msgid"));
    assertEquals("xyz", ev.ircv3Tags().get("draft/reply"));
    assertEquals("active", ev.ircv3Tags().get("typing"));
    assertThrows(UnsupportedOperationException.class, () -> ev.ircv3Tags().put("new", "value"));
  }

  @Test
  void serverResponseLineSupportsIdentityMetadata() {
    Map<String, String> raw = new LinkedHashMap<>();
    raw.put("@Label", "raw-42");
    raw.put("+MsgId", "srv123");

    IrcEvent.ServerResponseLine ev =
        new IrcEvent.ServerResponseLine(
            Instant.parse("2026-02-16T00:00:00Z"),
            421,
            "Unknown command",
            "@label=raw-42;msgid=srv123 :server 421 me FOO :Unknown command",
            "  srv123  ",
            raw);

    assertEquals("srv123", ev.messageId());
    assertEquals("raw-42", ev.ircv3Tags().get("label"));
    assertEquals("srv123", ev.ircv3Tags().get("msgid"));
    assertThrows(UnsupportedOperationException.class, () -> ev.ircv3Tags().put("x", "y"));
  }

  @Test
  void standardReplySupportsIdentityMetadata() {
    Map<String, String> raw = new LinkedHashMap<>();
    raw.put("@Label", "req-42");
    raw.put("+MsgId", "srv123");

    IrcEvent.StandardReply ev =
        new IrcEvent.StandardReply(
            Instant.parse("2026-02-16T00:00:00Z"),
            IrcEvent.StandardReplyKind.FAIL,
            "CHATHISTORY",
            "INVALID_PARAMS",
            "timestamp=bad",
            "Invalid selector",
            "@label=req-42;msgid=srv123 :server FAIL CHATHISTORY INVALID_PARAMS timestamp=bad :Invalid selector",
            "  srv123  ",
            raw);

    assertEquals("srv123", ev.messageId());
    assertEquals("req-42", ev.ircv3Tags().get("label"));
    assertEquals("srv123", ev.ircv3Tags().get("msgid"));
    assertThrows(UnsupportedOperationException.class, () -> ev.ircv3Tags().put("x", "y"));
  }
}
