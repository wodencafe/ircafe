package cafe.woden.ircclient.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.irc.PlaybackCursorProvider;
import cafe.woden.ircclient.irc.PlaybackCursorProviderConfig;
import cafe.woden.ircclient.logging.history.ChatHistoryIngestResult;
import cafe.woden.ircclient.logging.history.ChatHistoryIngestor;
import cafe.woden.ircclient.logging.history.ChatHistoryIngestorConfig;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.logging.history.ChatHistoryServiceConfig;
import cafe.woden.ircclient.logging.history.LoadOlderResult;
import cafe.woden.ircclient.logging.history.LogCursor;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerResult;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerService;
import cafe.woden.ircclient.logging.viewer.ChatLogViewerServiceConfig;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class LoggingFallbackConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              ChatLogWriterConfig.class,
              ChatLogMaintenanceConfig.class,
              ChatLogViewerServiceConfig.class,
              ChatHistoryServiceConfig.class,
              ChatHistoryIngestorConfig.class,
              PlaybackCursorProviderConfig.class);

  @Test
  void fallbackBeansAreAvailableAndNoOp() {
    runner.run(
        ctx -> {
          ChatLogWriter writer = ctx.getBean(ChatLogWriter.class);
          writer.log(null);

          ChatLogMaintenance maintenance = ctx.getBean(ChatLogMaintenance.class);
          assertFalse(maintenance.enabled());
          maintenance.clearTarget(new TargetRef("srv", "#chan"));

          ChatLogViewerService viewer = ctx.getBean(ChatLogViewerService.class);
          assertFalse(viewer.enabled());
          ChatLogViewerResult search = viewer.search(null);
          assertNotNull(search);
          assertTrue(search.rows().isEmpty());
          assertEquals(0, search.scannedRows());
          assertTrue(viewer.listUniqueChannels("srv", 20).isEmpty());

          ChatHistoryService history = ctx.getBean(ChatHistoryService.class);
          assertFalse(history.isEnabled());
          TargetRef target = new TargetRef("srv", "#chan");
          assertFalse(history.canLoadOlder(target));

          AtomicReference<LoadOlderResult> olderRef = new AtomicReference<>();
          history.loadOlder(target, 50, olderRef::set);
          LoadOlderResult older = olderRef.get();
          assertNotNull(older);
          assertTrue(older.linesOldestFirst().isEmpty());
          assertEquals(new LogCursor(0, 0), older.newOldestCursor());
          assertFalse(older.hasMore());

          ChatHistoryIngestor ingestor = ctx.getBean(ChatHistoryIngestor.class);
          AtomicReference<ChatHistoryIngestResult> ingestRef = new AtomicReference<>();
          ingestor.ingestAsync("srv", "#chan", "batch-1", List.of(), ingestRef::set);
          ChatHistoryIngestResult ingest = ingestRef.get();
          assertNotNull(ingest);
          assertFalse(ingest.enabled());
          assertEquals(0, ingest.total());
          assertEquals(0, ingest.inserted());
          assertEquals(0, ingest.skipped());

          PlaybackCursorProvider playback = ctx.getBean(PlaybackCursorProvider.class);
          assertEquals(OptionalLong.empty(), playback.lastSeenEpochSeconds("srv"));
        });
  }

  @Test
  void customBeansOverrideConditionalFallbacks() {
    new ApplicationContextRunner()
        .withUserConfiguration(
            CustomOverrideConfig.class,
            ChatLogWriterConfig.class,
            ChatLogMaintenanceConfig.class,
            ChatLogViewerServiceConfig.class,
            ChatHistoryServiceConfig.class,
            ChatHistoryIngestorConfig.class,
            PlaybackCursorProviderConfig.class)
        .run(
            ctx -> {
              assertSame(
                  CustomOverrideConfig.CHAT_LOG_WRITER,
                  ctx.getBean("chatLogWriter", ChatLogWriter.class));
              assertSame(
                  CustomOverrideConfig.CHAT_LOG_MAINTENANCE,
                  ctx.getBean("chatLogMaintenance", ChatLogMaintenance.class));
              assertSame(
                  CustomOverrideConfig.CHAT_LOG_VIEWER,
                  ctx.getBean("chatLogViewerService", ChatLogViewerService.class));
              assertSame(
                  CustomOverrideConfig.CHAT_HISTORY_SERVICE,
                  ctx.getBean("chatHistoryService", ChatHistoryService.class));
              assertSame(
                  CustomOverrideConfig.CHAT_HISTORY_INGESTOR,
                  ctx.getBean("chatHistoryIngestor", ChatHistoryIngestor.class));
              assertSame(
                  CustomOverrideConfig.PLAYBACK_CURSOR_PROVIDER,
                  ctx.getBean("playbackCursorProvider", PlaybackCursorProvider.class));
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomOverrideConfig {
    static final ChatLogWriter CHAT_LOG_WRITER = line -> {};

    static final ChatLogMaintenance CHAT_LOG_MAINTENANCE =
        new ChatLogMaintenance() {
          @Override
          public boolean enabled() {
            return true;
          }

          @Override
          public void clearTarget(TargetRef target) {}
        };

    static final ChatLogViewerService CHAT_LOG_VIEWER =
        new ChatLogViewerService() {
          @Override
          public boolean enabled() {
            return true;
          }

          @Override
          public ChatLogViewerResult search(cafe.woden.ircclient.logging.viewer.ChatLogViewerQuery query) {
            return new ChatLogViewerResult(List.of(), 0, false, false);
          }

          @Override
          public List<String> listUniqueChannels(String serverId, int limit) {
            return List.of("#custom");
          }
        };

    static final ChatHistoryService CHAT_HISTORY_SERVICE =
        new ChatHistoryService() {
          @Override
          public void onTargetSelected(TargetRef target) {}

          @Override
          public boolean isEnabled() {
            return true;
          }

          @Override
          public boolean canLoadOlder(TargetRef target) {
            return true;
          }

          @Override
          public void loadOlder(
              TargetRef target, int limit, java.util.function.Consumer<LoadOlderResult> callback) {
            if (callback == null) return;
            callback.accept(new LoadOlderResult(List.of(), new LogCursor(1, 1), true));
          }

          @Override
          public void reset(TargetRef target) {}
        };

    static final ChatHistoryIngestor CHAT_HISTORY_INGESTOR =
        (serverId, targetHint, batchId, entries, callback) -> {
          if (callback == null) return;
          callback.accept(new ChatHistoryIngestResult(true, 0, 0, 0, 0, 0, "ok"));
        };

    static final PlaybackCursorProvider PLAYBACK_CURSOR_PROVIDER = serverId -> OptionalLong.of(42L);

    @Bean
    ChatLogWriter chatLogWriter() {
      return CHAT_LOG_WRITER;
    }

    @Bean
    ChatLogMaintenance chatLogMaintenance() {
      return CHAT_LOG_MAINTENANCE;
    }

    @Bean
    ChatLogViewerService chatLogViewerService() {
      return CHAT_LOG_VIEWER;
    }

    @Bean
    ChatHistoryService chatHistoryService() {
      return CHAT_HISTORY_SERVICE;
    }

    @Bean
    ChatHistoryIngestor chatHistoryIngestor() {
      return CHAT_HISTORY_INGESTOR;
    }

    @Bean
    PlaybackCursorProvider playbackCursorProvider() {
      return PLAYBACK_CURSOR_PROVIDER;
    }
  }
}
