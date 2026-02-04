package cafe.woden.ircclient.ui.util;

import java.awt.event.AdjustmentListener;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.swing.BoundedRangeModel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.StyledDocument;

/** Decorates a transcript scrollpane with "follow tail" behavior: if the user is at the bottom, new content keeps the view pinned to the bot… */
public final class FollowTailScrollDecorator implements AutoCloseable {

  private final JScrollPane scroll;
  private final JScrollBar bar;
  private final BoundedRangeModel barModel;

  private final BooleanSupplier isFollowTail;
  private final Consumer<Boolean> setFollowTail;
  private final IntSupplier getSavedScrollValue;
  private final IntConsumer setSavedScrollValue;

  private boolean programmaticScroll = false;
  private StyledDocument currentDocument;

  // Track prior scrollbar state so we can keep "follow tail" enabled when the transcript grows.
  private int lastBarMax = -1;
  private int lastBarExtent = 0;
  private int lastBarValue = 0;

  private final DocumentListener docListener = new DocumentListener() {
    @Override public void insertUpdate(DocumentEvent e) { maybeAutoScroll(); }
    @Override public void removeUpdate(DocumentEvent e) { maybeAutoScroll(); }
    @Override public void changedUpdate(DocumentEvent e) { maybeAutoScroll(); }
  };

  private final AdjustmentListener barListener = e -> {
    if (programmaticScroll) return;
    updateScrollStateFromBar();
  };

  private final ChangeListener modelListener = (ChangeEvent e) -> onBarModelChanged();

  private boolean closed = false;

  public FollowTailScrollDecorator(
      JScrollPane scroll,
      BooleanSupplier isFollowTail,
      Consumer<Boolean> setFollowTail,
      IntSupplier getSavedScrollValue,
      IntConsumer setSavedScrollValue
  ) {
    this.scroll = scroll;
    this.bar = scroll.getVerticalScrollBar();
    this.barModel = this.bar.getModel();
    this.isFollowTail = isFollowTail;
    this.setFollowTail = setFollowTail;
    this.getSavedScrollValue = getSavedScrollValue;
    this.setSavedScrollValue = setSavedScrollValue;

    this.bar.addAdjustmentListener(barListener);
    this.barModel.addChangeListener(modelListener);
  }

  /** Call after the view's document has been swapped. */
public void onDocumentSwapped(StyledDocument next) {
    if (closed) return;

    if (currentDocument == next) {
      restoreScrollState();
      return;
    }

    if (currentDocument != null) {
      currentDocument.removeDocumentListener(docListener);
    }

    currentDocument = next;

    if (currentDocument != null) {
      currentDocument.addDocumentListener(docListener);
    }

    restoreScrollState();
  }

  private void captureBarState() {
    lastBarMax = bar.getMaximum();
    lastBarExtent = bar.getModel().getExtent();
    lastBarValue = bar.getValue();
  }

  private void maybeAutoScroll() {
    if (closed) return;
    if (!isFollowTail.getAsBoolean()) return;
    SwingUtilities.invokeLater(this::scrollToBottom);
  }

  private void onBarModelChanged() {
    if (closed) return;

    // Ignore re-entrant updates caused by our own scroll operations.
    if (programmaticScroll) {
      captureBarState();
      return;
    }

    int max = bar.getMaximum();
    int extent = barModel.getExtent();
    int val = bar.getValue();

    boolean maxOrExtentChanged = (max != lastBarMax) || (extent != lastBarExtent);
    if (!maxOrExtentChanged) {
      lastBarValue = val;
      return;
    }

    boolean wasAtBottom = lastBarMax < 0 || (lastBarValue + lastBarExtent) >= (lastBarMax - 2);

    // Update the cached model state.
    lastBarMax = max;
    lastBarExtent = extent;
    lastBarValue = val;

    if (isFollowTail.getAsBoolean() && wasAtBottom) {
      SwingUtilities.invokeLater(this::scrollToBottom);
    }
  }

  public void scrollToBottom() {
    if (closed) return;

    try {
      programmaticScroll = true;
      // Clamp-to-bottom: setting to maximum will be constrained by the model to (max - extent).
      bar.setValue(bar.getMaximum());
      captureBarState();
    } finally {
      programmaticScroll = false;
    }
  }

  public void updateScrollStateFromBar() {
    if (closed) return;

    int max = bar.getMaximum();
    int extent = bar.getModel().getExtent();
    int val = bar.getValue();

    boolean atBottomNow = (val + extent) >= (max - 2);

    if (atBottomNow) {
      setFollowTail.accept(true);
    } else if (isFollowTail.getAsBoolean()) {
      // If follow-tail is on and the user scrolls upward, disable follow-tail.
      if (val < lastBarValue) {
        setFollowTail.accept(false);
      }
    }

    setSavedScrollValue.accept(val);

    lastBarMax = max;
    lastBarExtent = extent;
    lastBarValue = val;
  }

  public void restoreScrollState() {
    if (closed) return;

    if (isFollowTail.getAsBoolean()) {
      SwingUtilities.invokeLater(this::scrollToBottom);
      return;
    }

    int saved = getSavedScrollValue.getAsInt();
    SwingUtilities.invokeLater(() -> {
      try {
        programmaticScroll = true;
        bar.setValue(Math.min(saved, Math.max(0, bar.getMaximum())));
        captureBarState();
      } finally {
        programmaticScroll = false;
      }
    });
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;

    try {
      bar.removeAdjustmentListener(barListener);
      barModel.removeChangeListener(modelListener);
    } catch (Exception ignored) {
    }

    if (currentDocument != null) {
      try {
        currentDocument.removeDocumentListener(docListener);
      } catch (Exception ignored) {
      }
      currentDocument = null;
    }
  }
}
