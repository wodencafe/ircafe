package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionContribution;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3FeatureContribution;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3SpecStatus;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3UiGroup;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ircv3ExtensionRegistryTest {

  @Test
  void channelContextIsAStableClientTagWithNoCapRequest() {
    var feature = Ircv3ExtensionRegistry.find("channel-context").orElseThrow();
    assertEquals(Ircv3ExtensionRegistry.SpecStatus.STABLE, feature.specStatus());
    assertEquals(Ircv3ExtensionRegistry.ExtensionKind.TAG_FEATURE, feature.kind());
    assertEquals("", feature.requestToken());
  }

  @Test
  void publishedClientTagsAndDraftFeaturesKeepTheirSpecificationStatus() {
    // Checked against https://ircv3.net/irc/ and its linked specs on 2026-09-18.
    for (String id : List.of("reply", "typing", "channel-context", "extended-monitor")) {
      assertEquals(
          Ircv3ExtensionRegistry.SpecStatus.STABLE,
          Ircv3ExtensionRegistry.find(id).orElseThrow().specStatus(),
          id);
    }
    for (String id :
        List.of(
            "react", "unreact", "read-marker", "multiline", "message-redaction", "chathistory")) {
      assertEquals(
          Ircv3ExtensionRegistry.SpecStatus.DRAFT,
          Ircv3ExtensionRegistry.find(id).orElseThrow().specStatus(),
          id);
    }
  }

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
  void extendedMonitorDraftAliasResolvesToFocusedCapability() {
    Ircv3ExtensionRegistry.ExtensionDefinition extendedMonitor =
        Ircv3ExtensionRegistry.find("draft/extended-monitor").orElseThrow();

    assertEquals("extended-monitor", extendedMonitor.id());
    assertEquals("extended-monitor", extendedMonitor.requestToken());
    assertEquals("extended-monitor", extendedMonitor.preferenceKey());
  }

  @Test
  void tagFeaturesAndNonRequestableCapabilitiesCannotProduceCapReqTokens() {
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("typing"));
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("draft/reply"));
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("draft/react"));
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("sts"));
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("monitor"));
    assertEquals("", Ircv3ExtensionRegistry.requestTokenFor("message-edit"));
    assertEquals("reply", Ircv3ExtensionRegistry.preferenceKeyFor("draft/reply"));
    assertEquals("react", Ircv3ExtensionRegistry.preferenceKeyFor("draft/react"));
    assertEquals("unreact", Ircv3ExtensionRegistry.preferenceKeyFor("draft/unreact"));
    assertEquals("typing", Ircv3ExtensionRegistry.preferenceKeyFor("draft/typing"));
    assertEquals(
        "channel-context", Ircv3ExtensionRegistry.preferenceKeyFor("draft/channel-context"));
    assertEquals("message-edit", Ircv3ExtensionRegistry.preferenceKeyFor("draft/message-edit"));
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
            "message-tags",
            "server-time",
            "echo-message",
            "standard-replies",
            "labeled-response",
            "account-notify",
            "away-notify",
            "extended-join",
            "chghost",
            "setname",
            "invite-notify",
            "monitor",
            "extended-monitor",
            "account-tag",
            "multi-prefix",
            "userhost-in-names",
            "cap-notify",
            "sts",
            "read-marker",
            "multiline",
            "message-redaction",
            "batch",
            "znc-playback",
            "chathistory",
            "reply",
            "reactions",
            "typing",
            "channel-context",
            "message-edit"),
        Ircv3ExtensionRegistry.providerIds());
  }

  @Test
  void serviceDescriptorUsesCanonicalSpiProviderName() {
    ClassLoader classLoader = Ircv3ExtensionProvider.class.getClassLoader();

    assertNotNull(
        classLoader.getResource("META-INF/services/" + Ircv3ExtensionProvider.class.getName()));
    assertNull(
        classLoader.getResource(
            "META-INF/services/cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionDefinitionProvider"));
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
    ArrayList<Ircv3ExtensionProvider> providers =
        new ArrayList<>(Ircv3ExtensionRegistry.defaultProviders());
    providers.add(
        new Ircv3ExtensionProvider() {
          @Override
          public String providerId() {
            return "duplicate-echo-message";
          }

          @Override
          public int sortOrder() {
            return 950;
          }

          @Override
          public List<Ircv3ExtensionContribution> extensions() {
            return List.of(
                Ircv3TestExtensionContributions.capability(
                    "plugin-echo-message-copy",
                    Ircv3SpecStatus.STABLE,
                    "echo-message",
                    "plugin-echo-message-copy",
                    "Echo message copy",
                    Ircv3UiGroup.OTHER,
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
  void duplicateProviderIdsAreRejected() {
    ArrayList<Ircv3ExtensionProvider> providers =
        new ArrayList<>(Ircv3ExtensionRegistry.defaultProviders());
    providers.add(
        new Ircv3ExtensionProvider() {
          @Override
          public String providerId() {
            return "message-tags";
          }

          @Override
          public int sortOrder() {
            return 950;
          }
        });

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> Ircv3ExtensionRegistry.snapshotForProviders(providers));

    assertTrue(error.getMessage().contains("Duplicate IRCv3 extension provider id registered"));
  }

  @Test
  void duplicateRequestTokensAreRejected() {
    ArrayList<Ircv3ExtensionProvider> providers =
        new ArrayList<>(Ircv3ExtensionRegistry.defaultProviders());
    providers.add(
        new Ircv3ExtensionProvider() {
          @Override
          public String providerId() {
            return "duplicate-request-token";
          }

          @Override
          public int sortOrder() {
            return 950;
          }

          @Override
          public List<Ircv3ExtensionContribution> extensions() {
            return List.of(
                Ircv3TestExtensionContributions.capability(
                    "plugin-read-marker-copy",
                    Ircv3SpecStatus.DRAFT,
                    "echo-message",
                    "plugin-read-marker-copy",
                    "Read marker copy",
                    Ircv3UiGroup.OTHER,
                    950,
                    "Conflicting request token test-only capability.",
                    "plugin/read-marker-copy"));
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
    ArrayList<Ircv3ExtensionProvider> providers =
        new ArrayList<>(Ircv3ExtensionRegistry.defaultProviders());
    providers.add(
        new Ircv3ExtensionProvider() {
          @Override
          public String providerId() {
            return "duplicate-visible-feature";
          }

          @Override
          public int sortOrder() {
            return 960;
          }

          @Override
          public List<Ircv3FeatureContribution> visibleFeatures() {
            return List.of(
                Ircv3TestExtensionContributions.feature(
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
