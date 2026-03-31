package cafe.woden.ircclient.ui.settings;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextArea;

record UserCommandAliasesControls(
    JTable table,
    UserCommandAliasesTableModel model,
    JTextArea template,
    JCheckBox unknownCommandAsRaw,
    JButton add,
    JButton importHexChat,
    JButton duplicate,
    JButton remove,
    JButton up,
    JButton down,
    JLabel hint) {}
