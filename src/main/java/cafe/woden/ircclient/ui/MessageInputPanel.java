package cafe.woden.ircclient.ui;
import cafe.woden.ircclient.irc.Ircv3DraftNormalizer;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import java.awt.*;
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
public class MessageInputPanel extends JPanel {
  private static final Logger log = LoggerFactory.getLogger(MessageInputPanel.class);
  public static final String ID = "input";
  private final JTextField input = new JTextField();
  private final JButton send = new JButton("Send");

  private final JPanel typingBanner = new JPanel(new BorderLayout());
  private final JLabel typingBannerLabel = new JLabel();
  private final TypingDotsIndicator typingDotsIndicator = new TypingDotsIndicator();
  private final TypingSignalIndicator typingSignalIndicator = new TypingSignalIndicator();
  private final MessageInputUndoSupport undoSupport;
  private final MessageInputNickCompletionSupport nickCompletionSupport;
  private final MessageInputHintPopupSupport hintPopupSupport;
  private final MessageInputContextMenuSupport contextMenuSupport;
  private final MessageInputComposeSupport composeSupport;
  private boolean programmaticEdit;
  private final FlowableProcessor<String> outbound = PublishProcessor.<String>create().toSerialized();
  private volatile Runnable onActivated = () -> {};
  private volatile Consumer<String> onDraftChanged = t -> {};
  private final MessageInputTypingSupport typingSupport;
  private final UiSettingsBus settingsBus;
  private final MessageInputHistorySupport historySupport;
  private final PropertyChangeListener settingsListener = this::onSettingsChanged;
  public MessageInputPanel(UiSettingsBus settingsBus, CommandHistoryStore historyStore) {
    super(new BorderLayout(8, 0));
    this.settingsBus = settingsBus;

    this.undoSupport = new MessageInputUndoSupport(input, () -> programmaticEdit);
    this.nickCompletionSupport = new MessageInputNickCompletionSupport(this, input, this.undoSupport);
    this.hintPopupSupport = new MessageInputHintPopupSupport(this, input, nickCompletionSupport::firstNickStartingWith);

    MessageInputUiHooks hooks = new MessageInputUiHooks() {
      @Override public void updateHint() {
        hintPopupSupport.updateHint();
      }

      @Override public void markCompletionUiDirty() {
        nickCompletionSupport.markUiDirty();
      }

      @Override public void runProgrammaticEdit(Runnable r) {
        MessageInputPanel.this.runProgrammaticEdit(r);
      }

      @Override public void focusInput() {
        MessageInputPanel.this.focusInput();
      }

      @Override public void flushTypingDone() {
        MessageInputPanel.this.flushTypingDone();
      }

      @Override public void fireDraftChanged() {
        MessageInputPanel.this.fireDraftChanged();
      }

      @Override public void sendOutbound(String line) {
        if (line == null || line.isBlank()) return;
        outbound.onNext(line);
      }
    };

    this.typingSupport = new MessageInputTypingSupport(
        input,
        typingBanner,
        typingBannerLabel,
        typingDotsIndicator,
        typingSignalIndicator,
        settingsBus::get,
        hooks
    );
    this.historySupport = new MessageInputHistorySupport(
        input,
        historyStore,
        nickCompletionSupport.getAutoCompletion(),
        undoSupport,
        hooks
    );

    this.contextMenuSupport = new MessageInputContextMenuSupport(input, undoSupport, historySupport);

    this.composeSupport = new MessageInputComposeSupport(
        this,
        this,
        input,
        send,
        hooks
    );

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
    JPanel right = new JPanel(new BorderLayout(0, 0));
    right.setOpaque(false);
    right.add(send, BorderLayout.CENTER);

    configureTypingBanner();
    configureTypingSignalIndicator();
    right.add(typingSignalIndicator, BorderLayout.SOUTH);

    JPanel center = new JPanel(new BorderLayout(0, 2));
    center.setOpaque(false);

    JPanel bannerStack = new JPanel();
    bannerStack.setOpaque(false);
    bannerStack.setLayout(new BoxLayout(bannerStack, BoxLayout.Y_AXIS));
    bannerStack.add(composeSupport.banner());
    bannerStack.add(typingBanner);

    center.add(bannerStack, BorderLayout.NORTH);
    center.add(input, BorderLayout.CENTER);

    add(center, BorderLayout.CENTER);
    add(right, BorderLayout.EAST);
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

  private void configureTypingSignalIndicator() {
    typingSignalIndicator.setVisible(false);
    typingSignalIndicator.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
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
    input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onDraftDocumentChanged(); }
      @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onDraftDocumentChanged(); }
      @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onDraftDocumentChanged(); }
    });

    input.getDocument().addUndoableEditListener(e -> undoSupport.handleUndoableEdit(e.getEdit()));
  }

  private void installSendActions() {
    input.addActionListener(e -> emit());
    send.addActionListener(e -> emit());
  }

  private void installActivationListeners() {
    // Mark this input surface as "active" when the user interacts with it.
    FocusAdapter focusAdapter = new FocusAdapter() {
      @Override public void focusGained(FocusEvent e) {
        fireActivated();
        hintPopupSupport.updateHint();
        nickCompletionSupport.markUiDirty();
      }
      @Override public void focusLost(FocusEvent e) {
        undoSupport.endCompoundEdit();
        flushTypingDone();
        hintPopupSupport.updateHint();
        nickCompletionSupport.markUiDirty();
      }
    };
    input.addFocusListener(focusAdapter);
    send.addFocusListener(focusAdapter);

    MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) { undoSupport.endCompoundEdit(); fireActivated(); }
    };
    input.addMouseListener(mouseAdapter);
    send.addMouseListener(mouseAdapter);
    addMouseListener(mouseAdapter);
  }

  
private void installEscapeHandler() {
  // Swing key bindings (InputMap/ActionMap) instead of a KeyListener:
  //  - end the current undo group when the user navigates the caret (left/right/home/end)
  //  - ESC cancels history-browse mode (but otherwise falls through to other handlers like AutoCompletion)
  ActionMap am = input.getActionMap();
  InputMap im = input.getInputMap(JComponent.WHEN_FOCUSED);

  am.put("ircafe.endUndoGroup", new AbstractAction() {
    @Override public void actionPerformed(ActionEvent e) {
      undoSupport.endCompoundEdit();
    }
  });

  // Bind on KEY RELEASE so we don't steal the normal caret-navigation actions from the text field.
  int[] navKeys = { KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_HOME, KeyEvent.VK_END };
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

  am.put("ircafe.cancelHistoryBrowse", new AbstractAction() {
    @Override public void actionPerformed(ActionEvent e) {
      if (!historySupport.isBrowsing()) return;
      historySupport.exitBrowse(true);
    }
  });
  // Also bind on release so other ESC handlers (e.g. completion popup dismissal) still run when not browsing.
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
    am.put("ircafe.filterToggleGlobal", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        outbound.onNext("/filter toggle");
      }
    });
    am.put("ircafe.filterToggleBuffer", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        outbound.onNext("/filter toggle @");
      }
    });

    // On US keyboards, the "+=" key is VK_EQUALS (with Shift producing '+').
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.ALT_DOWN_MASK), "ircafe.filterToggleGlobal");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "ircafe.filterToggleGlobal");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.ALT_DOWN_MASK), "ircafe.filterToggleBuffer");
  }



  @Override
  public void addNotify() {
    super.addNotify();
    if (settingsBus != null) settingsBus.addListener(settingsListener);
    hintPopupSupport.updateHint();
    nickCompletionSupport.onAddNotify();
  }
  @Override
  public void removeNotify() {
    hintPopupSupport.hide();
    typingSupport.onRemoveNotify();
    if (settingsBus != null) settingsBus.removeListener(settingsListener);
    super.removeNotify();
  }


  private void onSettingsChanged(PropertyChangeEvent evt) {
    if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
    if (evt.getNewValue() instanceof UiSettings s) {
      applySettings(s);
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
    nickCompletionSupport.setNickCompletions(nicks);
    hintPopupSupport.updateHint();
  }

  private void onDraftDocumentChanged() {
    hintPopupSupport.updateHint();
    nickCompletionSupport.markUiDirty();
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


  /**
   * Called when this input becomes the active typing surface (focus or click).
   */
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



  public void showRemoteTypingIndicator(String nick, String state) {
    typingSupport.showRemoteTypingIndicator(nick, state);
  }

  public void clearRemoteTypingIndicator() {
    typingSupport.clearRemoteTypingIndicator();
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
  public boolean normalizeIrcv3DraftForCapabilities(boolean replySupported, boolean reactSupported) {
    boolean changed = false;
    String before = getDraftText();
    String after = Ircv3DraftNormalizer.normalizeIrcv3DraftForCapabilities(before, replySupported, reactSupported);
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

  public static String normalizeIrcv3DraftForCapabilities(String draft, boolean replySupported, boolean reactSupported) {
    return Ircv3DraftNormalizer.normalizeIrcv3DraftForCapabilities(draft, replySupported, reactSupported);
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
  }
  public String getDraftText() {
    return historySupport.getDraftText();
  }

  public void setDraftText(String text) {
    typingSupport.onDraftTextSetProgrammatically();
    composeSupport.onDraftTextSetProgrammatically();
    historySupport.setDraftText(text);
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
}
