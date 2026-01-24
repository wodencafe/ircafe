package cafe.woden.ircclient.embed;

import io.reactivex.rxjava3.core.Single;
import org.springframework.stereotype.Component;

/**
 * Coordinates fetching of embeds based on URL type.
 */
@Component
public class EmbedFetcher {

    private final UrlClassifier urlClassifier;
    private final ImageFetcher imageFetcher;
    private final VideoFetcher videoFetcher;
    private final LinkPreviewFetcher linkPreviewFetcher;
    private final EmbedCache embedCache;
    private final EmbedSettings settings;

    public EmbedFetcher(
        UrlClassifier urlClassifier,
        ImageFetcher imageFetcher,
        VideoFetcher videoFetcher,
        LinkPreviewFetcher linkPreviewFetcher,
        EmbedCache embedCache,
        EmbedSettings settings
    ) {
        this.urlClassifier = urlClassifier;
        this.imageFetcher = imageFetcher;
        this.videoFetcher = videoFetcher;
        this.linkPreviewFetcher = linkPreviewFetcher;
        this.embedCache = embedCache;
        this.settings = settings;
    }

    /**
     * Fetch embed data for the given URL.
     * Returns cached result if available, otherwise fetches and caches.
     */
    public Single<EmbedResult> fetch(String url) {
        if (!settings.enabled()) {
            return Single.just(new EmbedResult.Failed(url, "Embeds disabled"));
        }

        // Check cache first
        EmbedResult cached = embedCache.get(url);
        if (cached != null) {
            return Single.just(cached);
        }

        EmbedType type = urlClassifier.classify(url);

        Single<EmbedResult> fetchSingle = switch (type) {
            case IMAGE -> imageFetcher.fetch(url);
            case VIDEO -> videoFetcher.fetch(url);
            case LINK_PREVIEW -> linkPreviewFetcher.fetch(url);
            case NONE -> Single.just(new EmbedResult.Failed(url, "URL type not supported"));
        };

        // Cache the result
        return fetchSingle.doOnSuccess(result -> {
            if (!(result instanceof EmbedResult.Failed)) {
                embedCache.put(url, result);
            }
        });
    }

    /**
     * Get the embed type for a URL without fetching.
     */
    public EmbedType classifyUrl(String url) {
        return urlClassifier.classify(url);
    }
}
