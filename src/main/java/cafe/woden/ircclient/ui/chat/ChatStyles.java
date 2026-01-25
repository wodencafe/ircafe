package cafe.woden.ircclient.ui.chat;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;

/**
 * Centralized chat styling.
 *
 * <p>Kept as a {@code @Lazy} bean so it is created on the EDT after the Look & Feel
 * is installed (see {@code IrcSwingApp}).
 */
@Component
@Lazy
public class ChatStyles {

  public static final String ATTR_URL = "chat.url";

  private final SimpleAttributeSet tsStyle;
  private final SimpleAttributeSet fromStyle;
  private final SimpleAttributeSet msgStyle;
  private final SimpleAttributeSet noticeFromStyle;
  private final SimpleAttributeSet noticeMsgStyle;
  private final SimpleAttributeSet statusStyle;
  private final SimpleAttributeSet errorStyle;
  private final SimpleAttributeSet linkStyle;
  private final SimpleAttributeSet mentionStyle;

  public ChatStyles() {
    // Styles based on current Look & Feel colors
    Color fg = uiColor("TextPane.foreground", Color.BLACK);
    Color bg = uiColor("TextPane.background", Color.WHITE);
    Color dim = uiColor("Label.disabledForeground", new Color(128, 128, 128));
    Color link = uiColor("Component.linkColor", uiColor("Link.foreground", new Color(0, 102, 204)));
    Color selBg = uiColor(
        "TextPane.selectionBackground",
        uiColor("TextArea.selectionBackground", new Color(60, 120, 200))
    );

    Color noticeFg = blend(fg, dim, 0.60f);
    Color statusFg = blend(fg, dim, 0.35f);
    Color errFg = uiColor("Component.errorForeground", new Color(220, 80, 80));

    // Mention highlight: soften selection background against the chat bg.
    Color mentionBg = blend(selBg, bg, 0.45f);

    tsStyle = attrs(dim, false, false, false, null);
    fromStyle = attrs(fg, true, false, false, null);
    msgStyle = attrs(fg, false, false, false, null);

    noticeFromStyle = attrs(noticeFg, true, false, false, null);
    noticeMsgStyle = attrs(noticeFg, false, true, false, null);

    statusStyle = attrs(statusFg, false, true, false, null);
    errorStyle = attrs(errFg, true, false, false, null);

    linkStyle = attrs(link, false, false, true, null);
    mentionStyle = attrs(fg, true, false, false, mentionBg);
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

  private static Color uiColor(String key, Color fallback) {
    Color c = UIManager.getColor(key);
    return c != null ? c : fallback;
  }

  private static SimpleAttributeSet attrs(Color fg, boolean bold, boolean italic, boolean underline, Color bg) {
    SimpleAttributeSet a = new SimpleAttributeSet();
    if (fg != null) StyleConstants.setForeground(a, fg);
    if (bg != null) StyleConstants.setBackground(a, bg);
    StyleConstants.setBold(a, bold);
    StyleConstants.setItalic(a, italic);
    StyleConstants.setUnderline(a, underline);
    return a;
  }

  /** Linear blend of colors (t=0 -> a, t=1 -> b). */
  private static Color blend(Color a, Color b, float t) {
    if (a == null) return b;
    if (b == null) return a;
    t = Math.max(0f, Math.min(1f, t));
    int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
    int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
    int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
    int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
    return new Color(r, g, bl, al);
  }
}
