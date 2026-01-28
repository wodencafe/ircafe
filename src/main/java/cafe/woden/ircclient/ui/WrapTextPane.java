package cafe.woden.ircclient.ui;

import javax.swing.*;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.*;

/**
 * JTextPane that word/line wraps when placed inside a JScrollPane.
 *
 * <p>Also allows wrapping long "words" (e.g., URLs) by lowering the paragraph's
 * minimum X span to 0, so content never forces horizontal growth.</p>
 */
public class WrapTextPane extends JTextPane {

  public WrapTextPane() {
    // Enable breaking long tokens (URLs, hashes) so text never forces horizontal scrolling.
    setEditorKit(new WrapEditorKit());
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

  /**
   * StyledEditorKit that installs a ViewFactory which allows long tokens to wrap.
   */
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
