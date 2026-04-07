package cafe.woden.ircclient.ui.util;

import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.util.VirtualThreads;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;

/**
 * Decorates a chat transcript {@link JTextComponent} with a right-click context menu.
 *
 * <ul>
 *   <li>Default: Copy / Select All / Find Text / Reload Recent History / Clear
 *   <li>If right-clicking on a URL token: Open Link in Browser / Copy Link Address / Save Link
 *       As...
 * </ul>
 */
public final class ChatTranscriptContextMenuDecorator implements AutoCloseable {

  private final JTextComponent transcript;
  private final Runnable openFind;

  private final Consumer<String> openUrl;
  private final MouseAdapter mouse;

  // Optional: allow the caller to provide a per-view proxy plan (e.g., per server).
  // If null, fall back to the global NetProxyContext proxy.
  private final Supplier<ProxyPlan> proxyPlanSupplier;
  private final Supplier<Boolean> loadNewerActionVisibleSupplier;
  private final Supplier<Boolean> loadAroundActionVisibleSupplier;
  private final Runnable onLoadNewerHistory;
  private final Consumer<String> onLoadContextAroundMessage;
  private final Supplier<Boolean> replyActionVisibleSupplier;
  private final Supplier<Boolean> reactActionVisibleSupplier;
  private final Supplier<Boolean> unreactActionVisibleSupplier;
  private final Supplier<Boolean> editActionVisibleSupplier;
  private final Supplier<Boolean> redactActionVisibleSupplier;
  private final Supplier<Boolean> revealRedactedActionVisibleSupplier;
  private final Consumer<String> onReplyToMessage;
  private final Consumer<String> onReactToMessage;
  private final Consumer<String> onUnreactToMessage;
  private final Consumer<String> onEditMessage;
  private final Consumer<String> onRedactMessage;
  private final Consumer<String> onRevealRedactedMessage;
  private final boolean historyActionsConfigured;
  private final boolean messageActionsConfigured;

  private final JPopupMenu menu = new JPopupMenu();
  private final JMenuItem copyItem = new JMenuItem("Copy");
  private final JMenuItem selectAllItem = new JMenuItem("Select All");
  private final JMenuItem findItem = new JMenuItem("Find Text");
  private final JMenuItem reloadRecentItem = new JMenuItem("Reload Recent History");
  private final JMenuItem clearItem = new JMenuItem("Clear");

  private final JMenuItem inspectLineItem = new JMenuItem("Inspect line…");
  private final JMenuItem copyMessageIdItem = new JMenuItem("Copy Message ID");
  private final JMenuItem copyIrcv3TagsItem = new JMenuItem("Copy IRCv3 Tags");
  private final JMenuItem loadNewerHistoryItem = new JMenuItem("Load Newer History");
  private final JMenuItem loadAroundMessageItem = new JMenuItem("Load Context Around Message…");
  private final JMenuItem replyToMessageItem = new JMenuItem("Reply to Message…");
  private final JMenuItem reactToMessageItem = new JMenuItem("React to Message…");
  private final JMenuItem unreactToMessageItem = new JMenuItem("Remove Reaction…");
  private final JMenuItem editMessageItem = new JMenuItem("Edit Message…");
  private final JMenuItem redactMessageItem = new JMenuItem("Redact Message…");
  private final JMenuItem revealRedactedMessageItem = new JMenuItem("Display Redacted Message…");

  private volatile Point lastPopupPoint;

  private volatile Runnable clearAction;
  private volatile Runnable reloadRecentAction;

  private final JMenuItem openLinkItem = new JMenuItem("Open Link in Browser");
  private final JMenuItem copyLinkItem = new JMenuItem("Copy Link Address");
  private final JMenuItem saveLinkItem = new JMenuItem("Save Link As...");

  private volatile String currentPopupUrl;
  private volatile String currentPopupMessageId;
  private volatile String currentPopupIrcv3Tags;
  private volatile boolean currentPopupOwnMessage;
  private volatile boolean currentPopupRedactedMessage;

  // Some sites reject programmatic downloads unless the request looks like a browser.
  private static final String DEFAULT_UA =
      "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0";

  private boolean closed = false;

  private ChatTranscriptContextMenuDecorator(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Function<Point, String> nickAt,
      Function<String, JPopupMenu> nickMenuFor,
      Consumer<String> openUrl,
      Runnable openFind,
      Supplier<ProxyPlan> proxyPlanSupplier,
      Supplier<Boolean> loadNewerActionVisibleSupplier,
      Supplier<Boolean> loadAroundActionVisibleSupplier,
      Runnable onLoadNewerHistory,
      Consumer<String> onLoadContextAroundMessage,
      Supplier<Boolean> replyActionVisibleSupplier,
      Supplier<Boolean> reactActionVisibleSupplier,
      Supplier<Boolean> unreactActionVisibleSupplier,
      Consumer<String> onReplyToMessage,
      Consumer<String> onReactToMessage,
      Consumer<String> onUnreactToMessage,
      Supplier<Boolean> editActionVisibleSupplier,
      Supplier<Boolean> redactActionVisibleSupplier,
      Consumer<String> onEditMessage,
      Consumer<String> onRedactMessage,
      Supplier<Boolean> revealRedactedActionVisibleSupplier,
      Consumer<String> onRevealRedactedMessage) {
    this.transcript = Objects.requireNonNull(transcript, "transcript");

    this.openUrl = openUrl;
    this.openFind = (openFind != null) ? openFind : () -> {};
    this.proxyPlanSupplier = proxyPlanSupplier;
    this.historyActionsConfigured =
        loadNewerActionVisibleSupplier != null
            || loadAroundActionVisibleSupplier != null
            || onLoadNewerHistory != null
            || onLoadContextAroundMessage != null;
    this.messageActionsConfigured =
        replyActionVisibleSupplier != null
            || reactActionVisibleSupplier != null
            || unreactActionVisibleSupplier != null
            || editActionVisibleSupplier != null
            || redactActionVisibleSupplier != null
            || onReplyToMessage != null
            || onReactToMessage != null
            || onUnreactToMessage != null
            || onEditMessage != null
            || onRedactMessage != null
            || revealRedactedActionVisibleSupplier != null
            || onRevealRedactedMessage != null;
    this.loadNewerActionVisibleSupplier =
        (loadNewerActionVisibleSupplier != null) ? loadNewerActionVisibleSupplier : () -> false;
    this.loadAroundActionVisibleSupplier =
        (loadAroundActionVisibleSupplier != null) ? loadAroundActionVisibleSupplier : () -> false;
    this.onLoadNewerHistory = (onLoadNewerHistory != null) ? onLoadNewerHistory : () -> {};
    this.onLoadContextAroundMessage =
        (onLoadContextAroundMessage != null) ? onLoadContextAroundMessage : msgId -> {};
    this.replyActionVisibleSupplier =
        (replyActionVisibleSupplier != null) ? replyActionVisibleSupplier : () -> false;
    this.reactActionVisibleSupplier =
        (reactActionVisibleSupplier != null) ? reactActionVisibleSupplier : () -> false;
    this.unreactActionVisibleSupplier =
        (unreactActionVisibleSupplier != null) ? unreactActionVisibleSupplier : () -> false;
    this.onReplyToMessage = (onReplyToMessage != null) ? onReplyToMessage : msgId -> {};
    this.onReactToMessage = (onReactToMessage != null) ? onReactToMessage : msgId -> {};
    this.onUnreactToMessage = (onUnreactToMessage != null) ? onUnreactToMessage : msgId -> {};
    this.editActionVisibleSupplier =
        (editActionVisibleSupplier != null) ? editActionVisibleSupplier : () -> false;
    this.redactActionVisibleSupplier =
        (redactActionVisibleSupplier != null) ? redactActionVisibleSupplier : () -> false;
    this.revealRedactedActionVisibleSupplier =
        (revealRedactedActionVisibleSupplier != null)
            ? revealRedactedActionVisibleSupplier
            : () -> false;
    this.onEditMessage = (onEditMessage != null) ? onEditMessage : msgId -> {};
    this.onRedactMessage = (onRedactMessage != null) ? onRedactMessage : msgId -> {};
    this.onRevealRedactedMessage =
        (onRevealRedactedMessage != null) ? onRevealRedactedMessage : msgId -> {};

    copyItem.addActionListener(this::onCopy);
    selectAllItem.addActionListener(this::onSelectAll);
    findItem.addActionListener(this::onFind);
    reloadRecentItem.addActionListener(this::onReloadRecent);
    clearItem.addActionListener(this::onClear);
    inspectLineItem.addActionListener(this::onInspectLine);
    copyMessageIdItem.addActionListener(this::onCopyMessageId);
    copyIrcv3TagsItem.addActionListener(this::onCopyIrcv3Tags);
    loadNewerHistoryItem.addActionListener(this::onLoadNewerHistory);
    loadAroundMessageItem.addActionListener(this::onLoadContextAroundMessage);
    replyToMessageItem.addActionListener(this::onReplyToMessage);
    reactToMessageItem.addActionListener(this::onReactToMessage);
    unreactToMessageItem.addActionListener(this::onUnreactToMessage);
    editMessageItem.addActionListener(this::onEditMessage);
    redactMessageItem.addActionListener(this::onRedactMessage);
    revealRedactedMessageItem.addActionListener(this::onRevealRedactedMessage);

    openLinkItem.addActionListener(this::onOpenLink);
    copyLinkItem.addActionListener(this::onCopyLink);
    saveLinkItem.addActionListener(this::onSaveLink);

    // Build initial (non-link) menu.
    rebuildMenu(null);

    this.mouse =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            maybeShow(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShow(e);
          }

          private void maybeShow(MouseEvent e) {
            if (closed) return;
            if (e == null) return;
            try {
              lastPopupPoint = e.getPoint();
            } catch (Exception ignored) {
              lastPopupPoint = null;
            }

            // Cross-platform: popup trigger may fire on pressed or released.
            if (!e.isPopupTrigger() && !SwingUtilities.isRightMouseButton(e)) return;
            if (!transcript.isShowing() || !transcript.isEnabled()) return;

            // If this click is on a nick token and a nick-menu factory is provided, prefer that
            // menu.
            String nick = safeHit(nickAt, e.getPoint());
            if (nick != null && !nick.isBlank() && nickMenuFor != null) {
              JPopupMenu nickMenu = null;
              try {
                nickMenu = nickMenuFor.apply(nick);
              } catch (Exception ignored) {
              }
              if (nickMenu != null && nickMenu.getComponentCount() > 0) {
                currentPopupUrl = null;
                PopupMenuThemeSupport.prepareForDisplay(nickMenu);
                nickMenu.show(transcript, e.getX(), e.getY());
                return;
              }
            }

            String url = safeHit(urlAt, e.getPoint());
            currentPopupUrl = url;
            LineIdentity identity = lineIdentityAtPoint(e.getPoint());
            currentPopupMessageId = identity.messageId();
            currentPopupIrcv3Tags = identity.ircv3Tags();
            currentPopupOwnMessage = identity.outgoingOwnMessage();
            currentPopupRedactedMessage = identity.redactedMessage();
            rebuildMenu(url);
            updateEnabledState(url);

            PopupMenuThemeSupport.prepareForDisplay(menu);
            menu.show(transcript, e.getX(), e.getY());
          }
        };

    transcript.addMouseListener(this.mouse);
  }

  public static ChatTranscriptContextMenuDecorator decorate(
      JTextComponent transcript, Runnable openFind) {
    return new ChatTranscriptContextMenuDecorator(
        transcript,
        null,
        null,
        null,
        null,
        openFind,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public static ChatTranscriptContextMenuDecorator decorate(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Consumer<String> openUrl,
      Runnable openFind) {
    return new ChatTranscriptContextMenuDecorator(
        transcript,
        urlAt,
        null,
        null,
        openUrl,
        openFind,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public static ChatTranscriptContextMenuDecorator decorate(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Function<Point, String> nickAt,
      Function<String, JPopupMenu> nickMenuFor,
      Consumer<String> openUrl,
      Runnable openFind) {
    return new ChatTranscriptContextMenuDecorator(
        transcript,
        urlAt,
        nickAt,
        nickMenuFor,
        openUrl,
        openFind,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public static ChatTranscriptContextMenuDecorator decorate(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Function<Point, String> nickAt,
      Function<String, JPopupMenu> nickMenuFor,
      Consumer<String> openUrl,
      Runnable openFind,
      Supplier<ProxyPlan> proxyPlanSupplier) {
    return new ChatTranscriptContextMenuDecorator(
        transcript,
        urlAt,
        nickAt,
        nickMenuFor,
        openUrl,
        openFind,
        proxyPlanSupplier,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public static ChatTranscriptContextMenuDecorator decorate(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Function<Point, String> nickAt,
      Function<String, JPopupMenu> nickMenuFor,
      Consumer<String> openUrl,
      Runnable openFind,
      Supplier<ProxyPlan> proxyPlanSupplier,
      Supplier<Boolean> loadNewerActionVisibleSupplier,
      Supplier<Boolean> loadAroundActionVisibleSupplier,
      Runnable onLoadNewerHistory,
      Consumer<String> onLoadContextAroundMessage,
      Supplier<Boolean> replyActionVisibleSupplier,
      Supplier<Boolean> reactActionVisibleSupplier,
      Supplier<Boolean> unreactActionVisibleSupplier,
      Consumer<String> onReplyToMessage,
      Consumer<String> onReactToMessage,
      Consumer<String> onUnreactToMessage,
      Supplier<Boolean> editActionVisibleSupplier,
      Supplier<Boolean> redactActionVisibleSupplier,
      Consumer<String> onEditMessage,
      Consumer<String> onRedactMessage,
      Supplier<Boolean> revealRedactedActionVisibleSupplier,
      Consumer<String> onRevealRedactedMessage) {
    return new ChatTranscriptContextMenuDecorator(
        transcript,
        urlAt,
        nickAt,
        nickMenuFor,
        openUrl,
        openFind,
        proxyPlanSupplier,
        loadNewerActionVisibleSupplier,
        loadAroundActionVisibleSupplier,
        onLoadNewerHistory,
        onLoadContextAroundMessage,
        replyActionVisibleSupplier,
        reactActionVisibleSupplier,
        unreactActionVisibleSupplier,
        onReplyToMessage,
        onReactToMessage,
        onUnreactToMessage,
        editActionVisibleSupplier,
        redactActionVisibleSupplier,
        onEditMessage,
        onRedactMessage,
        revealRedactedActionVisibleSupplier,
        onRevealRedactedMessage);
  }

  public void setClearAction(Runnable clearAction) {
    this.clearAction = clearAction;
  }

  public void setReloadRecentAction(Runnable reloadRecentAction) {
    this.reloadRecentAction = reloadRecentAction;
  }

  private void rebuildMenu(String url) {
    menu.removeAll();

    if (url != null) {
      menu.add(openLinkItem);
      menu.add(copyLinkItem);
      menu.add(saveLinkItem);
      menu.addSeparator();
      menu.add(selectAllItem);
      menu.add(inspectLineItem);
      menu.add(copyMessageIdItem);
      menu.add(copyIrcv3TagsItem);
      maybeAddHistoryActionItems();
      maybeAddMessageActionItems();
      menu.addSeparator();
      menu.add(findItem);
      menu.addSeparator();
      menu.add(reloadRecentItem);
      menu.add(clearItem);
    } else {
      menu.add(copyItem);
      menu.add(selectAllItem);
      menu.add(inspectLineItem);
      menu.add(copyMessageIdItem);
      menu.add(copyIrcv3TagsItem);
      maybeAddHistoryActionItems();
      maybeAddMessageActionItems();
      menu.addSeparator();
      menu.add(findItem);
      menu.addSeparator();
      menu.add(reloadRecentItem);
      menu.add(clearItem);
    }
  }

  private void maybeAddHistoryActionItems() {
    if (!historyActionsConfigured) return;
    menu.addSeparator();
    menu.add(loadNewerHistoryItem);
    menu.add(loadAroundMessageItem);
  }

  private void maybeAddMessageActionItems() {
    if (!messageActionsConfigured) return;
    menu.addSeparator();
    menu.add(replyToMessageItem);
    menu.add(reactToMessageItem);
    menu.add(unreactToMessageItem);
    menu.add(editMessageItem);
    menu.add(redactMessageItem);
    if (currentPopupRedactedMessage) {
      menu.add(revealRedactedMessageItem);
    }
  }

  private void updateEnabledState(String url) {
    try {
      int start = transcript.getSelectionStart();
      int end = transcript.getSelectionEnd();
      copyItem.setEnabled(start != end);
    } catch (Exception ignored) {
      copyItem.setEnabled(true);
    }

    boolean hasUrl = (url != null && !url.isBlank());
    openLinkItem.setEnabled(hasUrl);
    copyLinkItem.setEnabled(hasUrl);
    saveLinkItem.setEnabled(hasUrl);
    boolean hasMessageId = currentPopupMessageId != null && !currentPopupMessageId.isBlank();
    copyMessageIdItem.setEnabled(hasMessageId);
    copyIrcv3TagsItem.setEnabled(currentPopupIrcv3Tags != null && !currentPopupIrcv3Tags.isBlank());
    updateHistoryActionState(hasMessageId);
    updateMessageActionState(hasMessageId);

    try {
      Document doc = transcript.getDocument();
      boolean hasText = (doc != null && doc.getLength() > 0);
      boolean canReload = (reloadRecentAction != null);
      reloadRecentItem.setEnabled(canReload);
      clearItem.setEnabled(hasText);
    } catch (Exception ignored) {
      clearItem.setEnabled(true);
      reloadRecentItem.setEnabled(reloadRecentAction != null);
    }
  }

  private void updateHistoryActionState(boolean hasMessageId) {
    if (!historyActionsConfigured) return;

    boolean loadNewerAvailable = isActionVisible(loadNewerActionVisibleSupplier);
    loadNewerHistoryItem.setEnabled(loadNewerAvailable);
    loadNewerHistoryItem.setToolTipText(
        loadNewerAvailable
            ? "Load newer history from the connected server/bouncer."
            : "Unavailable: server does not support IRCv3 CHATHISTORY or playback for this target.");

    boolean loadAroundAvailable = isActionVisible(loadAroundActionVisibleSupplier);
    boolean loadAroundEnabled = loadAroundAvailable && hasMessageId;
    loadAroundMessageItem.setEnabled(loadAroundEnabled);
    loadAroundMessageItem.setToolTipText(
        loadAroundEnabled
            ? "Load surrounding history around this IRCv3 message ID."
            : historyActionUnavailableReason(hasMessageId, loadAroundAvailable));
  }

  private void updateMessageActionState(boolean hasMessageId) {
    if (!messageActionsConfigured) return;

    boolean replyAvailable = isActionVisible(replyActionVisibleSupplier);
    boolean replyEnabled = replyAvailable && hasMessageId;
    replyToMessageItem.setEnabled(replyEnabled);
    replyToMessageItem.setToolTipText(
        replyEnabled
            ? "Compose a reply linked to this IRCv3 message ID."
            : messageActionUnavailableReason(hasMessageId, replyAvailable, false, "reply"));

    boolean reactAvailable = isActionVisible(reactActionVisibleSupplier);
    boolean reactEnabled = reactAvailable && hasMessageId;
    reactToMessageItem.setEnabled(reactEnabled);
    reactToMessageItem.setToolTipText(
        reactEnabled
            ? "Send an IRCv3 reaction linked to this message."
            : messageActionUnavailableReason(hasMessageId, reactAvailable, false, "reaction"));

    boolean unreactAvailable = isActionVisible(unreactActionVisibleSupplier);
    boolean unreactEnabled = unreactAvailable && hasMessageId;
    unreactToMessageItem.setEnabled(unreactEnabled);
    unreactToMessageItem.setToolTipText(
        unreactEnabled
            ? "Remove your reaction metadata for this IRCv3 message."
            : messageActionUnavailableReason(
                hasMessageId, unreactAvailable, false, "reaction removal"));

    boolean editAvailable = isActionVisible(editActionVisibleSupplier);
    boolean editEnabled = editAvailable && hasMessageId && currentPopupOwnMessage;
    editMessageItem.setEnabled(editEnabled);
    editMessageItem.setToolTipText(
        editEnabled
            ? "Edit your message via experimental IRC draft/message-edit."
            : messageActionUnavailableReason(
                hasMessageId, editAvailable, currentPopupOwnMessage, "edit"));

    boolean redactAvailable = isActionVisible(redactActionVisibleSupplier);
    boolean redactEnabled = redactAvailable && hasMessageId && currentPopupOwnMessage;
    redactMessageItem.setEnabled(redactEnabled);
    redactMessageItem.setToolTipText(
        redactEnabled
            ? "Redact your message via IRCv3 message-redaction."
            : messageActionUnavailableReason(
                hasMessageId, redactAvailable, currentPopupOwnMessage, "redaction"));

    boolean revealAvailable = isActionVisible(revealRedactedActionVisibleSupplier);
    boolean revealEnabled = revealAvailable && hasMessageId && currentPopupRedactedMessage;
    revealRedactedMessageItem.setEnabled(revealEnabled);
    revealRedactedMessageItem.setToolTipText(
        revealEnabled
            ? "View the stored original content for this redacted message."
            : revealActionUnavailableReason(
                hasMessageId, revealAvailable, currentPopupRedactedMessage));
  }

  private static String historyActionUnavailableReason(boolean hasMessageId, boolean available) {
    if (!hasMessageId) return "Unavailable: this line has no IRCv3 message ID.";
    if (!available) {
      return "Unavailable: server does not support loading context around message IDs.";
    }
    return null;
  }

  private static String messageActionUnavailableReason(
      boolean hasMessageId, boolean available, boolean ownMessage, String actionNoun) {
    if (!hasMessageId) return "Unavailable: this line has no IRCv3 message ID.";
    if (!ownMessage && ("edit".equals(actionNoun) || "redaction".equals(actionNoun))) {
      String verb = "edit".equals(actionNoun) ? "edited" : "redacted";
      return "Unavailable: only your own messages can be " + verb + ".";
    }
    if (!available) {
      if ("reply".equals(actionNoun) || "reaction".equals(actionNoun)) {
        return "Unavailable: server did not negotiate IRCv3 message-tags support.";
      }
      if ("edit".equals(actionNoun)) {
        return "Unavailable: server did not negotiate experimental IRC draft/message-edit support.";
      }
      return "Unavailable: server did not negotiate IRCv3 " + actionNoun + " support.";
    }
    return null;
  }

  private static String revealActionUnavailableReason(
      boolean hasMessageId, boolean available, boolean redactedMessage) {
    if (!hasMessageId) return "Unavailable: this line has no IRCv3 message ID.";
    if (!redactedMessage) return "Unavailable: this line is not marked as redacted.";
    if (!available) return "Unavailable: redacted-message reveal is not configured for this view.";
    return null;
  }

  private void onInspectLine(ActionEvent e) {
    try {
      if (lastPopupPoint != null) {
        ChatLineInspectorDialog.showAtPoint(transcript, transcript, lastPopupPoint);
      } else {
        ChatLineInspectorDialog.showAtPosition(
            transcript, transcript, Math.max(0, transcript.getCaretPosition()));
      }
    } catch (Exception ignored) {
    }
  }

  private void onCopy(ActionEvent e) {
    try {
      transcript.copy();
    } catch (Exception ignored) {
    }
  }

  private void onCopyMessageId(ActionEvent e) {
    String msgId = currentPopupMessageId;
    if (msgId == null || msgId.isBlank()) return;
    try {
      Toolkit.getDefaultToolkit()
          .getSystemClipboard()
          .setContents(new StringSelection(msgId), null);
    } catch (Exception ignored) {
    }
  }

  private void onCopyIrcv3Tags(ActionEvent e) {
    String tags = currentPopupIrcv3Tags;
    if (tags == null || tags.isBlank()) return;
    try {
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(tags), null);
    } catch (Exception ignored) {
    }
  }

  private void onLoadNewerHistory(ActionEvent e) {
    if (!isActionVisible(loadNewerActionVisibleSupplier)) return;
    try {
      onLoadNewerHistory.run();
    } catch (Exception ignored) {
    }
  }

  private void onLoadContextAroundMessage(ActionEvent e) {
    if (!isActionVisible(loadAroundActionVisibleSupplier)) return;
    String msgId = currentPopupMessageId;
    if (msgId == null || msgId.isBlank()) return;
    try {
      onLoadContextAroundMessage.accept(msgId);
    } catch (Exception ignored) {
    }
  }

  private void onReplyToMessage(ActionEvent e) {
    if (!isActionVisible(replyActionVisibleSupplier)) return;
    String msgId = currentPopupMessageId;
    if (msgId == null || msgId.isBlank()) return;
    try {
      onReplyToMessage.accept(msgId);
    } catch (Exception ignored) {
    }
  }

  private void onReactToMessage(ActionEvent e) {
    if (!isActionVisible(reactActionVisibleSupplier)) return;
    String msgId = currentPopupMessageId;
    if (msgId == null || msgId.isBlank()) return;
    try {
      onReactToMessage.accept(msgId);
    } catch (Exception ignored) {
    }
  }

  private void onUnreactToMessage(ActionEvent e) {
    if (!isActionVisible(unreactActionVisibleSupplier)) return;
    String msgId = currentPopupMessageId;
    if (msgId == null || msgId.isBlank()) return;
    try {
      onUnreactToMessage.accept(msgId);
    } catch (Exception ignored) {
    }
  }

  private void onEditMessage(ActionEvent e) {
    if (!isActionVisible(editActionVisibleSupplier)) return;
    if (!currentPopupOwnMessage) return;
    String msgId = currentPopupMessageId;
    if (msgId == null || msgId.isBlank()) return;
    try {
      onEditMessage.accept(msgId);
    } catch (Exception ignored) {
    }
  }

  private void onRedactMessage(ActionEvent e) {
    if (!isActionVisible(redactActionVisibleSupplier)) return;
    if (!currentPopupOwnMessage) return;
    String msgId = currentPopupMessageId;
    if (msgId == null || msgId.isBlank()) return;
    try {
      onRedactMessage.accept(msgId);
    } catch (Exception ignored) {
    }
  }

  private void onRevealRedactedMessage(ActionEvent e) {
    if (!isActionVisible(revealRedactedActionVisibleSupplier)) return;
    if (!currentPopupRedactedMessage) return;
    String msgId = currentPopupMessageId;
    if (msgId == null || msgId.isBlank()) return;
    try {
      onRevealRedactedMessage.accept(msgId);
    } catch (Exception ignored) {
    }
  }

  private void onOpenLink(ActionEvent e) {
    String url = currentPopupUrl;
    if (url == null || url.isBlank()) return;
    if (openUrl == null) return;
    try {
      openUrl.accept(url);
    } catch (Exception ignored) {
    }
  }

  private void onCopyLink(ActionEvent e) {
    String url = currentPopupUrl;
    if (url == null || url.isBlank()) return;
    try {
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
    } catch (Exception ignored) {
    }
  }

  private void onSaveLink(ActionEvent e) {
    String url = currentPopupUrl;
    if (url == null || url.isBlank()) return;

    try {
      String suggested = suggestFileName(url);
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Save Link As...");
      chooser.setSelectedFile(new java.io.File(suggested));
      int result = chooser.showSaveDialog(transcript);
      if (result != JFileChooser.APPROVE_OPTION) return;

      Path out = chooser.getSelectedFile().toPath();
      if (Files.exists(out)) {
        int overwrite =
            JOptionPane.showConfirmDialog(
                transcript,
                "File already exists. Overwrite?\n\n" + out,
                "Overwrite file?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (overwrite != JOptionPane.YES_OPTION) return;
      }

      // Download off the EDT.
      VirtualThreads.start("ircafe-save-link", () -> downloadToFile(url, out));
    } catch (Exception ignored) {
    }
  }

  private void onSelectAll(ActionEvent e) {
    try {
      transcript.requestFocusInWindow();
      transcript.selectAll();
    } catch (Exception ignored) {
    }
  }

  private void onFind(ActionEvent e) {
    try {
      openFind.run();
    } catch (Exception ignored) {
    }
  }

  private void onReloadRecent(ActionEvent e) {
    // Reload recent history is intentionally implemented as: clear buffer first, then reload.
    try {
      clearBufferOnly();
    } catch (Exception ignored) {
    }

    try {
      Runnable reload = reloadRecentAction;
      if (reload != null) reload.run();
    } catch (Exception ignored) {
    }
  }

  private void onClear(ActionEvent e) {
    try {
      clearBufferOnly();
    } catch (Exception ignored) {
    }
  }

  private void clearBufferOnly() throws Exception {
    Runnable r = clearAction;
    if (r != null) {
      r.run();
      return;
    }
    Document doc = transcript.getDocument();
    if (doc == null) return;
    int len = doc.getLength();
    if (len <= 0) return;
    // Clear only the in-memory transcript buffer (the UI document).
    // This intentionally does not touch any persisted logs.
    doc.remove(0, len);
  }

  private static String safeHit(Function<Point, String> f, Point p) {
    if (f == null || p == null) return null;
    try {
      return f.apply(p);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean isActionVisible(Supplier<Boolean> visibleSupplier) {
    if (visibleSupplier == null) return false;
    try {
      return Boolean.TRUE.equals(visibleSupplier.get());
    } catch (Exception ignored) {
      return false;
    }
  }

  static LineIdentity lineIdentityFromAttributes(AttributeSet attrs) {
    if (attrs == null) return LineIdentity.EMPTY;
    String msgId = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_MSGID), "").trim();
    String tags = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_IRCV3_TAGS), "").trim();
    boolean outgoing = Boolean.TRUE.equals(attrs.getAttribute(ChatStyles.ATTR_OUTGOING));
    boolean redacted = Boolean.TRUE.equals(attrs.getAttribute(ChatStyles.ATTR_META_REDACTED));
    if (!outgoing) {
      String dir = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_DIRECTION), "").trim();
      outgoing = "OUT".equalsIgnoreCase(dir);
    }
    if (msgId.isBlank() && tags.isBlank() && !outgoing && !redacted) return LineIdentity.EMPTY;
    return new LineIdentity(msgId, tags, outgoing, redacted);
  }

  private LineIdentity lineIdentityAtPoint(Point p) {
    if (p == null) return LineIdentity.EMPTY;
    try {
      int pos = transcript.viewToModel2D(p);
      Document doc = transcript.getDocument();
      if (!(doc instanceof StyledDocument sdoc)) return LineIdentity.EMPTY;
      int len = doc.getLength();
      if (len <= 0) return LineIdentity.EMPTY;
      int safePos = Math.max(0, Math.min(pos, len - 1));
      Element el = sdoc.getCharacterElement(safePos);
      if (el == null) return LineIdentity.EMPTY;
      LineIdentity identity = lineIdentityFromAttributes(el.getAttributes());
      if (identity.redactedMessage()) {
        return identity;
      }
      return new LineIdentity(
          identity.messageId(),
          identity.ircv3Tags(),
          identity.outgoingOwnMessage(),
          paragraphLooksRedacted(doc, safePos));
    } catch (Exception ignored) {
      return LineIdentity.EMPTY;
    }
  }

  private static boolean paragraphLooksRedacted(Document doc, int pos) {
    if (doc == null || pos < 0) return false;
    try {
      Element root = doc.getDefaultRootElement();
      int idx = root.getElementIndex(pos);
      Element para = root.getElement(idx);
      if (para == null) return false;
      int start = para.getStartOffset();
      int end = para.getEndOffset();
      int len = Math.max(0, end - start);
      if (len <= 0) return false;
      String text = doc.getText(start, len);
      return text != null && text.contains("[message redacted]");
    } catch (Exception ignored) {
      return false;
    }
  }

  static final class LineIdentity {
    static final LineIdentity EMPTY = new LineIdentity("", "", false, false);

    private final String messageId;
    private final String ircv3Tags;
    private final boolean outgoingOwnMessage;
    private final boolean redactedMessage;

    LineIdentity(
        String messageId, String ircv3Tags, boolean outgoingOwnMessage, boolean redactedMessage) {
      this.messageId = Objects.toString(messageId, "").trim();
      this.ircv3Tags = Objects.toString(ircv3Tags, "").trim();
      this.outgoingOwnMessage = outgoingOwnMessage;
      this.redactedMessage = redactedMessage;
    }

    String messageId() {
      return messageId;
    }

    String ircv3Tags() {
      return ircv3Tags;
    }

    boolean outgoingOwnMessage() {
      return outgoingOwnMessage;
    }

    boolean redactedMessage() {
      return redactedMessage;
    }
  }

  private static String suggestFileName(String url) {
    try {
      URI u = URI.create(url);
      String path = u.getPath();
      if (path == null || path.isBlank()) return "download";
      int slash = path.lastIndexOf('/');
      String name = (slash >= 0) ? path.substring(slash + 1) : path;
      if (name == null || name.isBlank()) return "download";
      // Very small sanitation: avoid OS-path separators.
      name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
      return name.isBlank() ? "download" : name;
    } catch (Exception ignored) {
      return "download";
    }
  }

  private void downloadToFile(String url, Path out) {
    try {
      URI uri = URI.create(url);

      // Use HttpURLConnection (via HttpLite) so SOCKS proxies work.
      // java.net.http.HttpClient does not support SOCKS proxies.
      ProxyPlan plan = null;
      try {
        if (proxyPlanSupplier != null) {
          plan = proxyPlanSupplier.get();
        }
      } catch (Exception ignored) {
      }

      if (plan == null) {
        plan = ProxyPlan.from(NetProxyContext.settings());
      }
      Proxy proxy = (plan.proxy() != null) ? plan.proxy() : Proxy.NO_PROXY;

      Map<String, String> headers = new HashMap<>();
      headers.put("User-Agent", DEFAULT_UA);
      headers.put("Accept", "*/*");
      headers.put("Accept-Language", "en-US,en;q=0.9");

      int connectTimeoutMs = Math.max(1, plan.connectTimeoutMs());
      int readTimeoutMs = Math.max(Math.max(1, plan.readTimeoutMs()), 120_000);

      HttpLite.Response<InputStream> resp =
          HttpLite.getStream(uri, headers, proxy, connectTimeoutMs, readTimeoutMs);
      int code = resp.statusCode();

      // Second attempt: some hosts require a plausible Referer (anti-hotlinking).
      if (code == 403 && uri.getHost() != null) {
        try (InputStream ignored = resp.body()) {
          // ensure we don't leak the first connection
        }
        String origin = uri.getScheme() + "://" + uri.getHost() + "/";
        headers.put("Referer", origin);
        resp = HttpLite.getStream(uri, headers, proxy, connectTimeoutMs, readTimeoutMs);
        code = resp.statusCode();
      }

      if (code < 200 || code >= 300) {
        try (InputStream ignored = resp.body()) {
          // ensure we don't leak the connection
        }
        int finalCode = code;
        SwingUtilities.invokeLater(
            () ->
                JOptionPane.showMessageDialog(
                    transcript,
                    "Failed to download link (HTTP "
                        + finalCode
                        + "):\n\n"
                        + url
                        + "\n\nNote: Some sites block direct downloads from apps unless you\n"
                        + "are signed in (cookies) or they require special headers.\n"
                        + "If this keeps happening, use 'Open Link in Browser' then Save As there.",
                    "Save Link As...",
                    JOptionPane.ERROR_MESSAGE));
        return;
      }

      Path parent = out.getParent();
      if (parent != null) Files.createDirectories(parent);

      try (InputStream in = resp.body()) {
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
      }

      SwingUtilities.invokeLater(
          () ->
              JOptionPane.showMessageDialog(
                  transcript,
                  "Saved to:\n\n" + out,
                  "Save Link As...",
                  JOptionPane.INFORMATION_MESSAGE));
    } catch (Exception ex) {
      SwingUtilities.invokeLater(
          () ->
              JOptionPane.showMessageDialog(
                  transcript,
                  "Failed to download link:\n\n" + url + "\n\n" + ex.getMessage(),
                  "Save Link As...",
                  JOptionPane.ERROR_MESSAGE));
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    try {
      transcript.removeMouseListener(mouse);
    } catch (Exception ignored) {
    }
  }
}
