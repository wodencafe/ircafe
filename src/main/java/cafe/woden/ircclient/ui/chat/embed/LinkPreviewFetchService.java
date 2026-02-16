package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.net.ServerProxyResolver;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.net.URI;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class LinkPreviewFetchService {

  private static final Logger log = LoggerFactory.getLogger(LinkPreviewFetchService.class);

  private final ServerProxyResolver proxyResolver;
  private final List<LinkPreviewResolver> resolvers;

  private final ConcurrentMap<String, java.lang.ref.SoftReference<LinkPreview>> cache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Single<LinkPreview>> inflight = new ConcurrentHashMap<>();

  public LinkPreviewFetchService(ServerProxyResolver proxyResolver, List<LinkPreviewResolver> resolvers) {
    this.proxyResolver = proxyResolver;
    this.resolvers = (resolvers == null) ? List.of() : List.copyOf(resolvers);
  }

  public Single<LinkPreview> fetch(String serverId, String url) {
    if (url == null || url.isBlank()) {
      return Single.error(new IllegalArgumentException("url is blank"));
    }

    // Normalize early so cache keys are stable.
    final String normalized = ChatRichTextRenderer.normalizeUrl(url.trim());

    // Per-server cache key. Previews can vary by proxy (geo/CDN/bot pages), so we isolate.
    final String sid = Objects.toString(serverId, "").trim();
    final String key = sid + "|" + normalized + "|" + cacheVersion(normalized);

    // Cache hit
    var ref = cache.get(key);
    if (ref != null) {
      var v = ref.get();
      if (v != null) return Single.just(v);
      cache.remove(key, ref);
    }

    // Inflight de-dupe: computeIfAbsent + cache() so multiple subscribers share the same work.
    return inflight.computeIfAbsent(key, k ->
        Single.fromCallable(() -> load(sid, normalized))
            .subscribeOn(Schedulers.io())
            .doOnSuccess(p -> cache.put(k, new java.lang.ref.SoftReference<>(p)))
            .doFinally(() -> inflight.remove(k))
            .cache()
    );
  }

  // Back-compat for any callers not yet server-aware.
  public Single<LinkPreview> fetch(String url) {
    return fetch(null, url);
  }

  private LinkPreview load(String serverId, String url) throws Exception {
    URI uri = URI.create(url);
    String scheme = String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new IllegalArgumentException("unsupported scheme: " + scheme);
    }
    if (isDefinitelyLocalOrPrivateHost(uri.getHost())) {
      throw new IllegalArgumentException("refusing to fetch local/private host: " + uri.getHost());
    }

    PreviewHttp http = new PreviewHttp(proxyResolver != null ? proxyResolver.planForServer(serverId) : null);

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

  private static String cacheVersion(String normalizedUrl) {
    try {
      URI uri = URI.create(normalizedUrl);
      if (InstagramPreviewUtil.isInstagramPostUri(uri)) {
        // Bump this when Instagram extraction/layout semantics change to avoid stale cached cards.
        return "ig-v2";
      }
      if (NewsPreviewUtil.isLikelyNewsArticleUri(uri)) {
        // News previews can switch from plain OG to structured metadata+summary formatting.
        return "news-v2";
      }
    } catch (Exception ignored) {
      // Fall through to default version.
    }
    return "v1";
  }
}
