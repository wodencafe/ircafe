package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class Ircv3CapabilityCatalogTest {

  @Test
  void requestableCapabilitiesMatchPublishedNegotiationSet() {
    assertEquals(
        List.of(
            "multi-prefix",
            "cap-notify",
            "invite-notify",
            "away-notify",
            "account-notify",
            "monitor",
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
        Ircv3CapabilityCatalog.requestableCapabilities());
  }
}
