package cafe.woden.ircclient.ui;

import java.awt.Dimension;
import java.util.Objects;
import javax.swing.JTextPane;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Single-line styled text input that uses the emoji-aware editor kit. */
public final class SingleLineEmojiTextPane extends JTextPane {

  public SingleLineEmojiTextPane() {
    setEditorKit(EmojiEditorKits.singleLine());
    if (getDocument() instanceof AbstractDocument doc) {
      doc.setDocumentFilter(new SingleLineDocumentFilter());
    }
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return false;
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension preferred = getPreferredSize();
    return new Dimension(0, preferred != null ? preferred.height : 0);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension preferred = super.getPreferredSize();
    java.awt.FontMetrics metrics = getFontMetrics(getFont());
    int minHeight = metrics.getHeight() + getInsets().top + getInsets().bottom;
    preferred.height = Math.max(preferred.height, minHeight);
    return preferred;
  }

  private static final class SingleLineDocumentFilter extends DocumentFilter {
    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
        throws BadLocationException {
      String normalized = normalize(string);
      if (!normalized.isEmpty()) {
        super.insertString(fb, offset, normalized, attr);
      }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
        throws BadLocationException {
      super.replace(fb, offset, length, normalize(text), attrs);
    }

    private static String normalize(String text) {
      String raw = Objects.toString(text, "");
      if (raw.isEmpty()) {
        return raw;
      }
      return raw.replace("\r\n", " ")
          .replace('\r', ' ')
          .replace('\n', ' ')
          .replace('\u2028', ' ')
          .replace('\u2029', ' ');
    }
  }
}
