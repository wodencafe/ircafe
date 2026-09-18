package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class Ircv3CapabilityCatalogTest {

  @Test
  void requestableCapabilitiesMatchPublishedNegotiationSet() {
    assertEquals(
        Set.of(
            "multi-prefix",
            "cap-notify",
            "invite-notify",
            "away-notify",
            "account-notify",
            "extended-monitor",
            "extended-join",
            "setname",
            "chghost",
            "message-tags",
            "server-time",
            "standard-replies",
            "echo-message",
            "labeled-response",
            "draft/read-marker",
            "draft/multiline",
            "draft/message-redaction",
            "batch",
            "draft/chathistory",
            "znc.in/playback",
            "account-tag",
            "userhost-in-names"),
        Set.copyOf(Ircv3CapabilityCatalog.requestableCapabilities()));
  }
}
