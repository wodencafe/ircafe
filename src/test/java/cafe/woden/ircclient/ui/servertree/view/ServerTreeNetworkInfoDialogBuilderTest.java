package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.servertree.state.ServerRuntimeMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerTreeNetworkInfoDialogBuilderTest {

  @Test
  void computeCapabilityFeatureStatusesMarksReadyWhenRequirementsAreEnabled() {
    ServerRuntimeMetadata metadata = new ServerRuntimeMetadata();
    metadata.ircv3Caps.put("draft/reply", ServerRuntimeMetadata.CapabilityState.ENABLED);
    metadata.ircv3Caps.put("draft/react", ServerRuntimeMetadata.CapabilityState.ENABLED);
    metadata.ircv3Caps.put("draft/unreact", ServerRuntimeMetadata.CapabilityState.ENABLED);
    metadata.ircv3Caps.put("chathistory", ServerRuntimeMetadata.CapabilityState.ENABLED);

    List<ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus> statuses =
        ServerTreeNetworkInfoDialogBuilder.computeCapabilityFeatureStatuses(metadata);

    assertEquals("Ready", statusForFeature(statuses, "Replies").status());
    assertEquals("Ready", statusForFeature(statuses, "Reactions").status());
    assertEquals("Ready", statusForFeature(statuses, "Reaction removal").status());
    assertEquals("Ready", statusForFeature(statuses, "History").status());
  }

  @Test
  void computeCapabilityFeatureStatusesShowsPartialAndUnavailableStates() {
    ServerRuntimeMetadata metadata = new ServerRuntimeMetadata();
    metadata.ircv3Caps.put("draft/reply", ServerRuntimeMetadata.CapabilityState.ENABLED);
    metadata.ircv3Caps.put("draft/react", ServerRuntimeMetadata.CapabilityState.DISABLED);

    List<ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus> statuses =
        ServerTreeNetworkInfoDialogBuilder.computeCapabilityFeatureStatuses(metadata);

    ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus reactions =
        statusForFeature(statuses, "Reactions");
    ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus history =
        statusForFeature(statuses, "History");

    assertEquals("Partial", reactions.status());
    assertTrue(reactions.detail().contains("draft/react"));
    assertEquals("Unavailable", history.status());
    assertTrue(
        history.detail().contains("one of: chathistory, draft/chathistory, znc.in/playback"));
  }

  private static ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus statusForFeature(
      List<ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus> statuses, String feature) {
    for (ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus status : statuses) {
      if (feature.equals(status.feature())) {
        return status;
      }
    }
    throw new AssertionError("Missing feature row: " + feature);
  }
}
