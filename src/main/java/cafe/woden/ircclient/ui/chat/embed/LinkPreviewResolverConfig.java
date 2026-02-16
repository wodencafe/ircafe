package cafe.woden.ircclient.ui.chat.embed;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Spring wiring for link preview resolvers.
 *
 * <p>Order is significant: earlier resolvers get first shot before fallbacks like OpenGraph.
 */
@Configuration
public class LinkPreviewResolverConfig {

  // Keep in sync with any resolver HTML caps.
  static final int DEFAULT_MAX_HTML_BYTES = 1024 * 1024; // 1 MiB

  @Bean
  @Order(1)
  LinkPreviewResolver wikipediaLinkPreviewResolver() {
    return new WikipediaLinkPreviewResolver();
  }

  @Bean
  @Order(2)
  LinkPreviewResolver youTubeLinkPreviewResolver() {
    return new YouTubeLinkPreviewResolver();
  }

  @Bean
  @Order(3)
  LinkPreviewResolver slashdotLinkPreviewResolver() {
    return new SlashdotLinkPreviewResolver();
  }

  @Bean
  @Order(4)
  LinkPreviewResolver imdbLinkPreviewResolver() {
    return new ImdbLinkPreviewResolver();
  }

  @Bean
  @Order(5)
  LinkPreviewResolver rottenTomatoesLinkPreviewResolver() {
    return new RottenTomatoesLinkPreviewResolver();
  }

  @Bean
  @Order(6)
  LinkPreviewResolver xLinkPreviewResolver() {
    return new XLinkPreviewResolver(DEFAULT_MAX_HTML_BYTES);
  }

  @Bean
  @Order(7)
  LinkPreviewResolver instagramLinkPreviewResolver() {
    return new InstagramLinkPreviewResolver(DEFAULT_MAX_HTML_BYTES);
  }

  @Bean
  @Order(8)
  LinkPreviewResolver gitHubLinkPreviewResolver() {
    return new GitHubLinkPreviewResolver();
  }

  @Bean
  @Order(9)
  LinkPreviewResolver redditLinkPreviewResolver() {
    return new RedditLinkPreviewResolver();
  }

  @Bean
  @Order(10)
  LinkPreviewResolver mastodonStatusApiPreviewResolver() {
    return new MastodonStatusApiPreviewResolver();
  }

  @Bean
  @Order(11)
  LinkPreviewResolver oEmbedLinkPreviewResolver() {
    return new OEmbedLinkPreviewResolver(OEmbedLinkPreviewResolver.defaultProviders());
  }

  @Bean
  @Order(12)
  LinkPreviewResolver newsLinkPreviewResolver() {
    return new NewsLinkPreviewResolver(DEFAULT_MAX_HTML_BYTES);
  }

  @Bean
  @Order(13)
  LinkPreviewResolver openGraphLinkPreviewResolver() {
    return new OpenGraphLinkPreviewResolver(DEFAULT_MAX_HTML_BYTES);
  }
}
