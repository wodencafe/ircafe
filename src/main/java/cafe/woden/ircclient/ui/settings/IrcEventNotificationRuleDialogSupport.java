package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.model.BuiltInSound;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Dimension;
import java.awt.Window;
import java.io.File;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.miginfocom.swing.MigLayout;

final class IrcEventNotificationRuleDialogSupport {
  private IrcEventNotificationRuleDialogSupport() {}

  static IrcEventNotificationRule promptIrcEventNotificationRuleDialog(
      Window owner,
      String title,
      IrcEventNotificationRule seed,
      NotificationSoundService notificationSoundService,
      SoundFileImporter soundFileImporter) {
    IrcEventNotificationRule base =
        seed != null
            ? seed
            : new IrcEventNotificationRule(
                false,
                IrcEventNotificationRule.EventType.INVITE_RECEIVED,
                IrcEventNotificationRule.SourceMode.ANY,
                null,
                IrcEventNotificationRule.ChannelScope.ALL,
                null,
                true,
                IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY,
                true,
                true,
                false,
                PreferencesDialog.defaultBuiltInSoundForIrcEventRule(
                        IrcEventNotificationRule.EventType.INVITE_RECEIVED)
                    .name(),
                false,
                null,
                false,
                null,
                null,
                null);

    JCheckBox enabled = new JCheckBox("Enabled", base.enabled());

    JComboBox<IrcEventNotificationRule.EventType> eventType =
        new JComboBox<>(IrcEventNotificationRule.EventType.values());
    eventType.setSelectedItem(
        base.eventType() != null
            ? base.eventType()
            : IrcEventNotificationRule.EventType.INVITE_RECEIVED);

    JComboBox<IrcEventNotificationRule.SourceMode> sourceMode =
        new JComboBox<>(IrcEventNotificationRule.SourceMode.values());
    sourceMode.setSelectedItem(
        base.sourceMode() != null ? base.sourceMode() : IrcEventNotificationRule.SourceMode.ANY);

    JTextField sourcePattern = new JTextField(Objects.toString(base.sourcePattern(), ""));
    sourcePattern.setToolTipText(
        "For Specific nicks: comma-separated list.\n"
            + "For Nick glob: wildcard patterns (* and ?).\n"
            + "For Nick regex: Java regular expression.");

    JComboBox<IrcEventNotificationRule.ChannelScope> channelScope =
        new JComboBox<>(IrcEventNotificationRule.ChannelScope.values());
    channelScope.setSelectedItem(
        base.channelScope() != null
            ? base.channelScope()
            : IrcEventNotificationRule.ChannelScope.ALL);

    JTextField channelPatterns = new JTextField(Objects.toString(base.channelPatterns(), ""));
    channelPatterns.setToolTipText("Comma-separated channel masks (for example: #staff*, #ops).");

    JComboBox<IrcEventNotificationRule.CtcpMatchMode> ctcpCommandMode =
        new JComboBox<>(IrcEventNotificationRule.CtcpMatchMode.values());
    ctcpCommandMode.setSelectedItem(
        base.ctcpCommandMode() != null
            ? base.ctcpCommandMode()
            : IrcEventNotificationRule.CtcpMatchMode.ANY);
    JTextField ctcpCommandPattern = new JTextField(Objects.toString(base.ctcpCommandPattern(), ""));
    ctcpCommandPattern.setToolTipText(
        "Filter CTCP command by mode (for example: VERSION, PING, TIME, CLIENTINFO).");

    JComboBox<IrcEventNotificationRule.CtcpMatchMode> ctcpValueMode =
        new JComboBox<>(IrcEventNotificationRule.CtcpMatchMode.values());
    ctcpValueMode.setSelectedItem(
        base.ctcpValueMode() != null
            ? base.ctcpValueMode()
            : IrcEventNotificationRule.CtcpMatchMode.ANY);
    JTextField ctcpValuePattern = new JTextField(Objects.toString(base.ctcpValuePattern(), ""));
    ctcpValuePattern.setToolTipText("Filter CTCP value/argument by mode.");

    JComboBox<CtcpNotificationRuleTemplate> ctcpTemplate =
        new JComboBox<>(CtcpNotificationRuleTemplate.values());
    JButton applyCtcpTemplate = new JButton("Apply");
    PreferencesDialog.configureIconOnlyButton(
        applyCtcpTemplate, "check", "Apply selected CTCP template");

    JCheckBox toastEnabled = new JCheckBox("Desktop toast", base.toastEnabled());

    JComboBox<IrcEventNotificationRule.FocusScope> focusScope =
        new JComboBox<>(IrcEventNotificationRule.FocusScope.values());
    focusScope.setSelectedItem(
        base.focusScope() != null
            ? base.focusScope()
            : IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY);

    JCheckBox statusBarEnabled = new JCheckBox("Status bar message", base.statusBarEnabled());
    JCheckBox notificationsNodeEnabled =
        new JCheckBox("Notifications node entry", base.notificationsNodeEnabled());

    JCheckBox soundEnabled = new JCheckBox("Play sound", base.soundEnabled());
    JComboBox<BuiltInSound> builtInSound = new JComboBox<>(BuiltInSound.valuesForUi());
    PreferencesDialog.configureBuiltInSoundCombo(builtInSound);
    builtInSound.setSelectedItem(BuiltInSound.fromId(base.soundId()));

    JCheckBox soundUseCustom = new JCheckBox("Use custom file", base.soundUseCustom());
    JTextField soundCustomPath = new JTextField(Objects.toString(base.soundCustomPath(), ""));
    JButton browseCustomSound = new JButton("Browse...");
    JButton clearCustomSound = new JButton("Clear");
    JButton testSound = new JButton("Test");
    PreferencesDialog.configureIconOnlyButton(
        browseCustomSound, "folder-open", "Browse/import custom sound file");
    PreferencesDialog.configureIconOnlyButton(clearCustomSound, "close", "Clear custom sound path");
    PreferencesDialog.configureIconOnlyButton(testSound, "play", "Test selected sound");

    JCheckBox scriptEnabled = new JCheckBox("Run script/program", base.scriptEnabled());
    JTextField scriptPath = new JTextField(Objects.toString(base.scriptPath(), ""));
    JButton browseScript = new JButton("Browse...");
    JButton clearScript = new JButton("Clear");
    PreferencesDialog.configureIconOnlyButton(
        browseScript, "terminal", "Browse for script/program");
    PreferencesDialog.configureIconOnlyButton(clearScript, "close", "Clear script path");

    JTextField scriptArgs = new JTextField(Objects.toString(base.scriptArgs(), ""));
    JTextField scriptWorkingDirectory =
        new JTextField(Objects.toString(base.scriptWorkingDirectory(), ""));
    JButton browseScriptWorkingDirectory = new JButton("Browse...");
    JButton clearScriptWorkingDirectory = new JButton("Clear");
    PreferencesDialog.configureIconOnlyButton(
        browseScriptWorkingDirectory, "settings", "Browse for script working directory");
    PreferencesDialog.configureIconOnlyButton(
        clearScriptWorkingDirectory, "close", "Clear script working directory");

    Runnable refreshSourceFieldState =
        () -> {
          IrcEventNotificationRule.SourceMode mode =
              sourceMode.getSelectedItem() instanceof IrcEventNotificationRule.SourceMode s
                  ? s
                  : IrcEventNotificationRule.SourceMode.ANY;
          boolean needsPattern =
              mode == IrcEventNotificationRule.SourceMode.NICK_LIST
                  || mode == IrcEventNotificationRule.SourceMode.GLOB
                  || mode == IrcEventNotificationRule.SourceMode.REGEX;
          sourcePattern.setEnabled(needsPattern);
          sourcePattern.setEditable(needsPattern);
          String placeholder =
              switch (mode) {
                case NICK_LIST -> "alice, bob";
                case GLOB -> "op*, admin?";
                case REGEX -> "^op[0-9]+$";
                default -> "";
              };
          sourcePattern.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        };

    Runnable refreshChannelFieldState =
        () -> {
          IrcEventNotificationRule.ChannelScope scope =
              channelScope.getSelectedItem() instanceof IrcEventNotificationRule.ChannelScope s
                  ? s
                  : IrcEventNotificationRule.ChannelScope.ALL;
          boolean needsPattern =
              scope == IrcEventNotificationRule.ChannelScope.ONLY
                  || scope == IrcEventNotificationRule.ChannelScope.ALL_EXCEPT;
          channelPatterns.setEnabled(needsPattern);
          channelPatterns.setEditable(needsPattern);
          channelPatterns.putClientProperty(
              FlatClientProperties.PLACEHOLDER_TEXT, needsPattern ? "#staff*, #ops" : "");
        };

    Runnable refreshCtcpFieldState =
        () -> {
          IrcEventNotificationRule.EventType selectedEvent =
              eventType.getSelectedItem() instanceof IrcEventNotificationRule.EventType et
                  ? et
                  : IrcEventNotificationRule.EventType.INVITE_RECEIVED;
          boolean ctcp = selectedEvent == IrcEventNotificationRule.EventType.CTCP_RECEIVED;

          IrcEventNotificationRule.CtcpMatchMode selectedCommandMode =
              ctcpCommandMode.getSelectedItem() instanceof IrcEventNotificationRule.CtcpMatchMode m
                  ? m
                  : IrcEventNotificationRule.CtcpMatchMode.ANY;
          boolean commandNeedsPattern =
              ctcp && selectedCommandMode != IrcEventNotificationRule.CtcpMatchMode.ANY;
          ctcpCommandMode.setEnabled(ctcp);
          ctcpCommandPattern.setEnabled(commandNeedsPattern);
          ctcpCommandPattern.setEditable(commandNeedsPattern);
          ctcpCommandPattern.putClientProperty(
              FlatClientProperties.PLACEHOLDER_TEXT,
              commandNeedsPattern ? "VERSION / PING / TIME / CLIENTINFO" : "");

          IrcEventNotificationRule.CtcpMatchMode selectedValueMode =
              ctcpValueMode.getSelectedItem() instanceof IrcEventNotificationRule.CtcpMatchMode m
                  ? m
                  : IrcEventNotificationRule.CtcpMatchMode.ANY;
          boolean valueNeedsPattern =
              ctcp && selectedValueMode != IrcEventNotificationRule.CtcpMatchMode.ANY;
          ctcpValueMode.setEnabled(ctcp);
          ctcpValuePattern.setEnabled(valueNeedsPattern);
          ctcpValuePattern.setEditable(valueNeedsPattern);
          ctcpValuePattern.putClientProperty(
              FlatClientProperties.PLACEHOLDER_TEXT, valueNeedsPattern ? "argument pattern" : "");

          ctcpTemplate.setEnabled(ctcp);
          applyCtcpTemplate.setEnabled(ctcp);
        };

    Runnable refreshSoundState =
        () -> {
          boolean soundOn = soundEnabled.isSelected();
          soundUseCustom.setEnabled(soundOn);
          boolean useCustom = soundOn && soundUseCustom.isSelected();
          builtInSound.setEnabled(soundOn && !useCustom);
          soundCustomPath.setEnabled(soundOn && useCustom);
          soundCustomPath.setEditable(soundOn && useCustom);
          browseCustomSound.setEnabled(soundOn && useCustom);
          String custom = Objects.toString(soundCustomPath.getText(), "").trim();
          clearCustomSound.setEnabled(soundOn && useCustom && !custom.isBlank());
          testSound.setEnabled(soundOn);
        };

    Runnable refreshScriptState =
        () -> {
          boolean run = scriptEnabled.isSelected();
          scriptPath.setEnabled(run);
          scriptPath.setEditable(run);
          browseScript.setEnabled(run);
          clearScript.setEnabled(
              run && !Objects.toString(scriptPath.getText(), "").trim().isBlank());
          scriptArgs.setEnabled(run);
          scriptArgs.setEditable(run);
          scriptWorkingDirectory.setEnabled(run);
          scriptWorkingDirectory.setEditable(run);
          browseScriptWorkingDirectory.setEnabled(run);
          clearScriptWorkingDirectory.setEnabled(
              run && !Objects.toString(scriptWorkingDirectory.getText(), "").trim().isBlank());
        };

    final IrcEventNotificationRule.EventType[] priorEvent =
        new IrcEventNotificationRule.EventType[] {
          eventType.getSelectedItem() instanceof IrcEventNotificationRule.EventType e
              ? e
              : IrcEventNotificationRule.EventType.INVITE_RECEIVED
        };

    eventType.addActionListener(
        e -> {
          IrcEventNotificationRule.EventType selectedEvent =
              eventType.getSelectedItem() instanceof IrcEventNotificationRule.EventType et
                  ? et
                  : IrcEventNotificationRule.EventType.INVITE_RECEIVED;
          IrcEventNotificationRule.EventType previous = priorEvent[0];
          if (previous == null) previous = IrcEventNotificationRule.EventType.INVITE_RECEIVED;
          if (!soundUseCustom.isSelected()) {
            Object selectedSound = builtInSound.getSelectedItem();
            if (selectedSound instanceof BuiltInSound currentSound) {
              BuiltInSound previousDefault =
                  PreferencesDialog.defaultBuiltInSoundForIrcEventRule(previous);
              if (currentSound == previousDefault) {
                builtInSound.setSelectedItem(
                    PreferencesDialog.defaultBuiltInSoundForIrcEventRule(selectedEvent));
              }
            }
          }
          priorEvent[0] = selectedEvent;
          refreshCtcpFieldState.run();
        });

    sourceMode.addActionListener(e -> refreshSourceFieldState.run());
    channelScope.addActionListener(e -> refreshChannelFieldState.run());
    ctcpCommandMode.addActionListener(e -> refreshCtcpFieldState.run());
    ctcpValueMode.addActionListener(e -> refreshCtcpFieldState.run());
    soundEnabled.addActionListener(e -> refreshSoundState.run());
    soundUseCustom.addActionListener(e -> refreshSoundState.run());
    soundCustomPath.getDocument().addDocumentListener(new DocChangeListener(refreshSoundState));
    scriptEnabled.addActionListener(e -> refreshScriptState.run());
    scriptPath.getDocument().addDocumentListener(new DocChangeListener(refreshScriptState));
    scriptWorkingDirectory
        .getDocument()
        .addDocumentListener(new DocChangeListener(refreshScriptState));

    browseCustomSound.addActionListener(
        e -> {
          try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose notification sound (MP3 or WAV)");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setAcceptAllFileFilterUsed(true);
            chooser.addChoosableFileFilter(
                new FileNameExtensionFilter("Audio files (MP3, WAV)", "mp3", "wav"));
            int result = chooser.showOpenDialog(owner);
            if (result != JFileChooser.APPROVE_OPTION) return;
            File f = chooser.getSelectedFile();
            if (f == null || soundFileImporter == null) return;
            String rel = soundFileImporter.importFile(f);
            if (rel != null && !rel.isBlank()) {
              soundCustomPath.setText(rel);
              soundUseCustom.setSelected(true);
              refreshSoundState.run();
            }
          } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                owner,
                "Could not import sound file.\n\n" + ex.getMessage(),
                "Import failed",
                JOptionPane.ERROR_MESSAGE);
          }
        });

    clearCustomSound.addActionListener(
        e -> {
          soundUseCustom.setSelected(false);
          soundCustomPath.setText("");
          refreshSoundState.run();
        });

    testSound.addActionListener(
        e -> {
          try {
            if (notificationSoundService == null) return;
            if (soundUseCustom.isSelected()) {
              String rel = Objects.toString(soundCustomPath.getText(), "").trim();
              if (!rel.isBlank()) notificationSoundService.previewCustom(rel);
            } else {
              BuiltInSound sound =
                  builtInSound.getSelectedItem() instanceof BuiltInSound s ? s : null;
              notificationSoundService.preview(sound);
            }
          } catch (Throwable ignored) {
          }
        });

    browseScript.addActionListener(
        e -> {
          JFileChooser chooser = new JFileChooser();
          chooser.setDialogTitle("Select script/program");
          chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
          chooser.setAcceptAllFileFilterUsed(true);
          int result = chooser.showOpenDialog(owner);
          if (result != JFileChooser.APPROVE_OPTION) return;
          File selected = chooser.getSelectedFile();
          if (selected == null) return;
          scriptPath.setText(selected.getAbsolutePath());
          refreshScriptState.run();
        });

    clearScript.addActionListener(
        e -> {
          scriptPath.setText("");
          refreshScriptState.run();
        });

    browseScriptWorkingDirectory.addActionListener(
        e -> {
          JFileChooser chooser = new JFileChooser();
          chooser.setDialogTitle("Select script working directory");
          chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
          chooser.setAcceptAllFileFilterUsed(true);
          int result = chooser.showOpenDialog(owner);
          if (result != JFileChooser.APPROVE_OPTION) return;
          File selected = chooser.getSelectedFile();
          if (selected == null) return;
          scriptWorkingDirectory.setText(selected.getAbsolutePath());
          refreshScriptState.run();
        });

    clearScriptWorkingDirectory.addActionListener(
        e -> {
          scriptWorkingDirectory.setText("");
          refreshScriptState.run();
        });

    applyCtcpTemplate.addActionListener(
        e -> {
          CtcpNotificationRuleTemplate template =
              ctcpTemplate.getSelectedItem() instanceof CtcpNotificationRuleTemplate t
                  ? t
                  : CtcpNotificationRuleTemplate.CUSTOM;
          eventType.setSelectedItem(IrcEventNotificationRule.EventType.CTCP_RECEIVED);
          if (template == CtcpNotificationRuleTemplate.CUSTOM) {
            ctcpCommandMode.setSelectedItem(IrcEventNotificationRule.CtcpMatchMode.ANY);
            ctcpCommandPattern.setText("");
            ctcpValueMode.setSelectedItem(IrcEventNotificationRule.CtcpMatchMode.ANY);
            ctcpValuePattern.setText("");
            refreshCtcpFieldState.run();
            return;
          }
          ctcpCommandMode.setSelectedItem(IrcEventNotificationRule.CtcpMatchMode.LIKE);
          ctcpCommandPattern.setText(template.command());
          ctcpValueMode.setSelectedItem(IrcEventNotificationRule.CtcpMatchMode.ANY);
          ctcpValuePattern.setText("");
          refreshCtcpFieldState.run();
        });

    refreshSourceFieldState.run();
    refreshChannelFieldState.run();
    refreshCtcpFieldState.run();
    refreshSoundState.run();
    refreshScriptState.run();

    JPanel filtersPanel =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 2,hidemode 3",
                "[right]8[grow,fill]",
                "[]6[]6[]6[]6[]6[]6[]6[]6[]"));
    filtersPanel.add(enabled, "span 2,wrap");
    filtersPanel.add(new JLabel("Event"));
    filtersPanel.add(eventType, "growx, wmin 220, wrap");
    filtersPanel.add(new JLabel("Source"));
    filtersPanel.add(sourceMode, "growx, wrap");
    filtersPanel.add(new JLabel("Source match"));
    filtersPanel.add(sourcePattern, "growx, wrap");
    filtersPanel.add(new JLabel("Channel scope"));
    filtersPanel.add(channelScope, "growx, wrap");
    filtersPanel.add(new JLabel("Channels"));
    filtersPanel.add(channelPatterns, "growx, wrap");

    JPanel ctcpCommandRow =
        new JPanel(new MigLayout("insets 0,fillx", "[pref!]8[grow,fill]", "[]"));
    ctcpCommandRow.add(ctcpCommandMode, "w 110!");
    ctcpCommandRow.add(ctcpCommandPattern, "growx, pushx, wmin 0");
    filtersPanel.add(new JLabel("CTCP command"));
    filtersPanel.add(ctcpCommandRow, "growx, wmin 0, wrap");

    JPanel ctcpValueRow = new JPanel(new MigLayout("insets 0,fillx", "[pref!]8[grow,fill]", "[]"));
    ctcpValueRow.add(ctcpValueMode, "w 110!");
    ctcpValueRow.add(ctcpValuePattern, "growx, pushx, wmin 0");
    filtersPanel.add(new JLabel("CTCP value"));
    filtersPanel.add(ctcpValueRow, "growx, wmin 0, wrap");

    JPanel ctcpTemplateRow = new JPanel(new MigLayout("insets 0,fillx", "[grow,fill]8[]", "[]"));
    ctcpTemplateRow.add(ctcpTemplate, "growx, pushx, wmin 0");
    ctcpTemplateRow.add(applyCtcpTemplate, "w 36!, h 28!");
    filtersPanel.add(new JLabel("CTCP template"));
    filtersPanel.add(ctcpTemplateRow, "growx, wmin 0, wrap");
    filtersPanel.add(new JLabel(""));
    filtersPanel.add(
        helpText(
            "Active channel only means the event target must match the currently selected channel on the same server.\n"
                + "CTCP command/value filters only apply when Event is CTCP Request Received."),
        "growx, wmin 0, wrap");

    JPanel actionsPanel =
        new JPanel(
            new MigLayout("insets 10,fillx,wrap 2,hidemode 3", "[right]8[grow,fill]", "[]6[]6[]"));
    actionsPanel.add(toastEnabled, "span 2, growx, wrap");
    actionsPanel.add(new JLabel("Toast focus"));
    actionsPanel.add(focusScope, "growx, wrap");
    actionsPanel.add(statusBarEnabled, "span 2, growx, wrap");
    actionsPanel.add(notificationsNodeEnabled, "span 2, growx, wrap");
    actionsPanel.add(new JLabel(""));
    actionsPanel.add(
        helpText(
            "Tip: combine multiple rules for the same event to split foreground/background behavior."),
        "growx, wmin 0, wrap");

    JPanel soundPanel =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 4,hidemode 3", "[right]8[grow,fill]8[]8[]", "[]6[]4[]"));
    soundPanel.add(soundEnabled, "span 4, growx, wrap");
    soundPanel.add(new JLabel("Built-in"));
    soundPanel.add(builtInSound, "growx, wmin 180");
    soundPanel.add(testSound, "w 36!, h 28!");
    soundPanel.add(soundUseCustom, "wrap");
    soundPanel.add(new JLabel("Custom file"));
    soundPanel.add(soundCustomPath, "growx, pushx, wmin 0");
    soundPanel.add(browseCustomSound, "w 36!, h 28!");
    soundPanel.add(clearCustomSound, "w 36!, h 28!, wrap");
    soundPanel.add(new JLabel(""));
    soundPanel.add(
        helpText("When Sound is disabled on a rule, no sound is played for that event."),
        "span 3, growx, wmin 0, wrap");

    JPanel scriptPanel =
        new JPanel(
            new MigLayout(
                "insets 10,fillx,wrap 4,hidemode 3", "[right]8[grow,fill]8[]8[]", "[]6[]4[]"));
    scriptPanel.add(scriptEnabled, "span 4, growx, wrap");
    scriptPanel.add(new JLabel("Script path"));
    scriptPanel.add(scriptPath, "growx, pushx, wmin 0");
    scriptPanel.add(browseScript, "w 36!, h 28!");
    scriptPanel.add(clearScript, "w 36!, h 28!, wrap");
    scriptPanel.add(new JLabel("Arguments"));
    scriptPanel.add(scriptArgs, "span 3, growx, wmin 0, wrap");
    scriptPanel.add(new JLabel("Working dir"));
    scriptPanel.add(scriptWorkingDirectory, "growx, pushx, wmin 0");
    scriptPanel.add(browseScriptWorkingDirectory, "w 36!, h 28!");
    scriptPanel.add(clearScriptWorkingDirectory, "w 36!, h 28!, wrap");
    scriptPanel.add(new JLabel(""));
    scriptPanel.add(
        helpText(
            "If enabled, IRCafe executes the script and sets env vars:\n"
                + "IRCAFE_EVENT_TYPE, IRCAFE_SERVER_ID, IRCAFE_CHANNEL, IRCAFE_SOURCE_NICK,\n"
                + "IRCAFE_SOURCE_IS_SELF, IRCAFE_TITLE, IRCAFE_BODY,\n"
                + "IRCAFE_CTCP_COMMAND, IRCAFE_CTCP_VALUE, IRCAFE_TIMESTAMP_MS.\n"
                + "Arguments support quotes/escapes and are passed directly (no shell expansion)."),
        "span 3, growx, wmin 0, wrap");

    JTabbedPane tabs = new JTabbedPane();
    tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
    tabs.addTab("Filters", filtersPanel);
    tabs.addTab("Actions", actionsPanel);
    tabs.addTab("Sound", soundPanel);
    tabs.addTab("Script", scriptPanel);
    tabs.setPreferredSize(new Dimension(640, 420));

    JPanel form = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[grow,fill]"));
    form.add(tabs, "grow, push, wmin 0");

    String dialogTitle = Objects.toString(title, "IRC Event Rule");
    while (true) {
      int choice =
          JOptionPane.showConfirmDialog(
              owner, form, dialogTitle, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (choice != JOptionPane.OK_OPTION) return null;

      IrcEventNotificationRule.EventType selectedEvent =
          eventType.getSelectedItem() instanceof IrcEventNotificationRule.EventType ev
              ? ev
              : IrcEventNotificationRule.EventType.INVITE_RECEIVED;
      IrcEventNotificationRule.SourceMode selectedSourceMode =
          sourceMode.getSelectedItem() instanceof IrcEventNotificationRule.SourceMode mode
              ? mode
              : IrcEventNotificationRule.SourceMode.ANY;
      IrcEventNotificationRule.ChannelScope selectedChannelScope =
          channelScope.getSelectedItem() instanceof IrcEventNotificationRule.ChannelScope scope
              ? scope
              : IrcEventNotificationRule.ChannelScope.ALL;
      IrcEventNotificationRule.FocusScope selectedFocusScope =
          focusScope.getSelectedItem() instanceof IrcEventNotificationRule.FocusScope focus
              ? focus
              : IrcEventNotificationRule.FocusScope.BACKGROUND_ONLY;

      String sourcePatternValue = Objects.toString(sourcePattern.getText(), "").trim();
      if (sourcePatternValue.isEmpty()) sourcePatternValue = null;
      boolean sourceNeedsPattern =
          selectedSourceMode == IrcEventNotificationRule.SourceMode.NICK_LIST
              || selectedSourceMode == IrcEventNotificationRule.SourceMode.GLOB
              || selectedSourceMode == IrcEventNotificationRule.SourceMode.REGEX;
      if (sourceNeedsPattern && sourcePatternValue == null) {
        JOptionPane.showMessageDialog(
            owner,
            "Source mode \"" + selectedSourceMode + "\" requires a source pattern.",
            "Invalid IRC Event Rule",
            JOptionPane.ERROR_MESSAGE);
        tabs.setSelectedIndex(0);
        continue;
      }
      if (selectedSourceMode == IrcEventNotificationRule.SourceMode.REGEX
          && sourcePatternValue != null) {
        try {
          Pattern.compile(sourcePatternValue);
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(
              owner,
              "Invalid source regex pattern:\n"
                  + Objects.toString(ex.getMessage(), "Invalid regex"),
              "Invalid IRC Event Rule",
              JOptionPane.ERROR_MESSAGE);
          tabs.setSelectedIndex(0);
          continue;
        }
      }
      if (!sourceNeedsPattern) sourcePatternValue = null;

      String channelPatternsValue = Objects.toString(channelPatterns.getText(), "").trim();
      if (channelPatternsValue.isEmpty()) channelPatternsValue = null;
      boolean channelNeedsPattern =
          selectedChannelScope == IrcEventNotificationRule.ChannelScope.ONLY
              || selectedChannelScope == IrcEventNotificationRule.ChannelScope.ALL_EXCEPT;
      if (channelNeedsPattern && channelPatternsValue == null) {
        JOptionPane.showMessageDialog(
            owner,
            "Channel scope \"" + selectedChannelScope + "\" requires channel patterns.",
            "Invalid IRC Event Rule",
            JOptionPane.ERROR_MESSAGE);
        tabs.setSelectedIndex(0);
        continue;
      }
      if (!channelNeedsPattern) channelPatternsValue = null;

      IrcEventNotificationRule.CtcpMatchMode selectedCtcpCommandMode =
          ctcpCommandMode.getSelectedItem() instanceof IrcEventNotificationRule.CtcpMatchMode mode
              ? mode
              : IrcEventNotificationRule.CtcpMatchMode.ANY;
      IrcEventNotificationRule.CtcpMatchMode selectedCtcpValueMode =
          ctcpValueMode.getSelectedItem() instanceof IrcEventNotificationRule.CtcpMatchMode mode
              ? mode
              : IrcEventNotificationRule.CtcpMatchMode.ANY;
      String ctcpCommandPatternValue = Objects.toString(ctcpCommandPattern.getText(), "").trim();
      if (ctcpCommandPatternValue.isEmpty()) ctcpCommandPatternValue = null;
      String ctcpValuePatternValue = Objects.toString(ctcpValuePattern.getText(), "").trim();
      if (ctcpValuePatternValue.isEmpty()) ctcpValuePatternValue = null;

      boolean ctcpEvent = selectedEvent == IrcEventNotificationRule.EventType.CTCP_RECEIVED;
      if (ctcpEvent && selectedCtcpCommandMode != IrcEventNotificationRule.CtcpMatchMode.ANY) {
        if (ctcpCommandPatternValue == null) {
          JOptionPane.showMessageDialog(
              owner,
              "CTCP command mode \"" + selectedCtcpCommandMode + "\" requires a pattern.",
              "Invalid IRC Event Rule",
              JOptionPane.ERROR_MESSAGE);
          tabs.setSelectedIndex(0);
          continue;
        }
        if (selectedCtcpCommandMode == IrcEventNotificationRule.CtcpMatchMode.REGEX) {
          try {
            Pattern.compile(ctcpCommandPatternValue);
          } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                owner,
                "Invalid CTCP command regex pattern:\n"
                    + Objects.toString(ex.getMessage(), "Invalid regex"),
                "Invalid IRC Event Rule",
                JOptionPane.ERROR_MESSAGE);
            tabs.setSelectedIndex(0);
            continue;
          }
        }
      }
      if (ctcpEvent && selectedCtcpValueMode != IrcEventNotificationRule.CtcpMatchMode.ANY) {
        if (ctcpValuePatternValue == null) {
          JOptionPane.showMessageDialog(
              owner,
              "CTCP value mode \"" + selectedCtcpValueMode + "\" requires a pattern.",
              "Invalid IRC Event Rule",
              JOptionPane.ERROR_MESSAGE);
          tabs.setSelectedIndex(0);
          continue;
        }
        if (selectedCtcpValueMode == IrcEventNotificationRule.CtcpMatchMode.REGEX) {
          try {
            Pattern.compile(ctcpValuePatternValue);
          } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                owner,
                "Invalid CTCP value regex pattern:\n"
                    + Objects.toString(ex.getMessage(), "Invalid regex"),
                "Invalid IRC Event Rule",
                JOptionPane.ERROR_MESSAGE);
            tabs.setSelectedIndex(0);
            continue;
          }
        }
      }
      if (!ctcpEvent) {
        selectedCtcpCommandMode = IrcEventNotificationRule.CtcpMatchMode.ANY;
        ctcpCommandPatternValue = null;
        selectedCtcpValueMode = IrcEventNotificationRule.CtcpMatchMode.ANY;
        ctcpValuePatternValue = null;
      }

      BuiltInSound selectedSound =
          builtInSound.getSelectedItem() instanceof BuiltInSound sound
              ? sound
              : PreferencesDialog.defaultBuiltInSoundForIrcEventRule(selectedEvent);
      String soundCustomPathValue = Objects.toString(soundCustomPath.getText(), "").trim();
      if (soundCustomPathValue.isEmpty()) soundCustomPathValue = null;
      boolean useCustomSound = soundUseCustom.isSelected() && soundCustomPathValue != null;

      String scriptPathValue = Objects.toString(scriptPath.getText(), "").trim();
      if (scriptPathValue.isEmpty()) scriptPathValue = null;
      String scriptArgsValue = Objects.toString(scriptArgs.getText(), "").trim();
      if (scriptArgsValue.isEmpty()) scriptArgsValue = null;
      String scriptWorkingDirectoryValue =
          Objects.toString(scriptWorkingDirectory.getText(), "").trim();
      if (scriptWorkingDirectoryValue.isEmpty()) scriptWorkingDirectoryValue = null;
      boolean runScript = scriptEnabled.isSelected();
      if (runScript && scriptPathValue == null) {
        JOptionPane.showMessageDialog(
            owner,
            "Script path is required when Run script/program is enabled.",
            "Invalid IRC Event Rule",
            JOptionPane.ERROR_MESSAGE);
        tabs.setSelectedIndex(3);
        continue;
      }

      return new IrcEventNotificationRule(
          enabled.isSelected(),
          selectedEvent,
          selectedSourceMode,
          sourcePatternValue,
          selectedChannelScope,
          channelPatternsValue,
          toastEnabled.isSelected(),
          selectedFocusScope,
          statusBarEnabled.isSelected(),
          notificationsNodeEnabled.isSelected(),
          soundEnabled.isSelected(),
          selectedSound.name(),
          useCustomSound,
          soundCustomPathValue,
          runScript,
          scriptPathValue,
          scriptArgsValue,
          scriptWorkingDirectoryValue,
          selectedCtcpCommandMode,
          ctcpCommandPatternValue,
          selectedCtcpValueMode,
          ctcpValuePatternValue);
    }
  }

  @FunctionalInterface
  interface SoundFileImporter {
    String importFile(File source) throws Exception;
  }

  private static JTextArea helpText(String text) {
    JTextArea t = new JTextArea(text);
    t.setEditable(false);
    t.setLineWrap(true);
    t.setWrapStyleWord(true);
    t.setOpaque(false);
    t.setFocusable(false);
    t.setBorder(null);
    t.setFont(UIManager.getFont("Label.font"));
    t.setForeground(UIManager.getColor("Label.foreground"));
    Dimension pref = t.getPreferredSize();
    t.setMinimumSize(new Dimension(0, pref != null ? pref.height : 0));
    return t;
  }

  private static final class DocChangeListener implements DocumentListener {
    private final Runnable onChange;

    private DocChangeListener(Runnable onChange) {
      this.onChange = onChange;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      onChange.run();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      onChange.run();
    }
  }

  private enum CtcpNotificationRuleTemplate {
    CUSTOM("Custom", null),
    VERSION("VERSION request", "VERSION"),
    PING("PING request", "PING"),
    TIME("TIME request", "TIME"),
    CLIENTINFO("CLIENTINFO request", "CLIENTINFO"),
    SOURCE("SOURCE request", "SOURCE"),
    USERINFO("USERINFO request", "USERINFO");

    private final String label;
    private final String command;

    CtcpNotificationRuleTemplate(String label, String command) {
      this.label = label;
      this.command = command;
    }

    String command() {
      return command;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
