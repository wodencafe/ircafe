package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.settings.EmbedCardStyle;
import java.awt.Insets;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import org.junit.jupiter.api.Test;

class ChatLinkPreviewComponentStyleTest {

  @Test
  void buildCardBorderUsesRoundedLineBorder() throws Exception {
    Border border = ChatLinkPreviewComponent.buildCardBorder(EmbedCardStyle.DEFAULT, false);
    CompoundBorder compound = assertInstanceOf(CompoundBorder.class, border);
    LineBorder line = assertInstanceOf(LineBorder.class, compound.getOutsideBorder());
    assertEquals(1, line.getThickness());
    assertTrue(line.getRoundedCorners());
  }

  @Test
  void collapsedBorderUsesTighterVerticalPadding() throws Exception {
    Insets expanded =
        innerInsets(ChatLinkPreviewComponent.buildCardBorder(EmbedCardStyle.DEFAULT, false));
    Insets collapsed =
        innerInsets(ChatLinkPreviewComponent.buildCardBorder(EmbedCardStyle.DEFAULT, true));
    assertEquals(new Insets(10, 12, 10, 12), expanded);
    assertEquals(new Insets(6, 12, 6, 12), collapsed);
  }

  @Test
  void stylePresetsUseDistinctPaddingProfiles() {
    Insets minimal =
        innerInsets(ChatLinkPreviewComponent.buildCardBorder(EmbedCardStyle.MINIMAL, false));
    Insets glassy =
        innerInsets(ChatLinkPreviewComponent.buildCardBorder(EmbedCardStyle.GLASSY, false));
    Insets denser =
        innerInsets(ChatLinkPreviewComponent.buildCardBorder(EmbedCardStyle.DENSER, false));
    assertEquals(new Insets(6, 8, 6, 8), minimal);
    assertEquals(new Insets(12, 14, 12, 14), glassy);
    assertEquals(new Insets(5, 8, 5, 8), denser);
  }

  private static Insets innerInsets(Border border) {
    CompoundBorder compound = assertInstanceOf(CompoundBorder.class, border);
    EmptyBorder inner = assertInstanceOf(EmptyBorder.class, compound.getInsideBorder());
    return inner.getBorderInsets();
  }
}
