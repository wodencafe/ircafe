package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.testutil.SwingComponentSnapshotSupport;
import cafe.woden.ircclient.ui.WrapTextPane;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.view.ChatViewPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatTranscriptRepliesFunctionalTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void quotedPreviewAndBodyFormOneClickableBlockInBothThemes(boolean dark) throws Exception {
    SwingUtilities.invokeAndWait(() -> verifyReplyBlock(dark));
  }

  private static void verifyReplyBlock(boolean dark) {
    Color background = dark ? new Color(0x243244) : Color.WHITE;
    Color foreground = dark ? new Color(0xE3E8EF) : new Color(0x1D232B);
    Object oldBackground = UIManager.get("TextPane.background");
    Object oldForeground = UIManager.get("TextPane.foreground");
    PreviewPanel panel = null;
    try {
      UIManager.put("TextPane.background", background);
      UIManager.put("TextPane.foreground", foreground);
      ChatStyles styles = new ChatStyles(null);
      ChatTranscriptStore store =
          new ChatTranscriptStore(
              styles,
              new ChatRichTextRenderer(null, null, styles, null),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
      TargetRef target = new TargetRef("server", "#replies");
      store.appendChatAt(
          target,
          "alice",
          "Please check #channel before replying.",
          false,
          1_000L,
          "original-id",
          Map.of());
      store.appendChatAt(
          target,
          "bob",
          "I checked it.",
          false,
          2_000L,
          "reply-id",
          Map.of("+reply", "original-id"));
      store.appendChat(target, "carol", "Next ordinary message.");
      panel = new PreviewPanel(store.document(target));
      panel.pane().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
      panel.pane().setBackground(background);
      panel.pane().setForeground(foreground);
      for (int width : new int[] {720, 380}) {
        panel.pane().setSize(width, 480);
        BufferedImage snapshot = SwingComponentSnapshotSupport.capture(panel.pane());
        String text = store.document(target).getText(0, store.document(target).getLength());
        int quoteOffset = text.indexOf("↪ Reply to alice:");
        assertTrue(quoteOffset >= 0);
        Rectangle2D quote = panel.pane().modelToView2D(quoteOffset);
        Rectangle2D reply = panel.pane().modelToView2D(text.indexOf("bob:"));
        Rectangle2D next = panel.pane().modelToView2D(text.indexOf("carol:"));
        assertNotNull(quote);
        assertNotNull(reply);
        assertNotNull(next);
        assertTrue(quote.getY() < reply.getY());
        assertTrue(reply.getY() < next.getY());
        int sampleX = width - 15;
        int quoteColor = snapshot.getRGB(sampleX, (int) quote.getCenterY());
        assertNotEquals(background.getRGB(), quoteColor, "quote background must be visible");
        assertEquals(
            quoteColor,
            snapshot.getRGB(sampleX, (int) reply.getCenterY()),
            "quote and reply should share a background");
        assertEquals(
            background.getRGB(),
            snapshot.getRGB(sampleX, (int) next.getCenterY()),
            "the next ordinary message must not inherit the block");

        // A channel name within a quote navigates to its original message, not to that channel.
        Rectangle2D channel = panel.pane().modelToView2D(text.indexOf("#channel", quoteOffset));
        panel.clickedMessage = null;
        panel
            .pane()
            .dispatchEvent(
                new MouseEvent(
                    panel.pane(),
                    MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),
                    0,
                    (int) channel.getX() + 2,
                    (int) channel.getCenterY(),
                    1,
                    false,
                    MouseEvent.BUTTON1));
        assertEquals("original-id", panel.clickedMessage);
        assertEquals(0, panel.channelClicks);
      }
    } catch (Exception failure) {
      throw new AssertionError(failure);
    } finally {
      if (panel != null) panel.close();
      UIManager.put("TextPane.background", oldBackground);
      UIManager.put("TextPane.foreground", oldForeground);
    }
  }

  private static final class PreviewPanel extends ChatViewPanel {
    private String clickedMessage;
    private int channelClicks;

    PreviewPanel(StyledDocument document) {
      super(null);
      setDocument(document);
    }

    WrapTextPane pane() {
      return chat;
    }

    void close() {
      closeDecorators();
    }

    @Override
    protected boolean onMessageReferenceClicked(String messageId) {
      clickedMessage = messageId;
      return true;
    }

    @Override
    protected boolean onChannelClicked(String channel) {
      channelClicks++;
      return true;
    }

    @Override
    protected boolean isFollowTail() {
      return false;
    }

    @Override
    protected void setFollowTail(boolean followTail) {}

    @Override
    protected int getSavedScrollValue() {
      return 0;
    }

    @Override
    protected void setSavedScrollValue(int value) {}
  }
}
