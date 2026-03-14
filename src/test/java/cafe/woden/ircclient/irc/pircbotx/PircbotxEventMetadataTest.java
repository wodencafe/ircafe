package cafe.woden.ircclient.irc.pircbotx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pircbotx.User;

class PircbotxEventMetadataTest {

  @Test
  void withObservedHostmaskTagAddsUsefulHostmask() {
    User user = mock(User.class);
    when(user.getNick()).thenReturn("alice");
    when(user.getLogin()).thenReturn("ident");
    when(user.getHostname()).thenReturn("host.example");

    Map<String, String> tags = PircbotxEventMetadata.withObservedHostmaskTag(new HashMap<>(), user);

    assertEquals("alice!ident@host.example", tags.get("ircafe/hostmask"));
  }

  @Test
  void ircv3MessageIdPrefersStandardMsgidTag() {
    String messageId =
        PircbotxEventMetadata.ircv3MessageId(
            Map.of("znc.in/msgid", "legacy", "draft/msgid", "draft", "msgid", "modern"));

    assertEquals("modern", messageId);
  }
}
