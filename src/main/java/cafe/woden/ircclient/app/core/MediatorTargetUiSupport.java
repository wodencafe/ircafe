package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared target-routing and private-message presence behavior for mediator collaborators. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorTargetUiSupport {

  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final MediatorInboundEventPreparationService eventPreparationService;

  public boolean isFromSelf(String serverId, String from) {
    return eventPreparationService.isFromSelf(serverId, from);
  }

  public void markPrivateMessagePeerOnline(String serverId, String rawNick) {
    String nick = normalizePrivateMessagePeer(rawNick);
    if (nick.isEmpty() || isFromSelf(serverId, nick)) {
      return;
    }
    ui.setPrivateMessageOnlineState(serverId, nick, true);
  }

  public void markPrivateMessagePeerOffline(String serverId, String rawNick) {
    String nick = normalizePrivateMessagePeer(rawNick);
    if (nick.isEmpty() || isFromSelf(serverId, nick)) {
      return;
    }
    ui.setPrivateMessageOnlineState(serverId, nick, false);
  }

  public TargetRef resolveIrcv3Target(String sid, String target, String from, TargetRef status) {
    String targetName = Objects.toString(target, "").trim();
    if (!targetName.isEmpty() && (targetName.startsWith("#") || targetName.startsWith("&"))) {
      return new TargetRef(sid, targetName);
    }
    String fromNick = Objects.toString(from, "").trim();
    if (!fromNick.isEmpty() && !"server".equalsIgnoreCase(fromNick)) {
      return new TargetRef(sid, fromNick);
    }
    return resolveActiveOrStatus(sid, status);
  }

  public void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
    postTo(dest, targetCoordinator.getActiveTarget(), markUnreadIfNotActive, write);
  }

  public TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active != null && Objects.equals(active.serverId(), sid)) {
      return active;
    }
    return status != null ? status : safeStatusTarget();
  }

  public void postTo(
      TargetRef dest, TargetRef active, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
    TargetRef destination = dest != null ? dest : safeStatusTarget();
    ensureTargetExists(destination);
    if (write != null) {
      write.accept(destination);
    }
    if (markUnreadIfNotActive
        && active != null
        && !destination.equals(active)
        && !isMutedChannel(destination)) {
      ui.markUnread(destination);
    }
  }

  public boolean isMutedChannel(TargetRef target) {
    if (target == null || !target.isChannel()) {
      return false;
    }
    try {
      return ui.isChannelMuted(target);
    } catch (Exception ignored) {
      return false;
    }
  }

  public boolean isMutedChannel(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) {
      return false;
    }
    try {
      return isMutedChannel(new TargetRef(sid, ch));
    } catch (Exception ignored) {
      return false;
    }
  }

  public void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  public TargetRef safeStatusTarget() {
    return targetCoordinator.safeStatusTarget();
  }

  static String normalizeNickForCompare(String raw) {
    return MediatorInboundEventPreparationService.normalizeNickForCompare(raw);
  }

  private static String normalizePrivateMessagePeer(String raw) {
    String nick = normalizeNickForCompare(raw);
    nick = Objects.toString(nick, "").trim();
    if (nick.isEmpty() || "server".equalsIgnoreCase(nick) || nick.startsWith("*")) {
      return "";
    }
    return nick;
  }
}
