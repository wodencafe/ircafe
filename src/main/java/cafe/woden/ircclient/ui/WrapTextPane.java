package cafe.woden.ircclient.ui;

import javax.swing.*;
import javax.swing.SwingUtilities;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.*;

/** JTextPane that word/line wraps when placed inside a JScrollPane. */
public class WrapTextPane extends JTextPane {

  public WrapTextPane() {
    // Enable breaking long tokens (URLs, hashes) so text never forces horizontal scrolling.
    setEditorKit(new WrapEditorKit());

    // Important: prevent Swing's caret logic from auto-scrolling the viewport when the document changes.
    // Some LAF/UI delegate updates will reinstall the caret, so we re-apply this policy in updateUI/addNotify.
    setAutoscrolls(false);
    ensureNonAutoScrollingCaret();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    // UI delegates can replace the caret during updateUI(); re-apply after the delegate settles.
    SwingUtilities.invokeLater(this::ensureNonAutoScrollingCaret);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    // Some platforms install the UI delegate/caret during realization.
    SwingUtilities.invokeLater(this::ensureNonAutoScrollingCaret);
  }

  private void ensureNonAutoScrollingCaret() {
    // If the caret is not a DefaultCaret (or gets replaced), install a DefaultCaret with NEVER_UPDATE.
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

  private static final class WrapEditorKit extends StyledEditorKit {
    private final ViewFactory factory = new WrapViewFactory(super.getViewFactory());

    @Override
    public ViewFactory getViewFactory() {
      return factory;
    }
  }

  private static final class WrapViewFactory implements ViewFactory {
    private final ViewFactory delegate;

    private WrapViewFactory(ViewFactory delegate) {
      this.delegate = delegate;
    }

    @Override
    public View create(Element elem) {
      View v = delegate.create(elem);

      // ParagraphView drives line breaking. Lowering minimum span allows breaking long "words".
      if (v instanceof ParagraphView) {
        return new ParagraphView(elem) {
          @Override
          public float getMinimumSpan(int axis) {
            if (axis == View.X_AXIS) return 0;
            return super.getMinimumSpan(axis);
          }
        };
      }

      return v;
    }
  }
}
