package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerBackendRegistry;
import cafe.woden.ircclient.bouncer.BouncerDiscoveryEventPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.adapter.BouncerIrcConnectionPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcChatHistoryPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcConnectionLifecyclePortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcCurrentNickPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcIdentityPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcMediatorInteractionPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcMessagingPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcMonitorPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcReadMarkerPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcShutdownPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcTargetMembershipPortAdapter;
import cafe.woden.ircclient.irc.adapter.IrcTypingPortAdapter;
import cafe.woden.ircclient.irc.backend.BackendRoutingIrcClientService;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import cafe.woden.ircclient.irc.port.IrcChatHistoryPort;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.port.IrcCurrentNickPort;
import cafe.woden.ircclient.irc.port.IrcIdentityPort;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcMessagingPort;
import cafe.woden.ircclient.irc.port.IrcMonitorPort;
import cafe.woden.ircclient.irc.port.IrcReadMarkerPort;
import cafe.woden.ircclient.irc.port.IrcShutdownPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.irc.port.IrcTypingPort;
import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@TestPropertySource(
    properties = {
      "spring.main.headless=true",
      "ircafe.ui.autoConnectOnStart=false",
      "ircafe.ui.tray.enabled=false",
      "ircafe.runtime-config=build/tmp/modulith-tests/${random.uuid}/ircafe.yml"
    })
class IrcModuleIntegrationTest {

  @MockitoBean ServerProxyResolver serverProxyResolver;

  @TestBean(name = "pircbotxIrcClientService")
  IrcBackendRuntimeClientService pircbotxIrcClientService;

  @TestBean(name = "quasselCoreIrcClientService")
  IrcBackendRuntimeClientService quasselCoreIrcClientService;

  @TestBean(name = "matrixIrcClientService")
  IrcBackendRuntimeClientService matrixIrcClientService;

  @TestBean BouncerBackendRegistry bouncerBackendRegistry;

  @TestBean BouncerDiscoveryEventPort bouncerDiscoveryEventPort;

  @TestBean ServerIsupportStatePort serverIsupportStatePort;

  private final ApplicationContext applicationContext;
  private final BackendRoutingIrcClientService backendRoutingIrcClientService;
  private final IrcClientService ircClientService;
  private final IrcConnectionLifecyclePort ircConnectionLifecyclePort;
  private final IrcCurrentNickPort ircCurrentNickPort;
  private final IrcTargetMembershipPort ircTargetMembershipPort;
  private final IrcMediatorInteractionPort ircMediatorInteractionPort;
  private final IrcReadMarkerPort ircReadMarkerPort;
  private final IrcTypingPort ircTypingPort;
  private final IrcShutdownPort ircShutdownPort;
  private final BouncerIrcConnectionPortAdapter bouncerIrcConnectionPortAdapter;

  IrcModuleIntegrationTest(
      ApplicationContext applicationContext,
      BackendRoutingIrcClientService backendRoutingIrcClientService,
      @Qualifier("ircClientService") IrcClientService ircClientService,
      @Qualifier("ircConnectionLifecyclePort")
          IrcConnectionLifecyclePort ircConnectionLifecyclePort,
      @Qualifier("ircCurrentNickPort") IrcCurrentNickPort ircCurrentNickPort,
      @Qualifier("ircTargetMembershipPort") IrcTargetMembershipPort ircTargetMembershipPort,
      @Qualifier("ircMediatorInteractionPort")
          IrcMediatorInteractionPort ircMediatorInteractionPort,
      IrcReadMarkerPort ircReadMarkerPort,
      IrcTypingPort ircTypingPort,
      @Qualifier("ircShutdownPort") IrcShutdownPort ircShutdownPort,
      BouncerIrcConnectionPortAdapter bouncerIrcConnectionPortAdapter) {
    this.applicationContext = applicationContext;
    this.backendRoutingIrcClientService = backendRoutingIrcClientService;
    this.ircClientService = ircClientService;
    this.ircConnectionLifecyclePort = ircConnectionLifecyclePort;
    this.ircCurrentNickPort = ircCurrentNickPort;
    this.ircTargetMembershipPort = ircTargetMembershipPort;
    this.ircMediatorInteractionPort = ircMediatorInteractionPort;
    this.ircReadMarkerPort = ircReadMarkerPort;
    this.ircTypingPort = ircTypingPort;
    this.ircShutdownPort = ircShutdownPort;
    this.bouncerIrcConnectionPortAdapter = bouncerIrcConnectionPortAdapter;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @SuppressWarnings("unused")
  static IrcBackendRuntimeClientService pircbotxIrcClientService() {
    return backendStub(IrcProperties.Server.Backend.IRC);
  }

  @SuppressWarnings("unused")
  static IrcBackendRuntimeClientService quasselCoreIrcClientService() {
    return backendStub(IrcProperties.Server.Backend.QUASSEL_CORE);
  }

  @SuppressWarnings("unused")
  static IrcBackendRuntimeClientService matrixIrcClientService() {
    return backendStub(IrcProperties.Server.Backend.MATRIX);
  }

  @SuppressWarnings("unused")
  static BouncerBackendRegistry bouncerBackendRegistry() {
    return new BouncerBackendRegistry(java.util.List.of());
  }

  @SuppressWarnings("unused")
  static BouncerDiscoveryEventPort bouncerDiscoveryEventPort() {
    return BouncerDiscoveryEventPort.noOp();
  }

  @SuppressWarnings("unused")
  static ServerIsupportStatePort serverIsupportStatePort() {
    return new ServerIsupportStatePort() {
      @Override
      public void applyIsupportToken(String serverId, String tokenName, String tokenValue) {}

      @Override
      public ModeVocabulary vocabularyForServer(String serverId) {
        return ModeVocabulary.fallback();
      }

      @Override
      public void clearServer(String serverId) {}
    };
  }

  private static IrcBackendRuntimeClientService backendStub(
      IrcProperties.Server.Backend backendType) {
    IrcBackendRuntimeClientService backend =
        mock(
            IrcBackendRuntimeClientService.class,
            backendType.name().toLowerCase() + "-backend-stub");
    when(backend.backendId()).thenReturn(backendIdFor(backendType));
    when(backend.events()).thenReturn(Flowable.empty());
    when(backend.quasselCoreNetworkEvents()).thenReturn(Flowable.empty());
    when(backend.connect(anyString())).thenReturn(Completable.complete());
    when(backend.disconnect(anyString())).thenReturn(Completable.complete());
    when(backend.disconnect(anyString(), anyString())).thenReturn(Completable.complete());
    when(backend.currentNick(anyString())).thenReturn(Optional.empty());
    return backend;
  }

  private static String backendIdFor(IrcProperties.Server.Backend backendType) {
    return switch (backendType) {
      case IRC -> "irc";
      case MATRIX -> "matrix";
      case QUASSEL_CORE -> "quassel-core";
    };
  }

  @Test
  void exposesIrcModuleBeansAndPorts() {
    assertEquals(1, applicationContext.getBeansOfType(IrcMonitorPort.class).size());
    assertEquals(
        IrcMonitorPortAdapter.class,
        AopUtils.getTargetClass(
            applicationContext.getBean("ircMonitorPort", IrcMonitorPort.class)));
    assertEquals(1, applicationContext.getBeansOfType(IrcChatHistoryPort.class).size());
    assertEquals(
        IrcChatHistoryPortAdapter.class,
        AopUtils.getTargetClass(
            applicationContext.getBean("ircChatHistoryPort", IrcChatHistoryPort.class)));
    assertEquals(1, applicationContext.getBeansOfType(IrcMessagingPort.class).size());
    assertEquals(
        IrcMessagingPortAdapter.class,
        AopUtils.getTargetClass(
            applicationContext.getBean("ircMessagingPort", IrcMessagingPort.class)));
    assertEquals(1, applicationContext.getBeansOfType(IrcIdentityPort.class).size());
    assertEquals(
        IrcIdentityPortAdapter.class,
        AopUtils.getTargetClass(
            applicationContext.getBean("ircIdentityPort", IrcIdentityPort.class)));
    assertEquals(1, applicationContext.getBeansOfType(BackendRoutingIrcClientService.class).size());
    assertEquals(3, applicationContext.getBeansOfType(IrcBackendRuntimeClientService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IrcConnectionLifecyclePort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IrcCurrentNickPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IrcTargetMembershipPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IrcMediatorInteractionPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IrcReadMarkerPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IrcTypingPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IrcShutdownPort.class).size());
    assertEquals(
        1, applicationContext.getBeansOfType(BouncerIrcConnectionPortAdapter.class).size());

    assertNotNull(backendRoutingIrcClientService);
    assertSame(backendRoutingIrcClientService, ircClientService);
    assertNotNull(ircConnectionLifecyclePort);
    assertNotNull(ircCurrentNickPort);
    assertNotNull(ircTargetMembershipPort);
    assertNotNull(ircMediatorInteractionPort);
    assertNotNull(ircReadMarkerPort);
    assertNotNull(ircTypingPort);
    assertNotNull(ircShutdownPort);
    assertNotNull(bouncerIrcConnectionPortAdapter);

    assertEquals(
        IrcConnectionLifecyclePortAdapter.class,
        AopUtils.getTargetClass(ircConnectionLifecyclePort));
    assertEquals(IrcCurrentNickPortAdapter.class, AopUtils.getTargetClass(ircCurrentNickPort));
    assertEquals(
        IrcTargetMembershipPortAdapter.class, AopUtils.getTargetClass(ircTargetMembershipPort));
    assertEquals(
        IrcMediatorInteractionPortAdapter.class,
        AopUtils.getTargetClass(ircMediatorInteractionPort));
    assertEquals(IrcReadMarkerPortAdapter.class, AopUtils.getTargetClass(ircReadMarkerPort));
    assertEquals(IrcTypingPortAdapter.class, AopUtils.getTargetClass(ircTypingPort));
    assertEquals(IrcShutdownPortAdapter.class, AopUtils.getTargetClass(ircShutdownPort));
  }

  @Test
  void backendRoutingExposesMergedEmptyEventStreamsFromStubBackends() {
    ircClientService.events().test().assertNoValues().assertComplete();
    backendRoutingIrcClientService
        .quasselCoreNetworkEvents()
        .test()
        .assertNoValues()
        .assertComplete();
  }
}
