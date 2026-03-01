package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;

/** Coordinates tree typing-activity node state and timer-driven animation cleanup. */
public final class ServerTreeTypingActivityManager {

  public interface Context {
    boolean supportsTypingActivity(TargetRef ref);

    boolean typingIndicatorsEnabled();

    boolean uiShowing();

    void repaintTreeNode(DefaultMutableTreeNode node);
  }

  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Set<DefaultMutableTreeNode> typingActivityNodes;
  private final Timer typingActivityTimer;
  private final int typingActivityHoldMs;
  private final int typingActivityFadeMs;
  private final Context context;

  public ServerTreeTypingActivityManager(
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Set<DefaultMutableTreeNode> typingActivityNodes,
      Timer typingActivityTimer,
      int typingActivityHoldMs,
      int typingActivityFadeMs,
      Context context) {
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.typingActivityNodes = Objects.requireNonNull(typingActivityNodes, "typingActivityNodes");
    this.typingActivityTimer = Objects.requireNonNull(typingActivityTimer, "typingActivityTimer");
    this.typingActivityHoldMs = typingActivityHoldMs;
    this.typingActivityFadeMs = typingActivityFadeMs;
    this.context = Objects.requireNonNull(context, "context");
  }

  public void markTypingActivity(TargetRef ref, String state) {
    if (!context.supportsTypingActivity(ref)) return;
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return;

    if (!context.typingIndicatorsEnabled()) {
      clearNodeTypingState(node, nodeData);
      return;
    }

    if (nodeData.detached) {
      clearNodeTypingState(node, nodeData);
      return;
    }

    long now = System.currentTimeMillis();
    boolean changed = nodeData.applyTypingState(state, now, typingActivityHoldMs);
    if (changed) {
      context.repaintTreeNode(node);
    }
    if (nodeData.hasTypingActivity()) {
      typingActivityNodes.add(node);
      startTimerIfNeeded();
      return;
    }
    typingActivityNodes.remove(node);
    if (typingActivityNodes.isEmpty()) {
      typingActivityTimer.stop();
    }
  }

  public void onTypingActivityAnimationTick() {
    if (!context.uiShowing()) {
      typingActivityTimer.stop();
      return;
    }

    long now = System.currentTimeMillis();
    ArrayList<DefaultMutableTreeNode> repaintNodes = new ArrayList<>();

    Iterator<DefaultMutableTreeNode> iterator = typingActivityNodes.iterator();
    while (iterator.hasNext()) {
      DefaultMutableTreeNode node = iterator.next();
      if (node == null || node.getParent() == null) {
        iterator.remove();
        continue;
      }
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ServerTreeNodeData nodeData)) {
        iterator.remove();
        continue;
      }
      if (!nodeData.hasTypingActivity()) {
        iterator.remove();
        continue;
      }

      boolean hadTyping = nodeData.hasTypingActivity();
      nodeData.clearTypingActivityIfExpired(now, typingActivityFadeMs);
      if (!nodeData.hasTypingActivity()) {
        iterator.remove();
      }
      if (hadTyping) {
        repaintNodes.add(node);
      }
    }

    if (typingActivityNodes.isEmpty()) {
      typingActivityTimer.stop();
    }

    for (DefaultMutableTreeNode node : repaintNodes) {
      context.repaintTreeNode(node);
    }
  }

  public void clearTypingIndicatorsFromTree() {
    if (typingActivityNodes.isEmpty()) return;
    ArrayList<DefaultMutableTreeNode> changedNodes = new ArrayList<>();
    for (DefaultMutableTreeNode node : typingActivityNodes) {
      if (node == null) continue;
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ServerTreeNodeData nodeData)) continue;
      if (nodeData.clearTypingActivityNow()) {
        changedNodes.add(node);
      }
    }
    typingActivityNodes.clear();
    typingActivityTimer.stop();
    for (DefaultMutableTreeNode node : changedNodes) {
      context.repaintTreeNode(node);
    }
  }

  private void startTimerIfNeeded() {
    if (typingActivityNodes.isEmpty()) return;
    if (!context.uiShowing()) return;
    if (!typingActivityTimer.isRunning()) {
      typingActivityTimer.start();
    }
  }

  public void startTypingActivityTimerIfNeeded() {
    startTimerIfNeeded();
  }

  private void clearNodeTypingState(DefaultMutableTreeNode node, ServerTreeNodeData nodeData) {
    boolean changed = nodeData.clearTypingActivityNow();
    typingActivityNodes.remove(node);
    if (typingActivityNodes.isEmpty()) {
      typingActivityTimer.stop();
    }
    if (changed) {
      context.repaintTreeNode(node);
    }
  }
}
