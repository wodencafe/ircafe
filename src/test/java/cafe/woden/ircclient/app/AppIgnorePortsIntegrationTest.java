package cafe.woden.ircclient.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.outbound.OutboundChatCommandService;
import cafe.woden.ircclient.app.outbound.OutboundIgnoreCommandService;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class AppIgnorePortsIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  @MockitoBean ChatHistoryIngestionPort chatHistoryIngestionPort;

  @MockitoBean ChatHistoryIngestEventsPort chatHistoryIngestEventsPort;

  @MockitoBean ChatHistoryBatchEventsPort chatHistoryBatchEventsPort;

  @MockitoBean ZncPlaybackEventsPort zncPlaybackEventsPort;

  @MockitoBean TargetChatHistoryPort targetChatHistoryPort;

  @MockitoBean TargetLogMaintenancePort targetLogMaintenancePort;

  @MockitoBean DccTransferStore dccTransferStore;

  private final ApplicationContext applicationContext;
  private final OutboundIgnoreCommandService outboundIgnoreCommandService;
  private final OutboundChatCommandService outboundChatCommandService;

  AppIgnorePortsIntegrationTest(
      ApplicationContext applicationContext,
      OutboundIgnoreCommandService outboundIgnoreCommandService,
      OutboundChatCommandService outboundChatCommandService) {
    this.applicationContext = applicationContext;
    this.outboundIgnoreCommandService = outboundIgnoreCommandService;
    this.outboundChatCommandService = outboundChatCommandService;
  }

  @Test
  void appModuleExposesIgnoreApiPortsWithoutRequiringIgnoreServiceBean() {
    assertEquals(1, applicationContext.getBeansOfType(IgnoreListQueryPort.class).size());
    assertEquals(1, applicationContext.getBeansOfType(IgnoreListCommandPort.class).size());
    assertTrue(applicationContext.getBeansOfType(IgnoreListService.class).isEmpty());
  }

  @Test
  void ignoreAwareAppBeansAreWiredWithApiPortDependencies() {
    assertNotNull(outboundIgnoreCommandService);
    assertNotNull(outboundChatCommandService);
  }
}
