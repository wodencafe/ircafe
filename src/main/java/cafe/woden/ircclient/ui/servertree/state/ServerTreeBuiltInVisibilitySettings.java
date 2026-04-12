package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Encapsulates default and per-server built-in visibility settings mutations. */
@Component
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

  public boolean defaultVisibility(
      Context context, Function<ServerBuiltInNodesVisibility, Boolean> getter) {
    Context in = Objects.requireNonNull(context, "context");
    if (getter == null) return false;
    return getter.apply(in.defaultVisibility());
  }

  public boolean serverVisibility(
      Context context, String serverId, Function<ServerBuiltInNodesVisibility, Boolean> getter) {
    Context in = Objects.requireNonNull(context, "context");
    if (getter == null) return false;
    return getter.apply(in.visibilityForServer(serverId));
  }

  public void setVisibilityForServer(
      Context context,
      String serverId,
      boolean visible,
      BiFunction<ServerBuiltInNodesVisibility, Boolean, ServerBuiltInNodesVisibility> updater) {
    Context in = Objects.requireNonNull(context, "context");
    if (updater == null) return;
    String sid = in.normalizeServerId(serverId);
    if (sid.isEmpty()) return;
    ServerBuiltInNodesVisibility current = in.visibilityForServer(sid);
    ServerBuiltInNodesVisibility next = updater.apply(current, visible);
    if (next == null) return;
    in.applyVisibilityForServer(sid, next, true, true);
  }

  public void setDefaultVisibility(
      Context context,
      boolean visible,
      Function<ServerBuiltInNodesVisibility, Boolean> getter,
      BiFunction<ServerBuiltInNodesVisibility, Boolean, ServerBuiltInNodesVisibility> updater,
      String propertyName) {
    Context in = Objects.requireNonNull(context, "context");
    if (getter == null || updater == null) return;
    ServerBuiltInNodesVisibility current = in.defaultVisibility();
    boolean old = getter.apply(current);
    ServerBuiltInNodesVisibility next = updater.apply(current, visible);
    if (next == null) return;

    in.setDefaultVisibility(next);
    in.applyVisibilityGlobally(v -> updater.apply(v, visible));

    if (propertyName != null && !propertyName.isBlank()) {
      in.firePropertyChange(propertyName, old, visible);
    }
  }
}
