package cafe.woden.ircclient.embed;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Fetches video metadata and thumbnails for YouTube, Vimeo, and direct video URLs.
 */
@Component
public class VideoFetcher {

    private final HttpClient httpClient;
    private final EmbedSettings settings;
    private final UrlClassifier urlClassifier;
    private final ImageFetcher imageFetcher;

    public VideoFetcher(EmbedSettings settings, UrlClassifier urlClassifier, ImageFetcher imageFetcher) {
        this.settings = settings;
        this.urlClassifier = urlClassifier;
        this.imageFetcher = imageFetcher;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(settings.fetchTimeoutMs()))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Fetch video metadata including thumbnail.
     */
    public Single<EmbedResult> fetch(String url) {
        return Single.<EmbedResult>create(emitter -> {
            try {
                Optional<EmbedResult.VideoProvider> providerOpt = urlClassifier.getVideoProvider(url);
                if (providerOpt.isEmpty()) {
                    emitter.onSuccess(new EmbedResult.Failed(url, "Unknown video provider"));
                    return;
                }

                EmbedResult.VideoProvider provider = providerOpt.get();
                EmbedResult result = switch (provider) {
                    case YOUTUBE -> fetchYouTube(url);
                    case VIMEO -> fetchVimeo(url);
                    case DIRECT -> fetchDirect(url);
                };

                emitter.onSuccess(result);
            } catch (Exception e) {
                emitter.onSuccess(new EmbedResult.Failed(url, e.getMessage()));
            }
        }).subscribeOn(Schedulers.io());
    }

    private EmbedResult fetchYouTube(String url) {
        Optional<String> videoIdOpt = urlClassifier.extractYouTubeId(url);
        if (videoIdOpt.isEmpty()) {
            return new EmbedResult.Failed(url, "Could not extract YouTube video ID");
        }

        String videoId = videoIdOpt.get();

        // Try to fetch high quality thumbnail, fall back to default
        String[] thumbnailUrls = {
            "https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg",
            "https://img.youtube.com/vi/" + videoId + "/sddefault.jpg",
            "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg",
            "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg",
            "https://img.youtube.com/vi/" + videoId + "/default.jpg"
        };

        BufferedImage thumbnail = null;
        for (String thumbUrl : thumbnailUrls) {
            thumbnail = fetchImage(thumbUrl);
            if (thumbnail != null && thumbnail.getWidth() > 120) {
                break;
            }
        }

        if (thumbnail != null) {
            thumbnail = imageFetcher.scaleImage(thumbnail,
                settings.maxThumbnailWidth(), settings.maxThumbnailHeight());
        }

        // Try to get title from oEmbed
        String title = fetchYouTubeTitle(videoId);

        return new EmbedResult.VideoEmbed(url, thumbnail, videoId,
            EmbedResult.VideoProvider.YOUTUBE, title);
    }

    private EmbedResult fetchVimeo(String url) {
        Optional<String> videoIdOpt = urlClassifier.extractVimeoId(url);
        if (videoIdOpt.isEmpty()) {
            return new EmbedResult.Failed(url, "Could not extract Vimeo video ID");
        }

        String videoId = videoIdOpt.get();
        BufferedImage thumbnail = null;
        String title = null;

        // Fetch Vimeo oEmbed data
        try {
            String oembedUrl = "https://vimeo.com/api/oembed.json?url=" +
                java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(oembedUrl))
                .timeout(Duration.ofMillis(settings.fetchTimeoutMs()))
                .header("User-Agent", "IRCafe/1.0")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String json = response.body();
                // Simple JSON parsing for thumbnail_url and title
                title = extractJsonString(json, "title");
                String thumbUrl = extractJsonString(json, "thumbnail_url");
                if (thumbUrl != null) {
                    thumbnail = fetchImage(thumbUrl);
                    if (thumbnail != null) {
                        thumbnail = imageFetcher.scaleImage(thumbnail,
                            settings.maxThumbnailWidth(), settings.maxThumbnailHeight());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return new EmbedResult.VideoEmbed(url, thumbnail, videoId,
            EmbedResult.VideoProvider.VIMEO, title);
    }

    private EmbedResult fetchDirect(String url) {
        // Try to grab a frame from the video using VLCJ
        BufferedImage thumbnail = grabVideoFrame(url);
        if (thumbnail != null) {
            thumbnail = imageFetcher.scaleImage(thumbnail,
                settings.maxThumbnailWidth(), settings.maxThumbnailHeight());
        }

        // Extract filename as title
        String title = null;
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            title = url.substring(lastSlash + 1);
            // Remove query string if present
            int queryIdx = title.indexOf('?');
            if (queryIdx > 0) {
                title = title.substring(0, queryIdx);
            }
        }

        return new EmbedResult.VideoEmbed(url, thumbnail, null,
            EmbedResult.VideoProvider.DIRECT, title);
    }

    private BufferedImage grabVideoFrame(String url) {
        try {
            uk.co.caprica.vlcj.factory.MediaPlayerFactory factory = new uk.co.caprica.vlcj.factory.MediaPlayerFactory();
            uk.co.caprica.vlcj.player.base.MediaPlayer player = factory.mediaPlayers().newMediaPlayer();

            try {
                // Set up snapshot callback
                final BufferedImage[] frameHolder = new BufferedImage[1];
                final Object lock = new Object();

                player.events().addMediaPlayerEventListener(new uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter() {
                    @Override
                    public void playing(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                        // Wait a moment for video to stabilize, then grab frame
                        try {
                            Thread.sleep(500);
                            // Seek to 2 seconds in (or 10% of duration)
                            long length = player.status().length();
                            if (length > 0) {
                                player.controls().setTime(Math.min(2000, length / 10));
                            }
                            Thread.sleep(200);

                            // Grab snapshot
                            java.awt.Dimension size = player.video().videoDimension();
                            if (size != null && size.width > 0 && size.height > 0) {
                                // Save snapshot to temp file and read it back
                                java.io.File tempFile = java.io.File.createTempFile("vlc_snapshot", ".png");
                                tempFile.deleteOnExit();
                                boolean saved = player.snapshots().save(tempFile, size.width, size.height);
                                if (saved && tempFile.exists()) {
                                    frameHolder[0] = javax.imageio.ImageIO.read(tempFile);
                                    tempFile.delete();
                                }
                            }
                        } catch (Exception ignored) {
                        } finally {
                            synchronized (lock) {
                                lock.notifyAll();
                            }
                        }
                    }

                    @Override
                    public void error(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    }
                });

                // Start playing
                player.media().play(url);

                // Wait for frame capture (max 10 seconds)
                synchronized (lock) {
                    lock.wait(10000);
                }

                return frameHolder[0];
            } finally {
                player.controls().stop();
                player.release();
                factory.release();
            }
        } catch (Exception | UnsatisfiedLinkError e) {
            // VLC not available or error grabbing frame
            return null;
        }
    }

    private String fetchYouTubeTitle(String videoId) {
        try {
            String oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" +
                videoId + "&format=json";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(oembedUrl))
                .timeout(Duration.ofMillis(settings.fetchTimeoutMs()))
                .header("User-Agent", "IRCafe/1.0")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractJsonString(response.body(), "title");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private BufferedImage fetchImage(String url) {
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

    /**
     * Simple JSON string extraction without full parser dependency.
     */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;

        idx += search.length();
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) {
            idx++;
        }

        if (idx >= json.length() || json.charAt(idx) != '"') return null;
        idx++; // skip opening quote

        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '"') break;
            if (c == '\\' && idx + 1 < json.length()) {
                idx++;
                c = json.charAt(idx);
                if (c == 'n') sb.append('\n');
                else if (c == 't') sb.append('\t');
                else if (c == 'r') sb.append('\r');
                else sb.append(c);
            } else {
                sb.append(c);
            }
            idx++;
        }

        return sb.toString();
    }
}
