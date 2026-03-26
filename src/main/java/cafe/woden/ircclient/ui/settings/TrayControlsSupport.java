package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.PushyProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.model.BuiltInSound;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettings;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import java.awt.Color;
import java.io.File;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

final class TrayControlsSupport {
  private TrayControlsSupport() {}

  @FunctionalInterface
  interface NotificationSoundImporter {
    String importToRuntimeDir(File source) throws Exception;
  }

  static TrayControls buildControls(
      UiSettings current,
      NotificationSoundSettings soundSettings,
      PushyProperties pushySettings,
      RuntimeConfigStore runtimeConfig,
      GnomeDbusNotificationBackend gnomeDbusBackend,
      TrayNotificationService trayNotificationService,
      NotificationSoundService notificationSoundService,
      PushyNotificationService pushyNotificationService,
      ExecutorService pushyTestExecutor,
      NotificationSoundImporter notificationSoundImporter) {
    NotificationSoundSettings effectiveSoundSettings =
        soundSettings != null
            ? soundSettings
            : new NotificationSoundSettings(true, BuiltInSound.NOTIF_1.name(), false, null);
    PushyProperties effectivePushySettings =
        pushySettings != null
            ? pushySettings
            : new PushyProperties(false, null, null, null, null, null, null, null);

    JCheckBox enabled = new JCheckBox("Enable system tray icon", current.trayEnabled());
    JCheckBox closeToTray =
        new JCheckBox("Close button hides to tray instead of exiting", current.trayCloseToTray());
    JCheckBox minimizeToTray =
        new JCheckBox("Minimize button hides to tray", current.trayMinimizeToTray());
    JCheckBox startMinimized =
        new JCheckBox("Start minimized to tray", current.trayStartMinimized());

    JCheckBox notifyHighlights =
        new JCheckBox("Desktop notifications for highlights", current.trayNotifyHighlights());
    JCheckBox notifyPrivateMessages =
        new JCheckBox(
            "Desktop notifications for private messages", current.trayNotifyPrivateMessages());
    JCheckBox notifyConnectionState =
        new JCheckBox(
            "Desktop notifications for connection state", current.trayNotifyConnectionState());

    JCheckBox notifyOnlyWhenUnfocused =
        new JCheckBox(
            "Only notify when IRCafe is not focused", current.trayNotifyOnlyWhenUnfocused());
    JCheckBox notifyOnlyWhenMinimizedOrHidden =
        new JCheckBox(
            "Only notify when minimized or hidden to tray",
            current.trayNotifyOnlyWhenMinimizedOrHidden());
    JCheckBox notifySuppressWhenTargetActive =
        new JCheckBox(
            "Don't notify for the active buffer", current.trayNotifySuppressWhenTargetActive());
    JCheckBox updateNotifierEnabled =
        new JCheckBox(
            "Show update notifier in status bar",
            runtimeConfig == null || runtimeConfig.readUpdateNotifierEnabled(true));
    updateNotifierEnabled.setToolTipText(
        "Checks GitHub releases in the background and alerts when a newer IRCafe version exists.");
    JCheckBox lagIndicatorEnabled =
        new JCheckBox(
            "Show lag indicator in status bar",
            runtimeConfig == null || runtimeConfig.readLagIndicatorEnabled(true));
    lagIndicatorEnabled.setToolTipText(
        "Shows measured round-trip server lag for the active server in the status bar.");

    boolean linuxTmp = false;
    boolean linuxActionsSupportedTmp = false;
    try {
      linuxTmp = gnomeDbusBackend != null && gnomeDbusBackend.isLinux();
      if (linuxTmp) {
        GnomeDbusNotificationBackend.ProbeResult probeResult = gnomeDbusBackend.probe();
        linuxActionsSupportedTmp =
            probeResult != null
                && probeResult.sessionBusReachable()
                && probeResult.actionsSupported();
      }
    } catch (Exception ignored) {
    }

    final boolean linux = linuxTmp;
    final boolean linuxActionsSupported = linuxActionsSupportedTmp;

    JCheckBox linuxDbusActions =
        new JCheckBox(
            "Use Linux D-Bus notifications (click-to-open)",
            linux && linuxActionsSupported && current.trayLinuxDbusActionsEnabled());
    linuxDbusActions.setToolTipText(
        linux
            ? (linuxActionsSupported
                ? "Uses org.freedesktop.Notifications over D-Bus so clicking a notification can open IRCafe."
                : "Click actions aren't available in this session (no D-Bus notification actions support detected).")
            : "Linux only.");

    JComboBox<NotificationBackendMode> notificationBackend =
        new JComboBox<>(NotificationBackendMode.values());
    notificationBackend.setSelectedItem(current.trayNotificationBackendMode());
    notificationBackend.setToolTipText(
        "Select how desktop notifications are delivered: native backends, two-slices fallback, or two-slices only.");

    JButton testNotification = new JButton("Test notification");
    testNotification.setToolTipText(
        "Send a test desktop notification (click to open IRCafe).\n"
            + "This does not require highlight/PM notifications to be enabled.");
    testNotification.addActionListener(
        e -> {
          try {
            if (trayNotificationService != null) {
              trayNotificationService.notifyTest();
            }
          } catch (Throwable ignored) {
          }
        });

    notifyHighlights.setToolTipText(
        "Show a desktop notification when someone mentions your nick in a channel.");
    notifyPrivateMessages.setToolTipText(
        "Show a desktop notification when you receive a private message.");
    notifyConnectionState.setToolTipText(
        "Show a desktop notification when connecting/disconnecting.");
    notifyOnlyWhenUnfocused.setToolTipText(
        "Common HexChat behavior: only notify when IRCafe isn't the active window.");
    notifyOnlyWhenMinimizedOrHidden.setToolTipText(
        "Only notify when IRCafe is minimized or hidden to tray.");
    notifySuppressWhenTargetActive.setToolTipText(
        "If the message is in the currently selected buffer, suppress the notification.");

    JCheckBox notificationSoundsEnabled =
        new JCheckBox("Play sound with desktop notifications", effectiveSoundSettings.enabled());
    notificationSoundsEnabled.setToolTipText(
        "Plays a short sound whenever IRCafe shows a desktop notification.");

    JCheckBox notificationSoundUseCustom =
        new JCheckBox("Use custom sound file", effectiveSoundSettings.useCustom());
    notificationSoundUseCustom.setToolTipText(
        "If enabled, IRCafe will play a custom file stored next to your runtime config.\n"
            + "Supported formats: MP3, WAV.");

    JTextField notificationSoundCustomPath =
        new JTextField(Objects.toString(effectiveSoundSettings.customPath(), ""));
    notificationSoundCustomPath.setEditable(false);
    notificationSoundCustomPath.setToolTipText(
        "Custom sound path (relative to the runtime config directory).\n"
            + "Click Browse... to import a file.");

    JComboBox<BuiltInSound> notificationSound = new JComboBox<>(BuiltInSound.valuesForUi());
    PreferencesDialog.configureBuiltInSoundCombo(notificationSound);
    notificationSound.setSelectedItem(BuiltInSound.fromId(effectiveSoundSettings.soundId()));
    notificationSound.setToolTipText("Choose which bundled sound to use for notifications.");

    JButton browseCustomSound = new JButton("Browse...");
    browseCustomSound.setToolTipText(
        "Choose an MP3 or WAV file and copy it into IRCafe's runtime config directory.");
    browseCustomSound.addActionListener(
        e -> {
          try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose notification sound (MP3 or WAV)");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setAcceptAllFileFilterUsed(true);
            chooser.addChoosableFileFilter(
                new FileNameExtensionFilter("Audio files (MP3, WAV)", "mp3", "wav"));
            int result =
                chooser.showOpenDialog(SwingUtilities.getWindowAncestor(browseCustomSound));
            if (result != JFileChooser.APPROVE_OPTION) return;

            File selectedFile = chooser.getSelectedFile();
            if (selectedFile == null) return;
            String relativePath = notificationSoundImporter.importToRuntimeDir(selectedFile);
            if (relativePath != null && !relativePath.isBlank()) {
              notificationSoundCustomPath.setText(relativePath);
              notificationSoundUseCustom.setSelected(true);
            }
          } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(browseCustomSound),
                "Could not import sound file.\n\n" + ex.getMessage(),
                "Import failed",
                javax.swing.JOptionPane.ERROR_MESSAGE);
          }
        });

    JButton clearCustomSound = new JButton("Clear");
    clearCustomSound.setToolTipText("Stop using a custom file and revert to bundled sounds.");
    clearCustomSound.addActionListener(
        e -> {
          notificationSoundUseCustom.setSelected(false);
          notificationSoundCustomPath.setText("");
        });

    JButton testSound = new JButton("Test sound");
    testSound.setToolTipText("Play the selected sound.");
    testSound.addActionListener(
        e -> {
          try {
            if (notificationSoundService != null) {
              if (notificationSoundUseCustom.isSelected()) {
                String relativePath =
                    Objects.toString(notificationSoundCustomPath.getText(), "").trim();
                if (!relativePath.isBlank()) {
                  notificationSoundService.previewCustom(relativePath);
                }
              } else {
                BuiltInSound selectedSound = (BuiltInSound) notificationSound.getSelectedItem();
                notificationSoundService.preview(selectedSound);
              }
            }
          } catch (Throwable ignored) {
          }
        });

    JCheckBox pushyEnabled =
        new JCheckBox(
            "Forward matched IRC event notifications to Pushy",
            Boolean.TRUE.equals(effectivePushySettings.enabled()));
    pushyEnabled.setToolTipText(
        "Sends notifications for matching IRC event rules to Pushy (device token or topic).");

    JTextField pushyEndpoint =
        new JTextField(
            Objects.toString(effectivePushySettings.endpoint(), "https://api.pushy.me/push"));
    pushyEndpoint.setToolTipText("Pushy API endpoint URL.");

    JPasswordField pushyApiKey =
        new JPasswordField(Objects.toString(effectivePushySettings.apiKey(), ""));
    pushyApiKey.setToolTipText("Pushy Secret API key.");

    PushyTargetMode pushyInitialTargetMode =
        effectivePushySettings.deviceToken() != null
                && !effectivePushySettings.deviceToken().isBlank()
            ? PushyTargetMode.DEVICE_TOKEN
            : PushyTargetMode.TOPIC;
    String pushyInitialTargetValue =
        pushyInitialTargetMode == PushyTargetMode.DEVICE_TOKEN
            ? Objects.toString(effectivePushySettings.deviceToken(), "")
            : Objects.toString(effectivePushySettings.topic(), "");

    JComboBox<PushyTargetMode> pushyTargetMode = new JComboBox<>(PushyTargetMode.values());
    pushyTargetMode.setSelectedItem(pushyInitialTargetMode);
    pushyTargetMode.setToolTipText("Choose destination type for Pushy notifications.");

    JTextField pushyTargetValue = new JTextField(pushyInitialTargetValue);
    pushyTargetValue.setToolTipText("Destination value for selected target mode.");

    JTextField pushyTitlePrefix =
        new JTextField(Objects.toString(effectivePushySettings.titlePrefix(), "IRCafe"));
    pushyTitlePrefix.setToolTipText("Prefix prepended to Pushy notification titles.");

    JSpinner pushyConnectTimeoutSeconds =
        new JSpinner(
            new SpinnerNumberModel(
                Integer.valueOf(effectivePushySettings.connectTimeoutSeconds()),
                Integer.valueOf(1),
                Integer.valueOf(30),
                Integer.valueOf(1)));
    JSpinner pushyReadTimeoutSeconds =
        new JSpinner(
            new SpinnerNumberModel(
                Integer.valueOf(effectivePushySettings.readTimeoutSeconds()),
                Integer.valueOf(1),
                Integer.valueOf(60),
                Integer.valueOf(1)));

    JButton pushyTest = new JButton("Test Pushy");
    pushyTest.setToolTipText("Send a real test notification to the configured Pushy destination.");
    JLabel pushyValidationLabel = new JLabel(" ");
    pushyValidationLabel.setForeground(errorForeground());
    JLabel pushyTestStatus = new JLabel(" ");

    Runnable refreshPushyValidation =
        () -> {
          PushyTargetMode mode =
              pushyTargetMode.getSelectedItem() instanceof PushyTargetMode m
                  ? m
                  : PushyTargetMode.DEVICE_TOKEN;
          String endpoint = Objects.toString(pushyEndpoint.getText(), "").trim();
          String apiKey = new String(pushyApiKey.getPassword()).trim();
          String target = Objects.toString(pushyTargetValue.getText(), "").trim();
          String error =
              validatePushyInputs(pushyEnabled.isSelected(), endpoint, apiKey, mode, target);
          if (error == null) {
            pushyValidationLabel.setText(" ");
            pushyValidationLabel.setVisible(false);
            pushyTest.setEnabled(pushyEnabled.isSelected());
          } else {
            pushyValidationLabel.setText(error);
            pushyValidationLabel.setVisible(true);
            pushyTest.setEnabled(false);
          }
        };

    pushyTest.addActionListener(
        e -> {
          PushyTargetMode mode =
              pushyTargetMode.getSelectedItem() instanceof PushyTargetMode m
                  ? m
                  : PushyTargetMode.DEVICE_TOKEN;
          String endpoint = Objects.toString(pushyEndpoint.getText(), "").trim();
          String apiKey = new String(pushyApiKey.getPassword()).trim();
          String target = Objects.toString(pushyTargetValue.getText(), "").trim();
          String titlePrefix = Objects.toString(pushyTitlePrefix.getText(), "").trim();
          int connectSeconds = ((Number) pushyConnectTimeoutSeconds.getValue()).intValue();
          int readSeconds = ((Number) pushyReadTimeoutSeconds.getValue()).intValue();

          String error =
              validatePushyInputs(pushyEnabled.isSelected(), endpoint, apiKey, mode, target);
          if (error != null) {
            pushyTestStatus.setText(error);
            pushyTestStatus.setForeground(errorForeground());
            return;
          }

          String deviceToken = mode == PushyTargetMode.DEVICE_TOKEN ? target : null;
          String topic = mode == PushyTargetMode.TOPIC ? target : null;
          PushyProperties draft =
              new PushyProperties(
                  pushyEnabled.isSelected(),
                  endpoint.isBlank() ? null : endpoint,
                  apiKey.isBlank() ? null : apiKey,
                  deviceToken,
                  topic,
                  titlePrefix.isBlank() ? null : titlePrefix,
                  connectSeconds,
                  readSeconds);

          pushyTest.setEnabled(false);
          pushyTestStatus.setText("Sending test push…");
          pushyTestStatus.setForeground(UIManager.getColor("Label.foreground"));

          pushyTestExecutor.submit(
              () -> {
                PushyNotificationService.PushResult result =
                    pushyNotificationService != null
                        ? pushyNotificationService.sendTestNotification(
                            draft, "IRCafe Test", "This is a Pushy test notification from IRCafe.")
                        : PushyNotificationService.PushResult.failed(
                            "Pushy service is unavailable.");
                SwingUtilities.invokeLater(
                    () -> {
                      pushyTestStatus.setText(
                          result.message() == null || result.message().isBlank()
                              ? (result.success() ? "Push sent." : "Push failed.")
                              : result.message());
                      pushyTestStatus.setForeground(
                          result.success()
                              ? UIManager.getColor("Label.foreground")
                              : errorForeground());
                      refreshPushyValidation.run();
                    });
              });
        });

    Runnable refreshPushyDestinationState =
        () -> {
          PushyTargetMode mode =
              pushyTargetMode.getSelectedItem() instanceof PushyTargetMode m
                  ? m
                  : PushyTargetMode.DEVICE_TOKEN;
          if (mode == PushyTargetMode.DEVICE_TOKEN) {
            pushyTargetValue.setToolTipText("Single-device destination token.");
          } else {
            pushyTargetValue.setToolTipText("Topic destination for fan-out delivery.");
          }
        };

    Runnable refreshPushyState =
        () -> {
          boolean enabledState = pushyEnabled.isSelected();
          pushyEndpoint.setEnabled(enabledState);
          pushyApiKey.setEnabled(enabledState);
          pushyTargetMode.setEnabled(enabledState);
          pushyTargetValue.setEnabled(enabledState);
          pushyTitlePrefix.setEnabled(enabledState);
          pushyConnectTimeoutSeconds.setEnabled(enabledState);
          pushyReadTimeoutSeconds.setEnabled(enabledState);
          refreshPushyDestinationState.run();
          refreshPushyValidation.run();
        };
    pushyEnabled.addActionListener(e -> refreshPushyState.run());
    pushyTargetMode.addActionListener(e -> refreshPushyState.run());
    pushyEndpoint
        .getDocument()
        .addDocumentListener(new PreferencesDialog.SimpleDocListener(refreshPushyValidation));
    pushyApiKey
        .getDocument()
        .addDocumentListener(new PreferencesDialog.SimpleDocListener(refreshPushyValidation));
    pushyTargetValue
        .getDocument()
        .addDocumentListener(new PreferencesDialog.SimpleDocListener(refreshPushyValidation));
    refreshPushyState.run();

    Runnable refreshEnabled =
        () -> {
          boolean enabledState = enabled.isSelected();
          closeToTray.setEnabled(enabledState);
          minimizeToTray.setEnabled(enabledState);
          startMinimized.setEnabled(enabledState);
          notifyHighlights.setEnabled(enabledState);
          notifyPrivateMessages.setEnabled(enabledState);
          notifyConnectionState.setEnabled(enabledState);
          notifyOnlyWhenUnfocused.setEnabled(enabledState);
          notifyOnlyWhenMinimizedOrHidden.setEnabled(enabledState);
          notifySuppressWhenTargetActive.setEnabled(enabledState);
          linuxDbusActions.setEnabled(enabledState && linux && linuxActionsSupported);
          notificationBackend.setEnabled(enabledState);
          testNotification.setEnabled(enabledState);
          notificationSoundsEnabled.setEnabled(enabledState);

          boolean soundEnabled = enabledState && notificationSoundsEnabled.isSelected();
          notificationSoundUseCustom.setEnabled(soundEnabled);

          boolean useCustomSound = soundEnabled && notificationSoundUseCustom.isSelected();
          notificationSoundCustomPath.setEnabled(soundEnabled);
          browseCustomSound.setEnabled(soundEnabled);

          String relativePath = Objects.toString(notificationSoundCustomPath.getText(), "").trim();
          clearCustomSound.setEnabled(soundEnabled && !relativePath.isBlank());
          notificationSound.setEnabled(soundEnabled && !useCustomSound);
          testSound.setEnabled(soundEnabled);

          if (!enabledState) {
            closeToTray.setSelected(false);
            minimizeToTray.setSelected(false);
            startMinimized.setSelected(false);
            notifyHighlights.setSelected(false);
            notifyPrivateMessages.setSelected(false);
            notifyConnectionState.setSelected(false);
            notifyOnlyWhenUnfocused.setSelected(false);
            notifyOnlyWhenMinimizedOrHidden.setSelected(false);
            notifySuppressWhenTargetActive.setSelected(false);
            linuxDbusActions.setSelected(false);
            notificationSoundsEnabled.setSelected(false);
          }

          if (!(linux && linuxActionsSupported)) {
            linuxDbusActions.setSelected(false);
          }
        };

    enabled.addActionListener(e -> refreshEnabled.run());
    notificationSoundsEnabled.addActionListener(e -> refreshEnabled.run());
    notificationSoundUseCustom.addActionListener(e -> refreshEnabled.run());
    refreshEnabled.run();

    TrayControls controls =
        new TrayControls(
            enabled,
            closeToTray,
            minimizeToTray,
            startMinimized,
            notifyHighlights,
            notifyPrivateMessages,
            notifyConnectionState,
            notifyOnlyWhenUnfocused,
            notifyOnlyWhenMinimizedOrHidden,
            notifySuppressWhenTargetActive,
            updateNotifierEnabled,
            lagIndicatorEnabled,
            linuxDbusActions,
            notificationBackend,
            testNotification,
            notificationSoundsEnabled,
            notificationSoundUseCustom,
            notificationSoundCustomPath,
            browseCustomSound,
            clearCustomSound,
            notificationSound,
            testSound,
            pushyEnabled,
            pushyEndpoint,
            pushyApiKey,
            pushyTargetMode,
            pushyTargetValue,
            pushyTitlePrefix,
            pushyConnectTimeoutSeconds,
            pushyReadTimeoutSeconds,
            pushyValidationLabel,
            pushyTest,
            pushyTestStatus);
    controls.panel =
        TrayNotificationsPanelSupport.buildTabsPanel(
            controls, runtimeConfig, linux, linuxActionsSupported);
    return controls;
  }

  static String validatePushyInputs(
      boolean enabled,
      String endpoint,
      String apiKey,
      PushyTargetMode targetMode,
      String targetValue) {
    if (!enabled) return null;

    String key = Objects.toString(apiKey, "").trim();
    if (key.isEmpty()) return "Pushy API key is required.";

    String target = Objects.toString(targetValue, "").trim();
    if (target.isEmpty()) {
      return switch (targetMode) {
        case TOPIC -> "Pushy topic is required.";
        case DEVICE_TOKEN -> "Pushy device token is required.";
      };
    }

    String trimmedEndpoint = Objects.toString(endpoint, "").trim();
    if (!trimmedEndpoint.isEmpty() && !isValidPushyEndpoint(trimmedEndpoint)) {
      return "Pushy endpoint must be a valid http(s) URL.";
    }

    return null;
  }

  private static boolean isValidPushyEndpoint(String endpoint) {
    try {
      URI uri = URI.create(Objects.toString(endpoint, "").trim());
      String scheme = Objects.toString(uri.getScheme(), "").trim().toLowerCase(Locale.ROOT);
      String host = Objects.toString(uri.getHost(), "").trim();
      return ("https".equals(scheme) || "http".equals(scheme)) && !host.isBlank();
    } catch (Exception ignored) {
      return false;
    }
  }

  private static Color errorForeground() {
    Color color = UIManager.getColor("Label.errorForeground");
    if (color != null) return color;
    color = UIManager.getColor("Component.errorColor");
    if (color != null) return color;
    color = UIManager.getColor("Component.error.outlineColor");
    if (color != null) return color;
    color = UIManager.getColor("Component.error.borderColor");
    if (color != null) return color;
    color = UIManager.getColor("Component.error.focusedBorderColor");
    if (color != null) return color;
    return new Color(180, 0, 0);
  }
}
