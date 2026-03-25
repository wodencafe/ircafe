package cafe.woden.ircclient.ui.settings;

import java.awt.FlowLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.miginfocom.swing.MigLayout;

final class UserCommandsPanelSupport {
  private UserCommandsPanelSupport() {}

  static JPanel buildPanel(UserCommandAliasesControls controls) {
    JPanel panel =
        new JPanel(
            new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]8[]6[]8[grow,fill]8[]"));

    panel.add(PreferencesDialog.tabTitle("Commands"), "growx, wmin 0, wrap");
    panel.add(PreferencesDialog.sectionTitle("User command aliases"), "growx, wmin 0, wrap");
    panel.add(
        PreferencesDialog.helpText(
            "Define custom /commands that expand before built-in parsing.\n"
                + "Placeholders: %1..%9 (positional), %1- (rest from arg), %* (all args), &1..&9 (from end), %c (channel), %t (target), %s/%e (server), %n (nick).\n"
                + "HexChat import maps %t (time), %m and %v into IRCafe-compatible placeholders.\n"
                + "Multi-command expansion: separate commands with ';' or new lines."),
        "growx, wmin 0, wrap");

    JPanel behavior =
        PreferencesDialog.captionPanel("Behavior", "insets 0, fillx, wrap 1", "[grow,fill]", "");
    behavior.add(controls.unknownCommandAsRaw(), "growx, wmin 0, wrap");
    panel.add(behavior, "growx, wmin 0, wrap");

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    buttons.add(controls.add());
    buttons.add(controls.importHexChat());
    buttons.add(controls.duplicate());
    buttons.add(controls.remove());
    buttons.add(controls.up());
    buttons.add(controls.down());

    JScrollPane tableScroll = new JScrollPane(controls.table());
    tableScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    tableScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    JScrollPane templateScroll = new JScrollPane(controls.template());
    templateScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    templateScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    JPanel aliasList =
        PreferencesDialog.captionPanel(
            "Alias list", "insets 0, fill, wrap 1", "[grow,fill]", "[]6[grow,fill]");
    aliasList.add(buttons, "growx, wmin 0, wrap");
    aliasList.add(tableScroll, "grow, push, h 220!, wmin 0");
    panel.add(aliasList, "grow, push, wmin 0, wrap");

    JPanel editor =
        PreferencesDialog.captionPanel(
            "Expansion editor", "insets 0, fillx, wrap 1", "[grow,fill]", "[]6[]");
    editor.add(controls.hint(), "growx, wmin 0, wrap");
    editor.add(templateScroll, "growx, h 140!, wmin 0, wrap");
    panel.add(editor, "growx, wmin 0, wrap");

    return panel;
  }
}
