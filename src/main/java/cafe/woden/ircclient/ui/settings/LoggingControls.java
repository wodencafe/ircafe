package cafe.woden.ircclient.ui.settings;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;

final class LoggingControls {
  final JCheckBox enabled;
  final JCheckBox logSoftIgnored;
  final JCheckBox logPrivateMessages;
  final JCheckBox savePrivateMessageList;
  final JButton managePrivateMessageList;
  final JCheckBox keepForever;
  final JSpinner retentionDays;
  final JSpinner writerQueueMax;
  final JSpinner writerBatchSize;
  final JTextField dbBaseName;
  final JCheckBox dbNextToConfig;
  final JTextArea info;

  LoggingControls(
      JCheckBox enabled,
      JCheckBox logSoftIgnored,
      JCheckBox logPrivateMessages,
      JCheckBox savePrivateMessageList,
      JButton managePrivateMessageList,
      JCheckBox keepForever,
      JSpinner retentionDays,
      JSpinner writerQueueMax,
      JSpinner writerBatchSize,
      JTextField dbBaseName,
      JCheckBox dbNextToConfig,
      JTextArea info) {
    this.enabled = enabled;
    this.logSoftIgnored = logSoftIgnored;
    this.logPrivateMessages = logPrivateMessages;
    this.savePrivateMessageList = savePrivateMessageList;
    this.managePrivateMessageList = managePrivateMessageList;
    this.keepForever = keepForever;
    this.retentionDays = retentionDays;
    this.writerQueueMax = writerQueueMax;
    this.writerBatchSize = writerBatchSize;
    this.dbBaseName = dbBaseName;
    this.dbNextToConfig = dbNextToConfig;
    this.info = info;
  }
}
