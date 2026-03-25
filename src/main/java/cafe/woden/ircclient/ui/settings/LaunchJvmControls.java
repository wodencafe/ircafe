package cafe.woden.ircclient.ui.settings;

import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;

record LaunchJvmControls(
    JTextField javaCommand,
    JSpinner xmsMiB,
    JSpinner xmxMiB,
    JComboBox<LaunchGcOption> gc,
    JTextArea extraArgs) {}
