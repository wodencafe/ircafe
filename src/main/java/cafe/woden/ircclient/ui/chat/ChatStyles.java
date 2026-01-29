package cafe.woden.ircclient.ui.chat;

import java.awt.Color;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ChatStyles {

  public static final String ATTR_URL = "chat.url";
  public static final String ATTR_CHANNEL = "chat.channel";
  public static final String ATTR_STYLE = "chat.style";

  public static final String STYLE_TIMESTAMP = "timestamp";
  public static final String STYLE_FROM = "from";
  public static final String STYLE_MESSAGE = "message";
  public static final String STYLE_NOTICE_FROM = "noticeFrom";
  public static final String STYLE_NOTICE_MESSAGE = "noticeMessage";
  public static final String STYLE_STATUS = "status";
  public static final String STYLE_ERROR = "error";
  public static final String STYLE_LINK = "link";
  public static final String STYLE_MENTION = "mention";

  private SimpleAttributeSet tsStyle;
  private SimpleAttributeSet fromStyle;
  private SimpleAttributeSet msgStyle;
  private SimpleAttributeSet noticeFromStyle;
  private SimpleAttributeSet noticeMsgStyle;
  private SimpleAttributeSet statusStyle;
  private SimpleAttributeSet errorStyle;
  private SimpleAttributeSet linkStyle;
  private SimpleAttributeSet mentionStyle;

  public ChatStyles() {
    reload();
  }

  /**
   * Recompute styles from the current Look & Feel defaults.
   */
  public synchronized void reload() {
    Color fg = UIManager.getColor("TextPane.foreground");
    Color dim = UIManager.getColor("Label.disabledForeground");
    Color bg = UIManager.getColor("TextPane.background");

    Color link = UIManager.getColor("Component.linkColor");
    if (link == null) link = UIManager.getColor("Label.foreground");

    Color warn = UIManager.getColor("Component.warningColor");
    Color err = UIManager.getColor("Component.errorColor");
    if (warn == null) warn = new Color(0xF0B000);
    if (err == null) err = new Color(0xD05050);

    // Mention highlight: a subtle background based on selection.
    Color selBg = UIManager.getColor("TextPane.selectionBackground");
    Color mentionBg = selBg != null ? mix(selBg, bg, 0.35) : mix(new Color(0x6AA2FF), bg, 0.20);

    tsStyle = attrs(STYLE_TIMESTAMP, dim, bg, false, false);
    fromStyle = attrs(STYLE_FROM, fg, bg, true, false);
    msgStyle = attrs(STYLE_MESSAGE, fg, bg, false, false);
    noticeFromStyle = attrs(STYLE_NOTICE_FROM, warn, bg, true, false);
    noticeMsgStyle = attrs(STYLE_NOTICE_MESSAGE, warn, bg, false, false);
    statusStyle = attrs(STYLE_STATUS, dim, bg, false, true);
    errorStyle = attrs(STYLE_ERROR, err, bg, true, false);

    linkStyle = attrs(STYLE_LINK, link, bg, false, false);
    StyleConstants.setUnderline(linkStyle, true);

    mentionStyle = attrs(STYLE_MENTION, fg, mentionBg, false, false);
  }

  public AttributeSet timestamp() { return tsStyle; }
  public AttributeSet from() { return fromStyle; }
  public AttributeSet message() { return msgStyle; }
  public AttributeSet noticeFrom() { return noticeFromStyle; }
  public AttributeSet noticeMessage() { return noticeMsgStyle; }
  public AttributeSet status() { return statusStyle; }
  public AttributeSet error() { return errorStyle; }
  public AttributeSet link() { return linkStyle; }
  public AttributeSet mention() { return mentionStyle; }

  public AttributeSet byStyleId(String id) {
    if (id == null) return msgStyle;
    return switch (id) {
      case STYLE_TIMESTAMP -> tsStyle;
      case STYLE_FROM -> fromStyle;
      case STYLE_MESSAGE -> msgStyle;
      case STYLE_NOTICE_FROM -> noticeFromStyle;
      case STYLE_NOTICE_MESSAGE -> noticeMsgStyle;
      case STYLE_STATUS -> statusStyle;
      case STYLE_ERROR -> errorStyle;
      case STYLE_LINK -> linkStyle;
      case STYLE_MENTION -> mentionStyle;
      default -> msgStyle;
    };
  }

  private static SimpleAttributeSet attrs(String styleId, Color fg, Color bg, boolean bold, boolean italic) {
    SimpleAttributeSet a = new SimpleAttributeSet();
    a.addAttribute(ATTR_STYLE, styleId);

    if (fg != null) {
      StyleConstants.setForeground(a, fg);
    }
    if (bg != null) {
      StyleConstants.setBackground(a, bg);
    }

    StyleConstants.setBold(a, bold);
    StyleConstants.setItalic(a, italic);
    return a;
  }

  private static Color mix(Color a, Color b, double aWeight) {
    if (a == null) return b;
    if (b == null) return a;

    double bw = 1.0 - aWeight;
    int r = clamp((int) Math.round(a.getRed() * aWeight + b.getRed() * bw));
    int g = clamp((int) Math.round(a.getGreen() * aWeight + b.getGreen() * bw));
    int bl = clamp((int) Math.round(a.getBlue() * aWeight + b.getBlue() * bw));
    return new Color(r, g, bl);
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }
}
