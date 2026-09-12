package cafe.woden.ircclient.architecture;

import static cafe.woden.ircclient.architecture.JavaSourceText.containsIgnoringWhitespace;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import cafe.woden.ircclient.irc.matrix.MatrixIrcv3RuntimeSupport;
import cafe.woden.ircclient.irc.pircbotx.client.PircbotxBotFactory;
import cafe.woden.ircclient.irc.pircbotx.listener.PircbotxBridgeListenerFactory;
import cafe.woden.ircclient.irc.pircbotx.parse.PircbotxInputParserHookInstaller;
import cafe.woden.ircclient.irc.quassel.QuasselIrcv3RuntimeSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeatureSubprojectBoundaryTest {

  private static final Pattern IMPORT_PATTERN =
      Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;");
  private static final Pattern PACKAGE_PATTERN =
      Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
  private static final Pattern FEATURE_INCLUDE_PATTERN =
      Pattern.compile("(?m)^\\s*include\\s+['\"](ircafe-feature-[\\w-]+)['\"]\\s*$");

  @Test
  void featureSubprojectsAreIncludedByTheRootApp() throws IOException {
    String build = Files.readString(Path.of("build.gradle"));

    for (String projectName : featureProjectNames()) {
      assertTrue(
          build.contains("implementation project(':" + projectName + "')")
              || build.contains("runtimeOnly project(':" + projectName + "')"),
          "the root app should include "
              + projectName
              + " so feature Spring beans are available at runtime");
    }
  }

  @Test
  void ircv3FeatureSubprojectsApplySharedBuildConvention() throws IOException {
    for (String projectName : featureProjectNames()) {
      if (!projectName.startsWith("ircafe-feature-ircv3-")) {
        continue;
      }
      Path buildFile = Path.of(projectName, "build.gradle");
      String build = Files.readString(buildFile);
      assertTrue(
          build.contains("apply from: rootProject.file('gradle/ircv3-feature-conventions.gradle')"),
          buildFile + " should apply the shared IRCv3 feature convention");
      assertTrue(
          !build.contains("JavaLanguageVersion.of")
              && !build.contains("springBootStarterTest")
              && !build.contains("cyclonedxDirectBom"),
          buildFile + " should not repeat shared Java, test, or CycloneDX setup");
    }
  }

  @Test
  void featureSubprojectsDoNotDeclareServiceLoaderProviders() throws IOException {
    Set<String> violations = new TreeSet<>();
    for (Path projectDir : featureProjectDirs()) {
      Path servicesDir = projectDir.resolve("src/main/resources/META-INF/services");
      if (Files.exists(servicesDir)) {
        try (Stream<Path> files = Files.walk(servicesDir)) {
          files
              .filter(Files::isRegularFile)
              .sorted()
              .forEach(path -> violations.add(path.toString()));
        }
      }
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "Feature subprojects own Spring/runtime behavior; ServiceLoader provider data belongs "
                + "in ircafe-builtins-* jars. Violations:\n  "
                + String.join("\n  ", violations));
  }

  @Test
  void featureSubprojectsDoNotImportRootImplementationTypes() throws IOException {
    Set<String> violations = new TreeSet<>();
    Set<String> featureClassNames = featureClassNames();

    for (Path sourceRoot : featureSourceRoots()) {
      try (Stream<Path> files = Files.walk(sourceRoot)) {
        for (Path file :
            files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
          Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(file));
          while (matcher.find()) {
            String dependency = matcher.group(1);
            if (isRootImplementationImport(dependency, featureClassNames)) {
              violations.add(file + " -> " + dependency);
            }
          }
        }
      }
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "Feature subprojects should not depend on root implementation packages directly. "
                + "Extract a narrow shared API/port first. Violations:\n  "
                + String.join("\n  ", violations));
  }

  @Test
  void filterRulePatchParsingLivesInCommandFeatureSubproject() throws IOException {
    Path featureRoot =
        Path.of("ircafe-feature-commands/src/main/java/cafe/woden/ircclient/app/commands");
    Path featureDispatcher = featureRoot.resolve("FilterCommandSpecParser.java");
    Path rootAdapter =
        Path.of("src/main/java/cafe/woden/ircclient/app/commands/FilterCommandAdapter.java");

    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterRulePatchParser.java")),
        "filter rule patch parsing should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterRulePatchSpec.java")),
        "the feature-safe filter rule patch value should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterScopePatternNormalizer.java")),
        "filter scope shorthand normalization should remain feature-owned");

    String mutationParserSource =
        Files.readString(featureRoot.resolve("FilterRuleMutationCommandParser.java"));
    assertTrue(
        mutationParserSource.contains("FilterRulePatchParser"),
        "the feature-owned mutation envelope should delegate rule-patch parsing to the feature parser");
    assertTrue(
        mutationParserSource.contains("FilterRulePatchSpec"),
        "the feature-owned mutation envelope should consume the feature-safe rule-patch value");

    String dispatcherSource = Files.readString(featureDispatcher);
    assertTrue(
        dispatcherSource.contains("FilterRuleMutationCommandParser"),
        "the feature /filter dispatcher should delegate rule mutations to the feature parser");
    assertTrue(
        !dispatcherSource.contains("FilterRulePatchParser"),
        "the dispatcher should not bypass the feature-owned mutation envelope");

    String adapterSource = Files.readString(rootAdapter);
    assertTrue(
        adapterSource.contains("FilterRuleMutationCommandAdapter"),
        "the root boundary should adapt feature mutation values through the mutation adapter");
    assertTrue(
        !adapterSource.contains("FilterRulePatchParser"),
        "root should not parse feature-owned filter rule patches");
  }

  @Test
  void filterDisplayCommandParsingLivesInCommandFeatureSubproject() throws IOException {
    Path featureRoot =
        Path.of("ircafe-feature-commands/src/main/java/cafe/woden/ircclient/app/commands");
    Path rootAdapter =
        Path.of("src/main/java/cafe/woden/ircclient/app/commands/FilterCommandAdapter.java");

    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterDisplayCommandParser.java")),
        "filter display/default/override parsing should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterDisplayCommandSpec.java")),
        "feature-safe filter display command values should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterToggleModeSpec.java")),
        "filter toggle parsing values should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterTriStateSpec.java")),
        "filter override tri-state values should remain feature-owned");

    String dispatcherSource = Files.readString(featureRoot.resolve("FilterCommandSpecParser.java"));
    assertTrue(
        dispatcherSource.contains("FilterDisplayCommandParser"),
        "the feature /filter dispatcher should delegate display parsing to the display parser");

    String adapterSource = Files.readString(rootAdapter);
    assertTrue(
        adapterSource.contains("FilterDisplayCommandAdapter"),
        "the root boundary should adapt display values at the root model boundary");
    assertTrue(
        !dispatcherSource.contains("private static FilterCommand parseDefaults"),
        "the feature dispatcher should not reclaim display parser internals");
    assertTrue(
        !dispatcherSource.contains("private static FilterCommand parseOverride"),
        "the feature dispatcher should not reclaim override parser internals");
  }

  @Test
  void filterManagementCommandParsingLivesInCommandFeatureSubproject() throws IOException {
    Path featureRoot =
        Path.of("ircafe-feature-commands/src/main/java/cafe/woden/ircclient/app/commands");
    Path rootAdapter =
        Path.of("src/main/java/cafe/woden/ircclient/app/commands/FilterCommandAdapter.java");

    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterManagementCommandParser.java")),
        "filter list/export/move parsing should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterManagementCommandSpec.java")),
        "feature-safe filter management values should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterMoveModeSpec.java")),
        "feature-safe filter move modes should remain feature-owned");

    String dispatcherSource = Files.readString(featureRoot.resolve("FilterCommandSpecParser.java"));
    assertTrue(
        dispatcherSource.contains("FilterManagementCommandParser"),
        "the feature /filter dispatcher should delegate management parsing to the management parser");

    String adapterSource = Files.readString(rootAdapter);
    assertTrue(
        adapterSource.contains("FilterManagementCommandAdapter"),
        "the root boundary should adapt management values at the root model boundary");
    assertTrue(
        !dispatcherSource.contains("private static FilterCommand parseExport"),
        "the feature dispatcher should not reclaim export parser internals");
    assertTrue(
        !dispatcherSource.contains("private static FilterCommand parseMove"),
        "the feature dispatcher should not reclaim move parser internals");
  }

  @Test
  void filterLifecycleCommandParsingLivesInCommandFeatureSubproject() throws IOException {
    Path featureRoot =
        Path.of("ircafe-feature-commands/src/main/java/cafe/woden/ircclient/app/commands");
    Path rootAdapter =
        Path.of("src/main/java/cafe/woden/ircclient/app/commands/FilterCommandAdapter.java");

    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterLifecycleCommandParser.java")),
        "filter rename/recreate and name/mask parsing should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterLifecycleCommandSpec.java")),
        "feature-safe filter lifecycle values should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterTargetActionSpec.java")),
        "feature-safe filter target actions should remain feature-owned");

    String dispatcherSource = Files.readString(featureRoot.resolve("FilterCommandSpecParser.java"));
    assertTrue(
        dispatcherSource.contains("FilterLifecycleCommandParser"),
        "the feature /filter dispatcher should delegate lifecycle parsing to the lifecycle parser");

    String adapterSource = Files.readString(rootAdapter);
    assertTrue(
        adapterSource.contains("FilterLifecycleCommandAdapter"),
        "the root boundary should adapt lifecycle values at the root model boundary");
    assertTrue(
        !dispatcherSource.contains("new FilterCommand.Rename"),
        "the feature dispatcher should not construct root rename values");
    assertTrue(
        !dispatcherSource.contains("new FilterCommand.Del"),
        "the feature dispatcher should not construct root target values");
  }

  @Test
  void filterRuleMutationCommandParsingLivesInCommandFeatureSubproject() throws IOException {
    Path featureRoot =
        Path.of("ircafe-feature-commands/src/main/java/cafe/woden/ircclient/app/commands");
    Path rootAdapter =
        Path.of("src/main/java/cafe/woden/ircclient/app/commands/FilterCommandAdapter.java");

    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterRuleMutationCommandParser.java")),
        "filter add/addreplace/set envelope parsing should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterRuleMutationCommandSpec.java")),
        "feature-safe filter rule mutation values should remain feature-owned");

    String dispatcherSource = Files.readString(featureRoot.resolve("FilterCommandSpecParser.java"));
    assertTrue(
        dispatcherSource.contains("FilterRuleMutationCommandParser"),
        "the feature /filter dispatcher should delegate mutation parsing to the mutation parser");

    String adapterSource = Files.readString(rootAdapter);
    assertTrue(
        adapterSource.contains("FilterRuleMutationCommandAdapter"),
        "the root boundary should adapt mutation values at the root model boundary");
    assertTrue(
        !dispatcherSource.contains("private static FilterCommand parseAddOrAddReplace"),
        "the feature dispatcher should not reclaim add/addreplace parser internals");
    assertTrue(
        !dispatcherSource.contains("new FilterCommand.Set"),
        "the feature dispatcher should not construct root set values");
  }

  @Test
  void filterCommandDispatchLivesInCommandFeatureSubproject() throws IOException {
    Path featureRoot =
        Path.of("ircafe-feature-commands/src/main/java/cafe/woden/ircclient/app/commands");
    Path rootParser =
        Path.of("src/main/java/cafe/woden/ircclient/app/commands/FilterCommandParser.java");
    Path rootAdapter =
        Path.of("src/main/java/cafe/woden/ircclient/app/commands/FilterCommandAdapter.java");

    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterCommandSpec.java")),
        "the complete feature-safe filter command value should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("FilterCommandSpecParser.java")),
        "top-level /filter tokenization and subcommand routing should remain feature-owned");
    assertTrue(
        Files.isRegularFile(rootAdapter),
        "root should keep one narrow adapter to its existing FilterCommand model");

    String dispatcherSource = Files.readString(featureRoot.resolve("FilterCommandSpecParser.java"));
    assertTrue(
        dispatcherSource.contains("CommandLineTokenizer.tokenize"),
        "the feature dispatcher should own top-level /filter tokenization");
    assertTrue(
        dispatcherSource.contains("Unknown /filter subcommand"),
        "the feature dispatcher should own unknown-subcommand policy");

    String rootParserSource = Files.readString(rootParser);
    assertTrue(
        rootParserSource.contains("FilterCommandSpecParser"),
        "the root parser should delegate to the feature-owned dispatcher");
    assertTrue(
        rootParserSource.contains("FilterCommandAdapter"),
        "the root parser should adapt only at the root model boundary");
    assertTrue(
        !rootParserSource.contains("CommandLineTokenizer"),
        "root should not reclaim top-level /filter tokenization");
    assertTrue(
        !rootParserSource.contains("switch ("),
        "root should not reclaim /filter subcommand routing");
    assertTrue(
        !rootParserSource.contains("FilterRuleMutationCommandParser"),
        "root should not bypass the feature dispatcher for mutation commands");
    assertTrue(
        !rootParserSource.contains("FilterDisplayCommandParser"),
        "root should not bypass the feature dispatcher for display commands");
    assertTrue(
        !rootParserSource.contains("FilterManagementCommandParser"),
        "root should not bypass the feature dispatcher for management commands");
    assertTrue(
        !rootParserSource.contains("FilterLifecycleCommandParser"),
        "root should not bypass the feature dispatcher for lifecycle commands");
  }

  @Test
  void bouncerProviderCompositionLivesInFeatureSubproject() throws IOException {
    Path featureCatalog =
        Path.of(
            "ircafe-feature-bouncer/src/main/java/cafe/woden/ircclient/bouncer/"
                + "BouncerPluginProviderCatalog.java");
    Path rootLoader =
        Path.of("src/main/java/cafe/woden/ircclient/bouncer/BouncerPluginProviders.java");

    assertTrue(
        Files.isRegularFile(featureCatalog),
        "pure bouncer provider-list composition should remain feature-owned");

    String featureSource = Files.readString(featureCatalog);
    assertTrue(
        featureSource.contains("mappingStrategies("),
        "the feature catalog should compose mapping-strategy providers");
    assertTrue(
        featureSource.contains("discoveryHandlers("),
        "the feature catalog should compose discovery-handler providers");
    assertTrue(
        !featureSource.contains("InstalledPluginsPort"),
        "the feature catalog should not load installed plugins directly");
    assertTrue(
        !featureSource.contains("PluginServiceLoaderSupport"),
        "the feature catalog should remain independent of root ServiceLoader support");

    String rootSource = Files.readString(rootLoader);
    assertTrue(
        rootSource.contains("BouncerPluginProviderCatalog.mappingStrategies"),
        "root should delegate mapping-strategy composition to the bouncer feature");
    assertTrue(
        rootSource.contains("BouncerPluginProviderCatalog.discoveryHandlers"),
        "root should delegate discovery-handler composition to the bouncer feature");
    assertTrue(
        rootSource.contains("InstalledPluginsPort"),
        "installed-plugin loading should remain root-owned");
    assertTrue(
        rootSource.contains("PluginServiceLoaderSupport.loadApplicationServices"),
        "application-classpath ServiceLoader access should remain root-owned");
    assertTrue(
        !rootSource.contains("appendDedupeByProviderClass"),
        "root should not reclaim pure provider-list composition policy");
  }

  @Test
  void bouncerMappingStrategySelectionLivesInFeatureSubproject() throws IOException {
    Path featureSelector =
        Path.of(
            "ircafe-feature-bouncer/src/main/java/cafe/woden/ircclient/bouncer/"
                + "BouncerMappingStrategySelector.java");
    Path featureCatalog =
        Path.of(
            "ircafe-feature-bouncer/src/main/java/cafe/woden/ircclient/bouncer/"
                + "BouncerBackendCatalog.java");
    Path rootRegistry =
        Path.of("src/main/java/cafe/woden/ircclient/bouncer/BouncerBackendRegistry.java");
    Path genericImporter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/bouncer/"
                + "GenericBouncerEphemeralNetworkImporter.java");
    Path sojuImporter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/soju/" + "SojuEphemeralNetworkImporter.java");
    Path zncImporter =
        Path.of("src/main/java/cafe/woden/ircclient/irc/znc/" + "ZncEphemeralNetworkImporter.java");

    assertTrue(
        Files.isRegularFile(featureSelector),
        "missing mapping-strategy selection policy should remain feature-owned");

    String selectorSource = Files.readString(featureSelector);
    assertTrue(
        selectorSource.contains("Missing bouncer mapping strategy: "),
        "the feature selector should own the lazy missing-provider diagnostic");
    assertTrue(
        !selectorSource.contains("BouncerBackendRegistry"),
        "the feature selector should remain independent of the root registry");

    String catalogSource = Files.readString(featureCatalog);
    assertTrue(
        catalogSource.contains("mappingStrategyOrMissing"),
        "the feature catalog should expose resolved-or-missing strategy selection");
    assertTrue(
        catalogSource.contains("BouncerMappingStrategySelector"),
        "the feature catalog should delegate missing-provider policy to the feature selector");

    String registrySource = Files.readString(rootRegistry);
    assertTrue(
        registrySource.contains("catalog.mappingStrategyOrMissing"),
        "the root registry should delegate selection to the feature catalog");

    for (Path importer : List.of(genericImporter, sojuImporter, zncImporter)) {
      String importerSource = Files.readString(importer);
      assertTrue(
          importerSource.contains("mappingStrategyOrMissing"),
          importer + " should consume the shared registry selection contract");
      assertTrue(
          !importerSource.contains("missingMappingStrategy"),
          importer + " should not recreate missing-provider policy in root");
      assertTrue(
          !importerSource.contains("Missing bouncer mapping strategy: "),
          importer + " should not own the missing-provider diagnostic");
    }
  }

  @Test
  void bouncerDiscoveryEventRoutingLivesInFeatureSubproject() throws IOException {
    Path featureRouter =
        Path.of(
            "ircafe-feature-bouncer/src/main/java/cafe/woden/ircclient/bouncer/"
                + "BouncerDiscoveryEventRouter.java");
    Path featureCatalog =
        Path.of(
            "ircafe-feature-bouncer/src/main/java/cafe/woden/ircclient/bouncer/"
                + "BouncerDiscoveryHandlerCatalog.java");
    Path rootDispatcher =
        Path.of(
            "src/main/java/cafe/woden/ircclient/bouncer/" + "BouncerDiscoveryEventDispatcher.java");

    assertTrue(
        Files.isRegularFile(featureRouter),
        "discovery event routing over resolved handlers should remain feature-owned");

    String routerSource = Files.readString(featureRouter);
    assertTrue(
        routerSource.contains("BouncerDiscoveryHandlerCatalog.fromHandlers"),
        "the feature router should build on the feature-owned handler catalog");
    assertTrue(
        routerSource.contains("routeNetworkDiscovered"),
        "the feature router should own discovered-network dispatch");
    assertTrue(
        routerSource.contains("routeOriginDisconnected"),
        "the feature router should own origin-disconnect dispatch");
    assertTrue(
        !routerSource.contains("InstalledPluginsPort"),
        "the feature router should not load installed plugins directly");
    assertTrue(
        !routerSource.contains("ObjectProvider"),
        "the feature router should remain independent of Spring provider adaptation");

    String catalogSource = Files.readString(featureCatalog);
    assertTrue(
        catalogSource.contains("handler(String backendId)"),
        "the feature catalog should retain normalized handler lookup");

    String dispatcherSource = Files.readString(rootDispatcher);
    assertTrue(
        dispatcherSource.contains("BouncerDiscoveryEventRouter.fromHandlers"),
        "root should adapt resolved handlers into the feature router");
    assertTrue(
        dispatcherSource.contains("eventRouter.routeNetworkDiscovered"),
        "root should delegate discovered-network routing to the feature");
    assertTrue(
        dispatcherSource.contains("eventRouter.routeOriginDisconnected"),
        "root should delegate origin-disconnect routing to the feature");
    assertTrue(
        dispatcherSource.contains("BouncerPluginProviders.backendDiscoveryHandlers"),
        "installed/application handler loading should remain root-owned");
    assertTrue(
        dispatcherSource.contains("InstalledPluginsPort"),
        "installed-plugin access should remain root-owned");
    assertTrue(
        !dispatcherSource.contains("BouncerDiscoveryHandlerCatalog"),
        "root should not reclaim feature-owned handler lookup");
  }

  @Test
  void bouncerDiscoveredNetworkMaterializationLivesInFeatureSubproject() throws IOException {
    Path featureMaterializer =
        Path.of(
            "ircafe-feature-bouncer/src/main/java/cafe/woden/ircclient/bouncer/"
                + "BouncerDiscoveredNetworkMaterializer.java");
    Path genericParser =
        Path.of(
            "ircafe-feature-bouncer/src/main/java/cafe/woden/ircclient/bouncer/"
                + "GenericBouncerDiscoveryLineParser.java");
    Path sojuAdapter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/soju/" + "SojuBouncerDiscoveryAdapter.java");
    Path zncAdapter =
        Path.of("src/main/java/cafe/woden/ircclient/irc/znc/" + "ZncBouncerDiscoveryAdapter.java");

    assertTrue(
        Files.isRegularFile(featureMaterializer),
        "discovered-network fallback and metadata policy should remain feature-owned");

    String materializerSource = Files.readString(featureMaterializer);
    assertTrue(
        materializerSource.contains("fromGenericProtocol"),
        "the feature materializer should own generic protocol fallbacks");
    assertTrue(
        materializerSource.contains("fromSojuNetwork"),
        "the feature materializer should own Soju event construction");
    assertTrue(
        materializerSource.contains("fromZncListNetworksRow"),
        "the feature materializer should own ZNC event construction");
    assertTrue(
        materializerSource.contains("new BouncerDiscoveredNetwork"),
        "the feature materializer should construct the plugin API discovery value");

    for (Path adapter : List.of(genericParser, sojuAdapter, zncAdapter)) {
      String adapterSource = Files.readString(adapter);
      assertTrue(
          adapterSource.contains("BouncerDiscoveredNetworkMaterializer"),
          adapter + " should delegate event materialization to the bouncer feature");
      assertTrue(
          !adapterSource.contains("new BouncerDiscoveredNetwork("),
          adapter + " should not construct discovery events directly");
      assertTrue(
          !adapterSource.contains("loginUserHint("),
          adapter + " should not duplicate login-hint extraction");
      assertTrue(
          !adapterSource.contains("capabilityFlags("),
          adapter + " should not duplicate capability extraction");
    }
  }

  @Test
  void bouncerProtocolDiscoveryParsingLivesInFeatureSubproject() throws IOException {
    Path featureRoot = Path.of("ircafe-feature-bouncer/src/main/java/cafe/woden/ircclient/bouncer");
    Path sojuParser = featureRoot.resolve("SojuBouncerProtocolParser.java");
    Path zncParser = featureRoot.resolve("ZncBouncerListNetworksParser.java");
    Path rootSojuParser =
        Path.of("src/main/java/cafe/woden/ircclient/irc/soju/PircbotxSojuParsers.java");
    Path rootZncParser =
        Path.of("src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/PircbotxZncParsers.java");
    Path zncPlaybackDetector =
        Path.of(
            "ircafe-feature-ircv3-znc-playback/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3/Ircv3ZncDetector.java");

    assertTrue(
        Files.isRegularFile(sojuParser),
        "Soju discovery protocol parsing should remain feature-owned");
    assertTrue(
        Files.isRegularFile(zncParser), "ZNC ListNetworks parsing should remain feature-owned");
    assertTrue(
        !Files.exists(rootSojuParser), "the obsolete root Soju parser should not be reintroduced");

    String sojuAdapterSource =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/soju/"
                    + "SojuBouncerDiscoveryAdapter.java"));
    String isupportObserverSource =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/listener/"
                    + "PircbotxIsupportObserver.java"));
    String sojuStoreSource =
        Files.readString(
            Path.of("src/main/java/cafe/woden/ircclient/irc/soju/SojuAutoConnectStore.java"));
    String zncAdapterSource =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/znc/" + "ZncBouncerDiscoveryAdapter.java"));
    assertTrue(
        !Files.exists(rootZncParser),
        "the obsolete root ZNC heuristic parser should not be reintroduced");
    assertTrue(
        Files.isRegularFile(zncPlaybackDetector),
        "generic ZNC capability and RPL 004 detection should remain znc-playback-owned");
    String historyTransportDetectorSource = Files.readString(zncPlaybackDetector);

    assertTrue(
        sojuAdapterSource.contains("SojuBouncerProtocolParser"),
        "the Soju discovery adapter should delegate network-line parsing to the feature");
    assertTrue(
        isupportObserverSource.contains("SojuBouncerProtocolParser"),
        "the ISUPPORT observer should delegate BOUNCER_NETID parsing to the feature");
    assertTrue(
        sojuStoreSource.contains("BouncerAutoConnectNetworkKeyNormalizer"),
        "Soju auto-connect keys should reuse feature-owned normalization");
    assertTrue(
        zncAdapterSource.contains("ZncBouncerListNetworksParser"),
        "the ZNC discovery adapter should delegate ListNetworks parsing to the feature");
    assertTrue(
        !historyTransportDetectorSource.contains("parseListNetworksRow"),
        "generic ZNC detection should not reclaim ListNetworks row parsing");
    assertTrue(
        !historyTransportDetectorSource.contains("looksLikeListNetworksDoneLine"),
        "generic ZNC detection should not reclaim ListNetworks completion parsing");
  }

  @Test
  void bouncerAutoConnectRuleStateLivesInFeatureSubproject() throws IOException {
    Path featureRoot = Path.of("ircafe-feature-bouncer/src/main/java/cafe/woden/ircclient/bouncer");
    Path rulesState = featureRoot.resolve("BouncerAutoConnectRulesState.java");
    Path zncNormalizer = featureRoot.resolve("ZncAutoConnectNetworkKeyNormalizer.java");
    Path abstractStore =
        Path.of(
            "src/main/java/cafe/woden/ircclient/bouncer/" + "AbstractBouncerAutoConnectStore.java");
    Path zncStore =
        Path.of("src/main/java/cafe/woden/ircclient/irc/znc/" + "ZncAutoConnectStore.java");

    assertTrue(
        Files.isRegularFile(rulesState),
        "auto-connect seed, lookup, snapshot, and mutation policy should remain feature-owned");
    assertTrue(
        Files.isRegularFile(zncNormalizer),
        "ZNC auto-connect key normalization should remain feature-owned");

    String stateSource = Files.readString(rulesState);
    assertTrue(
        stateSource.contains("replace("),
        "the feature state should own seed replacement and cleanup");
    assertTrue(
        stateSource.contains("networksForBouncer("),
        "the feature state should own case-insensitive bouncer lookup");
    assertTrue(
        stateSource.contains("setEnabled("),
        "the feature state should own normalized add/remove mutation");
    assertTrue(
        !stateSource.contains("BehaviorProcessor"),
        "feature state should remain independent of Rx update streams");
    assertTrue(
        !stateSource.contains("BouncerDiscoveryConfigPort"),
        "feature state should remain independent of runtime-config persistence");

    String storeSource = Files.readString(abstractStore);
    assertTrue(
        storeSource.contains("BouncerAutoConnectRulesState"),
        "the root store should delegate pure rule state to the bouncer feature");
    assertTrue(
        storeSource.contains("persistAutoConnectRule"),
        "runtime-config persistence should remain root-owned");
    assertTrue(
        storeSource.contains("BehaviorProcessor"),
        "Rx update publication should remain root-owned");
    assertTrue(
        !storeSource.contains("new LinkedHashMap"),
        "the root store should not reclaim the feature-owned rule map");

    String zncStoreSource = Files.readString(zncStore);
    assertTrue(
        zncStoreSource.contains("ZncAutoConnectNetworkKeyNormalizer"),
        "the ZNC store should delegate key normalization to the feature");
    assertTrue(
        !zncStoreSource.contains("sanitizeZncNetworkSegment"),
        "the ZNC store should not duplicate network-key sanitization");
    assertTrue(
        !zncStoreSource.contains("replaceAll(\"_+\""),
        "the ZNC store should not duplicate underscore normalization");
  }

  @Test
  void ircv3MetadataCatalogLivesInFeatureSubproject() throws IOException {
    Path featureCatalog =
        Path.of(
            "ircafe-feature-ircv3-negotiation/src/main/java/cafe/woden/ircclient/irc/ircv3/"
                + "Ircv3ExtensionMetadataCatalog.java");
    Path rootRegistry =
        Path.of("src/main/java/cafe/woden/ircclient/irc/ircv3/" + "Ircv3ExtensionRegistry.java");

    assertTrue(
        Files.isRegularFile(featureCatalog),
        "pure IRCv3 provider aggregation should remain feature-owned");

    String featureSource = Files.readString(featureCatalog);
    assertTrue(
        featureSource.contains("normalizeProviders("),
        "the IRCv3 feature should own provider ordering and duplicate-id policy");
    assertTrue(
        featureSource.contains("indexExtensions("),
        "the IRCv3 feature should own extension alias indexing and conflict detection");
    assertTrue(
        featureSource.contains("collectVisibleFeatures("),
        "the IRCv3 feature should own visible-feature ordering and conflict detection");
    assertTrue(
        !featureSource.contains("PluginServiceLoaderSupport"),
        "the IRCv3 feature should operate only on already-resolved providers");
    assertTrue(
        !featureSource.contains("InstalledPluginsPort"),
        "installed-plugin loading should remain root-owned");
    assertTrue(
        !featureSource.contains("org.springframework"),
        "the initial IRCv3 feature catalog should remain Spring-independent");

    String rootSource = Files.readString(rootRegistry);
    assertTrue(
        rootSource.contains("Ircv3ExtensionMetadataCatalog.snapshot"),
        "the root compatibility facade should delegate metadata policy to the IRCv3 feature");
    assertTrue(
        rootSource.contains("PluginServiceLoaderSupport.loadApplicationServices"),
        "application ServiceLoader ownership should remain in root");
    assertTrue(
        !rootSource.contains("private static List<Ircv3ExtensionProvider> normalizeProviders"),
        "root should not reclaim feature-owned provider normalization");
    assertTrue(
        !rootSource.contains("private static Map<String, ExtensionDefinition> indexDefinitions"),
        "root should not reclaim feature-owned extension indexing");
  }

  @Test
  void ircv3FeatureReadinessEvaluationLivesInFeatureSubproject() throws IOException {
    Path featureEvaluator =
        Path.of(
            "ircafe-feature-ircv3-negotiation/src/main/java/cafe/woden/ircclient/irc/ircv3/"
                + "Ircv3FeatureAvailabilityEvaluator.java");
    Path featureCatalog =
        Path.of(
            "ircafe-feature-ircv3-negotiation/src/main/java/cafe/woden/ircclient/irc/ircv3/"
                + "Ircv3ExtensionMetadataCatalog.java");
    Path rootDialog =
        Path.of(
            "src/main/java/cafe/woden/ircclient/ui/servertree/view/"
                + "ServerTreeNetworkInfoDialogBuilder.java");

    assertTrue(
        Files.isRegularFile(featureEvaluator),
        "provider-defined IRCv3 feature readiness policy should remain feature-owned");

    String evaluatorSource = Files.readString(featureEvaluator);
    assertTrue(
        evaluatorSource.contains("missingRequiredAll"),
        "the IRCv3 feature should own required-all capability evaluation");
    assertTrue(
        evaluatorSource.contains("missingRequiredAny"),
        "the IRCv3 feature should own required-any capability evaluation");
    assertTrue(
        evaluatorSource.contains("Readiness.PARTIAL"),
        "the IRCv3 feature should own partial-readiness classification");
    assertTrue(
        !evaluatorSource.contains("javax.swing"),
        "feature readiness evaluation should remain Swing-independent");
    assertTrue(
        !evaluatorSource.contains("ServerRuntimeMetadata"),
        "feature readiness evaluation should not depend on root UI state models");

    String catalogSource = Files.readString(featureCatalog);
    assertTrue(
        catalogSource.contains("Ircv3FeatureAvailabilityEvaluator.evaluate"),
        "the feature metadata snapshot should expose readiness evaluation over visible features");

    String dialogSource = Files.readString(rootDialog);
    assertTrue(
        dialogSource.contains("evaluateVisibleFeatures"),
        "the network-info UI should delegate readiness policy to the IRCv3 feature");
    assertTrue(
        !dialogSource.contains("for (String required : feature.requiredAll())"),
        "the network-info UI should not reclaim required-all evaluation policy");
    assertTrue(
        !dialogSource.contains("for (String candidate : feature.requiredAny())"),
        "the network-info UI should not reclaim required-any evaluation policy");
  }

  @Test
  void ircv3TransportIndependentCommandPolicyLivesInFeatureSubproject() throws IOException {
    Path umbrellaRoot =
        Path.of("ircafe-feature-ircv3/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path chatHistoryRoot =
        Path.of(
            "ircafe-feature-ircv3-chat-history/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path multilineRoot =
        Path.of("ircafe-feature-ircv3-multiline/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path rootIrcv3 = Path.of("src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path capabilityCommands =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/client/"
                + "PircbotxCapabilityCommandSupport.java");
    Path multilineTransport =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/client/"
                + "PircbotxMultilineMessageSupport.java");
    Path outboundMultiline =
        Path.of(
            "src/main/java/cafe/woden/ircclient/app/outbound/messaging/"
                + "OutboundMultilineMessageSupport.java");
    Path multilineFeatureSupport =
        Path.of(
            "src/main/java/cafe/woden/ircclient/app/api/" + "Ircv3MultilineFeatureSupport.java");
    Path outboundChatHistory =
        Path.of(
            "src/main/java/cafe/woden/ircclient/app/outbound/chathistory/"
                + "OutboundChatHistoryCommandService.java");

    assertTrue(
        Files.isRegularFile(chatHistoryRoot.resolve("Ircv3ChatHistoryCommandBuilder.java")),
        "transport-independent CHATHISTORY command policy should remain feature-owned");
    assertTrue(
        Files.isRegularFile(chatHistoryRoot.resolve("Ircv3ChatHistorySelectors.java")),
        "CHATHISTORY selector constants should remain feature-owned");
    assertTrue(
        Files.isRegularFile(chatHistoryRoot.resolve("Ircv3ChatHistoryAvailability.java")),
        "CHATHISTORY capability dependency policy should remain feature-owned");
    assertTrue(
        Files.isRegularFile(multilineRoot.resolve("Ircv3MultilineSupport.java")),
        "multiline capability and limit policy should remain feature-owned");
    assertTrue(
        Files.isRegularFile(multilineRoot.resolve("Ircv3MultilineMessagePolicy.java")),
        "multiline payload normalization and size policy should remain feature-owned");
    assertTrue(
        Files.isRegularFile(multilineRoot.resolve("Ircv3MultilinePayload.java")),
        "normalized multiline payload facts should remain feature-owned");
    assertTrue(
        Files.isRegularFile(multilineRoot.resolve("Ircv3MultilineCommandPlanner.java")),
        "multiline raw-line planning should remain feature-owned");
    assertTrue(
        Files.isRegularFile(multilineRoot.resolve("Ircv3MultilineLimitPolicy.java")),
        "multiline negotiated-limit reasoning should remain feature-owned");
    assertTrue(
        !Files.exists(rootIrcv3.resolve("Ircv3ChatHistoryCommandBuilder.java")),
        "root should not keep a duplicate CHATHISTORY command builder");
    assertTrue(
        !Files.exists(rootIrcv3.resolve("Ircv3ChatHistorySelectors.java")),
        "root should not keep duplicate CHATHISTORY selectors");
    assertTrue(
        !Files.exists(umbrellaRoot.resolve("Ircv3ChatHistoryCommandBuilder.java")),
        "the compatibility umbrella should not retain CHATHISTORY command policy");
    assertTrue(
        !Files.exists(umbrellaRoot.resolve("Ircv3ChatHistorySelectors.java")),
        "the compatibility umbrella should not retain CHATHISTORY selector constants");
    assertTrue(
        !Files.exists(rootIrcv3.resolve("Ircv3MultilineSupport.java")),
        "root should not keep duplicate multiline policy");
    assertTrue(
        !Files.exists(umbrellaRoot.resolve("Ircv3MultilineSupport.java")),
        "the compatibility umbrella should not retain multiline support implementation");
    assertTrue(
        !Files.exists(umbrellaRoot.resolve("Ircv3MultilineMessagePolicy.java")),
        "the compatibility umbrella should not retain multiline payload implementation");

    String historySource =
        Files.readString(chatHistoryRoot.resolve("Ircv3ChatHistoryCommandBuilder.java"));
    assertTrue(
        historySource.contains("sanitizeSelector("),
        "the IRCv3 feature should own CHATHISTORY selector validation");
    assertTrue(
        historySource.contains("clampLimit("),
        "the IRCv3 feature should own CHATHISTORY limit policy");
    assertTrue(
        historySource.contains("normalizeRequestSelectorOrEmpty("),
        "the IRCv3 feature should own user-entered CHATHISTORY selector normalization");
    assertTrue(
        !historySource.contains("org.pircbotx"),
        "feature-owned command construction should remain transport-independent");

    String multilineSource = Files.readString(multilineRoot.resolve("Ircv3MultilineSupport.java"));
    assertTrue(
        multilineSource.contains("negotiatedBatchType("),
        "the IRCv3 feature should own final-versus-draft multiline negotiation policy");
    assertTrue(
        multilineSource.contains("parseLimitParams("),
        "the IRCv3 feature should own multiline limit-parameter parsing");
    assertTrue(
        !multilineSource.contains("cafe.woden.ircclient.util"),
        "feature-owned multiline policy should not import root capability constants");

    String payloadPolicySource =
        Files.readString(multilineRoot.resolve("Ircv3MultilineMessagePolicy.java"));
    assertTrue(
        payloadPolicySource.contains("normalizeLines("),
        "the IRCv3 feature should own multiline line-ending normalization");
    assertTrue(
        payloadPolicySource.contains("payloadUtf8Bytes("),
        "the IRCv3 feature should own multiline payload byte accounting");
    assertTrue(
        payloadPolicySource.contains("requireWithinMaxBytes("),
        "the IRCv3 feature should own negotiated byte-limit validation");
    assertTrue(
        !payloadPolicySource.contains("org.pircbotx"),
        "feature-owned multiline payload policy should remain transport-independent");

    String capabilityCommandSource = Files.readString(capabilityCommands);
    assertTrue(
        capabilityCommandSource.contains("Ircv3OutboundCommandRuntimeCatalog"),
        "the PircBotX command adapter should delegate command construction to runtime SPI providers");
    assertTrue(
        capabilityCommandSource.contains("Ircv3ChatHistoryRuntimeSupport")
            && capabilityCommandSource.contains("chatHistoryRuntimeSupport.before")
            && capabilityCommandSource.contains("plan.rawLine()"),
        "the PircBotX command adapter should validate provider-selected CHATHISTORY plans");
    assertTrue(
        capabilityCommandSource.contains("Ircv3OutboundCommandOperation.CHAT_HISTORY_BEFORE"),
        "the PircBotX command adapter should route CHATHISTORY through the runtime SPI operation");
    assertTrue(
        capabilityCommandSource.contains("Ircv3ChatHistoryAvailability"),
        "the PircBotX command adapter should retain transport-side CHATHISTORY dependency gating");
    assertTrue(
        !capabilityCommandSource.contains("Ircv3ChatHistoryCommandBuilder"),
        "the PircBotX command adapter should not bypass the runtime SPI catalog");
    String chatHistoryAppSource = Files.readString(outboundChatHistory);
    assertTrue(
        chatHistoryAppSource.contains("Ircv3ChatHistoryRuntimeSupport")
            && chatHistoryAppSource.contains("plan.rawLine()")
            && !chatHistoryAppSource.contains("Ircv3ChatHistoryCommandBuilder"),
        "the outbound CHATHISTORY flow should consume provider-selected normalized plans");
    assertTrue(
        !chatHistoryAppSource.contains("DateTimeFormatter"),
        "the outbound CHATHISTORY flow should not retain timestamp formatting policy");

    String multilineTransportSource = Files.readString(multilineTransport);
    assertTrue(
        multilineTransportSource.contains("Ircv3OutboundCommandOperation.MULTILINE"),
        "the PircBotX multiline adapter should route raw-line planning through runtime SPI");
    assertTrue(
        !multilineTransportSource.contains("Ircv3MultilineCommandPlanner"),
        "the PircBotX multiline adapter should not bypass the runtime SPI catalog");
    assertTrue(
        !multilineTransportSource.contains("BATCH +"),
        "the PircBotX multiline adapter should not render BATCH lines itself");
    assertTrue(
        Files.readString(outboundMultiline).contains("Ircv3MultilinePayload"),
        "outbound multiline planning should reuse feature-owned payload facts");
    assertTrue(
        Files.readString(multilineFeatureSupport).contains("Ircv3MultilineLimitPolicy"),
        "application availability support should delegate negotiated-limit reasoning");
  }

  @Test
  void ircv3ReplyAndReactionDraftPoliciesLiveInFocusedSubprojects() throws IOException {
    Path commonRoot =
        Path.of("ircafe-feature-ircv3-common/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path replyRoot =
        Path.of("ircafe-feature-ircv3-reply/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path reactionsRoot =
        Path.of("ircafe-feature-ircv3-reactions/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path multilineRoot =
        Path.of("ircafe-feature-ircv3-multiline/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path messageTagRoot =
        Path.of(
            "ircafe-feature-ircv3-message-tags/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path rootIrcv3 = Path.of("src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path oldDraftRoot =
        Path.of("ircafe-feature-ircv3-draft/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path oldAccumulator =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/support/"
                + "Ircv3MultilineAccumulator.java");
    Path clientTagFacade =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/"
                + "PircbotxClientTagParsers.java");
    Path messageInput =
        Path.of("src/main/java/cafe/woden/ircclient/ui/input/MessageInputPanel.java");

    assertTrue(
        Files.isRegularFile(commonRoot.resolve("Ircv3TaggedCommandDraft.java")),
        "shared tagged-command draft parsing should remain in IRCv3 common");
    assertTrue(
        Files.isRegularFile(replyRoot.resolve("Ircv3ReplyDraftPolicy.java")),
        "reply draft cleanup should remain reply-feature-owned");
    assertTrue(
        Files.isRegularFile(reactionsRoot.resolve("Ircv3ReactionDraftPolicy.java")),
        "reaction prefill cleanup should remain reactions-feature-owned");
    assertTrue(
        Files.isRegularFile(multilineRoot.resolve("Ircv3MultilineAccumulator.java")),
        "IRCv3 multiline reassembly should remain feature-owned");
    assertTrue(
        Files.isRegularFile(messageTagRoot.resolve("Ircv3ClientTagPolicy.java")),
        "CLIENTTAGDENY parsing and allow policy should remain message-tag feature-owned");
    assertTrue(
        !Files.exists(rootIrcv3.resolve("Ircv3DraftNormalizer.java")),
        "root should not keep the obsolete combined draft normalizer");
    assertTrue(
        !Files.exists(oldDraftRoot.resolve("Ircv3DraftNormalizer.java")),
        "the ambiguous draft project should not retain combined reply/reaction policy");
    assertTrue(
        !Files.exists(oldAccumulator),
        "the PircBotX support package should not keep a duplicate multiline accumulator");

    String parserSource = Files.readString(commonRoot.resolve("Ircv3TaggedCommandDraft.java"));
    assertTrue(
        parserSource.contains("withoutTags("),
        "IRCv3 common should own transport-independent staged tag removal");
    assertTrue(
        parserSource.contains("hasAnyTag("),
        "IRCv3 common should own normalized staged tag lookup");
    assertTrue(
        !parserSource.contains("javax.swing"),
        "shared tagged-command parsing should remain Swing-independent");

    String replySource = Files.readString(replyRoot.resolve("Ircv3ReplyDraftPolicy.java"));
    assertTrue(
        replySource.contains("draft/reply"),
        "the reply feature should own final and legacy reply-tag cleanup");
    assertTrue(
        replySource.contains("Ircv3TaggedCommandDraft"),
        "the reply feature should reuse shared tagged-command parsing");
    assertTrue(
        !replySource.contains("draft/react"),
        "the reply feature should not own reaction capability policy");

    String reactionSource =
        Files.readString(reactionsRoot.resolve("Ircv3ReactionDraftPolicy.java"));
    assertTrue(
        reactionSource.contains("draft/react"),
        "the reactions feature should own reaction prefill cleanup");
    assertTrue(
        reactionSource.contains("draft/unreact"),
        "the reactions feature should own unreaction prefill cleanup");
    assertTrue(
        reactionSource.contains("Ircv3TaggedCommandDraft"),
        "the reactions feature should reuse shared tagged-command parsing");
    assertTrue(
        !reactionSource.contains("withoutTags"),
        "reaction invalidation should not reclaim reply-tag removal policy");

    String inputSource = Files.readString(messageInput);
    assertTrue(
        inputSource.contains("Ircv3ReactionDraftPolicy.normalizeForCapabilities"),
        "the Swing composition point should delegate reaction invalidation to the reactions feature");
    assertTrue(
        inputSource.contains("Ircv3ReplyDraftPolicy.normalizeForCapability"),
        "the Swing composition point should delegate reply cleanup to the reply feature");
    assertTrue(
        !inputSource.contains("Ircv3DraftNormalizer"),
        "the Swing composition point should not depend on the obsolete combined normalizer");

    String accumulatorSource =
        Files.readString(multilineRoot.resolve("Ircv3MultilineAccumulator.java"));
    assertTrue(
        accumulatorSource.contains("FoldResult"),
        "the IRCv3 feature should own multiline fold results");
    assertTrue(
        accumulatorSource.contains("pruneExpired("),
        "the IRCv3 feature should own multiline buffer expiry policy");
    assertTrue(
        !accumulatorSource.contains("org.pircbotx"),
        "feature-owned multiline reassembly should remain transport-independent");

    String clientTagPolicy = Files.readString(messageTagRoot.resolve("Ircv3ClientTagPolicy.java"));
    assertTrue(
        clientTagPolicy.contains("parseRpl005ClientTagDenyValue("),
        "the IRCv3 feature should own CLIENTTAGDENY token parsing");
    assertTrue(
        clientTagPolicy.contains("isClientOnlyTagAllowed("),
        "the IRCv3 feature should own client-only tag allow policy");

    assertTrue(
        !Files.exists(clientTagFacade),
        "root should not keep a PircBotX-only CLIENTTAGDENY compatibility facade");
    String isupportObserver =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/listener/"
                    + "PircbotxIsupportObserver.java"));
    assertTrue(
        isupportObserver.contains("Ircv3IsupportRuntimeSupport")
            && !isupportObserver.contains("Ircv3ClientTagPolicy"),
        "the ISUPPORT adapter should consume CLIENTTAGDENY policy through runtime SPI");
  }

  @Test
  void ircv3TaggedMessageSignalsLiveInCapabilitySubprojects() throws IOException {
    Path messageTagsRoot =
        Path.of(
            "ircafe-feature-ircv3-message-tags/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path channelContextRoot =
        Path.of(
            "ircafe-feature-ircv3-channel-context/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3");
    Path replyRoot =
        Path.of("ircafe-feature-ircv3-reply/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path reactionsRoot =
        Path.of("ircafe-feature-ircv3-reactions/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path typingRoot =
        Path.of("ircafe-feature-ircv3-typing/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path readMarkerRoot =
        Path.of(
            "ircafe-feature-ircv3-read-marker/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path redactionRoot =
        Path.of(
            "ircafe-feature-ircv3-message-redaction/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3");
    Path rootAdapter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/"
                + "PircbotxTagSignalSupport.java");
    Path quasselRuntime =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/quassel/" + "QuasselIrcv3RuntimeSupport.java");

    assertTrue(
        Files.readString(messageTagsRoot.resolve("Ircv3Tags.java"))
            .contains("firstDecodedTagValue"),
        "message-tags should own normalized decoded tag lookup");
    assertTrue(
        Files.isRegularFile(channelContextRoot.resolve("Ircv3ChannelContextPolicy.java")),
        "channel-context target selection should remain capability-owned");
    assertTrue(
        Files.isRegularFile(replyRoot.resolve("Ircv3ReplyTagSignal.java")),
        "reply tag interpretation should remain reply-feature-owned");
    assertTrue(
        Files.isRegularFile(reactionsRoot.resolve("Ircv3ReactionTagSignal.java")),
        "reaction tag interpretation should remain reactions-feature-owned");
    assertTrue(
        Files.isRegularFile(typingRoot.resolve("Ircv3TypingTagSignal.java")),
        "typing tag interpretation should remain typing-feature-owned");
    assertTrue(
        Files.isRegularFile(readMarkerRoot.resolve("Ircv3ReadMarkerTagSignal.java")),
        "read-marker tag interpretation should remain read-marker-feature-owned");
    assertTrue(
        Files.isRegularFile(readMarkerRoot.resolve("Ircv3ReadMarkerTimestamp.java")),
        "read-marker timestamp parsing should remain read-marker-feature-owned");
    assertTrue(
        Files.isRegularFile(readMarkerRoot.resolve("Ircv3ReadMarkerCommandSignal.java")),
        "MARKREAD command parsing should remain read-marker-feature-owned");
    assertTrue(
        Files.isRegularFile(redactionRoot.resolve("Ircv3MessageRedactionTagSignal.java")),
        "message-redaction tag interpretation should remain redaction-feature-owned");
    assertTrue(
        Files.isRegularFile(redactionRoot.resolve("Ircv3MessageRedactionCommandSignal.java")),
        "REDACT command parsing should remain redaction-feature-owned");

    String adapterSource = Files.readString(rootAdapter);
    assertTrue(
        adapterSource.contains("Ircv3ChannelContextRuntimeSupport")
            && adapterSource.contains("channelContextRuntimeSupport.resolve(request)")
            && !adapterSource.contains("Ircv3InboundTagOperation.CHANNEL_CONTEXT")
            && adapterSource.contains("Ircv3MessageMutationRuntimeSupport")
            && adapterSource.contains(".conversationSignals(request)")
            && !adapterSource.contains("Ircv3InboundTagOperation.REPLY")
            && !adapterSource.contains("Ircv3InboundTagOperation.REACTIONS")
            && !adapterSource.contains("Ircv3InboundTagOperation.TYPING")
            && !adapterSource.contains("Ircv3InboundTagOperation.MESSAGE_REDACTION")
            && adapterSource.contains("Ircv3TypingRuntimeSupport")
            && adapterSource.contains("Ircv3ReadMarkerRuntimeSupport")
            && adapterSource.contains(".fromTags(request)"),
        "PircBotX should route tagged-message interpretation through validated runtime SPI boundaries");
    assertTrue(
        !adapterSource.contains("Ircv3ChannelContextPolicy")
            && !adapterSource.contains("Ircv3ReplyTagSignal")
            && !adapterSource.contains("Ircv3ReactionTagSignal")
            && !adapterSource.contains("Ircv3TypingTagSignal")
            && !adapterSource.contains("Ircv3ReadMarkerTagSignal")
            && !adapterSource.contains("Ircv3MessageRedactionTagSignal"),
        "the root adapter should not bypass inbound tag runtime providers");
    assertTrue(
        !adapterSource.contains("private static String observedMessageId"),
        "the root adapter should not reclaim reaction message-id selection");
    assertTrue(
        !adapterSource.contains("private static String unescapeTagValue"),
        "the root adapter should not reclaim message-tag decoding");

    String quasselRuntimeSource = Files.readString(quasselRuntime);
    assertTrue(
        quasselRuntimeSource.contains("Ircv3ChannelContextRuntimeSupport")
            && quasselRuntimeSource.contains("channelContextRuntimeSupport.resolve(request)")
            && !quasselRuntimeSource.contains("Ircv3InboundTagOperation.CHANNEL_CONTEXT"),
        "Quassel channel-context routing should use the shared validated runtime boundary");

    String inputParser =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/"
                    + "PircbotxIrcv3InputParser.java"));
    assertTrue(
        inputParser.contains("Ircv3MessageMutationRuntimeSupport")
            && inputParser.contains("Ircv3ReadMarkerRuntimeSupport")
            && inputParser.contains("Ircv3TypingRuntimeSupport")
            && inputParser.contains(".fromCommand(")
            && inputParser.contains(".redactionFromCommand(")
            && !inputParser.contains("Ircv3InboundCommandOperation.MESSAGE_REDACTION"),
        "PircBotX should route direct MARKREAD and REDACT parsing through validated runtime SPI boundaries");
    assertTrue(
        !inputParser.contains("Ircv3ReadMarkerCommandSignal")
            && !inputParser.contains("Ircv3MessageRedactionCommandSignal"),
        "the root input adapter should not bypass inbound command runtime providers");
  }

  @Test
  void ircv3PresenceMonitorAndReplyPoliciesLiveInFocusedSubprojects() throws IOException {
    Path awayNotifyRoot =
        Path.of("ircafe-feature-ircv3-away-notify/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path accountNotifyRoot =
        Path.of("ircafe-feature-ircv3-account-notify/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path extendedJoinRoot =
        Path.of("ircafe-feature-ircv3-extended-join/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path chghostRoot =
        Path.of("ircafe-feature-ircv3-chghost/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path setnameRoot =
        Path.of("ircafe-feature-ircv3-setname/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path inviteNotifyRoot =
        Path.of("ircafe-feature-ircv3-invite-notify/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path monitorRoot =
        Path.of("ircafe-feature-ircv3-monitor/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path standardRepliesRoot =
        Path.of(
            "ircafe-feature-ircv3-standard-replies/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3");
    Path accountTagRoot =
        Path.of(
            "ircafe-feature-ircv3-account-tag/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path rootParser = Path.of("src/main/java/cafe/woden/ircclient/irc/pircbotx/parse");
    Path presenceAdapter = rootParser.resolve("PircbotxPresenceSignalSupport.java");
    Path serverNumericRouter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/listener/"
                + "PircbotxServerNumericRouter.java");
    Path unknownLineFallback =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/listener/"
                + "PircbotxUnknownLineFallbackHandler.java");
    Path accountTagAdapter = rootParser.resolve("PircbotxAccountTagSupport.java");
    Path standardReplyAdapter = rootParser.resolve("PircbotxStandardReplySupport.java");
    Path inviteEmitter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/emit/"
                + "PircbotxInviteEventEmitter.java");
    Path unknownEventRouter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/listener/"
                + "PircbotxUnknownEventRouter.java");
    Path standardReplyRuntime =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                + "Ircv3StandardReplyRuntimeSupport.java");
    Path monitorEmitter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/emit/"
                + "PircbotxMonitorEventEmitter.java");
    Path quasselAdapter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/quassel/" + "QuasselCoreIrcClientService.java");

    assertTrue(
        Files.isRegularFile(awayNotifyRoot.resolve("Ircv3AwayNotifySignalParser.java"))
            && Files.isRegularFile(awayNotifyRoot.resolve("Ircv3AwayLineParser.java")),
        "away-notify parsing should remain capability-feature-owned");
    assertTrue(
        Files.isRegularFile(accountNotifyRoot.resolve("Ircv3AccountNotifySignalParser.java")),
        "account-notify parsing should remain capability-feature-owned");
    assertTrue(
        Files.isRegularFile(extendedJoinRoot.resolve("Ircv3ExtendedJoinSignalParser.java")),
        "extended-join parsing should remain capability-feature-owned");
    assertTrue(
        Files.isRegularFile(chghostRoot.resolve("Ircv3ChghostParser.java"))
            && Files.isRegularFile(chghostRoot.resolve("Ircv3HostmaskChangeTracker.java")),
        "chghost parsing and hostmask deduplication should remain capability-feature-owned");
    assertTrue(
        Files.isRegularFile(setnameRoot.resolve("Ircv3SetnameParser.java")),
        "setname parsing should remain capability-feature-owned");
    assertTrue(
        Files.isRegularFile(inviteNotifyRoot.resolve("Ircv3InviteNotifyParser.java")),
        "invite-notify parsing should remain capability-feature-owned");
    assertTrue(
        Files.isRegularFile(monitorRoot.resolve("Ircv3MonitorParser.java")),
        "MONITOR numeric and ISUPPORT parsing should remain monitor-feature-owned");
    assertTrue(
        Files.isRegularFile(standardRepliesRoot.resolve("Ircv3StandardReplyParser.java")),
        "FAIL/WARN/NOTE parsing should remain standard-replies-feature-owned");
    assertTrue(
        Files.isRegularFile(accountTagRoot.resolve("Ircv3AccountTagTracker.java")),
        "bounded account-tag state tracking should remain account-tag-feature-owned");
    assertTrue(
        Files.isRegularFile(accountTagRoot.resolve("Ircv3AccountTagSignal.java")),
        "stateless account-tag interpretation should remain account-tag-feature-owned");

    assertTrue(
        !Files.exists(rootParser.resolve("PircbotxMonitorParsers.java")),
        "root should not keep a duplicate MONITOR parser");
    assertTrue(
        !Files.exists(rootParser.resolve("PircbotxAwayParsers.java")),
        "root should not keep duplicate away-line parsing policy");
    assertTrue(
        !Files.exists(rootParser.resolve("ParsedInviteLine.java")),
        "root should not keep a duplicate invite-notify value type");
    assertTrue(
        !Files.readString(rootParser.resolve("PircbotxInboundLineParsers.java"))
            .contains("parseInviteLine"),
        "root should not keep duplicate invite-notify parsing policy");

    String presenceSource = Files.readString(presenceAdapter);
    assertTrue(
        presenceSource.contains("Ircv3InboundCommandSignalRuntimeCatalog")
            && presenceSource.contains("Ircv3InboundCommandOperation.AWAY_NOTIFY")
            && presenceSource.contains("Ircv3InboundCommandOperation.ACCOUNT_NOTIFY")
            && presenceSource.contains("Ircv3InboundCommandOperation.EXTENDED_JOIN")
            && presenceSource.contains("Ircv3InboundCommandOperation.CHGHOST")
            && presenceSource.contains("Ircv3InboundCommandOperation.SETNAME"),
        "PircBotX presence adaptation should route through focused inbound command operations");
    assertTrue(
        !presenceSource.contains("Ircv3AwayNotifySignalParser")
            && !presenceSource.contains("Ircv3AccountNotifySignalParser")
            && !presenceSource.contains("Ircv3ExtendedJoinSignalParser")
            && !presenceSource.contains("Ircv3ChghostParser")
            && !presenceSource.contains("Ircv3SetnameParser"),
        "the root presence adapter should not bypass focused runtime providers");
    assertTrue(
        !presenceSource.contains("private static IrcEvent.AccountState toAccountState"),
        "the root presence adapter should not reclaim account-state policy");

    String serverNumericSource = Files.readString(serverNumericRouter);
    String unknownFallbackSource = Files.readString(unknownLineFallback);
    assertTrue(
        serverNumericSource.contains("presenceSignals.observeSelfAwayConfirmation")
            && unknownFallbackSource.contains("presenceSignals.observeAwayNotifyRawLine")
            && unknownFallbackSource.contains("presenceSignals.observeSelfAwayConfirmationRawLine"),
        "PircBotX numeric and unknown-line away fallbacks should route through runtime SPI");
    assertTrue(
        !serverNumericSource.contains("Ircv3AwayLineParser")
            && !unknownFallbackSource.contains("Ircv3AwayLineParser"),
        "root numeric and unknown-line adapters should not bypass presence runtime providers");

    String accountTagSource = Files.readString(accountTagAdapter);
    String accountTagRuntimeSource =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3AccountTagRuntimeSupport.java"));
    assertTrue(
        accountTagSource.contains("Ircv3AccountTagTracker")
            && accountTagSource.contains("Ircv3AccountTagRuntimeSupport")
            && containsIgnoringWhitespace(accountTagSource, "runtimeSupport.observe(request)")
            && !accountTagSource.contains("Ircv3InboundTagOperation.ACCOUNT_TAG")
            && accountTagRuntimeSource.contains("Ircv3InboundTagOperation.ACCOUNT_TAG")
            && accountTagRuntimeSource.contains("requestedNick.equals(nick)"),
        "PircBotX account-tag adaptation should validate runtime SPI output before bounded state tracking");
    assertTrue(
        !accountTagSource.contains("lastAccountTagByNickLower"),
        "the root account-tag adapter should not retain duplicate state tracking");

    String inviteEmitterSource = Files.readString(inviteEmitter);
    String unknownEventRouterSource = Files.readString(unknownEventRouter);
    assertTrue(
        inviteEmitterSource.contains("Ircv3InboundCommandOperation.INVITE_NOTIFY")
            && unknownEventRouterSource.contains("Ircv3InboundCommandOperation.INVITE_NOTIFY"),
        "typed and unknown PircBotX invite flows should route through invite-notify runtime SPI");
    assertTrue(
        !inviteEmitterSource.contains("Ircv3InviteNotifyParser")
            && !unknownEventRouterSource.contains("Ircv3InviteNotifyParser"),
        "root invite adapters should not statically link invite-notify parsing policy");

    String standardReplySource = Files.readString(standardReplyAdapter);
    String standardReplyRuntimeSource = Files.readString(standardReplyRuntime);
    assertTrue(
        standardReplySource.contains("Ircv3StandardReplyRuntimeSupport")
            && standardReplyRuntimeSource.contains("Ircv3InboundCommandSignalRuntimeCatalog")
            && standardReplyRuntimeSource.contains("Ircv3InboundCommandOperation.STANDARD_REPLY"),
        "PircBotX standard-reply adaptation should route through the shared runtime SPI boundary");
    assertTrue(
        !standardReplySource.contains("Ircv3StandardReplyParser")
            && !standardReplySource.contains("Ircv3Tags.first")
            && !standardReplyRuntimeSource.contains("Ircv3StandardReplyParser"),
        "root standard-reply adapters should not bypass runtime providers or duplicate feature parsing");

    String monitorEmitterSource = Files.readString(monitorEmitter);
    assertTrue(
        monitorEmitterSource.contains("Ircv3InboundCommandSignalRuntimeCatalog")
            && monitorEmitterSource.contains("Ircv3InboundCommandOperation.MONITOR")
            && !monitorEmitterSource.contains("Ircv3MonitorParser"),
        "the PircBotX MONITOR adapter should route interpretation through runtime SPI");
    String quasselSource = Files.readString(quasselAdapter);
    assertTrue(
        quasselSource.contains("ircv3RuntimeSupport")
            && quasselSource.contains(".monitorSupport(raw)")
            && !quasselSource.contains("Ircv3MonitorParser"),
        "the Quassel MONITOR and ISUPPORT adapters should route through runtime SPI");
    assertTrue(
        quasselSource.contains("ircv3RuntimeSupport")
            && quasselSource.contains(".standardReply(")
            && !quasselSource.contains("parseStandardReply(")
            && !quasselSource.contains("record ParsedStandardReply")
            && !quasselSource.contains("Ircv3Tags.firstTagValue"),
        "Quassel standard replies should route through the shared runtime SPI boundary");
  }

  @Test
  void ircv3IsupportNamesAndMonitorCommandPolicyLiveInFocusedSubprojects() throws IOException {
    Path commonRoot =
        Path.of("ircafe-feature-ircv3-common/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path namesRoot =
        Path.of(
            "ircafe-feature-ircv3-user-identity/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path monitorRoot =
        Path.of("ircafe-feature-ircv3-monitor/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path typingRoot =
        Path.of("ircafe-feature-ircv3-typing/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path rootParser = Path.of("src/main/java/cafe/woden/ircclient/irc/pircbotx/parse");
    Path isupportObserver =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/listener/"
                + "PircbotxIsupportObserver.java");
    Path isupportRuntimeSupport =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/" + "Ircv3IsupportRuntimeSupport.java");
    Path whoEmitter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/emit/"
                + "PircbotxWhoEventEmitter.java");
    Path monitorService =
        Path.of(
            "src/main/java/cafe/woden/ircclient/app/outbound/monitor/"
                + "OutboundMonitorCommandService.java");
    Path monitorRuntimeSupport =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                + "Ircv3MonitorCommandRuntimeSupport.java");
    Path typingRuntimeSupport =
        Path.of("src/main/java/cafe/woden/ircclient/irc/ircv3/" + "Ircv3TypingRuntimeSupport.java");

    assertTrue(
        Files.isRegularFile(commonRoot.resolve("Ircv3IsupportLine.java")),
        "shared RPL_ISUPPORT tokenization should remain common-owned");
    assertTrue(
        Files.isRegularFile(namesRoot.resolve("Ircv3WhoUserhostParser.java")),
        "WHO/WHOX/USERHOST parsing should remain user-identity-feature-owned");
    assertTrue(
        Files.isRegularFile(namesRoot.resolve("Ircv3WhoisParser.java")),
        "WHOIS/WHOWAS numeric parsing should remain user-identity-feature-owned");
    assertTrue(
        Files.isRegularFile(namesRoot.resolve("Ircv3WhoisProbeTracker.java")),
        "WHOIS probe lifecycle should remain user-identity-feature-owned");
    assertTrue(
        Files.isRegularFile(namesRoot.resolve("Ircv3WhoxSchemaTracker.java")),
        "WHOX schema observation state should remain user-identity-feature-owned");
    assertTrue(
        Files.isRegularFile(monitorRoot.resolve("Ircv3MonitorCommandPlanner.java")),
        "/monitor operation and chunk planning should remain MONITOR-feature-owned");
    assertTrue(
        Files.isRegularFile(typingRoot.resolve("Ircv3TypingClientTagPolicy.java")),
        "typing CLIENTTAGDENY adaptation should remain typing-feature-owned");
    String typingClientTagPolicy =
        Files.readString(typingRoot.resolve("Ircv3TypingClientTagPolicy.java"));
    assertTrue(
        typingClientTagPolicy.contains("Ircv3ClientTagPolicy")
            && typingClientTagPolicy.contains("isClientOnlyTagAllowed"),
        "the typing feature should adapt shared client-tag policy without duplicating it");
    assertTrue(
        !Files.exists(rootParser.resolve("PircbotxWhoUserhostParsers.java")),
        "root should not keep duplicate WHO/WHOX/USERHOST parsing policy");
    assertTrue(
        !Files.exists(rootParser.resolve("PircbotxWhoisParsers.java")),
        "root should not keep duplicate WHOIS/WHOWAS parsing policy");
    assertTrue(
        !Files.exists(rootParser.resolve("PircbotxClientTagParsers.java")),
        "root should not keep a CLIENTTAGDENY compatibility facade");
    assertTrue(
        Files.isRegularFile(isupportRuntimeSupport),
        "the application should keep one runtime-provider adapter for ISUPPORT observations");
    String runtimeSupportSource = Files.readString(isupportRuntimeSupport);
    assertTrue(
        runtimeSupportSource.contains("ISUPPORT_TOKENS")
            && runtimeSupportSource.contains("ISUPPORT_WHOX")
            && runtimeSupportSource.contains("ISUPPORT_MONITOR")
            && !runtimeSupportSource.contains("ISUPPORT_CLIENT_TAG_POLICY"),
        "the generic ISUPPORT adapter should leave typing-specific policy to its runtime boundary");
    assertTrue(
        !runtimeSupportSource.contains("Ircv3IsupportLine")
            && !runtimeSupportSource.contains("Ircv3WhoUserhostParser")
            && !runtimeSupportSource.contains("Ircv3MonitorParser")
            && !runtimeSupportSource.contains("Ircv3ClientTagPolicy"),
        "the ISUPPORT runtime adapter should validate portable signals without reclaiming policy");

    assertTrue(
        Files.isRegularFile(typingRuntimeSupport),
        "the application should keep one runtime-provider adapter for typing behavior");
    String typingRuntimeSource = Files.readString(typingRuntimeSupport);
    assertTrue(
        typingRuntimeSource.contains("Ircv3OutboundCommandOperation.TYPING")
            && typingRuntimeSource.contains("Ircv3InboundTagOperation.TYPING")
            && typingRuntimeSource.contains("ISUPPORT_CLIENT_TAG_POLICY")
            && typingRuntimeSource.contains("changed the requested target"),
        "the typing runtime adapter should validate outbound, inbound-tag, and ISUPPORT provider output");
    assertTrue(
        !typingRuntimeSource.contains("Ircv3TypingCommandBuilder")
            && !typingRuntimeSource.contains("Ircv3TypingTagSignal")
            && !typingRuntimeSource.contains("Ircv3TypingClientTagPolicy"),
        "the typing runtime adapter should validate portable signals without reclaiming feature policy");

    String isupportSource = Files.readString(isupportObserver);
    assertTrue(
        isupportSource.contains("Ircv3IsupportRuntimeSupport")
            && isupportSource.contains("Ircv3TypingRuntimeSupport")
            && isupportSource.contains("tokenUpdates(rawLine)")
            && isupportSource.contains("whoxSupport(rawLine)")
            && isupportSource.contains("monitorSupport(rawLine)")
            && isupportSource.contains("clientTagPolicy(rawLine)"),
        "the PircBotX ISUPPORT adapter should route focused interpretation through runtime SPI");
    assertTrue(
        !isupportSource.contains("Ircv3IsupportLine")
            && !isupportSource.contains("Ircv3WhoUserhostParser")
            && !isupportSource.contains("Ircv3MonitorParser")
            && !isupportSource.contains("Ircv3ClientTagPolicy"),
        "the PircBotX ISUPPORT adapter should not bypass focused runtime providers");

    String whoSource = Files.readString(whoEmitter);
    assertTrue(
        whoSource.contains("Ircv3InboundCommandSignalRuntimeCatalog")
            && whoSource.contains("Ircv3InboundCommandOperation.USERHOST")
            && whoSource.contains("Ircv3InboundCommandOperation.WHOIS_AWAY")
            && whoSource.contains("Ircv3InboundCommandOperation.WHOIS_ACCOUNT")
            && whoSource.contains("Ircv3InboundCommandOperation.WHOIS_END")
            && whoSource.contains("Ircv3InboundCommandOperation.WHOIS_USER")
            && whoSource.contains("Ircv3InboundCommandOperation.WHO")
            && whoSource.contains("Ircv3InboundCommandOperation.WHOX"),
        "the WHO event adapter should route all focused names numerics through runtime SPI");
    assertTrue(
        whoSource.contains("Ircv3WhoisProbeTracker.Completion"),
        "the WHO event adapter should retain application-owned WHOIS probe completion");
    assertTrue(
        !whoSource.contains("Ircv3WhoUserhostParser")
            && !whoSource.contains("Ircv3WhoisParser")
            && !whoSource.contains("split(\"\\\\s+\")"),
        "the WHO event adapter should not bypass runtime providers or reclaim numeric parsing");

    String connectionState =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/state/"
                    + "PircbotxConnectionState.java"));
    assertTrue(
        connectionState.contains("Ircv3WhoisProbeTracker")
            && connectionState.contains("Ircv3WhoxSchemaTracker")
            && connectionState.contains("Ircv3HostmaskChangeTracker"),
        "connection state should delegate names and hostmask observation lifecycle");
    assertTrue(
        !connectionState.contains("whoisSawAwayByNickLower")
            && !connectionState.contains("whoisSawAccountByNickLower")
            && !connectionState.contains("lastHostmaskByNickLower"),
        "root connection state should not retain duplicate observation maps");

    assertTrue(
        Files.isRegularFile(monitorRuntimeSupport),
        "the application should keep one runtime-provider adapter for MONITOR rendering");
    String monitorRuntimeSource = Files.readString(monitorRuntimeSupport);
    assertTrue(
        monitorRuntimeSource.contains("MONITOR_LIST")
            && monitorRuntimeSource.contains("MONITOR_STATUS")
            && monitorRuntimeSource.contains("MONITOR_CLEAR")
            && monitorRuntimeSource.contains("MONITOR_ADD")
            && monitorRuntimeSource.contains("MONITOR_REMOVE"),
        "the MONITOR runtime adapter should expose all five outbound operations");
    assertTrue(
        !monitorRuntimeSource.contains("Ircv3MonitorCommandPlanner"),
        "the MONITOR runtime adapter should validate provider output without reclaiming planning");

    String monitorSource = Files.readString(monitorService);
    assertTrue(
        monitorSource.contains("Ircv3MonitorCommandPlanner.parse"),
        "the /monitor application flow should delegate command parsing");
    assertTrue(
        monitorSource.contains("Ircv3MonitorCommandRuntimeSupport")
            && monitorSource.contains("listCommand()")
            && monitorSource.contains("statusCommand()")
            && monitorSource.contains("clearCommand()")
            && monitorSource.contains("addCommands(nicks, negotiatedLimit)")
            && monitorSource.contains("removeCommands(nicks, negotiatedLimit)"),
        "the /monitor application flow should render wire commands through runtime SPI");
    assertTrue(
        !monitorSource.contains("Ircv3MonitorCommandPlanner.simpleRawLine")
            && !monitorSource.contains("Ircv3MonitorCommandPlanner.modificationRawLines")
            && !monitorSource.contains("raw.split"),
        "the /monitor application flow should not bypass runtime rendering or reclaim parsing");
  }

  @Test
  void ircv3MessageTagPolicyLivesInFeatureSubproject() throws IOException {
    Path featureRoot =
        Path.of(
            "ircafe-feature-ircv3-message-tags/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path serverTimeRoot =
        Path.of(
            "ircafe-feature-ircv3-server-time/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path umbrellaRoot =
        Path.of("ircafe-feature-ircv3/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path rootIrcv3 = Path.of("src/main/java/cafe/woden/ircclient/irc/ircv3");

    assertTrue(
        Files.isRegularFile(featureRoot.resolve("Ircv3Tags.java")),
        "IRCv3 message-tag parsing should remain feature-owned");
    assertTrue(
        Files.isRegularFile(featureRoot.resolve("Ircv3BatchTag.java")),
        "IRCv3 batch-tag extraction should remain feature-owned");
    assertTrue(
        Files.isRegularFile(serverTimeRoot.resolve("Ircv3ServerTime.java")),
        "IRCv3 server-time extraction should remain server-time-feature-owned");
    assertTrue(
        !Files.exists(featureRoot.resolve("Ircv3ServerTime.java")),
        "message-tags should not retain the server-time capability implementation");
    assertTrue(
        !Files.exists(rootIrcv3.resolve("Ircv3Tags.java")),
        "root should not keep a duplicate IRCv3 tag parser");
    assertTrue(
        !Files.exists(rootIrcv3.resolve("Ircv3BatchTag.java")),
        "root should not keep duplicate batch-tag policy");
    assertTrue(
        !Files.exists(rootIrcv3.resolve("Ircv3ServerTime.java")),
        "root should not keep duplicate server-time policy");
    assertTrue(
        !Files.exists(umbrellaRoot.resolve("Ircv3Tags.java")),
        "the compatibility umbrella should not retain message-tag implementation classes");
    assertTrue(
        !Files.exists(umbrellaRoot.resolve("Ircv3BatchTag.java")),
        "the compatibility umbrella should not retain batch-tag implementation classes");
    assertTrue(
        !Files.exists(umbrellaRoot.resolve("Ircv3ServerTime.java")),
        "the compatibility umbrella should not retain server-time implementation classes");
    assertTrue(
        !Files.exists(umbrellaRoot.resolve("Ircv3ClientTagPolicy.java")),
        "the compatibility umbrella should not retain client-tag implementation classes");

    String tagsSource = Files.readString(featureRoot.resolve("Ircv3Tags.java"));
    assertTrue(
        tagsSource.contains("fromRawLine("),
        "the IRCv3 feature should own raw message-tag parsing");
    assertTrue(
        tagsSource.contains("unescapeTagValue("),
        "the IRCv3 feature should own IRCv3 tag-value unescaping");
    assertTrue(
        tagsSource.contains("firstTagValue("),
        "the IRCv3 feature should own normalized tag lookup");
    assertTrue(
        !tagsSource.contains("org.pircbotx"),
        "feature-owned tag parsing should not depend on PircBotX types");

    String batchSource = Files.readString(featureRoot.resolve("Ircv3BatchTag.java"));
    assertTrue(
        batchSource.contains("Ircv3Tags.fromRawLine"),
        "batch-tag extraction should reuse the feature tag parser");
    assertTrue(
        !batchSource.contains("cafe.woden.ircclient.util"),
        "feature-owned batch-tag policy should not import root capability constants");

    String serverTimeSource = Files.readString(serverTimeRoot.resolve("Ircv3ServerTime.java"));
    assertTrue(
        serverTimeSource.contains("Instant.parse"),
        "the IRCv3 feature should own safe server-time parsing");
    assertTrue(
        serverTimeSource.contains("Ircv3Tags.fromRawLine"),
        "server-time extraction should reuse the feature tag parser");
  }

  @Test
  void ircv3ServerTimeAndEchoMessagePoliciesLiveInFocusedSubprojects() throws IOException {
    Path serverTimeRoot =
        Path.of(
            "ircafe-feature-ircv3-server-time/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path echoMessageRoot =
        Path.of(
            "ircafe-feature-ircv3-echo-message/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path inputParser =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/"
                + "PircbotxIrcv3InputParser.java");
    Path connectionState =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/state/"
                + "PircbotxConnectionState.java");
    Path availabilityAdapter =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/client/"
                + "PircbotxAvailabilitySupport.java");
    Path oldHintStore =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/state/"
                + "PircbotxPrivateTargetHintStore.java");

    Path serverTime = serverTimeRoot.resolve("Ircv3ServerTime.java");
    Path lagSample = serverTimeRoot.resolve("Ircv3ServerTimeLagSample.java");
    Path echoPlanner = echoMessageRoot.resolve("Ircv3EchoMessageTargetHintPlanner.java");
    Path echoStore = echoMessageRoot.resolve("Ircv3EchoMessageTargetHintStore.java");
    Path echoAvailability = echoMessageRoot.resolve("Ircv3EchoMessageAvailability.java");
    for (Path policy : List.of(serverTime, lagSample, echoPlanner, echoStore, echoAvailability)) {
      assertTrue(Files.isRegularFile(policy), policy + " should remain feature-owned");
      String policySource = Files.readString(policy);
      assertTrue(
          !policySource.contains("org.pircbotx")
              && !policySource.contains("PircbotxConnectionState"),
          policy + " should remain transport and root-state independent");
    }
    assertTrue(
        !Files.exists(oldHintStore), "the PircBotX-only echo hint store should stay removed");

    String serverTimeSource = Files.readString(serverTime);
    assertTrue(
        serverTimeSource.contains("fromTagsOrRawLine"),
        "server-time should own tag-first/raw-line fallback parsing");
    String lagSource = Files.readString(lagSample);
    assertTrue(
        lagSource.contains("MAX_PASSIVE_LAG") && lagSource.contains("saturatedDifference"),
        "server-time should own bounded passive lag derivation");

    String echoSource = Files.readString(echoPlanner);
    assertTrue(
        echoSource.contains("Ircv3ChannelContextPolicy.isChannelName"),
        "echo-message target planning should reuse channel-context rules");
    assertTrue(
        echoSource.contains("Ircv3Tags.firstDecodedTagValue"),
        "echo-message correlation should reuse decoded message tags");
    String storeSource = Files.readString(echoStore);
    assertTrue(
        storeSource.contains("ConcurrentHashMap")
            && storeSource.contains("DEFAULT_TTL")
            && storeSource.contains("DEFAULT_MAX_ENTRIES"),
        "echo-message should own bounded and expiring private-target correlation state");

    String inputSource = Files.readString(inputParser);
    assertTrue(
        inputSource.contains("Ircv3EchoMessageRuntimeSupport")
            && inputSource.contains("echoMessageRuntimeSupport")
            && inputSource.contains(".targetHint(")
            && !inputSource.contains("Ircv3EchoMessageTargetHintPlanner.plan"),
        "PircBotX should consume self-echo private-target planning through runtime SPI");
    assertTrue(
        inputSource.contains("serverTimeRuntimeSupport") && inputSource.contains(".passiveLag("),
        "PircBotX should delegate passive server-time lag derivation through the runtime SPI");
    assertTrue(
        !inputSource.contains("serverTaggedAt.toEpochMilli"),
        "the root input adapter should not retain passive-lag arithmetic");

    Path runtimeSupport =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/" + "Ircv3ServerTimeRuntimeSupport.java");
    assertTrue(Files.isRegularFile(runtimeSupport), "root should expose a server-time SPI adapter");
    String runtimeSource = Files.readString(runtimeSupport);
    assertTrue(
        runtimeSource.contains("Ircv3InboundTagSignalRuntimeCatalog")
            && runtimeSource.contains("Ircv3InboundTagOperation.SERVER_TIME")
            && runtimeSource.contains("Ircv3InboundTagOperation.SERVER_TIME_LAG"),
        "the server-time adapter should consume both runtime operations");
    assertTrue(
        !inputSource.contains("Ircv3ServerTime.")
            && !inputSource.contains("Ircv3ServerTimeLagSample"),
        "the root input adapter should not bypass the server-time runtime provider");
    assertTrue(
        !inputSource.contains("PircbotxUtil.parseCtcpAction")
            && !inputSource.contains("parseCtcpAction(")
            && !inputSource.contains("echoMessagePayload("),
        "the root input adapter should not retain echo-message payload policy");

    String stateSource = Files.readString(connectionState);
    assertTrue(
        stateSource.contains("Ircv3EchoMessageTargetHintStore"),
        "connection state should delegate echo correlation lifecycle to the feature store");
    String availabilitySource = Files.readString(availabilityAdapter);
    assertTrue(
        availabilitySource.contains("Ircv3EchoMessageAvailability.isAvailable"),
        "PircBotX availability should delegate echo-message readiness policy");
  }

  @Test
  void ircv3CapabilityNegotiationPolicyLivesInFeatureSubproject() throws IOException {
    Path commonRoot =
        Path.of("ircafe-feature-ircv3-common/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path negotiationRoot =
        Path.of("ircafe-feature-ircv3-negotiation/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path umbrellaRoot =
        Path.of("ircafe-feature-ircv3/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path multilineRoot =
        Path.of("ircafe-feature-ircv3-multiline/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path rootParse = Path.of("src/main/java/cafe/woden/ircclient/irc/pircbotx/parse");
    Path capabilityHandler =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/capability/"
                + "BatchedEnableCapHandler.java");

    assertTrue(
        Files.isRegularFile(commonRoot.resolve("Ircv3CapabilityLine.java")),
        "IRCv3 CAP line normalization should remain feature-owned");
    assertTrue(
        Files.isRegularFile(commonRoot.resolve("Ircv3CapabilityToken.java")),
        "IRCv3 CAP token normalization should remain feature-owned");
    assertTrue(
        Files.isRegularFile(negotiationRoot.resolve("Ircv3CapabilityFallbackPlanner.java")),
        "IRCv3 fallback request planning should remain feature-owned");
    assertTrue(
        Files.isRegularFile(negotiationRoot.resolve("Ircv3CapabilityRequestBatchSession.java")),
        "batched CAP offer matching and pending-resolution state should remain feature-owned");
    assertTrue(
        Files.isRegularFile(negotiationRoot.resolve("Ircv3CapabilityChangePlanner.java")),
        "ACK/DEL/NEW/LS/NAK transition planning should remain feature-owned");
    assertTrue(
        Files.isRegularFile(negotiationRoot.resolve("Ircv3TrackedCapability.java")),
        "tracked capability aliases should remain feature-owned");
    assertTrue(
        Files.isRegularFile(negotiationRoot.resolve("Ircv3CapabilityState.java")),
        "thread-safe negotiated capability state should remain feature-owned");
    assertTrue(
        Files.isRegularFile(negotiationRoot.resolve("Ircv3CapabilitySnapshot.java")),
        "the immutable negotiated capability snapshot should remain feature-owned");
    assertTrue(
        Files.isRegularFile(multilineRoot.resolve("Ircv3MultilineCapabilityStatePlanner.java")),
        "IRCv3 multiline CAP limit transitions should remain feature-owned");
    assertTrue(
        !Files.exists(rootParse.resolve("ParsedCapLine.java")),
        "the PircBotX parse package should not keep duplicate CAP line policy");
    assertTrue(
        !Files.exists(umbrellaRoot.resolve("Ircv3MultilineCapabilityStatePlanner.java")),
        "the compatibility umbrella should not retain multiline CAP transition policy");

    String tokenSource = Files.readString(commonRoot.resolve("Ircv3CapabilityToken.java"));
    assertTrue(
        tokenSource.contains("normalizedName()"),
        "the IRCv3 feature should own canonical capability-name lookup");
    assertTrue(
        !tokenSource.contains("org.pircbotx"),
        "feature-owned capability token policy should remain transport-independent");

    String negotiationSource =
        Files.readString(rootParse.resolve("PircbotxCapabilityNegotiationSupport.java"));
    assertTrue(
        negotiationSource.contains("Ircv3CapabilityNegotiationRuntimeSupport")
            && negotiationSource.contains("runtimeSupport.plan("),
        "the PircBotX negotiation adapter should consume CAP plans through runtime SPI");
    assertTrue(
        !negotiationSource.contains("Ircv3CapabilityFallbackPlanner")
            && !negotiationSource.contains("Ircv3CapabilityChangePlanner")
            && !negotiationSource.contains("Ircv3CapabilityToken.parse"),
        "the PircBotX negotiation adapter should not bypass runtime CAP planning");

    Path negotiationRuntime =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                + "Ircv3CapabilityNegotiationRuntimeSupport.java");
    assertTrue(
        Files.isRegularFile(negotiationRuntime),
        "root should expose a validating CAP negotiation runtime adapter");
    String negotiationRuntimeSource = Files.readString(negotiationRuntime);
    assertTrue(
        negotiationRuntimeSource.contains("Ircv3InboundCommandSignalRuntimeCatalog")
            && negotiationRuntimeSource.contains("CAP_NEGOTIATION")
            && negotiationRuntimeSource.contains("CapabilityChangeObserved")
            && negotiationRuntimeSource.contains("CapabilityFallbackPlanned"),
        "the CAP negotiation adapter should validate portable runtime-provider signals");

    String capabilityHandlerSource = Files.readString(capabilityHandler);
    assertTrue(
        capabilityHandlerSource.contains("Ircv3CapabilityRequestBatchSession"),
        "the PircBotX CAP handler should delegate offer matching and pending state to the feature");
    assertTrue(
        !capabilityHandlerSource.contains("pendingCapsLower")
            && !capabilityHandlerSource.contains("normalizeCap("),
        "the PircBotX CAP handler should not retain duplicate batching policy");

    String connectionStateSource =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/state/"
                    + "PircbotxConnectionState.java"));
    assertTrue(
        connectionStateSource.contains("Ircv3CapabilityState capabilities"),
        "the PircBotX connection state should delegate negotiated capability lifecycle");
    assertTrue(
        connectionStateSource.contains("capabilities.updateTrackedCapability"),
        "connection-state mutation should delegate capability aliases and updates");
    assertTrue(
        !connectionStateSource.contains("record CapabilitySnapshot")
            && !connectionStateSource.contains("final AtomicBoolean batchCapAcked")
            && !connectionStateSource.contains("final AtomicLong multilineMaxBytes")
            && !connectionStateSource.contains("messageTagsFallbackReqSent"),
        "PircBotX connection state should not retain negotiated flags, limits, or fallback gates");

    String multilineSource =
        Files.readString(rootParse.resolve("PircbotxMultilineCapStateSupport.java"));
    assertTrue(
        multilineSource.contains("Ircv3MultilineCapabilityRuntimeSupport")
            && multilineSource.contains("runtimeSupport.apply("),
        "the PircBotX multiline adapter should consume limit transitions through runtime SPI");
    assertTrue(
        !multilineSource.contains("Ircv3MultilineCapabilityStatePlanner")
            && !multilineSource.contains("parseMultilineCapLimits"),
        "the PircBotX multiline adapter should not bypass runtime multiline planning");

    Path multilineRuntime =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                + "Ircv3MultilineCapabilityRuntimeSupport.java");
    String multilineRuntimeSource = Files.readString(multilineRuntime);
    assertTrue(
        multilineRuntimeSource.contains("MULTILINE_CAPABILITY_STATE")
            && multilineRuntimeSource.contains("MultilineLimitsObserved"),
        "the root multiline adapter should validate portable runtime-provider observations");

    assertTrue(
        Files.readString(capabilityHandler).contains("Ircv3CapabilityRequestBatchSession"),
        "batched CAP handling should reuse the feature-owned request session");
  }

  @Test
  void ircv3SaslAndStsCapabilityPolicyLivesInFocusedProjects() throws IOException {
    Path handler =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/capability/"
                + "MultiSaslCapHandler.java");
    String handlerSource = Files.readString(handler);
    assertTrue(
        handlerSource.contains("Ircv3SaslSession")
            && handlerSource.contains("Ircv3SaslSessionUpdate")
            && handlerSource.contains("Ircv3SaslRuntimeSupport"),
        "the PircBotX SASL handler should adapt the feature-owned session through runtime SPI");
    assertTrue(
        handlerSource.contains("runtimeSupport.capabilityList")
            && handlerSource.contains("runtimeSupport.capabilityAck")
            && handlerSource.contains("runtimeSupport.capabilityNak")
            && handlerSource.contains("runtimeSupport.serverMessage")
            && handlerSource.contains("session.onParsedLine"),
        "the PircBotX SASL handler should consume server observations through runtime providers");
    assertTrue(
        !handlerSource.contains("Ircv3SaslCapabilityOffer")
            && !handlerSource.contains("Ircv3SaslAuthenticateFraming")
            && !handlerSource.contains("Ircv3ScramSaslConversation")
            && !handlerSource.contains("enum State"),
        "the transport adapter should not retain SASL negotiation or exchange state");

    Path failureHandler =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/listener/"
                + "PircbotxSaslFailureHandler.java");
    String failureHandlerSource = Files.readString(failureHandler);
    assertTrue(
        failureHandlerSource.contains("Ircv3SaslRuntimeSupport")
            && failureHandlerSource.contains("runtimeSupport.failure")
            && failureHandlerSource.contains("runtimeSupport.isFailureCode"),
        "the PircBotX failure handler should consume feature-owned failure facts through runtime SPI");
    assertTrue(
        !failureHandlerSource.contains("Ircv3SaslFailureSignal.parse")
            && !failureHandlerSource.contains("Ircv3SaslFailureSignal.from")
            && !failureHandlerSource.contains("Ircv3SaslFailureSignal.isFailureNumeric")
            && !failureHandlerSource.contains("ERR_SASL_FAIL")
            && !failureHandlerSource.contains("extractTrailingMessage"),
        "the transport adapter should not retain SASL numeric or message policy");

    assertTrue(
        Files.isRegularFile(
            Path.of(
                "ircafe-feature-ircv3-sasl/src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3SaslSession.java")),
        "the focused SASL feature should own the session lifecycle");
    assertTrue(
        Files.isRegularFile(
            Path.of(
                "ircafe-feature-ircv3-sasl/src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3SaslFailureSignal.java")),
        "the focused SASL feature should own failure numeric interpretation");
    Path saslRuntimeSupport =
        Path.of("src/main/java/cafe/woden/ircclient/irc/ircv3/Ircv3SaslRuntimeSupport.java");
    String saslRuntimeSource = Files.readString(saslRuntimeSupport);
    assertTrue(
        saslRuntimeSource.contains("Ircv3InboundCommandSignalRuntimeCatalog")
            && saslRuntimeSource.contains("SASL_CAPABILITY_LIST")
            && saslRuntimeSource.contains("SASL_SERVER_MESSAGE")
            && saslRuntimeSource.contains("SASL_FAILURE"),
        "the root SASL adapter should validate the installed-provider runtime contract");
    assertTrue(
        !saslRuntimeSource.contains("String username")
            && !saslRuntimeSource.contains("String secret")
            && !saslRuntimeSource.contains("String password")
            && !saslRuntimeSource.contains("Ircv3SaslResponseFactory")
            && !saslRuntimeSource.contains("Ircv3ScramSaslConversation"),
        "runtime providers must not receive credentials or own stateful client responses");
    assertTrue(
        Files.isRegularFile(
            Path.of(
                "ircafe-builtins-ircv3-sasl/src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3SaslRuntimeProvider.java")),
        "the focused SASL built-in should publish executable server interpretation");

    Path oldSaslSource = Path.of("src/main/java/cafe/woden/ircclient/irc/pircbotx/capability");
    for (String oldClass :
        List.of(
            "PircbotxParsedIrcLine.java",
            "PircbotxSaslAuthenticateFraming.java",
            "PircbotxSaslCapabilityOffer.java",
            "PircbotxSaslMechanismSelector.java",
            "PircbotxSaslResponseFactory.java",
            "PircbotxScramSaslConversation.java",
            "PircbotxScramSaslExchange.java")) {
      assertTrue(
          !Files.exists(oldSaslSource.resolve(oldClass)),
          "obsolete root SASL policy should be removed: " + oldClass);
    }

    Path stsService =
        Path.of("src/main/java/cafe/woden/ircclient/irc/ircv3/Ircv3StsPolicyService.java");
    String stsServiceSource = Files.readString(stsService);
    assertTrue(
        stsServiceSource.contains("Ircv3StsRuntimeSupport")
            && stsServiceSource.contains("Ircv3StsPersistedPolicyNormalizer")
            && stsServiceSource.contains("Ircv3StsTransportUpgradePlanner"),
        "the persisted STS adapter should use runtime learning plus feature restore/transport policy");
    assertTrue(
        !stsServiceSource.contains("Ircv3StsPolicyParser.findStsValues")
            && !stsServiceSource.contains("new Ircv3StsPolicyLearningPlanner"),
        "the root STS service should not bypass the runtime provider for capability learning");
    assertTrue(
        !stsServiceSource.contains("toMillisSaturated")
            && !stsServiceSource.contains("addSaturated")
            && !stsServiceSource.contains("record StsPolicy"),
        "the root STS service should not retain expiration arithmetic or the policy value");

    for (String stsPolicyClass :
        List.of(
            "Ircv3StsPolicy.java",
            "Ircv3StsPolicyLearningPlanner.java",
            "Ircv3StsPersistedPolicyNormalizer.java",
            "Ircv3StsTransportUpgradePlanner.java")) {
      assertTrue(
          Files.isRegularFile(
              Path.of(
                  "ircafe-feature-ircv3-sts/src/main/java/cafe/woden/ircclient/irc/ircv3/"
                      + stsPolicyClass)),
          "the focused STS feature should own lifecycle policy: " + stsPolicyClass);
    }

    for (Path sourceRoot :
        List.of(
            Path.of("ircafe-feature-ircv3-sasl/src/main/java"),
            Path.of("ircafe-feature-ircv3-sts/src/main/java"))) {
      try (Stream<Path> files = Files.walk(sourceRoot)) {
        for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
          assertTrue(
              !Files.readString(file).contains("org.pircbotx"),
              file + " should not depend on the PircBotX transport");
        }
      }
    }
  }

  @Test
  void ircv3FeatureFamilySplitUsesFocusedProjects() throws IOException {
    Path settings = Path.of("settings.gradle");
    Path rootBuild = Path.of("build.gradle");
    Path commonBuild = Path.of("ircafe-feature-ircv3-common/build.gradle");
    Path negotiationBuild = Path.of("ircafe-feature-ircv3-negotiation/build.gradle");
    Path messageTagsBuild = Path.of("ircafe-feature-ircv3-message-tags/build.gradle");
    Path serverTimeBuild = Path.of("ircafe-feature-ircv3-server-time/build.gradle");
    Path echoMessageBuild = Path.of("ircafe-feature-ircv3-echo-message/build.gradle");
    Path labeledResponseBuild = Path.of("ircafe-feature-ircv3-labeled-response/build.gradle");
    Path multilineBuild = Path.of("ircafe-feature-ircv3-multiline/build.gradle");
    Path chatHistoryBuild = Path.of("ircafe-feature-ircv3-chat-history/build.gradle");
    Path replyBuild = Path.of("ircafe-feature-ircv3-reply/build.gradle");
    Path reactionsBuild = Path.of("ircafe-feature-ircv3-reactions/build.gradle");
    Path channelContextBuild = Path.of("ircafe-feature-ircv3-channel-context/build.gradle");
    Path typingBuild = Path.of("ircafe-feature-ircv3-typing/build.gradle");
    Path readMarkerBuild = Path.of("ircafe-feature-ircv3-read-marker/build.gradle");
    Path redactionBuild = Path.of("ircafe-feature-ircv3-message-redaction/build.gradle");
    Path messageEditBuild = Path.of("ircafe-feature-ircv3-message-edit/build.gradle");
    Path saslBuild = Path.of("ircafe-feature-ircv3-sasl/build.gradle");
    Path stsBuild = Path.of("ircafe-feature-ircv3-sts/build.gradle");
    Path awayNotifyBuild = Path.of("ircafe-feature-ircv3-away-notify/build.gradle");
    Path accountNotifyBuild = Path.of("ircafe-feature-ircv3-account-notify/build.gradle");
    Path extendedJoinBuild = Path.of("ircafe-feature-ircv3-extended-join/build.gradle");
    Path chghostBuild = Path.of("ircafe-feature-ircv3-chghost/build.gradle");
    Path setnameBuild = Path.of("ircafe-feature-ircv3-setname/build.gradle");
    Path inviteNotifyBuild = Path.of("ircafe-feature-ircv3-invite-notify/build.gradle");
    Path monitorBuild = Path.of("ircafe-feature-ircv3-monitor/build.gradle");
    Path standardRepliesBuild = Path.of("ircafe-feature-ircv3-standard-replies/build.gradle");
    Path accountTagBuild = Path.of("ircafe-feature-ircv3-account-tag/build.gradle");
    Path userIdentityBuild = Path.of("ircafe-feature-ircv3-user-identity/build.gradle");
    Path batchBuild = Path.of("ircafe-feature-ircv3-batch/build.gradle");
    Path zncPlaybackBuild = Path.of("ircafe-feature-ircv3-znc-playback/build.gradle");
    Path oldNamesBuild = Path.of("ircafe-feature-ircv3-names/build.gradle");
    Path oldHistoryTransportBuild = Path.of("ircafe-feature-ircv3-history-transport/build.gradle");
    Path oldDraftBuild = Path.of("ircafe-feature-ircv3-draft/build.gradle");
    Path oldUmbrellaBuild = Path.of("ircafe-feature-ircv3/build.gradle");

    String settingsSource = Files.readString(settings);
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-common'"),
        "the IRCv3 family should declare a deliberately small shared project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-negotiation'"),
        "the IRCv3 family should declare a dedicated negotiation project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-message-tags'"),
        "the IRCv3 family should declare a dedicated message-tags project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-server-time'"),
        "the IRCv3 family should declare a dedicated server-time project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-echo-message'"),
        "the IRCv3 family should declare a dedicated echo-message project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-labeled-response'"),
        "the IRCv3 family should declare a dedicated labeled-response project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-multiline'"),
        "the IRCv3 family should declare a dedicated multiline project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-chat-history'"),
        "the IRCv3 family should declare a dedicated chat-history project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-reply'"),
        "the IRCv3 family should declare a dedicated reply project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-reactions'"),
        "the IRCv3 family should declare a dedicated reactions project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-channel-context'"),
        "the IRCv3 family should declare a dedicated channel-context project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-typing'"),
        "the IRCv3 family should declare a dedicated typing project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-read-marker'"),
        "the IRCv3 family should declare a dedicated read-marker project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-message-redaction'"),
        "the IRCv3 family should declare a dedicated message-redaction project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-message-edit'"),
        "the IRCv3 family should declare a dedicated message-edit project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-sasl'"),
        "the IRCv3 family should declare a dedicated SASL project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-sts'"),
        "the IRCv3 family should declare a dedicated STS project");
    for (String capability :
        List.of(
            "away-notify",
            "account-notify",
            "extended-join",
            "chghost",
            "setname",
            "invite-notify")) {
      assertTrue(
          settingsSource.contains("include 'ircafe-feature-ircv3-" + capability + "'"),
          "the IRCv3 family should declare a dedicated " + capability + " project");
    }
    assertTrue(
        !settingsSource.contains("include 'ircafe-feature-ircv3-presence'"),
        "the aggregate presence project should be retired");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-monitor'"),
        "the IRCv3 family should declare a dedicated MONITOR project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-standard-replies'"),
        "the IRCv3 family should declare a dedicated standard-replies project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-account-tag'"),
        "the IRCv3 family should declare a dedicated account-tag project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-user-identity'"),
        "the IRCv3 family should declare a dedicated user-identity/WHOX project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-batch'"),
        "the IRCv3 family should declare a dedicated BATCH project");
    assertTrue(
        settingsSource.contains("include 'ircafe-feature-ircv3-znc-playback'"),
        "the IRCv3 family should declare a dedicated ZNC playback project");
    assertTrue(
        !settingsSource.contains("include 'ircafe-feature-ircv3-history-transport'"),
        "the aggregate history-transport project should be retired");
    assertTrue(
        !settingsSource.contains("include 'ircafe-feature-ircv3-draft'"),
        "the ambiguous draft project should be removed");
    assertTrue(
        !settingsSource.contains("include 'ircafe-feature-ircv3'"),
        "the source-free compatibility aggregate should be removed");

    assertTrue(Files.isRegularFile(commonBuild), "the IRCv3 common project needs a build file");
    assertTrue(
        Files.isRegularFile(negotiationBuild), "the IRCv3 negotiation project needs a build file");
    assertTrue(
        Files.isRegularFile(messageTagsBuild), "the IRCv3 message-tags project needs a build file");
    assertTrue(
        Files.isRegularFile(serverTimeBuild), "the IRCv3 server-time project needs a build file");
    assertTrue(
        Files.isRegularFile(echoMessageBuild), "the IRCv3 echo-message project needs a build file");
    assertTrue(
        Files.isRegularFile(labeledResponseBuild),
        "the IRCv3 labeled-response project needs a build file");
    assertTrue(
        Files.isRegularFile(multilineBuild), "the IRCv3 multiline project needs a build file");
    assertTrue(
        Files.isRegularFile(chatHistoryBuild), "the IRCv3 chat-history project needs a build file");
    assertTrue(Files.isRegularFile(replyBuild), "the IRCv3 reply project needs a build file");
    assertTrue(
        Files.isRegularFile(reactionsBuild), "the IRCv3 reactions project needs a build file");
    assertTrue(
        Files.isRegularFile(channelContextBuild),
        "the IRCv3 channel-context project needs a build file");
    assertTrue(Files.isRegularFile(typingBuild), "the IRCv3 typing project needs a build file");
    assertTrue(
        Files.isRegularFile(readMarkerBuild), "the IRCv3 read-marker project needs a build file");
    assertTrue(
        Files.isRegularFile(redactionBuild),
        "the IRCv3 message-redaction project needs a build file");
    assertTrue(
        Files.isRegularFile(messageEditBuild), "the IRCv3 message-edit project needs a build file");
    assertTrue(Files.isRegularFile(saslBuild), "the IRCv3 SASL project needs a build file");
    assertTrue(Files.isRegularFile(stsBuild), "the IRCv3 STS project needs a build file");
    for (Path focusedPresenceBuild :
        List.of(
            awayNotifyBuild,
            accountNotifyBuild,
            extendedJoinBuild,
            chghostBuild,
            setnameBuild,
            inviteNotifyBuild)) {
      assertTrue(
          Files.isRegularFile(focusedPresenceBuild), focusedPresenceBuild + " needs a build file");
    }
    assertTrue(
        !Files.exists(Path.of("ircafe-feature-ircv3-presence/build.gradle")),
        "the aggregate presence project should be removed");
    assertTrue(Files.isRegularFile(monitorBuild), "the IRCv3 MONITOR project needs a build file");
    assertTrue(
        Files.isRegularFile(standardRepliesBuild),
        "the IRCv3 standard-replies project needs a build file");
    assertTrue(
        Files.isRegularFile(accountTagBuild), "the IRCv3 account-tag project needs a build file");
    assertTrue(
        Files.isRegularFile(userIdentityBuild),
        "the IRCv3 user-identity project needs a build file");
    assertTrue(Files.isRegularFile(batchBuild), "the IRCv3 BATCH project needs a build file");
    assertTrue(
        Files.isRegularFile(zncPlaybackBuild), "the IRCv3 ZNC playback project needs a build file");
    assertTrue(!Files.exists(oldNamesBuild), "the aggregate names project should be removed");
    assertTrue(
        !Files.exists(oldHistoryTransportBuild),
        "the aggregate history-transport project should be removed");
    assertTrue(!Files.exists(oldDraftBuild), "the ambiguous draft build should be removed");
    assertTrue(
        !Files.exists(oldUmbrellaBuild), "the obsolete compatibility aggregate should be removed");

    String commonSource = Files.readString(commonBuild);
    assertTrue(
        !commonSource.contains("project(':ircafe-feature-ircv3-"),
        "the IRCv3 common project must not depend on capability-family projects");

    String rootSource = Files.readString(rootBuild);
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-message-tags')"),
        "the root application should consume the focused message-tags runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-server-time')"),
        "the root application should consume the focused server-time runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-echo-message')"),
        "the root application should consume the focused echo-message runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-labeled-response')"),
        "the root application should consume the focused labeled-response runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-multiline')"),
        "the root application should consume the focused multiline runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-chat-history')"),
        "the root application should consume the focused chat-history runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-reply')"),
        "the root application should consume the focused reply runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-reactions')"),
        "the root application should consume the focused reactions runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-channel-context')"),
        "the root application should consume the focused channel-context runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-typing')"),
        "the root application should consume the focused typing runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-read-marker')"),
        "the root application should consume the focused read-marker runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-message-redaction')"),
        "the root application should consume the focused message-redaction runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-message-edit')"),
        "the root application should consume the focused message-edit runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-sasl')"),
        "the root application should consume the focused SASL runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-sts')"),
        "the root application should consume the focused STS runtime directly");
    for (String capability :
        List.of(
            "away-notify",
            "account-notify",
            "extended-join",
            "chghost",
            "setname",
            "invite-notify")) {
      assertTrue(
          rootSource.contains("implementation project(':ircafe-feature-ircv3-" + capability + "')"),
          "the root application should consume the focused " + capability + " runtime directly");
    }
    assertTrue(
        !rootSource.contains("implementation project(':ircafe-feature-ircv3-presence')"),
        "the root application should not consume the retired presence aggregate");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-monitor')"),
        "the root application should consume the focused MONITOR runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-standard-replies')"),
        "the root application should consume the focused standard-replies runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-account-tag')"),
        "the root application should consume the focused account-tag runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-user-identity')"),
        "the root application should consume the focused user-identity runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-batch')"),
        "the root application should consume the focused BATCH runtime directly");
    assertTrue(
        rootSource.contains("implementation project(':ircafe-feature-ircv3-znc-playback')"),
        "the root application should consume the focused ZNC playback runtime directly");
    assertTrue(
        !rootSource.contains("implementation project(':ircafe-feature-ircv3-history-transport')"),
        "the root application should not consume the retired history-transport aggregate");
    assertTrue(
        !rootSource.contains("implementation project(':ircafe-feature-ircv3-draft')"),
        "the root application should not consume the ambiguous draft runtime");
    assertTrue(
        !rootSource.contains("implementation project(':ircafe-feature-ircv3')"),
        "the root application should not consume the obsolete compatibility aggregate");

    String negotiationSource = Files.readString(negotiationBuild);
    assertTrue(
        negotiationSource.contains("api project(':ircafe-feature-ircv3-common')"),
        "the negotiation project should consume shared capability values through common");
    assertTrue(
        negotiationSource.contains("api project(':ircafe-plugin-api')"),
        "provider metadata policy should continue to compile against the public plugin API");

    assertTransportIndependentFocusedBuild(messageTagsBuild, false);
    assertMessageTagSignalBuild(serverTimeBuild);
    assertMessageTagSignalBuild(echoMessageBuild);
    assertMessageTagSignalBuild(labeledResponseBuild);
    assertTrue(
        Files.readString(labeledResponseBuild)
            .contains("api project(':ircafe-feature-ircv3-common')"),
        "labeled-response should reuse shared outbound tag escaping");
    assertTrue(
        Files.readString(echoMessageBuild)
            .contains("implementation project(':ircafe-feature-ircv3-channel-context')"),
        "echo-message should reuse channel-context target classification");
    assertTransportIndependentFocusedBuild(chatHistoryBuild, false);
    assertTransportIndependentFocusedBuild(replyBuild, true);
    assertTransportIndependentFocusedBuild(reactionsBuild, true);
    assertMessageTagSignalBuild(replyBuild);
    assertMessageTagSignalBuild(reactionsBuild);
    assertMessageTagSignalBuild(channelContextBuild);
    assertTransportIndependentFocusedBuild(typingBuild, true);
    assertMessageTagSignalBuild(typingBuild);
    assertTransportIndependentFocusedBuild(readMarkerBuild, true);
    assertMessageTagSignalBuild(readMarkerBuild);
    assertTransportIndependentFocusedBuild(redactionBuild, true);
    assertMessageTagSignalBuild(redactionBuild);
    assertTransportIndependentFocusedBuild(messageEditBuild, true);
    assertMessageTagSignalBuild(messageEditBuild);
    assertTransportIndependentFocusedBuild(saslBuild, false);
    assertTransportIndependentFocusedBuild(stsBuild, false);
    for (Path focusedPresenceBuild :
        List.of(
            awayNotifyBuild,
            accountNotifyBuild,
            extendedJoinBuild,
            chghostBuild,
            setnameBuild,
            inviteNotifyBuild)) {
      assertTransportIndependentFocusedBuild(focusedPresenceBuild, false);
    }
    assertTransportIndependentFocusedBuild(monitorBuild, false);
    assertTransportIndependentFocusedBuild(standardRepliesBuild, false);
    assertTransportIndependentFocusedBuild(accountTagBuild, false);
    assertTransportIndependentFocusedBuild(userIdentityBuild, false);
    assertTransportIndependentFocusedBuild(batchBuild, false);
    assertTransportIndependentFocusedBuild(zncPlaybackBuild, false);

    String multilineSource = Files.readString(multilineBuild);
    assertTrue(
        multilineSource.contains("api project(':ircafe-feature-ircv3-common')"),
        "the multiline feature should consume shared CAP values through common");
    assertTrue(
        !multilineSource.contains("project(':ircafe-feature-ircv3')"),
        "the multiline feature must not depend on the removed compatibility umbrella");
    assertTrue(
        !multilineSource.contains("org.pircbotx"),
        "the multiline feature build should remain transport-independent");
  }

  @Test
  void ircv3LabeledResponsePolicyStaysFeatureOwned() throws IOException {
    Path featureRoot =
        Path.of(
            "ircafe-feature-ircv3-labeled-response/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3");
    for (String className :
        List.of(
            "Ircv3LabeledResponseValues.java",
            "Ircv3LabeledResponseRawLinePreparer.java",
            "Ircv3LabeledResponseTagSignal.java")) {
      Path policy = featureRoot.resolve(className);
      assertTrue(Files.isRegularFile(policy), policy + " should remain feature-owned");
      String source = Files.readString(policy);
      assertTrue(!source.contains("org.pircbotx"), policy + " should remain transport-independent");
      assertTrue(
          !source.contains("cafe.woden.ircclient.state"),
          policy + " should not depend on root state contracts");
    }

    String commandAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/app/outbound/support/"
                    + "OutboundRawLineCorrelationService.java"));
    assertTrue(
        commandAdapter.contains("Ircv3OutboundCommandOperation.LABELED_RESPONSE")
            && commandAdapter.contains("Ircv3OutboundCommandRequest.labeledResponse")
            && commandAdapter.contains("Ircv3LabeledResponseRuntimeSupport"),
        "the outbound application adapter should consume runtime-selected label rendering");
    assertTrue(
        !commandAdapter.contains("Ircv3LabeledResponseRawLinePreparer")
            && !commandAdapter.contains("Ircv3LabeledResponseValues"),
        "the outbound application adapter should not statically link labeled-response policy");

    String stateAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/state/" + "LabeledResponseRoutingState.java"));
    assertTrue(
        !stateAdapter.contains("Ircv3LabeledResponse"),
        "the state module must not depend back on the IRC transport module");
    String statePort =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/state/api/"
                    + "LabeledResponseRoutingPort.java"));
    assertTrue(
        !statePort.contains("prepareOutgoingRaw") && !statePort.contains("nextClientLabel"),
        "protocol rendering should not remain on the state correlation port");

    String statusAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/app/core/"
                    + "MediatorServerStatusEventHandler.java"));
    assertTrue(
        statusAdapter.contains("Ircv3LabeledResponseRuntimeSupport")
            && statusAdapter.contains("fromTags(event.kind().name(), event.ircv3Tags())"),
        "server response routing should consume runtime-selected label and completion signals");
    assertTrue(
        !statusAdapter.contains("Ircv3LabeledResponseTagSignal"),
        "server response routing should not statically link labeled-response tag policy");
    assertTrue(
        !statusAdapter.contains("ircv3Tags().get(\"label\")"),
        "server response routing should not parse label tags directly");
  }

  @Test
  void ircv3OutboundConversationCommandsStayFeatureOwned() throws IOException {
    Path commonRoot =
        Path.of("ircafe-feature-ircv3-common/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path replyRoot =
        Path.of("ircafe-feature-ircv3-reply/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path reactionsRoot =
        Path.of("ircafe-feature-ircv3-reactions/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path editRoot =
        Path.of("ircafe-feature-ircv3-message-edit/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path redactionRoot =
        Path.of(
            "ircafe-feature-ircv3-message-redaction/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3");
    Path typingRoot =
        Path.of("ircafe-feature-ircv3-typing/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path readMarkerRoot =
        Path.of("ircafe-feature-ircv3-read-marker/src/main/java/cafe/woden/ircclient/irc/ircv3");

    for (Path policy :
        List.of(
            commonRoot.resolve("Ircv3CommandValuePolicy.java"),
            replyRoot.resolve("Ircv3ReplyCommandBuilder.java"),
            reactionsRoot.resolve("Ircv3ReactionCommandBuilder.java"),
            editRoot.resolve("Ircv3MessageEditCommandBuilder.java"),
            editRoot.resolve("Ircv3MessageEditTagSignal.java"),
            redactionRoot.resolve("Ircv3MessageRedactionCommandBuilder.java"),
            typingRoot.resolve("Ircv3TypingCommandBuilder.java"),
            readMarkerRoot.resolve("Ircv3ReadMarkerCommandBuilder.java"))) {
      assertTrue(Files.isRegularFile(policy), policy + " should remain feature-owned");
      String source = Files.readString(policy);
      assertTrue(!source.contains("org.pircbotx"), policy + " should remain transport-independent");
      assertTrue(
          !source.contains("cafe.woden.ircclient.app"),
          policy + " should not depend on application-layer mutation models");
    }

    String pircbotxCommands =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/client/"
                    + "PircbotxCapabilityCommandSupport.java"));
    String quassel =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/quassel/"
                    + "QuasselCoreIrcClientService.java"));
    String matrix =
        Files.readString(
            Path.of("src/main/java/cafe/woden/ircclient/irc/matrix/MatrixIrcClientService.java"));
    String matrixRuntime =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/matrix/MatrixIrcv3RuntimeSupport.java"));
    String chatView =
        Files.readString(
            Path.of("src/main/java/cafe/woden/ircclient/ui/chat/view/ChatViewPanel.java"));
    String inbound =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/app/core/"
                    + "MediatorInboundTextEventHandler.java"));

    assertTrue(
        pircbotxCommands.contains("Ircv3OutboundCommandRuntimeCatalog")
            && pircbotxCommands.contains("Ircv3TypingRuntimeSupport")
            && pircbotxCommands.contains("Ircv3ReadMarkerRuntimeSupport")
            && pircbotxCommands.contains("Ircv3OutboundCommandOperation.READ_MARKER")
            && pircbotxCommands.contains("Ircv3OutboundCommandOperation.CHAT_HISTORY_BEFORE")
            && !pircbotxCommands.contains("Ircv3TypingCommandBuilder")
            && !pircbotxCommands.contains("Ircv3ReadMarkerCommandBuilder")
            && !pircbotxCommands.contains("Ircv3ChatHistoryCommandBuilder"),
        "PircBotX outbound capability commands should route through runtime SPI providers");
    assertTrue(
        quassel.contains("QuasselIrcv3RuntimeSupport")
            && containsIgnoringWhitespace(quassel, "ircv3RuntimeSupport.typingRawLines")
            && containsIgnoringWhitespace(quassel, "ircv3RuntimeSupport.readMarkerRawLines")
            && containsIgnoringWhitespace(quassel, ".readMarkerFromCommand(")
            && containsIgnoringWhitespace(quassel, ".redactionFromCommand(")
            && containsIgnoringWhitespace(quassel, "ircv3RuntimeSupport.chatHistoryBefore")
            && containsIgnoringWhitespace(quassel, "ircv3RuntimeSupport.chatHistoryLatest")
            && containsIgnoringWhitespace(quassel, "ircv3RuntimeSupport.chatHistoryBetween")
            && containsIgnoringWhitespace(quassel, "ircv3RuntimeSupport.chatHistoryAround")
            && !quassel.contains("Ircv3TypingCommandBuilder")
            && !quassel.contains("Ircv3ReadMarkerCommandBuilder.buildTimestampRawLine")
            && !quassel.contains("Ircv3ChatHistoryCommandBuilder"),
        "Quassel outbound capability commands should route through runtime SPI providers");
    assertTrue(
        containsIgnoringWhitespace(quassel, "ircv3RuntimeSupport.conversationSignals")
            && containsIgnoringWhitespace(quassel, "ircv3RuntimeSupport.channelContext")
            && containsIgnoringWhitespace(quassel, "ircv3RuntimeSupport.monitorSignals")
            && !quassel.contains("Ircv3MonitorParser.parseRpl730MonitorOnlineEntries")
            && !quassel.contains("Ircv3MonitorParser.parseRpl731MonitorOfflineEntries")
            && !quassel.contains("Ircv3MonitorParser.parseRpl732MonitorListNicks")
            && !quassel.contains("Ircv3MonitorParser.isRpl733MonitorListEnd")
            && !quassel.contains("Ircv3MonitorParser.parseErr734MonitorListFull"),
        "Quassel inbound conversation and MONITOR signals should route through runtime SPI providers");
    assertTrue(
        matrix.contains("Ircv3TypingCommandBuilder.normalizeState"),
        "Matrix direct typing state normalization should reuse the typing feature policy");
    assertTrue(
        matrix.contains("MatrixIrcv3RuntimeSupport")
            && containsIgnoringWhitespace(matrix, "ircv3RuntimeSupport.messageTags")
            && containsIgnoringWhitespace(matrix, "ircv3RuntimeSupport.messageEditTarget")
            && containsIgnoringWhitespace(matrix, "ircv3RuntimeSupport.replyTarget")
            && containsIgnoringWhitespace(matrix, "ircv3RuntimeSupport.reaction")
            && containsIgnoringWhitespace(matrix, "ircv3RuntimeSupport.typingState")
            && !matrix.contains("Ircv3Tags.firstTagValue")
            && !matrix.contains("private static Map<String, String> parseRawTags"),
        "Matrix raw IRC compatibility commands should route tagged-message policy through runtime SPI");
    assertTrue(
        matrixRuntime.contains("Ircv3MessageTagsRuntimeCatalog")
            && matrixRuntime.contains("Ircv3MessageMutationRuntimeSupport")
            && matrixRuntime.contains("Ircv3TypingRuntimeSupport")
            && containsIgnoringWhitespace(matrixRuntime, ".replyFromTags(")
            && containsIgnoringWhitespace(matrixRuntime, ".reactionSelectionFromTags(")
            && containsIgnoringWhitespace(matrixRuntime, ".messageEditFromTags(")
            && !matrixRuntime.contains("Ircv3InboundTagOperation.REPLY")
            && !matrixRuntime.contains("Ircv3InboundTagOperation.REACTIONS")
            && !matrixRuntime.contains("Ircv3InboundTagOperation.MESSAGE_EDIT")
            && !matrixRuntime.contains("Ircv3InboundTagOperation.TYPING"),
        "the Matrix adapter should validate installed-provider tag signals behind focused runtime boundaries");
    assertTrue(
        chatView.contains("Ircv3ReplyCommandBuilder.buildPrefillDraft")
            && chatView.contains("Ircv3ReactionCommandBuilder.buildReactPrefillDraft"),
        "transcript prefills should delegate to reply and reaction feature builders");
    assertTrue(
        inbound.contains("Ircv3MessageMutationRuntimeSupport")
            && inbound.contains(".messageEditFromTags(")
            && inbound.contains(".hasNonReplyMutationTag(")
            && !inbound.contains("Ircv3InboundTagOperation.MESSAGE_EDIT")
            && !inbound.contains("Ircv3MessageEditTagSignal.fromTags"),
        "inbound message-mutation tag parsing should route through the validated runtime SPI boundary");

    String messageTagRuntimeCatalog =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3MessageTagsRuntimeCatalog.java"));
    String runtimeCatalog =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3MessageMutationRuntimeCatalog.java"));
    String mutationRuntimeSupport =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3MessageMutationRuntimeSupport.java"));
    String outboundRuntimeCatalog =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3OutboundCommandRuntimeCatalog.java"));
    String inboundRuntimeCatalog =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3InboundTagSignalRuntimeCatalog.java"));
    String inboundCommandRuntimeCatalog =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                    + "Ircv3InboundCommandSignalRuntimeCatalog.java"));
    String tagSignalAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/"
                    + "PircbotxTagSignalSupport.java"));
    String multilineAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/client/"
                    + "PircbotxMultilineMessageSupport.java"));
    String backendCatalog =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/app/outbound/backend/"
                    + "BackendExtensionCatalogState.java"));
    String mutationOutboundAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/app/outbound/backend/"
                    + "Ircv3MessageMutationOutboundCommands.java"));
    assertTrue(
        messageTagRuntimeCatalog.contains("Ircv3MessageTagParserProvider.class")
            && messageTagRuntimeCatalog.contains(
                "Ircv3RuntimeProviderSupport.loadInstalledProviders")
            && messageTagRuntimeCatalog.contains(
                "Ircv3MessageTagParserProvider::messageTagParserPriority"),
        "message-tag parsing should load a replaceable runtime SPI provider");
    assertTrue(
        runtimeCatalog.contains("@Component")
            && runtimeCatalog.contains("Ircv3MessageMutationProvider.class")
            && runtimeCatalog.contains("Ircv3RuntimeProviderSupport.indexByOperation")
            && runtimeCatalog.contains("Ircv3RuntimeProviderSupport.loadInstalledProviders")
            && runtimeCatalog.contains("Ircv3MessageMutationProvider::priority"),
        "outbound mutation rendering should load replaceable installed runtime SPI providers");
    assertTrue(
        mutationRuntimeSupport.contains("renderReply")
            && mutationRuntimeSupport.contains("renderReaction")
            && mutationRuntimeSupport.contains("renderEdit")
            && mutationRuntimeSupport.contains("renderRedaction")
            && mutationRuntimeSupport.contains("conversationSignals")
            && mutationRuntimeSupport.contains("redactionFromCommand")
            && mutationRuntimeSupport.contains("changed the requested target"),
        "message mutations should be validated behind one provider-aware runtime boundary");
    assertTrue(
        outboundRuntimeCatalog.contains("Ircv3OutboundCommandProvider.class")
            && outboundRuntimeCatalog.contains("Ircv3RuntimeProviderSupport.indexByOperation")
            && outboundRuntimeCatalog.contains("Ircv3OutboundCommandProvider::priority"),
        "typing, read-marker, history, and multiline rendering should load runtime SPI providers");
    assertTrue(
        inboundRuntimeCatalog.contains("Ircv3InboundTagSignalProvider.class")
            && inboundRuntimeCatalog.contains("Ircv3RuntimeProviderSupport.indexByOperation")
            && inboundRuntimeCatalog.contains("Ircv3InboundTagSignalProvider::inboundTagPriority"),
        "inbound tag interpretation should load replaceable runtime SPI providers");
    assertTrue(
        inboundCommandRuntimeCatalog.contains("Ircv3InboundCommandSignalProvider.class")
            && inboundCommandRuntimeCatalog.contains("Ircv3RuntimeProviderSupport.indexByOperation")
            && inboundCommandRuntimeCatalog.contains(
                "Ircv3InboundCommandSignalProvider::inboundCommandPriority"),
        "parsed inbound command interpretation should load replaceable runtime SPI providers");
    assertTrue(
        tagSignalAdapter.contains("Ircv3ChannelContextRuntimeSupport")
            && containsIgnoringWhitespace(
                tagSignalAdapter, "channelContextRuntimeSupport.resolve(request)")
            && tagSignalAdapter.contains("Ircv3MessageMutationRuntimeSupport")
            && tagSignalAdapter.contains("Ircv3TypingRuntimeSupport")
            && containsIgnoringWhitespace(tagSignalAdapter, ".conversationSignals(")
            && !tagSignalAdapter.contains("Ircv3InboundTagOperation.CHANNEL_CONTEXT")
            && !tagSignalAdapter.contains("Ircv3InboundTagOperation.REPLY")
            && !tagSignalAdapter.contains("Ircv3InboundTagOperation.REACTIONS")
            && !tagSignalAdapter.contains("Ircv3InboundTagOperation.TYPING")
            && !tagSignalAdapter.contains("Ircv3ReplyTagSignal")
            && !tagSignalAdapter.contains("Ircv3ReactionTagSignal")
            && !tagSignalAdapter.contains("Ircv3TypingTagSignal"),
        "PircBotX tagged-message interpretation should route through validated runtime SPI boundaries");
    assertTrue(
        multilineAdapter.contains("Ircv3OutboundCommandOperation.MULTILINE")
            && !multilineAdapter.contains("Ircv3MultilineCommandPlanner"),
        "the PircBotX multiline adapter should route rendering through the runtime SPI catalog");
    assertTrue(
        backendCatalog.contains("Ircv3MessageMutationRuntimeCatalog")
            && mutationOutboundAdapter.contains("Ircv3MessageMutationRuntimeSupport")
            && containsIgnoringWhitespace(mutationOutboundAdapter, ".renderReply(")
            && containsIgnoringWhitespace(mutationOutboundAdapter, ".renderReaction(")
            && containsIgnoringWhitespace(mutationOutboundAdapter, ".renderEdit(")
            && containsIgnoringWhitespace(mutationOutboundAdapter, ".renderRedaction(")
            && !backendCatalog.contains("Ircv3ReplyCommandBuilder")
            && !backendCatalog.contains("Ircv3ReactionCommandBuilder")
            && !backendCatalog.contains("Ircv3MessageEditCommandBuilder")
            && !backendCatalog.contains("Ircv3MessageRedactionCommandBuilder"),
        "the backend catalog should route mutation rendering through the validated runtime SPI boundary");
  }

  @Test
  void ircv3MessageTagParsingUsesRuntimeProviderBoundary() throws IOException {
    Path feature =
        Path.of(
            "ircafe-feature-ircv3-message-tags/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3/Ircv3Tags.java");
    Path provider =
        Path.of(
            "ircafe-builtins-ircv3-message-tags/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3/Ircv3MessageTagsExtensionProvider.java");
    Path runtimeCatalog =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                + "Ircv3MessageTagsRuntimeCatalog.java");
    Path runtimeProviderSupport =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/" + "Ircv3RuntimeProviderSupport.java");
    Path runtimeSupport =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/"
                + "Ircv3MessageTagsRuntimeSupport.java");
    Path messageIdSupport =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/ircv3/" + "Ircv3MessageIdRuntimeSupport.java");
    Path eventMetadata =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/support/"
                + "PircbotxEventMetadata.java");
    Path quassel =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/quassel/" + "QuasselCoreIrcClientService.java");
    Path inboundTextHandler =
        Path.of(
            "src/main/java/cafe/woden/ircclient/app/core/"
                + "MediatorInboundTextEventHandler.java");

    String featureSource = Files.readString(feature);
    assertTrue(
        featureSource.contains("fromMap")
            && featureSource.contains("fromRawLine")
            && !featureSource.contains("org.pircbotx"),
        "message-tags should own transport-neutral tag normalization and escape decoding");

    String providerSource = Files.readString(provider);
    assertTrue(
        providerSource.contains("Ircv3MessageTagParserProvider")
            && !providerSource.contains("Ircv3InboundTagSignalProvider")
            && !providerSource.contains("Ircv3InboundTagOperation.MESSAGE_ID")
            && providerSource.contains("Ircv3Tags.fromMap")
            && providerSource.contains("Ircv3Tags.fromRawLine"),
        "the focused message-tags built-in should publish only transport tag parsing");

    Path messageIdFeature =
        Path.of(
            "ircafe-feature-ircv3-message-id/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3/Ircv3MessageIdTagPolicy.java");
    Path messageIdProvider =
        Path.of(
            "ircafe-builtins-ircv3-message-id/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3/Ircv3MessageIdRuntimeProvider.java");
    String messageIdFeatureSource = Files.readString(messageIdFeature);
    String messageIdProviderSource = Files.readString(messageIdProvider);
    assertTrue(
        messageIdFeatureSource.contains("Ircv3Tags.firstTagValue")
            && messageIdFeatureSource.contains("draft/msgid")
            && messageIdFeatureSource.contains("znc.in/msgid"),
        "message-ID alias selection should live in its focused feature module");
    assertTrue(
        messageIdProviderSource.contains("Ircv3InboundTagSignalProvider")
            && messageIdProviderSource.contains("Ircv3InboundTagOperation.MESSAGE_ID")
            && messageIdProviderSource.contains("Ircv3MessageIdTagPolicy")
            && !messageIdProviderSource.contains("Ircv3ExtensionProvider"),
        "the focused message-ID built-in should publish only runtime tag interpretation");

    String catalogSource = Files.readString(runtimeCatalog);
    String runtimeProviderSupportSource = Files.readString(runtimeProviderSupport);
    assertTrue(
        catalogSource.contains("Ircv3MessageTagParserProvider.class")
            && JavaSourceText.containsIgnoringWhitespace(
                catalogSource, "Ircv3RuntimeProviderSupport.loadInstalledProviders(")
            && JavaSourceText.containsIgnoringWhitespace(
                catalogSource, "Ircv3RuntimeProviderSupport.selectHighestPriority(")
            && catalogSource.contains("Ircv3MessageTagParserProvider::messageTagParserPriority")
            && JavaSourceText.containsIgnoringWhitespace(
                runtimeProviderSupportSource, "installedPlugins.loadInstalledServices(")
            && JavaSourceText.containsIgnoringWhitespace(
                runtimeProviderSupportSource,
                "priority.applyAsInt(candidate) > priority.applyAsInt(selected)"),
        "the application should select installed message-tag parsers by priority");

    String supportSource = Files.readString(runtimeSupport);
    assertTrue(
        supportSource.contains("runtimeCatalog.parse")
            && supportSource.contains("fromEvent")
            && !supportSource.contains("Ircv3Tags.from"),
        "transport event adaptation should invoke the runtime parser without bypassing it");

    String messageIdSupportSource = Files.readString(messageIdSupport);
    assertTrue(
        messageIdSupportSource.contains("Ircv3InboundTagOperation.MESSAGE_ID")
            && messageIdSupportSource.contains("Ircv3InboundTagSignalType.MESSAGE_ID")
            && messageIdSupportSource.contains("MAX_MESSAGE_ID_LENGTH")
            && !messageIdSupportSource.contains("Ircv3Tags.firstTagValue"),
        "message-ID validation should consume provider signals without static alias parsing");

    String eventMetadataSource = Files.readString(eventMetadata);
    assertTrue(
        eventMetadataSource.contains("Ircv3MessageTagsRuntimeSupport")
            && eventMetadataSource.contains("Ircv3MessageIdRuntimeSupport")
            && eventMetadataSource.contains("messageTagsRuntimeSupport")
            && !eventMetadataSource.contains("Ircv3Tags.fromEvent")
            && !eventMetadataSource.contains("Ircv3Tags.firstTagValue"),
        "PircBotX event metadata should consume tags and message IDs through runtime SPI");

    String inboundTextHandlerSource = Files.readString(inboundTextHandler);
    assertTrue(
        inboundTextHandlerSource.contains("Ircv3MessageIdRuntimeSupport")
            && inboundTextHandlerSource.contains("messageIdRuntimeSupport.resolve(tags, messageId)")
            && !inboundTextHandlerSource.contains("firstIrcv3TagValue"),
        "application duplicate suppression should use provider-selected message IDs");

    String quasselSource = Files.readString(quassel);
    assertTrue(
        quasselSource.contains("ircv3RuntimeSupport.messageTags")
            && !quasselSource.contains("Ircv3Tags.fromRawLine"),
        "Quassel raw-line tag parsing should consume the runtime SPI adapter");
  }

  @Test
  void ircv3EchoMessageTargetPlanningUsesRuntimeProviderBoundary() throws IOException {
    Path featureRoot =
        Path.of("ircafe-feature-ircv3-echo-message/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path planner = featureRoot.resolve("Ircv3EchoMessageTargetHintPlanner.java");
    Path provider =
        Path.of(
            "ircafe-builtins-ircv3-echo-message/src/main/java/"
                + "cafe/woden/ircclient/irc/ircv3/Ircv3EchoMessageExtensionProvider.java");
    Path parser =
        Path.of(
            "src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/"
                + "PircbotxIrcv3InputParser.java");

    assertTrue(
        Files.isRegularFile(planner), "echo-message target planning should stay feature-owned");
    String plannerSource = Files.readString(planner);
    assertTrue(
        !plannerSource.contains("org.pircbotx") && !plannerSource.contains("PircBotX"),
        "echo-message target planning should remain transport-independent");

    String providerSource = Files.readString(provider);
    assertTrue(
        providerSource.contains("Ircv3InboundTagSignalProvider")
            && providerSource.contains("Ircv3EchoMessageTargetHintPlanner")
            && providerSource.contains("ECHO_MESSAGE_TARGET_HINT"),
        "the focused echo-message built-in should publish executable target-hint interpretation");

    String parserSource = Files.readString(parser);
    assertTrue(
        parserSource.contains("Ircv3EchoMessageRuntimeSupport")
            && parserSource.contains("echoMessageRuntimeSupport")
            && parserSource.contains(".targetHint(")
            && !parserSource.contains("Ircv3EchoMessageTargetHintPlanner.plan"),
        "the PircBotX parser should consume echo-message target hints through runtime SPI");
  }

  @Test
  void ircv3PresenceIdentityBatchAndZncPlaybackPolicyStayFeatureOwned() throws IOException {
    Path chghostRoot =
        Path.of("ircafe-feature-ircv3-chghost/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path setnameRoot =
        Path.of("ircafe-feature-ircv3-setname/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path batchRoot =
        Path.of("ircafe-feature-ircv3-batch/src/main/java/cafe/woden/ircclient/irc/ircv3");
    Path zncPlaybackRoot =
        Path.of(
            "ircafe-feature-ircv3-znc-playback/src/main/java/" + "cafe/woden/ircclient/irc/ircv3");
    Path chghostParser = chghostRoot.resolve("Ircv3ChghostParser.java");
    Path setnameParser = setnameRoot.resolve("Ircv3SetnameParser.java");
    Path zncDetector = zncPlaybackRoot.resolve("Ircv3ZncDetector.java");
    Path playbackPlanner = zncPlaybackRoot.resolve("Ircv3ZncPlaybackRequestPlanner.java");
    Path batchParser = batchRoot.resolve("Ircv3HistoryBatchControlParser.java");
    Path bootstrapPolicy = zncPlaybackRoot.resolve("Ircv3HistoryBootstrapSuppressionPolicy.java");
    Path oldZncParser =
        Path.of("src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/PircbotxZncParsers.java");

    for (Path policy :
        List.of(
            chghostParser,
            setnameParser,
            zncDetector,
            playbackPlanner,
            batchParser,
            bootstrapPolicy)) {
      assertTrue(Files.isRegularFile(policy), policy + " should remain feature-owned");
      String policySource = Files.readString(policy);
      assertTrue(
          !policySource.contains("org.pircbotx"), policy + " should remain transport-independent");
      assertTrue(
          !policySource.contains("cafe.woden.ircclient.util"),
          policy + " should not depend on root capability constants or sanitizers");
    }
    assertTrue(!Files.exists(oldZncParser), "the obsolete PircBotX ZNC parser should stay removed");

    String inputParser =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/"
                    + "PircbotxIrcv3InputParser.java"));
    assertTrue(
        inputParser.contains("presenceSignalSupport.observeIdentityChange"),
        "the PircBotX input adapter should route SETNAME/CHGHOST through runtime SPI");
    assertTrue(
        !inputParser.contains("Ircv3ChghostParser") && !inputParser.contains("Ircv3SetnameParser"),
        "the PircBotX input adapter should not bypass focused inbound command providers");
    assertTrue(
        !inputParser.contains("\"SETNAME\".equalsIgnoreCase")
            && !inputParser.contains("\"CHGHOST\".equalsIgnoreCase"),
        "the PircBotX input adapter should not retain duplicate identity command parsing");

    String negotiationAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/parse/"
                    + "PircbotxCapabilityNegotiationSupport.java"));
    String registrationAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/listener/"
                    + "PircbotxRegistrationLifecycleHandler.java"));
    String playbackAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/client/"
                    + "PircbotxZncPlaybackRequestSupport.java"));
    String batchCollector =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/emit/"
                    + "PircbotxChatHistoryBatchCollector.java"));
    String privateConversationAdapter =
        Files.readString(
            Path.of(
                "src/main/java/cafe/woden/ircclient/irc/pircbotx/emit/"
                    + "PircbotxPrivateConversationSupport.java"));
    assertTrue(
        negotiationAdapter.contains("Ircv3ZncPlaybackRuntimeSupport")
            && negotiationAdapter.contains("detectZncCapability")
            && !negotiationAdapter.contains("Ircv3ZncDetector"),
        "CAP-based ZNC detection should use installed focused BATCH/ZNC playback runtime providers");
    assertTrue(
        registrationAdapter.contains("Ircv3ZncPlaybackRuntimeSupport")
            && registrationAdapter.contains("detectZncRpl004")
            && !registrationAdapter.contains("Ircv3ZncDetector"),
        "RPL 004 ZNC detection should use installed focused BATCH/ZNC playback runtime providers");
    assertTrue(
        playbackAdapter.contains("Ircv3ZncPlaybackRequestPlanner")
            && playbackAdapter.contains("Ircv3OutboundCommandRuntimeCatalog"),
        "ZNC playback validation should remain feature-owned while rendering uses runtime SPI");
    assertTrue(
        !playbackAdapter.contains("renderCommand") && !playbackAdapter.contains("\"play "),
        "the root playback adapter should not retain ZNC wire-command rendering");
    assertTrue(
        registrationAdapter.contains("Ircv3OutboundCommandRuntimeCatalog")
            && !registrationAdapter.contains("renderCommand")
            && !registrationAdapter.contains("\"play * "),
        "registration playback bootstrap should render through the outbound runtime SPI");
    assertTrue(
        batchCollector.contains("Ircv3InboundCommandSignalRuntimeCatalog")
            && batchCollector.contains("Ircv3InboundTagSignalRuntimeCatalog"),
        "chat-history BATCH lifecycle and reference tags should use runtime SPI catalogs");
    assertTrue(
        !batchCollector.contains("Ircv3HistoryBatchControlParser")
            && !batchCollector.contains("Ircv3BatchTag"),
        "the root collector should not bypass focused BATCH/ZNC playback runtime providers");
    assertTrue(
        !batchCollector.contains("isChatHistoryBatchType"),
        "the root collector should not retain duplicate batch-type policy");
    assertTrue(
        privateConversationAdapter.contains("Ircv3ZncPlaybackRuntimeSupport")
            && privateConversationAdapter.contains("shouldSuppressBootstrap")
            && !privateConversationAdapter.contains("Ircv3HistoryBootstrapSuppressionPolicy"),
        "private conversation routing should use installed bootstrap-suppression providers");
    assertTrue(
        !privateConversationAdapter.contains("ListNetworks")
            && !privateConversationAdapter.contains("play *"),
        "the root private conversation adapter should not retain bootstrap command policy");
  }

  private static void assertMessageTagSignalBuild(Path buildFile) throws IOException {
    String source = Files.readString(buildFile);
    assertTrue(
        source.contains("implementation project(':ircafe-feature-ircv3-message-tags')"),
        buildFile + " should consume shared tag decoding through message-tags");
    assertTrue(
        !source.contains("org.pircbotx"), buildFile + " should remain transport-independent");
  }

  private static void assertTransportIndependentFocusedBuild(Path buildFile, boolean requiresCommon)
      throws IOException {
    String source = Files.readString(buildFile);
    assertTrue(
        !source.contains("project(':ircafe-feature-ircv3')"),
        buildFile + " must not depend on the removed compatibility umbrella");
    assertTrue(
        !source.contains("org.pircbotx"), buildFile + " should remain transport-independent");
    if (requiresCommon) {
      assertTrue(
          source.contains("implementation project(':ircafe-feature-ircv3-common')"),
          buildFile + " should consume shared IRCv3 command policy through common");
    }
  }

  @Test
  void ircv3CycloneDxTasksUseSharedTransitiveFeatureJarConvention() throws IOException {
    Path convention = Path.of("gradle/java-library-subproject-conventions.gradle");
    String conventionSource = Files.readString(convention);

    assertTrue(
        conventionSource.contains("collectProjectDependencies")
            && conventionSource.contains("withType(org.gradle.api.artifacts.ProjectDependency)")
            && conventionSource.contains("source.project(dependency.path)")
            && conventionSource.contains(
                "collectProjectDependencies(dependencyProject, discovered)")
            && conventionSource.contains("cyclonedxDirectBom")
            && conventionSource.contains("dependsOn(provider")
            && conventionSource.contains("it.tasks.named('jar')"),
        "the shared Java-library convention should make CycloneDX depend recursively on project "
            + "JAR producers");

    assertUsesIrcv3FeatureConvention(Path.of("ircafe-feature-ircv3-channel-context/build.gradle"));
    assertUsesIrcv3FeatureConvention(Path.of("ircafe-feature-ircv3-server-time/build.gradle"));
    assertUsesIrcv3FeatureConvention(Path.of("ircafe-feature-ircv3-echo-message/build.gradle"));
  }

  private static void assertUsesIrcv3FeatureConvention(Path buildFile) throws IOException {
    String source = Files.readString(buildFile);
    assertTrue(
        source.contains("gradle/ircv3-feature-conventions.gradle"),
        buildFile
            + " should inherit transitive CycloneDX producer wiring from the shared convention");
  }

  @Test
  void ircv3SpringWiringUsesCanonicalRuntimeCatalogBundle() throws IOException {
    String catalogs =
        Files.readString(
            Path.of("src/main/java/cafe/woden/ircclient/irc/ircv3/" + "Ircv3RuntimeCatalogs.java"));
    assertTrue(
        catalogs.contains("@Component")
            && catalogs.contains("record Ircv3RuntimeCatalogs")
            && catalogs.contains("Ircv3InboundCommandSignalRuntimeCatalog inboundCommands")
            && catalogs.contains("Ircv3InboundTagSignalRuntimeCatalog inboundTags")
            && catalogs.contains("Ircv3OutboundCommandRuntimeCatalog outboundCommands")
            && catalogs.contains("Ircv3MessageMutationRuntimeCatalog messageMutations")
            && catalogs.contains("Ircv3MessageTagsRuntimeCatalog messageTags"),
        "Spring should expose one authoritative bundle for the installed IRCv3 runtime catalogs");

    assertUsesRuntimeCatalogBundle(QuasselIrcv3RuntimeSupport.class);
    assertUsesRuntimeCatalogBundle(MatrixIrcv3RuntimeSupport.class);
    assertUsesRuntimeCatalogBundle(PircbotxInputParserHookInstaller.class);
    assertUsesRuntimeCatalogBundle(PircbotxBridgeListenerFactory.class);
    assertUsesRuntimeCatalogBundle(PircbotxBotFactory.class);
  }

  @Test
  void ircv3OutboundApplicationServicesRequireExplicitRuntimeSupportInjection() throws IOException {
    assertExplicitRuntimeSupportInjection(
        Path.of(
            "src/main/java/cafe/woden/ircclient/app/outbound/chathistory/"
                + "OutboundChatHistoryCommandService.java"),
        "public OutboundChatHistoryCommandService(IrcClientService irc, TargetCoordinator targetCoordinator, Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport, OutboundChatHistoryRequestSupport chatHistoryRequestSupport, Ircv3ChatHistoryRuntimeSupport chatHistoryRuntimeSupport)");
    assertExplicitRuntimeSupportInjection(
        Path.of(
            "src/main/java/cafe/woden/ircclient/app/outbound/monitor/"
                + "OutboundMonitorCommandService.java"),
        "public OutboundMonitorCommandService(MonitorRosterPort monitorRosterPort, OutboundMonitorCommandSupport monitorCommandSupport, Ircv3MonitorCommandRuntimeSupport monitorRuntimeSupport)");
    assertExplicitRuntimeSupportInjection(
        Path.of(
            "src/main/java/cafe/woden/ircclient/app/outbound/support/"
                + "OutboundRawLineCorrelationService.java"),
        "public OutboundRawLineCorrelationService(OutboundBackendCapabilityPolicy backendCapabilityPolicy, LabeledResponseRoutingPort labeledResponseRoutingState, Ircv3OutboundCommandRuntimeCatalog outboundCommandRuntimeCatalog, Ircv3LabeledResponseRuntimeSupport labeledResponseRuntimeSupport)");
  }

  private static void assertExplicitRuntimeSupportInjection(
      Path sourceFile, String springConstructorFragment) throws IOException {
    String source = Files.readString(sourceFile);
    assertTrue(
        containsIgnoringWhitespace(source, "@Autowired " + springConstructorFragment),
        sourceFile + " should expose one explicit Spring runtime-support constructor");
    assertTrue(
        !source.contains("applicationClasspath()"),
        sourceFile + " should not bootstrap IRCv3 providers inside an application service");
  }

  @Test
  void javaFormattingCoverageIncludesEveryRegisteredSubprojectSourceTree() throws IOException {
    String quality = Files.readString(Path.of("gradle/quality.gradle"));
    assertTrue(
        quality.contains("'src/**/*.java'")
            && quality.contains("'ircafe-*/src/**/*.java'")
            && quality.contains("tasks.register('verifyJavaFormattingCoverage')")
            && quality.contains("rootProject.allprojects.collectMany")
            && quality.contains("candidate.fileTree('src')")
            && quality.contains("include '**/*.java'")
            && quality.contains("include(javaFormattingTargets)"),
        "formatting coverage should include root and every registered Java subproject "
            + "without enumerating modules");
  }

  @Test
  void pullRequestFormattingAutofixDoesNotRunErrorPronePatches() throws IOException {
    String quality = Files.readString(Path.of("gradle/quality.gradle"));
    String workflow = Files.readString(Path.of(".github/workflows/pr-spotless-autofix.yml"));

    int taskStart = quality.indexOf("tasks.register('applyFormatting')");
    int taskEnd = quality.indexOf("tasks.named('spotlessCheck')", taskStart);
    assertTrue(taskStart >= 0 && taskEnd > taskStart, "the formatting-only task should exist");

    String taskSource = quality.substring(taskStart, taskEnd);
    assertTrue(
        containsIgnoringWhitespace(
                taskSource, "dependsOn('spotlessJavaApply', 'spotlessMiscApply')")
            && !taskSource.contains("errorProneApply")
            && !taskSource.contains("spotlessApply"),
        "the formatting-only lifecycle task should use only focused Spotless targets");
    assertTrue(
        workflow.contains("run: ./gradlew --no-daemon applyFormatting")
            && !workflow.contains("run: ./gradlew --no-daemon spotlessApply")
            && !workflow.contains("errorProneApply"),
        "the PR autofix workflow should not run aggregate Spotless or Error Prone patch tasks");
  }

  @Test
  void ircv3MigrationCheckUsesRegisteredProjectsWithoutFormattingTasks() throws IOException {
    String verification = Files.readString(Path.of("gradle/plugin-release-verification.gradle"));
    assertTrue(
        verification.contains("tasks.register('ircv3MigrationCheck')")
            && verification.contains("it.name.startsWith('ircafe-feature-ircv3-')")
            && verification.contains("it.name.startsWith('ircafe-builtins-ircv3-')")
            && verification.contains("it.tasks.named('test')")
            && verification.contains("it.tasks.named('cyclonedxDirectBom')")
            && verification.contains("tasks.named('architectureTest')")
            && verification.contains("tasks.named('verifyBuiltInProviderPackaging')")
            && verification.contains("tasks.named('verifyBootJarPluginPackaging')")
            && verification.contains("tasks.named('verifyJavaFormattingCoverage')"),
        "IRCv3 verification should discover registered projects and cover tests, boundaries, "
            + "BOMs, packaging, and formatting coverage");

    int taskStart = verification.indexOf("tasks.register('ircv3MigrationCheck')");
    int taskEnd = verification.indexOf("tasks.register('externalPluginSmokeTest'", taskStart);
    String taskSource = verification.substring(taskStart, taskEnd);
    assertTrue(
        !taskSource.contains("spotlessCheck") && !taskSource.contains("spotlessApply"),
        "the authoritative IRCv3 migration check must not run formatting tasks");
  }

  private static void assertUsesRuntimeCatalogBundle(Class<?> componentType) {
    boolean hasAutowiredBundleConstructor =
        Arrays.stream(componentType.getConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .anyMatch(
                constructor ->
                    Arrays.stream(constructor.getParameterTypes())
                        .anyMatch(Ircv3RuntimeCatalogs.class::equals));
    assertTrue(
        hasAutowiredBundleConstructor,
        componentType.getName()
            + " should use the canonical runtime catalog bundle for Spring wiring");
  }

  @Test
  void commandFeatureSubprojectStaysInsideAppCommandsNamedInterface() throws IOException {
    Path commandsSourceRoot = Path.of("ircafe-feature-commands/src/main/java");
    assertTrue(
        Files.isDirectory(commandsSourceRoot),
        "the command feature project should have a main Java source root");

    Set<String> violations = new TreeSet<>();
    try (Stream<Path> files = Files.walk(commandsSourceRoot)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
        Matcher matcher = PACKAGE_PATTERN.matcher(Files.readString(file));
        String packageName = matcher.find() ? matcher.group(1) : "";
        if (!isAppCommandsPackage(packageName)) {
          violations.add(
              file
                  + " -> "
                  + (packageName.isBlank() ? "<missing package declaration>" : packageName));
        }
      }
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "ircafe-feature-commands is a Gradle feature split, but its code should remain in "
                + "cafe.woden.ircclient.app.commands so Spring Modulith keeps it inside the "
                + "existing app::commands named interface. A top-level commands package "
                + "reintroduces an app -> commands -> app cycle. Violations:\n  "
                + String.join("\n  ", violations));
  }

  private static Set<Path> featureSourceRoots() throws IOException {
    Set<Path> sourceRoots = new TreeSet<>();
    for (Path projectDir : featureProjectDirs()) {
      Path sourceRoot = projectDir.resolve("src/main/java");
      if (Files.isDirectory(sourceRoot)) {
        sourceRoots.add(sourceRoot);
      }
    }
    return sourceRoots;
  }

  private static Set<String> featureClassNames() throws IOException {
    Set<String> classNames = new TreeSet<>();
    for (Path sourceRoot : featureSourceRoots()) {
      try (Stream<Path> files = Files.walk(sourceRoot)) {
        files
            .filter(path -> path.toString().endsWith(".java"))
            .sorted()
            .map(sourceRoot::relativize)
            .map(FeatureSubprojectBoundaryTest::javaClassName)
            .forEach(classNames::add);
      }
    }
    return classNames;
  }

  private static String javaClassName(Path relativeSourcePath) {
    String className = relativeSourcePath.toString().replace('/', '.').replace('\\', '.');
    return className.substring(0, className.length() - ".java".length());
  }

  private static Set<Path> featureProjectDirs() throws IOException {
    Set<Path> projectDirs = new TreeSet<>();
    for (String projectName : featureProjectNames()) {
      Path projectDir = Path.of(projectName);
      assertTrue(
          Files.isDirectory(projectDir),
          "settings.gradle includes " + projectName + " but its project directory is missing");
      projectDirs.add(projectDir);
    }
    return projectDirs;
  }

  private static Set<String> featureProjectNames() throws IOException {
    Set<String> projectNames = new TreeSet<>();
    Matcher matcher = FEATURE_INCLUDE_PATTERN.matcher(Files.readString(Path.of("settings.gradle")));
    while (matcher.find()) {
      projectNames.add(matcher.group(1));
    }
    assertTrue(!projectNames.isEmpty(), "settings.gradle should declare feature projects");
    return projectNames;
  }

  private static boolean isRootImplementationImport(
      String dependency, Set<String> featureClassNames) {
    return dependency.startsWith("cafe.woden.ircclient.")
        && !dependency.contains(".spi.")
        && !isFeatureClassImport(dependency, featureClassNames);
  }

  private static boolean isAppCommandsPackage(String packageName) {
    return packageName.equals("cafe.woden.ircclient.app.commands")
        || packageName.startsWith("cafe.woden.ircclient.app.commands.");
  }

  private static boolean isFeatureClassImport(String dependency, Set<String> featureClassNames) {
    return featureClassNames.stream()
        .anyMatch(
            className -> dependency.equals(className) || dependency.startsWith(className + "."));
  }
}
