package cafe.woden.ircclient.ui.settings.network;

import javax.swing.JCheckBox;
import javax.swing.JSpinner;

public record FloodProtectionControls(
    JCheckBox enabled, JSpinner commandIntervalMs, JSpinner autoJoinDelaySeconds) {}
