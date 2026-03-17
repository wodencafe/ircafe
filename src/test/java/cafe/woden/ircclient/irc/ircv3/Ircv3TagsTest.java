package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ircv3TagsTest {

  @Test
  void parsesRawLineTagsAndUnescapesValues() {
    Map<String, String> tags =
        Ircv3Tags.fromRawLine(
            "@time=2026-02-16T12:34:56.000Z;label=req\\:42;draft/reply=abc\\s123;empty :server 001 nick :hi");

    assertEquals("2026-02-16T12:34:56.000Z", tags.get("time"));
    assertEquals("req;42", tags.get("label"));
    assertEquals("abc 123", tags.get("draft/reply"));
    assertEquals("", tags.get("empty"));
  }

  @Test
  void fromEventUsesGetTagsWhenAvailable() {
    Map<String, String> tags = Ircv3Tags.fromEvent(new EventWithTags());

    assertEquals("abc123", tags.get("msgid"));
    assertEquals("xyz", tags.get("draft/reply"));
    assertTrue(!tags.containsKey("label"));
  }

  @Test
  void fromEventFallsBackToRawLineWhenTagMapMissing() {
    Map<String, String> tags = Ircv3Tags.fromEvent(new EventWithRawLine());

    assertEquals("raw-1", tags.get("label"));
    assertEquals("zzz", tags.get("msgid"));
  }

  @Test
  void firstTagValueNormalizesRequestedKeys() {
    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("msgid", "abc");
    tags.put("draft/msgid", "legacy");

    assertEquals("abc", Ircv3Tags.firstTagValue(tags, "+msgid", "@draft/msgid"));
  }

  private static final class EventWithTags {
    public Map<String, String> getTags() {
      LinkedHashMap<String, String> tags = new LinkedHashMap<>();
      tags.put("@MsgId", "abc123");
      tags.put("+Draft/Reply", "xyz");
      return tags;
    }

    public String getRawLine() {
      return "@label=raw-ignored :server 001 nick :hello";
    }
  }

  private static final class EventWithRawLine {
    public String getRawLine() {
      return "@label=raw-1;msgid=zzz :server 001 nick :hello";
    }
  }
}
