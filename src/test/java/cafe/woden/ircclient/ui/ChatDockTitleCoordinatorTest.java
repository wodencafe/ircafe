package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import java.awt.BorderLayout;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.Test;

class ChatDockTitleCoordinatorTest {

  @Test
  void tabTextUsesExpectedBuiltInTitles() {
    AtomicReference<TargetRef> activeTarget = new AtomicReference<>();
    AtomicReference<String> dockName = new AtomicReference<>("old");
    ChatDockTitleCoordinator coordinator =
        new ChatDockTitleCoordinator(
            new JPanel(),
            activeTarget::get,
            mock(InterceptorStore.class),
            dockName::get,
            dockName::set,
            Runnable::run);

    assertEquals("Chat", coordinator.tabText());

    activeTarget.set(TargetRef.channelList("libera"));
    assertEquals("Channel List", coordinator.tabText());

    activeTarget.set(new TargetRef("libera", "status"));
    assertEquals("Server", coordinator.tabText());

    activeTarget.set(new TargetRef("libera", "#ircafe"));
    assertEquals("#ircafe", coordinator.tabText());
  }

  @Test
  void tabTextUsesInterceptorNameAndFallback() {
    AtomicReference<TargetRef> activeTarget =
        new AtomicReference<>(TargetRef.interceptor("libera", "audit"));
    AtomicReference<String> dockName = new AtomicReference<>("old");
    InterceptorStore interceptorStore = mock(InterceptorStore.class);
    when(interceptorStore.interceptorName("libera", "audit")).thenReturn("Audit Rule");

    ChatDockTitleCoordinator coordinator =
        new ChatDockTitleCoordinator(
            new JPanel(),
            activeTarget::get,
            interceptorStore,
            dockName::get,
            dockName::set,
            Runnable::run);

    assertEquals("Audit Rule", coordinator.tabText());

    when(interceptorStore.interceptorName("libera", "audit")).thenReturn(" ");
    assertEquals("Interceptor", coordinator.tabText());
  }

  @Test
  void updateDockTitleUpdatesNameAndDirectTabbedTitle() {
    AtomicReference<TargetRef> activeTarget =
        new AtomicReference<>(new TargetRef("libera", "#ircafe"));
    AtomicReference<String> dockName = new AtomicReference<>("old");
    JPanel dock = new JPanel();
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Old Tab", dock);

    ChatDockTitleCoordinator coordinator =
        new ChatDockTitleCoordinator(
            dock,
            activeTarget::get,
            mock(InterceptorStore.class),
            dockName::get,
            dockName::set,
            Runnable::run);

    coordinator.updateDockTitle();

    assertEquals("#ircafe", dockName.get());
    assertEquals("#ircafe", tabs.getTitleAt(0));
  }

  @Test
  void updateDockTitleUpdatesWrappedTabbedTitle() {
    AtomicReference<TargetRef> activeTarget =
        new AtomicReference<>(TargetRef.notifications("libera"));
    AtomicReference<String> dockName = new AtomicReference<>("old");
    JPanel dock = new JPanel();
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(dock, BorderLayout.CENTER);
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Old Tab", wrapper);

    ChatDockTitleCoordinator coordinator =
        new ChatDockTitleCoordinator(
            dock,
            activeTarget::get,
            mock(InterceptorStore.class),
            dockName::get,
            dockName::set,
            Runnable::run);

    coordinator.updateDockTitle();

    assertEquals("Notifications", dockName.get());
    assertEquals("Notifications", tabs.getTitleAt(0));
  }
}
