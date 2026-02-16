package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class NewsPreviewUtilTest {

  @Test
  void parseArticleDocumentExtractsMetadataAndExtendedSummary() {
    String html = """
        <html><head>
          <meta property="og:url" content="https://abcnews.com/GMA/Culture/robert-duvall-star-godfather-apocalypse-now-dead-95/story?id=96490943">
          <meta property="og:site_name" content="ABC News">
          <meta property="og:title" content="Robert Duvall, star of The Godfather and Apocalypse Now, dead at 95 - ABC News">
          <meta property="og:description" content="A short fallback description that should be replaced by article paragraphs.">
          <meta property="og:image" content="https://cdn.example.com/duvall.jpg">
          <meta property="article:published_time" content="2026-02-15T19:22:00Z">
          <meta name="author" content="By Jane Reporter">
        </head>
        <body>
          <article>
            <p>Robert Duvall, the acclaimed actor known for roles in The Godfather and Apocalypse Now, has died at 95 after a long career spanning decades in film and television.</p>
            <p>His performances earned multiple Academy Award nominations and inspired generations of actors, directors, and writers across the industry.</p>
            <p>Tributes from colleagues and fans have poured in, praising his intensity, range, and unmistakable screen presence.</p>
          </article>
        </body></html>
        """;

    var doc = Jsoup.parse(html, "https://abcnews.com/GMA/Culture/robert-duvall-star-godfather-apocalypse-now-dead-95/story?id=96490943");
    LinkPreview preview = NewsPreviewUtil.parseArticleDocument(
        doc,
        "https://abcnews.com/GMA/Culture/robert-duvall-star-godfather-apocalypse-now-dead-95/story?id=96490943"
    );

    assertNotNull(preview);
    assertEquals("ABC News", preview.siteName());
    assertEquals("https://cdn.example.com/duvall.jpg", preview.imageUrl());
    assertNotNull(preview.title());
    assertTrue(preview.title().contains("Robert Duvall"), preview.title());
    assertNotNull(preview.description());
    assertTrue(preview.description().contains("Author: Jane Reporter"), preview.description());
    assertTrue(preview.description().contains("Date: 2026-02-15"), preview.description());
    assertTrue(preview.description().contains("Summary:\n"), preview.description());
    assertTrue(preview.description().contains("acclaimed actor"), preview.description());
    assertTrue(preview.description().contains("Tributes"), preview.description());
  }

  @Test
  void parseArticleDocumentReturnsNullForNonNewsPage() {
    String html = """
        <html><head>
          <title>About Example</title>
          <meta name="description" content="Static informational page.">
        </head><body><main><p>Welcome to our company website.</p></main></body></html>
        """;

    var doc = Jsoup.parse(html, "https://example.com/about");
    LinkPreview preview = NewsPreviewUtil.parseArticleDocument(doc, "https://example.com/about");
    assertNull(preview);
  }

  @Test
  void heuristicsRecognizeNewsUrlAndStructuredDescription() {
    assertTrue(NewsPreviewUtil.isLikelyNewsArticleUrl(
        "https://abcnews.com/GMA/Culture/robert-duvall-star-godfather-apocalypse-now-dead-95/story?id=96490943"));
    assertTrue(NewsPreviewUtil.looksLikeNewsDescription(
        "Author: Jane Reporter\nDate: 2026-02-15\n\nSummary:\nParagraph one. Paragraph two."));
    assertFalse(NewsPreviewUtil.looksLikeNewsDescription("Just a plain short description"));
  }

  @Test
  void heuristicsRecognizeRequestedPublishers() {
    List<String> urls = List.of(
        "https://www.reuters.com/world/europe/some-big-story-2026-02-16/",
        "https://apnews.com/article/economy-federal-reserve-rates-abc1234567",
        "https://www.nytimes.com/2026/02/16/us/politics/sample-story.html",
        "https://www.bbc.com/news/world-us-canada-12345678",
        "https://www.cnn.com/2026/02/16/politics/sample-story/index.html",
        "https://www.washingtonpost.com/politics/2026/02/16/sample-story/",
        "https://www.theguardian.com/world/2026/feb/16/sample-story",
        "https://www.npr.org/2026/02/16/1234567890/sample-story",
        "https://www.wsj.com/world/sample-story-1234567890"
    );

    for (String url : urls) {
      assertTrue(NewsPreviewUtil.isLikelyNewsArticleUrl(url), url);
    }
  }

  @Test
  void parseArticleDocumentSupportsRequestedPublishers() {
    record Case(String key, String url, String siteName, String expectedPublisher, String author, String bodyHtml) {}

    List<Case> cases = List.of(
        new Case(
            "reuters",
            "https://www.reuters.com/world/europe/some-big-story-2026-02-16/",
            "Reuters",
            "Reuters",
            "Alice Smith",
            "<article data-testid='Body'><p>First Reuters paragraph with context and detail for the story development.</p>"
                + "<p>Second Reuters paragraph adds more reporting depth and confirms the event timeline.</p></article>"
        ),
        new Case(
            "ap",
            "https://apnews.com/article/economy-federal-reserve-rates-abc1234567",
            "AP News",
            "AP News",
            "Bob Jones",
            "<div class='RichTextStoryBody'><p>First AP paragraph describing the lead and regional impact.</p>"
                + "<p>Second AP paragraph provides officials, numbers, and next steps for readers.</p></div>"
        ),
        new Case(
            "nyt",
            "https://www.nytimes.com/2026/02/16/us/politics/sample-story.html",
            "The New York Times",
            "New York Times",
            "Cara Lee",
            "<section name='articleBody'><p>First NYT paragraph frames the issue with reporting context.</p>"
                + "<p>Second NYT paragraph extends analysis and adds on-the-record statements.</p></section>"
        ),
        new Case(
            "bbc",
            "https://www.bbc.com/news/world-us-canada-12345678",
            "BBC News",
            "BBC",
            "Dan Roe",
            "<div data-component='text-block'><p>First BBC paragraph outlines what happened and why it matters.</p></div>"
                + "<div data-component='text-block'><p>Second BBC paragraph adds reaction and operational details.</p></div>"
        ),
        new Case(
            "cnn",
            "https://www.cnn.com/2026/02/16/politics/sample-story/index.html",
            "CNN",
            "CNN",
            "Eli Fox",
            "<div class='article__content'><p>First CNN paragraph introduces the story with key participants.</p>"
                + "<p>Second CNN paragraph expands with chronology and evidence from interviews.</p></div>"
        ),
        new Case(
            "wapo",
            "https://www.washingtonpost.com/politics/2026/02/16/sample-story/",
            "The Washington Post",
            "Washington Post",
            "Fran Holt",
            "<div data-qa='article-body'><p>First Washington Post paragraph covers the event and public response.</p>"
                + "<p>Second Washington Post paragraph includes analysis from officials and experts.</p></div>"
        ),
        new Case(
            "guardian",
            "https://www.theguardian.com/world/2026/feb/16/sample-story",
            "The Guardian",
            "The Guardian",
            "Gail Kim",
            "<div data-gu-name='body'><p>First Guardian paragraph explains the background and immediate stakes.</p>"
                + "<p>Second Guardian paragraph adds context from local and international sources.</p></div>"
        ),
        new Case(
            "npr",
            "https://www.npr.org/2026/02/16/1234567890/sample-story",
            "NPR",
            "NPR",
            "Hank Li",
            "<div class='storytext'><p>First NPR paragraph sets the scene with concise reporting context.</p>"
                + "<p>Second NPR paragraph provides quotes, implications, and forward-looking detail.</p></div>"
        ),
        new Case(
            "wsj",
            "https://www.wsj.com/world/sample-story-1234567890",
            "The Wall Street Journal",
            "Wall Street Journal",
            "Ivy Moss",
            "<div data-module='ArticleBody'><p>First WSJ paragraph introduces the economic and policy consequences.</p>"
                + "<p>Second WSJ paragraph adds market reaction and timeline detail for readers.</p></div>"
        )
    );

    for (Case c : cases) {
      String html = """
          <html><head>
            <meta property="og:url" content="%s">
            <meta property="og:site_name" content="%s">
            <meta property="og:title" content="Sample headline for %s - %s">
            <meta property="og:image" content="https://cdn.example/%s.jpg">
            <meta property="article:published_time" content="2026-02-16T10:22:00Z">
            <meta name="author" content="By %s">
          </head><body>%s</body></html>
          """.formatted(c.url(), c.siteName(), c.expectedPublisher(), c.siteName(), c.key(), c.author(), c.bodyHtml());

      var doc = Jsoup.parse(html, c.url());
      LinkPreview preview = NewsPreviewUtil.parseArticleDocument(doc, c.url());

      assertNotNull(preview, c.url());
      assertEquals(c.expectedPublisher(), preview.siteName(), c.url());
      assertEquals("https://cdn.example/%s.jpg".formatted(c.key()), preview.imageUrl(), c.url());
      assertNotNull(preview.description(), c.url());
      assertTrue(preview.description().contains("Author: " + c.author()), preview.description());
      assertTrue(preview.description().contains("Date: 2026-02-16"), preview.description());
      assertTrue(preview.description().contains("Publisher: " + c.expectedPublisher()), preview.description());
      assertTrue(preview.description().contains("Summary:\n"), preview.description());
      assertTrue(preview.description().contains("Second "), preview.description());
    }
  }
}
