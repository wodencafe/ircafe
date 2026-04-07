package cafe.woden.ircclient.ui.shell;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.api.UiShellRuntimeConfigPort;
import cafe.woden.ircclient.ui.ExternalBrowserLauncher;
import org.junit.jupiter.api.Test;

class UpdateNotifierServiceBrowserRoutingTest {

  private static final String RELEASES_URL = "https://github.com/wodencafe/ircafe/releases";

  @Test
  void workerDelegatesToSharedBrowserLauncher() {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    ExternalBrowserLauncher browserLauncher = mock(ExternalBrowserLauncher.class);
    when(browserLauncher.open(RELEASES_URL)).thenReturn(true);
    UpdateNotifierService service =
        new UpdateNotifierService(runtimeConfig, statusBar, browserLauncher);

    try {
      service.openReleasesPageOnWorker();

      verify(browserLauncher).open(RELEASES_URL);
      verify(statusBar, never()).enqueueNotification("Could not open browser for updates.", null);
    } finally {
      service.shutdown();
    }
  }

  @Test
  void workerNotifiesWhenSharedBrowserLauncherFails() {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    ExternalBrowserLauncher browserLauncher = mock(ExternalBrowserLauncher.class);
    when(browserLauncher.open(RELEASES_URL)).thenReturn(false);
    UpdateNotifierService service =
        new UpdateNotifierService(runtimeConfig, statusBar, browserLauncher);

    try {
      service.openReleasesPageOnWorker();

      verify(browserLauncher).open(RELEASES_URL);
      verify(statusBar).enqueueNotification("Could not open browser for updates.", null);
    } finally {
      service.shutdown();
    }
  }
}
