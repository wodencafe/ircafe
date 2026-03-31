package cafe.woden.ircclient.ui.settings;

import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

final class CtcpAutoReplySupport {
  private CtcpAutoReplySupport() {}

  static CtcpAutoReplyControls buildControls(
      boolean enabledByDefault,
      boolean versionByDefault,
      boolean pingByDefault,
      boolean timeByDefault) {
    JCheckBox enabled = new JCheckBox("Enable automatic CTCP replies");
    enabled.setSelected(enabledByDefault);
    enabled.setToolTipText(
        "When enabled, IRCafe can auto-reply to private CTCP requests (VERSION, PING, TIME).");

    JCheckBox version = new JCheckBox("Reply to CTCP VERSION");
    version.setSelected(versionByDefault);
    version.setToolTipText("Respond with your client version.");

    JCheckBox ping = new JCheckBox("Reply to CTCP PING");
    ping.setSelected(pingByDefault);
    ping.setToolTipText("Echo back the request payload so the sender can measure latency.");

    JCheckBox time = new JCheckBox("Reply to CTCP TIME");
    time.setSelected(timeByDefault);
    time.setToolTipText("Respond with your current local timestamp.");

    Runnable syncEnabled =
        () -> {
          boolean on = enabled.isSelected();
          version.setEnabled(on);
          ping.setEnabled(on);
          time.setEnabled(on);
        };
    enabled.addActionListener(e -> syncEnabled.run());
    syncEnabled.run();

    return new CtcpAutoReplyControls(enabled, version, ping, time);
  }

  static JPanel buildPanel(CtcpAutoReplyControls controls) {
    JPanel form = new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]8[]8[]"));

    form.add(PreferencesDialog.tabTitle("CTCP Replies"), "growx, wmin 0, wrap");
    form.add(
        PreferencesDialog.subtleInfoTextWith(
            "Control automatic replies to inbound private CTCP requests. "
                + "Outbound /ctcp commands are not affected."),
        "growx, wmin 0, wrap");
    form.add(controls.enabled, "growx, wrap");

    JPanel perCommand =
        new JPanel(new MigLayout("insets 8, fillx, wrap 1, hidemode 3", "[grow,fill]", "[]2[]2[]"));
    perCommand.setOpaque(false);
    perCommand.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Per-command replies"),
            BorderFactory.createEmptyBorder(4, 8, 6, 8)));
    perCommand.add(controls.version, "growx, wmin 0, gapleft 8, wrap");
    perCommand.add(controls.ping, "growx, wmin 0, gapleft 8, wrap");
    perCommand.add(controls.time, "growx, wmin 0, gapleft 8, wrap");
    form.add(perCommand, "growx, wmin 0, wrap");

    JButton enableDefaults = new JButton("Enable defaults");
    enableDefaults.setToolTipText("Enable automatic replies and turn on VERSION, PING, and TIME.");
    enableDefaults.addActionListener(
        e -> {
          controls.enabled.setSelected(true);
          controls.version.setSelected(true);
          controls.ping.setSelected(true);
          controls.time.setSelected(true);
        });

    JButton disableAll = new JButton("Disable all");
    disableAll.setToolTipText("Disable all automatic CTCP replies.");
    disableAll.addActionListener(
        e -> {
          controls.enabled.setSelected(false);
          controls.version.setSelected(false);
          controls.ping.setSelected(false);
          controls.time.setSelected(false);
        });

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    actions.setOpaque(false);
    actions.add(enableDefaults);
    actions.add(disableAll);
    form.add(actions, "growx, wmin 0, wrap");

    form.add(
        PreferencesDialog.helpText(
            "If the top toggle is off, IRCafe will not send any automatic CTCP replies."),
        "growx, wmin 0, wrap");
    return form;
  }
}
