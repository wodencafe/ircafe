package cafe.woden.ircclient.ui.chat.render;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.model.UserListStore;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.chat.NickColorService;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Inserts styled chat text into a {@link StyledDocument}:
 * <ul>
 *   <li>URL highlighting (clickable via {@link ChatStyles#ATTR_URL})</li>
 *   <li>Mention highlighting using the current nick (per server)</li>
 * </ul>
 */
@Component
@Lazy
public class ChatRichTextRenderer {

  // Links: http(s)://... or www....
  private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+|www\\.\\S+)");

  private final MentionPatternRegistry mentions;
  private final UserListStore userLists;
  private final ChatStyles styles;
  private final NickColorService nickColors;

  public ChatRichTextRenderer(
      MentionPatternRegistry mentions,
      UserListStore userLists,
      ChatStyles styles,
      NickColorService nickColors
  ) {
    this.mentions = mentions;
    this.userLists = userLists;
    this.styles = styles;
    this.nickColors = nickColors;
  }

  /**
   * Inserts text that may contain URLs and mentions.
   */
  public void insertRichText(StyledDocument doc, TargetRef ref, String text, AttributeSet baseStyle)
      throws BadLocationException {
    if (text == null || text.isEmpty()) {
      return;
    }

    AttributeSet base = baseStyle != null ? baseStyle : styles.message();

    // serverId is used for self-mention highlighting.
    String serverId = ref != null ? ref.serverId() : "";

    Matcher m = URL_PATTERN.matcher(text);
    int last = 0;
    while (m.find()) {
      if (m.start() > last) {
        insertWithMentions(doc, ref, serverId, text.substring(last, m.start()), base);
      }

      String raw = m.group(1);
      UrlParts parts = splitUrlTrailingPunct(raw);

      SimpleAttributeSet linkAttr = new SimpleAttributeSet(styles.link());
      linkAttr.addAttribute(ChatStyles.ATTR_URL, normalizeUrl(parts.url));
      doc.insertString(doc.getLength(), parts.url, linkAttr);

      if (!parts.trailing.isEmpty()) {
        insertWithMentions(doc, ref, serverId, parts.trailing, base);
      }

      last = m.end();
    }

    if (last < text.length()) {
      insertWithMentions(doc, ref, serverId, text.substring(last), base);
    }
  }

  private void insertWithMentions(StyledDocument doc, TargetRef ref, String serverId, String text, AttributeSet baseStyle)
      throws BadLocationException {
    if (text == null || text.isEmpty()) return;

    // Only color nick mentions if the nick exists in the channel user list.
    Set<String> channelNicks = Set.of();
    if (ref != null && ref.isChannel() && userLists != null) {
      channelNicks = userLists.getLowerNickSet(ref.serverId(), ref.target());
    }

    String selfLower = mentions != null ? mentions.currentNickLower(serverId) : null;

    int i = 0;
    int len = text.length();
    while (i < len) {
      int start = i;
      while (start < len && !isNickChar(text.charAt(start))) start++;
      if (start > i) {
        doc.insertString(doc.getLength(), text.substring(i, start), baseStyle);
      }
      if (start >= len) break;

      int end = start;
      while (end < len && isNickChar(text.charAt(end))) end++;

      String token = text.substring(start, end);
      String tokenLower = token.toLowerCase(Locale.ROOT);

      boolean isSelf = selfLower != null && tokenLower.equals(selfLower);
      boolean inChannel = channelNicks.contains(tokenLower);

      if (isSelf) {
        // Mention background is always allowed; per-nick foreground only if it's a channel nick.
        SimpleAttributeSet mention = new SimpleAttributeSet(styles.mention());
        // Preserve the base emphasis (e.g. status italic).
        StyleConstants.setBold(mention, StyleConstants.isBold(baseStyle));
        StyleConstants.setItalic(mention, StyleConstants.isItalic(baseStyle));

        if (inChannel && nickColors != null && nickColors.enabled()) {
          mention.addAttribute(NickColorService.ATTR_NICK, tokenLower);
          nickColors.applyColor(mention, tokenLower);
        }

        doc.insertString(doc.getLength(), token, mention);
      } else if (inChannel && nickColors != null && nickColors.enabled()) {
        // Channel nick mention: apply the deterministic nick color on top of the base style.
        SimpleAttributeSet nickStyle = nickColors.forNick(tokenLower, baseStyle);
        doc.insertString(doc.getLength(), token, nickStyle);
      } else {
        doc.insertString(doc.getLength(), token, baseStyle);
      }

      i = end;
    }
  }

  private static boolean isNickChar(char c) {
    return Character.isLetterOrDigit(c)
        || c == '[' || c == ']' || c == '\\' || c == '`'
        || c == '_' || c == '^' || c == '{' || c == '|' || c == '}'
        || c == '-';
  }

  public static String normalizeUrl(String url) {
    if (url == null) return "";
    if (url.startsWith("http://") || url.startsWith("https://")) return url;
    if (url.startsWith("www.")) return "https://" + url;
    return url;
  }

  /**
   * Strip common trailing punctuation that tends to cling to URLs in chat.
   */
  private static UrlParts splitUrlTrailingPunct(String raw) {
    if (raw == null || raw.isEmpty()) return new UrlParts("", "");
    int end = raw.length();
    while (end > 0) {
      char c = raw.charAt(end - 1);
      if (c == '.' || c == ',' || c == ')' || c == ']' || c == '}' || c == '!' || c == '?') {
        end--;
      } else {
        break;
      }
    }
    if (end == raw.length()) return new UrlParts(raw, "");
    return new UrlParts(raw.substring(0, end), raw.substring(end));
  }

  private record UrlParts(String url, String trailing) {}
}
