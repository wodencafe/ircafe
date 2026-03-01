package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTypingIndicatorStyle;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import javax.swing.SwingUtilities;

/** Synchronizes server-tree settings state and lifecycle listeners with UI properties. */
public final class ServerTreeSettingsSynchronizer {

  public interface Context {
    UiSettingsBus settingsBus();

    JfrRuntimeEventsService jfrRuntimeEventsService();

    RuntimeConfigStore runtimeConfig();

    boolean typingIndicatorsTreeEnabled();

    void setTypingIndicatorsTreeEnabled(boolean enabled);

    void clearTypingIndicatorsFromTree();

    void setTypingIndicatorStyle(ServerTreeTypingIndicatorStyle style);

    void setServerTreeNotificationBadgesEnabled(boolean enabled);

    void setUnreadBadgeScalePercent(int percent);

    void refreshTreeLayoutAfterUiChange();

    void refreshApplicationJfrNode();
  }

  private static final int MIN_BADGE_SCALE_PERCENT = 50;
  private static final int MAX_BADGE_SCALE_PERCENT = 150;

  private final Context context;
  private final int defaultBadgeScalePercent;

  private PropertyChangeListener settingsListener;
  private PropertyChangeListener jfrStateListener;

  public ServerTreeSettingsSynchronizer(Context context, int defaultBadgeScalePercent) {
    this.context = Objects.requireNonNull(context, "context");
    this.defaultBadgeScalePercent = defaultBadgeScalePercent;
  }

  public void applyInitialSettings() {
    syncTypingTreeEnabledFromSettings(false);
    syncTypingIndicatorStyleFromSettings();
    syncUnreadBadgeScaleFromRuntimeConfig();
    syncServerTreeNotificationBadgesFromSettings();
  }

  public void bindListeners() {
    UiSettingsBus settingsBus = context.settingsBus();
    if (settingsBus != null) {
      settingsListener =
          event -> {
            if (!UiSettingsBus.PROP_UI_SETTINGS.equals(event.getPropertyName())) return;
            syncTypingTreeEnabledFromSettings(true);
            syncTypingIndicatorStyleFromSettings();
            syncUnreadBadgeScaleFromRuntimeConfig();
            syncServerTreeNotificationBadgesFromSettings();
            SwingUtilities.invokeLater(context::refreshTreeLayoutAfterUiChange);
          };
      settingsBus.addListener(settingsListener);
    }

    JfrRuntimeEventsService jfrRuntimeEventsService = context.jfrRuntimeEventsService();
    if (jfrRuntimeEventsService != null) {
      jfrStateListener =
          event -> {
            if (!JfrRuntimeEventsService.PROP_STATE.equals(event.getPropertyName())) return;
            SwingUtilities.invokeLater(context::refreshApplicationJfrNode);
          };
      jfrRuntimeEventsService.addStateListener(jfrStateListener);
    }
  }

  public void shutdown() {
    UiSettingsBus settingsBus = context.settingsBus();
    if (settingsBus != null && settingsListener != null) {
      settingsBus.removeListener(settingsListener);
      settingsListener = null;
    }

    JfrRuntimeEventsService jfrRuntimeEventsService = context.jfrRuntimeEventsService();
    if (jfrRuntimeEventsService != null && jfrStateListener != null) {
      jfrRuntimeEventsService.removeStateListener(jfrStateListener);
      jfrStateListener = null;
    }
  }

  private void syncTypingTreeEnabledFromSettings(boolean clearIfDisabled) {
    boolean enabled = true;
    try {
      UiSettingsBus settingsBus = context.settingsBus();
      enabled =
          settingsBus == null
              || settingsBus.get() == null
              || settingsBus.get().typingIndicatorsTreeEnabled();
    } catch (Exception ignored) {
      enabled = true;
    }
    boolean wasEnabled = context.typingIndicatorsTreeEnabled();
    context.setTypingIndicatorsTreeEnabled(enabled);
    if (clearIfDisabled && wasEnabled && !enabled) {
      context.clearTypingIndicatorsFromTree();
    }
  }

  private void syncTypingIndicatorStyleFromSettings() {
    String configured = null;
    try {
      UiSettingsBus settingsBus = context.settingsBus();
      configured =
          settingsBus != null && settingsBus.get() != null
              ? settingsBus.get().typingIndicatorsTreeStyle()
              : null;
    } catch (Exception ignored) {
    }
    context.setTypingIndicatorStyle(ServerTreeTypingIndicatorStyle.from(configured));
  }

  private void syncServerTreeNotificationBadgesFromSettings() {
    boolean enabled = true;
    try {
      UiSettingsBus settingsBus = context.settingsBus();
      enabled =
          settingsBus == null
              || settingsBus.get() == null
              || settingsBus.get().serverTreeNotificationBadgesEnabled();
    } catch (Exception ignored) {
      enabled = true;
    }
    context.setServerTreeNotificationBadgesEnabled(enabled);
  }

  private void syncUnreadBadgeScaleFromRuntimeConfig() {
    int next = defaultBadgeScalePercent;
    try {
      RuntimeConfigStore runtimeConfig = context.runtimeConfig();
      if (runtimeConfig != null) {
        next = runtimeConfig.readServerTreeUnreadBadgeScalePercent(defaultBadgeScalePercent);
      }
    } catch (Exception ignored) {
      next = defaultBadgeScalePercent;
    }
    if (next < MIN_BADGE_SCALE_PERCENT) next = MIN_BADGE_SCALE_PERCENT;
    if (next > MAX_BADGE_SCALE_PERCENT) next = MAX_BADGE_SCALE_PERCENT;
    context.setUnreadBadgeScalePercent(next);
  }
}
