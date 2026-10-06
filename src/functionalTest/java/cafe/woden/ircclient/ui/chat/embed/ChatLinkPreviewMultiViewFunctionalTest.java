package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.WrapTextPane;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreview;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptRestyleSupport;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.subjects.SingleSubject;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(15)
class ChatLinkPreviewMultiViewFunctionalTest {
  private static final String URL = "https://www.bbc.com/news/articles/example";
  private static final String TITLE = "Shared news article";

  @ParameterizedTest
  @CsvSource({"false, false", "true, false", "false, true", "true, true"})
  void bothDocksKeepTheirPreviewWhenSharingATranscript(
      boolean openExtraDockFirst, boolean manualPreview) throws Exception {
    SingleSubject<LinkPreview> download = SingleSubject.create();
    LinkPreviewFetchService fetch = mock(LinkPreviewFetchService.class);
    when(fetch.fetch("server", URL)).thenReturn(download);
    UiSettings settings = mock(UiSettings.class);
    when(settings.linkPreviewsEnabled()).thenReturn(true);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(settings);
    ChatStyles styles = new ChatStyles(null);
    ChatLinkPreviewEmbedder embedder =
        new ChatLinkPreviewEmbedder(
            settingsBus,
            fetch,
            null,
            null,
            null,
            new EmbedDocumentApplicationService(styles),
            null);
    DefaultStyledDocument doc = new DefaultStyledDocument();
    AtomicReference<DockViews> views = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () ->
            views.set(
                new DockViews(
                    new JPanel(new GridLayout(1, 2)), new WrapTextPane(), new WrapTextPane())));
    JPanel docks = views.get().docks();
    WrapTextPane main = views.get().main();
    WrapTextPane extra = views.get().extra();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            main.setDocument(doc);
            docks.add(main);
            docks.setSize(1000, 600);
            docks.addNotify();
            if (openExtraDockFirst) {
              extra.setDocument(doc);
              docks.add(extra);
            }
            TargetRef target = new TargetRef("server", "#news");
            if (manualPreview) {
              assertTrue(embedder.insertPreviewForUrlAt(target, doc, URL, 0));
            } else {
              assertEquals(
                  1, embedder.appendPreviews(target, doc, URL, "alice", Map.of()).appendedCount());
            }
            paint(docks);
            if (!openExtraDockFirst) {
              extra.setDocument(doc);
              docks.add(extra);
              paint(docks);
            }
          });
      download.onSuccess(new LinkPreview(URL, TITLE, "Article summary", "BBC News", null, 1));
      SwingUtilities.invokeAndWait(
          () -> {
            paint(docks);
            assertEquals(1, previewsIn(main).size(), "main dock must retain its preview");
            assertEquals(1, previewsIn(extra).size(), "extra dock must have its own preview");
            assertNotSame(previewsIn(main).getFirst(), previewsIn(extra).getFirst());
            assertTrue(hasTitle(main));
            assertTrue(hasTitle(extra));

            // Rebuilding one pane's views must not take the other pane's component.
            extra.setDocument(new DefaultStyledDocument());
            paint(docks);
            assertEquals(1, previewsIn(main).size());
            extra.setDocument(doc);
            paint(docks);
            assertEquals(1, previewsIn(main).size());
            assertEquals(1, previewsIn(extra).size());

            ChatTranscriptRestyleSupport.restyleDocument(
                new ChatTranscriptRestyleSupport.Context(styles, null, (fresh, action) -> {}),
                doc,
                false,
                null);
            paint(docks);
            assertEquals(1, previewsIn(main).size(), "restyling must preserve the main preview");
            assertEquals(1, previewsIn(extra).size(), "restyling must preserve the extra preview");
          });
    } finally {
      SwingUtilities.invokeAndWait(docks::removeNotify);
    }
  }

  private record DockViews(JPanel docks, WrapTextPane main, WrapTextPane extra) {}

  private static List<ChatLinkPreviewComponent> previewsIn(Container container) {
    List<ChatLinkPreviewComponent> previews = new ArrayList<>();
    for (Component child : container.getComponents()) {
      if (child instanceof ChatLinkPreviewComponent preview) previews.add(preview);
      else if (child instanceof Container nested) previews.addAll(previewsIn(nested));
    }
    return previews;
  }

  private static boolean hasTitle(Container container) {
    for (Component child : container.getComponents()) {
      if (child instanceof JTextArea text && TITLE.equals(text.getText())) return true;
      if (child instanceof Container nested && hasTitle(nested)) return true;
    }
    return false;
  }

  private static void paint(JPanel docks) {
    docks.doLayout();
    BufferedImage image = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      docks.paint(graphics);
    } finally {
      graphics.dispose();
    }
  }
}
