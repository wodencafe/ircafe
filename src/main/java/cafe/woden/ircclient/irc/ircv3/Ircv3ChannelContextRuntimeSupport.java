package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagOperation;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagRequest;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignal;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3InboundTagSignalType;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Validates runtime-provider channel-context observations before conversation routing. */
@Component
@InfrastructureLayer
public final class Ircv3ChannelContextRuntimeSupport {

  private static final int MAX_TARGET_LENGTH = 512;

  private final Ircv3InboundTagSignalRuntimeCatalog inboundTagCatalog;

  @Autowired
  public Ircv3ChannelContextRuntimeSupport(Ircv3InboundTagSignalRuntimeCatalog inboundTagCatalog) {
    this.inboundTagCatalog = Objects.requireNonNull(inboundTagCatalog, "inboundTagCatalog");
  }

  /**
   * Resolves one safe conversation target, falling back to the transport-neutral direct-message
   * target when a provider is absent, ambiguous, or unsafe.
   */
  public String resolve(Ircv3InboundTagRequest request) {
    if (request == null) {
      return "";
    }
    String fallback = fallbackTarget(request.rawTarget(), request.sourceNick());
    if (!("PRIVMSG".equalsIgnoreCase(request.command())
            || "NOTICE".equalsIgnoreCase(request.command()))
        || isChannelName(normalizeTarget(request.rawTarget()))) {
      return fallback;
    }
    String accepted = "";
    for (Ircv3InboundTagSignal signal :
        inboundTagCatalog.parse(Ircv3InboundTagOperation.CHANNEL_CONTEXT, request)) {
      if (signal.type() != Ircv3InboundTagSignalType.CONVERSATION_TARGET) {
        continue;
      }
      String target = normalizeTarget(signal.primaryValue());
      if (target.isEmpty() || !isAllowedTarget(target, fallback) || !accepted.isEmpty()) {
        return fallback;
      }
      accepted = target;
    }
    return accepted.isEmpty() ? fallback : accepted;
  }

  private static boolean isAllowedTarget(String target, String fallback) {
    return target.equals(fallback) || isChannelName(target);
  }

  private static String fallbackTarget(String rawTarget, String sourceNick) {
    String target = normalizeTarget(rawTarget);
    if (isChannelName(target)) {
      return target;
    }
    String source = normalizeTarget(sourceNick);
    return source.isEmpty() ? target : source;
  }

  private static String normalizeTarget(String raw) {
    String target = Objects.toString(raw, "").trim();
    if (target.isEmpty()
        || target.length() > MAX_TARGET_LENGTH
        || target.indexOf(',') >= 0
        || containsControl(target)
        || target.chars().anyMatch(Character::isWhitespace)) {
      return "";
    }
    return target;
  }

  private static boolean isChannelName(String target) {
    if (target.isEmpty()) {
      return false;
    }
    char leading = target.charAt(0);
    return leading == '#' || leading == '&' || leading == '!' || leading == '+';
  }

  private static boolean containsControl(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isISOControl(value.charAt(i))) {
        return true;
      }
    }
    return false;
  }
}
