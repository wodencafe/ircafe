package cafe.woden.ircclient.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.config.api.UiShellRuntimeConfigPort;
import org.junit.jupiter.api.Test;

class UpdateNotifierServiceOpenReleasesAsyncTest {

  @Test
  void openReleasesPageSchedulesDesktopBrowseOnWorker() {
    UiShellRuntimeConfigPort runtimeConfig = mock(UiShellRuntimeConfigPort.class);
    StatusBar statusBar = mock(StatusBar.class);
    TestableUpdateNotifierService service =
        new TestableUpdateNotifierService(runtimeConfig, statusBar);

    try {
      service.openReleasesPage();

      assertEquals(1, service.executeBackgroundCalls);
      assertNotNull(service.lastScheduledTask);
      assertEquals(0, service.openOnWorkerCalls);

      Runnable task = service.lastScheduledTask;
      service.lastScheduledTask = null;
      task.run();
      assertEquals(1, service.openOnWorkerCalls);
      assertNull(service.lastScheduledTask);
    } finally {
      service.shutdown();
    }
  }

  private static final class TestableUpdateNotifierService extends UpdateNotifierService {
    private Runnable lastScheduledTask;
    private int executeBackgroundCalls;
    private int openOnWorkerCalls;

    private TestableUpdateNotifierService(
        UiShellRuntimeConfigPort runtimeConfig, StatusBar statusBar) {
      super(runtimeConfig, statusBar);
    }

    @Override
    protected void executeBackground(Runnable task) {
      executeBackgroundCalls++;
      lastScheduledTask = task;
    }

    @Override
    protected void openReleasesPageOnWorker() {
      openOnWorkerCalls++;
    }
  }
}
