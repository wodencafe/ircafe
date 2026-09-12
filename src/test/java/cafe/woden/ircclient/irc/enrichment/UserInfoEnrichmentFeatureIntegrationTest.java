package cafe.woden.ircclient.irc.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.execution.ExecutorConfig;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.backend.IrcBackendRuntimeClientService;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class UserInfoEnrichmentFeatureIntegrationTest {

  @Test
  void componentScanWiresFeaturePlannerIntoRootServiceAndDisposesSubscription() {
    PublishProcessor<ServerIrcEvent> events = PublishProcessor.create();
    IrcBackendRuntimeClientService irc = mock(IrcBackendRuntimeClientService.class);
    when(irc.events()).thenReturn(events);
    ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    Instant now = Instant.parse("2026-02-26T00:00:00Z");
    UserInfoEnrichmentPlanner.Settings settings =
        new UserInfoEnrichmentPlanner.Settings(
            true,
            Duration.ofSeconds(1),
            60,
            Duration.ofMinutes(1),
            5,
            false,
            Duration.ofSeconds(30),
            Duration.ofHours(1),
            false,
            Duration.ofMinutes(5),
            5);

    new ApplicationContextRunner(
            () -> {
              AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
              context.scan(UserInfoEnrichmentService.class.getPackageName());
              return context;
            })
        .withBean("ircClientService", IrcBackendRuntimeClientService.class, () -> irc)
        .withBean(
            ExecutorConfig.USER_INFO_ENRICHMENT_SCHEDULER,
            ScheduledExecutorService.class,
            () -> executor)
        .run(
            context -> {
              assertThat(context).hasSingleBean(UserInfoEnrichmentPlanner.class);
              assertThat(context).hasSingleBean(UserInfoEnrichmentService.class);
              assertThat(events.hasSubscribers()).isTrue();

              UserInfoEnrichmentPlanner planner = context.getBean(UserInfoEnrichmentPlanner.class);
              UserInfoEnrichmentService service = context.getBean(UserInfoEnrichmentService.class);
              planner.enqueueUserhost("libera", List.of("alice"));
              assertThat(planner.nextReadyDelayMs("libera", now, settings)).isZero();
              service.clearServer("libera");
              assertThat(planner.pollNext("libera", now, settings)).isEmpty();
            });

    assertThat(events.hasSubscribers()).isFalse();
  }
}
