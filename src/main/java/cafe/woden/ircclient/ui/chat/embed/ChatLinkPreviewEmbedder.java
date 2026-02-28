package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final EmbedLoadPolicyMatcher policyMatcher;

  public record AppendResult(int appendedCount, List<String> blockedUrls) {
    static AppendResult empty() {
      return new AppendResult(0, List.of());
    }
  }

  private record InsertResult(boolean appended, int nextInsertAt, String blockedUrl) {}

  public ChatLinkPreviewEmbedder(
      UiSettingsBus uiSettings,
      ChatStyles styles,
      LinkPreviewFetchService fetch,
      ImageFetchService imageFetch,
      EmbedLoadPolicyMatcher policyMatcher) {
    this.uiSettings = uiSettings;
    this.styles = styles;
    this.fetch = fetch;
    this.imageFetch = imageFetch;
    this.policyMatcher = policyMatcher;
  }

  public AppendResult appendPreviews(
      TargetRef ctx,
      StyledDocument doc,
      String messageText,
      String fromNick,
      Map<String, String> ircv3Tags) {
    if (doc == null || messageText == null || messageText.isBlank()) return AppendResult.empty();
    if (!uiSettings.get().linkPreviewsEnabled()) return AppendResult.empty();

    String serverId = (ctx != null) ? ctx.serverId() : null;
    List<String> urls = LinkUrlExtractor.extractUrls(messageText);
    if (urls.isEmpty()) return AppendResult.empty();

    int count = 0;
    int insertAt = doc.getLength();
    LinkedHashSet<String> blocked = new LinkedHashSet<>();
    for (String url : urls) {
      if (count >= MAX_PREVIEWS_PER_MESSAGE) break;
      try {
        InsertResult result =
            insertPreview(
                ctx, doc, fromNick, ircv3Tags, serverId, url, false, Math.max(0, insertAt));
        if (result.appended()) {
          count++;
          insertAt = result.nextInsertAt();
        } else if (result.blockedUrl() != null && !result.blockedUrl().isBlank()) {
          blocked.add(result.blockedUrl());
        }
      } catch (Exception ignored) {
      }
    }
    if (blocked.isEmpty()) {
      return new AppendResult(count, List.of());
    }
    return new AppendResult(count, List.copyOf(blocked));
  }

  public boolean insertPreviewForUrlAt(
      TargetRef ctx, StyledDocument doc, String rawUrl, int insertAt) {
    if (doc == null) return false;
    String serverId = (ctx != null) ? ctx.serverId() : null;
    try {
      InsertResult result =
          insertPreview(ctx, doc, "", Map.of(), serverId, rawUrl, true, Math.max(0, insertAt));
      return result.appended();
    } catch (Exception ignored) {
      return false;
    }
  }

  private InsertResult insertPreview(
      TargetRef ctx,
      StyledDocument doc,
      String fromNick,
      Map<String, String> ircv3Tags,
      String serverId,
      String rawUrl,
      boolean bypassPolicy,
      int insertAt) {
    String url = Objects.toString(rawUrl, "").trim();
    if (doc == null || url.isEmpty()) {
      return new InsertResult(false, Math.max(0, insertAt), null);
    }
    if (!bypassPolicy
        && policyMatcher != null
        && !policyMatcher.allow(ctx, fromNick, ircv3Tags, url)) {
      return new InsertResult(false, Math.max(0, insertAt), url);
    }

    ChatLinkPreviewComponent comp =
        new ChatLinkPreviewComponent(
            serverId, url, fetch, imageFetch, uiSettings.get().linkPreviewsCollapsedByDefault());

    SimpleAttributeSet a = new SimpleAttributeSet(styles.message());
    a.addAttribute(ChatStyles.ATTR_URL, url);
    a.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_MESSAGE);
    StyleConstants.setComponent(a, comp);

    int pos = Math.max(0, Math.min(insertAt, doc.getLength()));
    try {
      doc.insertString(pos, " ", a);
      pos += 1;
      doc.insertString(pos, "\n", styles.timestamp());
      pos += 1;
      return new InsertResult(true, pos, null);
    } catch (Exception ignored) {
      return new InsertResult(false, Math.max(0, insertAt), null);
    }
  }
}
