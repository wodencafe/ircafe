package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.irc.port.IrcTypingPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.ChatDockable;
import cafe.woden.ircclient.ui.input.MessageInputPanel;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns typing-indicator coordination for {@link ChatDockable}. */
public final class ChatTypingCoordinator {

  private static final Logger log = LoggerFactory.getLogger(ChatTypingCoordinator.class);

  private final MessageInputPanel inputPanel;
  private final IrcTypingPort typingPort;
  private final MessageActionCapabilityPolicy messageActionCapabilityPolicy;
  private final Supplier<TargetRef> activeTargetSupplier;
  private final BooleanSupplier transcriptAtBottomSupplier;
  private final Runnable armTailPinOnNextAppendIfAtBottom;
  private final BooleanSupplier followTailSupplier;
  private final Runnable scrollToBottom;
  private final Map<TargetRef, String> draftByTarget;

  // Diagnostics: avoid spamming logs if the server doesn't support typing.
  private final AtomicBoolean typingUnavailableWarned = new AtomicBoolean(false);

  public ChatTypingCoordinator(
      MessageInputPanel inputPanel,
      IrcTypingPort typingPort,
      MessageActionCapabilityPolicy messageActionCapabilityPolicy,
      Supplier<TargetRef> activeTargetSupplier,
      BooleanSupplier transcriptAtBottomSupplier,
      Runnable armTailPinOnNextAppendIfAtBottom,
      BooleanSupplier followTailSupplier,
      Runnable scrollToBottom,
      Map<TargetRef, String> draftByTarget) {
    this.inputPanel = Objects.requireNonNull(inputPanel, "inputPanel");
    this.typingPort = Objects.requireNonNull(typingPort, "typingPort");
    this.messageActionCapabilityPolicy =
        Objects.requireNonNull(messageActionCapabilityPolicy, "messageActionCapabilityPolicy");
    this.activeTargetSupplier =
        Objects.requireNonNull(activeTargetSupplier, "activeTargetSupplier");
    this.transcriptAtBottomSupplier =
        Objects.requireNonNull(transcriptAtBottomSupplier, "transcriptAtBottomSupplier");
    this.armTailPinOnNextAppendIfAtBottom =
        Objects.requireNonNull(
            armTailPinOnNextAppendIfAtBottom, "armTailPinOnNextAppendIfAtBottom");
    this.followTailSupplier = Objects.requireNonNull(followTailSupplier, "followTailSupplier");
    this.scrollToBottom = Objects.requireNonNull(scrollToBottom, "scrollToBottom");
    this.draftByTarget = Objects.requireNonNull(draftByTarget, "draftByTarget");
  }

  public void showTypingIndicator(TargetRef target, String nick, String state) {
    if (target == null || nick == null || nick.isBlank()) return;

    TargetRef activeTarget = activeTargetSupplier.get();
    if (activeTarget == null || !activeTarget.equals(target)) return;

    boolean atBottomBefore = transcriptAtBottomSupplier.getAsBoolean();
    if (atBottomBefore) {
      armTailPinOnNextAppendIfAtBottom.run();
    }
    boolean typingBannerVisibilityChanged = inputPanel.showRemoteTypingIndicator(nick, state);
    repinAfterInputAreaGeometryChange(atBottomBefore, typingBannerVisibilityChanged);
  }

  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    String sid = Objects.toString(serverId, "").trim();
    String cap = Objects.toString(capability, "").trim().toLowerCase(Locale.ROOT);
    if (sid.isEmpty() || cap.isEmpty()) return;

    boolean messageTagsChanged = "message-tags".equals(cap);
    if ("typing".equals(cap) || messageTagsChanged) {
      TargetRef activeTarget = activeTargetSupplier.get();
      if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid)) {
        boolean atBottomBefore = transcriptAtBottomSupplier.getAsBoolean();
        if (atBottomBefore) {
          armTailPinOnNextAppendIfAtBottom.run();
        }
        boolean typingBannerVisibilityChanged = inputPanel.clearRemoteTypingIndicator();
        repinAfterInputAreaGeometryChange(atBottomBefore, typingBannerVisibilityChanged);
        refreshTypingSignalAvailabilityForActiveTarget();
      }
    }

    if (!messageTagsChanged
        && !"draft/reply".equals(cap)
        && !"draft/react".equals(cap)
        && !"draft/unreact".equals(cap)) {
      return;
    }

    boolean replySupported = messageActionCapabilityPolicy.canReply(sid);
    boolean reactSupported = messageActionCapabilityPolicy.canReact(sid);

    TargetRef activeTarget = activeTargetSupplier.get();
    if (activeTarget != null
        && Objects.equals(activeTarget.serverId(), sid)
        && !activeTarget.isUiOnly()) {
      inputPanel.normalizeIrcv3DraftForCapabilities(replySupported, reactSupported);
    }

    ArrayList<TargetRef> targets = new ArrayList<>(draftByTarget.keySet());
    for (TargetRef target : targets) {
      if (target == null || !Objects.equals(target.serverId(), sid)) continue;
      String before = draftByTarget.getOrDefault(target, "");
      String after =
          MessageInputPanel.normalizeIrcv3DraftForCapabilities(
              before, replySupported, reactSupported);
      if (!Objects.equals(before, after)) {
        draftByTarget.put(target, after);
      }
    }
  }

  public void onLocalTypingStateChanged(String state) {
    TargetRef target = activeTargetSupplier.get();
    if (target == null || target.isStatus() || target.isUiOnly()) return;

    boolean typingAvailable = false;
    try {
      typingAvailable = typingPort.isTypingAvailable(target.serverId());
    } catch (Exception ignored) {
    }
    inputPanel.setTypingSignalAvailable(typingAvailable);

    if (!typingAvailable) {
      String normalized = normalizeTypingState(state);
      // Only warn once per session when the user is actively composing.
      if (!"done".equals(normalized) && typingUnavailableWarned.compareAndSet(false, true)) {
        String reason =
            Objects.toString(typingPort.typingAvailabilityReason(target.serverId()), "").trim();
        if (reason.isEmpty()) reason = "not negotiated / not allowed";
        log.info(
            "[{}] typing indicators are enabled, but unavailable on this server ({})",
            target.serverId(),
            reason);
      }
      return;
    }

    typingUnavailableWarned.set(false);
    String normalized = normalizeTypingState(state);
    if (normalized.isEmpty()) return;
    var unused =
        typingPort
            .sendTyping(target.serverId(), target.target(), normalized)
            .subscribe(
                () -> inputPanel.onLocalTypingIndicatorSent(normalized),
                err -> {
                  if (log.isDebugEnabled()) {
                    log.debug(
                        "[{}] typing send failed (target={} state={}): {}",
                        target.serverId(),
                        target.target(),
                        normalized,
                        err.toString());
                  }
                });
  }

  public void refreshTypingSignalAvailabilityForActiveTarget() {
    TargetRef target = activeTargetSupplier.get();
    boolean available = false;
    if (target != null && !target.isStatus() && !target.isUiOnly()) {
      try {
        available = typingPort.isTypingAvailable(target.serverId());
      } catch (Exception ignored) {
      }
    }
    inputPanel.setTypingSignalAvailable(available);
  }

  private void repinAfterInputAreaGeometryChange(
      boolean atBottomBefore, boolean inputAreaChangedHeight) {
    if (!inputAreaChangedHeight) return;
    if (!atBottomBefore && !followTailSupplier.getAsBoolean()) return;
    SwingUtilities.invokeLater(scrollToBottom);
  }

  private static String normalizeTypingState(String state) {
    String normalized = Objects.toString(state, "").trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) return "";
    return switch (normalized) {
      case "active", "composing" -> "active";
      case "paused" -> "paused";
      case "done", "inactive" -> "done";
      default -> "";
    };
  }
}
