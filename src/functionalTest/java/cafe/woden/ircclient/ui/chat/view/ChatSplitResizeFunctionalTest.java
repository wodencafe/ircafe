package cafe.woden.ircclient.ui.chat.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JScrollBar;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(15)
class ChatSplitResizeFunctionalTest {

  @ParameterizedTest
  @ValueSource(ints = {JSplitPane.VERTICAL_SPLIT, JSplitPane.HORIZONTAL_SPLIT})
  void chatsStayAtBottomAsDividerMovesInBothDirections(int orientation) throws Exception {
    Fixture fixture = new Fixture(orientation);
    try {
      onEdt(fixture::install);
      onEdt(fixture::pin);
      for (int divider : new int[] {280, 200, 120, 240, 360, 440, 320}) {
        onEdt(() -> fixture.resize(divider));
        onEdt(
            () -> {
              assertAtBottom(fixture.upper);
              assertAtBottom(fixture.lower);
            });
      }
    } finally {
      onEdt(fixture::close);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {JSplitPane.VERTICAL_SPLIT, JSplitPane.HORIZONTAL_SPLIT})
  void resizingPreservesHistoryPositionInOneChatWhileOtherFollowsTail(int orientation)
      throws Exception {
    Fixture fixture = new Fixture(orientation);
    try {
      onEdt(fixture::install);
      onEdt(fixture::pin);
      onEdt(
          () -> {
            fixture.upper.scroll.getVerticalScrollBar().setValue(120);
            assertFalse(fixture.upper.follow);
          });
      for (int divider : new int[] {200, 120, 320, 440}) {
        onEdt(() -> fixture.resize(divider));
        onEdt(
            () -> {
              assertEquals(120, fixture.upper.scroll.getVerticalScrollBar().getValue());
              assertFalse(fixture.upper.follow);
              assertAtBottom(fixture.lower);
            });
      }
    } finally {
      onEdt(fixture::close);
    }
  }

  private static void assertAtBottom(TestChatView view) {
    JScrollBar bar = view.scroll.getVerticalScrollBar();
    assertTrue(view.follow, "divider movement must keep follow-tail enabled");
    assertEquals(bar.getMaximum() - bar.getVisibleAmount(), bar.getValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {JSplitPane.VERTICAL_SPLIT, JSplitPane.HORIZONTAL_SPLIT})
  void manualScrollAfterDividerMovementCancelsPendingTailPin(int orientation) throws Exception {
    Fixture fixture = new Fixture(orientation);
    try {
      onEdt(fixture::install);
      onEdt(fixture::pin);
      onEdt(
          () -> {
            fixture.resize(200);
            fixture.upper.scroll.getVerticalScrollBar().setValue(120);
            assertFalse(fixture.upper.follow);
          });
      onEdt(
          () -> {
            assertEquals(120, fixture.upper.scroll.getVerticalScrollBar().getValue());
            assertFalse(fixture.upper.follow);
            assertAtBottom(fixture.lower);
          });
    } finally {
      onEdt(fixture::close);
    }
  }

  private static void onEdt(Runnable task) throws Exception {
    SwingUtilities.invokeAndWait(task);
  }

  private static void layout(Container container) {
    container.doLayout();
    for (Component component : container.getComponents()) {
      if (component instanceof Container child) layout(child);
    }
  }

  private static final class Fixture {
    private TestChatView upper;
    private TestChatView lower;
    private JSplitPane split;
    private final int orientation;

    private Fixture(int orientation) {
      this.orientation = orientation;
    }

    private void install() {
      upper = new TestChatView();
      lower = new TestChatView();
      split = new JSplitPane(orientation, upper, lower);
      split.setContinuousLayout(true);
      split.setSize(640, 640);
      split.addNotify();
      resize(320);
    }

    private void pin() {
      layout(split);
      upper.scrollToBottom();
      lower.scrollToBottom();
      assertAtBottom(upper);
      assertAtBottom(lower);
    }

    private void resize(int divider) {
      split.setDividerLocation(divider);
      layout(split);
    }

    private void close() {
      if (split != null) split.removeNotify();
      if (upper != null) upper.closeDecorators();
      if (lower != null) lower.closeDecorators();
    }
  }

  private static final class TestChatView extends ChatViewPanel {
    private boolean follow = true;
    private int saved;

    private TestChatView() {
      super(null);
      DefaultStyledDocument document = new DefaultStyledDocument();
      try {
        document.insertString(
            0,
            ("alice: "
                    + "a long message that wraps when the buffer gets narrower ".repeat(8)
                    + "\n")
                .repeat(40),
            null);
      } catch (Exception ex) {
        throw new AssertionError(ex);
      }
      setDocument(document);
    }

    @Override
    protected boolean isFollowTail() {
      return follow;
    }

    @Override
    protected void setFollowTail(boolean followTail) {
      follow = followTail;
    }

    @Override
    protected int getSavedScrollValue() {
      return saved;
    }

    @Override
    protected void setSavedScrollValue(int value) {
      saved = value;
    }
  }
}
