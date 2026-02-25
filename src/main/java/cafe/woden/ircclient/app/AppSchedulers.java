package cafe.woden.ircclient.app;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import javax.swing.SwingUtilities;

/** Shared schedulers for app-layer services. */
public final class AppSchedulers {

  private static final Scheduler EDT = Schedulers.from(SwingUtilities::invokeLater);

  private AppSchedulers() {}

  public static Scheduler edt() {
    return EDT;
  }
}
