package cafe.woden.ircclient.logging.channelmeta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelMetadataStoreTest {

  @Test
  void initializeCacheLoadsPersistedChannelTopics() {
    ChannelMetadataRepository repository = mock(ChannelMetadataRepository.class);
    when(repository.findAll())
        .thenReturn(
            List.of(
                new ChannelMetadataRepository.ChannelMetadataRow(
                    "libera", "#ircafe", "#ircafe", "Persisted topic", null, null, 1L),
                new ChannelMetadataRepository.ChannelMetadataRow(
                    "libera", "status", "status", "not a channel", null, null, 1L)));
    ChannelMetadataStore store = new ChannelMetadataStore(repository, Runnable::run);

    store.initializeCache();

    assertEquals("Persisted topic", store.topicFor(new TargetRef("libera", "#ircafe")));
    assertEquals("", store.topicFor(new TargetRef("libera", "status")));
  }

  @Test
  void rememberTopicUpdatesCacheAndPersists() {
    ChannelMetadataRepository repository = mock(ChannelMetadataRepository.class);
    when(repository.findAll()).thenReturn(List.of());
    ChannelMetadataStore store = new ChannelMetadataStore(repository, Runnable::run);
    store.initializeCache();

    TargetRef target = new TargetRef("libera", "#ircafe");
    store.rememberTopic(target, "New topic", null, 123L);

    assertEquals("New topic", store.topicFor(target));
    verify(repository).upsert(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rememberBlankTopicDeletesPersistedSnapshot() {
    ChannelMetadataRepository repository = mock(ChannelMetadataRepository.class);
    when(repository.findAll())
        .thenReturn(
            List.of(
                new ChannelMetadataRepository.ChannelMetadataRow(
                    "libera", "#ircafe", "#ircafe", "Persisted topic", null, null, 1L)));
    ChannelMetadataStore store = new ChannelMetadataStore(repository, Runnable::run);
    store.initializeCache();

    TargetRef target = new TargetRef("libera", "#ircafe");
    store.rememberTopic(target, "", null, null);

    assertEquals("", store.topicFor(target));
    verify(repository).delete("libera", "#ircafe");
  }

  @Test
  void rememberTopicPanelHeightUpdatesCacheAndPersists() {
    ChannelMetadataRepository repository = mock(ChannelMetadataRepository.class);
    when(repository.findAll()).thenReturn(List.of());
    ChannelMetadataStore store = new ChannelMetadataStore(repository, Runnable::run);
    store.initializeCache();

    TargetRef target = new TargetRef("libera", "#ircafe");
    store.rememberTopicPanelHeight(target, 147);

    assertEquals(147, store.topicPanelHeightPxFor(target));
    verify(repository).upsert(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void topicPanelHeightFallsBackToDefaultWhenMissing() {
    ChannelMetadataRepository repository = mock(ChannelMetadataRepository.class);
    when(repository.findAll()).thenReturn(List.of());
    ChannelMetadataStore store = new ChannelMetadataStore(repository, Runnable::run);
    store.initializeCache();

    assertEquals(58, store.topicPanelHeightPxFor(new TargetRef("libera", "#ircafe")));
  }
}
