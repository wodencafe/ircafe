package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class ImgurPreviewUtilTest {

  @Test
  void parsePostDocumentExtractsMetadataFromPostJson() {
    String html =
        """
        <html><head>
          <meta property="og:url" content="https://imgur.com/gallery/AbCdE">
          <meta property="og:title" content="Desk setup - Imgur">
          <meta property="og:image" content="https://i.imgur.com/fallback.jpg">
          <script>
            window.postDataJSON = {
              "hash": "AbCdE",
              "title": "Desk setup",
              "description": "Evening coding session with coffee and charts.",
              "account_url": "alice",
              "datetime": 1771228800,
              "type": "image/jpeg",
              "link": "https://i.imgur.com/AbCdE.jpg"
            };
          </script>
        </head><body></body></html>
        """;

    var doc = Jsoup.parse(html, "https://imgur.com/gallery/AbCdE");
    LinkPreview preview =
        ImgurPreviewUtil.parsePostDocument(doc, "https://imgur.com/gallery/AbCdE");

    assertNotNull(preview);
    assertEquals("Imgur", preview.siteName());
    assertEquals("Desk setup", preview.title());
    assertEquals("https://i.imgur.com/AbCdE.jpg", preview.imageUrl());
    assertEquals(1, preview.mediaCount());
    assertNotNull(preview.description());
    assertTrue(preview.description().contains("Submitter: alice"), preview.description());
    assertTrue(preview.description().contains("Date: "), preview.description());
    assertTrue(preview.description().contains("Summary:\n"), preview.description());
    assertTrue(preview.description().contains("Evening coding session"), preview.description());
  }

  @Test
  void parsePostDocumentUsesLdJsonAndFiltersBoilerplateCaption() {
    String html =
        """
        <html><head>
          <meta property="og:url" content="https://imgur.com/a/QwErT">
          <meta property="og:title" content="Imgur: The magic of the Internet">
          <meta property="og:description" content="Discover the magic of the internet at Imgur.">
          <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@type": "SocialMediaPosting",
              "author": { "@type": "Person", "name": "Bob" },
              "datePublished": "2026-02-16",
              "description": "Launch night gallery with friends.",
              "image": { "@type": "ImageObject", "url": "https://i.imgur.com/QwErT.png" }
            }
          </script>
        </head><body></body></html>
        """;

    var doc = Jsoup.parse(html, "https://imgur.com/a/QwErT");
    LinkPreview preview = ImgurPreviewUtil.parsePostDocument(doc, "https://imgur.com/a/QwErT");

    assertNotNull(preview);
    assertEquals("Imgur", preview.siteName());
    assertEquals("Imgur post", preview.title());
    assertEquals("https://i.imgur.com/QwErT.png", preview.imageUrl());
    assertNotNull(preview.description());
    assertTrue(preview.description().contains("Submitter: Bob"), preview.description());
    assertTrue(preview.description().contains("Date: 2026-02-16"), preview.description());
    assertTrue(preview.description().contains("Launch night gallery"), preview.description());
    assertFalse(
        preview.description().toLowerCase().contains("discover the magic of the internet"),
        preview.description());
  }

  @Test
  void isImgurUriMatchesSupportedPostPaths() {
    assertTrue(ImgurPreviewUtil.isImgurUri(URI.create("https://imgur.com/gallery/AbCdE")));
    assertTrue(ImgurPreviewUtil.isImgurUri(URI.create("https://imgur.com/a/AbCdE")));
    assertTrue(ImgurPreviewUtil.isImgurUri(URI.create("https://imgur.com/AbCdE")));
    assertFalse(ImgurPreviewUtil.isImgurUri(URI.create("https://imgur.com/upload")));
    assertFalse(ImgurPreviewUtil.isImgurUri(URI.create("https://i.imgur.com/AbCdE.jpg")));
  }
}
