package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;

/** Owns static application subtree targets and label mapping. */
public final class ServerTreeApplicationNodes {

  private static final String UNHANDLED_ERRORS_LABEL = "Unhandled Errors";
  private static final String ASSERTJ_SWING_LABEL = "AssertJ Swing";
  private static final String JHICCUP_LABEL = "jHiccup";
  private static final String INBOUND_DEDUP_LABEL = "Inbound Dedup";
  private static final String PLUGINS_LABEL = "Plugins";
  private static final String JFR_LABEL = "JFR";
  private static final String SPRING_LABEL = "Spring";
  private static final String TERMINAL_LABEL = "Terminal";

  private final DefaultMutableTreeNode applicationRoot;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final LinkedHashMap<TargetRef, String> builtInLabelsByRef = new LinkedHashMap<>();
  private final TargetRef jfrRef = TargetRef.applicationJfr();

  public ServerTreeApplicationNodes(
      DefaultMutableTreeNode applicationRoot, Map<TargetRef, DefaultMutableTreeNode> leaves) {
    this.applicationRoot = Objects.requireNonNull(applicationRoot, "applicationRoot");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    builtInLabelsByRef.put(TargetRef.applicationUnhandledErrors(), UNHANDLED_ERRORS_LABEL);
    builtInLabelsByRef.put(TargetRef.applicationAssertjSwing(), ASSERTJ_SWING_LABEL);
    builtInLabelsByRef.put(TargetRef.applicationJhiccup(), JHICCUP_LABEL);
    builtInLabelsByRef.put(TargetRef.applicationInboundDedup(), INBOUND_DEDUP_LABEL);
    builtInLabelsByRef.put(TargetRef.applicationPlugins(), PLUGINS_LABEL);
    builtInLabelsByRef.put(jfrRef, JFR_LABEL);
    builtInLabelsByRef.put(TargetRef.applicationSpring(), SPRING_LABEL);
    builtInLabelsByRef.put(TargetRef.applicationTerminal(), TERMINAL_LABEL);
  }

  public void initialize() {
    applicationRoot.removeAllChildren();
    for (Map.Entry<TargetRef, String> entry : builtInLabelsByRef.entrySet()) {
      addLeaf(entry.getKey(), entry.getValue());
    }
  }

  public void addLeaf(TargetRef ref, String label) {
    if (ref == null) return;
    String normalizedLabel = Objects.toString(label, "").trim();
    String nodeLabel = normalizedLabel.isEmpty() ? labelFor(ref) : normalizedLabel;
    DefaultMutableTreeNode leaf =
        new DefaultMutableTreeNode(new ServerTreeNodeData(ref, nodeLabel));
    leaves.put(ref, leaf);
    applicationRoot.add(leaf);
  }

  public String labelFor(TargetRef ref) {
    if (ref == null) return "";
    String known = builtInLabelsByRef.get(ref);
    if (known != null && !known.isBlank()) return known;
    return Objects.toString(ref.target(), "");
  }

  public TargetRef jfrRef() {
    return jfrRef;
  }
}
