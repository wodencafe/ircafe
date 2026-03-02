package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTypingActivityManager;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeTypingActivityManager.Context}. */
public final class ServerTreeTypingActivityManagerContextAdapter
    implements ServerTreeTypingActivityManager.Context {

  private final Predicate<TargetRef> supportsTypingActivity;
  private final BooleanSupplier typingIndicatorsEnabled;
  private final BooleanSupplier uiShowing;
  private final Consumer<DefaultMutableTreeNode> repaintTreeNode;

  public ServerTreeTypingActivityManagerContextAdapter(
      Predicate<TargetRef> supportsTypingActivity,
      BooleanSupplier typingIndicatorsEnabled,
      BooleanSupplier uiShowing,
      Consumer<DefaultMutableTreeNode> repaintTreeNode) {
    this.supportsTypingActivity =
        Objects.requireNonNull(supportsTypingActivity, "supportsTypingActivity");
    this.typingIndicatorsEnabled =
        Objects.requireNonNull(typingIndicatorsEnabled, "typingIndicatorsEnabled");
    this.uiShowing = Objects.requireNonNull(uiShowing, "uiShowing");
    this.repaintTreeNode = Objects.requireNonNull(repaintTreeNode, "repaintTreeNode");
  }

  @Override
  public boolean supportsTypingActivity(TargetRef ref) {
    return supportsTypingActivity.test(ref);
  }

  @Override
  public boolean typingIndicatorsEnabled() {
    return typingIndicatorsEnabled.getAsBoolean();
  }

  @Override
  public boolean uiShowing() {
    return uiShowing.getAsBoolean();
  }

  @Override
  public void repaintTreeNode(DefaultMutableTreeNode node) {
    repaintTreeNode.accept(node);
  }
}
