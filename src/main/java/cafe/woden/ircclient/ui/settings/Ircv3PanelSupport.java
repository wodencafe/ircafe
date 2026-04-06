package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityCatalog;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import net.miginfocom.swing.MigLayout;

final class Ircv3PanelSupport {
  private Ircv3PanelSupport() {}

  static Ircv3CapabilitiesControls buildCapabilitiesControls(RuntimeConfigStore runtimeConfig) {
    Map<String, Boolean> persisted = runtimeConfig.readIrcv3Capabilities();

    LinkedHashMap<String, JCheckBox> checkboxes = new LinkedHashMap<>();
    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1, hidemode 3", "[grow,fill]", "[]6[]"));
    panel.setOpaque(false);

    LinkedHashMap<String, List<String>> grouped = new LinkedHashMap<>();
    for (String capability : Ircv3CapabilityCatalog.requestableCapabilities()) {
      String key = normalizeCapabilityKey(capability);
      if (key.isEmpty()) continue;
      grouped.computeIfAbsent(capabilityGroupKey(key), __ -> new ArrayList<>()).add(key);
    }

    for (Map.Entry<String, List<String>> group : grouped.entrySet()) {
      List<String> caps = group.getValue();
      if (caps == null || caps.isEmpty()) continue;

      List<String> orderedCaps = new ArrayList<>(caps);
      orderedCaps.sort(
          (left, right) -> {
            int leftOrder = capabilitySortOrder(left);
            int rightOrder = capabilitySortOrder(right);
            if (leftOrder != rightOrder) return Integer.compare(leftOrder, rightOrder);
            return capabilityDisplayLabel(left).compareToIgnoreCase(capabilityDisplayLabel(right));
          });

      JPanel groupPanel =
          new JPanel(
              new MigLayout(
                  "insets 6 8 8 8, fillx, wrap 2, hidemode 3",
                  "[grow,fill]12[grow,fill]",
                  "[]2[]"));
      groupPanel.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createTitledBorder(capabilityGroupTitle(group.getKey())),
              BorderFactory.createEmptyBorder(4, 6, 4, 6)));
      groupPanel.setOpaque(false);

      for (String key : orderedCaps) {
        JCheckBox checkbox = new JCheckBox(capabilityDisplayLabel(key));
        checkbox.setSelected(persisted.getOrDefault(key, Boolean.TRUE));
        checkbox.setToolTipText(capabilityImpactSummary(key));
        checkboxes.put(key, checkbox);

        JButton help =
            PreferencesDialog.whyHelpButton(capabilityHelpTitle(key), capabilityHelpMessage(key));
        help.setToolTipText("What does this capability do in IRCafe?");

        JPanel row = new JPanel(new MigLayout("insets 0, fillx", "[grow,fill]4[]", "[]"));
        row.setOpaque(false);
        row.add(checkbox, "growx, wmin 0");
        row.add(help, "aligny center");

        groupPanel.add(row, "growx, wmin 0");
      }

      panel.add(groupPanel, "growx, wmin 0, wrap");
    }

    return new Ircv3CapabilitiesControls(checkboxes, panel);
  }

  static JPanel buildPanel(
      JCheckBox typingIndicatorsSendEnabled,
      JCheckBox typingIndicatorsReceiveEnabled,
      JCheckBox typingIndicatorsTreeDisplayEnabled,
      JCheckBox typingIndicatorsUsersListDisplayEnabled,
      JCheckBox typingIndicatorsTranscriptDisplayEnabled,
      JCheckBox typingIndicatorsSendSignalDisplayEnabled,
      JComboBox<?> typingTreeIndicatorStyle,
      JComboBox<?> matrixUserListNameDisplayMode,
      JCheckBox serverTreeNotificationBadgesEnabled,
      JSpinner serverTreeUnreadBadgeScalePercent,
      Ircv3CapabilitiesControls ircv3Capabilities) {
    JPanel form =
        new JPanel(
            new MigLayout(
                "insets 12, fill, wrap 1, hidemode 3", "[grow,fill]", "[]8[]8[grow,fill]"));

    form.add(PreferencesDialog.tabTitle("IRCv3"), "growx, wmin 0, wrap");
    form.add(
        PreferencesDialog.subtleInfoTextWith(
            "Typing and capability settings for modern IRCv3 features. Capability changes apply on reconnect."),
        "growx, wmin 0, wrap");

    JButton typingHelp =
        PreferencesDialog.whyHelpButton(
            "Typing indicators",
            "What it is:\n"
                + "Typing indicators show when someone is actively typing or has paused.\n\n"
                + "Impact in IRCafe:\n"
                + "- Send: broadcasts your typing state to peers when supported.\n"
                + "- Display: shows incoming typing state in the active UI.\n\n"
                + "If disabled:\n"
                + "- Send disabled: IRCafe won't broadcast your typing state.\n"
                + "- Display disabled: IRCafe won't render incoming typing indicators.");
    typingHelp.setToolTipText("How typing indicators affect IRCafe");

    JPanel typingRow =
        new JPanel(
            new MigLayout(
                "insets 8, fillx, wrap 1, hidemode 3", "[grow,fill]6[]", "[]2[]2[]2[]2[]"));
    typingRow.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Typing indicators"),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
    typingRow.setOpaque(false);
    typingRow.add(typingIndicatorsSendEnabled, "growx, wmin 0, split 2");
    typingRow.add(typingHelp, "aligny center");
    typingRow.add(typingIndicatorsReceiveEnabled, "growx, wmin 0");

    JPanel treeStyleRow = new JPanel(new MigLayout("insets 0, fillx", "[]8[grow,fill]", "[]"));
    treeStyleRow.setOpaque(false);
    treeStyleRow.add(new JLabel("Server tree marker style"));
    treeStyleRow.add(typingTreeIndicatorStyle, "growx, wmin 180");
    typingRow.add(treeStyleRow, "growx, wmin 0");

    JPanel displaysRow =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2, hidemode 3", "[grow,fill]16[grow,fill]", "[]2[]"));
    displaysRow.setOpaque(false);
    displaysRow.add(typingIndicatorsTreeDisplayEnabled, "growx, wmin 0");
    displaysRow.add(typingIndicatorsUsersListDisplayEnabled, "growx, wmin 0");
    displaysRow.add(typingIndicatorsTranscriptDisplayEnabled, "growx, wmin 0");
    displaysRow.add(typingIndicatorsSendSignalDisplayEnabled, "growx, wmin 0");
    typingRow.add(displaysRow, "growx, wmin 0");

    JPanel matrixNamesRow = new JPanel(new MigLayout("insets 0, fillx", "[]8[grow,fill]", "[]"));
    matrixNamesRow.setOpaque(false);
    matrixNamesRow.add(new JLabel("Matrix user list names"));
    matrixNamesRow.add(matrixUserListNameDisplayMode, "growx, wmin 220");
    typingRow.add(matrixNamesRow, "growx, wmin 0");

    typingRow.add(serverTreeNotificationBadgesEnabled, "growx, wmin 0");

    JPanel badgeScaleRow = new JPanel(new MigLayout("insets 0, fillx", "[]8[]6[]", "[]"));
    badgeScaleRow.setOpaque(false);
    badgeScaleRow.add(new JLabel("Unread badge size"));
    badgeScaleRow.add(serverTreeUnreadBadgeScalePercent, "w 90!");
    badgeScaleRow.add(new JLabel("%"));
    typingRow.add(badgeScaleRow, "growx, wmin 0");

    JTextArea typingImpact = PreferencesDialog.subtleInfoText();
    typingImpact.setText(
        "Send controls your outbound typing state; Display controls incoming typing state from others.\n"
            + "Display toggles control where typing hints render: server tree, user list, transcript, and send telemetry arrows.\n"
            + "Matrix user list names controls whether Matrix users render as display name only or as display name + Matrix user ID.\n"
            + "Server tree marker style controls the channel typing activity indicator.\n"
            + "Show unread/highlight badges toggles server tree notification count badges.\n"
            + "Unread badge size scales channel unread/highlight count badges in the server tree.");
    typingImpact.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));

    JPanel typingTab =
        new JPanel(new MigLayout("insets 6, fillx, wrap 1, hidemode 3", "[grow,fill]", "[]6[]"));
    typingTab.setOpaque(false);
    typingTab.add(typingRow, "growx, wmin 0, wrap");
    typingTab.add(typingImpact, "growx, wmin 0, wrap");

    JPanel capabilityBlock =
        new JPanel(
            new MigLayout("insets 8, fill, wrap 1, hidemode 3", "[grow,fill]", "[]6[grow,fill]"));
    capabilityBlock.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Requested capabilities"),
            BorderFactory.createEmptyBorder(4, 6, 6, 6)));
    capabilityBlock.setOpaque(false);
    capabilityBlock.add(
        PreferencesDialog.subtleInfoTextWith(
            "These capabilities are requested during CAP negotiation.\n"
                + "Changes apply on new connections or reconnect."),
        "growx, wmin 0, wrap");

    JScrollPane capabilityScroll =
        new JScrollPane(
            ircv3Capabilities.panel(),
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    capabilityScroll.setBorder(BorderFactory.createEmptyBorder());
    capabilityScroll.setViewportBorder(null);
    capabilityScroll.getVerticalScrollBar().setUnitIncrement(16);
    capabilityScroll.setPreferredSize(new Dimension(1, 320));
    capabilityBlock.add(capabilityScroll, "grow, push, wmin 0, hmin 180");

    JPanel capabilitiesTab =
        new JPanel(
            new MigLayout("insets 6, fill, wrap 1, hidemode 3", "[grow,fill]", "[grow,fill]"));
    capabilitiesTab.setOpaque(false);
    capabilitiesTab.add(capabilityBlock, "grow, push, wmin 0");

    JButton typingHeader = new JButton();
    typingHeader.setHorizontalAlignment(SwingConstants.LEFT);
    typingHeader.setFocusable(false);
    typingHeader.setMargin(new Insets(6, 10, 6, 10));
    typingHeader.setToolTipText("Show typing-indicator settings");

    JButton capabilitiesHeader = new JButton();
    capabilitiesHeader.setHorizontalAlignment(SwingConstants.LEFT);
    capabilitiesHeader.setFocusable(false);
    capabilitiesHeader.setMargin(new Insets(6, 10, 6, 10));
    capabilitiesHeader.setToolTipText("Show requested IRCv3 capabilities");

    final boolean[] typingExpanded = new boolean[] {true};
    final boolean[] capabilitiesExpanded = new boolean[] {false};

    Runnable refreshAccordion =
        () -> {
          typingHeader.setText((typingExpanded[0] ? "▾ " : "▸ ") + "Typing indicators");
          capabilitiesHeader.setText(
              (capabilitiesExpanded[0] ? "▾ " : "▸ ") + "Requested capabilities");
          typingTab.setVisible(typingExpanded[0]);
          capabilitiesTab.setVisible(capabilitiesExpanded[0]);
          form.revalidate();
          form.repaint();
        };

    typingHeader.addActionListener(
        event -> {
          if (typingExpanded[0]) return;
          typingExpanded[0] = true;
          capabilitiesExpanded[0] = false;
          refreshAccordion.run();
        });

    capabilitiesHeader.addActionListener(
        event -> {
          if (capabilitiesExpanded[0]) return;
          capabilitiesExpanded[0] = true;
          typingExpanded[0] = false;
          refreshAccordion.run();
        });

    form.add(typingHeader, "growx, wmin 0, wrap");
    form.add(typingTab, "growx, wmin 0, wrap, hidemode 3");
    form.add(capabilitiesHeader, "growx, wmin 0, wrap");
    form.add(capabilitiesTab, "grow, push, wmin 0, hmin 180, hidemode 3");
    refreshAccordion.run();

    return form;
  }

  static void persistCapabilities(
      RuntimeConfigStore runtimeConfig, Map<String, Boolean> capabilities) {
    if (capabilities == null || capabilities.isEmpty()) return;
    for (Map.Entry<String, Boolean> entry : capabilities.entrySet()) {
      String key = normalizeCapabilityKey(entry.getKey());
      if (key.isEmpty()) continue;
      boolean enabled = Boolean.TRUE.equals(entry.getValue());
      runtimeConfig.rememberIrcv3CapabilityEnabled(key, enabled);
    }
  }

  private static String capabilityDisplayLabel(String capability) {
    return switch (capability) {
      case "message-tags" -> "Message tags";
      case "sts" -> "Strict transport security";
      case "server-time" -> "Server timestamps";
      case "echo-message" -> "Echo own messages";
      case "account-tag" -> "Account tags";
      case "userhost-in-names" -> "USERHOST in NAMES";
      case "multiline" -> "Multiline messages";
      case "draft/multiline" -> "Multiline messages (draft)";
      case "draft/read-marker" -> "Read markers (draft)";
      case "read-marker" -> "Read markers";
      case "draft/message-edit" -> "Message edits (draft)";
      case "message-edit" -> "Message edits (final)";
      case "draft/message-redaction" -> "Message redaction (draft)";
      case "message-redaction" -> "Message redaction (final)";
      case "chathistory" -> "Chat history (final)";
      case "draft/chathistory" -> "Chat history (draft)";
      case "znc.in/playback" -> "ZNC playback";
      case "labeled-response" -> "Labeled responses";
      case "standard-replies" -> "Standard replies";
      case "multi-prefix" -> "Multi-prefix names";
      case "cap-notify" -> "CAP updates";
      case "away-notify" -> "Away status updates";
      case "account-notify" -> "Account status updates";
      case "extended-join" -> "Extended join data";
      case "setname" -> "Setname updates";
      case "chghost" -> "Hostmask changes";
      case "batch" -> "Batch event grouping";
      default -> capability;
    };
  }

  private static String capabilityGroupKey(String capability) {
    return switch (capability) {
      case "multi-prefix",
          "cap-notify",
          "away-notify",
          "account-notify",
          "extended-join",
          "setname",
          "chghost",
          "message-tags",
          "sts",
          "server-time",
          "standard-replies",
          "echo-message",
          "labeled-response",
          "account-tag",
          "userhost-in-names" ->
          "core";
      case "draft/reply",
          "draft/channel-context",
          "draft/react",
          "draft/unreact",
          "draft/message-edit",
          "message-edit",
          "draft/message-redaction",
          "message-redaction",
          "draft/typing",
          "typing",
          "draft/read-marker",
          "read-marker",
          "multiline",
          "draft/multiline" ->
          "conversation";
      case "batch", "chathistory", "draft/chathistory", "znc.in/playback" -> "history";
      default -> "other";
    };
  }

  private static String capabilityGroupTitle(String groupKey) {
    return switch (groupKey) {
      case "core" -> "Core metadata and sync";
      case "conversation" -> "Conversation features";
      case "history" -> "History and playback";
      default -> "Other capabilities";
    };
  }

  private static int capabilitySortOrder(String capability) {
    return switch (capability) {
      case "message-tags" -> 10;
      case "sts" -> 20;
      case "server-time" -> 30;
      case "echo-message" -> 40;
      case "labeled-response" -> 50;
      case "standard-replies" -> 60;
      case "account-tag" -> 70;
      case "account-notify" -> 80;
      case "away-notify" -> 90;
      case "extended-join" -> 100;
      case "chghost" -> 110;
      case "setname" -> 120;
      case "multi-prefix" -> 130;
      case "cap-notify" -> 140;
      case "userhost-in-names" -> 150;
      case "multiline" -> 210;
      case "draft/multiline" -> 220;
      case "draft/typing" -> 225;
      case "typing" -> 230;
      case "draft/read-marker", "read-marker" -> 240;
      case "draft/channel-context" -> 245;
      case "draft/reply" -> 250;
      case "draft/react" -> 260;
      case "draft/unreact" -> 265;
      case "message-edit" -> 270;
      case "draft/message-edit" -> 280;
      case "message-redaction" -> 290;
      case "draft/message-redaction" -> 300;
      case "batch" -> 410;
      case "chathistory" -> 420;
      case "draft/chathistory" -> 430;
      case "znc.in/playback" -> 440;
      default -> 10_000;
    };
  }

  private static String capabilityImpactSummary(String capability) {
    return switch (capability) {
      case "message-tags" ->
          "Foundation for many IRCv3 features: carries structured metadata on messages.";
      case "sts" ->
          "Learns strict transport policy and upgrades future connects for this host to TLS.";
      case "server-time" ->
          "Uses server-provided timestamps to improve ordering and replay accuracy.";
      case "echo-message" ->
          "Server echoes your outbound messages, improving multi-client/bouncer consistency.";
      case "account-tag" -> "Attaches account metadata to messages for richer identity info.";
      case "userhost-in-names" ->
          "May provide richer host/user identity details during names lists.";
      case "multiline", "draft/multiline" ->
          "Allows sending and receiving multiline messages as a single logical message.";
      case "typing", "draft/typing" ->
          "Transport for typing indicators; required to send/receive typing events.";
      case "draft/read-marker", "read-marker" ->
          "Enables read-position markers on servers that support them.";
      case "draft/message-edit", "message-edit" ->
          "Allows edit updates for previously sent messages.";
      case "draft/message-redaction", "message-redaction" ->
          "Allows delete/redaction updates for messages.";
      case "chathistory", "draft/chathistory" ->
          "Enables server-side history retrieval and backfill features.";
      case "znc.in/playback" -> "Requests playback support from ZNC bouncers when available.";
      case "labeled-response" -> "Correlates command responses with requests more reliably.";
      case "standard-replies" -> "Provides structured success/error replies from the server.";
      case "multi-prefix" ->
          "Preserves all nick privilege prefixes (not just the highest) in user data.";
      case "cap-notify" -> "Allows capability change notifications after initial connection.";
      case "away-notify" -> "Tracks away/back state transitions for users.";
      case "account-notify" -> "Tracks account login/logout changes for users.";
      case "extended-join" -> "Adds account/realname metadata to join events when available.";
      case "setname" -> "Receives user real-name changes without extra lookups.";
      case "chghost" -> "Keeps hostmask/userhost identity changes in sync.";
      case "batch" -> "Groups related events into coherent batches (useful for playback/history).";
      default -> "Requests \"" + capability + "\" during CAP negotiation on connect/reconnect.";
    };
  }

  private static String capabilityHelpTitle(String capability) {
    return capabilityDisplayLabel(capability) + " (" + capability + ")";
  }

  private static String capabilityHelpMessage(String capability) {
    return "What it is:\n"
        + "Requests IRCv3 capability \""
        + capability
        + "\" during CAP negotiation.\n\n"
        + "Impact in IRCafe:\n"
        + capabilityImpactSummary(capability)
        + "\n\n"
        + "If disabled:\n"
        + "IRCafe will not request this capability on new connections; related features may be unavailable.";
  }

  private static String normalizeCapabilityKey(String capability) {
    if (capability == null) return "";
    String key = capability.trim().toLowerCase(Locale.ROOT);
    return switch (key) {
      case "draft/read-marker", "read-marker" -> "read-marker";
      case "draft/multiline", "multiline" -> "multiline";
      case "draft/chathistory", "chathistory" -> "chathistory";
      case "draft/message-redaction", "message-redaction" -> "message-redaction";
      default -> key;
    };
  }
}
