package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Encapsulates default and per-server built-in visibility settings mutations. */
public final class ServerTreeBuiltInVisibilitySettings {

  public interface Context {
    String normalizeServerId(String serverId);

    ServerBuiltInNodesVisibility defaultVisibility();

    void setDefaultVisibility(ServerBuiltInNodesVisibility next);

    ServerBuiltInNodesVisibility visibilityForServer(String serverId);

    void applyVisibilityForServer(
        String serverId, ServerBuiltInNodesVisibility next, boolean persist, boolean syncUi);

    void applyVisibilityGlobally(
        java.util.function.UnaryOperator<ServerBuiltInNodesVisibility> mutator);

    void firePropertyChange(String propertyName, boolean oldValue, boolean newValue);
  }

  private final Context context;

  public ServerTreeBuiltInVisibilitySettings(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public boolean defaultVisibility(Function<ServerBuiltInNodesVisibility, Boolean> getter) {
    if (getter == null) return false;
    return getter.apply(context.defaultVisibility());
  }

  public boolean serverVisibility(
      String serverId, Function<ServerBuiltInNodesVisibility, Boolean> getter) {
    if (getter == null) return false;
    return getter.apply(context.visibilityForServer(serverId));
  }

  public void setVisibilityForServer(
      String serverId,
      boolean visible,
      BiFunction<ServerBuiltInNodesVisibility, Boolean, ServerBuiltInNodesVisibility> updater) {
    if (updater == null) return;
    String sid = context.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerBuiltInNodesVisibility current = context.visibilityForServer(sid);
    ServerBuiltInNodesVisibility next = updater.apply(current, visible);
    if (next == null) return;
    context.applyVisibilityForServer(sid, next, true, true);
  }

  public void setDefaultVisibility(
      boolean visible,
      Function<ServerBuiltInNodesVisibility, Boolean> getter,
      BiFunction<ServerBuiltInNodesVisibility, Boolean, ServerBuiltInNodesVisibility> updater,
      String propertyName) {
    if (getter == null || updater == null) return;
    ServerBuiltInNodesVisibility current = context.defaultVisibility();
    boolean old = getter.apply(current);
    ServerBuiltInNodesVisibility next = updater.apply(current, visible);
    if (next == null) return;

    context.setDefaultVisibility(next);
    context.applyVisibilityGlobally(v -> updater.apply(v, visible));

    if (propertyName != null && !propertyName.isBlank()) {
      context.firePropertyChange(propertyName, old, visible);
    }
  }
}
