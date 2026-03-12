package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import org.junit.jupiter.api.Test;

class EmojiFontSupportTest {

  @Test
  void resolveInputFontPreservesConfiguredFont() {
    Font preferred = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    Font inputFont = EmojiFontSupport.resolveMessageInputFont(preferred);

    assertTrue(preferred.equals(inputFont));
  }

  @Test
  void applyEmojiRunFontMarksAttributeSet() {
    javax.swing.text.SimpleAttributeSet attrs = new javax.swing.text.SimpleAttributeSet();

    EmojiFontSupport.applyEmojiRunFont(attrs);

    assertTrue(EmojiFontSupport.isEmojiRun(attrs));
  }
}
