package cafe.woden.ircclient.ui.servertree.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerAutoConnectStore;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServerTreeServerLabelPolicyTest {

  @Test
  void prettyServerLabelAppendsAutoForAutoConnectEphemeralServer() {
    BouncerAutoConnectStore autoConnectStore = mock(BouncerAutoConnectStore.class);
    when(autoConnectStore.isEnabled("origin", "Pretty libera")).thenReturn(true);

    ServerTreeServerLabelPolicy policy = new ServerTreeServerLabelPolicy();
    ServerTreeServerLabelPolicy.Context context =
        ServerTreeServerLabelPolicy.context(
            Map.of("bouncer:origin:libera", "Pretty libera"),
            Set.of("bouncer:origin:libera"),
            Map.of(ServerTreeBouncerBackends.GENERIC, Map.of("bouncer:origin:libera", "origin")),
            Map.of(ServerTreeBouncerBackends.GENERIC, autoConnectStore));

    assertEquals(
        "Pretty libera (auto)", policy.prettyServerLabel(context, "bouncer:origin:libera"));
  }

  @Test
  void backendIdForEphemeralServerMatchesConfiguredBackendPrefix() {
    ServerTreeServerLabelPolicy policy = new ServerTreeServerLabelPolicy();
    ServerTreeServerLabelPolicy.Context context =
        ServerTreeServerLabelPolicy.context(
            Map.of(), Set.of("znc:origin:network"), Map.of(), Map.of());

    assertEquals(
        ServerTreeBouncerBackends.ZNC,
        policy.backendIdForEphemeralServer(context, "znc:origin:network"));
    assertNull(policy.backendIdForEphemeralServer(context, "libera"));
  }

  @Test
  void originForServerFallsBackToParsingCompoundServerId() {
    ServerTreeServerLabelPolicy policy = new ServerTreeServerLabelPolicy();
    ServerTreeServerLabelPolicy.Context context =
        ServerTreeServerLabelPolicy.context(
            Map.of(), Set.of("bouncer:origin:network"), Map.of(), Map.of());

    assertEquals(
        "origin",
        policy.originForServer(
            context, ServerTreeBouncerBackends.GENERIC, "bouncer:origin:network"));
  }
}
