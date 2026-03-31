package cafe.woden.ircclient.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class BackendAvailabilityReasonFormatterTest {

  @Test
  void builtInMetadataUsesBuiltInDisplayNamesAndLabels() {
    AvailableBackendIdsPort metadata = AvailableBackendIdsPort.builtInsOnly();

    assertEquals("Quassel Core", metadata.backendDisplayName("quassel-core"));
    assertEquals("Quassel Core backend", metadata.backendDisplayLabel("quassel-core"));
  }

  @Test
  void decorateUsesPluginDisplayLabelWhenProvided() {
    AvailableBackendIdsPort metadata =
        new AvailableBackendIdsPort() {
          @Override
          public List<String> availableBackendIds() {
            return List.of("plugin-backend");
          }

          @Override
          public String backendDisplayName(String backendId) {
            return "Fancy Plugin";
          }
        };

    assertEquals(
        "Fancy Plugin backend: not connected",
        BackendAvailabilityReasonFormatter.decorate("plugin-backend", "not connected", metadata));
  }

  @Test
  void decorateLeavesReasonUnchangedWhenBackendIsUnknown() {
    assertEquals(
        "not connected",
        BackendAvailabilityReasonFormatter.decorate(
            "", "not connected", AvailableBackendIdsPort.builtInsOnly()));
  }
}
