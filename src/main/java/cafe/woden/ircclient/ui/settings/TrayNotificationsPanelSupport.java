package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.nio.file.Path;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import net.miginfocom.swing.MigLayout;

final class TrayNotificationsPanelSupport {
  private TrayNotificationsPanelSupport() {}

  static JPanel buildTabsPanel(
      TrayControls controls,
      RuntimeConfigStore runtimeConfig,
      boolean linux,
      boolean linuxActionsSupported) {
    JPanel trayTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    trayTab.setOpaque(false);
    JPanel trayBehavior =
        PreferencesDialog.captionPanel(
            "Tray behavior", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    trayBehavior.add(controls.enabled, "growx");
    trayBehavior.add(controls.closeToTray, "growx");
    trayBehavior.add(controls.minimizeToTray, "growx");
    trayBehavior.add(controls.startMinimized, "growx, wrap");
    trayTab.add(trayBehavior, "growx, wmin 0, wrap");
    trayTab.add(
        PreferencesDialog.helpText(
            "Tray availability depends on your desktop environment. If tray support is unavailable, these options will have no effect."),
        "growx");

    JPanel notificationsTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    notificationsTab.setOpaque(false);
    JPanel notificationEvents =
        PreferencesDialog.captionPanel(
            "Notification events", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    notificationEvents.add(controls.notifyHighlights, "growx");
    notificationEvents.add(controls.notifyPrivateMessages, "growx");
    notificationEvents.add(controls.notifyConnectionState, "growx");
    notificationsTab.add(notificationEvents, "growx, wmin 0, wrap");
    JPanel notificationBackendGroup =
        PreferencesDialog.captionPanel(
            "Delivery backend", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "[]");
    notificationBackendGroup.add(new JLabel("Mode:"));
    notificationBackendGroup.add(controls.notificationBackend, "w 260!, wrap");
    notificationBackendGroup.add(
        PreferencesDialog.helpText(
            "Auto tries native OS notifications first and falls back to two-slices.\n"
                + "Native only disables fallback. Two-slices only bypasses OS-native backends."),
        "span 2, growx");
    notificationsTab.add(notificationBackendGroup, "growx, wmin 0, wrap");
    JPanel notificationVisibility =
        PreferencesDialog.captionPanel(
            "Suppression and focus rules", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    notificationVisibility.add(controls.updateNotifierEnabled, "growx");
    notificationVisibility.add(controls.lagIndicatorEnabled, "growx");
    notificationVisibility.add(controls.notifyOnlyWhenUnfocused, "growx");
    notificationVisibility.add(controls.notifyOnlyWhenMinimizedOrHidden, "growx");
    notificationVisibility.add(controls.notifySuppressWhenTargetActive, "growx, wrap");
    notificationVisibility.add(new JSeparator(), "growx, gaptop 4");
    notificationVisibility.add(controls.testNotification, "w 180!");
    notificationsTab.add(notificationVisibility, "growx, wmin 0, wrap");
    notificationsTab.add(
        PreferencesDialog.helpText(
            "Desktop notifications are shown when your notification rules trigger (or for connection events, if enabled)."),
        "growx");

    JPanel soundsTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    soundsTab.setOpaque(false);
    JPanel soundsBehavior =
        PreferencesDialog.captionPanel(
            "Sound behavior", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    soundsBehavior.add(controls.notificationSoundsEnabled, "growx");
    soundsBehavior.add(controls.notificationSoundUseCustom, "growx, wrap");
    soundsTab.add(soundsBehavior, "growx, wmin 0, wrap");
    JPanel customSound =
        PreferencesDialog.captionPanel(
            "Custom sound file", "insets 0, fillx, wrap 4", "[right]8[grow,fill]8[]8[]", "[]");
    customSound.add(new JLabel("File:"));
    customSound.add(controls.notificationSoundCustomPath, "growx, pushx, wmin 0");
    customSound.add(controls.browseCustomSound, "w 110!");
    customSound.add(controls.clearCustomSound, "w 80!, wrap");
    soundsTab.add(customSound, "growx, wmin 0, wrap");
    JPanel builtInSound =
        PreferencesDialog.captionPanel(
            "Built-in sound", "insets 0, fillx, wrap 3", "[right]8[grow,fill]8[]", "[]");
    builtInSound.add(new JLabel("Preset:"));
    builtInSound.add(controls.notificationSound, "w 240!");
    builtInSound.add(controls.testSound, "w 120!, wrap");
    soundsTab.add(builtInSound, "growx, wmin 0, wrap");

    Path configPath = runtimeConfig != null ? runtimeConfig.runtimeConfigPath() : null;
    Path base = configPath != null ? configPath.getParent() : null;
    if (base != null) {
      soundsTab.add(
          PreferencesDialog.helpText(
              "Custom sounds are copied to: "
                  + base.resolve("sounds")
                  + "\nTip: Use small files (short MP3/WAV) for snappy notifications."),
          "growx");
    }

    JPanel pushyTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    pushyTab.setOpaque(false);

    JPanel pushyBasics =
        PreferencesDialog.captionPanel(
            "Pushy integration", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "[]");
    pushyBasics.add(controls.pushyEnabled, "span 2, growx, wrap");
    pushyBasics.add(new JLabel("Endpoint:"));
    pushyBasics.add(controls.pushyEndpoint, "growx, pushx, wmin 0, wrap");
    pushyBasics.add(new JLabel("API key:"));
    pushyBasics.add(controls.pushyApiKey, "growx, pushx, wmin 0, wrap");
    pushyBasics.add(new JLabel("Title prefix:"));
    pushyBasics.add(controls.pushyTitlePrefix, "growx, pushx, wmin 0, wrap");
    pushyTab.add(pushyBasics, "growx, wmin 0, wrap");

    JPanel pushyDestination =
        PreferencesDialog.captionPanel(
            "Destination", "insets 0, fillx, wrap 2", "[right]8[grow,fill]", "[]");
    pushyDestination.add(new JLabel("Target mode:"));
    pushyDestination.add(controls.pushyTargetMode, "w 180!, wrap");
    pushyDestination.add(new JLabel("Target value:"));
    pushyDestination.add(controls.pushyTargetValue, "growx, pushx, wmin 0, wrap");
    pushyDestination.add(
        PreferencesDialog.helpText("Choose a destination type and enter the corresponding value."),
        "span 2, growx");
    pushyTab.add(pushyDestination, "growx, wmin 0, wrap");

    JPanel pushyTimeouts =
        PreferencesDialog.captionPanel(
            "Network timeouts", "insets 0, fillx, wrap 4", "[right]8[]20[right]8[]", "[]");
    pushyTimeouts.add(new JLabel("Connect (s):"));
    pushyTimeouts.add(controls.pushyConnectTimeoutSeconds, "w 90!");
    pushyTimeouts.add(new JLabel("Read (s):"));
    pushyTimeouts.add(controls.pushyReadTimeoutSeconds, "w 90!, wrap");
    pushyTab.add(pushyTimeouts, "growx, wmin 0, wrap");
    JPanel pushyActions =
        PreferencesDialog.captionPanel(
            "Validation & testing", "insets 0, fillx, wrap 2", "[]12[grow,fill]", "[]");
    pushyActions.add(controls.pushyTest, "w 150!");
    pushyActions.add(controls.pushyTestStatus, "growx, wmin 0, wrap");
    pushyActions.add(new JLabel(""));
    pushyActions.add(controls.pushyValidationLabel, "growx, wmin 0");
    pushyTab.add(pushyActions, "growx, wmin 0, wrap");
    pushyTab.add(
        PreferencesDialog.helpText(
            "Pushy notifications are triggered by matching IRC event rules in Notifications -> IRC Event Rules."),
        "growx");

    JPanel linuxTab = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    linuxTab.setOpaque(false);
    JPanel linuxGroup =
        PreferencesDialog.captionPanel(
            "Linux integration", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    linuxGroup.add(controls.linuxDbusActions, "growx, wrap");
    if (!linux) {
      linuxGroup.add(PreferencesDialog.helpText("Linux only."), "growx");
    } else if (!linuxActionsSupported) {
      linuxGroup.add(
          PreferencesDialog.helpText(
              "Linux notification actions were not detected for this session.\n"
                  + "IRCafe will fall back to notify-send."),
          "growx");
    } else {
      linuxGroup.add(
          PreferencesDialog.helpText(
              "Uses org.freedesktop.Notifications over D-Bus so clicking a notification can open IRCafe."),
          "growx");
    }
    linuxTab.add(linuxGroup, "growx, wmin 0");

    JTabbedPane subTabs = new DynamicTabbedPane();
    subTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    subTabs.addTab("Tray", PreferencesDialog.padSubTab(trayTab));
    subTabs.addTab("Desktop notifications", PreferencesDialog.padSubTab(notificationsTab));
    subTabs.addTab("Sounds", PreferencesDialog.padSubTab(soundsTab));
    subTabs.addTab("Pushy", PreferencesDialog.padSubTab(pushyTab));
    subTabs.addTab("Linux / Advanced", PreferencesDialog.padSubTab(linuxTab));

    JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]"));
    panel.setOpaque(false);
    panel.add(subTabs, "growx, wmin 0");
    return panel;
  }
}
