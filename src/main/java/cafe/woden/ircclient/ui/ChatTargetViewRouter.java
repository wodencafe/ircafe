package cafe.woden.ircclient.ui;

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
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JPanel;

/** Routes active targets to the correct center card in {@link ChatDockable}. */
final class ChatTargetViewRouter {

  static final String CARD_TRANSCRIPT = "transcript";
  static final String CARD_NOTIFICATIONS = "notifications";
  static final String CARD_CHANNEL_LIST = "channel-list";
  static final String CARD_DCC_TRANSFERS = "dcc-transfers";
  static final String CARD_MONITOR = "monitor";
  static final String CARD_LOG_VIEWER = "log-viewer";
  static final String CARD_INTERCEPTOR = "interceptor";
  static final String CARD_APP_ASSERTJ = "app-assertj";
  static final String CARD_APP_JHICCUP = "app-jhiccup";
  static final String CARD_APP_JFR = "app-jfr";
  static final String CARD_APP_SPRING = "app-spring";
  static final String CARD_TERMINAL = "terminal";

  enum TargetViewType {
    TRANSCRIPT,
    UI_ONLY
  }

  private final JPanel centerCards;
  private final NotificationsPanel notificationsPanel;
  private final ChannelListPanel channelListPanel;
  private final DccTransfersPanel dccTransfersPanel;
  private final MonitorPanel monitorPanel;
  private final LogViewerPanel logViewerPanel;
  private final InterceptorPanel interceptorPanel;
  private final RuntimeEventsPanel appAssertjPanel;
  private final RuntimeEventsPanel appJhiccupPanel;
  private final JfrDiagnosticsPanel appJfrPanel;
  private final RuntimeEventsPanel appSpringPanel;
  private final Consumer<String> managedChannelRefresher;
  private final Consumer<String> monitorRowsRefresher;

  ChatTargetViewRouter(
      JPanel centerCards,
      NotificationsPanel notificationsPanel,
      ChannelListPanel channelListPanel,
      DccTransfersPanel dccTransfersPanel,
      MonitorPanel monitorPanel,
      LogViewerPanel logViewerPanel,
      InterceptorPanel interceptorPanel,
      RuntimeEventsPanel appAssertjPanel,
      RuntimeEventsPanel appJhiccupPanel,
      JfrDiagnosticsPanel appJfrPanel,
      RuntimeEventsPanel appSpringPanel,
      Consumer<String> managedChannelRefresher,
      Consumer<String> monitorRowsRefresher) {
    this.centerCards = Objects.requireNonNull(centerCards, "centerCards");
    this.notificationsPanel = Objects.requireNonNull(notificationsPanel, "notificationsPanel");
    this.channelListPanel = Objects.requireNonNull(channelListPanel, "channelListPanel");
    this.dccTransfersPanel = Objects.requireNonNull(dccTransfersPanel, "dccTransfersPanel");
    this.monitorPanel = Objects.requireNonNull(monitorPanel, "monitorPanel");
    this.logViewerPanel = Objects.requireNonNull(logViewerPanel, "logViewerPanel");
    this.interceptorPanel = Objects.requireNonNull(interceptorPanel, "interceptorPanel");
    this.appAssertjPanel = Objects.requireNonNull(appAssertjPanel, "appAssertjPanel");
    this.appJhiccupPanel = Objects.requireNonNull(appJhiccupPanel, "appJhiccupPanel");
    this.appJfrPanel = Objects.requireNonNull(appJfrPanel, "appJfrPanel");
    this.appSpringPanel = Objects.requireNonNull(appSpringPanel, "appSpringPanel");
    this.managedChannelRefresher =
        Objects.requireNonNull(managedChannelRefresher, "managedChannelRefresher");
    this.monitorRowsRefresher =
        Objects.requireNonNull(monitorRowsRefresher, "monitorRowsRefresher");
  }

  TargetViewType route(TargetRef target) {
    if (target == null) {
      showTranscriptCard();
      return TargetViewType.TRANSCRIPT;
    }

    if (target.isNotifications()) {
      showNotificationsCard(target.serverId());
      return TargetViewType.UI_ONLY;
    }
    if (target.isChannelList()) {
      showChannelListCard(target.serverId());
      return TargetViewType.UI_ONLY;
    }
    if (target.isDccTransfers()) {
      showDccTransfersCard(target.serverId());
      return TargetViewType.UI_ONLY;
    }
    if (target.isMonitorGroup()) {
      showMonitorCard(target.serverId());
      return TargetViewType.UI_ONLY;
    }
    if (target.isApplicationAssertjSwing()) {
      showApplicationAssertjCard();
      return TargetViewType.UI_ONLY;
    }
    if (target.isApplicationJhiccup()) {
      showApplicationJhiccupCard();
      return TargetViewType.UI_ONLY;
    }
    if (target.isApplicationJfr()) {
      showApplicationJfrCard();
      return TargetViewType.UI_ONLY;
    }
    if (target.isApplicationSpring()) {
      showApplicationSpringCard();
      return TargetViewType.UI_ONLY;
    }
    if (target.isApplicationTerminal()) {
      showTerminalCard();
      return TargetViewType.UI_ONLY;
    }
    if (target.isLogViewer()) {
      showLogViewerCard(target.serverId());
      return TargetViewType.UI_ONLY;
    }
    if (target.isInterceptorsGroup()) {
      showInterceptorCard(target.serverId(), "");
      return TargetViewType.UI_ONLY;
    }
    if (target.isInterceptor()) {
      showInterceptorCard(target.serverId(), target.interceptorId());
      return TargetViewType.UI_ONLY;
    }

    showTranscriptCard();
    return TargetViewType.TRANSCRIPT;
  }

  void showTranscriptCard() {
    try {
      showCard(CARD_TRANSCRIPT);
    } catch (Exception ignored) {
    }
  }

  private void showNotificationsCard(String serverId) {
    try {
      notificationsPanel.setServerId(serverId);
      showCard(CARD_NOTIFICATIONS);
    } catch (Exception ignored) {
    }
  }

  private void showChannelListCard(String serverId) {
    try {
      channelListPanel.setServerId(serverId);
      managedChannelRefresher.accept(serverId);
      showCard(CARD_CHANNEL_LIST);
    } catch (Exception ignored) {
    }
  }

  private void showDccTransfersCard(String serverId) {
    try {
      dccTransfersPanel.setServerId(serverId);
      showCard(CARD_DCC_TRANSFERS);
    } catch (Exception ignored) {
    }
  }

  private void showMonitorCard(String serverId) {
    try {
      String sid = Objects.toString(serverId, "").trim();
      monitorPanel.setServerId(sid);
      monitorRowsRefresher.accept(sid);
      showCard(CARD_MONITOR);
    } catch (Exception ignored) {
    }
  }

  private void showLogViewerCard(String serverId) {
    try {
      logViewerPanel.setServerId(serverId);
      showCard(CARD_LOG_VIEWER);
    } catch (Exception ignored) {
    }
  }

  private void showTerminalCard() {
    try {
      showCard(CARD_TERMINAL);
    } catch (Exception ignored) {
    }
  }

  private void showApplicationJfrCard() {
    try {
      appJfrPanel.refreshNow();
      showCard(CARD_APP_JFR);
    } catch (Exception ignored) {
    }
  }

  private void showApplicationAssertjCard() {
    try {
      appAssertjPanel.refreshNow();
      showCard(CARD_APP_ASSERTJ);
    } catch (Exception ignored) {
    }
  }

  private void showApplicationJhiccupCard() {
    try {
      appJhiccupPanel.refreshNow();
      showCard(CARD_APP_JHICCUP);
    } catch (Exception ignored) {
    }
  }

  private void showApplicationSpringCard() {
    try {
      appSpringPanel.refreshNow();
      showCard(CARD_APP_SPRING);
    } catch (Exception ignored) {
    }
  }

  private void showInterceptorCard(String serverId, String interceptorId) {
    try {
      interceptorPanel.setInterceptorTarget(serverId, interceptorId);
      showCard(CARD_INTERCEPTOR);
    } catch (Exception ignored) {
    }
  }

  private void showCard(String cardId) {
    CardLayout cardLayout = (CardLayout) centerCards.getLayout();
    cardLayout.show(centerCards, cardId);
  }
}
