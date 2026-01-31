package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.net.URI;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Fetch and parse link preview metadata.
 *
 * <p>Implementation strategy:
 * <ul>
 *   <li>GET the URL (http/https) with a small timeout</li>
 *   <li>Read up to {@link #MAX_BYTES} of HTML</li>
 *   <li>Parse OpenGraph/Twitter tags with jsoup</li>
 *   <li>Cache results and de-dupe inflight fetches</li>
 * </ul>
 */
@Component
@Lazy
public class LinkPreviewFetchService {

  private static final Logger log = LoggerFactory.getLogger(LinkPreviewFetchService.class);

  /** Avoid downloading giant pages just to find OG tags. */
  private static final int MAX_BYTES = 1024 * 1024; // 1 MiB

  private final PreviewHttp http = new PreviewHttp();
  private final List<LinkPreviewResolver> resolvers = List.of(
      new WikipediaLinkPreviewResolver(),
      new YouTubeLinkPreviewResolver(),
      new XLinkPreviewResolver(MAX_BYTES),
      new GitHubLinkPreviewResolver(),
      new RedditLinkPreviewResolver(),
      new MastodonStatusApiPreviewResolver(),
      new OEmbedLinkPreviewResolver(OEmbedLinkPreviewResolver.defaultProviders()),
      new OpenGraphLinkPreviewResolver(MAX_BYTES)
  );

  private final ConcurrentMap<String, java.lang.ref.SoftReference<LinkPreview>> cache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Single<LinkPreview>> inflight = new ConcurrentHashMap<>();

  public Single<LinkPreview> fetch(String url) {
    if (url == null || url.isBlank()) {
      return Single.error(new IllegalArgumentException("url is blank"));
    }

    // Normalize early so cache keys are stable.
    final String normalized = ChatRichTextRenderer.normalizeUrl(url.trim());

    // Cache hit
    var ref = cache.get(normalized);
    if (ref != null) {
      var v = ref.get();
      if (v != null) return Single.just(v);
      cache.remove(normalized, ref);
    }

    // Inflight de-dupe: computeIfAbsent + cache() so multiple subscribers share the same work.
    return inflight.computeIfAbsent(normalized, key ->
        Single.fromCallable(() -> load(key))
            .subscribeOn(Schedulers.io())
            .doOnSuccess(p -> cache.put(key, new java.lang.ref.SoftReference<>(p)))
            .doFinally(() -> inflight.remove(key))
            .cache()
    );
  }

  private LinkPreview load(String url) throws Exception {
    URI uri = URI.create(url);
    String scheme = String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new IllegalArgumentException("unsupported scheme: " + scheme);
    }
    if (isDefinitelyLocalOrPrivateHost(uri.getHost())) {
      throw new IllegalArgumentException("refusing to fetch local/private host: " + uri.getHost());
    }

    for (LinkPreviewResolver r : resolvers) {
      try {
        LinkPreview p = r.tryResolve(uri, url, http);
        if (p != null) return p;
      } catch (Exception e) {
        // Resolvers may throw when they apply but fail (e.g., HTTP errors).
        // Don't fail the whole preview chain: keep trying fallbacks.
        log.debug("Link preview resolver {} failed for {}: {}", r.getClass().getSimpleName(), url, e.toString());
        // Continue to the next resolver.
      }
    }

    throw new IllegalStateException("no preview resolver matched");
  }

  private static boolean isDefinitelyLocalOrPrivateHost(String host) {
    if (host == null || host.isBlank()) return false;
    String h = host.toLowerCase(Locale.ROOT).trim();

    if (h.equals("localhost") || h.equals("localhost.localdomain") || h.equals("0.0.0.0") || h.equals("::1")) {
      return true;
    }
    // If it's an IPv4 literal, block common private ranges.
    if (h.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
      String[] parts = h.split("\\.");
      if (parts.length == 4) {
        int a = parseInt(parts[0]);
        int b = parseInt(parts[1]);
        if (a == 10) return true;
        if (a == 127) return true;
        if (a == 192 && b == 168) return true;
        if (a == 172 && b >= 16 && b <= 31) return true;
      }
    }
    // IPv6: block loopback + unique local + link-local.
    if (h.contains(":")) {
      if (h.equals("::1")) return true;
      if (h.startsWith("fc") || h.startsWith("fd")) return true; // fc00::/7 (very rough)
      if (h.startsWith("fe80")) return true; // link-local
    }

    return false;
  }

  private static int parseInt(String s) {
    try {
      return Integer.parseInt(s);
    } catch (Exception ignored) {
      return -1;
    }
  }
}
