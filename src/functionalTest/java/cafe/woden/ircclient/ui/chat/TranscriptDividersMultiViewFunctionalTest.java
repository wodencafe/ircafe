package cafe.woden.ircclient.ui.chat;

import static cafe.woden.ircclient.ui.chat.transcript.support.ChatTranscriptStoreTestFactory.newStore;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.WrapTextPane;
import cafe.woden.ircclient.ui.chat.fold.HistoryDividerComponent;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptRestyleSupport;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(15)
class TranscriptDividersMultiViewFunctionalTest {

  @ParameterizedTest
  @CsvSource({"false, false", "true, false", "false, true", "true, true"})
  void bothDocksKeepTheirDividers(boolean openExtraDockFirst, boolean unread) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ChatTranscriptStore store = newStore();
          TargetRef target = new TargetRef("server", "#news");
          store.appendChatAt(target, "alice", "older message", false, 900L);
          StyledDocument document = store.document(target);
          JPanel docks = new JPanel(new GridLayout(1, 2));
          WrapTextPane main = new WrapTextPane();
          WrapTextPane extra = new WrapTextPane();
          main.setDocument(document);
          docks.add(main);
          docks.setSize(1000, 600);
          docks.addNotify();
          try {
            if (openExtraDockFirst) {
              extra.setDocument(document);
              docks.add(extra);
            }
            String label = unread ? "Unread" : "History — Oct 6, 2026";
            if (unread) {
              store.updateReadMarker(target, 1000L);
            } else {
              store.ensureHistoryDivider(target, document.getLength(), label);
            }
            store.appendChatAt(target, "alice", "newer message", false, 1100L);
            paint(docks);
            if (!openExtraDockFirst) {
              extra.setDocument(document);
              docks.add(extra);
              paint(docks);
            }
            assertDivider(main, label);
            assertDivider(extra, label);
            assertNotSame(dividersIn(main).getFirst(), dividersIn(extra).getFirst());

            if (unread) {
              store.appendChatAt(target, "alice", "latest message", false, 1200L);
              store.updateReadMarker(target, 1150L);
              assertTrue(store.readMarkerJumpOffset(target) >= 0);
            } else {
              label = "History — Oct 7, 2026";
              int length = document.getLength();
              store.ensureHistoryDivider(target, 0, label);
              assertEquals(length, document.getLength(), "updating the label must not add a row");
            }
            paint(docks);
            assertDivider(main, label);
            assertDivider(extra, label);

            extra.setDocument(new DefaultStyledDocument());
            paint(docks);
            assertDivider(main, label);
            extra.setDocument(document);
            ChatTranscriptRestyleSupport.restyleDocument(
                new ChatTranscriptRestyleSupport.Context(
                    new ChatStyles(null), null, (fresh, action) -> {}),
                document,
                false,
                null);
            paint(docks);
            assertDivider(main, label);
            assertDivider(extra, label);

            if (unread) {
              store.clearReadMarker(target);
              paint(docks);
              assertTrue(dividersIn(main).isEmpty());
              assertTrue(dividersIn(extra).isEmpty());
              assertEquals(-1, store.readMarkerJumpOffset(target));
            } else {
              main.setDocument(new DefaultStyledDocument());
              paint(docks);
              assertDivider(extra, label);
            }
          } finally {
            docks.removeNotify();
          }
        });
  }

  private static void assertDivider(Container pane, String label) {
    List<HistoryDividerComponent> dividers = dividersIn(pane);
    assertEquals(1, dividers.size(), "each dock must have its own divider");
    assertEquals(label, dividers.getFirst().getText());
  }

  private static List<HistoryDividerComponent> dividersIn(Container container) {
    List<HistoryDividerComponent> dividers = new ArrayList<>();
    for (Component child : container.getComponents()) {
      if (child instanceof HistoryDividerComponent divider) dividers.add(divider);
      else if (child instanceof Container nested) dividers.addAll(dividersIn(nested));
    }
    return dividers;
  }

  private static void paint(JPanel docks) {
    docks.doLayout();
    BufferedImage image = new BufferedImage(1000, 600, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      docks.paint(graphics);
    } finally {
      graphics.dispose();
    }
  }
}
