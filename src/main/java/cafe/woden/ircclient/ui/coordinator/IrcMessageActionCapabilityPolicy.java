package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.app.api.Ircv3ChatHistoryFeatureSupport;
import cafe.woden.ircclient.app.api.Ircv3MessageRedactionFeatureSupport;
import cafe.woden.ircclient.irc.playback.IrcBouncerPlaybackPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Negotiated-feature backed capability policy for transcript message actions. */
@Component
@InterfaceLayer
public final class IrcMessageActionCapabilityPolicy implements MessageActionCapabilityPolicy {
  private final IrcNegotiatedFeaturePort irc;
  private final Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport;
  private final Ircv3MessageRedactionFeatureSupport messageRedactionFeatureSupport;

  @Autowired
  public IrcMessageActionCapabilityPolicy(
      @Qualifier("ircNegotiatedFeaturePort") IrcNegotiatedFeaturePort irc,
      @Qualifier("ircClientService") IrcBouncerPlaybackPort bouncerPlayback,
      Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport,
      Ircv3MessageRedactionFeatureSupport messageRedactionFeatureSupport) {
    this.irc = irc;
    this.chatHistoryFeatureSupport =
        chatHistoryFeatureSupport == null
            ? new Ircv3ChatHistoryFeatureSupport(irc, bouncerPlayback)
            : chatHistoryFeatureSupport;
    this.messageRedactionFeatureSupport =
        messageRedactionFeatureSupport == null
            ? new Ircv3MessageRedactionFeatureSupport(irc)
            : messageRedactionFeatureSupport;
  }

  @Deprecated(forRemoval = false)
  public IrcMessageActionCapabilityPolicy(
      IrcNegotiatedFeaturePort irc, IrcBouncerPlaybackPort bouncerPlayback) {
    this(
        irc,
        bouncerPlayback,
        new Ircv3ChatHistoryFeatureSupport(irc, bouncerPlayback),
        new Ircv3MessageRedactionFeatureSupport(irc));
  }

  @Override
  public boolean canReply(String serverId) {
    return safe(() -> irc != null && irc.isMessageTagsAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canReact(String serverId) {
    return safe(() -> irc != null && irc.isMessageTagsAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canUnreact(String serverId) {
    return safe(() -> irc != null && irc.isMessageTagsAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canEdit(String serverId) {
    return safe(
        () -> irc != null && irc.isExperimentalMessageEditAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canRedact(String serverId) {
    return safe(() -> messageRedactionFeatureSupport.isAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canLoadAroundMessage(String serverId) {
    return safe(() -> chatHistoryFeatureSupport.isAvailable(normalizeServerId(serverId)));
  }

  @Override
  public boolean canLoadNewerHistory(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return safe(() -> chatHistoryFeatureSupport.isRemoteHistoryAvailable(sid));
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
