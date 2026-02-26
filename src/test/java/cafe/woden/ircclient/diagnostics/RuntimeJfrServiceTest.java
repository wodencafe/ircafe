package cafe.woden.ircclient.diagnostics;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class RuntimeJfrServiceTest {

  @Test
  void statusReportAndCaptureSnapshotUseUnavailableMessageWhenJfrDisabled() throws Exception {
    RuntimeConfigStore runtimeConfigStore = mock(RuntimeConfigStore.class);
    JfrSnapshotSummarizer snapshotSummarizer = mock(JfrSnapshotSummarizer.class);
    RuntimeJfrService service = new RuntimeJfrService(runtimeConfigStore, snapshotSummarizer);

    setField(service, "unavailable", true);
    setField(service, "unavailableReason", "Unavailable for test");

    String report = service.statusReport();
    assertTrue(report.contains("JFR is unavailable on this runtime."));
    assertTrue(report.contains("Unavailable for test"));

    RuntimeJfrService.SnapshotReport snapshot = service.captureSnapshot();
    assertNull(snapshot.snapshotPath());
    assertTrue(snapshot.summary().contains("JFR is unavailable on this runtime."));
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
