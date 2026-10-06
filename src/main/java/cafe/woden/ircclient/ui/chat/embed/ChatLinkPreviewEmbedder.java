package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.DocumentComponentFactory;
import cafe.woden.ircclient.ui.settings.EmbedCardStyle;
import cafe.woden.ircclient.ui.settings.EmbedCardStyleBus;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.List;
import java.util.Map;
import javax.swing.text.StyledDocument;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@InterfaceLayer
@Lazy
public class ChatLinkPreviewEmbedder {

  private static final int MAX_PREVIEWS_PER_MESSAGE = 1;

  private final UiSettingsBus uiSettings;
  private final LinkPreviewFetchService fetch;
  private final ImageFetchService imageFetch;
  private final EmbedLoadPolicyMatcher policyMatcher;
  private final EmbedCardStyleBus embedCardStyleBus;
  private final EmbedDocumentApplicationService documentApplication;
  private final EmbedRenderRequestService renderRequestService;
  private volatile List<cafe.woden.ircclient.ui.chat.embed.spi.ImageUrlExtensionProvider>
      imageUrlExtensionProviders = List.of();

  public record AppendResult(int appendedCount, List<String> blockedUrls) {
    static AppendResult empty() {
      return new AppendResult(0, List.of());
    }
  }

  public ChatLinkPreviewEmbedder(
      UiSettingsBus uiSettings,
      LinkPreviewFetchService fetch,
      ImageFetchService imageFetch,
      EmbedLoadPolicyMatcher policyMatcher,
      EmbedCardStyleBus embedCardStyleBus,
      EmbedDocumentApplicationService documentApplication,
      EmbedRenderRequestService renderRequestService) {
    this.uiSettings = uiSettings;
    this.fetch = fetch;
    this.imageFetch = imageFetch;
    this.policyMatcher = policyMatcher;
    this.embedCardStyleBus = embedCardStyleBus;
    this.documentApplication = documentApplication;
    this.renderRequestService =
        renderRequestService != null ? renderRequestService : new EmbedRenderRequestService();
  }

  @Autowired(required = false)
  void setInstalledPluginsPort(InstalledPluginsPort installedPlugins) {
    imageUrlExtensionProviders =
        ImageUrlExtensionProviders.loadInstalledProviders(installedPlugins);
  }

  public AppendResult appendPreviews(
      TargetRef ctx,
      StyledDocument doc,
      String messageText,
      String fromNick,
      Map<String, String> ircv3Tags) {
    if (doc == null || messageText == null || messageText.isBlank()) return AppendResult.empty();
    if (!uiSettings.get().linkPreviewsEnabled()) return AppendResult.empty();

    String serverId = (ctx != null) ? ctx.serverId() : null;
    List<String> urls = LinkUrlExtractor.extractUrls(messageText, imageUrlExtensionProviders);
    if (urls.isEmpty()) return AppendResult.empty();

    EmbedAppendResultAccumulator accumulator =
        EmbedAppendResultAccumulator.startingAt(doc.getLength());
    for (String url : urls) {
      if (!accumulator.canAppendMore(MAX_PREVIEWS_PER_MESSAGE)) break;
      try {
        accumulator.add(
            insertPreview(
                ctx, doc, fromNick, ircv3Tags, serverId, url, false, accumulator.nextInsertAt()));
      } catch (Exception ignored) {
      }
    }
    return toAppendResult(accumulator.finish());
  }

  private static AppendResult toAppendResult(EmbedAppendResult result) {
    return result == null
        ? AppendResult.empty()
        : new AppendResult(result.appendedCount(), result.blockedUrls());
  }

  public boolean insertPreviewForUrlAt(
      TargetRef ctx, StyledDocument doc, String rawUrl, int insertAt) {
    if (doc == null) return false;
    String serverId = (ctx != null) ? ctx.serverId() : null;
    try {
      EmbedApplicationResult result =
          insertPreview(ctx, doc, "", Map.of(), serverId, rawUrl, true, Math.max(0, insertAt));
      return result.appended();
    } catch (Exception ignored) {
      return false;
    }
  }

  private EmbedApplicationResult insertPreview(
      TargetRef ctx,
      StyledDocument doc,
      String fromNick,
      Map<String, String> ircv3Tags,
      String serverId,
      String rawUrl,
      boolean bypassPolicy,
      int insertAt) {
    if (doc == null) {
      return EmbedApplicationResult.skipped(insertAt);
    }

    UiSettings settings = uiSettings.get();
    LinkPreviewRenderRequest request;
    try {
      request =
          renderRequestService.linkPreviewRequest(
              serverId,
              rawUrl,
              settings != null && settings.linkPreviewsCollapsedByDefault(),
              settings != null ? settings.imageEmbedsMaxWidthPx() : 0,
              settings != null ? settings.imageEmbedsMaxHeightPx() : 0);
    } catch (IllegalArgumentException ignored) {
      return EmbedApplicationResult.skipped(insertAt);
    }

    if (!bypassPolicy
        && policyMatcher != null
        && !policyMatcher.allow(ctx, fromNick, ircv3Tags, request.url())) {
      return EmbedApplicationResult.blocked(insertAt, request.url());
    }
    EmbedCardStyle cardStyle =
        embedCardStyleBus != null ? embedCardStyleBus.get() : EmbedCardStyle.DEFAULT;
    DocumentComponentFactory comp =
        new DocumentComponentFactory(
            () ->
                new ChatLinkPreviewComponent(
                    request.serverId(),
                    request.url(),
                    fetch,
                    imageFetch,
                    request.collapsedByDefault(),
                    cardStyle,
                    request.imageEmbedsMaxWidthPx(),
                    request.imageEmbedsMaxHeightPx()));

    EmbedDocumentApplicationService.InsertResult result =
        documentApplication.insertComponent(doc, request.url(), comp, insertAt);
    return result.inserted()
        ? EmbedApplicationResult.appended(result.nextInsertAt())
        : EmbedApplicationResult.skipped(insertAt);
  }
}
