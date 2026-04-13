package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

final class LoggingControlsSupport {
  private LoggingControlsSupport() {}

  static LoggingControls buildControls(
      LogProperties logProps,
      java.util.List<AutoCloseable> closeables,
      ServerDialogs serverDialogs,
      Window owner) {
    boolean loggingEnabledCurrent = logProps != null && Boolean.TRUE.equals(logProps.enabled());
    boolean logSoftIgnoredCurrent =
        logProps == null || Boolean.TRUE.equals(logProps.logSoftIgnoredLines());
    boolean redactionAuditEnabledCurrent =
        logProps != null && Boolean.TRUE.equals(logProps.redactionAuditEnabled());
    boolean logPrivateMessagesCurrent =
        logProps == null || Boolean.TRUE.equals(logProps.logPrivateMessages());
    boolean savePrivateMessageListCurrent =
        logProps == null || Boolean.TRUE.equals(logProps.savePrivateMessageList());

    JCheckBox loggingEnabled = new JCheckBox("Enable chat logging (store messages to local DB)");
    loggingEnabled.setSelected(loggingEnabledCurrent);
    loggingEnabled.setToolTipText(
        "When enabled, IRCafe will persist chat messages to an embedded local database for history loading.\n"
            + "Privacy-first: this is OFF by default.\n\n"
            + "Note: enabling/disabling requires restarting IRCafe to take effect.");

    JCheckBox loggingSoftIgnore = new JCheckBox("Log soft-ignored (spoiler) lines");
    loggingSoftIgnore.setSelected(logSoftIgnoredCurrent);
    loggingSoftIgnore.setToolTipText(
        "If enabled, messages that are soft-ignored (spoiler-covered) are still stored,\n"
            + "and will re-load as spoiler-covered lines in history.");
    loggingSoftIgnore.setEnabled(loggingEnabled.isSelected());

    JCheckBox redactionAuditEnabled =
        new JCheckBox("Store original text for redacted messages in audit log");
    redactionAuditEnabled.setSelected(redactionAuditEnabledCurrent);
    redactionAuditEnabled.setToolTipText(
        "If enabled, IRCafe stores the original pre-redaction text in a separate audit table,\n"
            + "while the normal chat log keeps only the [message redacted] placeholder.\n"
            + "Use this only if you explicitly want local audit retention of redacted content.");
    redactionAuditEnabled.setEnabled(loggingEnabled.isSelected());

    JCheckBox loggingPrivateMessages = new JCheckBox("Save private-message history");
    loggingPrivateMessages.setSelected(logPrivateMessagesCurrent);
    loggingPrivateMessages.setToolTipText(
        "If enabled, PM/query messages are stored in the local history database.\n"
            + "If disabled, only non-PM targets are persisted.");
    loggingPrivateMessages.setEnabled(loggingEnabled.isSelected());

    JCheckBox savePrivateMessageList = new JCheckBox("Save private-message chat list");
    savePrivateMessageList.setSelected(savePrivateMessageListCurrent);
    savePrivateMessageList.setToolTipText(
        "If enabled, PM/query targets are remembered and re-opened after reconnect/restart.\n"
            + "The per-server PM list is managed in Servers -> Edit -> Auto-Join.");

    boolean keepForeverCurrent = logProps == null || Boolean.TRUE.equals(logProps.keepForever());
    int retentionDaysCurrent =
        (logProps != null && logProps.retentionDays() != null)
            ? Math.max(0, logProps.retentionDays())
            : 0;

    JCheckBox keepForever = new JCheckBox("Keep chat history forever (no retention pruning)");
    keepForever.setSelected(keepForeverCurrent);
    keepForever.setToolTipText(
        "If enabled, IRCafe will never automatically delete old chat history.\n"
            + "If disabled, you can set a retention window in days to prune older rows.\n\n"
            + "Note: retention pruning runs only when logging is enabled and takes effect after restart.");

    javax.swing.JSpinner retentionDays =
        PreferencesDialog.numberSpinner(retentionDaysCurrent, 0, 10_000, 1, closeables);
    retentionDays.setToolTipText(
        "Retention window in days (0 disables retention).\n"
            + "Only used when Keep forever is unchecked.\n\n"
            + "Note: applied on next restart.");

    int writerQueueMaxCurrent =
        (logProps != null && logProps.writerQueueMax() != null)
            ? Math.max(100, Math.min(1_000_000, logProps.writerQueueMax()))
            : 50_000;
    javax.swing.JSpinner writerQueueMax =
        PreferencesDialog.numberSpinner(writerQueueMaxCurrent, 100, 1_000_000, 500, closeables);
    writerQueueMax.setToolTipText(
        "Maximum buffered log lines before new lines are dropped.\n"
            + "Higher values reduce drop risk during bursts but use more memory.\n\n"
            + "Note: applied on next restart.");

    int writerBatchSizeCurrent =
        (logProps != null && logProps.writerBatchSize() != null)
            ? Math.max(1, Math.min(10_000, logProps.writerBatchSize()))
            : 250;
    javax.swing.JSpinner writerBatchSize =
        PreferencesDialog.numberSpinner(writerBatchSizeCurrent, 1, 10_000, 25, closeables);
    writerBatchSize.setToolTipText(
        "How many queued log lines are written per DB transaction.\n"
            + "Larger batches improve write throughput, smaller batches reduce commit latency.\n\n"
            + "Note: applied on next restart.");

    String dbBaseNameCurrent =
        (logProps != null && logProps.hsqldb() != null)
            ? logProps.hsqldb().fileBaseName()
            : "ircafe-chatlog";
    boolean dbNextToConfigCurrent =
        logProps == null
            || (logProps.hsqldb() != null
                && Boolean.TRUE.equals(logProps.hsqldb().nextToRuntimeConfig()));

    JTextField dbBaseName = new JTextField(dbBaseNameCurrent, 18);
    dbBaseName.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "ircafe-chatlog");
    dbBaseName.setToolTipText(
        "Base filename for HSQLDB (no extension).\n"
            + "HSQLDB will create multiple files like .data/.script/.properties.");

    JCheckBox dbNextToConfig = new JCheckBox("Store DB next to runtime config file");
    dbNextToConfig.setSelected(dbNextToConfigCurrent);
    dbNextToConfig.setToolTipText(
        "If enabled, the DB files are stored alongside your runtime YAML config (recommended).\n"
            + "If disabled, IRCafe uses the default runtime-config directory\n"
            + "(XDG_CONFIG_HOME/ircafe when set, otherwise ~/.config/ircafe).");

    JTextArea loggingInfo =
        new JTextArea(
            "Logging settings are applied on the next restart.\n"
                + "Tip: You can enable logging first, restart, then history controls (Load older messages…) will appear when data exists.");
    loggingInfo.setEditable(false);
    loggingInfo.setLineWrap(true);
    loggingInfo.setWrapStyleWord(true);
    loggingInfo.setOpaque(false);
    loggingInfo.setFocusable(false);
    loggingInfo.setBorder(null);
    loggingInfo.setFont(UIManager.getFont("Label.font"));
    loggingInfo.setForeground(UIManager.getColor("Label.foreground"));
    loggingInfo.setColumns(48);

    Runnable updateRetentionUi = () -> retentionDays.setEnabled(!keepForever.isSelected());
    keepForever.addActionListener(e -> updateRetentionUi.run());

    Runnable updateLoggingEnabledState =
        () -> {
          boolean enabled = loggingEnabled.isSelected();
          loggingSoftIgnore.setEnabled(enabled);
          redactionAuditEnabled.setEnabled(enabled);
          loggingPrivateMessages.setEnabled(enabled);
          writerQueueMax.setEnabled(enabled);
          writerBatchSize.setEnabled(enabled);
          dbBaseName.setEnabled(true);
          dbNextToConfig.setEnabled(true);
          updateRetentionUi.run();
        };
    loggingEnabled.addActionListener(e -> updateLoggingEnabledState.run());
    updateLoggingEnabledState.run();

    JButton managePmList = new JButton("Open Server Auto-Join Settings…");
    managePmList.setIcon(SvgIcons.action("settings", 16));
    managePmList.setDisabledIcon(SvgIcons.actionDisabled("settings", 16));
    managePmList.setEnabled(serverDialogs != null);
    managePmList.addActionListener(
        e -> {
          if (serverDialogs == null) return;
          Window effectiveOwner =
              owner != null ? owner : SwingUtilities.getWindowAncestor(managePmList);
          serverDialogs.openManageServers(effectiveOwner);
        });

    return new LoggingControls(
        loggingEnabled,
        loggingSoftIgnore,
        redactionAuditEnabled,
        loggingPrivateMessages,
        savePrivateMessageList,
        managePmList,
        keepForever,
        retentionDays,
        writerQueueMax,
        writerBatchSize,
        dbBaseName,
        dbNextToConfig,
        loggingInfo);
  }
}
