package cafe.woden.ircclient.ui.channellist;

import java.util.Locale;
import java.util.Objects;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;

final class IrcChannelListUxMode implements ChannelListUxMode {
  private static final String DEFAULT_HINT =
      "Use the refresh button to request /list (heavy) or the ALIS search button for filtered results.";
  private static final ActionPresentation PRESENTATION =
      new ActionPresentation(
          "Request full /list from server (heavy; confirmation required)",
          "Run /list",
          "Run filtered ALIS search (topic, min/max users, and display filters)",
          "Run ALIS search",
          false,
          "Run next Matrix /list page (uses next_batch from last response)",
          "Run next page");

  @Override
  public String defaultHint() {
    return DEFAULT_HINT;
  }

  @Override
  public ActionPresentation actionPresentation() {
    return PRESENTATION;
  }

  @Override
  public void runPrimaryAction(Context context, String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    if (!context.confirmFullListRequest()) return;
    context.rememberRequestType(sid, ChannelListRequestType.FULL_LIST);
    context.clearFilterText();
    context.emitRunListRequest();
  }

  @Override
  public void runSecondaryAction(Context context, String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    JTextField queryField = new JTextField(28);
    JCheckBox includeTopic = new JCheckBox("Include topic matching (-topic)", true);
    JCheckBox minEnabled = new JCheckBox("Minimum users (-min)");
    JSpinner minUsers = new JSpinner(new SpinnerNumberModel(10, 0, 1_000_000, 1));
    JCheckBox maxEnabled = new JCheckBox("Maximum users (-max)");
    JSpinner maxUsers = new JSpinner(new SpinnerNumberModel(500, 0, 1_000_000, 1));
    JCheckBox skipEnabled = new JCheckBox("Skip first results (-skip)");
    JSpinner skipCount = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));
    JCheckBox showModes = new JCheckBox("Show channel modes (-show m)", false);
    JCheckBox showTopicSetter = new JCheckBox("Show topic setter (-show t)", false);
    JComboBox<String> registrationScope =
        new JComboBox<>(
            new String[] {
              "Any channel registration",
              "Registered channels only (-show r)",
              "Unregistered channels only (-show u)"
            });
    JPanel showFlagsPanel = new JPanel(new MigLayout("insets 0, fillx", "[][grow]", "[]"));
    showFlagsPanel.add(showModes);
    showFlagsPanel.add(showTopicSetter, "gapleft 10");

    minUsers.setEnabled(false);
    maxUsers.setEnabled(false);
    skipCount.setEnabled(false);
    minEnabled.addActionListener(e -> minUsers.setEnabled(minEnabled.isSelected()));
    maxEnabled.addActionListener(e -> maxUsers.setEnabled(maxEnabled.isSelected()));
    skipEnabled.addActionListener(e -> skipCount.setEnabled(skipEnabled.isSelected()));

    JPanel form =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2", "[right][grow,fill]", "[]6[]6[]6[]6[]6[]6[]6[]"));
    form.add(new JLabel("Query pattern:"));
    form.add(queryField, "growx");
    form.add(new JLabel("Topic filter:"));
    form.add(includeTopic, "growx");
    form.add(minEnabled);
    form.add(minUsers, "w 120!");
    form.add(maxEnabled);
    form.add(maxUsers, "w 120!");
    form.add(skipEnabled);
    form.add(skipCount, "w 120!");
    form.add(new JLabel("Display extras:"));
    form.add(showFlagsPanel, "growx");
    form.add(new JLabel("Registration:"));
    form.add(registrationScope, "growx");

    int choice =
        JOptionPane.showConfirmDialog(
            context.ownerWindow(),
            form,
            "Run ALIS Search",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) return;

    String query = Objects.toString(queryField.getText(), "").trim();
    Integer minUsersValue =
        minEnabled.isSelected() ? ((Number) minUsers.getValue()).intValue() : null;
    Integer maxUsersValue =
        maxEnabled.isSelected() ? ((Number) maxUsers.getValue()).intValue() : null;
    Integer skipValue =
        skipEnabled.isSelected() ? ((Number) skipCount.getValue()).intValue() : null;
    if (minUsersValue != null && maxUsersValue != null && minUsersValue > maxUsersValue) {
      int t = minUsersValue;
      minUsersValue = maxUsersValue;
      maxUsersValue = t;
    }
    ChannelListPanel.AlisRegistrationFilter registrationFilter =
        switch (registrationScope.getSelectedIndex()) {
          case 1 -> ChannelListPanel.AlisRegistrationFilter.REGISTERED_ONLY;
          case 2 -> ChannelListPanel.AlisRegistrationFilter.UNREGISTERED_ONLY;
          default -> ChannelListPanel.AlisRegistrationFilter.ANY;
        };
    ChannelListPanel.AlisSearchOptions options =
        new ChannelListPanel.AlisSearchOptions(
            includeTopic.isSelected(),
            minUsersValue,
            maxUsersValue,
            skipValue,
            showModes.isSelected(),
            showTopicSetter.isSelected(),
            registrationFilter);
    String cmd = buildAlisCommand(query, options);

    context.rememberRequestType(sid, ChannelListRequestType.ALIS);
    context.beginList(sid, "Loading ALIS search results...");
    context.emitRunCommand(cmd);
  }

  @Override
  public void runPagingAction(Context context, String serverId) {}

  @Override
  public void onBeginList(String serverId, String banner) {}

  @Override
  public void onEndList(String serverId, String summary) {}

  @Override
  public boolean isPagingActionEnabled(String serverId) {
    return false;
  }

  @Override
  public ChannelListRequestType inferRequestTypeFromBanner(String banner) {
    String text = Objects.toString(banner, "").trim().toLowerCase(Locale.ROOT);
    if (text.contains("alis")) return ChannelListRequestType.ALIS;
    return ChannelListRequestType.UNKNOWN;
  }

  static String buildAlisCommand(String query, ChannelListPanel.AlisSearchOptions options) {
    ChannelListPanel.AlisSearchOptions opts =
        options == null ? ChannelListPanel.AlisSearchOptions.defaults(false) : options;
    String q = Objects.toString(query, "").trim();
    StringBuilder raw = new StringBuilder("LIST ");
    raw.append(opts.includeTopic() ? "*" : (q.isEmpty() ? "*" : q));
    if (opts.includeTopic()) {
      raw.append(" -topic");
      raw.append(" ").append(q.isEmpty() ? "*" : q);
    }
    if (opts.minUsers() != null && opts.minUsers() >= 0) {
      raw.append(" -min ").append(opts.minUsers());
    }
    if (opts.maxUsers() != null && opts.maxUsers() >= 0) {
      raw.append(" -max ").append(opts.maxUsers());
    }
    if (opts.skipCount() != null && opts.skipCount() > 0) {
      raw.append(" -skip ").append(opts.skipCount());
    }

    StringBuilder showFlags = new StringBuilder();
    if (opts.showModes()) showFlags.append("m");
    if (opts.showTopicSetter()) showFlags.append("t");
    if (!showFlags.isEmpty()) {
      raw.append(" -show ").append(showFlags);
    }

    ChannelListPanel.AlisRegistrationFilter registration =
        opts.registrationFilter() == null
            ? ChannelListPanel.AlisRegistrationFilter.ANY
            : opts.registrationFilter();
    if (registration == ChannelListPanel.AlisRegistrationFilter.REGISTERED_ONLY) {
      raw.append(" -show r");
    } else if (registration == ChannelListPanel.AlisRegistrationFilter.UNREGISTERED_ONLY) {
      raw.append(" -show u");
    }

    return "/quote PRIVMSG ALIS :" + raw.toString().trim();
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
