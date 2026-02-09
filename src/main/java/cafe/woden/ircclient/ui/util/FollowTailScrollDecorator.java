package cafe.woden.ircclient.ui.util;

import java.awt.event.AdjustmentListener;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decorates a transcript scrollpane with "follow tail" behavior: if the user is at the bottom, new content keeps the view pinned to the bot… */
public final class FollowTailScrollDecorator implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(FollowTailScrollDecorator.class);
  private static final AtomicInteger IDS = new AtomicInteger(0);
  private final int id = IDS.incrementAndGet();

  private static final long INFO_INTERVAL_NS = TimeUnit.SECONDS.toNanos(1);
  private volatile long lastInfoNs = 0L;

  private final JScrollPane scroll;
  private final JScrollBar bar;
  private final BoundedRangeModel barModel;

  private final BooleanSupplier isFollowTail;
  private final Consumer<Boolean> setFollowTail;
  private final IntSupplier getSavedScrollValue;
  private final IntConsumer setSavedScrollValue;

  private boolean programmaticScroll = false;
  private StyledDocument currentDocument;

  private volatile boolean forcePinOnce = false;

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

    SwingUtilities.invokeLater(() -> infoOnce("installed"));
  }

  private boolean safeIsFollowTail() {
    try {
      return isFollowTail.getAsBoolean();
    } catch (Throwable t) {
      return true;
    }
  }

  private void infoOnce(String reason) {
    if (!log.isInfoEnabled()) return;
    long now = System.nanoTime();
    if (now - lastInfoNs < INFO_INTERVAL_NS) return;
    lastInfoNs = now;
    try {
      int max = bar.getMaximum();
      int extent = barModel.getExtent();
      int val = bar.getValue();
      boolean atBottomNow = (val + extent) >= (max - 2);
      log.info("[FollowTail#{}] {} followTail={} forcePinOnce={} atBottomNow={} val={} extent={} max={} lastVal={} lastExtent={} lastMax={}",
          id,
          reason,
          safeIsFollowTail(),
          forcePinOnce,
          atBottomNow,
          val,
          extent,
          max,
          lastBarValue,
          lastBarExtent,
          lastBarMax);
    } catch (Throwable t) {
      log.info("[FollowTail#{}] {} (unable to snapshot scroll state: {})", id, reason, t.toString());
    }
  }

  private void setFollowTailWithReason(boolean next, String reason) {
    boolean before = safeIsFollowTail();
    // NOTE: do not rate-limit transitions; they're rare and are exactly what we need.
    if (before != next && log.isInfoEnabled()) {
      log.info("[FollowTail#{}] followTail {} -> {} reason={}", id, before, next, reason);
    }
    try {
      setFollowTail.accept(next);
    } catch (Throwable t) {
      if (log.isInfoEnabled()) {
        log.info("[FollowTail#{}] setFollowTail failed (reason={}): {}", id, reason, t.toString());
      }
    }
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
    boolean follow = safeIsFollowTail();
    boolean atBottomNow = false;

    try {
      int max = bar.getMaximum();
      int extent = barModel.getExtent();
      int val = bar.getValue();
      atBottomNow = (val + extent) >= (max - 2);
    } catch (Throwable ignored) {
    }

    if (!follow && !forcePinOnce && atBottomNow) {
      log.info("[FollowTail#{}] docMutation atBottomNow=true but followTail=false -> auto enable", id);
      setFollowTailWithReason(true, "docMutation.atBottomAutoEnable");
      follow = true;
    }

    if (!(follow || forcePinOnce)) {
      if (atBottomNow) {
        infoOnce("docMutation but followTail=false while atBottomNow=true");
      }
      return;
    }

    infoOnce("docMutation -> maybeAutoScroll");
    SwingUtilities.invokeLater(() -> {
      try {
        scrollToBottom();
      } finally {
        // Clear the one-shot pin after we have successfully re-pinned.
        forcePinOnce = false;
      }
    });
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

    if ((safeIsFollowTail() && wasAtBottom) || forcePinOnce) {
      infoOnce("barModelChanged -> repin");
      SwingUtilities.invokeLater(() -> {
        try {
          scrollToBottom();
        } finally {
          forcePinOnce = false;
        }
      });
    }
  }

  public void armTailPinIfAtBottomNow() {
    if (closed) return;
    int max = bar.getMaximum();
    int extent = barModel.getExtent();
    int val = bar.getValue();
    boolean atBottomNow = (val + extent) >= (max - 2);
    if (!atBottomNow) return;

    // Ensure follow-tail is on and force a re-pin on the next mutation.
    setFollowTailWithReason(true, "armTailPin");
    forcePinOnce = true;
    infoOnce("armTailPinIfAtBottomNow");
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
    int extent = barModel.getExtent();
    int val = bar.getValue();

    boolean maxOrExtentChanged = (max != lastBarMax) || (extent != lastBarExtent);
    boolean wasAtBottom = lastBarMax < 0 || (lastBarValue + lastBarExtent) >= (lastBarMax - 2);
    boolean atBottomNow = (val + extent) >= (max - 2);

    if (forcePinOnce) {
      setFollowTailWithReason(true, "forcePinOnce");
      setSavedScrollValue.accept(val);
      lastBarMax = max;
      lastBarExtent = extent;
      lastBarValue = val;
      return;
    }

    if (maxOrExtentChanged) {
      if (safeIsFollowTail() && wasAtBottom) {
        setFollowTailWithReason(true, "modelChurn.wasAtBottom");
      }
      setSavedScrollValue.accept(val);
      lastBarMax = max;
      lastBarExtent = extent;
      lastBarValue = val;
      return;
    }

    if (atBottomNow) {
      // When the user reaches the bottom, re-enable follow-tail.
      setFollowTailWithReason(true, "atBottomNow");
    } else if (safeIsFollowTail()) {
      // Only treat a non-bottom position as user intent when the model is otherwise stable.
      setFollowTailWithReason(false, "userScrolledAway");
    }

    setSavedScrollValue.accept(val);

    lastBarMax = max;
    lastBarExtent = extent;
    lastBarValue = val;
  }

  public void restoreScrollState() {
    if (closed) return;

    if (safeIsFollowTail()) {
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
