package cafe.woden.ircclient.ui.input;

import cafe.woden.ircclient.irc.Ircv3DraftNormalizer;
import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.settings.SpellcheckSettings;
import cafe.woden.ircclient.ui.settings.SpellcheckSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageInputPanel extends JPanel {
  private static final Logger log = LoggerFactory.getLogger(MessageInputPanel.class);
  public static final String ID = "input";
  private final JTextField input = new JTextField();
  private final JButton send = new JButton();

  private final JPanel typingBanner = new JPanel(new BorderLayout());
  private final JLabel typingBannerLabel = new JLabel();
  private final TypingDotsIndicator typingDotsIndicator = new TypingDotsIndicator();
  private final TypingSignalIndicator typingSignalIndicator = new TypingSignalIndicator();
  private final MessageInputUndoSupport undoSupport;
  private final MessageInputSpellcheckSupport spellcheckSupport;
  private final MessageInputNickCompletionSupport nickCompletionSupport;
  private final MessageInputHintPopupSupport hintPopupSupport;
  private final MessageInputContextMenuSupport contextMenuSupport;
  private final MessageInputComposeSupport composeSupport;
  private boolean programmaticEdit;
  private final FlowableProcessor<String> outbound =
      PublishProcessor.<String>create().toSerialized();
  private volatile Runnable onActivated = () -> {};
  private volatile Consumer<String> onDraftChanged = t -> {};
  private final MessageInputTypingSupport typingSupport;
  private final UiSettingsBus settingsBus;
  private final SpellcheckSettingsBus spellcheckSettingsBus;
  private final MessageInputHistorySupport historySupport;
  private final PropertyChangeListener settingsListener = this::onSettingsChanged;
  private final PropertyChangeListener spellcheckSettingsListener = this::onSpellcheckChanged;
  private boolean shutdown;

  public MessageInputPanel(UiSettingsBus settingsBus, CommandHistoryStore historyStore) {
    this(settingsBus, historyStore, null);
  }

  public MessageInputPanel(
      UiSettingsBus settingsBus,
      CommandHistoryStore historyStore,
      SpellcheckSettingsBus spellcheckSettingsBus) {
    super(new BorderLayout(0, 0));
    this.settingsBus = settingsBus;
    this.spellcheckSettingsBus = spellcheckSettingsBus;

    this.undoSupport = new MessageInputUndoSupport(input, () -> programmaticEdit);
    SpellcheckSettings spellcheck =
        spellcheckSettingsBus != null ? spellcheckSettingsBus.get() : SpellcheckSettings.defaults();
    this.spellcheckSupport = new MessageInputSpellcheckSupport(input, spellcheck);
    this.nickCompletionSupport =
        new MessageInputNickCompletionSupport(this, input, this.undoSupport, spellcheckSupport);
    this.hintPopupSupport =
        new MessageInputHintPopupSupport(this, input, nickCompletionSupport::firstCompletionHint);

    MessageInputUiHooks hooks =
        new MessageInputUiHooks() {
          @Override
          public void updateHint() {
            hintPopupSupport.updateHint();
          }

          @Override
          public void markCompletionUiDirty() {
            nickCompletionSupport.markUiDirty();
          }

          @Override
          public void runProgrammaticEdit(Runnable r) {
            MessageInputPanel.this.runProgrammaticEdit(r);
          }

          @Override
          public void focusInput() {
            MessageInputPanel.this.focusInput();
          }

          @Override
          public void flushTypingDone() {
            MessageInputPanel.this.flushTypingDone();
          }

          @Override
          public void fireDraftChanged() {
            MessageInputPanel.this.fireDraftChanged();
          }

          @Override
          public void sendOutbound(String line) {
            if (line == null || line.isBlank()) return;
            outbound.onNext(line);
          }
        };

    this.typingSupport =
        new MessageInputTypingSupport(
            input,
            typingBanner,
            typingBannerLabel,
            typingDotsIndicator,
            typingSignalIndicator,
            settingsBus::get,
            hooks);
    this.historySupport =
        new MessageInputHistorySupport(
            input, historyStore, nickCompletionSupport.getAutoCompletion(), undoSupport, hooks);

    this.contextMenuSupport =
        new MessageInputContextMenuSupport(input, undoSupport, historySupport, spellcheckSupport);

    this.composeSupport = new MessageInputComposeSupport(this, this, input, send, hooks);

    buildLayout();
    installSupports();
    installInputSurface();
    installDraftListeners();
    installSendActions();
    installActivationListeners();
    installEscapeHandler();

    UiSettings initial = settingsBus.get();
    applySettings(initial);
  }

  private void buildLayout() {
    configureTypingBanner();
    configureInputShell();
    configureTypingSignalIndicator();

    JPanel sendOverlay = new JPanel();
    sendOverlay.setOpaque(false);
    sendOverlay.setLayout(new OverlayLayout(sendOverlay));
    sendOverlay.add(send);
    sendOverlay.add(typingSignalIndicator);

    JPanel inputRow = new JPanel(new BorderLayout(0, 0));
    inputRow.setOpaque(false);
    inputRow.add(input, BorderLayout.CENTER);
    inputRow.add(sendOverlay, BorderLayout.EAST);

    JPanel center = new JPanel(new BorderLayout(0, 2));
    center.setOpaque(false);

    JPanel bannerStack = new JPanel();
    bannerStack.setOpaque(false);
    bannerStack.setLayout(new BoxLayout(bannerStack, BoxLayout.Y_AXIS));
    bannerStack.add(composeSupport.banner());
    bannerStack.add(typingBanner);

    center.add(bannerStack, BorderLayout.NORTH);
    center.add(inputRow, BorderLayout.CENTER);

    JPanel shell = new JPanel(new BorderLayout(0, 0));
    shell.setOpaque(true);
    Color textBg = UIManager.getColor("TextField.background");
    if (textBg == null) textBg = input.getBackground();
    if (textBg != null) shell.setBackground(textBg);
    shell.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(resolveInputShellBorderColor(), 1, true),
            BorderFactory.createEmptyBorder(2, 6, 2, 2)));
    shell.add(center, BorderLayout.CENTER);

    add(shell, BorderLayout.CENTER);
  }

  private void configureTypingBanner() {
    typingBanner.setOpaque(false);
    typingBanner.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
    typingBannerLabel.setText("");
    // FlatLaf will render this a bit subtler if available; otherwise it's a normal label.
    typingBannerLabel.putClientProperty("FlatLaf.styleClass", "small");
    typingDotsIndicator.setVisible(false);
    typingDotsIndicator.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    typingBanner.add(typingBannerLabel, BorderLayout.CENTER);
    typingBanner.add(typingDotsIndicator, BorderLayout.EAST);
    typingBanner.setVisible(false);
  }

  private void configureInputShell() {
    input.setOpaque(false);
    input.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));

    send.setName("messageSendButton");
    send.setText("");
    // Arrow visuals are painted by TypingSignalIndicator so all states share one icon design.
    send.setIcon(null);
    send.setDisabledIcon(null);
    send.setToolTipText("Send message");
    if (send.getAccessibleContext() != null) {
      send.getAccessibleContext().setAccessibleName("Send message");
      send.getAccessibleContext().setAccessibleDescription("Send current message");
    }
    send.setOpaque(false);
    send.setContentAreaFilled(false);
    send.setFocusPainted(false);
    send.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, resolveInputDividerColor()),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
    send.setPreferredSize(new Dimension(38, 30));
  }

  private void configureTypingSignalIndicator() {
    typingSignalIndicator.setVisible(true);
    typingSignalIndicator.setAlignmentX(0.5f);
    typingSignalIndicator.setAlignmentY(0.5f);
    typingSignalIndicator.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    send.setAlignmentX(0.5f);
    send.setAlignmentY(0.5f);
  }

  private static Color resolveInputShellBorderColor() {
    Color c = UIManager.getColor("Component.borderColor");
    if (c == null) c = UIManager.getColor("TextField.borderColor");
    if (c == null) c = UIManager.getColor("Separator.foreground");
    if (c == null) c = new Color(0x8B8F95);
    return c;
  }

  private static Color resolveInputDividerColor() {
    Color c = UIManager.getColor("Component.borderColor");
    if (c == null) c = UIManager.getColor("Separator.foreground");
    if (c == null) c = UIManager.getColor("Label.disabledForeground");
    if (c == null) c = new Color(0x9AA0A6);
    return c;
  }

  private void installSupports() {
    hintPopupSupport.installListeners();

    undoSupport.installKeybindings();
    nickCompletionSupport.install();
    installHistoryKeybindings();
  }

  private void installInputSurface() {
    contextMenuSupport.install();
    input.setFocusTraversalKeysEnabled(false);

    input.addCaretListener(e -> hintPopupSupport.updateHint());
  }

  private void installDraftListeners() {
    input
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              @Override
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                onDraftDocumentChanged();
              }

              @Override
              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                onDraftDocumentChanged();
              }

              @Override
              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                onDraftDocumentChanged();
              }
            });

    input.getDocument().addUndoableEditListener(e -> undoSupport.handleUndoableEdit(e.getEdit()));
  }

  private void installSendActions() {
    input.addActionListener(e -> emit());
    send.addActionListener(e -> emit());
  }

  private void installActivationListeners() {
    // Mark this input surface as "active" when the user interacts with it.
    FocusAdapter focusAdapter =
        new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent e) {
            fireActivated();
            hintPopupSupport.updateHint();
            nickCompletionSupport.markUiDirty();
          }

          @Override
          public void focusLost(FocusEvent e) {
            undoSupport.endCompoundEdit();
            flushTypingDone();
            hintPopupSupport.updateHint();
            nickCompletionSupport.markUiDirty();
          }
        };
    input.addFocusListener(focusAdapter);
    send.addFocusListener(focusAdapter);

    MouseAdapter mouseAdapter =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            undoSupport.endCompoundEdit();
            fireActivated();
          }
        };
    input.addMouseListener(mouseAdapter);
    send.addMouseListener(mouseAdapter);
    addMouseListener(mouseAdapter);
  }

  private void installEscapeHandler() {
    // Swing key bindings (InputMap/ActionMap) instead of a KeyListener:
    //  - end the current undo group when the user navigates the caret (left/right/home/end)
    //  - ESC cancels history-browse mode (but otherwise falls through to other handlers like
    // AutoCompletion)
    ActionMap am = input.getActionMap();
    InputMap im = input.getInputMap(JComponent.WHEN_FOCUSED);

    am.put(
        "ircafe.endUndoGroup",
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            undoSupport.endCompoundEdit();
          }
        });

    // Bind on KEY RELEASE so we don't steal the normal caret-navigation actions from the text
    // field.
    int[] navKeys = {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_HOME, KeyEvent.VK_END};
    int[] mods = {
      0,
      java.awt.event.InputEvent.SHIFT_DOWN_MASK,
      java.awt.event.InputEvent.CTRL_DOWN_MASK,
      java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK,
      java.awt.event.InputEvent.ALT_DOWN_MASK,
      java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK
    };
    for (int key : navKeys) {
      for (int mod : mods) {
        im.put(KeyStroke.getKeyStroke(key, mod, true), "ircafe.endUndoGroup");
      }
    }

    am.put(
        "ircafe.cancelHistoryBrowse",
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (!historySupport.isBrowsing()) return;
            historySupport.exitBrowse(true);
          }
        });
    // Also bind on release so other ESC handlers (e.g. completion popup dismissal) still run when
    // not browsing.
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), "ircafe.cancelHistoryBrowse");
  }

  private void runProgrammaticEdit(Runnable r) {
    boolean prev = programmaticEdit;
    programmaticEdit = true;
    try {
      r.run();
    } finally {
      programmaticEdit = prev;
    }
  }

  private void installHistoryKeybindings() {
    historySupport.installKeybindings();

    // WeeChat-style filter toggles:
    //  - Alt+= toggles global filtering (/filter toggle)
    //  - Alt+- toggles filtering for current buffer (/filter toggle @)
    ActionMap am = input.getActionMap();
    InputMap im = input.getInputMap(JComponent.WHEN_FOCUSED);
    am.put(
        "ircafe.filterToggleGlobal",
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            outbound.onNext("/filter toggle");
          }
        });
    am.put(
        "ircafe.filterToggleBuffer",
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            outbound.onNext("/filter toggle @");
          }
        });

    // On US keyboards, the "+=" key is VK_EQUALS (with Shift producing '+').
    im.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.ALT_DOWN_MASK),
        "ircafe.filterToggleGlobal");
    im.put(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_EQUALS, KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
        "ircafe.filterToggleGlobal");
    im.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.ALT_DOWN_MASK),
        "ircafe.filterToggleBuffer");
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (settingsBus != null) settingsBus.addListener(settingsListener);
    if (spellcheckSettingsBus != null)
      spellcheckSettingsBus.addListener(spellcheckSettingsListener);
    hintPopupSupport.updateHint();
    nickCompletionSupport.onAddNotify();
    spellcheckSupport.onDraftChanged();
  }

  @Override
  public void removeNotify() {
    hintPopupSupport.hide();
    typingSupport.onRemoveNotify();
    spellcheckSupport.onRemoveNotify();
    if (spellcheckSettingsBus != null)
      spellcheckSettingsBus.removeListener(spellcheckSettingsListener);
    if (settingsBus != null) settingsBus.removeListener(settingsListener);
    super.removeNotify();
  }

  public void shutdownResources() {
    if (shutdown) return;
    shutdown = true;
    hintPopupSupport.shutdown();
    nickCompletionSupport.shutdown();
    spellcheckSupport.shutdown();
    typingSupport.onRemoveNotify();
    if (spellcheckSettingsBus != null)
      spellcheckSettingsBus.removeListener(spellcheckSettingsListener);
    if (settingsBus != null) settingsBus.removeListener(settingsListener);
  }

  private void onSettingsChanged(PropertyChangeEvent evt) {
    if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
    if (evt.getNewValue() instanceof UiSettings s) {
      applySettings(s);
    }
  }

  private void onSpellcheckChanged(PropertyChangeEvent evt) {
    if (!SpellcheckSettingsBus.PROP_SPELLCHECK_SETTINGS.equals(evt.getPropertyName())) return;
    if (evt.getNewValue() instanceof SpellcheckSettings s) {
      spellcheckSupport.onSettingsApplied(s);
      hintPopupSupport.updateHint();
      nickCompletionSupport.markUiDirty();
    }
  }

  private void applySettings(UiSettings s) {
    if (s == null) return;
    try {
      Font f = new Font(s.chatFontFamily(), Font.PLAIN, s.chatFontSize());
      input.setFont(f);
      send.setFont(f);
      typingBannerLabel.setFont(f.deriveFont(Math.max(10f, f.getSize2D() - 2f)));
      typingDotsIndicator.setFont(typingBannerLabel.getFont());
      typingSignalIndicator.setFont(typingBannerLabel.getFont());
      hintPopupSupport.onAppearanceChanged(f);

      // Mark completion popup UI dirty when appearance changes (e.g., accent sliders).
      // Refresh existing popup windows if present.
      nickCompletionSupport.markUiDirtyAndRefreshAsync();
    } catch (Exception ex) {
      log.warn("[MessageInputPanel] applySettings failed", ex);
    }

    typingSupport.onSettingsApplied(s);
  }

  public void setNickCompletions(List<String> nicks) {
    spellcheckSupport.setNickWhitelist(nicks);
    nickCompletionSupport.setNickCompletions(nicks);
    hintPopupSupport.updateHint();
  }

  private void onDraftDocumentChanged() {
    hintPopupSupport.updateHint();
    nickCompletionSupport.markUiDirty();
    spellcheckSupport.onDraftChanged();
    fireDraftChanged();
    historySupport.onUserEdit(programmaticEdit);
    if (!programmaticEdit) {
      typingSupport.onUserEdit(programmaticEdit);
    }
  }

  public Flowable<String> outboundMessages() {
    return outbound.onBackpressureBuffer();
  }

  private void emit() {
    String msg = input.getText().trim();
    if (msg.isEmpty()) return;

    MessageInputComposeSupport.TransformResult tr = composeSupport.transformOutgoing(msg);
    String outboundLine = tr.outboundLine();
    boolean consumeReplyCompose = tr.consumeReplyCompose();

    flushTypingDone();
    historySupport.addToHistory(msg);
    // Leaving history-browse mode before clearing ensures draft persistence isn't polluted.
    historySupport.clearBrowseState();

    programmaticEdit = true;
    try {
      input.setText("");
    } finally {
      programmaticEdit = false;
    }
    undoSupport.discardAllEdits();

    outbound.onNext(outboundLine);

    if (consumeReplyCompose) {
      composeSupport.clearReplyComposeInternal(false, false);
    }
  }

  public void beginReplyCompose(String ircTarget, String messageId) {
    composeSupport.beginReplyCompose(ircTarget, messageId);
  }

  public void clearReplyCompose() {
    composeSupport.clearReplyCompose();
  }

  public void openQuickReactionPicker(String ircTarget, String messageId) {
    composeSupport.openQuickReactionPicker(ircTarget, messageId);
  }

  /** Called when this input becomes the active typing surface (focus or click). */
  public void setOnActivated(Runnable onActivated) {
    this.onActivated = onActivated == null ? () -> {} : onActivated;
  }

  public void setOnDraftChanged(Consumer<String> onDraftChanged) {
    this.onDraftChanged = onDraftChanged == null ? t -> {} : onDraftChanged;
  }

  public void setOnTypingStateChanged(Consumer<String> onTypingStateChanged) {
    typingSupport.setOnTypingStateChanged(onTypingStateChanged);
  }

  public void flushTypingDone() {
    typingSupport.flushTypingDone();
  }

  public void flushTypingForBufferSwitch() {
    typingSupport.flushTypingForBufferSwitch();
  }

  private void fireDraftChanged() {
    try {
      onDraftChanged.accept(getDraftText());
    } catch (Exception ex) {
      log.warn("[MessageInputPanel] remove LAF listener failed", ex);
    }
  }

  private void fireActivated() {
    try {
      onActivated.run();
    } catch (Exception ex) {
      log.warn("[MessageInputPanel] remove LAF listener failed", ex);
    }
  }

  public boolean showRemoteTypingIndicator(String nick, String state) {
    int beforeHeight = inputAreaHeightPx();
    boolean wasVisible = typingBanner.isVisible();
    typingSupport.showRemoteTypingIndicator(nick, state);
    int afterHeight = inputAreaHeightPx();
    // While typing banners are active, Swing layout can shift asynchronously (banner text / dots),
    // so we treat updates as geometry-affecting to keep transcript tail anchoring reliable.
    return (wasVisible != typingBanner.isVisible())
        || (beforeHeight != afterHeight)
        || wasVisible
        || typingBanner.isVisible();
  }

  public boolean clearRemoteTypingIndicator() {
    int beforeHeight = inputAreaHeightPx();
    boolean wasVisible = typingBanner.isVisible();
    typingSupport.clearRemoteTypingIndicator();
    int afterHeight = inputAreaHeightPx();
    return (wasVisible != typingBanner.isVisible())
        || (beforeHeight != afterHeight)
        || wasVisible
        || typingBanner.isVisible();
  }

  private int inputAreaHeightPx() {
    int h = getHeight();
    if (h > 0) return h;
    Dimension pref = getPreferredSize();
    return pref != null ? Math.max(0, pref.height) : 0;
  }

  public void setTypingSignalAvailable(boolean available) {
    if (SwingUtilities.isEventDispatchThread()) {
      typingSupport.setTypingSignalAvailable(available);
    } else {
      SwingUtilities.invokeLater(() -> typingSupport.setTypingSignalAvailable(available));
    }
  }

  public void onLocalTypingIndicatorSent(String state) {
    if (SwingUtilities.isEventDispatchThread()) {
      typingSupport.onLocalTypingIndicatorSent(state);
    } else {
      SwingUtilities.invokeLater(() -> typingSupport.onLocalTypingIndicatorSent(state));
    }
  }

  /**
   * Normalize staged IRCv3 /quote drafts against currently negotiated capabilities.
   *
   * <p>This exits reply/react prefill modes when the backing capability is disabled mid-session.
   *
   * @return true when the draft text changed.
   */
  public boolean normalizeIrcv3DraftForCapabilities(
      boolean replySupported, boolean reactSupported) {
    boolean changed = false;
    String before = getDraftText();
    String after =
        Ircv3DraftNormalizer.normalizeIrcv3DraftForCapabilities(
            before, replySupported, reactSupported);
    if (!Objects.equals(before, after)) {
      setDraftText(after);
      changed = true;
    }
    if (!replySupported && composeSupport.hasReplyCompose()) {
      composeSupport.clearReplyComposeInternal(false, false);
      changed = true;
    }
    return changed;
  }

  public static String normalizeIrcv3DraftForCapabilities(
      String draft, boolean replySupported, boolean reactSupported) {
    return Ircv3DraftNormalizer.normalizeIrcv3DraftForCapabilities(
        draft, replySupported, reactSupported);
  }

  public void setInputEnabled(boolean enabled) {
    input.setEditable(enabled);
    input.setEnabled(enabled);
    send.setEnabled(enabled);
    if (!enabled) {
      flushTypingDone();
      clearRemoteTypingIndicator();
      composeSupport.onInputDisabled();
    }
    hintPopupSupport.updateHint();
    nickCompletionSupport.markUiDirty();
    spellcheckSupport.onInputEnabledChanged(enabled);
  }

  public String getDraftText() {
    return historySupport.getDraftText();
  }

  public void setDraftText(String text) {
    typingSupport.onDraftTextSetProgrammatically();
    composeSupport.onDraftTextSetProgrammatically();
    historySupport.setDraftText(text);
  }

  public boolean isInputEditable() {
    return input.isEnabled() && input.isEditable();
  }

  public boolean canUndo() {
    if (!isInputEditable()) return false;
    undoSupport.refreshActions();
    Action action = undoSupport.getUndoAction();
    return action != null && action.isEnabled();
  }

  public boolean canRedo() {
    if (!isInputEditable()) return false;
    undoSupport.refreshActions();
    Action action = undoSupport.getRedoAction();
    return action != null && action.isEnabled();
  }

  public boolean undo() {
    if (!canUndo()) return false;
    Action action = undoSupport.getUndoAction();
    if (action == null || !action.isEnabled()) return false;
    action.actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "menuUndo"));
    input.requestFocusInWindow();
    return true;
  }

  public boolean redo() {
    if (!canRedo()) return false;
    Action action = undoSupport.getRedoAction();
    if (action == null || !action.isEnabled()) return false;
    action.actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "menuRedo"));
    input.requestFocusInWindow();
    return true;
  }

  public boolean canCut() {
    return isInputEditable() && hasSelection();
  }

  public boolean cutSelection() {
    if (!canCut()) return false;
    input.cut();
    input.requestFocusInWindow();
    return true;
  }

  public boolean canCopy() {
    return input.isEnabled() && hasSelection();
  }

  public boolean copySelection() {
    if (!canCopy()) return false;
    input.copy();
    input.requestFocusInWindow();
    return true;
  }

  public boolean canPaste() {
    return isInputEditable() && clipboardHasText();
  }

  public boolean pasteFromClipboard() {
    if (!canPaste()) return false;
    input.paste();
    input.requestFocusInWindow();
    return true;
  }

  public boolean canDeleteForward() {
    if (!isInputEditable()) return false;
    if (hasSelection()) return true;
    int caret = input.getCaretPosition();
    return caret < input.getDocument().getLength();
  }

  public boolean deleteForward() {
    if (!canDeleteForward()) return false;
    int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
    int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
    try {
      if (start != end) {
        input.getDocument().remove(start, end - start);
        input.setCaretPosition(start);
      } else {
        int caret = input.getCaretPosition();
        input.getDocument().remove(caret, 1);
      }
      input.requestFocusInWindow();
      return true;
    } catch (BadLocationException ex) {
      return false;
    }
  }

  public boolean canSelectAllInput() {
    if (!input.isEnabled()) return false;
    int len = input.getDocument().getLength();
    if (len <= 0) return false;
    int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
    int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
    return !(start == 0 && end == len);
  }

  public boolean selectAllInput() {
    if (!canSelectAllInput()) return false;
    input.selectAll();
    input.requestFocusInWindow();
    return true;
  }

  public boolean canClearInput() {
    return isInputEditable() && input.getDocument().getLength() > 0;
  }

  public boolean clearInput() {
    if (!canClearInput()) return false;
    historySupport.clearBrowseState();
    input.setText("");
    input.requestFocusInWindow();
    return true;
  }

  public boolean isHistoryMenuEnabled() {
    return historySupport.menuState(isInputEditable()).menuEnabled;
  }

  public boolean canHistoryPrev() {
    return historySupport.menuState(isInputEditable()).canPrev;
  }

  public boolean canHistoryNext() {
    return historySupport.menuState(isInputEditable()).canNext;
  }

  public boolean canClearCommandHistory() {
    return historySupport.menuState(isInputEditable()).canClear;
  }

  public boolean historyPrev() {
    if (!canHistoryPrev()) return false;
    historySupport.browsePrev();
    input.requestFocusInWindow();
    return true;
  }

  public boolean historyNext() {
    if (!canHistoryNext()) return false;
    historySupport.browseNext();
    input.requestFocusInWindow();
    return true;
  }

  public boolean clearCommandHistory() {
    if (!canClearCommandHistory()) return false;
    historySupport.clearHistory();
    input.requestFocusInWindow();
    return true;
  }

  public boolean insertTextAtCaret(String text) {
    String t = Objects.toString(text, "");
    if (t.isEmpty()) return false;
    if (!isInputEditable()) return false;
    input.replaceSelection(t);
    input.requestFocusInWindow();
    return true;
  }

  public boolean insertPrefixOrWrapSelection(String prefix, String suffix) {
    String p = Objects.toString(prefix, "");
    String s = Objects.toString(suffix, "");
    if (p.isEmpty() && s.isEmpty()) return false;
    if (!isInputEditable()) return false;

    int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
    int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
    if (start < end) {
      String selected = Objects.toString(input.getSelectedText(), "");
      input.replaceSelection(p + selected + s);
    } else {
      input.replaceSelection(p);
    }
    input.requestFocusInWindow();
    return true;
  }

  public void focusInput() {
    if (!input.isEnabled()) return;
    input.requestFocusInWindow();
    try {
      input.setCaretPosition(input.getDocument().getLength());
    } catch (Exception ex) {
      log.warn("[MessageInputPanel] remove LAF listener failed", ex);
    }
  }

  private boolean hasSelection() {
    int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
    int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
    return start != end;
  }

  private static boolean clipboardHasText() {
    try {
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      if (clipboard == null) return false;
      Transferable transfer = clipboard.getContents(null);
      return transfer != null && transfer.isDataFlavorSupported(DataFlavor.stringFlavor);
    } catch (Exception ignored) {
      return false;
    }
  }
}
