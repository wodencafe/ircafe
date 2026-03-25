package cafe.woden.ircclient.ui.settings;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.miginfocom.swing.MigLayout;

final class DiagnosticsPanelSupport {
  private DiagnosticsPanelSupport() {}

  static JPanel buildPanel(DiagnosticsControls controls) {
    JPanel panel = new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]8[]8[]"));

    panel.add(PreferencesDialog.tabTitle("Diagnostics"), "growx, wmin 0, wrap");
    panel.add(
        PreferencesDialog.helpText(
            "Configure optional application diagnostics integrations exposed under the Application tree node.\n"
                + "Startup-related changes apply after restarting IRCafe."),
        "growx, wmin 0, wrap");

    JPanel assertjPanel =
        PreferencesDialog.captionPanel(
            "AssertJ Swing / EDT watchdog",
            "insets 0, fillx, wrap 2",
            "[right]10[grow,fill]",
            "[]4[]4[]4[]4[]4[]");
    assertjPanel.add(controls.assertjSwingEnabled(), "span 2, growx, wmin 0, wrap");
    assertjPanel.add(
        controls.assertjSwingFreezeWatchdogEnabled(), "span 2, growx, wmin 0, gapleft 14, wrap");
    assertjPanel.add(new JLabel("Freeze threshold (ms)"), "gapleft 24");
    assertjPanel.add(controls.assertjSwingFreezeThresholdMs(), "w 140!");
    assertjPanel.add(new JLabel("Watchdog poll (ms)"), "gapleft 24");
    assertjPanel.add(controls.assertjSwingWatchdogPollMs(), "w 140!");
    assertjPanel.add(new JLabel("Fallback violation report interval (ms)"), "gapleft 24");
    assertjPanel.add(controls.assertjSwingFallbackViolationReportMs(), "w 140!");
    assertjPanel.add(
        controls.assertjSwingOnIssuePlaySound(), "span 2, growx, wmin 0, gapleft 24, wrap");
    assertjPanel.add(
        controls.assertjSwingOnIssueShowNotification(), "span 2, growx, wmin 0, gapleft 24, wrap");
    assertjPanel.add(
        PreferencesDialog.helpText(
            "Watchdog logs stalls when EDT lag exceeds the threshold. Fallback interval controls how often "
                + "off-EDT Swing violations are re-reported."),
        "span 2, gapleft 24, growx, wrap");
    panel.add(assertjPanel, "growx, wmin 0, wrap");

    JScrollPane argsScroll = new JScrollPane(controls.jhiccupArgs());
    argsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    argsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    JPanel jhiccupPanel =
        PreferencesDialog.captionPanel(
            "jHiccup integration",
            "insets 0, fillx, wrap 2",
            "[right]10[grow,fill]",
            "[]4[]4[]4[]");
    jhiccupPanel.add(controls.jhiccupEnabled(), "span 2, growx, wmin 0, wrap");
    jhiccupPanel.add(new JLabel("jHiccup jar"), "aligny top");
    jhiccupPanel.add(controls.jhiccupJarPath(), "growx, wmin 0, wrap");
    jhiccupPanel.add(new JLabel("Java command"), "aligny top");
    jhiccupPanel.add(controls.jhiccupJavaCommand(), "growx, wmin 0, wrap");
    jhiccupPanel.add(new JLabel("Arguments"), "aligny top");
    jhiccupPanel.add(argsScroll, "growx, wmin 0, h 110!, wrap");
    jhiccupPanel.add(
        PreferencesDialog.helpText(
            "One argument per line. Example flags: -i 1000, -l 2000000.\n"
                + "Relative jar paths are resolved from the runtime-config directory."),
        "span 2, growx, wmin 0, wrap");
    panel.add(jhiccupPanel, "growx, wmin 0, wrap");

    return panel;
  }
}
