package cafe.woden.ircclient.ui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.LayoutManager;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.ViewportLayout;
import org.junit.jupiter.api.Test;

class FollowTailScrollDecoratorTest {

  @Test
  void shrinkingViewportKeepsFollowingTail() throws Exception {
    Fixture fixture = new Fixture();
    try {
      onEdt(
          () -> {
            fixture.install();
            fixture.scroll.getVerticalScrollBar().getModel().setExtent(200);
            assertTrue(fixture.follow.get(), "resizing must not look like user scrolling");
          });
      onEdt(() -> assertEquals(800, fixture.scroll.getVerticalScrollBar().getValue()));
    } finally {
      onEdt(fixture::close);
    }
  }

  @Test
  void resizingWhileReadingHistoryPreservesPosition() throws Exception {
    Fixture fixture = new Fixture();
    try {
      onEdt(
          () -> {
            fixture.install();
            fixture.scroll.getVerticalScrollBar().setValue(250);
            assertFalse(fixture.follow.get());
            fixture.scroll.getVerticalScrollBar().getModel().setExtent(200);
          });
      onEdt(
          () -> {
            assertEquals(250, fixture.scroll.getVerticalScrollBar().getValue());
            assertEquals(250, fixture.saved.get());
            assertFalse(fixture.follow.get());
          });
    } finally {
      onEdt(fixture::close);
    }
  }

  @Test
  void repeatedLayoutChangesKeepTailPinned() throws Exception {
    Fixture fixture = new Fixture();
    try {
      onEdt(
          () -> {
            fixture.install();
            fixture.scroll.getVerticalScrollBar().getModel().setExtent(200);
            fixture.scroll.getVerticalScrollBar().getModel().setExtent(150);
            fixture.scroll.getVerticalScrollBar().setMaximum(1200);
            fixture.decorator.updateScrollStateFromBar();
            assertTrue(fixture.follow.get());
          });
      onEdt(
          () -> {
            assertEquals(1050, fixture.scroll.getVerticalScrollBar().getValue());
            assertEquals(1050, fixture.saved.get());
          });
    } finally {
      onEdt(fixture::close);
    }
  }

  @Test
  void manualScrollCancelsQueuedResizePin() throws Exception {
    Fixture fixture = new Fixture();
    try {
      onEdt(
          () -> {
            fixture.install();
            fixture.scroll.getVerticalScrollBar().getModel().setExtent(200);
            fixture.scroll.getVerticalScrollBar().setValue(250);
          });
      onEdt(
          () -> {
            assertEquals(250, fixture.scroll.getVerticalScrollBar().getValue());
            assertFalse(fixture.follow.get());
          });
    } finally {
      onEdt(fixture::close);
    }
  }

  @Test
  void reachingBottomReenablesTailFollowingForNextResize() throws Exception {
    Fixture fixture = new Fixture();
    try {
      onEdt(
          () -> {
            fixture.install();
            fixture.scroll.getVerticalScrollBar().setValue(250);
            assertFalse(fixture.follow.get());
            fixture.scroll.getVerticalScrollBar().setValue(700);
            assertTrue(fixture.follow.get());
            fixture.scroll.getVerticalScrollBar().getModel().setExtent(200);
          });
      onEdt(() -> assertEquals(800, fixture.scroll.getVerticalScrollBar().getValue()));
    } finally {
      onEdt(fixture::close);
    }
  }

  @Test
  void armedTailPinSurvivesLayoutChange() throws Exception {
    Fixture fixture = new Fixture();
    try {
      onEdt(
          () -> {
            fixture.install();
            fixture.decorator.armTailPinIfAtBottomNow();
            fixture.scroll.getVerticalScrollBar().getModel().setExtent(200);
          });
      onEdt(
          () -> {
            assertEquals(800, fixture.scroll.getVerticalScrollBar().getValue());
            assertTrue(fixture.follow.get());
          });
    } finally {
      onEdt(fixture::close);
    }
  }

  @Test
  void closedDecoratorDoesNotApplyQueuedResizePin() throws Exception {
    Fixture fixture = new Fixture();
    onEdt(
        () -> {
          fixture.install();
          fixture.scroll.getVerticalScrollBar().getModel().setExtent(200);
          fixture.close();
        });
    onEdt(() -> assertEquals(700, fixture.scroll.getVerticalScrollBar().getValue()));
  }

  @Test
  void closeRestoresOriginalViewportLayout() throws Exception {
    Fixture fixture = new Fixture();
    onEdt(
        () -> {
          fixture.install();
          fixture.close();
          assertSame(fixture.originalLayout, fixture.scroll.getViewport().getLayout());
        });
  }

  @Test
  void closePreservesReplacementViewportLayout() throws Exception {
    Fixture fixture = new Fixture();
    onEdt(
        () -> {
          fixture.install();
          LayoutManager replacement = new ViewportLayout();
          fixture.scroll.getViewport().setLayout(replacement);
          fixture.close();
          assertSame(replacement, fixture.scroll.getViewport().getLayout());
        });
  }

  private static void onEdt(Runnable task) throws Exception {
    SwingUtilities.invokeAndWait(task);
  }

  private static final class Fixture {
    private JScrollPane scroll;
    private FollowTailScrollDecorator decorator;
    private LayoutManager originalLayout;
    private final AtomicBoolean follow = new AtomicBoolean(true);
    private final AtomicInteger saved = new AtomicInteger(700);

    private void install() {
      scroll = new JScrollPane();
      originalLayout = scroll.getViewport().getLayout();
      scroll.getVerticalScrollBar().setValues(700, 300, 0, 1000);
      decorator =
          new FollowTailScrollDecorator(scroll, follow::get, follow::set, saved::get, saved::set);
      decorator.updateScrollStateFromBar();
    }

    private void close() {
      if (decorator != null) decorator.close();
    }
  }
}
