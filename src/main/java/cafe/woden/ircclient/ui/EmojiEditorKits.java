package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.util.EmojiFontSupport;
import javax.swing.text.Element;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

/** Shared styled-editor kits that substitute emoji image views for emoji-tagged text runs. */
final class EmojiEditorKits {

  private EmojiEditorKits() {}

  static StyledEditorKit wrapping() {
    return new EmojiStyledEditorKit(true);
  }

  static StyledEditorKit singleLine() {
    return new EmojiStyledEditorKit(false);
  }

  private static final class EmojiStyledEditorKit extends StyledEditorKit {
    private final ViewFactory factory;

    private EmojiStyledEditorKit(boolean wrapParagraphs) {
      this.factory = new EmojiViewFactory(super.getViewFactory(), wrapParagraphs);
    }

    @Override
    public ViewFactory getViewFactory() {
      return factory;
    }
  }

  private static final class EmojiViewFactory implements ViewFactory {
    private final ViewFactory delegate;
    private final boolean wrapParagraphs;

    private EmojiViewFactory(ViewFactory delegate, boolean wrapParagraphs) {
      this.delegate = delegate;
      this.wrapParagraphs = wrapParagraphs;
    }

    @Override
    public View create(Element elem) {
      if (EmojiFontSupport.isEmojiRun(elem.getAttributes())) {
        return new EmojiInlineView(elem);
      }

      View view = delegate.create(elem);
      if (wrapParagraphs && view instanceof ParagraphView) {
        return new ParagraphView(elem) {
          @Override
          public float getMinimumSpan(int axis) {
            if (axis == View.X_AXIS) {
              return 0;
            }
            return super.getMinimumSpan(axis);
          }
        };
      }
      if (view instanceof LabelView) {
        return view;
      }
      return view;
    }
  }
}
