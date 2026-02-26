package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.application.JfrDiagnosticsPanel;
import cafe.woden.ircclient.ui.application.RuntimeEventsPanel;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.dcc.DccTransfersPanel;
import cafe.woden.ircclient.ui.interceptors.InterceptorPanel;
import cafe.woden.ircclient.ui.logviewer.LogViewerPanel;
import cafe.woden.ircclient.ui.monitor.MonitorPanel;
import cafe.woden.ircclient.ui.notifications.NotificationsPanel;
import java.awt.CardLayout;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class ChatTargetViewRouterTest {

  @Test
  void routeChannelListRoutesToUiOnlyAndRefreshesManagedChannels() {
    NotificationsPanel notificationsPanel = mock(NotificationsPanel.class);
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    DccTransfersPanel dccTransfersPanel = mock(DccTransfersPanel.class);
    MonitorPanel monitorPanel = mock(MonitorPanel.class);
    LogViewerPanel logViewerPanel = mock(LogViewerPanel.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    RuntimeEventsPanel appAssertjPanel = mock(RuntimeEventsPanel.class);
    RuntimeEventsPanel appJhiccupPanel = mock(RuntimeEventsPanel.class);
    JfrDiagnosticsPanel appJfrPanel = mock(JfrDiagnosticsPanel.class);
    RuntimeEventsPanel appSpringPanel = mock(RuntimeEventsPanel.class);

    AtomicReference<String> refreshedServerId = new AtomicReference<>();
    AtomicReference<String> monitorRefreshedServerId = new AtomicReference<>();
    ChatTargetViewRouter router =
        new ChatTargetViewRouter(
            createCardDeck(),
            notificationsPanel,
            channelListPanel,
            dccTransfersPanel,
            monitorPanel,
            logViewerPanel,
            interceptorPanel,
            appAssertjPanel,
            appJhiccupPanel,
            appJfrPanel,
            appSpringPanel,
            refreshedServerId::set,
            monitorRefreshedServerId::set);

    ChatTargetViewRouter.TargetViewType viewType = router.route(TargetRef.channelList("libera"));

    assertEquals(ChatTargetViewRouter.TargetViewType.UI_ONLY, viewType);
    assertEquals("libera", refreshedServerId.get());
    verify(channelListPanel).setServerId("libera");
    verifyNoInteractions(monitorPanel);
  }

  @Test
  void routeMonitorGroupTrimsServerIdAndRefreshesMonitorRows() {
    NotificationsPanel notificationsPanel = mock(NotificationsPanel.class);
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    DccTransfersPanel dccTransfersPanel = mock(DccTransfersPanel.class);
    MonitorPanel monitorPanel = mock(MonitorPanel.class);
    LogViewerPanel logViewerPanel = mock(LogViewerPanel.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    RuntimeEventsPanel appAssertjPanel = mock(RuntimeEventsPanel.class);
    RuntimeEventsPanel appJhiccupPanel = mock(RuntimeEventsPanel.class);
    JfrDiagnosticsPanel appJfrPanel = mock(JfrDiagnosticsPanel.class);
    RuntimeEventsPanel appSpringPanel = mock(RuntimeEventsPanel.class);

    AtomicReference<String> refreshedServerId = new AtomicReference<>();
    AtomicReference<String> monitorRefreshedServerId = new AtomicReference<>();
    ChatTargetViewRouter router =
        new ChatTargetViewRouter(
            createCardDeck(),
            notificationsPanel,
            channelListPanel,
            dccTransfersPanel,
            monitorPanel,
            logViewerPanel,
            interceptorPanel,
            appAssertjPanel,
            appJhiccupPanel,
            appJfrPanel,
            appSpringPanel,
            refreshedServerId::set,
            monitorRefreshedServerId::set);

    ChatTargetViewRouter.TargetViewType viewType =
        router.route(new TargetRef("  libera  ", TargetRef.MONITOR_GROUP_TARGET));

    assertEquals(ChatTargetViewRouter.TargetViewType.UI_ONLY, viewType);
    assertEquals("libera", monitorRefreshedServerId.get());
    verify(monitorPanel).setServerId("libera");
    verifyNoInteractions(channelListPanel);
  }

  @Test
  void routeInterceptorTargetDelegatesToInterceptorPanel() {
    NotificationsPanel notificationsPanel = mock(NotificationsPanel.class);
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    DccTransfersPanel dccTransfersPanel = mock(DccTransfersPanel.class);
    MonitorPanel monitorPanel = mock(MonitorPanel.class);
    LogViewerPanel logViewerPanel = mock(LogViewerPanel.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    RuntimeEventsPanel appAssertjPanel = mock(RuntimeEventsPanel.class);
    RuntimeEventsPanel appJhiccupPanel = mock(RuntimeEventsPanel.class);
    JfrDiagnosticsPanel appJfrPanel = mock(JfrDiagnosticsPanel.class);
    RuntimeEventsPanel appSpringPanel = mock(RuntimeEventsPanel.class);

    ChatTargetViewRouter router =
        new ChatTargetViewRouter(
            createCardDeck(),
            notificationsPanel,
            channelListPanel,
            dccTransfersPanel,
            monitorPanel,
            logViewerPanel,
            interceptorPanel,
            appAssertjPanel,
            appJhiccupPanel,
            appJfrPanel,
            appSpringPanel,
            sid -> {},
            sid -> {});

    ChatTargetViewRouter.TargetViewType viewType =
        router.route(TargetRef.interceptor("libera", "audit-rules"));

    assertEquals(ChatTargetViewRouter.TargetViewType.UI_ONLY, viewType);
    verify(interceptorPanel).setInterceptorTarget("libera", "audit-rules");
  }

  @Test
  void routeChannelTargetKeepsTranscriptView() {
    NotificationsPanel notificationsPanel = mock(NotificationsPanel.class);
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    DccTransfersPanel dccTransfersPanel = mock(DccTransfersPanel.class);
    MonitorPanel monitorPanel = mock(MonitorPanel.class);
    LogViewerPanel logViewerPanel = mock(LogViewerPanel.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    RuntimeEventsPanel appAssertjPanel = mock(RuntimeEventsPanel.class);
    RuntimeEventsPanel appJhiccupPanel = mock(RuntimeEventsPanel.class);
    JfrDiagnosticsPanel appJfrPanel = mock(JfrDiagnosticsPanel.class);
    RuntimeEventsPanel appSpringPanel = mock(RuntimeEventsPanel.class);

    ChatTargetViewRouter router =
        new ChatTargetViewRouter(
            createCardDeck(),
            notificationsPanel,
            channelListPanel,
            dccTransfersPanel,
            monitorPanel,
            logViewerPanel,
            interceptorPanel,
            appAssertjPanel,
            appJhiccupPanel,
            appJfrPanel,
            appSpringPanel,
            sid -> {},
            sid -> {});

    ChatTargetViewRouter.TargetViewType viewType = router.route(new TargetRef("libera", "#ircafe"));

    assertEquals(ChatTargetViewRouter.TargetViewType.TRANSCRIPT, viewType);
    verifyNoInteractions(
        notificationsPanel,
        channelListPanel,
        dccTransfersPanel,
        monitorPanel,
        logViewerPanel,
        interceptorPanel,
        appAssertjPanel,
        appJhiccupPanel,
        appJfrPanel,
        appSpringPanel);
  }

  @Test
  void routeApplicationJfrRefreshesPanelAndUsesUiOnlyView() {
    NotificationsPanel notificationsPanel = mock(NotificationsPanel.class);
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    DccTransfersPanel dccTransfersPanel = mock(DccTransfersPanel.class);
    MonitorPanel monitorPanel = mock(MonitorPanel.class);
    LogViewerPanel logViewerPanel = mock(LogViewerPanel.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    RuntimeEventsPanel appAssertjPanel = mock(RuntimeEventsPanel.class);
    RuntimeEventsPanel appJhiccupPanel = mock(RuntimeEventsPanel.class);
    JfrDiagnosticsPanel appJfrPanel = mock(JfrDiagnosticsPanel.class);
    RuntimeEventsPanel appSpringPanel = mock(RuntimeEventsPanel.class);

    ChatTargetViewRouter router =
        new ChatTargetViewRouter(
            createCardDeck(),
            notificationsPanel,
            channelListPanel,
            dccTransfersPanel,
            monitorPanel,
            logViewerPanel,
            interceptorPanel,
            appAssertjPanel,
            appJhiccupPanel,
            appJfrPanel,
            appSpringPanel,
            sid -> {},
            sid -> {});

    ChatTargetViewRouter.TargetViewType viewType = router.route(TargetRef.applicationJfr());

    assertEquals(ChatTargetViewRouter.TargetViewType.UI_ONLY, viewType);
    verify(appJfrPanel).refreshNow();
  }

  @Test
  void routeNotificationsSetsServerIdOnNotificationsPanel() {
    NotificationsPanel notificationsPanel = mock(NotificationsPanel.class);
    ChannelListPanel channelListPanel = mock(ChannelListPanel.class);
    DccTransfersPanel dccTransfersPanel = mock(DccTransfersPanel.class);
    MonitorPanel monitorPanel = mock(MonitorPanel.class);
    LogViewerPanel logViewerPanel = mock(LogViewerPanel.class);
    InterceptorPanel interceptorPanel = mock(InterceptorPanel.class);
    RuntimeEventsPanel appAssertjPanel = mock(RuntimeEventsPanel.class);
    RuntimeEventsPanel appJhiccupPanel = mock(RuntimeEventsPanel.class);
    JfrDiagnosticsPanel appJfrPanel = mock(JfrDiagnosticsPanel.class);
    RuntimeEventsPanel appSpringPanel = mock(RuntimeEventsPanel.class);

    ChatTargetViewRouter router =
        new ChatTargetViewRouter(
            createCardDeck(),
            notificationsPanel,
            channelListPanel,
            dccTransfersPanel,
            monitorPanel,
            logViewerPanel,
            interceptorPanel,
            appAssertjPanel,
            appJhiccupPanel,
            appJfrPanel,
            appSpringPanel,
            sid -> {},
            sid -> {});

    ChatTargetViewRouter.TargetViewType viewType = router.route(TargetRef.notifications("libera"));

    assertEquals(ChatTargetViewRouter.TargetViewType.UI_ONLY, viewType);
    verify(notificationsPanel).setServerId("libera");
  }

  private static JPanel createCardDeck() {
    JPanel deck = new JPanel(new CardLayout());
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_TRANSCRIPT);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_NOTIFICATIONS);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_CHANNEL_LIST);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_DCC_TRANSFERS);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_MONITOR);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_LOG_VIEWER);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_INTERCEPTOR);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_APP_ASSERTJ);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_APP_JHICCUP);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_APP_JFR);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_APP_SPRING);
    deck.add(new JPanel(), ChatTargetViewRouter.CARD_TERMINAL);
    return deck;
  }
}
