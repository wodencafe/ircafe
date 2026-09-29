package cafe.woden.ircclient.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.api.EmbedPreviewRuntimeConfigPort.EmbedPreviewSnapshot;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.junit.jupiter.api.Test;

class RuntimeConfigEmbedPreviewAdapterTest {

  @Test
  void writesEmbedPreviewSnapshotToRuntimeStore() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    RuntimeConfigEmbedPreviewAdapter adapter = new RuntimeConfigEmbedPreviewAdapter(runtimeConfig);

    adapter.rememberEmbedPreviewSettings(
        new EmbedPreviewSnapshot(true, false, 640, 480, true, true, false, "denser"));

    verify(runtimeConfig).rememberImageEmbedsEnabled(true);
    verify(runtimeConfig).rememberImageEmbedsCollapsedByDefault(false);
    verify(runtimeConfig).rememberImageEmbedsMaxWidthPx(640);
    verify(runtimeConfig).rememberImageEmbedsMaxHeightPx(480);
    verify(runtimeConfig).rememberImageEmbedsAnimateGifs(true);
    verify(runtimeConfig).rememberEmbedCardStyle("denser");
    verify(runtimeConfig).rememberLinkPreviewsEnabled(true);
    verify(runtimeConfig).rememberLinkPreviewsCollapsedByDefault(false);
  }
}
