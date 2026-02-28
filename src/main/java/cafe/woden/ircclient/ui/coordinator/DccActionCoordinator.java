package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.NickContextMenuFactory;
import java.awt.Component;
import java.io.File;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Handles DCC nick-context actions and emits validated commands to the outbound pipeline. */
public final class DccActionCoordinator {

  @FunctionalInterface
  interface InvalidPathWarning {
    void show(Component owner);
  }

  private final Component ownerComponent;
  private final BiConsumer<TargetRef, String> commandEmitter;
  private final Supplier<JFileChooser> chooserSupplier;
  private final InvalidPathWarning invalidPathWarning;

  public DccActionCoordinator(
      Component ownerComponent, BiConsumer<TargetRef, String> commandEmitter) {
    this(
        ownerComponent,
        commandEmitter,
        JFileChooser::new,
        DccActionCoordinator::showInvalidPathWarning);
  }

  public DccActionCoordinator(
      Component ownerComponent,
      BiConsumer<TargetRef, String> commandEmitter,
      Supplier<JFileChooser> chooserSupplier,
      InvalidPathWarning invalidPathWarning) {
    this.ownerComponent = Objects.requireNonNull(ownerComponent, "ownerComponent");
    this.commandEmitter = Objects.requireNonNull(commandEmitter, "commandEmitter");
    this.chooserSupplier = Objects.requireNonNull(chooserSupplier, "chooserSupplier");
    this.invalidPathWarning = Objects.requireNonNull(invalidPathWarning, "invalidPathWarning");
  }

  public void requestAction(TargetRef ctx, String nick, NickContextMenuFactory.DccAction action) {
    if (ctx == null) return;
    if (action == null) return;

    String normalizedNick = Objects.toString(nick, "").trim();
    if (normalizedNick.isEmpty()) return;

    switch (action) {
      case CHAT -> emitCommand(ctx, "/dcc chat " + normalizedNick);
      case ACCEPT_CHAT -> emitCommand(ctx, "/dcc accept " + normalizedNick);
      case GET_FILE -> emitCommand(ctx, "/dcc get " + normalizedNick);
      case CLOSE_CHAT -> emitCommand(ctx, "/dcc close " + normalizedNick);
      case SEND_FILE -> promptAndSendFile(ctx, normalizedNick);
    }
  }

  private void promptAndSendFile(TargetRef ctx, String nick) {
    JFileChooser chooser =
        Objects.requireNonNull(
            chooserSupplier.get(), "chooserSupplier must not return a null chooser");
    chooser.setDialogTitle("Send File to " + nick);
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

    Component owner = SwingUtilities.getWindowAncestor(ownerComponent);
    int result = chooser.showOpenDialog(owner);
    if (result != JFileChooser.APPROVE_OPTION) return;

    File selected = chooser.getSelectedFile();
    if (selected == null) return;

    String path = Objects.toString(selected.getAbsolutePath(), "").trim();
    if (path.isEmpty()) return;
    if (path.indexOf('\r') >= 0 || path.indexOf('\n') >= 0) {
      invalidPathWarning.show(owner);
      return;
    }

    emitCommand(ctx, "/dcc send " + nick + " " + path);
  }

  private void emitCommand(TargetRef ctx, String line) {
    String sid = Objects.toString(ctx.serverId(), "").trim();
    String cmd = Objects.toString(line, "").trim();
    if (sid.isEmpty() || cmd.isEmpty()) return;
    commandEmitter.accept(ctx, cmd);
  }

  private static void showInvalidPathWarning(Component owner) {
    JOptionPane.showMessageDialog(
        owner, "Refusing file path containing newlines.", "DCC Send", JOptionPane.WARNING_MESSAGE);
  }
}
