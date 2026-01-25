package cafe.woden.ircclient.ui.chat.render;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private final ChatStyles styles;

  public ChatRichTextRenderer(MentionPatternRegistry mentions, ChatStyles styles) {
    this.mentions = mentions;
    this.styles = styles;
  }

  /**
   * Inserts text that may contain URLs and mentions.
   */
  public void insertRichText(StyledDocument doc, String serverId, String text, AttributeSet baseStyle)
      throws BadLocationException {
    if (text == null || text.isEmpty()) {
      return;
    }

    Matcher m = URL_PATTERN.matcher(text);
    int last = 0;
    while (m.find()) {
      if (m.start() > last) {
        insertWithMentions(doc, serverId, text.substring(last, m.start()), baseStyle);
      }

      String raw = m.group(1);
      UrlParts parts = splitUrlTrailingPunct(raw);

      SimpleAttributeSet linkAttr = new SimpleAttributeSet(styles.link());
      linkAttr.addAttribute(ChatStyles.ATTR_URL, normalizeUrl(parts.url));
      doc.insertString(doc.getLength(), parts.url, linkAttr);

      if (!parts.trailing.isEmpty()) {
        insertWithMentions(doc, serverId, parts.trailing, baseStyle);
      }

      last = m.end();
    }

    if (last < text.length()) {
      insertWithMentions(doc, serverId, text.substring(last), baseStyle);
    }
  }

  private void insertWithMentions(StyledDocument doc, String serverId, String text, AttributeSet baseStyle)
      throws BadLocationException {
    if (text == null || text.isEmpty()) return;

    Pattern p = mentions.get(serverId);
    if (p == null) {
      doc.insertString(doc.getLength(), text, baseStyle);
      return;
    }

    Matcher mm = p.matcher(text);
    int last = 0;
    while (mm.find()) {
      if (mm.start() > last) {
        doc.insertString(doc.getLength(), text.substring(last, mm.start()), baseStyle);
      }

      String hit = mm.group();
      doc.insertString(doc.getLength(), hit, styles.mention());

      last = mm.end();
    }

    if (last < text.length()) {
      doc.insertString(doc.getLength(), text.substring(last), baseStyle);
    }
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
