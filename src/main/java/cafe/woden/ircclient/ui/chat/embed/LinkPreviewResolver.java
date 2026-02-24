package cafe.woden.ircclient.ui.chat.embed;

import java.net.URI;

/**
 * Strategy interface for resolving a {@link LinkPreview} for a URL.
 *
 * <p>Resolvers should return {@code null} when they don't apply to the URL. They may throw when
 * they apply but fail in a meaningful way.
 */
interface LinkPreviewResolver {

  LinkPreview tryResolve(URI uri, String originalUrl, PreviewHttp http) throws Exception;
}
