package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** Owns dock/tab title resolution and refresh behavior for {@link ChatDockable}. */
final class ChatDockTitleCoordinator {

  private static final String DEFAULT_TITLE = "Chat";

  private final Component dockComponent;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final InterceptorStore interceptorStore;
  private final Supplier<String> dockNameSupplier;
  private final Consumer<String> dockNameSetter;
  private final Consumer<Runnable> laterInvoker;

  ChatDockTitleCoordinator(
      Component dockComponent,
      Supplier<TargetRef> activeTargetSupplier,
      InterceptorStore interceptorStore,
      Supplier<String> dockNameSupplier,
      Consumer<String> dockNameSetter,
      Consumer<Runnable> laterInvoker) {
    this.dockComponent = Objects.requireNonNull(dockComponent, "dockComponent");
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.interceptorStore = Objects.requireNonNull(interceptorStore, "interceptorStore");
    this.dockNameSupplier = Objects.requireNonNull(dockNameSupplier, "dockNameSupplier");
    this.dockNameSetter = Objects.requireNonNull(dockNameSetter, "dockNameSetter");
    this.laterInvoker = Objects.requireNonNull(laterInvoker, "laterInvoker");
  }

  String tabText() {
    TargetRef target = activeTargetSupplier.get();
    if (target == null) return DEFAULT_TITLE;
    if (target.isNotifications()) return "Notifications";
    if (target.isChannelList()) return "Channel List";
    if (target.isWeechatFilters()) return "Filters";
    if (target.isDccTransfers()) return "DCC Transfers";
    if (target.isMonitorGroup()) return "Monitor";
    if (target.isInterceptorsGroup()) return "Interceptors";
    if (target.isApplicationUnhandledErrors()) return "Unhandled Errors";
    if (target.isApplicationAssertjSwing()) return "AssertJ Swing";
    if (target.isApplicationJhiccup()) return "jHiccup";
    if (target.isApplicationJfr()) return "JFR";
    if (target.isApplicationSpring()) return "Spring";
    if (target.isApplicationTerminal()) return "Terminal";
    if (target.isLogViewer()) return "Log Viewer";
    if (target.isInterceptor()) {
      String name = interceptorStore.interceptorName(target.serverId(), target.interceptorId());
      return (name == null || name.isBlank()) ? "Interceptor" : name;
    }
    if (target.isStatus()) return "Server";
    String name = target.target();
    if (name == null || name.isBlank()) return DEFAULT_TITLE;
    return name;
  }

  void updateDockTitle() {
    try {
      String title = normalizedTitle();
      if (!Objects.equals(dockNameSupplier.get(), title)) {
        dockNameSetter.accept(title);
      }

      // ModernDocking caches Dockable#getTabText when rendering tab groups.
      // Some versions expose Docking.updateTabText(...) for this, but the single-app
      // facade doesn't always include it. So we defensively update the tab title
      // ourselves when we're inside a JTabbedPane.
      laterInvoker.accept(this::updateTabTitleIfTabbed);
    } catch (Exception ignored) {
    }
  }

  private String normalizedTitle() {
    String title = tabText();
    if (title == null || title.isBlank()) return DEFAULT_TITLE;
    return title;
  }

  private void updateTabTitleIfTabbed() {
    try {
      String title = normalizedTitle();

      JTabbedPane tabs =
          (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, dockComponent);
      if (tabs == null) return;

      int idx = tabs.indexOfComponent(dockComponent);
      if (idx < 0) {
        // Dockables are sometimes wrapped; locate the tab whose component contains us.
        for (int i = 0; i < tabs.getTabCount(); i++) {
          Component c = tabs.getComponentAt(i);
          if (c == null) continue;
          if (c == dockComponent || SwingUtilities.isDescendingFrom(dockComponent, c)) {
            idx = i;
            break;
          }
        }
      }

      if (idx >= 0 && idx < tabs.getTabCount()) {
        if (!Objects.equals(tabs.getTitleAt(idx), title)) {
          tabs.setTitleAt(idx, title);
        }
      }
    } catch (Exception ignored) {
    }
  }
}
