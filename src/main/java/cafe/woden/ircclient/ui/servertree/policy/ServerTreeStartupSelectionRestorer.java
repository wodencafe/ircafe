package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Restores and consumes startup remembered tree selection when it becomes selectable. */
public final class ServerTreeStartupSelectionRestorer {

  public interface Context {
    String normalizeServerId(String serverId);

    boolean hasLeaf(TargetRef ref);

    boolean isMonitorGroupSelectable(String serverId);

    boolean isInterceptorsGroupSelectable(String serverId);

    void selectTarget(TargetRef ref);
  }

  public static Context context(
      Function<String, String> normalizeServerId,
      Predicate<TargetRef> hasLeaf,
      Predicate<String> isMonitorGroupSelectable,
      Predicate<String> isInterceptorsGroupSelectable,
      Consumer<TargetRef> selectTarget) {
    Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    Objects.requireNonNull(hasLeaf, "hasLeaf");
    Objects.requireNonNull(isMonitorGroupSelectable, "isMonitorGroupSelectable");
    Objects.requireNonNull(isInterceptorsGroupSelectable, "isInterceptorsGroupSelectable");
    Objects.requireNonNull(selectTarget, "selectTarget");
    return new Context() {
      @Override
      public String normalizeServerId(String serverId) {
        return normalizeServerId.apply(serverId);
      }

      @Override
      public boolean hasLeaf(TargetRef ref) {
        return hasLeaf.test(ref);
      }

      @Override
      public boolean isMonitorGroupSelectable(String serverId) {
        return isMonitorGroupSelectable.test(serverId);
      }

      @Override
      public boolean isInterceptorsGroupSelectable(String serverId) {
        return isInterceptorsGroupSelectable.test(serverId);
      }

      @Override
      public void selectTarget(TargetRef ref) {
        selectTarget.accept(ref);
      }
    };
  }

  public static TargetRef readRememberedSelection(RuntimeConfigStore runtimeConfig) {
    if (runtimeConfig == null) return null;
    Optional<RuntimeConfigStore.LastSelectedTarget> remembered =
        runtimeConfig.readLastSelectedTarget();
    if (remembered.isEmpty()) return null;
    RuntimeConfigStore.LastSelectedTarget selected = remembered.get();
    if (!selected.isValid()) return null;
    try {
      return new TargetRef(selected.serverId(), selected.target());
    } catch (Exception ignored) {
      return null;
    }
  }

  private final Context context;
  private volatile TargetRef rememberedSelection = null;

  public ServerTreeStartupSelectionRestorer(TargetRef rememberedSelection, Context context) {
    this.context = Objects.requireNonNull(context, "context");
    this.rememberedSelection = rememberedSelection;
  }

  public TargetRef rememberedSelection() {
    return rememberedSelection;
  }

  public boolean tryRestoreForServer(String serverId) {
    TargetRef remembered = rememberedSelection;
    if (remembered == null) return false;

    String expectedServerId = normalize(serverId);
    String rememberedServerId = normalize(remembered.serverId());
    if (expectedServerId.isEmpty() || !expectedServerId.equals(rememberedServerId)) return false;
    if (!isSelectable(remembered)) return false;

    context.selectTarget(remembered);
    rememberedSelection = null;
    return true;
  }

  public boolean tryRestoreAfterEnsure(TargetRef ensuredRef) {
    TargetRef remembered = rememberedSelection;
    if (remembered == null || ensuredRef == null) return false;
    if (!remembered.equals(ensuredRef)) return false;
    if (!isSelectable(remembered)) return false;

    rememberedSelection = null;
    context.selectTarget(remembered);
    return true;
  }

  private boolean isSelectable(TargetRef ref) {
    if (ref == null) return false;
    if (ref.isMonitorGroup()) {
      String sid = normalize(ref.serverId());
      return !sid.isEmpty() && context.isMonitorGroupSelectable(sid);
    }
    if (ref.isInterceptorsGroup()) {
      String sid = normalize(ref.serverId());
      return !sid.isEmpty() && context.isInterceptorsGroupSelectable(sid);
    }
    return context.hasLeaf(ref);
  }

  private String normalize(String serverId) {
    String normalized = context.normalizeServerId(serverId);
    return Objects.toString(normalized, "").trim();
  }
}
