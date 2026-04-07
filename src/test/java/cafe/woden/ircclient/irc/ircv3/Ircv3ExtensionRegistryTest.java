package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Ircv3ExtensionRegistryTest {

  @Test
  void draftCapabilitiesUseDraftRequestTokensAndFinalPreferenceKeys() {
    Ircv3ExtensionRegistry.ExtensionDefinition readMarker =
        Ircv3ExtensionRegistry.find("read-marker").orElseThrow();
    Ircv3ExtensionRegistry.ExtensionDefinition chathistory =
        Ircv3ExtensionRegistry.find("draft/chathistory").orElseThrow();

    assertEquals("draft/read-marker", readMarker.requestToken());
    assertEquals("read-marker", readMarker.preferenceKey());
    assertEquals("draft/chathistory", chathistory.requestToken());
    assertEquals("chathistory", chathistory.preferenceKey());
  }

  @Test
  void tagFeaturesAndNonRequestableCapabilitiesCannotProduceCapReqTokens() {
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("typing"));
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("draft/reply"));
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("draft/react"));
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("sts"));
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("message-edit"));
  }

  @Test
  void requestableCapabilityListContainsOnlyCapabilities() {
    assertFalse(Ircv3ExtensionRegistry.requestableCapabilities().isEmpty());
    assertTrue(
        Ircv3ExtensionRegistry.requestableCapabilities().stream()
            .allMatch(Ircv3ExtensionRegistry.ExtensionDefinition::requestable));
    assertTrue(
        Ircv3ExtensionRegistry.requestableCapabilities().stream()
            .allMatch(
                definition ->
                    definition.kind() == Ircv3ExtensionRegistry.ExtensionKind.CAPABILITY));
  }
}
