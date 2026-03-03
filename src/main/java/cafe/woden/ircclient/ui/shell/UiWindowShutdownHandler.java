package cafe.woden.ircclient.ui.shell;

import java.awt.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Ensures Swing/AWT windows are disposed when the Spring context is shutting down. */
@Component
public class UiWindowShutdownHandler {
  private static final Logger log = LoggerFactory.getLogger(UiWindowShutdownHandler.class);

  @EventListener
  void onContextClosed(ContextClosedEvent ignoredEvent) {
    closeWindows(Window.getWindows());
  }

  void closeWindows(Window[] windows) {
    if (windows == null || windows.length == 0) return;
    for (Window window : windows) {
      if (window == null) continue;
      try {
        window.setVisible(false);
      } catch (Exception e) {
        log.debug("[ircafe] failed to hide window during shutdown", e);
      }
      try {
        window.dispose();
      } catch (Exception e) {
        log.debug("[ircafe] failed to dispose window during shutdown", e);
      }
    }
  }
}
