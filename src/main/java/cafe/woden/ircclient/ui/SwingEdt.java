package cafe.woden.ircclient.ui;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executor;

public final class SwingEdt {
  private SwingEdt() {}
  public static Scheduler scheduler() {
    Executor edt = SwingUtilities::invokeLater;
    return Schedulers.from(edt);
  }
}
