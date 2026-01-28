package cafe.woden.ircclient.ui;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;

import javax.swing.SwingUtilities;

/**
 * RxJava scheduler that runs work on the Swing Event Dispatch Thread (EDT).
 */
public final class SwingEdt {
  private SwingEdt() {}

  private static final Scheduler EDT = Schedulers.from(SwingUtilities::invokeLater);

  public static Scheduler scheduler() {
    return EDT;
  }
}
