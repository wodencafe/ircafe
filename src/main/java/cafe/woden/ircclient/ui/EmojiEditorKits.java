package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.util.EmojiFontSupport;
import java.awt.Component;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
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
      if (StyleConstants.getComponent(elem.getAttributes())
          instanceof DocumentComponentProvider factory) {
        return new ComponentView(elem) {
          @Override
          protected Component createComponent() {
            return factory.createComponent();
          }
        };
      }
      if (EmojiFontSupport.isEmojiRun(elem.getAttributes())) {
        return new EmojiInlineView(elem);
      }

      View view = delegate.create(elem);
      if (!wrapParagraphs && view instanceof ParagraphView) {
        return new ParagraphView(elem) {
          @Override
          public int getFlowSpan(int index) {
            // Caret/popup queries can lay out a newly edited draft before its viewport grows.
            // Never wrap at that stale width: the compose field scrolls horizontally.
            return Integer.MAX_VALUE;
          }
        };
      }
      if (wrapParagraphs && view instanceof ParagraphView) {
        return new ReplyBlockParagraphView(elem);
      }
      if (view instanceof LabelView) {
        return view;
      }
      return view;
    }
  }
}
