package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.miginfocom.swing.MigLayout;

final class StartupPanelSupport {
  private StartupPanelSupport() {}

  static JPanel buildPanel(JCheckBox autoConnectOnStart, LaunchJvmControls launchJvm) {
    JPanel form =
        new JPanel(new MigLayout("insets 12, fillx, wrap 1", "[grow,fill]", "[]10[]10[]"));
    form.add(PreferencesDialog.tabTitle("Startup"), "growx, wrap");

    form.add(PreferencesDialog.sectionTitle("On launch"), "growx, wrap");
    form.add(autoConnectOnStart, "growx, wrap");
    form.add(
        PreferencesDialog.helpText(
            "If enabled, IRCafe will connect to all configured servers automatically after the UI loads."),
        "growx, wrap");

    JScrollPane extraArgsScroll = new JScrollPane(launchJvm.extraArgs());
    extraArgsScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    extraArgsScroll.setHorizontalScrollBarPolicy(
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    JPanel jvm =
        PreferencesDialog.captionPanel(
            "JVM on next launch",
            "insets 0, fillx, wrap 2",
            "[right]10[grow,fill]",
            "[]4[]4[]4[]4[]");
    jvm.add(new JLabel("Java command"));
    jvm.add(launchJvm.javaCommand(), "growx, wmin 0, wrap");
    jvm.add(new JLabel("Initial heap (MiB)"));
    jvm.add(launchJvm.xmsMiB(), "w 140!, wrap");
    jvm.add(new JLabel("Max heap (MiB)"));
    jvm.add(launchJvm.xmxMiB(), "w 140!, wrap");
    jvm.add(new JLabel("GC"));
    jvm.add(launchJvm.gc(), "growx, wmin 0, wrap");
    jvm.add(new JLabel("Extra JVM args"), "aligny top");
    jvm.add(extraArgsScroll, "growx, h 100!, wmin 0, wrap");
    jvm.add(
        PreferencesDialog.helpText(
            "These settings are stored in runtime config and applied on a future restart by launcher scripts.\n"
                + "Use 0 for heap values to leave them unset."),
        "span 2, growx, wmin 0, wrap");
    form.add(jvm, "growx, wmin 0, wrap");

    return form;
  }
}
