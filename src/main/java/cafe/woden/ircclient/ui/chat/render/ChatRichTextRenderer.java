package cafe.woden.ircclient.ui.chat.render;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.model.UserListStore;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.chat.NickColorService;
import java.awt.Color;
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

    // First, parse and strip mIRC formatting codes, producing spans that already include
    // the appropriate StyleConstants and metadata attributes.
    for (IrcFormatting.Span span : IrcFormatting.parse(text, base)) {
      insertRichTextPlain(doc, ref, serverId, span.text(), span.style());
    }
  }

  /**
   * Inserts text that may contain URLs and mentions (no mIRC control codes expected).
   */
  private void insertRichTextPlain(StyledDocument doc, TargetRef ref, String serverId, String text, AttributeSet base)
      throws BadLocationException {
    if (text == null || text.isEmpty()) return;

    Matcher m = URL_PATTERN.matcher(text);
    int last = 0;
    while (m.find()) {
      if (m.start() > last) {
        insertWithMentions(doc, ref, serverId, text.substring(last, m.start()), base);
      }

      String raw = m.group(1);
      UrlParts parts = splitUrlTrailingPunct(raw);

      // Start from the base style so mIRC formatting (bold/italic/colors) is preserved.
      SimpleAttributeSet linkAttr = new SimpleAttributeSet(base);
      linkAttr.addAttributes(styles.link());
      linkAttr.addAttribute(ChatStyles.ATTR_URL, normalizeUrl(parts.url));

      // If this segment has explicit mIRC colors, keep them (don't let the theme link color override).
      if (hasIrcColors(base)) {
        Color fg = StyleConstants.getForeground(base);
        Color bg = StyleConstants.getBackground(base);
        if (fg != null) StyleConstants.setForeground(linkAttr, fg);
        if (bg != null) StyleConstants.setBackground(linkAttr, bg);
      }

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
      char c = text.charAt(i);

      // Channel token: #something
      if (c == '#') {
        ChannelParts chan = tryParseChannel(text, i);
        if (chan != null) {
          SimpleAttributeSet chanAttr = new SimpleAttributeSet(baseStyle);
          chanAttr.addAttributes(styles.link());
          chanAttr.addAttribute(ChatStyles.ATTR_CHANNEL, chan.channel);

          if (hasIrcColors(baseStyle)) {
            Color fg = StyleConstants.getForeground(baseStyle);
            Color bg = StyleConstants.getBackground(baseStyle);
            if (fg != null) StyleConstants.setForeground(chanAttr, fg);
            if (bg != null) StyleConstants.setBackground(chanAttr, bg);
          }

          doc.insertString(doc.getLength(), chan.channel, chanAttr);
          if (!chan.trailing.isEmpty()) {
            doc.insertString(doc.getLength(), chan.trailing, baseStyle);
          }
          i = chan.nextIndex;
          continue;
        }
      }

      // Nick/mention token
      if (isNickChar(c)) {
        int start = i;
        int end = start;
        while (end < len && isNickChar(text.charAt(end))) end++;

        String token = text.substring(start, end);
        String tokenLower = token.toLowerCase(Locale.ROOT);

        boolean isSelf = selfLower != null && tokenLower.equals(selfLower);
        boolean inChannel = channelNicks.contains(tokenLower);

        if (isSelf) {
          // Mention background is always allowed; per-nick foreground only if it's a channel nick.
          SimpleAttributeSet mention = new SimpleAttributeSet(baseStyle);
          mention.addAttributes(styles.mention());

          if (!hasIrcColors(baseStyle) && inChannel && nickColors != null && nickColors.enabled()) {
            mention.addAttribute(NickColorService.ATTR_NICK, tokenLower);
            nickColors.applyColor(mention, tokenLower);
          }

          doc.insertString(doc.getLength(), token, mention);
        } else if (!hasIrcColors(baseStyle) && inChannel && nickColors != null && nickColors.enabled()) {
          // Channel nick mention: apply the deterministic nick color on top of the base style.
          SimpleAttributeSet nickStyle = nickColors.forNick(tokenLower, baseStyle);
          doc.insertString(doc.getLength(), token, nickStyle);
        } else {
          doc.insertString(doc.getLength(), token, baseStyle);
        }

        i = end;
        continue;
      }

      // Plain text chunk until next interesting char.
      int start = i;
      int end = start + 1;
      while (end < len) {
        char d = text.charAt(end);
        if (d == '#' || isNickChar(d)) break;
        end++;
      }
      doc.insertString(doc.getLength(), text.substring(start, end), baseStyle);
      i = end;
    }
  }

  private static boolean hasIrcColors(AttributeSet attrs) {
    if (attrs == null) return false;
    return attrs.getAttribute(ChatStyles.ATTR_IRC_FG) != null
        || attrs.getAttribute(ChatStyles.ATTR_IRC_BG) != null
        || Boolean.TRUE.equals(attrs.getAttribute(ChatStyles.ATTR_IRC_REVERSE));
  }

  private static boolean isNickChar(char c) {
    return Character.isLetterOrDigit(c)
        || c == '[' || c == ']' || c == '\\' || c == '`'
        || c == '_' || c == '^' || c == '{' || c == '|' || c == '}'
        || c == '-';
  }

  /**
   * IRC channel names start with '#'. The RFC rule is permissive: the rest of the name can contain
   * anything except spaces, commas, or ASCII bell (\u0007). We also trim common trailing punctuation
   * so "#chan," links as "#chan".
   */
  private static ChannelParts tryParseChannel(String text, int at) {
    if (text == null) return null;
    int len = text.length();
    if (at < 0 || at >= len) return null;
    if (text.charAt(at) != '#') return null;

    int j = at + 1;
    if (j >= len) return null;

    // Parse until a delimiter.
    while (j < len) {
      char c = text.charAt(j);
      if (isChannelDelimiter(c)) break;
      j++;
    }

    if (j <= at + 1) return null; // just "#"

    String raw = text.substring(at, j);

    // Strip common trailing punctuation.
    int end = raw.length();
    while (end > 1) {
      char c = raw.charAt(end - 1);
      if (c == '.' || c == ',' || c == ')' || c == ']' || c == '}' || c == '!' || c == '?' || c == ':' || c == ';') {
        end--;
      } else {
        break;
      }
    }

    String channel = raw.substring(0, end);
    String trailing = raw.substring(end);

    // If we stripped everything but '#', bail.
    if (channel.length() <= 1) return null;

    return new ChannelParts(channel, trailing, j);
  }

  private static boolean isChannelDelimiter(char c) {
    // Stop on whitespace, commas, ASCII bell, or NUL.
    // Also stop on any other control char to avoid weird escape/control sequences being treated as a channel.
    return Character.isWhitespace(c)
        || Character.isISOControl(c)
        || c == ','
        || c == '\u0007'
        || c == '\u0000';
  }

  private record ChannelParts(String channel, String trailing, int nextIndex) {}

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
