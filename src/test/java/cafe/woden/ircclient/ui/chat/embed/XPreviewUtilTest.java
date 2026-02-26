package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

class XPreviewUtilTest {

  @Test
  void extractsStatusIdFromCanonicalAndWebPaths() {
    assertEquals("12345", XPreviewUtil.extractStatusId("https://x.com/alice/status/12345"));
    assertEquals("67890", XPreviewUtil.extractStatusId("https://x.com/i/web/status/67890"));
    assertEquals(
        "24680", XPreviewUtil.extractStatusId("https://twitter.com/bob/status/24680/photo/1"));
  }

  @Test
  void rejectsNonXHostsAndNonNumericStatusIds() {
    assertNull(XPreviewUtil.extractStatusId("https://example.com/alice/status/123"));
    assertNull(XPreviewUtil.extractStatusId("https://x.com/alice/status/not-a-number"));
  }

  @Test
  void recognizesXAndProxyLikeHosts() {
    assertTrue(XPreviewUtil.isXLikeHost("x.com"));
    assertTrue(XPreviewUtil.isXLikeHost("twitter.com"));
    assertTrue(XPreviewUtil.isXLikeHost("fixupx.com"));
    assertTrue(XPreviewUtil.isXLikeHost("nitter.net"));
  }

  @Test
  void buildsSyndicationAndOembedApiUris() {
    URI syndication = XPreviewUtil.syndicationApiUri("12345");
    URI oembed = XPreviewUtil.oEmbedApiUri("12345");

    assertNotNull(syndication);
    assertNotNull(oembed);
    assertTrue(syndication.toString().contains("tweet-result?id=12345"));
    assertTrue(syndication.toString().contains("lang=en"));
    assertTrue(oembed.toString().contains("publish.x.com/oembed?url="));
  }

  @Test
  void parsesOembedJsonIntoLinkPreview() {
    String json =
        """
        {
          "author_name":"Alice",
          "author_url":"https://x.com/alice",
          "thumbnail_url":"https://cdn.example/thumb.jpg",
          "html":"<blockquote><p>Hello from X preview</p></blockquote>"
        }
        """;

    LinkPreview preview =
        XPreviewUtil.parseOEmbedJson(json, URI.create("https://x.com/alice/status/12345"), "12345");

    assertNotNull(preview);
    assertEquals("Alice (@alice)", preview.title());
    assertEquals("X", preview.siteName());
    assertEquals("https://cdn.example/thumb.jpg", preview.imageUrl());
    assertTrue(preview.description().contains("Hello from X preview"));
  }
}
