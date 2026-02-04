package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Appends inline image previews to a transcript {@link StyledDocument}.
 */
@Component
@Lazy
public class ChatImageEmbedder {

  private final UiSettingsBus uiSettings;
  private final ChatStyles styles;
  private final ImageFetchService fetch;

  private final java.util.Map<StyledDocument, DocState> perDocState =
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

  public ChatImageEmbedder(UiSettingsBus uiSettings, ChatStyles styles, ImageFetchService fetch) {
    this.uiSettings = uiSettings;
    this.styles = styles;
    this.fetch = fetch;
  }

  private DocState stateFor(StyledDocument doc) {
    return perDocState.computeIfAbsent(doc, d -> new DocState());
  }

  private static final class DocState {
    long nextSeq = 0;
    final GifAnimationCoordinator gifCoordinator = new GifAnimationCoordinator();
  }

  /**
   * Scan the message text for direct image URLs and append a preview block for each.
   *
   * <p>Must be called on the Swing EDT (the caller in IRCafe already runs on EDT).
   */
  public void appendEmbeds(StyledDocument doc, String messageText) {
    if (doc == null) return;

    DocState st = stateFor(doc);
    for (String url : ImageUrlExtractor.extractImageUrls(messageText)) {
      try {
        long seq = st.nextSeq++;

        // If it looks like a GIF by URL, proactively hint to stop older GIFs immediately.
        // If the decode later shows it's NOT a GIF, the component will call rejectGifHint(seq).
        if (ImageDecodeUtil.looksLikeGif(url, null)) {
          st.gifCoordinator.hintNewGifPlaceholder(seq);
        }

        // Insert a component as a single "character" in the styled document.
        ChatImageComponent comp = new ChatImageComponent(
            url,
            fetch,
            uiSettings.get().imageEmbedsCollapsedByDefault(),
            uiSettings,
            st.gifCoordinator,
            seq);

        SimpleAttributeSet a = new SimpleAttributeSet(styles.message());
        a.addAttribute(ChatStyles.ATTR_URL, url);
        // Mark it as a "message" style so restyling keeps it consistent.
        a.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_MESSAGE);
        StyleConstants.setComponent(a, comp);

        doc.insertString(doc.getLength(), " ", a);
        doc.insertString(doc.getLength(), "\n", styles.timestamp());
      } catch (Exception ignored) {
        // best-effort
      }
    }
  }
}
