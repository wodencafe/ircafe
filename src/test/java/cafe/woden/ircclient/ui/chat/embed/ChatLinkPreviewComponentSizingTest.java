package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatLinkPreviewComponentSizingTest {

  @Test
  void thumbnailWidthUsesRequestedWidthWhenNoConfiguredCap() {
    assertEquals(320, ChatLinkPreviewComponent.effectiveThumbnailMaxWidth(320, 0));
  }

  @Test
  void thumbnailWidthHonorsConfiguredCap() {
    assertEquals(280, ChatLinkPreviewComponent.effectiveThumbnailMaxWidth(640, 280));
  }

  @Test
  void thumbnailHeightNormalizesNegativeCapToZero() {
    assertEquals(0, ChatLinkPreviewComponent.effectiveThumbnailMaxHeight(-10));
  }

  @Test
  void extendedCardWidthHonorsConfiguredCapWithCardChrome() {
    assertEquals(348, ChatLinkPreviewComponent.effectiveExtendedCardMaxWidth(900, 320));
  }

  @Test
  void extendedCardWidthStaysUnchangedWhenCapDisabled() {
    assertEquals(900, ChatLinkPreviewComponent.effectiveExtendedCardMaxWidth(900, 0));
  }
}
