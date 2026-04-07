package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionRegistry;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    LinkedHashMap<Ircv3ExtensionRegistry.UiGroup, List<Ircv3ExtensionRegistry.ExtensionDefinition>>
        grouped = new LinkedHashMap<>();
    for (Ircv3ExtensionRegistry.ExtensionDefinition definition :
        Ircv3ExtensionRegistry.requestableCapabilities()) {
      grouped
          .computeIfAbsent(definition.uiMetadata().group(), __ -> new ArrayList<>())
          .add(definition);
    }

    for (Map.Entry<Ircv3ExtensionRegistry.UiGroup, List<Ircv3ExtensionRegistry.ExtensionDefinition>>
        group : grouped.entrySet()) {
      List<Ircv3ExtensionRegistry.ExtensionDefinition> caps = group.getValue();
      if (caps == null || caps.isEmpty()) continue;

      List<Ircv3ExtensionRegistry.ExtensionDefinition> orderedCaps = new ArrayList<>(caps);
      orderedCaps.sort(
          (left, right) -> {
            int leftOrder = left.uiMetadata().sortOrder();
            int rightOrder = right.uiMetadata().sortOrder();
            if (leftOrder != rightOrder) return Integer.compare(leftOrder, rightOrder);
            return left.uiMetadata().label().compareToIgnoreCase(right.uiMetadata().label());
          });

      JPanel groupPanel =
          new JPanel(
              new MigLayout(
                  "insets 6 8 8 8, fillx, wrap 2, hidemode 3",
                  "[grow,fill]12[grow,fill]",
                  "[]2[]"));
      groupPanel.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createTitledBorder(group.getKey().title()),
              BorderFactory.createEmptyBorder(4, 6, 4, 6)));
      groupPanel.setOpaque(false);

      for (Ircv3ExtensionRegistry.ExtensionDefinition definition : orderedCaps) {
        String preferenceKey = definition.preferenceKey();
        JCheckBox checkbox = new JCheckBox(definition.uiMetadata().label());
        checkbox.setSelected(persisted.getOrDefault(preferenceKey, Boolean.TRUE));
        checkbox.setToolTipText(definition.uiMetadata().impactSummary());
        checkboxes.put(preferenceKey, checkbox);

        JButton help =
            PreferencesDialog.whyHelpButton(helpTitle(definition), helpMessage(definition));
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
      String key = Ircv3ExtensionRegistry.preferenceKeyFor(entry.getKey());
      if (key.isEmpty()) continue;
      boolean enabled = Boolean.TRUE.equals(entry.getValue());
      runtimeConfig.rememberIrcv3CapabilityEnabled(key, enabled);
    }
  }

  private static String helpTitle(Ircv3ExtensionRegistry.ExtensionDefinition definition) {
    return definition.uiMetadata().label() + " (" + definition.requestToken() + ")";
  }

  private static String helpMessage(Ircv3ExtensionRegistry.ExtensionDefinition definition) {
    return "What it is:\n"
        + "Requests IRCv3 capability \""
        + definition.requestToken()
        + "\" during CAP negotiation.\n\n"
        + "Impact in IRCafe:\n"
        + definition.uiMetadata().impactSummary()
        + "\n\n"
        + "If disabled:\n"
        + "IRCafe will not request this capability on new connections; related features may be unavailable.";
  }
}
