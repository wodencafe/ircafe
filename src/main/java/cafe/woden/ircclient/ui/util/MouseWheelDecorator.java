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

    private NumericSpinnerWheelListener(JSpinner spinner) {
      this.spinner = spinner;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      if (!spinner.isEnabled()) return;
      if (!(spinner.getModel() instanceof SpinnerNumberModel)) return;

      int steps = e.getWheelRotation();

      if (steps == 0) {
        preciseAccum += e.getPreciseWheelRotation();
        steps = (int) preciseAccum;     // take whole steps
        preciseAccum -= steps;          // keep remainder
      }

      if (steps == 0) return;

      int dir = steps > 0 ? 1 : -1;
      int count = Math.abs(steps);

      // Optional: speed modifiers
      if (e.isShiftDown()) count *= 5;
      if (e.isControlDown()) count *= 10;

      for (int i = 0; i < count; i++) {
        Object next = (dir > 0) ? spinner.getNextValue() : spinner.getPreviousValue();
        if (next == null) break; // hit min/max
        spinner.setValue(next);
      }

      e.consume(); // prevent scrollpane from also scrolling
    }
  }
}
