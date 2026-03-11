package cafe.woden.ircclient.ui.util;

import java.awt.Font;
import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;

/** Shared helpers for tagging transcript/input runs that should render with emoji images. */
public final class EmojiFontSupport {

  public static final String ATTR_EMOJI_RUN = "ircafe.emoji.run";

  private EmojiFontSupport() {}

  public static Font resolveTranscriptPaneFont(Font preferred) {
    return preferred;
  }

  public static Font resolveTranscriptComponentFont(Font preferred) {
    return preferred;
  }

  public static Font resolveMessageInputFont(Font preferred) {
    return preferred;
  }

  public static void applyEmojiRunFont(MutableAttributeSet attrs) {
    if (attrs == null) {
      return;
    }
    attrs.addAttribute(ATTR_EMOJI_RUN, Boolean.TRUE);
  }

  public static void reapplyEmojiRunFontIfPresent(AttributeSet source, MutableAttributeSet target) {
    if (!isEmojiRun(source) || target == null) {
      return;
    }
    applyEmojiRunFont(target);
  }

  public static boolean isEmojiRun(AttributeSet attrs) {
    return attrs != null && Boolean.TRUE.equals(attrs.getAttribute(ATTR_EMOJI_RUN));
  }
}
