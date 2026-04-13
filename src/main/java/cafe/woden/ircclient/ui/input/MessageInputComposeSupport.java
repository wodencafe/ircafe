package cafe.woden.ircclient.ui.input;

import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import java.awt.*;
import java.util.Objects;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns reply-compose state + banner UI, and quick-reaction emission. */
public final class MessageInputComposeSupport {
  private static final Logger log = LoggerFactory.getLogger(MessageInputComposeSupport.class);

  private static final String[] QUICK_REACTION_TOKENS = {
    ":+1:", ":heart:", ":laughing:", ":thinking:", ":eyes:"
  };
  private static final int REPLY_PREVIEW_TEXT_MAX_CHARS = 96;

  private final JComponent layoutTarget;
  private final Component dialogOwner;
  private final JTextComponent input;
  private final JButton sendButton;
  private final MessageInputUiHooks hooks;
  private ReactionCommandResolver quickReactionCommandResolver =
      MessageInputComposeSupport::defaultQuickReactionCommand;

  private final JPanel composeBanner = new JPanel(new BorderLayout(6, 0));
  private final JLabel composeBannerLabel = new JLabel();
  private final JPanel composeBannerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
  private final JButton composeBannerJump = new JButton("Jump");
  private final JButton composeBannerCancel = new JButton("Cancel");

  private String replyComposeTarget = "";
  private String replyComposeMessageId = "";
  private String replyComposePreview = "";
  private Runnable replyComposeJumpAction = null;

  @FunctionalInterface
  public interface ReactionCommandResolver {
    String commandFor(String ircTarget, String messageId, String reactionToken);
  }

  public MessageInputComposeSupport(
      JComponent layoutTarget,
      Component dialogOwner,
      JTextComponent input,
      JButton sendButton,
      MessageInputUiHooks hooks) {
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

  public void setQuickReactionCommandResolver(ReactionCommandResolver resolver) {
    quickReactionCommandResolver =
        resolver != null ? resolver : MessageInputComposeSupport::defaultQuickReactionCommand;
  }

  public boolean hasReplyCompose() {
    return !replyComposeTarget.isBlank() && !replyComposeMessageId.isBlank();
  }

  public void beginReplyCompose(String ircTarget, String messageId) {
    beginReplyCompose(ircTarget, messageId, "", null);
  }

  public void beginReplyCompose(
      String ircTarget, String messageId, String previewText, Runnable jumpAction) {
    String target = normalizeComposeTarget(ircTarget);
    String msgId = normalizeComposeMessageId(messageId);
    if (target.isEmpty() || msgId.isEmpty()) return;
    replyComposeTarget = target;
    replyComposeMessageId = msgId;
    replyComposePreview = normalizeReplyPreviewText(previewText);
    replyComposeJumpAction = jumpAction;
    updateComposeBanner();
  }

  public void clearReplyCompose() {
    clearReplyComposeInternal(true, true);
  }

  void clearReplyComposeInternal(boolean focusInputAfter, boolean notifyDraftChangedFlag) {
    boolean hadCompose = hasReplyCompose();
    replyComposeTarget = "";
    replyComposeMessageId = "";
    replyComposePreview = "";
    replyComposeJumpAction = null;
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
    // Slash commands should remain explicit user commands; reply mode only applies to plain chat
    // text.
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
    custom.addActionListener(
        e -> {
          String entered =
              JOptionPane.showInputDialog(
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

  void emitQuickReaction(String ircTarget, String msgId, String reaction) {
    String target = normalizeComposeTarget(ircTarget);
    String m = normalizeComposeMessageId(msgId);
    String r = normalizeReactionToken(reaction);
    if (target.isEmpty() || m.isEmpty() || r.isEmpty()) return;

    try {
      hooks.flushTypingDone();
    } catch (Exception ex) {
      log.warn("[MessageInputComposeSupport] hooks.flushTypingDone failed", ex);
    }

    try {
      String line = quickReactionCommandResolver.commandFor(target, m, r);
      if (line == null || line.isBlank()) return;
      hooks.sendOutbound(line.trim());
    } catch (Exception ex) {
      log.warn("[MessageInputComposeSupport] hooks.sendOutbound failed", ex);
    }
  }

  private static String defaultQuickReactionCommand(
      String ircTarget, String messageId, String reactionToken) {
    String msgId = normalizeComposeMessageId(messageId);
    String reaction = normalizeReactionToken(reactionToken);
    if (msgId.isEmpty() || reaction.isEmpty()) return "";
    return "/react " + msgId + " " + reaction;
  }

  private void configureComposeBanner() {
    composeBanner.setOpaque(false);
    composeBanner.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
    composeBannerLabel.setText("");

    composeBannerActions.setOpaque(false);

    composeBannerJump.setFocusable(false);
    composeBannerJump.addActionListener(
        e -> {
          Runnable jump = replyComposeJumpAction;
          if (jump == null) return;
          try {
            jump.run();
          } catch (Exception ex) {
            log.warn("[MessageInputComposeSupport] jump action failed", ex);
          }
          try {
            hooks.focusInput();
          } catch (Exception ex) {
            log.warn("[MessageInputComposeSupport] hooks.focusInput failed after jump", ex);
          }
        });

    composeBannerCancel.setFocusable(false);
    composeBannerCancel.addActionListener(e -> clearReplyCompose());

    composeBannerActions.add(composeBannerJump);
    composeBannerActions.add(composeBannerCancel);
    composeBanner.add(composeBannerLabel, BorderLayout.CENTER);
    composeBanner.add(composeBannerActions, BorderLayout.EAST);
    composeBanner.setVisible(false);
  }

  private void updateComposeBanner() {
    if (hasReplyCompose()) {
      composeBannerLabel.setText(replyComposeBannerText());
      composeBannerJump.setVisible(replyComposeJumpAction != null);
      composeBanner.setVisible(true);
      updateSendButtonHint("Send reply");
    } else {
      composeBannerLabel.setText("");
      composeBannerJump.setVisible(false);
      composeBanner.setVisible(false);
      updateSendButtonHint("Send message");
    }

    try {
      layoutTarget.revalidate();
      layoutTarget.repaint();
    } catch (Exception ex) {
      log.warn("[MessageInputComposeSupport] refresh layout failed", ex);
    }
  }

  private void updateSendButtonHint(String tooltip) {
    String text = Objects.toString(tooltip, "").trim();
    if (text.isEmpty()) text = "Send message";
    sendButton.setToolTipText(text);
    if (sendButton.getAccessibleContext() != null) {
      sendButton.getAccessibleContext().setAccessibleName(text);
      sendButton.getAccessibleContext().setAccessibleDescription(text);
    }
  }

  private static String abbreviateMessageId(String raw) {
    String id = Objects.toString(raw, "").trim();
    if (id.length() <= 18) return id;
    return id.substring(0, 18) + "...";
  }

  private String replyComposeBannerText() {
    String base = "Replying to message " + abbreviateMessageId(replyComposeMessageId);
    if (replyComposePreview.isBlank()) return base;
    return base + " - " + replyComposePreview;
  }

  private static String normalizeReplyPreviewText(String rawText) {
    String raw = Objects.toString(rawText, "");
    if (raw.isBlank()) return "";

    StringBuilder out = new StringBuilder(Math.min(REPLY_PREVIEW_TEXT_MAX_CHARS, raw.length()));
    boolean pendingSpace = false;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (Character.isWhitespace(c)) {
        pendingSpace = out.length() > 0;
        continue;
      }
      if (c < 0x20 && c != '\t') continue;
      if (pendingSpace && out.length() > 0) {
        out.append(' ');
        pendingSpace = false;
      }
      out.append(c);
      if (out.length() >= REPLY_PREVIEW_TEXT_MAX_CHARS) break;
    }

    String normalized = out.toString().trim();
    if (normalized.length() >= REPLY_PREVIEW_TEXT_MAX_CHARS && raw.length() > normalized.length()) {
      int max = Math.max(1, REPLY_PREVIEW_TEXT_MAX_CHARS - 3);
      normalized = normalized.substring(0, Math.min(max, normalized.length())).trim() + "...";
    }
    return normalized;
  }

  private static String normalizeComposeTarget(String raw) {
    String target = Objects.toString(raw, "").trim();
    if (target.isEmpty()) return "";
    if (target.indexOf(' ') >= 0 || target.indexOf('\n') >= 0 || target.indexOf('\r') >= 0)
      return "";
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
