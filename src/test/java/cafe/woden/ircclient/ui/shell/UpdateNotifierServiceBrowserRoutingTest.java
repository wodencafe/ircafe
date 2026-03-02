package cafe.woden.ircclient.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateNotifierServiceBrowserRoutingTest {

  @Test
  void linuxPrefersKnownBrowserBeforeDesktopBrowse() {
    StatusBar statusBar = mock(StatusBar.class);
    TestableService service = new TestableService(statusBar, "linux");
    service.succeedCommandPrefix = "firefox";

    try {
      service.openReleasesPageOnWorker();

      assertTrue(service.attemptedCommands.stream().anyMatch(cmd -> cmd.startsWith("firefox ")));
      assertFalse(service.desktopBrowseCalled);
    } finally {
      service.shutdown();
    }
  }

  @Test
  void linuxFallsBackToDesktopWhenPlatformOpenFails() {
    StatusBar statusBar = mock(StatusBar.class);
    TestableService service = new TestableService(statusBar, "linux");
    service.desktopBrowseResult = true;

    try {
      service.openReleasesPageOnWorker();

      assertTrue(service.desktopBrowseCalled);
    } finally {
      service.shutdown();
    }
  }

  @Test
  void workerNotifiesWhenNoLaunchStrategySucceeds() {
    StatusBar statusBar = mock(StatusBar.class);
    TestableService service = new TestableService(statusBar, "linux");

    try {
      service.openReleasesPageOnWorker();

      verify(statusBar).enqueueNotification("Could not open browser for updates.", null);
      assertTrue(service.desktopBrowseCalled);
      assertEquals(0, service.openedCommandCount);
    } finally {
      service.shutdown();
    }
  }

  private static final class TestableService extends UpdateNotifierService {
    private final String osLower;
    private final List<String> attemptedCommands = new ArrayList<>();
    private boolean desktopBrowseCalled;
    private boolean desktopBrowseResult;
    private String succeedCommandPrefix;
    private int openedCommandCount;

    private TestableService(StatusBar statusBar, String osLower) {
      super(mock(RuntimeConfigStore.class), statusBar);
      this.osLower = osLower;
    }

    @Override
    protected String currentOsLowerCase() {
      return osLower;
    }

    @Override
    protected boolean tryDesktopBrowse(String url) {
      desktopBrowseCalled = true;
      return desktopBrowseResult;
    }

    @Override
    protected boolean tryStart(String... cmd) {
      String joined = String.join(" ", Arrays.asList(cmd));
      attemptedCommands.add(joined);
      if (succeedCommandPrefix != null && cmd.length > 0 && cmd[0].equals(succeedCommandPrefix)) {
        openedCommandCount++;
        return true;
      }
      return false;
    }
  }
}
