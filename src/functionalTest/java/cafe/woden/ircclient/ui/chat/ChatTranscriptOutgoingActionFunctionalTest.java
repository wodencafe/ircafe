package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.testutil.SwingComponentSnapshotSupport;
import cafe.woden.ircclient.ui.WrapTextPane;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptDeliveryIndicatorSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.OutgoingSendIndicator;
import java.awt.Component;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

class ChatTranscriptOutgoingActionFunctionalTest {
  @Test
  void pendingActionShowsSpinnerThenConfirmedDotOnSameTranscriptLine() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
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
            TargetRef target = new TargetRef("server", "#actions");
            StyledDocument doc = store.document(target);
            WrapTextPane pane = new WrapTextPane();
            pane.setDocument(doc);
            pane.setSize(720, 300);

            store.appendPendingOutgoingAction(target, "pending-action", "me", "waves", 1_000L);
            SwingComponentSnapshotSupport.capture(pane);
            assertTrue(doc.getText(0, doc.getLength()).contains("* me waves"));
            Component spinner = findIndicator(doc, OutgoingSendIndicator.PendingSpinner.class);
            assertNotNull(spinner);
            assertTrue(
                spinner.getWidth() > 0, "pending indicator must be laid out in the transcript");
            Rectangle2D pendingBody =
                pane.modelToView2D(doc.getText(0, doc.getLength()).indexOf("waves"));
            assertNotNull(pendingBody);

            store.appendChat(target, "alice", "next message");
            assertTrue(
                store.resolvePendingOutgoingAction(
                    target,
                    "pending-action",
                    "me",
                    "waves",
                    1_100L,
                    "action-id",
                    Map.of("msgid", "action-id")));
            SwingComponentSnapshotSupport.capture(pane);
            String text = doc.getText(0, doc.getLength());
            assertEquals(text.indexOf("* me waves"), text.lastIndexOf("* me waves"));
            assertTrue(text.indexOf("waves") < text.indexOf("next message"));
            assertEquals(
                0,
                ChatTranscriptDeliveryIndicatorSupport.inlineComponentCount(
                    doc, OutgoingSendIndicator.PendingSpinner.class));
            Component dot = findIndicator(doc, OutgoingSendIndicator.ConfirmedDot.class);
            assertNotNull(dot);
            assertTrue(
                dot.getWidth() > 0, "confirmed indicator must be laid out in the transcript");
            Rectangle2D confirmedBody = pane.modelToView2D(text.indexOf("waves"));
            assertEquals(pendingBody.getY(), confirmedBody.getY());
            pane.setDocument(new javax.swing.text.DefaultStyledDocument());
          } catch (Exception failure) {
            throw new AssertionError(failure);
          }
        });
  }

  private static Component findIndicator(StyledDocument doc, Class<?> type) {
    for (int offset = 0; offset < doc.getLength(); offset++) {
      Component component =
          StyleConstants.getComponent(doc.getCharacterElement(offset).getAttributes());
      if (type.isInstance(component)) return component;
    }
    return null;
  }
}
