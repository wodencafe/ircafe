package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.irc.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.logging.history.ChatHistoryService;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Owns transcript context-action capability checks and history command flows. */
public final class ChatHistoryActionCoordinator {

  @FunctionalInterface
  public interface ReplyComposeStarter {
    void begin(String target, String messageId, String previewText, Runnable jumpAction);
  }

  private final MessageActionCapabilityPolicy capabilityPolicy;
  private final ChatHistoryService chatHistoryService;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final Consumer<TargetRef> activateTarget;
  private final Runnable activateInputSurface;
  private final Runnable focusInput;
  private final Runnable armTailPinOnNextAppendIfAtBottom;
  private final Consumer<String> outboundEmitter;
  private final ReplyComposeStarter beginReplyCompose;
  private final BiConsumer<String, String> openQuickReactionPicker;
  private final Consumer<String> setDraftText;
  private final Supplier<String> historyLatestCommandSupplier;
  private final Function<String, String> historyAroundByMessageIdCommandBuilder;
  private final BiFunction<TargetRef, String, String> messagePreviewLookup;
  private final BiFunction<TargetRef, String, Integer> messageOffsetLookup;
  private final Runnable disableFollowTail;
  private final IntConsumer scrollToTranscriptOffset;

  public ChatHistoryActionCoordinator(
      IrcClientService irc,
      IrcBouncerPlaybackPort bouncerPlayback,
      ChatHistoryService chatHistoryService,
      Supplier<TargetRef> activeTargetSupplier,
      Consumer<TargetRef> activateTarget,
      Runnable activateInputSurface,
      Runnable focusInput,
      Runnable armTailPinOnNextAppendIfAtBottom,
      Consumer<String> outboundEmitter,
      ReplyComposeStarter beginReplyCompose,
      BiConsumer<String, String> openQuickReactionPicker,
      Consumer<String> setDraftText,
      Supplier<String> historyLatestCommandSupplier,
      Function<String, String> historyAroundByMessageIdCommandBuilder,
      BiFunction<TargetRef, String, String> messagePreviewLookup,
      BiFunction<TargetRef, String, Integer> messageOffsetLookup,
      Runnable disableFollowTail,
      IntConsumer scrollToTranscriptOffset) {
    this(
        new IrcMessageActionCapabilityPolicy(irc, bouncerPlayback),
        chatHistoryService,
        activeTargetSupplier,
        activateTarget,
        activateInputSurface,
        focusInput,
        armTailPinOnNextAppendIfAtBottom,
        outboundEmitter,
        beginReplyCompose,
        openQuickReactionPicker,
        setDraftText,
        historyLatestCommandSupplier,
        historyAroundByMessageIdCommandBuilder,
        messagePreviewLookup,
        messageOffsetLookup,
        disableFollowTail,
        scrollToTranscriptOffset);
  }

  public ChatHistoryActionCoordinator(
      MessageActionCapabilityPolicy capabilityPolicy,
      ChatHistoryService chatHistoryService,
      Supplier<TargetRef> activeTargetSupplier,
      Consumer<TargetRef> activateTarget,
      Runnable activateInputSurface,
      Runnable focusInput,
      Runnable armTailPinOnNextAppendIfAtBottom,
      Consumer<String> outboundEmitter,
      ReplyComposeStarter beginReplyCompose,
      BiConsumer<String, String> openQuickReactionPicker,
      Consumer<String> setDraftText,
      Supplier<String> historyLatestCommandSupplier,
      Function<String, String> historyAroundByMessageIdCommandBuilder,
      BiFunction<TargetRef, String, String> messagePreviewLookup,
      BiFunction<TargetRef, String, Integer> messageOffsetLookup,
      Runnable disableFollowTail,
      IntConsumer scrollToTranscriptOffset) {
    this.capabilityPolicy =
        Objects.requireNonNull(capabilityPolicy, "messageActionCapabilityPolicy");
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
    this.messagePreviewLookup =
        Objects.requireNonNull(messagePreviewLookup, "messagePreviewLookup");
    this.messageOffsetLookup = Objects.requireNonNull(messageOffsetLookup, "messageOffsetLookup");
    this.disableFollowTail = Objects.requireNonNull(disableFollowTail, "disableFollowTail");
    this.scrollToTranscriptOffset =
        Objects.requireNonNull(scrollToTranscriptOffset, "scrollToTranscriptOffset");
  }

  public boolean replyContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return capabilityPolicy.canReply(target.serverId());
  }

  public boolean reactContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return capabilityPolicy.canReact(target.serverId());
  }

  public boolean unreactContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return capabilityPolicy.canUnreact(target.serverId());
  }

  public boolean editContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return isMessageEditSupportedForServer(target.serverId());
  }

  public boolean redactContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return isMessageRedactionSupportedForServer(target.serverId());
  }

  public boolean loadNewerHistoryContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return isLoadNewerHistorySupportedForServer(target.serverId());
  }

  public boolean loadAroundMessageContextActionVisible() {
    TargetRef target = activeMessageTarget();
    if (target == null) return false;
    return isChatHistorySupportedForServer(target.serverId());
  }

  public void onLoadNewerHistoryRequested() {
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

  public void onLoadContextAroundMessageRequested(String messageId) {
    if (!requestHistoryAroundMessage(messageId)) return;
  }

  public boolean requestHistoryAroundMessage(String messageId) {
    if (!loadAroundMessageContextActionVisible()) return false;
    String line = historyAroundByMessageIdCommandBuilder.apply(messageId);
    if (Objects.toString(line, "").isBlank()) return false;
    emitHistoryCommand(line);
    return true;
  }

  public void onReplyToMessageRequested(String messageId) {
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (!replyContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    activateInputForTarget(target);
    String preview = Objects.toString(messagePreviewLookup.apply(target, msgId), "").trim();
    Runnable jumpAction = () -> jumpToMessage(target, msgId);
    beginReplyCompose.begin(target.target(), msgId, preview, jumpAction);
    focusInput.run();
  }

  public void onReactToMessageRequested(String messageId) {
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (!reactContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    activateInputForTarget(target);
    openQuickReactionPicker.accept(target.target(), msgId);
    focusInput.run();
  }

  public void onUnreactToMessageRequested(String messageId) {
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (!unreactContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    activateInputForTarget(target);
    setDraftText.accept("/unreact " + msgId + " ");
    focusInput.run();
  }

  public void onEditMessageRequested(String messageId) {
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (!editContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    activateInputForTarget(target);
    setDraftText.accept("/edit " + msgId + " ");
    focusInput.run();
  }

  public void onRedactMessageRequested(String messageId) {
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    if (!redactContextActionVisible()) return;
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return;
    emitHistoryCommand("/redact " + msgId);
  }

  public void emitHistoryCommand(String line) {
    String cmd = Objects.toString(line, "").trim();
    if (cmd.isEmpty()) return;
    TargetRef target = activeMessageTarget();
    if (target == null) return;
    activateInputForTarget(target);
    armTailPinOnNextAppendIfAtBottom.run();
    outboundEmitter.accept(cmd);
  }

  private void jumpToMessage(TargetRef target, String msgId) {
    if (target == null) return;
    int offset;
    try {
      offset = messageOffsetLookup.apply(target, msgId);
    } catch (Exception ignored) {
      offset = -1;
    }
    if (offset >= 0) {
      disableFollowTail.run();
      scrollToTranscriptOffset.accept(offset);
      return;
    }
    requestHistoryAroundMessage(msgId);
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
    return capabilityPolicy.canEdit(serverId);
  }

  private boolean isMessageRedactionSupportedForServer(String serverId) {
    return capabilityPolicy.canRedact(serverId);
  }

  private boolean isLoadNewerHistorySupportedForServer(String serverId) {
    return capabilityPolicy.canLoadNewerHistory(serverId);
  }

  private boolean isChatHistorySupportedForServer(String serverId) {
    return capabilityPolicy.canLoadAroundMessage(serverId);
  }
}
