package cafe.woden.ircclient.ui.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public final class MouseWheelDecorator {

  private MouseWheelDecorator() {}

  /** Installs mousewheel support on a numeric JSpinner (and its child components). */
  public static AutoCloseable decorateNumberSpinner(JSpinner spinner) {
    MouseWheelListener listener = new NumericSpinnerWheelListener(spinner);

    installRecursive(spinner, listener);

    // Uninstall
    return () -> uninstallRecursive(spinner, listener);
  }

  private static void installRecursive(Component c, MouseWheelListener l) {
    c.addMouseWheelListener(l);
    if (c instanceof Container ctr) {
      for (Component child : ctr.getComponents()) {
        installRecursive(child, l);
      }
    }
  }

  private static void uninstallRecursive(Component c, MouseWheelListener l) {
    c.removeMouseWheelListener(l);
    if (c instanceof Container ctr) {
      for (Component child : ctr.getComponents()) {
        uninstallRecursive(child, l);
      }
    }
  }

  private static final class NumericSpinnerWheelListener implements MouseWheelListener {
    private final JSpinner spinner;
    private double preciseAccum = 0.0;

    private static final long BURST_WINDOW_MS = 25;

    private long lastStepWhenMs = -1;
    private int lastStepDir = 0;

    private NumericSpinnerWheelListener(JSpinner spinner) {
      this.spinner = spinner;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      if (!spinner.isEnabled()) return;
      if (!(spinner.getModel() instanceof SpinnerNumberModel)) return;

      final int wheel = e.getWheelRotation();

      int rawDir;

      if (wheel != 0) {
        preciseAccum = 0.0;
        rawDir = Integer.signum(wheel);
      } else {
        preciseAccum += e.getPreciseWheelRotation();
        if (Math.abs(preciseAccum) < 1.0) return;
        rawDir = (int) Math.signum(preciseAccum);
        preciseAccum -= rawDir; // keep remainder (fractional)
      }

      final int dir = -rawDir;

      final long whenMs = e.getWhen();
      if (lastStepWhenMs >= 0
          && dir == lastStepDir
          && (whenMs - lastStepWhenMs) <= BURST_WINDOW_MS) {
        e.consume();
        return;
      }

      Object next = (dir > 0) ? spinner.getNextValue() : spinner.getPreviousValue();
      if (next != null) {
        spinner.setValue(next);
        lastStepWhenMs = whenMs;
        lastStepDir = dir;
      }

      e.consume(); // prevent scrollpane from also scrolling
    }
  }
}
