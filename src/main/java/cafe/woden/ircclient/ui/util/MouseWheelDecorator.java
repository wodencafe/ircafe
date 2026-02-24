package cafe.woden.ircclient.ui.util;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public final class MouseWheelDecorator {

  private MouseWheelDecorator() {}

  public static AutoCloseable decorateNumberSpinner(JSpinner spinner) {
    MouseWheelListener listener = new NumericSpinnerWheelListener(spinner);

    installRecursive(spinner, listener);

    // Uninstall
    return () -> uninstallRecursive(spinner, listener);
  }

  /**
   * Decorates a combo box so the mousewheel moves the selection up/down while hovering.
   *
   * <p>Only consumes the wheel event if the selection actually changes; otherwise it lets parent
   * scroll panes handle it.
   */
  public static <T> AutoCloseable decorateComboBoxSelection(JComboBox<T> comboBox) {
    MouseWheelListener listener = new ComboBoxWheelListener<>(comboBox);

    installRecursive(comboBox, listener);

    // Uninstall
    return () -> uninstallRecursive(comboBox, listener);
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

  private static final class ComboBoxWheelListener<T> implements MouseWheelListener {
    private final JComboBox<T> comboBox;
    private double preciseAccum = 0.0;

    private static final long BURST_WINDOW_MS = 25;

    private long lastStepWhenMs = -1;
    private int lastStepDir = 0;

    private ComboBoxWheelListener(JComboBox<T> comboBox) {
      this.comboBox = comboBox;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      if (!comboBox.isEnabled()) return;

      final int wheel = e.getWheelRotation();

      int dir;
      if (wheel != 0) {
        preciseAccum = 0.0;
        dir = Integer.signum(wheel);
      } else {
        preciseAccum += e.getPreciseWheelRotation();
        if (Math.abs(preciseAccum) < 1.0) return;
        dir = (int) Math.signum(preciseAccum);
        preciseAccum -= dir;
      }

      if (dir == 0) return;

      final long whenMs = e.getWhen();
      if (lastStepWhenMs >= 0
          && dir == lastStepDir
          && (whenMs - lastStepWhenMs) <= BURST_WINDOW_MS) {
        e.consume();
        return;
      }

      int count = comboBox.getItemCount();
      if (count <= 0) return;

      int idx = comboBox.getSelectedIndex();
      if (idx < 0) {
        int pick = dir > 0 ? 0 : count - 1;
        comboBox.setSelectedIndex(pick);
        lastStepWhenMs = whenMs;
        lastStepDir = dir;
        e.consume();
        return;
      }

      int next = idx + dir;
      if (next < 0 || next >= count) {
        // At the edge: don't consume so the dialog scrollpane can still scroll.
        return;
      }

      comboBox.setSelectedIndex(next);
      lastStepWhenMs = whenMs;
      lastStepDir = dir;

      e.consume();
    }
  }
}
