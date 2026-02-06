package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.List;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ChatLinkPreviewEmbedder {

  private static final int MAX_PREVIEWS_PER_MESSAGE = 1;

  private final UiSettingsBus uiSettings;
  private final ChatStyles styles;
  private final LinkPreviewFetchService fetch;
  private final ImageFetchService imageFetch;

  public ChatLinkPreviewEmbedder(
      UiSettingsBus uiSettings,
      ChatStyles styles,
      LinkPreviewFetchService fetch,
      ImageFetchService imageFetch
  ) {
    this.uiSettings = uiSettings;
    this.styles = styles;
    this.fetch = fetch;
    this.imageFetch = imageFetch;
  }

  public void appendPreviews(TargetRef ctx, StyledDocument doc, String messageText) {
    if (doc == null || messageText == null || messageText.isBlank()) return;
    if (!uiSettings.get().linkPreviewsEnabled()) return;

    String serverId = (ctx != null) ? ctx.serverId() : null;
    List<String> urls = LinkUrlExtractor.extractUrls(messageText);
    if (urls.isEmpty()) return;

    int count = 0;
    for (String url : urls) {
      if (count >= MAX_PREVIEWS_PER_MESSAGE) break;
      try {
        ChatLinkPreviewComponent comp = new ChatLinkPreviewComponent(serverId, url, fetch, imageFetch, uiSettings.get().linkPreviewsCollapsedByDefault());

        SimpleAttributeSet a = new SimpleAttributeSet(styles.message());
        a.addAttribute(ChatStyles.ATTR_URL, url);
        a.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_MESSAGE);
        StyleConstants.setComponent(a, comp);

        doc.insertString(doc.getLength(), " ", a);
        doc.insertString(doc.getLength(), "\n", styles.timestamp());
        count++;
      } catch (Exception ignored) {
      }
    }
  }
}
