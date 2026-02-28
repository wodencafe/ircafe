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

/** Appends inline image previews to a transcript {@link StyledDocument}. */
@Component
@Lazy
public class ChatImageEmbedder {

  private final UiSettingsBus uiSettings;
  private final ChatStyles styles;
  private final ImageFetchService fetch;
  private final EmbedLoadPolicyMatcher policyMatcher;

  private final java.util.Map<StyledDocument, DocState> perDocState =
      java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

  public ChatImageEmbedder(
      UiSettingsBus uiSettings,
      ChatStyles styles,
      ImageFetchService fetch,
      EmbedLoadPolicyMatcher policyMatcher) {
    this.uiSettings = uiSettings;
    this.styles = styles;
    this.fetch = fetch;
    this.policyMatcher = policyMatcher;
  }

  private DocState stateFor(StyledDocument doc) {
    return perDocState.computeIfAbsent(doc, d -> new DocState());
  }

  private static final class DocState {
    long nextSeq = 0;
    final GifAnimationCoordinator gifCoordinator = new GifAnimationCoordinator();
  }

  public record AppendResult(int appendedCount, List<String> blockedUrls) {
    static AppendResult empty() {
      return new AppendResult(0, List.of());
    }
  }

  private record InsertResult(boolean appended, int nextInsertAt, String blockedUrl) {}

  /**
   * Scan the message text for direct image URLs and append a preview block for each.
   *
   * <p>Must be called on the Swing EDT (the caller in IRCafe already runs on EDT).
   */
  public AppendResult appendEmbeds(
      TargetRef ctx,
      StyledDocument doc,
      String messageText,
      String fromNick,
      Map<String, String> ircv3Tags) {
    if (doc == null) return AppendResult.empty();
    String serverId = (ctx != null) ? ctx.serverId() : null;

    DocState st = stateFor(doc);
    int insertAt = doc.getLength();
    int appendedCount = 0;
    LinkedHashSet<String> blocked = new LinkedHashSet<>();
    for (String url : ImageUrlExtractor.extractImageUrls(messageText)) {
      try {
        InsertResult result =
            insertEmbed(
                ctx, doc, fromNick, ircv3Tags, serverId, st, url, false, Math.max(0, insertAt));
        if (result.appended()) {
          appendedCount++;
          insertAt = result.nextInsertAt();
        } else if (result.blockedUrl() != null && !result.blockedUrl().isBlank()) {
          blocked.add(result.blockedUrl());
        }
      } catch (Exception ignored) {
        // best-effort
      }
    }
    if (blocked.isEmpty()) {
      return new AppendResult(appendedCount, List.of());
    }
    return new AppendResult(appendedCount, List.copyOf(blocked));
  }

  public boolean insertEmbedForUrlAt(TargetRef ctx, StyledDocument doc, String url, int insertAt) {
    if (doc == null) return false;
    String serverId = (ctx != null) ? ctx.serverId() : null;
    DocState st = stateFor(doc);
    try {
      InsertResult result =
          insertEmbed(ctx, doc, "", Map.of(), serverId, st, url, true, Math.max(0, insertAt));
      return result.appended();
    } catch (Exception ignored) {
      return false;
    }
  }

  private InsertResult insertEmbed(
      TargetRef ctx,
      StyledDocument doc,
      String fromNick,
      Map<String, String> ircv3Tags,
      String serverId,
      DocState st,
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

    long seq = st.nextSeq++;

    // If it looks like a GIF by URL, proactively hint to stop older GIFs immediately.
    // If the decode later shows it's NOT a GIF, the component will call rejectGifHint(seq).
    if (ImageDecodeUtil.looksLikeGif(url, null)) {
      st.gifCoordinator.hintNewGifPlaceholder(seq);
    }

    ChatImageComponent comp =
        new ChatImageComponent(
            serverId,
            url,
            fetch,
            uiSettings.get().imageEmbedsCollapsedByDefault(),
            uiSettings,
            st.gifCoordinator,
            seq);

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
