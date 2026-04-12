package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.config.api.UiShellRuntimeConfigPort;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Restores and consumes startup remembered tree selection when it becomes selectable. */
@Component
public final class ServerTreeStartupSelectionRestorer {

  public interface Context {
    TargetRef rememberedSelection();

    void setRememberedSelection(TargetRef rememberedSelection);

    String normalizeServerId(String serverId);

    boolean hasLeaf(TargetRef ref);

    boolean isMonitorGroupSelectable(String serverId);

    boolean isInterceptorsGroupSelectable(String serverId);

    void selectTarget(TargetRef ref);
  }

  public static Context context(
      Supplier<TargetRef> rememberedSelection,
      Consumer<TargetRef> setRememberedSelection,
      Function<String, String> normalizeServerId,
      Predicate<TargetRef> hasLeaf,
      Predicate<String> isMonitorGroupSelectable,
      Predicate<String> isInterceptorsGroupSelectable,
      Consumer<TargetRef> selectTarget) {
    Objects.requireNonNull(rememberedSelection, "rememberedSelection");
    Objects.requireNonNull(setRememberedSelection, "setRememberedSelection");
    Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    Objects.requireNonNull(hasLeaf, "hasLeaf");
    Objects.requireNonNull(isMonitorGroupSelectable, "isMonitorGroupSelectable");
    Objects.requireNonNull(isInterceptorsGroupSelectable, "isInterceptorsGroupSelectable");
    Objects.requireNonNull(selectTarget, "selectTarget");
    return new Context() {
      @Override
      public TargetRef rememberedSelection() {
        return rememberedSelection.get();
      }

      @Override
      public void setRememberedSelection(TargetRef rememberedSelection) {
        setRememberedSelection.accept(rememberedSelection);
      }

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

  public static TargetRef readRememberedSelection(UiShellRuntimeConfigPort runtimeConfig) {
    if (runtimeConfig == null) return null;
    Optional<UiShellRuntimeConfigPort.LastSelectedTarget> remembered =
        runtimeConfig.readLastSelectedTarget();
    if (remembered.isEmpty()) return null;
    UiShellRuntimeConfigPort.LastSelectedTarget selected = remembered.get();
    if (!selected.isValid()) return null;
    try {
      return new TargetRef(selected.serverId(), selected.target());
    } catch (Exception ignored) {
      return null;
    }
  }

  public TargetRef rememberedSelection(Context context) {
    return Objects.requireNonNull(context, "context").rememberedSelection();
  }

  public boolean tryRestoreForServer(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    TargetRef remembered = in.rememberedSelection();
    if (remembered == null) return false;

    String expectedServerId = normalize(in, serverId);
    String rememberedServerId = normalize(in, remembered.serverId());
    if (expectedServerId.isEmpty() || !expectedServerId.equals(rememberedServerId)) return false;
    if (!isSelectable(in, remembered)) return false;

    in.selectTarget(remembered);
    in.setRememberedSelection(null);
    return true;
  }

  public boolean tryRestoreAfterEnsure(Context context, TargetRef ensuredRef) {
    Context in = Objects.requireNonNull(context, "context");
    TargetRef remembered = in.rememberedSelection();
    if (remembered == null || ensuredRef == null) return false;
    if (!remembered.equals(ensuredRef)) return false;
    if (!isSelectable(in, remembered)) return false;

    in.setRememberedSelection(null);
    in.selectTarget(remembered);
    return true;
  }

  private boolean isSelectable(Context context, TargetRef ref) {
    if (ref == null) return false;
    if (ref.isMonitorGroup()) {
      String sid = normalize(context, ref.serverId());
      return !sid.isEmpty() && context.isMonitorGroupSelectable(sid);
    }
    if (ref.isInterceptorsGroup()) {
      String sid = normalize(context, ref.serverId());
      return !sid.isEmpty() && context.isInterceptorsGroupSelectable(sid);
    }
    return context.hasLeaf(ref);
  }

  private String normalize(Context context, String serverId) {
    String normalized = context.normalizeServerId(serverId);
    return Objects.toString(normalized, "").trim();
  }
}
