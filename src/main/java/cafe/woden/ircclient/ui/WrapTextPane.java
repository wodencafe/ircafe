package cafe.woden.ircclient.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.SwingUtilities;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;

/** JTextPane that word/line wraps when placed inside a JScrollPane. */
public class WrapTextPane extends JTextPane {

  public WrapTextPane() {
    setEditorKit(EmojiEditorKits.wrapping());

    setAutoscrolls(false);
    ensureNonAutoScrollingCaret();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    SwingUtilities.invokeLater(this::ensureNonAutoScrollingCaret);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    SwingUtilities.invokeLater(this::ensureNonAutoScrollingCaret);
  }

  private void ensureNonAutoScrollingCaret() {
    Caret c = getCaret();
    if (c instanceof DefaultCaret dc) {
      if (dc.getUpdatePolicy() != DefaultCaret.NEVER_UPDATE) {
        dc.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
      }
      return;
    }

    DefaultCaret dc = new DefaultCaret();
    dc.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
    dc.setBlinkRate(0);
    setCaret(dc);
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    // Always track the viewport width to keep wrapping correct during resize.
    return true;
  }

  @Override
  public void setSize(Dimension d) {
    // Force the view width to match the viewport, so shrinking the window re-wraps properly.
    if (getParent() instanceof JViewport viewport) {
      int w = viewport.getWidth();
      if (w > 0) {
        d = new Dimension(w, d.height);
      }
    }
    super.setSize(d);
  }
}
