package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.interceptors.InterceptorScope;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.localization.UiMessages;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/** Owns dock/tab title resolution and refresh behavior for {@link ChatDockable}. */
public final class ChatDockTitleCoordinator {

  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();
  private static final String DEFAULT_TITLE_KEY = "chatDock.title.chat";

  private final Component dockComponent;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final InterceptorStore interceptorStore;
  private final Supplier<String> dockNameSupplier;
  private final Consumer<String> dockNameSetter;
  private final Consumer<Runnable> laterInvoker;
  private final Runnable dockingTabInfoUpdater;

  public ChatDockTitleCoordinator(
      Component dockComponent,
      Supplier<TargetRef> activeTargetSupplier,
      InterceptorStore interceptorStore,
      Supplier<String> dockNameSupplier,
      Consumer<String> dockNameSetter,
      Consumer<Runnable> laterInvoker) {
    this(
        dockComponent,
        activeTargetSupplier,
        interceptorStore,
        dockNameSupplier,
        dockNameSetter,
        laterInvoker,
        () -> {});
  }

  public ChatDockTitleCoordinator(
      Component dockComponent,
      Supplier<TargetRef> activeTargetSupplier,
      InterceptorStore interceptorStore,
      Supplier<String> dockNameSupplier,
      Consumer<String> dockNameSetter,
      Consumer<Runnable> laterInvoker,
      Runnable dockingTabInfoUpdater) {
    this.dockComponent = Objects.requireNonNull(dockComponent, "dockComponent");
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.interceptorStore = Objects.requireNonNull(interceptorStore, "interceptorStore");
    this.dockNameSupplier = Objects.requireNonNull(dockNameSupplier, "dockNameSupplier");
    this.dockNameSetter = Objects.requireNonNull(dockNameSetter, "dockNameSetter");
    this.laterInvoker = Objects.requireNonNull(laterInvoker, "laterInvoker");
    this.dockingTabInfoUpdater =
        Objects.requireNonNull(dockingTabInfoUpdater, "dockingTabInfoUpdater");
  }

  public String tabText() {
    TargetRef target = activeTargetSupplier.get();
    if (target == null) return defaultTitle();
    if (target.isNotifications()) return title("chatDock.title.notifications");
    if (target.isChannelList()) return title("chatDock.title.channelList");
    if (target.isWeechatFilters()) return title("chatDock.title.filters");
    if (target.isIgnores()) return title("chatDock.title.ignores");
    if (target.isDccTransfers()) return title("chatDock.title.dccTransfers");
    if (target.isDccChat()) return MESSAGES.text("chatDock.title.dccChat", target.dccChatNick());
    if (target.isMonitorGroup()) return title("chatDock.title.monitor");
    if (target.isInterceptorsGroup()) return title("chatDock.title.interceptors");
    if (target.isApplicationUnhandledErrors()) return title("chatDock.title.unhandledErrors");
    if (target.isApplicationAssertjSwing()) return title("chatDock.title.assertjSwing");
    if (target.isApplicationJhiccup()) return title("chatDock.title.jhiccup");
    if (target.isApplicationInboundDedup()) return title("chatDock.title.inboundDedup");
    if (target.isApplicationPlugins()) return title("chatDock.title.plugins");
    if (target.isApplicationJfr()) return title("chatDock.title.jfr");
    if (target.isApplicationSpring()) return title("chatDock.title.spring");
    if (target.isApplicationTerminal()) return title("chatDock.title.terminal");
    if (target.isLogViewer()) return title("chatDock.title.logViewer");
    if (target.isInterceptor()) {
      String scopeServerId = InterceptorScope.scopedServerIdForTarget(target);
      String name = interceptorStore.interceptorName(scopeServerId, target.interceptorId());
      return (name == null || name.isBlank()) ? title("chatDock.title.interceptor") : name;
    }
    if (target.isStatus()) return title("chatDock.title.server");
    String name = target.target();
    if (name == null || name.isBlank()) return defaultTitle();
    return name;
  }

  public void updateDockTitle() {
    try {
      String title = normalizedTitle();
      if (!Objects.equals(dockNameSupplier.get(), title)) {
        dockNameSetter.accept(title);
      }

      // Let the docking framework refresh header and tab labels when possible.
      try {
        dockingTabInfoUpdater.run();
      } catch (Exception ignored) {
      }

      // Fallback for plain Swing tab containers and wrapper panels.
      laterInvoker.accept(this::updateTabTitleIfTabbed);
    } catch (Exception ignored) {
    }
  }

  private String normalizedTitle() {
    String title = tabText();
    if (title == null || title.isBlank()) return defaultTitle();
    return title;
  }

  private static String defaultTitle() {
    return title(DEFAULT_TITLE_KEY);
  }

  private static String title(String code) {
    return MESSAGES.text(code);
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
