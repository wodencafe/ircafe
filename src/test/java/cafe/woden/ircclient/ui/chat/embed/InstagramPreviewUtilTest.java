package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class InstagramPreviewUtilTest {

  @Test
  void parsePostDocumentExtractsNestedAuthorDateCaptionAndImage() {
    String html =
        """
        <html><head>
          <meta property="og:url" content="https://www.instagram.com/p/AbCdEf123/">
          <meta property="og:title" content="A post shared by Alice (@alice) on Instagram">
          <meta property="og:description" content="A post shared by Alice (@alice) on Instagram: \\"Sunset walk\\"">
          <meta property="og:image" content="https://cdninstagram.com/fallback.jpg">
          <script type="application/ld+json">
            {
              "@context": "https://schema.org",
              "@type": "SocialMediaPosting",
              "author": {
                "@type": "Person",
                "name": "Alice (@alice)",
                "alternateName": "@alice"
              },
              "datePublished": "2025-08-15",
              "articleBody": "Sunset walk by the water",
              "image": {
                "@type": "ImageObject",
                "url": "https://cdninstagram.com/photo.jpg"
              }
            }
          </script>
        </head><body></body></html>
        """;

    var doc = Jsoup.parse(html, "https://www.instagram.com/p/AbCdEf123/");
    LinkPreview preview =
        InstagramPreviewUtil.parsePostDocument(doc, "https://www.instagram.com/p/AbCdEf123/");

    assertNotNull(preview);
    assertEquals("Instagram", preview.siteName());
    assertEquals("Instagram post by @alice", preview.title());
    assertEquals("https://cdninstagram.com/photo.jpg", preview.imageUrl());
    assertEquals(1, preview.mediaCount());
    assertNotNull(preview.description());
    assertTrue(preview.description().contains("Author: @alice"));
    assertTrue(preview.description().contains("Date: 2025-08-15"));
    assertTrue(preview.description().contains("Summary:\n"));
    assertTrue(preview.description().contains("Sunset walk by the water"));
  }

  @Test
  void parsePostDocumentHandlesImageArrayShape() {
    String html =
        """
        <html><head>
          <meta property="og:url" content="https://www.instagram.com/p/QwErTy789/">
          <script type="application/ld+json">
            {
              "@type": "SocialMediaPosting",
              "author": { "alternateName": "@alice" },
              "image": [
                { "url": "https://cdninstagram.com/array-image.jpg" }
              ]
            }
          </script>
        </head><body></body></html>
        """;

    var doc = Jsoup.parse(html, "https://www.instagram.com/p/QwErTy789/");
    LinkPreview preview =
        InstagramPreviewUtil.parsePostDocument(doc, "https://www.instagram.com/p/QwErTy789/");

    assertNotNull(preview);
    assertEquals("https://cdninstagram.com/array-image.jpg", preview.imageUrl());
  }

  @Test
  void isInstagramPostUriMatchesSupportedPaths() {
    assertTrue(
        InstagramPreviewUtil.isInstagramPostUri(
            URI.create("https://www.instagram.com/p/AbCdEf123/")));
    assertTrue(
        InstagramPreviewUtil.isInstagramPostUri(
            URI.create("https://instagram.com/reel/AbCdEf123")));
    assertTrue(
        InstagramPreviewUtil.isInstagramPostUri(
            URI.create("https://www.instagram.com/tv/AbCdEf123/")));
    assertFalse(
        InstagramPreviewUtil.isInstagramPostUri(URI.create("https://www.instagram.com/explore/")));
  }

  @Test
  void parsePostDocumentExtractsFromInlinePostJsonWhenLdJsonMissing() {
    String html =
        """
        <html><head>
          <meta property="og:url" content="https://www.instagram.com/p/ZxYwVu987/">
          <meta property="og:title" content="Instagram">
          <script type="application/json">
            {
              "xdt_shortcode_media": {
                "owner": { "username": "zoe" },
                "taken_at_timestamp": 1723800000,
                "display_url": "https://scontent.cdninstagram.com/media.jpg",
                "edge_media_to_caption": {
                  "edges": [
                    { "node": { "text": "Coffee and code." } }
                  ]
                }
              }
            }
          </script>
        </head><body></body></html>
        """;

    var doc = Jsoup.parse(html, "https://www.instagram.com/p/ZxYwVu987/");
    LinkPreview preview =
        InstagramPreviewUtil.parsePostDocument(doc, "https://www.instagram.com/p/ZxYwVu987/");

    assertNotNull(preview);
    assertEquals("Instagram", preview.siteName());
    assertEquals("Instagram post by @zoe", preview.title());
    assertEquals("https://scontent.cdninstagram.com/media.jpg", preview.imageUrl());
    assertNotNull(preview.description());
    assertTrue(preview.description().contains("Author: @zoe"));
    assertTrue(preview.description().contains("Date: "));
    assertTrue(preview.description().contains("Summary:\n"));
    assertTrue(preview.description().contains("Coffee and code."));
  }

  @Test
  void parsePostDocumentFallsBackToOgDescriptionForAuthorAndCaption() {
    String html =
        """
        <html><head>
          <meta property="og:url" content="https://www.instagram.com/p/FaLlBaCk123/">
          <meta property="og:title" content="Instagram">
          <meta property="og:description" content="1,234 likes, 56 comments - memehouse on Instagram: &quot;Trust data, verify data&quot;">
          <meta property="og:image" content="https://scontent.cdninstagram.com/fallback.jpg">
        </head><body></body></html>
        """;

    var doc = Jsoup.parse(html, "https://www.instagram.com/p/FaLlBaCk123/");
    LinkPreview preview =
        InstagramPreviewUtil.parsePostDocument(doc, "https://www.instagram.com/p/FaLlBaCk123/");

    assertNotNull(preview);
    assertEquals("Instagram post by @memehouse", preview.title());
    assertEquals("https://scontent.cdninstagram.com/fallback.jpg", preview.imageUrl());
    assertNotNull(preview.description());
    assertTrue(preview.description().contains("Author: @memehouse"));
    assertTrue(preview.description().contains("Summary:\n"));
    assertTrue(preview.description().contains("Trust data, verify data"), preview.description());
  }

  @Test
  void parsePostDocumentPrefersLargestDisplayResourceOverThumbnail() {
    String html =
        """
        <html><head>
          <meta property="og:url" content="https://www.instagram.com/p/LArGe123/">
          <meta property="og:title" content="Instagram">
          <script type="application/json">
            {
              "xdt_shortcode_media": {
                "owner": { "username": "zoe" },
                "thumbnail_src": "https://cdninstagram.com/thumb-square.jpg",
                "display_resources": [
                  { "src": "https://cdninstagram.com/small.jpg", "config_width": 320, "config_height": 320 },
                  { "src": "https://cdninstagram.com/large.jpg", "config_width": 1080, "config_height": 1350 }
                ],
                "edge_media_to_caption": {
                  "edges": [
                    { "node": { "text": "Sentence one. Sentence two. Sentence three. Sentence four." } }
                  ]
                }
              }
            }
          </script>
        </head><body></body></html>
        """;

    var doc = Jsoup.parse(html, "https://www.instagram.com/p/LArGe123/");
    LinkPreview preview =
        InstagramPreviewUtil.parsePostDocument(doc, "https://www.instagram.com/p/LArGe123/");

    assertNotNull(preview);
    assertEquals("https://cdninstagram.com/large.jpg", preview.imageUrl());
    assertNotNull(preview.description());
    assertTrue(preview.description().contains("Sentence one. Sentence two. Sentence three."));
  }
}
