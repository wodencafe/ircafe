package cafe.woden.ircclient.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.UserListStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
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
class IgnoreModuleIntegrationTest {

  @MockitoBean UserListStore userListStore;

  private final ApplicationContext applicationContext;
  private final IgnoreListService ignoreListService;
  private final IgnoreStatusService ignoreStatusService;
  private final InboundIgnorePolicy inboundIgnorePolicy;
  private final InboundIgnorePolicyPort inboundIgnorePolicyPort;
  private final IgnoreListQueryPort ignoreListQueryPort;
  private final IgnoreListCommandPort ignoreListCommandPort;

  IgnoreModuleIntegrationTest(
      ApplicationContext applicationContext,
      IgnoreListService ignoreListService,
      IgnoreStatusService ignoreStatusService,
      InboundIgnorePolicy inboundIgnorePolicy,
      InboundIgnorePolicyPort inboundIgnorePolicyPort,
      IgnoreListQueryPort ignoreListQueryPort,
      IgnoreListCommandPort ignoreListCommandPort) {
    this.applicationContext = applicationContext;
    this.ignoreListService = ignoreListService;
    this.ignoreStatusService = ignoreStatusService;
    this.inboundIgnorePolicy = inboundIgnorePolicy;
    this.inboundIgnorePolicyPort = inboundIgnorePolicyPort;
    this.ignoreListQueryPort = ignoreListQueryPort;
    this.ignoreListCommandPort = ignoreListCommandPort;
  }

  @TestBean(name = "run")
  ApplicationRunner run;

  @SuppressWarnings("unused")
  static ApplicationRunner run() {
    return args -> {};
  }

  @Test
  void exposesIgnoreModuleBeansAndPort() {
    assertEquals(1, applicationContext.getBeansOfType(IgnoreListService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IgnoreStatusService.class).size());
    assertEquals(1, applicationContext.getBeansOfType(InboundIgnorePolicy.class).size());
    assertEquals(1, applicationContext.getBeansOfType(InboundIgnorePolicyPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IgnoreListQueryPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IgnoreListCommandPort.class).size());
    assertNotNull(ignoreListService);
    assertNotNull(ignoreStatusService);
    assertNotNull(inboundIgnorePolicy);
    assertNotNull(ignoreListQueryPort);
    assertNotNull(ignoreListCommandPort);
    assertEquals(InboundIgnorePolicy.class, AopUtils.getTargetClass(inboundIgnorePolicyPort));
    assertEquals(IgnoreListService.class, AopUtils.getTargetClass(ignoreListQueryPort));
    assertEquals(IgnoreListService.class, AopUtils.getTargetClass(ignoreListCommandPort));
    assertSame(ignoreListService, ignoreListQueryPort);
    assertSame(ignoreListService, ignoreListCommandPort);
    assertSame(inboundIgnorePolicy, inboundIgnorePolicyPort);
  }

  @Test
  void policyPortAllowsMessageWhenNoIgnoreMasksExist() {
    InboundIgnorePolicyPort.Decision decision =
        inboundIgnorePolicyPort.decide(
            "libera", "alice", "alice!ident@example.org", false, List.of("MSGS", "PUBLIC"));

    assertEquals(InboundIgnorePolicyPort.Decision.ALLOW, decision);
  }
}
