package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ExecutorConfigIntegrationTest {

  @Test
  void closingContextShutsDownExecutorBeans() {
    ExecutorService uiLogViewerExec = null;
    ScheduledExecutorService jfrSamplerExec = null;
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    try {
      ctx.register(ExecutorConfig.class);
      ctx.refresh();
      uiLogViewerExec = ctx.getBean(ExecutorConfig.UI_LOG_VIEWER_EXECUTOR, ExecutorService.class);
      jfrSamplerExec =
          ctx.getBean(
              ExecutorConfig.JFR_RUNTIME_EVENTS_SAMPLER_SCHEDULER, ScheduledExecutorService.class);
      assertFalse(uiLogViewerExec.isShutdown());
      assertFalse(jfrSamplerExec.isShutdown());
    } finally {
      ctx.close();
    }

    assertNotNull(uiLogViewerExec);
    assertNotNull(jfrSamplerExec);
    assertTrue(uiLogViewerExec.isShutdown());
    assertTrue(jfrSamplerExec.isShutdown());
  }
}
