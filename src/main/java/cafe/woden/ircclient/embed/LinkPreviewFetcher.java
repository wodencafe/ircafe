package cafe.woden.ircclient.embed;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches link preview data using OpenGraph meta tags.
 */
@Component
public class LinkPreviewFetcher {

    private final HttpClient httpClient;
    private final EmbedSettings settings;
    private final ImageFetcher imageFetcher;

    public LinkPreviewFetcher(EmbedSettings settings, ImageFetcher imageFetcher) {
        this.settings = settings;
        this.imageFetcher = imageFetcher;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(settings.fetchTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Fetch link preview data from OpenGraph tags.
     */
    public Single<EmbedResult> fetch(String url) {
        return Single.<EmbedResult>create(emitter -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(settings.fetchTimeoutMs()))
                    .header("User-Agent", "IRCafe/1.0 (Link Preview)")
                    .header("Accept", "text/html")
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    emitter.onSuccess(new EmbedResult.Failed(url,
                        "HTTP " + response.statusCode()));
                    return;
                }

                Document doc = Jsoup.parse(response.body(), url);

                String title = getMetaContent(doc, "og:title");
                if (title == null || title.isBlank()) {
                    title = doc.title();
                }

                String description = getMetaContent(doc, "og:description");
                if (description == null || description.isBlank()) {
                    description = getMetaContent(doc, "description");
                }

                String siteName = getMetaContent(doc, "og:site_name");

                String ogImageUrl = getMetaContent(doc, "og:image");
                BufferedImage ogImage = null;
                if (ogImageUrl != null && !ogImageUrl.isBlank()) {
                    ogImage = fetchImage(resolveUrl(url, ogImageUrl));
                    if (ogImage != null) {
                        ogImage = imageFetcher.scaleImage(ogImage,
                            settings.maxThumbnailWidth(), settings.maxThumbnailHeight());
                    }
                }

                // Fetch favicon
                BufferedImage favicon = fetchFavicon(doc, url);

                // Only create preview if we have meaningful content
                if ((title == null || title.isBlank()) &&
                    (description == null || description.isBlank()) &&
                    ogImage == null) {
                    emitter.onSuccess(new EmbedResult.Failed(url, "No preview data available"));
                    return;
                }

                emitter.onSuccess(new EmbedResult.LinkPreview(url, title, description,
                    siteName, favicon, ogImage));
            } catch (Exception e) {
                emitter.onSuccess(new EmbedResult.Failed(url, e.getMessage()));
            }
        }).subscribeOn(Schedulers.io());
    }

    private String getMetaContent(Document doc, String property) {
        Element meta = doc.selectFirst("meta[property=" + property + "]");
        if (meta != null) {
            return meta.attr("content");
        }
        meta = doc.selectFirst("meta[name=" + property + "]");
        if (meta != null) {
            return meta.attr("content");
        }
        return null;
    }

    private BufferedImage fetchFavicon(Document doc, String pageUrl) {
        try {
            // Try link rel="icon" first
            Element iconLink = doc.selectFirst("link[rel~=icon]");
            String faviconUrl = null;

            if (iconLink != null) {
                faviconUrl = iconLink.attr("href");
            }

            if (faviconUrl == null || faviconUrl.isBlank()) {
                // Fall back to /favicon.ico
                URI uri = URI.create(pageUrl);
                faviconUrl = uri.getScheme() + "://" + uri.getHost() + "/favicon.ico";
            } else {
                faviconUrl = resolveUrl(pageUrl, faviconUrl);
            }

            BufferedImage favicon = fetchImage(faviconUrl);
            if (favicon != null) {
                // Scale favicon to standard size
                favicon = imageFetcher.scaleImage(favicon, 32, 32);
            }
            return favicon;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUrl(String base, String relative) {
        if (relative == null) return null;
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return relative;
        }
        try {
            return URI.create(base).resolve(relative).toString();
        } catch (Exception e) {
            return relative;
        }
    }

    private BufferedImage fetchImage(String url) {
        if (url == null) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(settings.fetchTimeoutMs()))
                .header("User-Agent", "IRCafe/1.0")
                .GET()
                .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                try (InputStream is = response.body()) {
                    return ImageIO.read(is);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
