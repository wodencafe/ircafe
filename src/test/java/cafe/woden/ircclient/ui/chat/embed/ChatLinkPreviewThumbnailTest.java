package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.reactivex.rxjava3.observers.TestObserver;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatLinkPreviewThumbnailTest {
  @ParameterizedTest
  @ValueSource(strings = {"png", "gif"})
  void cachedThumbnailIsDecodedAndScaledOffEdt(String format) throws Exception {
    BufferedImage source = new BufferedImage(240, 480, BufferedImage.TYPE_INT_RGB);
    var graphics = source.createGraphics();
    try {
      graphics.setColor(Color.ORANGE);
      graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
    } finally {
      graphics.dispose();
    }
    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(source, format, encoded));
    AtomicBoolean completedOnEdt = new AtomicBoolean(true);
    AtomicReference<TestObserver<BufferedImage>> observer = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () ->
            observer.set(
                ChatLinkPreviewComponent.decodeThumbnail(
                        "https://example.org/image." + format, encoded.toByteArray(), 80, 120)
                    .doOnSuccess(
                        image -> completedOnEdt.set(SwingUtilities.isEventDispatchThread()))
                    .test()));

    observer
        .get()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertComplete()
        .assertNoErrors()
        .assertValue(
            image -> {
              assertEquals(60, image.getWidth());
              assertEquals(120, image.getHeight());
              assertEquals(Color.ORANGE.getRGB(), image.getRGB(30, 60));
              return true;
            });
    assertFalse(completedOnEdt.get(), "cached image decoding must leave the EDT");
  }

  @Test
  void malformedThumbnailReportsDecodeFailure() {
    ChatLinkPreviewComponent.decodeThumbnail("https://example.org/broken.png", new byte[0], 80, 120)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(IOException.class)
        .assertNoValues();
  }
}
