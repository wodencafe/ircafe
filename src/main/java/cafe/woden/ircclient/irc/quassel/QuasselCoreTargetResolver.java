package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.looksLikeChannel;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.parseQualifiedTarget;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.stripLeadingColon;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreTargetRouting.QualifiedTarget;
import java.util.Locale;
import java.util.Objects;

/** Resolves targets against live session catalogs without retaining session state. */
final class QuasselCoreTargetResolver {
  private static final int BUFFER_STATUS = 0x01;
  private static final int BUFFER_CHANNEL = 0x02;
  private static final int BUFFER_QUERY = 0x04;

  interface SessionPort {
    void observeNetwork(QuasselCoreSession session, int networkId);

    boolean isSelfNick(QuasselCoreSession session, String nick, int networkId);
  }

  private final SessionPort port;

  QuasselCoreTargetResolver(SessionPort port) {
    this.port = Objects.requireNonNull(port, "port");
  }

  BufferInfoValue historyBuffer(
      QuasselCoreSession session, String serverId, String operation, QualifiedTarget target)
      throws BackendNotAvailableException {
    if (target == null) {
      throw new IllegalArgumentException("target is blank");
    }
    int typeBitsHint = looksLikeChannel(target.baseTarget()) ? BUFFER_CHANNEL : BUFFER_QUERY;
    int preferredNetworkId =
        preferredNetworkId(session, target.baseTarget(), target.networkToken());
    BufferInfoValue byName =
        session.buffers.findByName(target.baseTarget(), typeBitsHint, preferredNetworkId);
    if (byName != null && byName.bufferId() >= 0) {
      return byName;
    }
    throw new BackendNotAvailableException(
        IrcProperties.Server.Backend.QUASSEL_CORE,
        operation,
        serverId,
        "target buffer '" + target.baseTarget() + "' is not known yet");
  }

  private int preferredNetworkId(QuasselCoreSession session, String target, String networkToken) {
    if (session == null) return -1;
    String token = Objects.toString(networkToken, "").trim().toLowerCase(Locale.ROOT);
    if (!token.isEmpty()) {
      Integer byToken = session.networks.idForToken(token);
      if (byToken != null && byToken.intValue() >= 0) {
        return byToken.intValue();
      }
    }
    int hinted = session.targetNetworkHints.networkIdForTarget(target);
    if (hinted >= 0) return hinted;
    return firstKnownNetworkId(session);
  }

  String signalTarget(
      QuasselCoreSession session,
      String fromDisplay,
      String fallbackTarget,
      int networkId,
      String rawTarget) {
    String fallback = Objects.toString(fallbackTarget, "").trim();
    String hint = stripLeadingColon(rawTarget);
    if (hint.isBlank()) {
      return fallback;
    }
    QualifiedTarget parsed = parseQualifiedTarget(hint);
    String base = parsed.baseTarget();
    if (base.isBlank()) {
      return fallback;
    }
    if (port.isSelfNick(session, base, networkId)) {
      String from = Objects.toString(fromDisplay, "").trim();
      if (!from.isBlank()) {
        base = from;
      }
    }
    if (!parsed.networkToken().isBlank()) {
      return parsed.rawTarget();
    }
    return qualifyTarget(session, base, networkId);
  }

  String targetForBuffer(
      QuasselCoreSession session, BufferInfoValue bufferInfo, String fallbackFromNick) {
    boolean channelBuffer = isChannelBuffer(bufferInfo);
    boolean queryBuffer = isQueryBuffer(bufferInfo);
    if (!channelBuffer && !queryBuffer && isStatusBuffer(bufferInfo)) {
      return "status";
    }
    String base = normalizedBufferName(bufferInfo);
    if (base.isEmpty() && queryBuffer) {
      base = Objects.toString(fallbackFromNick, "").trim();
    }
    if (base.isEmpty()) return "";
    if (!channelBuffer && !queryBuffer) {
      return base;
    }
    int networkId = bufferInfo == null ? -1 : bufferInfo.networkId();
    return qualifyTarget(session, base, networkId);
  }

  String qualifyTarget(QuasselCoreSession session, String baseTarget, int networkId) {
    String base = Objects.toString(baseTarget, "").trim();
    if (base.isEmpty()) return "";
    if (session == null || networkId < 0) return base;
    if (knownNetworkCount(session) <= 1) return base;
    String token = networkTokenForNetworkId(session, networkId);
    return QuasselCoreTargetRouting.qualifyTarget(base, token);
  }

  private static int knownNetworkCount(QuasselCoreSession session) {
    return session.networks.knownIds(session.authResult.get(), session.buffers.values()).size();
  }

  private String networkTokenForNetworkId(QuasselCoreSession session, int networkId) {
    if (session == null || networkId < 0) return "";
    String token = session.networks.token(networkId);
    if (!token.isEmpty()) return token;
    port.observeNetwork(session, networkId);
    return session.networks.token(networkId);
  }

  BufferInfoValue outboundBuffer(
      QuasselCoreSession session, int fallbackTypeBits, QualifiedTarget requestedTarget) {
    if (requestedTarget == null) {
      return new BufferInfoValue(-1, firstKnownNetworkId(session), fallbackTypeBits, -1, "");
    }
    String requestedName = requestedTarget.baseTarget();
    int preferredNetworkId =
        preferredNetworkId(session, requestedName, requestedTarget.networkToken());
    BufferInfoValue byName =
        session.buffers.findByName(requestedName, fallbackTypeBits, preferredNetworkId);
    if (byName != null) {
      return byName;
    }

    int networkId = preferredNetworkId >= 0 ? preferredNetworkId : firstKnownNetworkId(session);
    return new BufferInfoValue(-1, networkId, fallbackTypeBits, -1, requestedName);
  }

  private static int firstKnownNetworkId(QuasselCoreSession session) {
    return session == null
        ? -1
        : session.networks.firstKnownNetworkId(session.authResult.get(), session.buffers.values());
  }

  private static String normalizedBufferName(BufferInfoValue bufferInfo) {
    return bufferInfo == null ? "" : Objects.toString(bufferInfo.bufferName(), "").trim();
  }

  private static boolean isChannelBuffer(BufferInfoValue bufferInfo) {
    return bufferInfo != null && (bufferInfo.typeBits() & BUFFER_CHANNEL) != 0;
  }

  private static boolean isQueryBuffer(BufferInfoValue bufferInfo) {
    return bufferInfo != null && (bufferInfo.typeBits() & BUFFER_QUERY) != 0;
  }

  private static boolean isStatusBuffer(BufferInfoValue bufferInfo) {
    return bufferInfo != null && (bufferInfo.typeBits() & BUFFER_STATUS) != 0;
  }
}
