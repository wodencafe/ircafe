package cafe.woden.ircclient.ui.coordinator;

import cafe.woden.ircclient.app.api.Ircv3ChatHistoryFeatureSupport;
import cafe.woden.ircclient.app.api.Ircv3MessageRedactionFeatureSupport;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Negotiated-feature backed capability policy for transcript message actions. */
@Component
@InterfaceLayer
@RequiredArgsConstructor
public final class IrcMessageActionCapabilityPolicy implements MessageActionCapabilityPolicy {
  @Qualifier("ircNegotiatedFeaturePort")
  private final IrcNegotiatedFeaturePort irc;

  @NonNull private final Ircv3ChatHistoryFeatureSupport chatHistoryFeatureSupport;
  @NonNull private final Ircv3MessageRedactionFeatureSupport messageRedactionFeatureSupport;

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
