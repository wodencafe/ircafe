package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.irc.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.IrcNegotiatedFeaturePort;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Negotiated-feature backed capability policy for transcript message actions. */
@Component
@InterfaceLayer
public final class IrcMessageActionCapabilityPolicy implements MessageActionCapabilityPolicy {
  private final IrcNegotiatedFeaturePort irc;
  private final IrcBouncerPlaybackPort bouncerPlayback;

  public IrcMessageActionCapabilityPolicy(
      @Qualifier("ircNegotiatedFeaturePort") IrcNegotiatedFeaturePort irc,
      @Qualifier("ircClientService") IrcBouncerPlaybackPort bouncerPlayback) {
    this.irc = irc;
    this.bouncerPlayback = bouncerPlayback;
  }

  @Override
  public boolean canReply(String serverId) {
    return safe(() -> irc != null && irc.isDraftReplyAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canReact(String serverId) {
    return safe(() -> irc != null && irc.isDraftReactAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canUnreact(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || irc == null) return false;
    return safe(() -> irc.isDraftReplyAvailable(sid) && irc.isDraftUnreactAvailable(sid));
  }

  @Override
  public boolean canEdit(String serverId) {
    return safe(() -> irc != null && irc.isMessageEditAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canRedact(String serverId) {
    return safe(() -> irc != null && irc.isMessageRedactionAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canLoadAroundMessage(String serverId) {
    return safe(() -> irc != null && irc.isChatHistoryAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canLoadNewerHistory(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty() || irc == null || bouncerPlayback == null) return false;
    return safe(
        () -> irc.isChatHistoryAvailable(sid) || bouncerPlayback.isZncPlaybackAvailable(sid));
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static boolean safe(BooleanSupplier call) {
    try {
      return call.getAsBoolean();
    } catch (Exception ignored) {
      return false;
    }
  }
}
