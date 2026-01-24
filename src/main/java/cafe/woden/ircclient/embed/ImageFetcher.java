package cafe.woden.ircclient.embed;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches and scales images from URLs.
 */
@Component
public class ImageFetcher {

    private final HttpClient httpClient;
    private final EmbedSettings settings;

    public ImageFetcher(EmbedSettings settings) {
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(settings.fetchTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Fetch an image from the given URL and scale it to fit within max dimensions.
     */
    public Single<EmbedResult> fetch(String url) {
        return Single.<EmbedResult>create(emitter -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(settings.fetchTimeoutMs()))
                    .header("User-Agent", "IRCafe/1.0 (Embed Fetcher)")
                    .GET()
                    .build();

                HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    emitter.onSuccess(new EmbedResult.Failed(url,
                        "HTTP " + response.statusCode()));
                    return;
                }

                try (InputStream is = response.body()) {
                    BufferedImage original = ImageIO.read(is);
                    if (original == null) {
                        emitter.onSuccess(new EmbedResult.Failed(url, "Could not decode image"));
                        return;
                    }

                    int origWidth = original.getWidth();
                    int origHeight = original.getHeight();

                    // Create thumbnail for display, keep original for full-size viewer
                    BufferedImage thumbnail = scaleImage(original,
                        settings.maxThumbnailWidth(), settings.maxThumbnailHeight());

                    emitter.onSuccess(new EmbedResult.ImageEmbed(url, original, thumbnail, origWidth, origHeight));
                }
            } catch (Exception e) {
                emitter.onSuccess(new EmbedResult.Failed(url, e.getMessage()));
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Scale image to fit within maxWidth x maxHeight while preserving aspect ratio.
     */
    public BufferedImage scaleImage(BufferedImage original, int maxWidth, int maxHeight) {
        int origWidth = original.getWidth();
        int origHeight = original.getHeight();

        if (origWidth <= maxWidth && origHeight <= maxHeight) {
            return original;
        }

        double widthRatio = (double) maxWidth / origWidth;
        double heightRatio = (double) maxHeight / origHeight;
        double ratio = Math.min(widthRatio, heightRatio);

        int newWidth = (int) (origWidth * ratio);
        int newHeight = (int) (origHeight * ratio);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return scaled;
    }
}
