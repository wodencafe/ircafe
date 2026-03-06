package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.Test;

class MessageInputPanelFunctionalTest {

  @Test
  void clickingSendPublishesOutboundMessage() throws Exception {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    CommandHistoryStore historyStore = mock(CommandHistoryStore.class);

    MessageInputPanel panel = new MessageInputPanel(settingsBus, historyStore);
    CopyOnWriteArrayList<String> outbound = new CopyOnWriteArrayList<>();
    Disposable subscription = panel.outboundMessages().subscribe(outbound::add);

    try {
      JTextField input = findFirst(panel, JTextField.class);
      JButton send = findNamedButton(panel, "messageSendButton");
      assertNotNull(input, "message input field should be present");
      assertNotNull(send, "send button should be present");

      onEdt(() -> input.setText("hello functional smoke"));
      flushEdt();

      onEdt(() -> send.doClick());
      flushEdt();

      waitFor(() -> outbound.size() == 1, Duration.ofSeconds(3));
      assertEquals("hello functional smoke", outbound.getFirst());
      onEdt(() -> assertTrue(input.getText().isEmpty(), "input should clear after send"));
    } finally {
      subscription.dispose();
      flushEdt();
    }
  }

  @Test
  void matrixUploadControlsOnlyShowForMatrixServers() throws Exception {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    CommandHistoryStore historyStore = mock(CommandHistoryStore.class);

    MessageInputPanel panel = new MessageInputPanel(settingsBus, historyStore);
    JButton attach = findNamedButton(panel, "messageAttachButton");
    assertNotNull(attach, "attach button should be present");

    onEdt(
        () -> {
          panel.setIsMatrixServer(sid -> "matrix".equalsIgnoreCase(sid));
          panel.setActiveServerId("libera");
          assertFalse(attach.isVisible(), "attach should hide on IRC servers");
        });

    onEdt(
        () -> {
          panel.setActiveServerId("matrix");
          assertTrue(attach.isVisible(), "attach should show on Matrix servers");
        });
  }

  @Test
  void droppingImagePublishesMuploadAndUsesDraftAsCaption() throws Exception {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    CommandHistoryStore historyStore = mock(CommandHistoryStore.class);

    MessageInputPanel panel = new MessageInputPanel(settingsBus, historyStore);
    CopyOnWriteArrayList<String> outbound = new CopyOnWriteArrayList<>();
    Disposable subscription = panel.outboundMessages().subscribe(outbound::add);
    Path image = Files.createTempFile("matrix-upload-image-", ".png");

    try {
      JTextField input = findFirst(panel, JTextField.class);
      assertNotNull(input, "message input field should be present");
      onEdt(() -> enableMatrixUploads(panel));

      onEdt(() -> input.setText("caption from draft"));
      flushEdt();

      onEdt(() -> assertTrue(dropFiles(input, List.of(image.toFile())), "file drop should import"));
      flushEdt();

      waitFor(() -> outbound.size() == 1, Duration.ofSeconds(3));
      assertEquals(
          expectedUpload("m.image", image.toFile(), "caption from draft"), outbound.getFirst());
      onEdt(
          () -> assertTrue(input.getText().isEmpty(), "input should clear after caption consume"));
    } finally {
      subscription.dispose();
      Files.deleteIfExists(image);
      flushEdt();
    }
  }

  @Test
  void droppingMultipleFilesUsesCaptionOnlyForFirstAndInfersTypes() throws Exception {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    CommandHistoryStore historyStore = mock(CommandHistoryStore.class);

    MessageInputPanel panel = new MessageInputPanel(settingsBus, historyStore);
    CopyOnWriteArrayList<String> outbound = new CopyOnWriteArrayList<>();
    Disposable subscription = panel.outboundMessages().subscribe(outbound::add);
    Path image = Files.createTempFile("matrix-upload-image-", ".png");
    Path audio = Files.createTempFile("matrix-upload-audio-", ".mp3");

    try {
      JTextField input = findFirst(panel, JTextField.class);
      assertNotNull(input, "message input field should be present");
      onEdt(() -> enableMatrixUploads(panel));

      onEdt(() -> input.setText("first caption"));
      flushEdt();

      onEdt(
          () ->
              assertTrue(
                  dropFiles(input, List.of(image.toFile(), audio.toFile())),
                  "multi file drop should import"));
      flushEdt();

      waitFor(() -> outbound.size() == 2, Duration.ofSeconds(3));
      assertEquals(expectedUpload("m.image", image.toFile(), "first caption"), outbound.get(0));
      assertEquals(expectedUpload("m.audio", audio.toFile(), ""), outbound.get(1));
      onEdt(
          () -> assertTrue(input.getText().isEmpty(), "input should clear after caption consume"));
    } finally {
      subscription.dispose();
      Files.deleteIfExists(image);
      Files.deleteIfExists(audio);
      flushEdt();
    }
  }

  private static boolean dropFiles(JComponent target, List<File> files) {
    TransferHandler handler = target.getTransferHandler();
    assertNotNull(handler, "target should have a transfer handler");
    TransferHandler.TransferSupport support =
        new TransferHandler.TransferSupport(target, new FileListTransferable(files));
    return handler.importData(support);
  }

  private static String expectedUpload(String msgType, File file, String caption) {
    String path = file.getAbsolutePath().replace("\\", "\\\\").replace("\"", "\\\"");
    String line = "/upload " + msgType + " \"" + path + "\"";
    String text = caption == null ? "" : caption.trim();
    if (!text.isEmpty()) {
      line += " " + text;
    }
    return line;
  }

  private static void enableMatrixUploads(MessageInputPanel panel) {
    panel.setIsMatrixServer(sid -> "matrix".equalsIgnoreCase(sid));
    panel.setActiveServerId("matrix");
  }

  private static JButton findNamedButton(Component root, String name) {
    if (root == null || name == null) return null;
    if (root instanceof JButton button && name.equals(button.getName())) {
      return button;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JButton found = findNamedButton(child, name);
      if (found != null) return found;
    }
    return null;
  }

  private static <T extends Component> T findFirst(Component root, Class<T> type) {
    if (root == null || type == null) return null;
    if (type.isInstance(root)) return type.cast(root);
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      T found = findFirst(child, type);
      if (found != null) return found;
    }
    return null;
  }

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static void onEdt(ThrowingRunnable r) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            r.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class FileListTransferable implements Transferable {
    private final List<File> files;

    private FileListTransferable(List<File> files) {
      this.files = files == null ? List.of() : List.copyOf(files);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[] {DataFlavor.javaFileListFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return DataFlavor.javaFileListFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor)
        throws UnsupportedFlavorException, IOException {
      if (!isDataFlavorSupported(flavor)) {
        throw new UnsupportedFlavorException(flavor);
      }
      return files;
    }
  }
}
