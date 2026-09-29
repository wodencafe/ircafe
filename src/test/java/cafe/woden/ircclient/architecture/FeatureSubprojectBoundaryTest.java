package cafe.woden.ircclient.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Checks artifact ownership and dependency direction, independent of Java import syntax or method
 * bodies. Protocol behavior belongs in the feature and runtime-adapter unit tests.
 */
class FeatureSubprojectBoundaryTest {

  private static final Map<String, JavaClasses> FEATURES = importFeatures();
  private static final JavaClasses ROOT = CompiledSubprojects.importProject("root");
  private static final JavaClasses API = CompiledSubprojects.importProject("ircafe-plugin-api");
  private static final String IRCV3 = "cafe.woden.ircclient.irc.ircv3.";

  // These existing feature values carry validated provider output or normalized CAP input.
  // The adapters may construct/inspect them, but must not invoke the feature's planners/parsers.
  private static final Set<String> RUNTIME_VALUES =
      Set.of(
          IRCV3 + "Ircv3CapabilityLine",
          IRCV3 + "Ircv3SaslCapabilityOffer",
          IRCV3 + "Ircv3SaslFailureSignal",
          IRCV3 + "Ircv3SaslIrcLine",
          IRCV3 + "Ircv3StsPolicy",
          IRCV3 + "Ircv3StsPolicyLearningPlanner$Decision",
          IRCV3 + "Ircv3StsPolicyLearningPlanner$Outcome");

  @Test
  void featureSubprojectsDoNotDependOnRootImplementationTypes() {
    Set<String> allowedTypes = new TreeSet<>(CompiledSubprojects.names(API));
    FEATURES.values().forEach(classes -> allowedTypes.addAll(CompiledSubprojects.names(classes)));
    FEATURES.forEach(
        (project, classes) ->
            CompiledBoundaryRules.featureDependencies(allowedTypes)
                .as(project + " must not depend on root implementation types")
                .check(classes));
  }

  @Test
  void artifactsDoNotContainDuplicateImplementationClasses() {
    Map<String, Set<String>> owners = new LinkedHashMap<>();
    Map<String, JavaClasses> artifacts = new LinkedHashMap<>(FEATURES);
    artifacts.put("root", ROOT);
    artifacts.put("ircafe-plugin-api", API);
    artifacts.forEach(
        (artifact, classes) ->
            classes.forEach(
                type -> {
                  // Split packages can carry module metadata in more than one artifact.
                  if (!type.getSimpleName().equals("package-info")
                      && !type.getName().equals("module-info")) {
                    owners
                        .computeIfAbsent(type.getName(), ignored -> new TreeSet<>())
                        .add(artifact);
                  }
                }));
    assertThat(owners.entrySet().stream().filter(entry -> entry.getValue().size() > 1).toList())
        .as("each implementation class must be owned by one Gradle artifact")
        .isEmpty();
  }

  @Test
  void protocolFeaturesRemainTransportAndSwingIndependent() {
    FEATURES.entrySet().stream()
        .filter(
            entry ->
                entry.getKey().startsWith("ircafe-feature-ircv3-")
                    || entry.getKey().equals("ircafe-feature-bouncer")
                    || entry.getKey().equals("ircafe-feature-commands")
                    || entry.getKey().equals("ircafe-feature-user-lookup"))
        .forEach(
            entry ->
                CompiledBoundaryRules.transportIndependentFeatures()
                    .as(entry.getKey() + " must remain transport and Swing independent")
                    .check(entry.getValue()));
  }

  @Test
  void extractedFeaturesStayInsideTheirModulithPackages() {
    CompiledBoundaryRules.featurePackages("cafe.woden.ircclient.app.commands..")
        .check(FEATURES.get("ircafe-feature-commands"));
    CompiledBoundaryRules.featurePackages("cafe.woden.ircclient.irc.enrichment..")
        .check(FEATURES.get("ircafe-feature-user-lookup"));
    CompiledBoundaryRules.featurePackages("cafe.woden.ircclient.bouncer..")
        .check(FEATURES.get("ircafe-feature-bouncer"));
    FEATURES.entrySet().stream()
        .filter(entry -> entry.getKey().startsWith("ircafe-feature-ircv3-"))
        .forEach(
            entry ->
                CompiledBoundaryRules.featurePackages(
                        "cafe.woden.ircclient.irc.ircv3..", "cafe.woden.ircclient.state..")
                    .check(entry.getValue()));
  }

  @Test
  void requestTrackingDependsOnlyOnTheJdk() {
    CompiledBoundaryRules.jdkOnly("cafe.woden.ircclient.state.LabeledResponseRequestStore")
        .check(FEATURES.get("ircafe-feature-ircv3-labeled-response"));
  }

  @Test
  void runtimeAdaptersDoNotLinkFeatureImplementations() {
    Set<String> featureTypes =
        FEATURES.values().stream()
            .flatMap(classes -> CompiledSubprojects.names(classes).stream())
            .filter(name -> !RUNTIME_VALUES.contains(name))
            .collect(Collectors.toSet());
    CompiledBoundaryRules.mustNotBypassRuntimeProviders(
            new DescribedPredicate<>("IRCv3 runtime adapters and catalogs") {
              @Override
              public boolean test(JavaClass type) {
                String name = type.getName().split("\\$", 2)[0];
                return name.startsWith(IRCV3)
                    && (name.endsWith("RuntimeSupport")
                        || name.endsWith("RuntimeCatalog")
                        || name.endsWith("RuntimeCatalogs"));
              }
            },
            featureTypes)
        .check(ROOT);
  }

  @Test
  void applicationServicesDoNotBootstrapRuntimeProviders() {
    noClasses()
        .that()
        .resideInAPackage("cafe.woden.ircclient.app..")
        .should()
        .callMethodWhere(
            new DescribedPredicate<>("applicationClasspath provider bootstrap") {
              @Override
              public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall call) {
                return call.getTarget().getName().equals("applicationClasspath")
                    && call.getTargetOwner().getName().startsWith(IRCV3);
              }
            })
        .check(ROOT);
  }

  @Test
  void transportAndApplicationAdaptersDoNotBypassFeatureProviders() {
    Set<String> providerPolicy =
        Set.of(
            "Ircv3AwayLineParser",
            "Ircv3AwayNotifySignalParser",
            "Ircv3AccountNotifySignalParser",
            "Ircv3ExtendedJoinSignalParser",
            "Ircv3ChghostParser",
            "Ircv3SetnameParser",
            "Ircv3InviteNotifyParser",
            "Ircv3MonitorParser",
            "Ircv3StandardReplyParser",
            "Ircv3WhoUserhostParser",
            "Ircv3WhoisParser",
            "Ircv3IsupportLine",
            "Ircv3ClientTagPolicy",
            "Ircv3ReplyTagSignal",
            "Ircv3ReactionTagSignal",
            "Ircv3TypingTagSignal",
            "Ircv3ReadMarkerTagSignal",
            "Ircv3MessageRedactionTagSignal",
            "Ircv3MessageRedactionCommandSignal",
            "Ircv3ReadMarkerCommandSignal",
            "Ircv3MessageEditTagSignal",
            "Ircv3MessageIdTagPolicy",
            "Ircv3ServerTime",
            "Ircv3ServerTimeLagSample",
            "Ircv3EchoMessageTargetHintPlanner",
            "Ircv3CapabilityFallbackPlanner",
            "Ircv3CapabilityChangePlanner",
            "Ircv3MultilineCapabilityStatePlanner",
            "Ircv3MultilineCommandPlanner",
            "Ircv3LabeledResponseRawLinePreparer",
            "Ircv3LabeledResponseValues",
            "Ircv3LabeledResponseTagSignal",
            "Ircv3ChatHistoryCommandBuilder",
            "Ircv3HistoryBatchControlParser",
            "Ircv3ZncDetector",
            "Ircv3HistoryBootstrapSuppressionPolicy",
            "Ircv3SaslAuthenticateFraming",
            "Ircv3ScramSaslConversation",
            "Ircv3StsPolicyParser",
            "Ircv3StsPolicyLearningPlanner");
    Set<String> policyTypes =
        FEATURES.values().stream()
            .flatMap(JavaClasses::stream)
            .filter(
                type ->
                    providerPolicy.contains(
                        type.getName()
                            .substring(type.getName().lastIndexOf('.') + 1)
                            .split("\\$", 2)[0]))
            .map(JavaClass::getName)
            .filter(name -> !RUNTIME_VALUES.contains(name))
            .collect(Collectors.toSet());
    CompiledBoundaryRules.mustNotBypassRuntimeProviders(
            new DescribedPredicate<>("root protocol consumers") {
              @Override
              public boolean test(JavaClass type) {
                return type.getPackageName().startsWith("cafe.woden.ircclient.irc.")
                    || type.getPackageName().startsWith("cafe.woden.ircclient.app.");
              }
            },
            policyTypes)
        .check(ROOT);
  }

  @Test
  void tagAndSaslTransportAdaptersUseRuntimeInterpretation() {
    // The raw input parser also uses channel-target classification for redaction; only tag
    // interpretation belongs behind the channel-context provider boundary.
    CompiledBoundaryRules.mustNotBypassRuntimeProviders(
            JavaClass.Predicates.simpleName("PircbotxTagSignalSupport"),
            Set.of(IRCV3 + "Ircv3ChannelContextPolicy"))
        .check(ROOT);
    CompiledBoundaryRules.mustNotBypassRuntimeProviders(
            JavaClass.Predicates.simpleName("MultiSaslCapHandler"),
            Set.of(IRCV3 + "Ircv3SaslCapabilityOffer"))
        .check(ROOT);
  }

  @Test
  void rootFilterParserDoesNotBypassTheFeatureDispatcher() {
    String commands = "cafe.woden.ircclient.app.commands.";
    noClasses()
        .that()
        .haveFullyQualifiedName(commands + "FilterCommandParser")
        .should()
        .dependOnClassesThat()
        .haveNameMatching(
            "cafe\\.woden\\.ircclient\\.app\\.commands\\.(CommandLineTokenizer|Filter(DisplayCommand|ManagementCommand|LifecycleCommand|RuleMutationCommand|RulePatch)Parser)(\\$.*)?")
        .check(ROOT);
  }

  @ParameterizedTest(name = "{0} owns {1}")
  @CsvSource({
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.BouncerAutoConnectNetworkKeyNormalizer",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.BouncerAutoConnectRulesState",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.BouncerBackendCatalog",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.BouncerDiscoveredNetworkMaterializer",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.BouncerDiscoveryEventRouter",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.BouncerDiscoveryHandlerCatalog",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.BouncerMappingStrategySelector",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.BouncerPluginProviderCatalog",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.GenericBouncerDiscoveryLineParser",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.SojuBouncerProtocolParser",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.ZncAutoConnectNetworkKeyNormalizer",
    "ircafe-feature-bouncer, cafe.woden.ircclient.bouncer.ZncBouncerListNetworksParser",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.CommandLineTokenizer",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterCommandSpec",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterCommandSpecParser",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterDisplayCommandParser",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterDisplayCommandSpec",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterLifecycleCommandParser",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterLifecycleCommandSpec",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterManagementCommandParser",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterManagementCommandSpec",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterMoveModeSpec",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterRuleMutationCommandParser",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterRuleMutationCommandSpec",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterRulePatchParser",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterRulePatchSpec",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterScopePatternNormalizer",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterTargetActionSpec",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterToggleModeSpec",
    "ircafe-feature-commands, cafe.woden.ircclient.app.commands.FilterTriStateSpec",
    "ircafe-feature-ircv3-account-notify, cafe.woden.ircclient.irc.ircv3.Ircv3AccountNotifySignalParser",
    "ircafe-feature-ircv3-account-tag, cafe.woden.ircclient.irc.ircv3.Ircv3AccountTagSignal",
    "ircafe-feature-ircv3-account-tag, cafe.woden.ircclient.irc.ircv3.Ircv3AccountTagTracker",
    "ircafe-feature-ircv3-away-notify, cafe.woden.ircclient.irc.ircv3.Ircv3AwayLineParser",
    "ircafe-feature-ircv3-away-notify, cafe.woden.ircclient.irc.ircv3.Ircv3AwayNotifySignalParser",
    "ircafe-feature-ircv3-batch, cafe.woden.ircclient.irc.ircv3.Ircv3HistoryBatchControlParser",
    "ircafe-feature-ircv3-channel-context, cafe.woden.ircclient.irc.ircv3.Ircv3ChannelContextPolicy",
    "ircafe-feature-ircv3-chat-history, cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryAvailability",
    "ircafe-feature-ircv3-chat-history, cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistoryCommandBuilder",
    "ircafe-feature-ircv3-chat-history, cafe.woden.ircclient.irc.ircv3.Ircv3ChatHistorySelectors",
    "ircafe-feature-ircv3-chghost, cafe.woden.ircclient.irc.ircv3.Ircv3ChghostParser",
    "ircafe-feature-ircv3-chghost, cafe.woden.ircclient.irc.ircv3.Ircv3HostmaskChangeTracker",
    "ircafe-feature-ircv3-common, cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityLine",
    "ircafe-feature-ircv3-common, cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityToken",
    "ircafe-feature-ircv3-common, cafe.woden.ircclient.irc.ircv3.Ircv3CommandValuePolicy",
    "ircafe-feature-ircv3-common, cafe.woden.ircclient.irc.ircv3.Ircv3IsupportLine",
    "ircafe-feature-ircv3-common, cafe.woden.ircclient.irc.ircv3.Ircv3TaggedCommandDraft",
    "ircafe-feature-ircv3-echo-message, cafe.woden.ircclient.irc.ircv3.Ircv3EchoMessageAvailability",
    "ircafe-feature-ircv3-echo-message, cafe.woden.ircclient.irc.ircv3.Ircv3EchoMessageTargetHintPlanner",
    "ircafe-feature-ircv3-echo-message, cafe.woden.ircclient.irc.ircv3.Ircv3EchoMessageTargetHintStore",
    "ircafe-feature-ircv3-extended-join, cafe.woden.ircclient.irc.ircv3.Ircv3ExtendedJoinSignalParser",
    "ircafe-feature-ircv3-invite-notify, cafe.woden.ircclient.irc.ircv3.Ircv3InviteNotifyParser",
    "ircafe-feature-ircv3-labeled-response, cafe.woden.ircclient.irc.ircv3.Ircv3LabeledResponseRawLinePreparer",
    "ircafe-feature-ircv3-labeled-response, cafe.woden.ircclient.irc.ircv3.Ircv3LabeledResponseTagSignal",
    "ircafe-feature-ircv3-labeled-response, cafe.woden.ircclient.irc.ircv3.Ircv3LabeledResponseValues",
    "ircafe-feature-ircv3-labeled-response, cafe.woden.ircclient.state.LabeledResponseRequestStore",
    "ircafe-feature-ircv3-message-edit, cafe.woden.ircclient.irc.ircv3.Ircv3MessageEditCommandBuilder",
    "ircafe-feature-ircv3-message-edit, cafe.woden.ircclient.irc.ircv3.Ircv3MessageEditTagSignal",
    "ircafe-feature-ircv3-message-id, cafe.woden.ircclient.irc.ircv3.Ircv3MessageIdTagPolicy",
    "ircafe-feature-ircv3-message-redaction, cafe.woden.ircclient.irc.ircv3.Ircv3MessageRedactionCommandBuilder",
    "ircafe-feature-ircv3-message-redaction, cafe.woden.ircclient.irc.ircv3.Ircv3MessageRedactionCommandSignal",
    "ircafe-feature-ircv3-message-redaction, cafe.woden.ircclient.irc.ircv3.Ircv3MessageRedactionTagSignal",
    "ircafe-feature-ircv3-message-tags, cafe.woden.ircclient.irc.ircv3.Ircv3BatchTag",
    "ircafe-feature-ircv3-message-tags, cafe.woden.ircclient.irc.ircv3.Ircv3ClientTagPolicy",
    "ircafe-feature-ircv3-message-tags, cafe.woden.ircclient.irc.ircv3.Ircv3Tags",
    "ircafe-feature-ircv3-monitor, cafe.woden.ircclient.irc.ircv3.Ircv3MonitorCommandPlanner",
    "ircafe-feature-ircv3-monitor, cafe.woden.ircclient.irc.ircv3.Ircv3MonitorParser",
    "ircafe-feature-ircv3-multiline, cafe.woden.ircclient.irc.ircv3.Ircv3MultilineAccumulator",
    "ircafe-feature-ircv3-multiline, cafe.woden.ircclient.irc.ircv3.Ircv3MultilineCapabilityStatePlanner",
    "ircafe-feature-ircv3-multiline, cafe.woden.ircclient.irc.ircv3.Ircv3MultilineCommandPlanner",
    "ircafe-feature-ircv3-multiline, cafe.woden.ircclient.irc.ircv3.Ircv3MultilineLimitPolicy",
    "ircafe-feature-ircv3-multiline, cafe.woden.ircclient.irc.ircv3.Ircv3MultilineMessagePolicy",
    "ircafe-feature-ircv3-multiline, cafe.woden.ircclient.irc.ircv3.Ircv3MultilinePayload",
    "ircafe-feature-ircv3-multiline, cafe.woden.ircclient.irc.ircv3.Ircv3MultilineSupport",
    "ircafe-feature-ircv3-negotiation, cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityChangePlanner",
    "ircafe-feature-ircv3-negotiation, cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityFallbackPlanner",
    "ircafe-feature-ircv3-negotiation, cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityRequestBatchSession",
    "ircafe-feature-ircv3-negotiation, cafe.woden.ircclient.irc.ircv3.Ircv3CapabilitySnapshot",
    "ircafe-feature-ircv3-negotiation, cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityState",
    "ircafe-feature-ircv3-negotiation, cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionMetadataCatalog",
    "ircafe-feature-ircv3-negotiation, cafe.woden.ircclient.irc.ircv3.Ircv3FeatureAvailabilityEvaluator",
    "ircafe-feature-ircv3-negotiation, cafe.woden.ircclient.irc.ircv3.Ircv3TrackedCapability",
    "ircafe-feature-ircv3-reactions, cafe.woden.ircclient.irc.ircv3.Ircv3ReactionCommandBuilder",
    "ircafe-feature-ircv3-reactions, cafe.woden.ircclient.irc.ircv3.Ircv3ReactionDraftPolicy",
    "ircafe-feature-ircv3-reactions, cafe.woden.ircclient.irc.ircv3.Ircv3ReactionTagSignal",
    "ircafe-feature-ircv3-read-marker, cafe.woden.ircclient.irc.ircv3.Ircv3ReadMarkerCommandBuilder",
    "ircafe-feature-ircv3-read-marker, cafe.woden.ircclient.irc.ircv3.Ircv3ReadMarkerCommandSignal",
    "ircafe-feature-ircv3-read-marker, cafe.woden.ircclient.irc.ircv3.Ircv3ReadMarkerTagSignal",
    "ircafe-feature-ircv3-read-marker, cafe.woden.ircclient.irc.ircv3.Ircv3ReadMarkerTimestamp",
    "ircafe-feature-ircv3-reply, cafe.woden.ircclient.irc.ircv3.Ircv3ReplyCommandBuilder",
    "ircafe-feature-ircv3-reply, cafe.woden.ircclient.irc.ircv3.Ircv3ReplyDraftPolicy",
    "ircafe-feature-ircv3-reply, cafe.woden.ircclient.irc.ircv3.Ircv3ReplyTagSignal",
    "ircafe-feature-ircv3-sasl, cafe.woden.ircclient.irc.ircv3.Ircv3SaslAuthenticateFraming",
    "ircafe-feature-ircv3-sasl, cafe.woden.ircclient.irc.ircv3.Ircv3SaslCapabilityOffer",
    "ircafe-feature-ircv3-sasl, cafe.woden.ircclient.irc.ircv3.Ircv3SaslFailureSignal",
    "ircafe-feature-ircv3-sasl, cafe.woden.ircclient.irc.ircv3.Ircv3SaslResponseFactory",
    "ircafe-feature-ircv3-sasl, cafe.woden.ircclient.irc.ircv3.Ircv3SaslSession",
    "ircafe-feature-ircv3-sasl, cafe.woden.ircclient.irc.ircv3.Ircv3SaslSessionUpdate",
    "ircafe-feature-ircv3-sasl, cafe.woden.ircclient.irc.ircv3.Ircv3ScramSaslConversation",
    "ircafe-feature-ircv3-server-time, cafe.woden.ircclient.irc.ircv3.Ircv3ServerTime",
    "ircafe-feature-ircv3-server-time, cafe.woden.ircclient.irc.ircv3.Ircv3ServerTimeLagSample",
    "ircafe-feature-ircv3-setname, cafe.woden.ircclient.irc.ircv3.Ircv3SetnameParser",
    "ircafe-feature-ircv3-standard-replies, cafe.woden.ircclient.irc.ircv3.Ircv3StandardReplyParser",
    "ircafe-feature-ircv3-sts, cafe.woden.ircclient.irc.ircv3.Ircv3StsPersistedPolicyNormalizer",
    "ircafe-feature-ircv3-sts, cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicy",
    "ircafe-feature-ircv3-sts, cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyLearningPlanner",
    "ircafe-feature-ircv3-sts, cafe.woden.ircclient.irc.ircv3.Ircv3StsPolicyParser",
    "ircafe-feature-ircv3-sts, cafe.woden.ircclient.irc.ircv3.Ircv3StsTransportUpgradePlanner",
    "ircafe-feature-ircv3-typing, cafe.woden.ircclient.irc.ircv3.Ircv3TypingClientTagPolicy",
    "ircafe-feature-ircv3-typing, cafe.woden.ircclient.irc.ircv3.Ircv3TypingCommandBuilder",
    "ircafe-feature-ircv3-typing, cafe.woden.ircclient.irc.ircv3.Ircv3TypingTagSignal",
    "ircafe-feature-ircv3-user-identity, cafe.woden.ircclient.irc.ircv3.Ircv3WhoUserhostParser",
    "ircafe-feature-ircv3-user-identity, cafe.woden.ircclient.irc.ircv3.Ircv3WhoisParser",
    "ircafe-feature-ircv3-user-identity, cafe.woden.ircclient.irc.ircv3.Ircv3WhoisProbeTracker",
    "ircafe-feature-ircv3-user-identity, cafe.woden.ircclient.irc.ircv3.Ircv3WhoxSchemaTracker",
    "ircafe-feature-ircv3-znc-playback, cafe.woden.ircclient.irc.ircv3.Ircv3HistoryBootstrapSuppressionPolicy",
    "ircafe-feature-ircv3-znc-playback, cafe.woden.ircclient.irc.ircv3.Ircv3ZncDetector",
    "ircafe-feature-ircv3-znc-playback, cafe.woden.ircclient.irc.ircv3.Ircv3ZncPlaybackRequestPlanner",
    "ircafe-feature-user-lookup, cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentPlanner"
  })
  void extractedPolicyIsOwnedByItsFeatureArtifact(String project, String className) {
    assertThat(CompiledSubprojects.names(FEATURES.get(project))).as(project).contains(className);
  }

  private static Map<String, JavaClasses> importFeatures() {
    Map<String, JavaClasses> features = new LinkedHashMap<>();
    for (String project : CompiledSubprojects.projectNames("ircafe-feature-")) {
      features.put(project, CompiledSubprojects.importProject(project));
    }
    return features;
  }
}
