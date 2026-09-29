package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.EmbedPreviewRuntimeConfigPort;
import cafe.woden.ircclient.config.api.EmbedPreviewRuntimeConfigPort.EmbedPreviewSnapshot;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Secondary adapter for embed preview settings backed by {@link RuntimeConfigStore}. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class RuntimeConfigEmbedPreviewAdapter implements EmbedPreviewRuntimeConfigPort {
  private final RuntimeConfigStore runtimeConfig;

  public RuntimeConfigEmbedPreviewAdapter(RuntimeConfigStore runtimeConfig) {
    this.runtimeConfig = runtimeConfig;
  }

  @Override
  public void rememberEmbedPreviewSettings(EmbedPreviewSnapshot settings) {
    if (settings == null) {
      return;
    }
    runtimeConfig.rememberImageEmbedsEnabled(settings.imageEmbedsEnabled());
    runtimeConfig.rememberImageEmbedsCollapsedByDefault(settings.imageEmbedsCollapsedByDefault());
    runtimeConfig.rememberImageEmbedsMaxWidthPx(settings.imageEmbedsMaxWidthPx());
    runtimeConfig.rememberImageEmbedsMaxHeightPx(settings.imageEmbedsMaxHeightPx());
    runtimeConfig.rememberImageEmbedsAnimateGifs(settings.imageEmbedsAnimateGifs());
    runtimeConfig.rememberEmbedCardStyle(settings.embedCardStyleToken());
    runtimeConfig.rememberLinkPreviewsEnabled(settings.linkPreviewsEnabled());
    runtimeConfig.rememberLinkPreviewsCollapsedByDefault(settings.linkPreviewsCollapsedByDefault());
  }
}
