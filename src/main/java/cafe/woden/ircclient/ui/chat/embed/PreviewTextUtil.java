package cafe.woden.ircclient.ui.chat.embed;

import java.awt.Dimension;
import javax.swing.JTextArea;

final class PreviewTextUtil {

  private PreviewTextUtil() {}

  static String trimToSentence(String text, int maxChars) {
    if (text == null) return null;
    String t = text.strip();
    if (t.isEmpty()) return t;
    if (t.length() <= maxChars) return t;

    int hard = Math.min(Math.max(0, maxChars), t.length());
    String prefix = t.substring(0, hard);
    String snapped = snapToNiceBoundary(prefix);
    if (snapped.isBlank()) snapped = prefix.strip();
    return snapped + " …";
  }

  static String clampToLines(String fullText, JTextArea template, int widthPx, int maxLines) {
    if (template == null) return fullText;
    if (fullText == null) return null;

    String t = fullText.strip();
    if (t.isEmpty()) return t;

    JTextArea m = new JTextArea();
    m.setFont(template.getFont());
    m.setLineWrap(template.getLineWrap());
    m.setWrapStyleWord(template.getWrapStyleWord());
    m.setBorder(template.getBorder());

    int lineH = m.getFontMetrics(m.getFont()).getHeight();
    int maxH = lineH * Math.max(1, maxLines) + 4;

    if (fitsHeight(m, t, widthPx, maxH)) return t;

    int lo = 0;
    int hi = t.length();
    String best = "";

    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      String prefix = t.substring(0, mid).strip();
      if (prefix.isEmpty()) {
        lo = mid + 1;
        continue;
      }

      String snapped = snapToNiceBoundary(prefix);
      if (snapped.isBlank()) snapped = prefix;
      String candidate = snapped.strip() + " …";

      if (fitsHeight(m, candidate, widthPx, maxH)) {
        best = candidate;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }

    if (best.isBlank()) {
      // Very narrow widths can make even small strings wrap; just hard cut.
      best = trimToSentence(t, Math.max(60, Math.min(t.length(), 140)));
      if (!best.endsWith(" …")) best = best.strip() + " …";
    }

    return best;
  }

  private static boolean fitsHeight(JTextArea m, String text, int widthPx, int maxH) {
    m.setText(text);
    m.setSize(new Dimension(Math.max(1, widthPx), Short.MAX_VALUE));
    return m.getPreferredSize().height <= maxH;
  }

  /** Trim to a reasonable boundary near the end of the text. */
  private static String snapToNiceBoundary(String text) {
    if (text == null) return null;
    String t = text.strip();
    if (t.isEmpty()) return t;

    int hard = t.length();
    int lookBack = Math.max(0, hard - 220);
    for (int i = hard - 1; i >= lookBack; i--) {
      char c = t.charAt(i);
      if (c == '.' || c == '!' || c == '?') {
        char prev = i > 0 ? t.charAt(i - 1) : '\0';
        char next = (i + 1) < t.length() ? t.charAt(i + 1) : '\0';
        if (c == '.' && Character.isUpperCase(prev) && Character.isUpperCase(next)) {
          continue;
        }
        return t.substring(0, i + 1).strip();
      }
    }
    int ws = t.lastIndexOf(' ', hard - 1);
    if (ws >= Math.max(40, hard - 120)) {
      return t.substring(0, ws).strip();
    }

    return t;
  }
}
