package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * Owns reply-compose state + banner UI, and quick-reaction emission.
 */
public final class MessageInputComposeSupport {
  private static final Logger log = LoggerFactory.getLogger(MessageInputComposeSupport.class);

  private static final String[] QUICK_REACTION_TOKENS = {
      ":+1:",
      ":heart:",
      ":laughing:",
      ":thinking:",
      ":eyes:"
  };

  private final JComponent layoutTarget;
  private final Component dialogOwner;
  private final JTextField input;
  private final JButton sendButton;
  private final MessageInputUiHooks hooks;

  private final JPanel composeBanner = new JPanel(new BorderLayout(6, 0));
  private final JLabel composeBannerLabel = new JLabel();
  private final JButton composeBannerCancel = new JButton("Cancel");

  private String replyComposeTarget = "";
  private String replyComposeMessageId = "";

  public MessageInputComposeSupport(
      JComponent layoutTarget,
      Component dialogOwner,
      JTextField input,
      JButton sendButton,
      MessageInputUiHooks hooks
  ) {
    this.layoutTarget = Objects.requireNonNull(layoutTarget, "layoutTarget");
    this.dialogOwner = Objects.requireNonNull(dialogOwner, "dialogOwner");
    this.input = Objects.requireNonNull(input, "input");
    this.sendButton = Objects.requireNonNull(sendButton, "sendButton");
    this.hooks = Objects.requireNonNull(hooks, "hooks");

    configureComposeBanner();
  }

  public JComponent banner() {
    return composeBanner;
  }

  public boolean hasReplyCompose() {
    return !replyComposeTarget.isBlank() && !replyComposeMessageId.isBlank();
  }

  public void beginReplyCompose(String ircTarget, String messageId) {
    String target = normalizeComposeTarget(ircTarget);
    String msgId = normalizeComposeMessageId(messageId);
    if (target.isEmpty() || msgId.isEmpty()) return;
    replyComposeTarget = target;
    replyComposeMessageId = msgId;
    updateComposeBanner();
  }

  public void clearReplyCompose() {
    clearReplyComposeInternal(true, true);
  }

  void clearReplyComposeInternal(boolean focusInputAfter, boolean notifyDraftChangedFlag) {
    boolean hadCompose = hasReplyCompose();
    replyComposeTarget = "";
    replyComposeMessageId = "";
    updateComposeBanner();

    if (focusInputAfter) {
      try {
        hooks.focusInput();
      } catch (Exception ex) {
        log.warn("[MessageInputComposeSupport] hooks.focusInput failed", ex);
      }
    }
    if (hadCompose && notifyDraftChangedFlag) {
      try {
        hooks.fireDraftChanged();
      } catch (Exception ex) {
        log.warn("[MessageInputComposeSupport] hooks.fireDraftChanged failed", ex);
      }
    }
  }

  public void onDraftTextSetProgrammatically() {
    clearReplyComposeInternal(false, false);
  }

  public void onInputDisabled() {
    clearReplyComposeInternal(false, false);
  }

  public TransformResult transformOutgoing(String message) {
    String msg = Objects.toString(message, "");
    boolean consumeReplyCompose = shouldEmitReplyComposeCommand(msg);
    if (!consumeReplyCompose) return new TransformResult(msg, false);
    return new TransformResult("/reply " + replyComposeMessageId + " " + msg, true);
  }

  private boolean shouldEmitReplyComposeCommand(String message) {
    if (!hasReplyCompose()) return false;
    String m = Objects.toString(message, "").trim();
    if (m.isEmpty()) return false;
    // Slash commands should remain explicit user commands; reply mode only applies to plain chat text.
    return !m.startsWith("/");
  }

  public void openQuickReactionPicker(String ircTarget, String messageId) {
    String target = normalizeComposeTarget(ircTarget);
    String msgId = normalizeComposeMessageId(messageId);
    if (target.isEmpty() || msgId.isEmpty()) return;
    if (!input.isEnabled()) return;

    JPopupMenu menu = new JPopupMenu();
    for (String reaction : QUICK_REACTION_TOKENS) {
      JMenuItem item = new JMenuItem(reaction);
      item.addActionListener(e -> emitQuickReaction(target, msgId, reaction));
      menu.add(item);
    }
    menu.addSeparator();
    JMenuItem custom = new JMenuItem("Custom...");
    custom.addActionListener(e -> {
      String entered = JOptionPane.showInputDialog(
          SwingUtilities.getWindowAncestor(dialogOwner),
          "Reaction token (for example :sparkles:)",
          "React to Message",
          JOptionPane.PLAIN_MESSAGE);
      String token = normalizeReactionToken(entered);
      if (token.isEmpty()) return;
      emitQuickReaction(target, msgId, token);
    });
    menu.add(custom);

    try {
      PopupMenuThemeSupport.prepareForDisplay(menu);
      menu.show(input, Math.max(0, input.getWidth() - 8), input.getHeight());
    } catch (Exception ex) {
      log.warn("[MessageInputComposeSupport] showing quick reaction menu failed", ex);
    }
  }

  private void emitQuickReaction(String target, String msgId, String reaction) {
    String m = normalizeComposeMessageId(msgId);
    String r = normalizeReactionToken(reaction);
    if (m.isEmpty() || r.isEmpty()) return;

    try {
      hooks.flushTypingDone();
    } catch (Exception ex) {
      log.warn("[MessageInputComposeSupport] hooks.flushTypingDone failed", ex);
    }

    try {
      hooks.sendOutbound("/react " + m + " " + r);
    } catch (Exception ex) {
      log.warn("[MessageInputComposeSupport] hooks.sendOutbound failed", ex);
    }
  }

  private void configureComposeBanner() {
    composeBanner.setOpaque(false);
    composeBanner.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
    composeBannerLabel.setText("");

    composeBannerCancel.setFocusable(false);
    composeBannerCancel.addActionListener(e -> clearReplyCompose());

    composeBanner.add(composeBannerLabel, BorderLayout.CENTER);
    composeBanner.add(composeBannerCancel, BorderLayout.EAST);
    composeBanner.setVisible(false);
  }

  private void updateComposeBanner() {
    if (hasReplyCompose()) {
      composeBannerLabel.setText("Replying to message " + abbreviateMessageId(replyComposeMessageId));
      composeBanner.setVisible(true);
      sendButton.setText("Reply");
    } else {
      composeBannerLabel.setText("");
      composeBanner.setVisible(false);
      sendButton.setText("Send");
    }

    try {
      layoutTarget.revalidate();
      layoutTarget.repaint();
    } catch (Exception ex) {
      log.warn("[MessageInputComposeSupport] refresh layout failed", ex);
    }
  }

  private static String abbreviateMessageId(String raw) {
    String id = Objects.toString(raw, "").trim();
    if (id.length() <= 18) return id;
    return id.substring(0, 18) + "...";
  }

  private static String normalizeComposeTarget(String raw) {
    String target = Objects.toString(raw, "").trim();
    if (target.isEmpty()) return "";
    if (target.indexOf(' ') >= 0 || target.indexOf('\n') >= 0 || target.indexOf('\r') >= 0) return "";
    return target;
  }

  private static String normalizeComposeMessageId(String raw) {
    String msgId = Objects.toString(raw, "").trim();
    if (msgId.isEmpty()) return "";
    if (msgId.indexOf(' ') >= 0 || msgId.indexOf('\n') >= 0 || msgId.indexOf('\r') >= 0) return "";
    return msgId;
  }

  private static String normalizeReactionToken(String raw) {
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return "";
    if (token.indexOf(' ') >= 0 || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) return "";
    return token;
  }

  public record TransformResult(String outboundLine, boolean consumeReplyCompose) {}
}
