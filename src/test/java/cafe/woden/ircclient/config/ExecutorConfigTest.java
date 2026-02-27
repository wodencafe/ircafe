package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ExecutorConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(ExecutorConfig.class);

  @Test
  void exposesExpectedExecutorBeans() {
    runner.run(
        ctx -> {
          assertInstanceOf(
              ScheduledExecutorService.class,
              ctx.getBean(ExecutorConfig.JFR_RUNTIME_EVENTS_SAMPLER_SCHEDULER));
          assertInstanceOf(
              ExecutorService.class, ctx.getBean(ExecutorConfig.INTERCEPTOR_STORE_INGEST_EXECUTOR));
          assertInstanceOf(
              ExecutorService.class, ctx.getBean(ExecutorConfig.INTERCEPTOR_STORE_PERSIST_EXECUTOR));
          assertInstanceOf(ExecutorService.class, ctx.getBean(ExecutorConfig.UI_LOG_VIEWER_EXECUTOR));
          assertInstanceOf(
              ExecutorService.class, ctx.getBean(ExecutorConfig.UI_INTERCEPTOR_REFRESH_EXECUTOR));
          assertInstanceOf(
              ExecutorService.class, ctx.getBean(ExecutorConfig.PREFERENCES_PUSHY_TEST_EXECUTOR));
          assertInstanceOf(
              ExecutorService.class,
              ctx.getBean(ExecutorConfig.PREFERENCES_NOTIFICATION_RULE_TEST_EXECUTOR));
        });
  }

  @Test
  void createsDistinctExecutorsForIndependentWorkloads() {
    runner.run(
        ctx -> {
          ExecutorService logViewer = ctx.getBean(ExecutorConfig.UI_LOG_VIEWER_EXECUTOR, ExecutorService.class);
          ExecutorService interceptorRefresh =
              ctx.getBean(ExecutorConfig.UI_INTERCEPTOR_REFRESH_EXECUTOR, ExecutorService.class);
          ExecutorService pushyTest =
              ctx.getBean(ExecutorConfig.PREFERENCES_PUSHY_TEST_EXECUTOR, ExecutorService.class);
          assertNotSame(logViewer, interceptorRefresh);
          assertNotSame(logViewer, pushyTest);
          assertNotSame(interceptorRefresh, pushyTest);
        });
  }

  @Test
  void contextCloseShutsDownExecutorsViaDestroyMethod() {
    AtomicReference<ExecutorService> logViewerRef = new AtomicReference<>();
    AtomicReference<ScheduledExecutorService> jfrSamplerRef = new AtomicReference<>();

    runner.run(
        ctx -> {
          logViewerRef.set(ctx.getBean(ExecutorConfig.UI_LOG_VIEWER_EXECUTOR, ExecutorService.class));
          jfrSamplerRef.set(
              ctx.getBean(
                  ExecutorConfig.JFR_RUNTIME_EVENTS_SAMPLER_SCHEDULER,
                  ScheduledExecutorService.class));
        });

    assertTrue(logViewerRef.get().isShutdown());
    assertTrue(jfrSamplerRef.get().isShutdown());
  }
}

