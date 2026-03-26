package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

record TypingTreeIndicatorStyleOption(String id, String label) {}

record MatrixUserListNameDisplayModeOption(String id, String label) {}

final class ChatBehaviorControlsSupport {
  private ChatBehaviorControlsSupport() {}

  static JCheckBox buildPresenceFoldsCheckbox(UiSettings current) {
    JCheckBox presenceFolds = new JCheckBox("Fold join/part/quit spam into a compact block");
    presenceFolds.setSelected(current.presenceFoldsEnabled());
    presenceFolds.setToolTipText(
        "When enabled, runs of join/part/quit/nick-change events are folded into a single expandable block.\n"
            + "When disabled, each event is shown as its own status line.");
    return presenceFolds;
  }

  static JCheckBox buildCtcpRequestsInActiveTargetCheckbox(UiSettings current) {
    JCheckBox ctcp = new JCheckBox("Show inbound CTCP requests in the currently active chat tab");
    ctcp.setSelected(current.ctcpRequestsInActiveTargetEnabled());
    ctcp.setToolTipText(
        "When enabled, inbound CTCP requests (e.g. VERSION, PING) are announced in the currently active chat tab.\n"
            + "When disabled, CTCP requests are routed to the target they came from (channel or PM).");
    return ctcp;
  }

  static JTextField buildDefaultQuitMessageField(RuntimeConfigStore runtimeConfig) {
    JTextField field =
        new JTextField(runtimeConfig != null ? runtimeConfig.readDefaultQuitMessage() : "");
    field.setToolTipText(
        "Used when /quit has no explicit reason, and when IRCafe closes IRC connections during shutdown.");
    return field;
  }

  static JCheckBox buildOutgoingDeliveryIndicatorsCheckbox(UiSettings current) {
    JCheckBox checkbox =
        new JCheckBox("Show send-status indicators for my outgoing messages (spinner + green dot)");
    checkbox.setSelected(current.outgoingDeliveryIndicatorsEnabled());
    checkbox.setToolTipText(
        "When enabled, outgoing messages show a pending spinner and a brief green confirmation dot when server echo reconciliation completes.\n"
            + "When disabled, these visual indicators are hidden; message send/reconcile behavior is unchanged.");
    return checkbox;
  }

  static JCheckBox buildTypingIndicatorsSendCheckbox(UiSettings current) {
    JCheckBox checkbox = new JCheckBox("Send typing indicators (IRCv3)");
    checkbox.setSelected(current.typingIndicatorsEnabled());
    checkbox.setToolTipText(
        "When enabled, IRCafe will send your IRCv3 typing state (active/paused/done) when the server supports it.");
    return checkbox;
  }

  static JCheckBox buildTypingIndicatorsReceiveCheckbox(UiSettings current) {
    JCheckBox checkbox = new JCheckBox("Display incoming typing indicators (IRCv3)");
    checkbox.setSelected(current.typingIndicatorsReceiveEnabled());
    checkbox.setToolTipText(
        "When enabled, IRCafe will display incoming IRCv3 typing indicators from other users.");
    return checkbox;
  }

  static JCheckBox buildTypingIndicatorsTreeDisplayCheckbox(UiSettings current) {
    JCheckBox checkbox = new JCheckBox("Show typing marker next to channels");
    checkbox.setSelected(current.typingIndicatorsTreeEnabled());
    checkbox.setToolTipText(
        "Controls typing markers in the server tree channel list.\n"
            + "Typing transport behavior is unchanged.");
    return checkbox;
  }

  static JCheckBox buildTypingIndicatorsUsersListDisplayCheckbox(UiSettings current) {
    JCheckBox checkbox = new JCheckBox("Show typing marker next to users");
    checkbox.setSelected(current.typingIndicatorsUsersListEnabled());
    checkbox.setToolTipText(
        "Controls typing markers beside nicknames in the channel user list.\n"
            + "Typing transport behavior is unchanged.");
    return checkbox;
  }

  static JCheckBox buildTypingIndicatorsTranscriptDisplayCheckbox(UiSettings current) {
    JCheckBox checkbox = new JCheckBox("Show typing status in the transcript input area");
    checkbox.setSelected(current.typingIndicatorsTranscriptEnabled());
    checkbox.setToolTipText(
        "Controls the incoming typing banner above the input field (\"X is typing\").\n"
            + "Typing transport behavior is unchanged.");
    return checkbox;
  }

  static JCheckBox buildTypingIndicatorsSendSignalDisplayCheckbox(UiSettings current) {
    JCheckBox checkbox = new JCheckBox("Show local typing-send arrows near Send");
    checkbox.setSelected(current.typingIndicatorsSendSignalEnabled());
    checkbox.setToolTipText(
        "Controls the local send telemetry arrows near the Send button.\n"
            + "Typing transport behavior is unchanged.");
    return checkbox;
  }

  static JComboBox<TypingTreeIndicatorStyleOption> buildTypingTreeIndicatorStyleCombo(
      UiSettings current) {
    TypingTreeIndicatorStyleOption[] options =
        new TypingTreeIndicatorStyleOption[] {
          new TypingTreeIndicatorStyleOption("dots", "3 dots (ellipsis)"),
          new TypingTreeIndicatorStyleOption("keyboard", "Keyboard glyph"),
          new TypingTreeIndicatorStyleOption("glow-dot", "Glowing green dot")
        };
    JComboBox<TypingTreeIndicatorStyleOption> combo = new JComboBox<>(options);
    combo.setToolTipText("Choose how typing activity appears in the server tree for channels.");
    combo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            if (value instanceof TypingTreeIndicatorStyleOption option) {
              label.setText(option.label());
            }
            return label;
          }
        });

    String configured = current != null ? current.typingIndicatorsTreeStyle() : null;
    String normalized = UiSettings.normalizeTypingTreeIndicatorStyle(configured);
    for (TypingTreeIndicatorStyleOption option : options) {
      if (option.id().equalsIgnoreCase(normalized)) {
        combo.setSelectedItem(option);
        break;
      }
    }
    return combo;
  }

  static JComboBox<MatrixUserListNameDisplayModeOption> buildMatrixUserListNameDisplayModeCombo(
      UiSettings current) {
    MatrixUserListNameDisplayModeOption[] options =
        new MatrixUserListNameDisplayModeOption[] {
          new MatrixUserListNameDisplayModeOption("compact", "Display name only (compact)"),
          new MatrixUserListNameDisplayModeOption(
              "verbose", "Display name + Matrix user ID (verbose)")
        };
    JComboBox<MatrixUserListNameDisplayModeOption> combo = new JComboBox<>(options);
    combo.setToolTipText(
        "Controls how Matrix users are shown in the channel user list (display name only or display name with Matrix user ID).");
    combo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            if (value instanceof MatrixUserListNameDisplayModeOption option) {
              label.setText(option.label());
            }
            return label;
          }
        });

    String configured = current != null ? current.matrixUserListNameDisplayMode() : null;
    String normalized = UiSettings.normalizeMatrixUserListNameDisplayMode(configured);
    for (MatrixUserListNameDisplayModeOption option : options) {
      if (option.id().equalsIgnoreCase(normalized)) {
        combo.setSelectedItem(option);
        break;
      }
    }
    return combo;
  }

  static JCheckBox buildServerTreeNotificationBadgesCheckbox(UiSettings current) {
    JCheckBox checkbox = new JCheckBox("Show unread/highlight badges in the server tree");
    checkbox.setSelected(current.serverTreeNotificationBadgesEnabled());
    checkbox.setToolTipText(
        "When enabled, the server tree shows numeric unread/highlight badges next to targets.\n"
            + "When disabled, badge counts are hidden but unread/highlight tracking still runs.");
    return checkbox;
  }

  static JSpinner buildServerTreeUnreadBadgeScalePercentSpinner(RuntimeConfigStore runtimeConfig) {
    int current =
        runtimeConfig != null ? runtimeConfig.readServerTreeUnreadBadgeScalePercent(100) : 100;
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(current, 50, 150, 5));
    spinner.setToolTipText(
        "Scale for unread/highlight count badges in the server tree. Lower values make badges and numbers smaller.");
    return spinner;
  }

  static String typingTreeIndicatorStyleValue(JComboBox<TypingTreeIndicatorStyleOption> combo) {
    Object selected = combo != null ? combo.getSelectedItem() : null;
    if (selected instanceof TypingTreeIndicatorStyleOption option) {
      return UiSettings.normalizeTypingTreeIndicatorStyle(option.id());
    }
    return "dots";
  }

  static String matrixUserListNameDisplayModeValue(
      JComboBox<MatrixUserListNameDisplayModeOption> combo) {
    Object selected = combo != null ? combo.getSelectedItem() : null;
    if (selected instanceof MatrixUserListNameDisplayModeOption option) {
      return UiSettings.normalizeMatrixUserListNameDisplayMode(option.id());
    }
    return "compact";
  }
}
