package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Coordinates IRCv3 transcript mutation and capability side effects. */
@Component
@ApplicationLayer
public class MediatorIrcv3EventHandler {

  interface Callbacks {
    TargetRef resolveIrcv3Target(String sid, String target, String from, TargetRef status);
  }

  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;

  public MediatorIrcv3EventHandler(UiPort ui, TargetCoordinator targetCoordinator) {
    this.ui = Objects.requireNonNull(ui, "ui");
    this.targetCoordinator = Objects.requireNonNull(targetCoordinator, "targetCoordinator");
  }

  public void handleMessageReactObserved(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.MessageReactObserved event) {
    // Some servers relay mutation tags even when the corresponding CAP was never advertised.
    // Render observed inbound mutations leniently; outbound actions remain capability-gated.
    TargetRef dest = callbacks.resolveIrcv3Target(sid, event.target(), event.from(), status);
    String from = Objects.toString(event.from(), "").trim();
    if (from.isEmpty()) {
      return;
    }
    String reaction = Objects.toString(event.reaction(), "").trim();
    String targetMsgId = Objects.toString(event.messageId(), "").trim();
    if (reaction.isEmpty() || targetMsgId.isEmpty()) {
      return;
    }
    ui.applyMessageReaction(dest, event.at(), from, targetMsgId, reaction);
  }

  public void handleMessageUnreactObserved(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.MessageUnreactObserved event) {
    TargetRef dest = callbacks.resolveIrcv3Target(sid, event.target(), event.from(), status);
    String from = Objects.toString(event.from(), "").trim();
    if (from.isEmpty()) {
      return;
    }
    String reaction = Objects.toString(event.reaction(), "").trim();
    String targetMsgId = Objects.toString(event.messageId(), "").trim();
    if (reaction.isEmpty() || targetMsgId.isEmpty()) {
      return;
    }
    ui.removeMessageReaction(dest, event.at(), from, targetMsgId, reaction);
  }

  public void handleMessageRedactionObserved(
      Callbacks callbacks, String sid, TargetRef status, IrcEvent.MessageRedactionObserved event) {
    TargetRef dest = callbacks.resolveIrcv3Target(sid, event.target(), event.from(), status);
    String from = Objects.toString(event.from(), "").trim();
    String targetMsgId = Objects.toString(event.messageId(), "").trim();
    if (targetMsgId.isEmpty()) {
      return;
    }
    ui.applyMessageRedaction(
        dest, event.at(), from, targetMsgId, "", Map.of("draft/delete", targetMsgId));
  }

  public void handleIrcv3CapabilityChanged(
      String sid, TargetRef status, IrcEvent.Ircv3CapabilityChanged event) {
    TargetRef dest = status != null ? status : targetCoordinator.safeStatusTarget();
    ui.ensureTargetExists(dest);
    String sub = Objects.toString(event.subcommand(), "").trim().toUpperCase(Locale.ROOT);
    String cap = Objects.toString(event.capability(), "").trim();
    ui.setServerIrcv3Capability(sid, cap, sub, event.enabled());
    if (!event.enabled() && ("ACK".equals(sub) || "DEL".equals(sub))) {
      ui.normalizeIrcv3CapabilityUiState(sid, cap);
    }
    ui.appendStatusAt(
        dest, event.at(), "(ircv3)", renderIrcv3CapabilityChange(sub, cap, event.enabled()));
  }

  private static String renderIrcv3CapabilityChange(String sub, String cap, boolean enabled) {
    String capability = Objects.toString(cap, "").trim();
    if (capability.isEmpty()) {
      capability = "(unknown)";
    }
    return switch (sub) {
      case "NEW" -> "CAP NEW: " + capability + " (available)";
      case "LS" -> "CAP LS: " + capability + " (available)";
      case "NAK" -> "CAP NAK: " + capability + " (rejected)";
      case "DEL" -> "CAP DEL: " + capability + " (removed)";
      case "ACK" -> "CAP ACK: " + capability + (enabled ? " (enabled)" : " (disabled)");
      default -> "CAP " + sub + ": " + capability + (enabled ? " (enabled)" : "");
    };
  }
}
