package cafe.woden.ircclient.ui.servertree.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ServerTreeNodeVisibilityApiTest {

  @Test
  void dccTransfersVisibilityChangeSyncsAndFiresPropertyChange() {
    ServerTreeBuiltInVisibilitySettings builtInVisibilitySettings =
        mock(ServerTreeBuiltInVisibilitySettings.class);
    ServerTreeBuiltInVisibilitySettings.Context builtInVisibilitySettingsContext =
        mock(ServerTreeBuiltInVisibilitySettings.Context.class);
    AtomicInteger syncUiCount = new AtomicInteger();
    AtomicReference<String> propertyName = new AtomicReference<>();
    AtomicReference<Boolean> oldValue = new AtomicReference<>();
    AtomicReference<Boolean> newValue = new AtomicReference<>();

    ServerTreeNodeVisibilityApi api =
        visibilityApi(
            builtInVisibilitySettings,
            builtInVisibilitySettingsContext,
            syncUiCount::incrementAndGet,
            () -> {},
            (prop, old, next) -> {
              propertyName.set(prop);
              oldValue.set(old);
              newValue.set(next);
            });

    assertFalse(api.isDccTransfersNodesVisible());
    api.setDccTransfersNodesVisible(true);

    assertTrue(api.isDccTransfersNodesVisible());
    assertEquals(1, syncUiCount.get());
    assertEquals("dccTransfersNodesVisible", propertyName.get());
    assertEquals(Boolean.FALSE, oldValue.get());
    assertEquals(Boolean.TRUE, newValue.get());

    api.setDccTransfersNodesVisible(true);
    assertEquals(1, syncUiCount.get());
  }

  @Test
  void applicationRootVisibilityChangeSyncsAndFiresPropertyChange() {
    ServerTreeBuiltInVisibilitySettings builtInVisibilitySettings =
        mock(ServerTreeBuiltInVisibilitySettings.class);
    ServerTreeBuiltInVisibilitySettings.Context builtInVisibilitySettingsContext =
        mock(ServerTreeBuiltInVisibilitySettings.Context.class);
    AtomicInteger syncAppCount = new AtomicInteger();
    AtomicReference<String> propertyName = new AtomicReference<>();

    ServerTreeNodeVisibilityApi api =
        visibilityApi(
            builtInVisibilitySettings,
            builtInVisibilitySettingsContext,
            () -> {},
            syncAppCount::incrementAndGet,
            (prop, old, next) -> propertyName.set(prop));

    assertTrue(api.isApplicationRootVisible());
    api.setApplicationRootVisible(false);

    assertFalse(api.isApplicationRootVisible());
    assertEquals(1, syncAppCount.get());
    assertEquals("applicationRootVisible", propertyName.get());

    api.setApplicationRootVisible(false);
    assertEquals(1, syncAppCount.get());
  }

  @Test
  void delegatesBuiltInVisibilityQueriesAndMutations() {
    ServerTreeBuiltInVisibilitySettings builtInVisibilitySettings =
        mock(ServerTreeBuiltInVisibilitySettings.class);
    ServerTreeBuiltInVisibilitySettings.Context builtInVisibilitySettingsContext =
        mock(ServerTreeBuiltInVisibilitySettings.Context.class);
    when(builtInVisibilitySettings.defaultVisibility(eq(builtInVisibilitySettingsContext), any()))
        .thenReturn(true);
    when(builtInVisibilitySettings.serverVisibility(
            eq(builtInVisibilitySettingsContext), anyString(), any()))
        .thenReturn(true);

    ServerTreeNodeVisibilityApi api =
        visibilityApi(
            builtInVisibilitySettings,
            builtInVisibilitySettingsContext,
            () -> {},
            () -> {},
            (prop, old, next) -> {});

    assertTrue(api.isServerNodesVisible());
    assertTrue(api.isLogViewerNodesVisible());
    assertTrue(api.isNotificationsNodesVisible());
    assertTrue(api.isMonitorNodesVisible());
    assertTrue(api.isInterceptorsNodesVisible());
    assertTrue(api.isServerNodeVisibleForServer("libera"));
    assertTrue(api.isLogViewerNodeVisibleForServer("libera"));
    assertTrue(api.isNotificationsNodeVisibleForServer("libera"));
    assertTrue(api.isMonitorNodeVisibleForServer("libera"));
    assertTrue(api.isInterceptorsNodeVisibleForServer("libera"));

    api.setServerNodeVisibleForServer("libera", true);
    api.setLogViewerNodeVisibleForServer("libera", true);
    api.setNotificationsNodeVisibleForServer("libera", true);
    api.setMonitorNodeVisibleForServer("libera", true);
    api.setInterceptorsNodeVisibleForServer("libera", true);
    api.setServerNodesVisible(true);
    api.setLogViewerNodesVisible(true);
    api.setNotificationsNodesVisible(true);
    api.setMonitorNodesVisible(true);
    api.setInterceptorsNodesVisible(true);

    verify(builtInVisibilitySettings, times(5))
        .setVisibilityForServer(
            eq(builtInVisibilitySettingsContext), eq("libera"), eq(true), any());
    verify(builtInVisibilitySettings, times(5))
        .setDefaultVisibility(eq(builtInVisibilitySettingsContext), eq(true), any(), any(), any());
  }

  @Test
  void channelListVisibilityFalseIsIgnored() {
    ServerTreeBuiltInVisibilitySettings builtInVisibilitySettings =
        mock(ServerTreeBuiltInVisibilitySettings.class);
    ServerTreeBuiltInVisibilitySettings.Context builtInVisibilitySettingsContext =
        mock(ServerTreeBuiltInVisibilitySettings.Context.class);
    AtomicInteger syncUiCount = new AtomicInteger();

    ServerTreeNodeVisibilityApi api =
        visibilityApi(
            builtInVisibilitySettings,
            builtInVisibilitySettingsContext,
            syncUiCount::incrementAndGet,
            () -> {},
            (prop, old, next) -> {});

    api.setChannelListNodesVisible(false);

    assertEquals(0, syncUiCount.get());
    verify(builtInVisibilitySettings, never())
        .setDefaultVisibility(any(), anyBoolean(), any(), any(), any());
  }

  private static ServerTreeNodeVisibilityApi visibilityApi(
      ServerTreeBuiltInVisibilitySettings builtInVisibilitySettings,
      ServerTreeBuiltInVisibilitySettings.Context builtInVisibilitySettingsContext,
      Runnable syncUiLeafVisibility,
      Runnable syncApplicationRootVisibility,
      ServerTreeNodeVisibilityApi.BooleanPropertyChangePublisher propertyChangePublisher) {
    return new ServerTreeNodeVisibilityApi(
        builtInVisibilitySettings,
        builtInVisibilitySettingsContext,
        syncUiLeafVisibility,
        syncApplicationRootVisibility,
        propertyChangePublisher,
        "channelListNodesVisible",
        "dccTransfersNodesVisible",
        "logViewerNodesVisible",
        "notificationsNodesVisible",
        "monitorNodesVisible",
        "interceptorsNodesVisible",
        "applicationRootVisible");
  }
}
