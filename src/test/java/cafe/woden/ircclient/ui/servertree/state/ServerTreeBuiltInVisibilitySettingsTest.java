package cafe.woden.ircclient.ui.servertree.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ServerTreeBuiltInVisibilitySettingsTest {

  @Test
  void setVisibilityForServerAppliesUpdatedState() {
    TestContext context = new TestContext();
    context.perServer.put("libera", ServerBuiltInNodesVisibility.defaults());
    ServerTreeBuiltInVisibilitySettings settings = new ServerTreeBuiltInVisibilitySettings(context);

    settings.setVisibilityForServer(
        "  libera  ", false, ServerBuiltInNodesVisibility::withNotifications);

    assertFalse(context.perServer.get("libera").notifications());
    assertEquals("libera", context.lastAppliedServerId);
    assertTrue(context.lastApplyPersist);
    assertTrue(context.lastApplySyncUi);
  }

  @Test
  void setDefaultVisibilityFiresPropertyChangeAndUpdatesServers() {
    TestContext context = new TestContext();
    context.perServer.put("libera", ServerBuiltInNodesVisibility.defaults());
    context.perServer.put("oftc", ServerBuiltInNodesVisibility.defaults().withMonitor(false));
    ServerTreeBuiltInVisibilitySettings settings = new ServerTreeBuiltInVisibilitySettings(context);

    settings.setDefaultVisibility(
        false,
        ServerBuiltInNodesVisibility::monitor,
        ServerBuiltInNodesVisibility::withMonitor,
        "monitorNodesVisible");

    assertFalse(context.defaultVisibility.monitor());
    assertFalse(context.perServer.get("libera").monitor());
    assertFalse(context.perServer.get("oftc").monitor());
    assertEquals("monitorNodesVisible", context.firedPropertyName.get());
    assertTrue(context.firedOldValue.get());
    assertFalse(context.firedNewValue.get());
  }

  @Test
  void defaultAndServerVisibilityReadThroughGetters() {
    TestContext context = new TestContext();
    context.defaultVisibility = context.defaultVisibility.withLogViewer(false);
    context.perServer.put("libera", context.defaultVisibility.withInterceptors(false));
    ServerTreeBuiltInVisibilitySettings settings = new ServerTreeBuiltInVisibilitySettings(context);

    assertFalse(settings.defaultVisibility(ServerBuiltInNodesVisibility::logViewer));
    assertFalse(settings.serverVisibility("libera", ServerBuiltInNodesVisibility::interceptors));
    assertTrue(settings.serverVisibility("unknown", ServerBuiltInNodesVisibility::interceptors));
  }

  private static final class TestContext implements ServerTreeBuiltInVisibilitySettings.Context {
    private final Map<String, ServerBuiltInNodesVisibility> perServer = new HashMap<>();
    private final AtomicReference<String> firedPropertyName = new AtomicReference<>();
    private final AtomicReference<Boolean> firedOldValue = new AtomicReference<>();
    private final AtomicReference<Boolean> firedNewValue = new AtomicReference<>();
    private ServerBuiltInNodesVisibility defaultVisibility = ServerBuiltInNodesVisibility.defaults();
    private String lastAppliedServerId;
    private boolean lastApplyPersist;
    private boolean lastApplySyncUi;

    @Override
    public String normalizeServerId(String serverId) {
      return serverId == null ? "" : serverId.trim();
    }

    @Override
    public ServerBuiltInNodesVisibility defaultVisibility() {
      return defaultVisibility;
    }

    @Override
    public void setDefaultVisibility(ServerBuiltInNodesVisibility next) {
      defaultVisibility = next;
    }

    @Override
    public ServerBuiltInNodesVisibility visibilityForServer(String serverId) {
      return perServer.getOrDefault(normalizeServerId(serverId), defaultVisibility);
    }

    @Override
    public void applyVisibilityForServer(
        String serverId, ServerBuiltInNodesVisibility next, boolean persist, boolean syncUi) {
      String sid = normalizeServerId(serverId);
      if (sid.isEmpty() || next == null) return;
      perServer.put(sid, next);
      lastAppliedServerId = sid;
      lastApplyPersist = persist;
      lastApplySyncUi = syncUi;
    }

    @Override
    public void applyVisibilityGlobally(
        java.util.function.UnaryOperator<ServerBuiltInNodesVisibility> mutator) {
      for (Map.Entry<String, ServerBuiltInNodesVisibility> entry : perServer.entrySet()) {
        ServerBuiltInNodesVisibility current = entry.getValue();
        entry.setValue(mutator.apply(current));
      }
    }

    @Override
    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
      firedPropertyName.set(propertyName);
      firedOldValue.set(oldValue);
      firedNewValue.set(newValue);
    }
  }
}
