package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Insets;
import java.lang.reflect.Method;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import org.junit.jupiter.api.Test;

class ChatLinkPreviewComponentStyleTest {

  @Test
  void buildCardBorderUsesRoundedLineBorder() throws Exception {
    Border border = invokeBuildCardBorder(false);
    CompoundBorder compound = assertInstanceOf(CompoundBorder.class, border);
    LineBorder line = assertInstanceOf(LineBorder.class, compound.getOutsideBorder());
    assertEquals(1, line.getThickness());
    assertTrue(line.getRoundedCorners());
  }

  @Test
  void collapsedBorderUsesTighterVerticalPadding() throws Exception {
    Insets expanded = innerInsets(invokeBuildCardBorder(false));
    Insets collapsed = innerInsets(invokeBuildCardBorder(true));
    assertEquals(new Insets(10, 12, 10, 12), expanded);
    assertEquals(new Insets(6, 12, 6, 12), collapsed);
  }

  private static Border invokeBuildCardBorder(boolean collapsed) throws Exception {
    Method method =
        ChatLinkPreviewComponent.class.getDeclaredMethod("buildCardBorder", boolean.class);
    method.setAccessible(true);
    return (Border) method.invoke(null, collapsed);
  }

  private static Insets innerInsets(Border border) {
    CompoundBorder compound = assertInstanceOf(CompoundBorder.class, border);
    EmptyBorder inner = assertInstanceOf(EmptyBorder.class, compound.getInsideBorder());
    return inner.getBorderInsets();
  }
}
