package cafe.woden.ircclient.ui.chat.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.ui.chat.embed.spi.LinkPreview;
import cafe.woden.ircclient.ui.settings.EmbedCardStyle;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatLinkPreviewThumbnailFunctionalTest {
  @ParameterizedTest
  @ValueSource(strings = {"https://example.org/post", "https://www.instagram.com/p/example/"})
  void thumbnailRespectsMediaCapsAndUpdatesComponentOnEdt(String url) throws Exception {
    String imageUrl = "https://example.org/photo.png";
    LinkPreviewFetchService previews = mock(LinkPreviewFetchService.class);
    ImageFetchService images = mock(ImageFetchService.class);
    SingleSubject<byte[]> download = SingleSubject.create();
    when(previews.fetch("server", url))
        .thenReturn(
            Single.just(new LinkPreview(url, "A title", "Description", "Site", imageUrl, 1)));
    when(images.fetch("server", imageUrl)).thenReturn(download);
    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
    assertTrue(
        ImageIO.write(new BufferedImage(600, 1200, BufferedImage.TYPE_INT_RGB), "png", encoded));
    CountDownLatch iconLoaded = new CountDownLatch(1);
    AtomicReference<ImageIcon> icon = new AtomicReference<>();
    AtomicBoolean iconUpdatedOnEdt = new AtomicBoolean();
    AtomicReference<ChatLinkPreviewComponent> component = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () ->
            component.set(
                new ChatLinkPreviewComponent(
                    "server", url, previews, images, false, EmbedCardStyle.DEFAULT, 80, 120)));
    SwingUtilities.invokeAndWait(
        () -> {
          observeIconUpdates(component.get(), icon, iconUpdatedOnEdt, iconLoaded);
          // Cache hits can emit synchronously on the EDT; decoding must still move off it.
          download.onSuccess(encoded.toByteArray());
        });

    assertTrue(iconLoaded.await(5, TimeUnit.SECONDS), "thumbnail did not finish loading");
    assertTrue(iconUpdatedOnEdt.get(), "Swing component updates must return to the EDT");
    assertEquals(60, icon.get().getIconWidth());
    assertEquals(120, icon.get().getIconHeight());
  }

  private static void observeIconUpdates(
      Container container,
      AtomicReference<ImageIcon> icon,
      AtomicBoolean updatedOnEdt,
      CountDownLatch loaded) {
    for (Component child : container.getComponents()) {
      if (child instanceof JLabel label) {
        label.addPropertyChangeListener(
            "icon",
            event -> {
              if (event.getNewValue() instanceof ImageIcon image) {
                icon.set(image);
                updatedOnEdt.set(SwingUtilities.isEventDispatchThread());
                loaded.countDown();
              }
            });
      }
      if (child instanceof Container nested) observeIconUpdates(nested, icon, updatedOnEdt, loaded);
    }
  }
}
