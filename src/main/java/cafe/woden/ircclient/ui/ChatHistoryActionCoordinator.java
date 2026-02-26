package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Owns transcript context-action capability checks and history command flows. */
final class ChatHistoryActionCoordinator {

  private final IrcClientService irc;
  private final ChatHistoryService chatHistoryService;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final Consumer<TargetRef> activateTarget;
  private final Runnable activateInputSurface;
  private final Runnable focusInput;
  private final Runnable armTailPinOnNextAppendIfAtBottom;
  private final Consumer<String> outboundEmitter;
  private final BiConsumer<String, String> beginReplyCompose;
  private final BiConsumer<String, String> openQuickReactionPicker;
  private final Consumer<String> setDraftText;
  private final Supplier<String> historyLatestCommandSupplier;
  private final Function<String, String> historyAroundByMessageIdCommandBuilder;

  ChatHistoryActionCoordinator(
      IrcClientService irc,
      ChatHistoryService chatHistoryService,
      Supplier<TargetRef> activeTargetSupplier,
      Consumer<TargetRef> activateTarget,
      Runnable activateInputSurface,
      Runnable focusInput,
      Runnable armTailPinOnNextAppendIfAtBottom,
      Consumer<String> outboundEmitter,
      BiConsumer<String, String> beginReplyCompose,
      BiConsumer<String, String> openQuickReactionPicker,
      Consumer<String> setDraftText,
      Supplier<String> historyLatestCommandSupplier,
      Function<String, String> historyAroundByMessageIdCommandBuilder) {
    this.irc = irc;
    this.chatHistoryService = chatHistoryService;
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.activateTarget = Objects.requireNonNull(activateTarget, "activateTarget");
    this.activateInputSurface =
        Objects.requireNonNull(activateInputSurface, "activateInputSurface");
    this.focusInput = Objects.requireNonNull(focusInput, "focusInput");
    this.armTailPinOnNextAppendIfAtBottom =
        Objects.requireNonNull(
            armTailPinOnNextAppendIfAtBottom, "armTailPinOnNextAppendIfAtBottom");
    this.outboundEmitter = Objects.requireNonNull(outboundEmitter, "outboundEmitter");
    this.beginReplyCompose = Objects.requireNonNull(beginReplyCompose, "beginReplyCompose");
    this.openQuickReactionPicker =
        Objects.requireNonNull(openQuickReactionPicker, "openQuickReactionPicker");
    this.setDraftText = Objects.requireNonNull(setDraftText, "setDraftText");
    this.historyLatestCommandSupplier =
        Objects.requireNonNull(historyLatestCommandSupplier, "historyLatestCommandSupplier");
    this.historyAroundByMessageIdCommandBuilder =
        Objects.requireNonNull(
            historyAroundByMessageIdCommandBuilder, "historyAroundByMessageIdCommandBuilder");
  }

  boolean replyContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null || irc == null) return false;
    try {
      return irc.isDraftReplyAvailable(target.serverId());
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean reactContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null || irc == null) return false;
    try {
      return irc.isDraftReactAvailable(target.serverId());
    } catch (Exception ignored) {
      return false;
    }
  }

  boolean editContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return isMessageEditSupportedForServer(target.serverId());
  }

  boolean redactContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return isMessageRedactionSupportedForServer(target.serverId());
  }

  boolean loadNewerHistoryContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return isLoadNewerHistorySupportedForServer(target.serverId());
  }

  boolean loadAroundMessageContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return isChatHistorySupportedForServer(target.serverId());
  }

  void onLoadNewerHistoryRequested() {
    if (!loadNewerHistoryContextActionVisible()) return;
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (isChatHistorySupportedForServer(target.serverId())) {
      emitHistoryCommand(historyLatestCommandSupplier.get());
      return;
    }
    if (chatHistoryService != null && chatHistoryService.canReloadRecent(target)) {
      chatHistoryService.reloadRecent(target);
    }
  }

  void onLoadContextAroundMessageRequested(String messageId) {
    if (!requestHistoryAroundMessage(messageId)) return;
  }

  boolean requestHistoryAroundMessage(String messageId) {
    if (!loadAroundMessageContextActionVisible()) return false;
    String line = historyAroundByMessageIdCommandBuilder.apply(messageId);
    if (Objects.toString(line, "").isBlank()) return false;
    emitHistoryCommand(line);
    return true;
  }

  void onReplyToMessageRequested(String messageId) {
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (!replyContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    activateInputForTarget(target);
    beginReplyCompose.accept(target.target(), msgId);
    focusInput.run();
  }

  void onReactToMessageRequested(String messageId) {
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (!reactContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    activateInputForTarget(target);
    openQuickReactionPicker.accept(target.target(), msgId);
    focusInput.run();
  }

  void onEditMessageRequested(String messageId) {
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (!editContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    activateInputForTarget(target);
    setDraftText.accept("/edit " + msgId + " ");
    focusInput.run();
  }

  void onRedactMessageRequested(String messageId) {
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (!redactContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    emitHistoryCommand("/redact " + msgId);
  }

  void emitHistoryCommand(String line) {
    String cmd = Objects.toString(line, "").trim();
    if (cmd.isEmpty()) return;
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    activateInputForTarget(target);
    armTailPinOnNextAppendIfAtBottom.run();
    outboundEmitter.accept(cmd);
  }

  private void activateInputForTarget(TargetRef target) {
    activateTarget.accept(target);
    activateInputSurface.run();
  }

  private TargetRef activeMessageTarget() {
    TargetRef target = activeTargetSupplier.get();
    if (target == null || target.isStatus() || target.isUiOnly()) return null;
    return target;
  }

  private boolean isMessageEditSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isMessageEditAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isMessageRedactionSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isMessageRedactionAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isLoadNewerHistorySupportedForServer(String serverId) {
    return isChatHistorySupportedForServer(serverId) || isZncPlaybackSupportedForServer(serverId);
  }

  private boolean isChatHistorySupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isChatHistoryAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isZncPlaybackSupportedForServer(String serverId) {
    if (irc == null) return false;
    try {
      return irc.isZncPlaybackAvailable(serverId);
    } catch (Exception ignored) {
      return false;
    }
  }
}
