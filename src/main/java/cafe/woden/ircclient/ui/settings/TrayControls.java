package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.model.BuiltInSound;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;

enum PushyTargetMode {
  DEVICE_TOKEN("Device token"),
  TOPIC("Topic");

  private final String label;

  PushyTargetMode(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}

final class TrayControls {
  final JCheckBox enabled;
  final JCheckBox closeToTray;
  final JCheckBox minimizeToTray;
  final JCheckBox startMinimized;
  final JCheckBox notifyHighlights;
  final JCheckBox notifyPrivateMessages;
  final JCheckBox notifyConnectionState;
  final JCheckBox notifyOnlyWhenUnfocused;
  final JCheckBox notifyOnlyWhenMinimizedOrHidden;
  final JCheckBox notifySuppressWhenTargetActive;
  final JCheckBox updateNotifierEnabled;
  final JCheckBox lagIndicatorEnabled;
  final JCheckBox linuxDbusActions;
  final JComboBox<NotificationBackendMode> notificationBackend;
  final JButton testNotification;
  final JCheckBox notificationSoundsEnabled;
  final JCheckBox notificationSoundUseCustom;
  final JTextField notificationSoundCustomPath;
  final JButton browseCustomSound;
  final JButton clearCustomSound;
  final JComboBox<BuiltInSound> notificationSound;
  final JButton testSound;
  final JCheckBox pushyEnabled;
  final JTextField pushyEndpoint;
  final JPasswordField pushyApiKey;
  final JComboBox<PushyTargetMode> pushyTargetMode;
  final JTextField pushyTargetValue;
  final JTextField pushyTitlePrefix;
  final JSpinner pushyConnectTimeoutSeconds;
  final JSpinner pushyReadTimeoutSeconds;
  final JLabel pushyValidationLabel;
  final JButton pushyTest;
  final JLabel pushyTestStatus;
  JPanel panel;

  TrayControls(
      JCheckBox enabled,
      JCheckBox closeToTray,
      JCheckBox minimizeToTray,
      JCheckBox startMinimized,
      JCheckBox notifyHighlights,
      JCheckBox notifyPrivateMessages,
      JCheckBox notifyConnectionState,
      JCheckBox notifyOnlyWhenUnfocused,
      JCheckBox notifyOnlyWhenMinimizedOrHidden,
      JCheckBox notifySuppressWhenTargetActive,
      JCheckBox updateNotifierEnabled,
      JCheckBox lagIndicatorEnabled,
      JCheckBox linuxDbusActions,
      JComboBox<NotificationBackendMode> notificationBackend,
      JButton testNotification,
      JCheckBox notificationSoundsEnabled,
      JCheckBox notificationSoundUseCustom,
      JTextField notificationSoundCustomPath,
      JButton browseCustomSound,
      JButton clearCustomSound,
      JComboBox<BuiltInSound> notificationSound,
      JButton testSound,
      JCheckBox pushyEnabled,
      JTextField pushyEndpoint,
      JPasswordField pushyApiKey,
      JComboBox<PushyTargetMode> pushyTargetMode,
      JTextField pushyTargetValue,
      JTextField pushyTitlePrefix,
      JSpinner pushyConnectTimeoutSeconds,
      JSpinner pushyReadTimeoutSeconds,
      JLabel pushyValidationLabel,
      JButton pushyTest,
      JLabel pushyTestStatus) {
    this.enabled = enabled;
    this.closeToTray = closeToTray;
    this.minimizeToTray = minimizeToTray;
    this.startMinimized = startMinimized;
    this.notifyHighlights = notifyHighlights;
    this.notifyPrivateMessages = notifyPrivateMessages;
    this.notifyConnectionState = notifyConnectionState;
    this.notifyOnlyWhenUnfocused = notifyOnlyWhenUnfocused;
    this.notifyOnlyWhenMinimizedOrHidden = notifyOnlyWhenMinimizedOrHidden;
    this.notifySuppressWhenTargetActive = notifySuppressWhenTargetActive;
    this.updateNotifierEnabled = updateNotifierEnabled;
    this.lagIndicatorEnabled = lagIndicatorEnabled;
    this.linuxDbusActions = linuxDbusActions;
    this.notificationBackend = notificationBackend;
    this.testNotification = testNotification;
    this.notificationSoundsEnabled = notificationSoundsEnabled;
    this.notificationSoundUseCustom = notificationSoundUseCustom;
    this.notificationSoundCustomPath = notificationSoundCustomPath;
    this.browseCustomSound = browseCustomSound;
    this.clearCustomSound = clearCustomSound;
    this.notificationSound = notificationSound;
    this.testSound = testSound;
    this.pushyEnabled = pushyEnabled;
    this.pushyEndpoint = pushyEndpoint;
    this.pushyApiKey = pushyApiKey;
    this.pushyTargetMode = pushyTargetMode;
    this.pushyTargetValue = pushyTargetValue;
    this.pushyTitlePrefix = pushyTitlePrefix;
    this.pushyConnectTimeoutSeconds = pushyConnectTimeoutSeconds;
    this.pushyReadTimeoutSeconds = pushyReadTimeoutSeconds;
    this.pushyValidationLabel = pushyValidationLabel;
    this.pushyTest = pushyTest;
    this.pushyTestStatus = pushyTestStatus;
  }
}
