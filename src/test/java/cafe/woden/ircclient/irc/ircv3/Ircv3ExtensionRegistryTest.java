package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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

  @Test
  void providerAggregationIncludesBuiltInsAndSpiContributors() {
    assertEquals(
        List.of(
            "core-transport",
            "read-marker",
            "multiline",
            "message-redaction",
            "core-history",
            "chathistory",
            "core-misc"),
        Ircv3ExtensionRegistry.providerIds());
  }

  @Test
  void visibleFeaturesRemainInStableDisplayOrder() {
    assertEquals(
        List.of(
            "Replies",
            "Reactions",
            "Reaction removal",
            "Message redaction",
            "History",
            "Typing",
            "Read markers"),
        Ircv3ExtensionRegistry.visibleFeatures().stream()
            .map(Ircv3ExtensionRegistry.FeatureDefinition::label)
            .toList());
  }

  @Test
  void duplicateCapabilityTokensAreRejected() {
    ArrayList<Ircv3ExtensionDefinitionProvider> providers =
        new ArrayList<>(Ircv3ExtensionRegistry.builtInProviders());
    providers.add(
        new Ircv3ExtensionDefinitionProvider() {
          @Override
          public String providerId() {
            return "duplicate-echo-message";
          }

          @Override
          public int sortOrder() {
            return 950;
          }

          @Override
          public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
            return List.of(
                Ircv3ExtensionProviderSupport.capability(
                    "plugin-echo-message-copy",
                    Ircv3ExtensionRegistry.SpecStatus.STABLE,
                    "echo-message",
                    "plugin-echo-message-copy",
                    "Echo message copy",
                    Ircv3ExtensionRegistry.UiGroup.OTHER,
                    950,
                    "Conflicting test-only capability token."));
          }
        });

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> Ircv3ExtensionRegistry.snapshotForProviders(providers));

    assertTrue(
        error.getMessage().contains("Duplicate IRCv3 extension name registered: echo-message"));
  }

  @Test
  void duplicateVisibleFeatureLabelsAreRejected() {
    ArrayList<Ircv3ExtensionDefinitionProvider> providers =
        new ArrayList<>(Ircv3ExtensionRegistry.builtInProviders());
    providers.add(
        new Ircv3ExtensionDefinitionProvider() {
          @Override
          public String providerId() {
            return "duplicate-visible-feature";
          }

          @Override
          public int sortOrder() {
            return 960;
          }

          @Override
          public List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
            return List.of(
                Ircv3ExtensionProviderSupport.feature(
                    960, "Replies", List.of("message-tags"), List.of()));
          }
        });

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> Ircv3ExtensionRegistry.snapshotForProviders(providers));

    assertTrue(
        error.getMessage().contains("Duplicate IRCv3 visible feature label registered: Replies"));
  }
}
