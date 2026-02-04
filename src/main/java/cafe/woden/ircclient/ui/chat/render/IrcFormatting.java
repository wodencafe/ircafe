package cafe.woden.ircclient.ui.chat.render;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * Minimal mIRC formatting parser (bold/italic/underline/reverse/color/reset).
 */
public final class IrcFormatting {

  private IrcFormatting() {}

  /** mIRC 16-color palette (0-15). */
  private static final Color[] MIRCCOLORS = new Color[] {
      new Color(0xFFFFFF), // 0 white
      new Color(0x000000), // 1 black
      new Color(0x00007F), // 2 navy
      new Color(0x009300), // 3 green
      new Color(0xFF0000), // 4 red
      new Color(0x7F0000), // 5 maroon
      new Color(0x9C009C), // 6 purple
      new Color(0xFC7F00), // 7 orange
      new Color(0xFFFF00), // 8 yellow
      new Color(0x00FC00), // 9 light green
      new Color(0x009393), // 10 teal
      new Color(0x00FFFF), // 11 light cyan
      new Color(0x0000FC), // 12 light blue
      new Color(0xFF00FF), // 13 pink
      new Color(0x7F7F7F), // 14 gray
      new Color(0xD2D2D2)  // 15 light gray
  };

  /** A parsed span of plain text with derived attributes (base style + mIRC styling). */
  public record Span(String text, AttributeSet style) {}

  /** Parse {@code input} into spans, stripping mIRC control codes. */
  public static List<Span> parse(String input, AttributeSet baseStyle) {
    String s = input == null ? "" : input;
    if (s.isEmpty()) return List.of();

    AttributeSet base = baseStyle == null ? new SimpleAttributeSet() : baseStyle;

    boolean defBold = StyleConstants.isBold(base);
    boolean defItalic = StyleConstants.isItalic(base);
    boolean defUnderline = StyleConstants.isUnderline(base);

    boolean bold = defBold;
    boolean italic = defItalic;
    boolean underline = defUnderline;
    boolean reverse = false;
    Integer fg = null;
    Integer bg = null;

    ArrayList<Span> out = new ArrayList<>();
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case 0x02 -> { // bold
          flush(out, buf, base, bold, italic, underline, reverse, fg, bg);
          bold = !bold;
        }
        case 0x1D -> { // italic
          flush(out, buf, base, bold, italic, underline, reverse, fg, bg);
          italic = !italic;
        }
        case 0x1F -> { // underline
          flush(out, buf, base, bold, italic, underline, reverse, fg, bg);
          underline = !underline;
        }
        case 0x16 -> { // reverse
          flush(out, buf, base, bold, italic, underline, reverse, fg, bg);
          reverse = !reverse;
        }
        case 0x0F -> { // reset
          flush(out, buf, base, bold, italic, underline, reverse, fg, bg);
          bold = defBold;
          italic = defItalic;
          underline = defUnderline;
          reverse = false;
          fg = null;
          bg = null;
        }
        case 0x03 -> { // color
          flush(out, buf, base, bold, italic, underline, reverse, fg, bg);
          // Parse up to 2 digits for fg, and optional ,bg.
          int j = i + 1;
          Integer parsedFg = null;
          Integer parsedBg = null;

          int[] fgRes = parse1or2Digits(s, j);
          if (fgRes[0] >= 0) {
            parsedFg = fgRes[0];
            j = fgRes[1];

            if (j < s.length() && s.charAt(j) == ',') {
              j++;
              int[] bgRes = parse1or2Digits(s, j);
              if (bgRes[0] >= 0) {
                parsedBg = bgRes[0];
                j = bgRes[1];
              }
            }
          }

          if (parsedFg == null) {
            // Bare \u0003 resets colors.
            fg = null;
            bg = null;
          } else {
            fg = (parsedFg >= 0 && parsedFg <= 15) ? parsedFg : null;
            bg = (parsedBg != null && parsedBg >= 0 && parsedBg <= 15) ? parsedBg : null;
          }

          // Skip the digits we consumed.
          i = j - 1;
        }
        default -> buf.append(c);
      }
    }

    flush(out, buf, base, bold, italic, underline, reverse, fg, bg);
    return List.copyOf(out);
  }



  private static void flush(
      List<Span> out,
      StringBuilder buf,
      AttributeSet base,
      boolean bold,
      boolean italic,
      boolean underline,
      boolean reverse,
      Integer fg,
      Integer bg) {
    if (buf.length() == 0) return;

    SimpleAttributeSet attrs = new SimpleAttributeSet(base);

    // Style flags.
    StyleConstants.setBold(attrs, bold);
    StyleConstants.setItalic(attrs, italic);
    StyleConstants.setUnderline(attrs, underline);

    // Store metadata so we can restyle later (theme switches) without losing IRC intent.
    attrs.addAttribute(ChatStyles.ATTR_IRC_BOLD, bold);
    attrs.addAttribute(ChatStyles.ATTR_IRC_ITALIC, italic);
    attrs.addAttribute(ChatStyles.ATTR_IRC_UNDERLINE, underline);
    attrs.addAttribute(ChatStyles.ATTR_IRC_REVERSE, reverse);
    if (fg != null) attrs.addAttribute(ChatStyles.ATTR_IRC_FG, fg);
    if (bg != null) attrs.addAttribute(ChatStyles.ATTR_IRC_BG, bg);

    Color baseFg = StyleConstants.getForeground(attrs);
    Color baseBg = StyleConstants.getBackground(attrs);

    Color fgColor = (fg != null) ? colorForCode(fg) : null;
    Color bgColor = (bg != null) ? colorForCode(bg) : null;

    Color finalFg = fgColor != null ? fgColor : baseFg;
    Color finalBg = bgColor != null ? bgColor : baseBg;

    if (reverse) {
      Color tmp = finalFg;
      finalFg = finalBg;
      finalBg = tmp;
    }

    if (finalFg != null) StyleConstants.setForeground(attrs, finalFg);
    if (finalBg != null) StyleConstants.setBackground(attrs, finalBg);

    out.add(new Span(buf.toString(), attrs));
    buf.setLength(0);
  }

  private static int[] parse1or2Digits(String s, int start) {
    if (start >= s.length()) return new int[] {-1, start};
    char a = s.charAt(start);
    if (a < '0' || a > '9') return new int[] {-1, start};

    int val = a - '0';
    int idx = start + 1;
    if (idx < s.length()) {
      char b = s.charAt(idx);
      if (b >= '0' && b <= '9') {
        val = (val * 10) + (b - '0');
        idx++;
      }
    }
    return new int[] {val, idx};
  }

  public static Color colorForCode(Integer code) {
    if (code == null) return null;
    int c = code;
    if (c < 0 || c >= MIRCCOLORS.length) return null;
    return MIRCCOLORS[c];
  }
}
