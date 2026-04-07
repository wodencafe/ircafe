package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.servertree.state.ServerRuntimeMetadata;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerTreeNetworkInfoDialogBuilderTest {

  @Test
  void computeCapabilityFeatureStatusesMarksReadyWhenRequirementsAreEnabled() {
    ServerRuntimeMetadata metadata = new ServerRuntimeMetadata();
    metadata.ircv3Caps.put("message-tags", ServerRuntimeMetadata.CapabilityState.ENABLED);
    metadata.ircv3Caps.put("chathistory", ServerRuntimeMetadata.CapabilityState.ENABLED);

    List<ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus> statuses =
        ServerTreeNetworkInfoDialogBuilder.computeCapabilityFeatureStatuses(metadata);

    assertEquals("Ready", statusForFeature(statuses, "Replies").status());
    assertEquals("Ready", statusForFeature(statuses, "Reactions").status());
    assertEquals("Ready", statusForFeature(statuses, "Reaction removal").status());
    assertEquals("Ready", statusForFeature(statuses, "Typing").status());
    assertEquals("Ready", statusForFeature(statuses, "History").status());
    assertFalse(hasFeature(statuses, "Message edit"));
  }

  @Test
  void computeCapabilityFeatureStatusesIgnoreLegacyTagCapabilityNames() {
    ServerRuntimeMetadata metadata = new ServerRuntimeMetadata();
    metadata.ircv3Caps.put("message-tags", ServerRuntimeMetadata.CapabilityState.ENABLED);
    metadata.ircv3Caps.put("typing", ServerRuntimeMetadata.CapabilityState.DISABLED);
    metadata.ircv3Caps.put("draft/reply", ServerRuntimeMetadata.CapabilityState.DISABLED);
    metadata.ircv3Caps.put("draft/react", ServerRuntimeMetadata.CapabilityState.DISABLED);
    metadata.ircv3Caps.put("draft/unreact", ServerRuntimeMetadata.CapabilityState.DISABLED);

    List<ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus> statuses =
        ServerTreeNetworkInfoDialogBuilder.computeCapabilityFeatureStatuses(metadata);

    assertEquals("Ready", statusForFeature(statuses, "Replies").status());
    assertEquals("Ready", statusForFeature(statuses, "Reactions").status());
    assertEquals("Ready", statusForFeature(statuses, "Reaction removal").status());
    assertEquals("Ready", statusForFeature(statuses, "Typing").status());
  }

  @Test
  void computeCapabilityFeatureStatusesAcceptFinalMessageRedactionAlias() {
    ServerRuntimeMetadata metadata = new ServerRuntimeMetadata();
    metadata.ircv3Caps.put("message-redaction", ServerRuntimeMetadata.CapabilityState.ENABLED);

    List<ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus> statuses =
        ServerTreeNetworkInfoDialogBuilder.computeCapabilityFeatureStatuses(metadata);

    assertEquals("Ready", statusForFeature(statuses, "Message redaction").status());
  }

  @Test
  void computeCapabilityFeatureStatusesShowsPartialAndUnavailableStates() {
    ServerRuntimeMetadata metadata = new ServerRuntimeMetadata();
    metadata.ircv3Caps.put("message-tags", ServerRuntimeMetadata.CapabilityState.DISABLED);

    List<ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus> statuses =
        ServerTreeNetworkInfoDialogBuilder.computeCapabilityFeatureStatuses(metadata);

    ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus reactions =
        statusForFeature(statuses, "Reactions");
    ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus history =
        statusForFeature(statuses, "History");

    assertEquals("Unavailable", reactions.status());
    assertTrue(reactions.detail().contains("message-tags"));
    assertEquals("Unavailable", history.status());
    assertTrue(
        history.detail().contains("one of: chathistory, draft/chathistory, znc.in/playback"));
  }

  @Test
  void connectionInfoRowsIncludeBackendDisplayNameAndId() {
    ServerRuntimeMetadata metadata = new ServerRuntimeMetadata();
    metadata.connectedHost = "irc.example.net";
    metadata.connectedPort = 6697;
    metadata.nick = "tester";

    ServerTreeNetworkInfoDialogBuilder.Context context =
        ServerTreeNetworkInfoDialogBuilder.context(
            serverId -> ConnectionState.CONNECTED,
            serverId -> true,
            serverId -> "plugin-backend",
            serverId -> "Fancy Plugin",
            serverId -> "Plugin Server",
            serverId -> "",
            (serverId, capability, enable) -> {});

    List<ServerTreeNetworkInfoDialogBuilder.InfoRow> rows =
        ServerTreeNetworkInfoDialogBuilder.connectionInfoRows(context, "plugin", metadata);

    assertEquals("Fancy Plugin (plugin-backend)", rowValue(rows, "Backend"));
    assertEquals("Plugin Server", rowValue(rows, "Display"));
    assertEquals("irc.example.net:6697", rowValue(rows, "Connected endpoint"));
  }

  @Test
  void requestTokenForCapabilityUsesDraftTokensAndRejectsTagFeatures() throws Exception {
    assertEquals("draft/read-marker", requestTokenForCapability("read-marker"));
    assertEquals("draft/read-marker", requestTokenForCapability("draft/read-marker"));
    assertEquals("draft/multiline", requestTokenForCapability("multiline"));
    assertEquals("draft/chathistory", requestTokenForCapability("chathistory"));
    assertEquals("draft/message-redaction", requestTokenForCapability("message-redaction"));
    assertEquals("", requestTokenForCapability("typing"));
    assertEquals("", requestTokenForCapability("draft/reply"));
    assertEquals("", requestTokenForCapability("draft/react"));
    assertEquals("", requestTokenForCapability("draft/unreact"));
    assertEquals("", requestTokenForCapability("sts"));
    assertEquals("", requestTokenForCapability("message-edit"));
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

  private static boolean hasFeature(
      List<ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus> statuses, String feature) {
    for (ServerTreeNetworkInfoDialogBuilder.CapabilityFeatureStatus status : statuses) {
      if (feature.equals(status.feature())) {
        return true;
      }
    }
    return false;
  }

  private static String rowValue(
      List<ServerTreeNetworkInfoDialogBuilder.InfoRow> rows, String key) {
    for (ServerTreeNetworkInfoDialogBuilder.InfoRow row : rows) {
      if (key.equals(row.key())) {
        return row.value();
      }
    }
    throw new AssertionError("Missing row: " + key);
  }

  private static String requestTokenForCapability(String capability) throws Exception {
    Method method =
        ServerTreeNetworkInfoDialogBuilder.class.getDeclaredMethod(
            "requestTokenForCapability", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, capability);
  }
}
