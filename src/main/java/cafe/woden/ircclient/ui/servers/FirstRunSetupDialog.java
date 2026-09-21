package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.FirstRunSetupConfigPort;
import cafe.woden.ircclient.ui.localization.UiMessages;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional first-launch setup. This dialog owns and cancels its one persistence worker. */
public final class FirstRunSetupDialog extends JDialog {
  private static final Logger log = LoggerFactory.getLogger(FirstRunSetupDialog.class);
  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();

  private final FirstRunSetupConfigPort config;
  private final IrcProperties.Server defaults;
  private final JTextField id = named(new JTextField(), "setup.id");
  private final JTextField host = named(new JTextField(), "setup.host");
  private final JTextField port = named(new JTextField(), "setup.port");
  private final JTextField nick = named(new JTextField(), "setup.nick");
  private final JCheckBox tls = named(new JCheckBox(MESSAGES.text("setup.tls"), true), "setup.tls");
  private final JTextField account = named(new JTextField(), "setup.account");
  private final JPasswordField password = named(new JPasswordField(), "setup.password");
  private final JTextArea channels = named(new JTextArea(5, 30), "setup.channels");
  private final JButton back = named(new JButton(MESSAGES.text("setup.back")), "setup.back");
  private final JButton next = named(new JButton(), "setup.next");
  private final JButton skip =
      named(new JButton(MESSAGES.text("setup.skipStep")), "setup.skipStep");
  private final JButton skipAll =
      named(new JButton(MESSAGES.text("setup.skipAll")), "setup.skipAll");
  private final JLabel heading = new JLabel();
  private final JLabel status = named(new JLabel(" "), "setup.status");
  private final CardLayout layout = new CardLayout();
  private final JPanel pages = new JPanel(layout);
  private int page;
  private SwingWorker<Void, Void> worker;

  public FirstRunSetupDialog(
      Window owner, FirstRunSetupConfigPort config, IrcProperties.Server defaults) {
    super(owner, MESSAGES.text("setup.title"), ModalityType.APPLICATION_MODAL);
    this.config = config;
    this.defaults = defaults;
    setName("firstRunSetup");
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    resetServer();

    JPanel server = form();
    row(server, "setup.connectionName", id);
    row(server, "setup.host", host);
    row(server, "setup.port", port);
    row(server, "setup.nick", nick);
    server.add(new JLabel());
    server.add(tls);
    pages.add(page(server, "setup.serverHelp"), Integer.toString(0));

    JPanel auth = form();
    row(auth, "setup.account", account);
    row(auth, "setup.password", password);
    pages.add(page(auth, "setup.accountHelp"), Integer.toString(1));
    pages.add(page(new JScrollPane(channels), "setup.channelsHelp"), Integer.toString(2));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    for (JButton button : new JButton[] {skipAll, back, skip, next}) buttons.add(button);
    JPanel footer = new JPanel(new BorderLayout(0, 8));
    footer.add(status, BorderLayout.NORTH);
    footer.add(buttons, BorderLayout.SOUTH);
    JPanel content = new JPanel(new BorderLayout(0, 16));
    content.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
    content.add(heading, BorderLayout.NORTH);
    content.add(pages, BorderLayout.CENTER);
    content.add(footer, BorderLayout.SOUTH);
    setContentPane(content);

    back.addActionListener(
        e -> {
          page--;
          updatePage();
        });
    next.addActionListener(e -> advance(false));
    skip.addActionListener(e -> advance(true));
    skipAll.addActionListener(e -> finish(null));
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            finish(null);
          }
        });
    getRootPane()
        .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke("ESCAPE"), "skipSetup");
    getRootPane()
        .getActionMap()
        .put(
            "skipSetup",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                finish(null);
              }
            });
    tls.addActionListener(
        e -> {
          if (tls.isSelected() && port.getText().equals("6667")) port.setText("6697");
          else if (!tls.isSelected() && port.getText().equals("6697")) port.setText("6667");
        });
    updatePage();
    setMinimumSize(new Dimension(640, 410));
    pack();
    setLocationRelativeTo(owner);
  }

  private void resetServer() {
    id.setText(defaults == null ? "libera" : defaults.id());
    host.setText(defaults == null ? "irc.libera.chat" : defaults.host());
    port.setText(defaults == null ? "6697" : Integer.toString(defaults.port()));
    nick.setText(defaults == null ? "IRCafeUser" : defaults.nick());
    tls.setSelected(defaults == null || defaults.tls());
  }

  private void advance(boolean skipped) {
    if (worker != null) return;
    if (skipped) {
      switch (page) {
        case 0 -> resetServer();
        case 1 -> {
          account.setText("");
          password.setText("");
        }
        case 2 -> channels.setText("");
        default -> throw new IllegalStateException("Unknown setup page");
      }
    }
    try {
      if (page == 0) {
        FirstRunSetupModel.build(
            id.getText(),
            host.getText(),
            port.getText(),
            tls.isSelected(),
            nick.getText(),
            "",
            "",
            "");
      } else if (page == 1) {
        FirstRunSetupModel.account(tls.isSelected(), account.getText(), passwordText());
      } else {
        finish(
            FirstRunSetupModel.build(
                id.getText(),
                host.getText(),
                port.getText(),
                tls.isSelected(),
                nick.getText(),
                account.getText(),
                passwordText(),
                channels.getText()));
        return;
      }
      page++;
      updatePage();
    } catch (IllegalArgumentException ex) {
      status.setText(ex.getMessage());
    }
  }

  private String passwordText() {
    char[] secret = password.getPassword();
    try {
      return new String(secret);
    } finally {
      Arrays.fill(secret, '\0');
    }
  }

  private void updatePage() {
    heading.setText(MESSAGES.text("setup.step." + page));
    layout.show(pages, Integer.toString(page));
    back.setEnabled(page > 0);
    next.setText(MESSAGES.text(page == 2 ? "setup.finish" : "setup.next"));
    status.setText(" ");
    getRootPane().setDefaultButton(next);
  }

  private void finish(IrcProperties.Server server) {
    if (worker != null) return;
    for (JButton button : new JButton[] {back, next, skip, skipAll}) button.setEnabled(false);
    status.setText(MESSAGES.text("setup.saving"));
    worker =
        new SwingWorker<>() {
          @Override
          protected Void doInBackground() {
            config.finishSetup(server);
            return null;
          }

          @Override
          protected void done() {
            if (isCancelled()) return;
            worker = null;
            try {
              get();
              dispose();
            } catch (CancellationException ex) {
              dispose();
            } catch (Exception ex) {
              log.warn("[ircafe] Could not save first-launch setup", ex);
              if (server == null) {
                // Optional setup must never trap a user whose config cannot be written.
                dispose();
              } else {
                updatePage();
                next.setEnabled(true);
                skip.setEnabled(true);
                skipAll.setEnabled(true);
                status.setText(MESSAGES.text("setup.saveFailed"));
              }
            }
          }
        };
    worker.execute();
  }

  @Override
  public void dispose() {
    if (worker != null) {
      worker.cancel(true);
      worker = null;
    }
    password.setText("");
    super.dispose();
  }

  private static JPanel form() {
    return new JPanel(new GridLayout(0, 2, 12, 12));
  }

  private static JPanel page(JComponent fields, String helpKey) {
    JPanel panel = new JPanel(new BorderLayout(0, 16));
    panel.add(new JLabel(MESSAGES.text(helpKey)), BorderLayout.NORTH);
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(fields, BorderLayout.NORTH);
    panel.add(wrapper, BorderLayout.CENTER);
    return panel;
  }

  private static void row(JPanel panel, String labelKey, JComponent field) {
    JLabel label = new JLabel(MESSAGES.text(labelKey));
    label.setLabelFor(field);
    panel.add(label);
    panel.add(field);
  }

  private static <T extends JComponent> T named(T component, String name) {
    component.setName(name);
    return component;
  }
}
