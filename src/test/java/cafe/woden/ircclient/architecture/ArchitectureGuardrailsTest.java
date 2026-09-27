package cafe.woden.ircclient.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.originOwnerEqualsTargetOwner;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.bouncer.spi.BouncerBackendDiscoveryHandler;
import cafe.woden.ircclient.bouncer.spi.BouncerNetworkMappingStrategy;
import cafe.woden.ircclient.irc.pircbotx.client.*;
import cafe.woden.ircclient.irc.pircbotx.client.PircbotxIrcClientService;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

@AnalyzeClasses(
    packages = "cafe.woden.ircclient",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class ArchitectureGuardrailsTest {

  private static final DescribedPredicate<JavaClass> IGNORE_INTERNAL_CLASSES =
      new DescribedPredicate<>("ignore internal classes (outside ignore::api)") {
        @Override
        public boolean test(JavaClass input) {
          String pkg = input.getPackageName();
          if (!pkg.startsWith("cafe.woden.ircclient.ignore")) return false;
          return !pkg.startsWith("cafe.woden.ircclient.ignore.api");
        }
      };

  private static final DescribedPredicate<JavaClass> STATE_INTERNAL_CLASSES =
      new DescribedPredicate<>("state internal classes (outside state::api)") {
        @Override
        public boolean test(JavaClass input) {
          String pkg = input.getPackageName();
          if (!pkg.startsWith("cafe.woden.ircclient.state")) return false;
          return !pkg.startsWith("cafe.woden.ircclient.state.api");
        }
      };

  private static final DescribedPredicate<JavaClass> RUNTIME_CONFIG_STORE_TYPES =
      new DescribedPredicate<>("RuntimeConfigStore types") {
        @Override
        public boolean test(JavaClass input) {
          String name = input.getName();
          return name.equals("cafe.woden.ircclient.config.RuntimeConfigStore")
              || name.startsWith("cafe.woden.ircclient.config.RuntimeConfigStore$");
        }
      };

  private static final DescribedPredicate<JavaClass> BOUNCER_INTERNAL_TYPES =
      new DescribedPredicate<>("bouncer internal types") {
        @Override
        public boolean test(JavaClass input) {
          String name = input.getName();
          return name.equals("cafe.woden.ircclient.bouncer.AbstractBouncerAutoConnectStore")
              || name.startsWith("cafe.woden.ircclient.bouncer.AbstractBouncerAutoConnectStore$")
              || name.equals("cafe.woden.ircclient.bouncer.BouncerNetworkDiscoveryOrchestrator")
              || name.startsWith(
                  "cafe.woden.ircclient.bouncer.BouncerNetworkDiscoveryOrchestrator$");
        }
      };

  private static final DescribedPredicate<JavaClass> IRC_PROTOCOL_PARSER_TYPES =
      new DescribedPredicate<>("irc protocol parser types") {
        @Override
        public boolean test(JavaClass input) {
          String name = input.getName();
          return name.equals("cafe.woden.ircclient.irc.ircv3.Ircv3ZncDetector")
              || name.startsWith("cafe.woden.ircclient.irc.ircv3.Ircv3ZncDetector$");
        }
      };

  private static final DescribedPredicate<JavaMethodCall> APPLICATION_CLASSPATH_METHOD_CALL =
      new DescribedPredicate<>("applicationClasspath runtime bootstrap") {
        @Override
        public boolean test(JavaMethodCall input) {
          return input.getTarget().getName().equals("applicationClasspath");
        }
      };

  private static final Set<String> EXTRACTED_SPRING_BEAN_TYPE_NAMES =
      Set.of(
          "cafe.woden.ircclient.app.outbound.channel.OutboundTargetMembershipCommandSupport",
          "cafe.woden.ircclient.app.outbound.channel.PartCommandSupport",
          "cafe.woden.ircclient.app.outbound.channel.OutboundModeCommandService",
          "cafe.woden.ircclient.app.outbound.channel.OutboundNamesWhoListCommandService",
          "cafe.woden.ircclient.app.outbound.channel.OutboundJoinPartCommandService",
          "cafe.woden.ircclient.app.outbound.channel.OutboundTopicKickCommandService",
          "cafe.woden.ircclient.app.outbound.ignore.IgnoreHardCommandSupport",
          "cafe.woden.ircclient.app.outbound.ignore.IgnoreSoftCommandSupport",
          "cafe.woden.ircclient.app.outbound.ignore.OutboundIgnoreCommandService",
          "cafe.woden.ircclient.app.outbound.monitor.OutboundMonitorCommandSupport",
          "cafe.woden.ircclient.app.outbound.monitor.OutboundMonitorCommandService",
          "cafe.woden.ircclient.app.outbound.identity.NickCommandSupport",
          "cafe.woden.ircclient.app.outbound.identity.AwayCommandSupport",
          "cafe.woden.ircclient.app.outbound.identity.OutboundNickAwayCommandService",
          "cafe.woden.ircclient.app.outbound.dcc.DccRuntimeRegistry",
          "cafe.woden.ircclient.app.outbound.dcc.DccCommandSupport",
          "cafe.woden.ircclient.app.outbound.dcc.DccChatSessionSupport",
          "cafe.woden.ircclient.app.outbound.dcc.DccInboundOfferSupport",
          "cafe.woden.ircclient.app.outbound.dcc.DccFileTransferIoSupport",
          "cafe.woden.ircclient.app.outbound.dcc.DccOfferCommandSupport",
          "cafe.woden.ircclient.app.outbound.dcc.OutboundDccCommandService",
          "cafe.woden.ircclient.app.api.Ircv3ReadMarkerFeatureSupport",
          "cafe.woden.ircclient.app.api.Ircv3MultilineFeatureSupport",
          "cafe.woden.ircclient.app.outbound.messaging.OutboundMultilineMessageSupport",
          "cafe.woden.ircclient.app.api.Ircv3MessageRedactionFeatureSupport",
          "cafe.woden.ircclient.app.outbound.mutation.OutboundMessageMutationCommandService",
          "cafe.woden.ircclient.app.outbound.invite.PendingInviteCommandSupport",
          "cafe.woden.ircclient.app.outbound.invite.OutboundInviteCommandService",
          "cafe.woden.ircclient.app.api.Ircv3ChatHistoryFeatureSupport",
          "cafe.woden.ircclient.app.outbound.chathistory.OutboundChatHistoryRequestSupport",
          "cafe.woden.ircclient.app.outbound.chathistory.OutboundChatHistoryCommandService",
          "cafe.woden.ircclient.app.outbound.backend.BackendExtensionCatalog",
          "cafe.woden.ircclient.app.outbound.backend.OutboundBackendFeatureRegistry",
          "cafe.woden.ircclient.app.outbound.backend.BackendNamedOutboundCommandRouter",
          "cafe.woden.ircclient.app.outbound.backend.QuasselOutboundCommandSupport",
          "cafe.woden.ircclient.app.outbound.backend.MessageMutationOutboundCommandsRouter",
          "cafe.woden.ircclient.app.outbound.backend.BackendUploadCommandRegistry",
          "cafe.woden.ircclient.ui.coordinator.IrcMessageActionCapabilityPolicy",
          "cafe.woden.ircclient.ui.servertree.composition.ServerTreeCompositionAssembler",
          "cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaboratorsFactory",
          "cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaboratorsFactory",
          "cafe.woden.ircclient.ui.servertree.composition.ServerTreeViewInteractionCollaboratorsFactory",
          "cafe.woden.ircclient.ui.servertree.composition.ServerTreeLifecycleSettingsCollaboratorsFactory",
          "cafe.woden.ircclient.ui.servertree.composition.ServerTreeTargetLifecycleCoordinatorFactory",
          "cafe.woden.ircclient.ui.servertree.composition.ServerTreeChannelInteractionCollaboratorsFactory",
          "cafe.woden.ircclient.ui.servertree.composition.ServerTreeTreeInteractionBindingsFactory",
          "cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutApplier",
          "cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutCoordinator",
          "cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutVisibilityFacade",
          "cafe.woden.ircclient.ui.servertree.layout.ServerTreeRootSiblingOrderCoordinator",
          "cafe.woden.ircclient.ui.servertree.builder.ServerTreeServerNodeBuilder",
          "cafe.woden.ircclient.ui.servertree.interaction.ServerTreeNodeActionsFactory",
          "cafe.woden.ircclient.ui.servertree.ServerTreeEdtExecutor",
          "cafe.woden.ircclient.ui.servertree.ServerTreeExternalStreamBinder",
          "cafe.woden.ircclient.ui.servertree.view.ServerTreeServerNodeMenuBuilder",
          "cafe.woden.ircclient.ui.servertree.view.ServerTreeTargetNodeMenuBuilder",
          "cafe.woden.ircclient.ui.servertree.view.ServerTreeQuasselNetworkNodeMenuBuilder",
          "cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder",
          "cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver",
          "cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipTextPolicy",
          "cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLabelPolicy",
          "cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy",
          "cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionFallbackPolicy",
          "cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionPersistencePolicy",
          "cafe.woden.ircclient.ui.servertree.policy.ServerTreeStartupSelectionRestorer",
          "cafe.woden.ircclient.ui.servertree.policy.ServerTreeTargetNodePolicy",
          "cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions",
          "cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier",
          "cafe.woden.ircclient.ui.servertree.query.ServerTreeChannelQueryService",
          "cafe.woden.ircclient.ui.servertree.query.ServerTreeTargetSnapshotProvider",
          "cafe.woden.ircclient.ui.servertree.query.ServerTreeServerNodeResolver",
          "cafe.woden.ircclient.ui.servertree.resolver.ServerTreeServerParentResolver",
          "cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelTargetOperations",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeRequestApi",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreePrivateMessageOnlineStateCoordinator",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerCatalogSynchronizer",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiLeafVisibilitySynchronizer",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeApplicationRootVisibilityCoordinator",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeRuntimeHeaderApi",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerLeafVisibilityCoordinator",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiRefreshCoordinator",
          "cafe.woden.ircclient.ui.servertree.mutation.ServerTreeChannelListNodeEnsurer",
          "cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeStatusLabelManager",
          "cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator",
          "cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator",
          "cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilitySettings",
          "cafe.woden.ircclient.ui.servertree.state.ServerTreeExpansionStateManager",
          "cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater",
          "cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater",
          "cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner",
          "cafe.woden.ircclient.ui.servertree.view.ServerTreeCellPresentationPolicy",
          "cafe.woden.ircclient.ui.servertree.view.ServerTreeNetworkInfoDialogBuilder");

  private static final DescribedPredicate<JavaClass> EXTRACTED_SPRING_BEAN_TYPES =
      new DescribedPredicate<>("extracted Spring bean types") {
        @Override
        public boolean test(JavaClass input) {
          return EXTRACTED_SPRING_BEAN_TYPE_NAMES.contains(input.getName());
        }
      };

  @ArchTest
  static final ArchRule only_config_should_depend_on_runtime_config_store =
      noClasses()
          .that()
          .resideOutsideOfPackage("cafe.woden.ircclient.config..")
          .should()
          .dependOnClassesThat(RUNTIME_CONFIG_STORE_TYPES)
          .because("all consumers must use config ports instead of the persistence implementation");

  @ArchTest
  static final ArchRule app_should_not_depend_on_ui_package_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.ui..")
          .because(
              "application code should use app-level ports and abstractions, not concrete Swing/UI types");

  @ArchTest
  static final ArchRule app_should_not_depend_on_logging_package_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.logging..")
          .because("application code should use app-owned ports, not logging module internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_interceptors_module_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.interceptors..")
          .because(
              "application code should depend on app::api interceptor ports, not interceptor module internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_notifications_module_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.notifications..")
          .because(
              "application code should depend on app::api notification ports, not notification module internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_ignore_module_internals_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat(IGNORE_INTERNAL_CLASSES)
          .because("application code should only depend on ignore::api, not ignore internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_state_module_internals_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat(STATE_INTERNAL_CLASSES)
          .because("application code should only depend on state::api, not state internals");

  @ArchTest
  static final ArchRule non_ui_modules_should_not_depend_on_ignore_module_internals =
      noClasses()
          .that()
          .resideOutsideOfPackages("cafe.woden.ircclient.ignore..", "cafe.woden.ircclient.ui..")
          .should()
          .dependOnClassesThat(IGNORE_INTERNAL_CLASSES)
          .because(
              "non-UI modules should depend only on ignore::api and remain decoupled from ignore internals");

  @ArchTest
  static final ArchRule ignore_api_should_remain_dependency_light =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ignore.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.state..",
              "cafe.woden.ircclient.irc..",
              "cafe.woden.ircclient.logging..")
          .because("ignore::api should stay stable and free from app, UI, and transport internals");

  @ArchTest
  static final ArchRule app_should_not_depend_on_diagnostics_module_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.diagnostics..")
          .because(
              "application code should not couple directly to diagnostics internals and should use app-level seams");

  @ArchTest
  static final ArchRule logging_should_not_depend_on_ui_package_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.logging..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.ui..")
          .because("logging should stay UI-agnostic and communicate through app/logging ports");

  @ArchTest
  static final ArchRule logging_should_not_depend_on_app_internal_or_feature_modules =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.logging..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.state..",
              "cafe.woden.ircclient.app.util..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..",
              "cafe.woden.ircclient.perform..",
              "cafe.woden.ircclient.diagnostics..")
          .because(
              "logging module should expose adapters through app::api and avoid coupling to app internals or peer feature modules");

  @ArchTest
  static final ArchRule refactored_irc_ports_should_not_depend_on_broad_irc_client =
      noClasses()
          .that()
          .haveNameMatching(
              "cafe\\.woden\\.ircclient\\.irc\\.port\\.Irc(ConnectionLifecycle|CurrentNick|LagProbe|Shutdown|EchoCapability|ReadMarker|NegotiatedFeature)Port(\\$.*)?")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("cafe.woden.ircclient.irc.IrcClientService")
          .because("client adaptation belongs in the IRC adapters, not in narrow port contracts");

  @ArchTest
  static final ArchRule app_should_not_depend_on_pircbotx_service_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .areAssignableTo(PircbotxIrcClientService.class)
          .because(
              "application code should depend on IrcClientService, not transport-specific adapters");

  @ArchTest
  static final ArchRule non_irc_modules_should_not_depend_on_matrix_transport_internals =
      noClasses()
          .that()
          .resideOutsideOfPackage("cafe.woden.ircclient.irc..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.irc.matrix..")
          .because(
              "matrix transport internals should stay behind the irc module boundary and be accessed via irc ports");

  @ArchTest
  static final ArchRule non_app_modules_should_not_depend_on_app_core_directly =
      noClasses()
          .that()
          .resideOutsideOfPackage("cafe.woden.ircclient.app..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.app.core..")
          .because("app.core is internal orchestration and should only be used from within app.");

  @ArchTest
  static final ArchRule perform_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.perform..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..")
          .because(
              "perform module should integrate through app api/commands ports and remain decoupled from UI and app internals");

  @ArchTest
  static final ArchRule monitor_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.monitor..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..")
          .because(
              "monitor module should integrate via app::api plus config/irc without coupling to UI or app internals");

  @ArchTest
  static final ArchRule interceptors_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.interceptors..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..")
          .because(
              "interceptor module should integrate via app::api and remain decoupled from app internals and UI/logging");

  @ArchTest
  static final ArchRule notifications_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.notifications..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.interceptors..")
          .because(
              "notification module should integrate via app::api and remain decoupled from UI and app internals");

  @ArchTest
  static final ArchRule state_should_not_depend_on_ui_logging_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.state..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..")
          .because(
              "state module should remain a reusable correlation/state holder and integrate through app::api plus config");

  @ArchTest
  static final ArchRule state_api_should_not_depend_on_state_implementations =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.state.api..")
          .should()
          .dependOnClassesThat(STATE_INTERNAL_CLASSES)
          .because(
              "state::api should stay implementation-agnostic and independent from state internals");

  @ArchTest
  static final ArchRule only_quassel_outbound_service_should_depend_on_quassel_core_control_port =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app.outbound..")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.QuasselOutboundCommandService")
          .should()
          .dependOnClassesThat()
          .areAssignableTo(QuasselCoreControlPort.class)
          .because(
              "backend-specific Quassel transport control should stay isolated in QuasselOutboundCommandService");

  @ArchTest
  static final ArchRule only_matrix_upload_services_should_depend_on_upload_translation_handlers =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app.outbound..")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.MatrixOutboundCommandService")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.BackendUploadCommandRegistry")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.BackendExtensionCatalog")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.BackendExtensionCatalogState")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.MatrixBackendExtension")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.backend.MatrixUploadCommandTranslationHandler")
          .and()
          .doNotHaveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(
              "cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler")
          .because(
              "semantic /upload backend translation should stay behind dedicated matrix upload services");

  @ArchTest
  static final ArchRule
      only_matrix_upload_services_should_depend_on_matrix_outbound_command_support =
          noClasses()
              .that()
              .resideInAPackage("cafe.woden.ircclient.app.outbound..")
              .and()
              .doNotHaveFullyQualifiedName(
                  "cafe.woden.ircclient.app.outbound.backend.MatrixOutboundCommandSupport")
              .and()
              .doNotHaveFullyQualifiedName(
                  "cafe.woden.ircclient.app.outbound.backend.MatrixOutboundCommandService")
              .and()
              .doNotHaveFullyQualifiedName(
                  "cafe.woden.ircclient.app.outbound.backend.MatrixBackendExtension")
              .and()
              .doNotHaveFullyQualifiedName(
                  "cafe.woden.ircclient.app.outbound.backend.MatrixUploadCommandTranslationHandler")
              .should()
              .dependOnClassesThat()
              .haveFullyQualifiedName(
                  "cafe.woden.ircclient.app.outbound.backend.MatrixOutboundCommandSupport")
              .because(
                  "matrix upload payload shaping should remain isolated to dedicated matrix outbound services");

  @ArchTest
  static final ArchRule ui_should_only_access_backend_mode_port_through_ui_backend_profile_types =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui..")
          .and()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.ui.backend.BackendUiProfile")
          .and()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.ui.backend.BackendUiContext")
          .and()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.ui.backend.BackendUiProfileProvider")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("cafe.woden.ircclient.irc.backend.IrcBackendModePort")
          .because(
              "backend mode checks in UI should stay centralized behind backend-ui profile/context services");

  @ArchTest
  static final ArchRule ui_ignore_should_not_depend_on_app_internal_or_irc_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.ignore..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.state..",
              "cafe.woden.ircclient.irc..")
          .because(
              "ignore UI components should stay presentation-focused and avoid coupling to app internals or IRC transport details");

  @ArchTest
  static final ArchRule dcc_should_not_depend_on_ui_or_app_internal_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.dcc..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.app..",
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..",
              "cafe.woden.ircclient.perform..")
          .because(
              "dcc transfer state should remain shared domain/application state and avoid app-internal or UI coupling");

  @ArchTest
  static final ArchRule diagnostics_should_not_depend_on_app_ui_or_logging_packages =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.diagnostics..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "cafe.woden.ircclient.app",
              "cafe.woden.ircclient.app.commands..",
              "cafe.woden.ircclient.app.core..",
              "cafe.woden.ircclient.app.outbound..",
              "cafe.woden.ircclient.state..",
              "cafe.woden.ircclient.app.util..",
              "cafe.woden.ircclient.dcc..",
              "cafe.woden.ircclient.monitor..",
              "cafe.woden.ircclient.notifications..",
              "cafe.woden.ircclient.interceptors..",
              "cafe.woden.ircclient.perform..",
              "cafe.woden.ircclient.ui..",
              "cafe.woden.ircclient.logging..")
          .because(
              "diagnostics support should stay independent from app internals while integrating only via app::api plus config/model/util/notify seams");

  @ArchTest
  static final ArchRule bouncer_should_not_depend_on_irc_package_directly =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.bouncer..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.irc..")
          .because(
              "bouncer support should remain transport-agnostic and use BouncerConnectionPort for connect operations");

  @ArchTest
  static final ArchRule only_irc_module_should_implement_bouncer_connection_port =
      noClasses()
          .that()
          .resideOutsideOfPackage("cafe.woden.ircclient.irc..")
          .should()
          .implement(BouncerConnectionPort.class)
          .because(
              "BouncerConnectionPort adapters should stay in the IRC transport module to keep infrastructure ownership explicit");

  @ArchTest
  static final ArchRule only_bouncer_module_should_implement_bouncer_discovery_event_port =
      noClasses()
          .that()
          .resideOutsideOfPackage("cafe.woden.ircclient.bouncer..")
          .should()
          .implement(BouncerDiscoveryEventPort.class)
          .because(
              "BouncerDiscoveryEventPort dispatch should stay in bouncer module so backend routing is centralized");

  @ArchTest
  static final ArchRule only_bouncer_or_irc_modules_should_implement_bouncer_backend_handler =
      noClasses()
          .that()
          .resideOutsideOfPackages("cafe.woden.ircclient.bouncer..", "cafe.woden.ircclient.irc..")
          .should()
          .implement(BouncerBackendDiscoveryHandler.class)
          .because(
              "backend discovery handlers should live in bouncer or irc modules, not app/ui/features");

  @ArchTest
  static final ArchRule only_bouncer_or_irc_modules_should_implement_mapping_strategy =
      noClasses()
          .that()
          .resideOutsideOfPackages("cafe.woden.ircclient.bouncer..", "cafe.woden.ircclient.irc..")
          .should()
          .implement(BouncerNetworkMappingStrategy.class)
          .because(
              "bouncer network mapping strategies belong to bouncer core or irc backend adapters");

  @ArchTest
  static final ArchRule bouncer_should_not_depend_on_irc_protocol_parsers =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.bouncer..")
          .should()
          .dependOnClassesThat(IRC_PROTOCOL_PARSER_TYPES)
          .because(
              "bouncer core should stay parser-agnostic and receive normalized discovery events only");

  @ArchTest
  static final ArchRule only_bouncer_and_backend_adapters_should_depend_on_bouncer_internal_types =
      noClasses()
          .that()
          .resideOutsideOfPackages(
              "cafe.woden.ircclient.bouncer..",
              "cafe.woden.ircclient.irc.soju..",
              "cafe.woden.ircclient.irc.znc..")
          .should()
          .dependOnClassesThat(BOUNCER_INTERNAL_TYPES)
          .because(
              "bouncer internals should remain implementation details used only by bouncer core and backend-specific IRC adapters");

  @ArchTest
  static final ArchRule miglayout_construction_should_stay_in_ui_layout_helpers =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient..")
          .and()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.ui.util.MigLayouts")
          .and()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.ui.settings.PreferencesUiSupport")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("net.miginfocom.swing.MigLayout")
          .because(
              "MigLayout construction should stay centralized in MigLayouts, with PreferencesUiSupport retaining only legacy compatibility factories");

  @ArchTest
  static final ArchRule mig_component_constraints_should_stay_in_ui_constraint_helpers =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient..")
          .and()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.ui.util.MigConstraints")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("net.miginfocom.layout.CC")
          .because("Mig component constraints should be expressed through MigConstraints helpers");

  @ArchTest
  static final ArchRule only_virtual_threads_factory_should_depend_on_executors =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.util.VirtualThreads")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.concurrent.Executors")
          .because(
              "raw Executors factories should stay centralized in VirtualThreads so executor ownership, naming, and shutdown policies remain consistent");

  @ArchTest
  static final ArchRule only_virtual_threads_factory_should_call_thread_of_virtual =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName("cafe.woden.ircclient.util.VirtualThreads")
          .should()
          .callMethod(Thread.class, "ofVirtual")
          .because(
              "virtual-thread creation should stay centralized in VirtualThreads to keep thread naming and policy consistent");

  @ArchTest
  static final ArchRule app_should_not_construct_threads_directly =
      noClasses()
          .should()
          .callConstructorWhere(target(owner(assignableTo(Thread.class))))
          .because(
              "direct new Thread(...) creation should be avoided in favor of VirtualThreads helpers");

  @ArchTest
  static final ArchRule production_code_should_not_manually_construct_extracted_spring_beans =
      noClasses()
          .should()
          .callConstructorWhere(
              target(owner(EXTRACTED_SPRING_BEAN_TYPES)).and(not(originOwnerEqualsTargetOwner())))
          .because(
              "standalone logic bundles promoted to Spring beans should be injected, not manually instantiated in production code");

  @ArchTest
  static final ArchRule app_should_not_use_rxjava_default_io_scheduler =
      noClasses()
          .should()
          .callMethod(io.reactivex.rxjava3.schedulers.Schedulers.class, "io")
          .because(
              "RxJava Schedulers.io() uses the default platform-thread pool; use RxVirtualSchedulers.io()");

  @ArchTest
  static final ArchRule app_should_not_use_completable_future_common_pool_overloads =
      noClasses()
          .should()
          .callMethod(
              java.util.concurrent.CompletableFuture.class,
              "supplyAsync",
              java.util.function.Supplier.class)
          .orShould()
          .callMethod(java.util.concurrent.CompletableFuture.class, "runAsync", Runnable.class)
          .because(
              "CompletableFuture common-pool overloads bypass virtual-thread executors; pass an explicit VirtualThreads-backed executor");

  @ArchTest
  static final ArchRule
      non_ui_packages_should_not_depend_on_ui_input_servertree_or_coordinator_subpackages =
          noClasses()
              .that()
              .resideOutsideOfPackage("cafe.woden.ircclient.ui..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(
                  "cafe.woden.ircclient.ui.input..",
                  "cafe.woden.ircclient.ui.servertree..",
                  "cafe.woden.ircclient.ui.coordinator..",
                  "cafe.woden.ircclient.ui.bus..",
                  "cafe.woden.ircclient.ui.controls..")
              .because(
                  "ui internals (input, server-tree, coordinator, bus, controls) should remain behind the top-level ui adapter boundary");

  @ArchTest
  static final ArchRule ui_input_should_not_depend_on_servertree_subpackage =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.input..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.ui.servertree..")
          .because(
              "message-input internals and server-tree internals should stay decoupled and coordinate via higher-level UI services");

  @ArchTest
  static final ArchRule ui_servertree_should_not_depend_on_input_subpackage =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.ui.servertree..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("cafe.woden.ircclient.ui.input..")
          .because(
              "server-tree internals should not depend on message-input internals; interactions belong in UI coordinators");

  @ArchTest
  static final ArchRule application_services_should_not_open_ircv3_application_classpath_catalogs =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.app..")
          .should()
          .callMethodWhere(APPLICATION_CLASSPATH_METHOD_CALL)
          .because(
              "application services should receive IRCv3 runtime catalogs from the composition root");

  @ArchTest
  static final ArchRule pircbotx_transport_should_not_open_application_classpath_runtime_catalogs =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.pircbotx..")
          .should()
          .callMethodWhere(APPLICATION_CLASSPATH_METHOD_CALL)
          .because(
              "PircBotX production composition should receive IRCv3 runtime catalogs explicitly");

  @ArchTest
  static final ArchRule quassel_transport_should_not_open_application_classpath_runtime_catalogs =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.quassel..")
          .should()
          .callMethodWhere(APPLICATION_CLASSPATH_METHOD_CALL)
          .because(
              "Quassel production composition should receive IRCv3 runtime catalogs explicitly");

  @ArchTest
  static final ArchRule matrix_transport_should_not_open_application_classpath_runtime_catalogs =
      noClasses()
          .that()
          .resideInAPackage("cafe.woden.ircclient.irc.matrix..")
          .should()
          .callMethodWhere(APPLICATION_CLASSPATH_METHOD_CALL)
          .because(
              "Matrix production composition should receive IRCv3 runtime catalogs explicitly");
}
