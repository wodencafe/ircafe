package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class ChatTranscriptColorSupportTest {

  @Test
  void parseHexColorAcceptsCssAndHexPrefixes() {
    assertEquals(new Color(0x11_22_33), ChatTranscriptColorSupport.parseHexColor("#112233"));
    assertEquals(new Color(0x44_55_66), ChatTranscriptColorSupport.parseHexColor("0x445566"));
    assertEquals(new Color(0x77_88_99), ChatTranscriptColorSupport.parseHexColor("778899"));
    assertNull(ChatTranscriptColorSupport.parseHexColor("#123"));
    assertNull(ChatTranscriptColorSupport.parseHexColor("not-a-color"));
    assertNull(ChatTranscriptColorSupport.parseHexColor(null));
  }

  @Test
  void blendTowardRespectsWeightEndpointsAndNullInputs() {
    Color target = new Color(0x10, 0x20, 0x30);
    Color source = new Color(0xA0, 0xB0, 0xC0);

    assertEquals(target, ChatTranscriptColorSupport.blendToward(target, source, 0.0));
    assertEquals(source, ChatTranscriptColorSupport.blendToward(target, source, 1.0));
    assertEquals(source, ChatTranscriptColorSupport.blendToward(null, source, 0.5));
    assertEquals(target, ChatTranscriptColorSupport.blendToward(target, null, 0.5));
  }

  @Test
  void contrastUtilitiesMatchExpectedBlackWhiteBehavior() {
    assertEquals(21.0, ChatTranscriptColorSupport.contrastRatio(Color.BLACK, Color.WHITE), 0.01);
    assertEquals(0.0, ChatTranscriptColorSupport.relativeLuminance(Color.BLACK), 0.0001);
    assertEquals(1.0, ChatTranscriptColorSupport.relativeLuminance(Color.WHITE), 0.0001);
    assertEquals(
        Color.BLACK,
        ChatTranscriptColorSupport.bestTextColorForBackground(new Color(245, 245, 245)));
    assertEquals(
        Color.WHITE, ChatTranscriptColorSupport.bestTextColorForBackground(new Color(20, 20, 20)));
  }
}
