package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.NegotiatedModeSemantics;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Coordinates alerting and debounce logic extracted from {@link IrcMediator}. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorAlertNotificationHandler {
  private static final long NETSPLIT_NOTIFY_DEBOUNCE_MS = 20_000L;
  private static final int NETSPLIT_NOTIFY_MAX_KEYS = 512;

  interface Callbacks {
    boolean notifyIrcEvent(
        IrcEventNotificationRule.EventType eventType,
        String serverId,
        String channel,
        String sourceNick,
        String title,
        String body);
  }

  private final IrcMediatorInteractionPort irc;
  private final ServerIsupportStatePort serverIsupportState;

  private final Map<String, Long> lastNetsplitNotifyAtMs = new ConcurrentHashMap<>();

  public void maybeNotifyUserKlineFromQuit(
      Callbacks callbacks, String serverId, IrcEvent.UserQuitChannel event) {
    if (event == null) {
      return;
    }
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      return;
    }

    String nick = Objects.toString(event.nick(), "").trim();
    if (nick.isEmpty() || isFromSelf(sid, nick)) {
      return;
    }

    String reason = Objects.toString(event.reason(), "").trim();
    if (!looksLikeKlineMessage(reason)) {
      return;
    }

    String channel = Objects.toString(event.channel(), "").trim();
    String body = nick + " appears to be restricted";
    if (!reason.isEmpty()) {
      body = body + " (" + reason + ")";
    }
    if (!channel.isEmpty()) {
      body = body + " in " + channel;
    }

    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.KLINED,
        sid,
        channel,
        nick,
        "User restricted" + (channel.isEmpty() ? "" : " in " + channel),
        body);
  }

  public void maybeNotifyNetsplitDetected(
      Callbacks callbacks, String serverId, IrcEvent.UserQuitChannel event) {
    if (event == null) {
      return;
    }
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) {
      return;
    }

    NetsplitServers split = parseNetsplitServers(event.reason());
    if (split == null) {
      return;
    }

    long now = System.currentTimeMillis();
    String key = (sid + "|" + split.left() + "|" + split.right()).toLowerCase(Locale.ROOT);
    Long previous = lastNetsplitNotifyAtMs.put(key, now);
    if (previous != null && (now - previous) < NETSPLIT_NOTIFY_DEBOUNCE_MS) {
      return;
    }

    if (lastNetsplitNotifyAtMs.size() > NETSPLIT_NOTIFY_MAX_KEYS) {
      long cutoff = now - (NETSPLIT_NOTIFY_DEBOUNCE_MS * 3L);
      lastNetsplitNotifyAtMs
          .entrySet()
          .removeIf(entry -> entry.getValue() == null || entry.getValue() < cutoff);
    }

    String channel = Objects.toString(event.channel(), "").trim();
    String nick = Objects.toString(event.nick(), "").trim();
    String body = "Possible netsplit detected (" + split.left() + " ↔ " + split.right() + ")";
    if (!channel.isEmpty()) {
      body = body + " in " + channel;
    }
    if (!nick.isEmpty()) {
      body = body + " after " + nick + " quit";
    }

    callbacks.notifyIrcEvent(
        IrcEventNotificationRule.EventType.NETSPLIT_DETECTED,
        sid,
        channel,
        nick,
        "Netsplit detected",
        body);
  }

  public void clearNetsplitDebounceForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim().toLowerCase(Locale.ROOT);
    if (sid.isEmpty()) {
      return;
    }
    String prefix = sid + "|";
    lastNetsplitNotifyAtMs.keySet().removeIf(key -> key != null && key.startsWith(prefix));
  }

  public void maybeNotifyModeEvents(
      Callbacks callbacks, String serverId, IrcEvent.ChannelModeObserved event) {
    if (serverId == null || event == null) {
      return;
    }

    String channel = Objects.toString(event.channel(), "").trim();
    if (channel.isEmpty()) {
      return;
    }

    String actor = Objects.toString(event.by(), "").trim();
    String by = actor.isEmpty() ? "Someone" : actor;

    for (ModeChangeToken change : parseModeChanges(serverId, event.details())) {
      if (change == null) {
        continue;
      }

      IrcEventNotificationRule.EventType type =
          switch (change.mode()) {
            case 'o' ->
                change.add()
                    ? IrcEventNotificationRule.EventType.OPPED
                    : IrcEventNotificationRule.EventType.DEOPPED;
            case 'v' ->
                change.add()
                    ? IrcEventNotificationRule.EventType.VOICED
                    : IrcEventNotificationRule.EventType.DEVOICED;
            case 'h' ->
                change.add()
                    ? IrcEventNotificationRule.EventType.HALF_OPPED
                    : IrcEventNotificationRule.EventType.DEHALF_OPPED;
            case 'b' -> change.add() ? IrcEventNotificationRule.EventType.BANNED : null;
            default -> null;
          };
      if (type == null) {
        continue;
      }

      String arg = Objects.toString(change.arg(), "").trim();
      String body =
          switch (type) {
            case OPPED ->
                by + " gave operator to " + (arg.isEmpty() ? "(unknown)" : arg) + " in " + channel;
            case DEOPPED ->
                by
                    + " removed operator from "
                    + (arg.isEmpty() ? "(unknown)" : arg)
                    + " in "
                    + channel;
            case VOICED ->
                by + " gave voice to " + (arg.isEmpty() ? "(unknown)" : arg) + " in " + channel;
            case DEVOICED ->
                by
                    + " removed voice from "
                    + (arg.isEmpty() ? "(unknown)" : arg)
                    + " in "
                    + channel;
            case HALF_OPPED ->
                by + " gave half-op to " + (arg.isEmpty() ? "(unknown)" : arg) + " in " + channel;
            case DEHALF_OPPED ->
                by
                    + " removed half-op from "
                    + (arg.isEmpty() ? "(unknown)" : arg)
                    + " in "
                    + channel;
            case BANNED ->
                by + " set ban " + (arg.isEmpty() ? "(unknown)" : arg) + " in " + channel;
            default -> by + " changed mode in " + channel;
          };

      callbacks.notifyIrcEvent(type, serverId, channel, actor, "Mode change in " + channel, body);

      IrcEventNotificationRule.EventType selfType = selfTargetedModeEventType(type);
      if (selfType != null && isSelfModeTargetForEvent(serverId, type, arg)) {
        callbacks.notifyIrcEvent(
            selfType,
            serverId,
            channel,
            actor,
            selfTargetedModeTitle(selfType, channel),
            selfTargetedModeBody(selfType, by, channel));
      }
    }
  }

  static boolean looksLikeKlineMessage(String message) {
    String m = Objects.toString(message, "").trim().toLowerCase(Locale.ROOT);
    if (m.isEmpty()) {
      return false;
    }
    return m.contains("k-line")
        || m.contains("klined")
        || m.contains("kline")
        || m.contains("g-line")
        || m.contains("gline")
        || m.contains("z-line")
        || m.contains("zline")
        || m.contains("akill")
        || m.contains("autokill")
        || m.contains("banned from this server");
  }

  private boolean isSelfModeTargetForEvent(
      String serverId, IrcEventNotificationRule.EventType baseType, String rawTarget) {
    if (baseType == IrcEventNotificationRule.EventType.BANNED) {
      return isSelfBanTarget(serverId, rawTarget);
    }
    return isSelfModeTarget(serverId, rawTarget);
  }

  private boolean isSelfModeTarget(String serverId, String rawTargetNick) {
    String target = MediatorInboundEventPreparationService.normalizeNickForCompare(rawTargetNick);
    if (target == null || target.isBlank()) {
      return false;
    }
    return isFromSelf(serverId, target);
  }

  private boolean isSelfBanTarget(String serverId, String rawBanTarget) {
    String target = Objects.toString(rawBanTarget, "").trim();
    if (target.isEmpty()) {
      return false;
    }

    if (isSelfModeTarget(serverId, target)) {
      return true;
    }

    String me = irc.currentNick(serverId).orElse("");
    if (me.isBlank()) {
      return false;
    }
    return target.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT));
  }

  private boolean isFromSelf(String serverId, String from) {
    String sid = Objects.toString(serverId, "").trim();
    String source = Objects.toString(from, "").trim();
    if (sid.isEmpty() || source.isEmpty()) {
      return false;
    }
    String me = irc.currentNick(sid).orElse("");
    if (me.isBlank()) {
      return false;
    }
    String meNorm = MediatorInboundEventPreparationService.normalizeNickForCompare(me);
    String fromNorm = MediatorInboundEventPreparationService.normalizeNickForCompare(source);
    return fromNorm != null && meNorm != null && fromNorm.equalsIgnoreCase(meNorm);
  }

  private static IrcEventNotificationRule.EventType selfTargetedModeEventType(
      IrcEventNotificationRule.EventType baseType) {
    if (baseType == null) {
      return null;
    }
    return switch (baseType) {
      case OPPED -> IrcEventNotificationRule.EventType.YOU_OPPED;
      case DEOPPED -> IrcEventNotificationRule.EventType.YOU_DEOPPED;
      case VOICED -> IrcEventNotificationRule.EventType.YOU_VOICED;
      case DEVOICED -> IrcEventNotificationRule.EventType.YOU_DEVOICED;
      case HALF_OPPED -> IrcEventNotificationRule.EventType.YOU_HALF_OPPED;
      case DEHALF_OPPED -> IrcEventNotificationRule.EventType.YOU_DEHALF_OPPED;
      case BANNED -> IrcEventNotificationRule.EventType.YOU_BANNED;
      default -> null;
    };
  }

  private static String selfTargetedModeTitle(
      IrcEventNotificationRule.EventType selfType, String channel) {
    if (selfType == null) {
      return "Mode change in " + channel;
    }
    return switch (selfType) {
      case YOU_OPPED -> "You were opped in " + channel;
      case YOU_DEOPPED -> "You were de-opped in " + channel;
      case YOU_VOICED -> "You were voiced in " + channel;
      case YOU_DEVOICED -> "You were de-voiced in " + channel;
      case YOU_HALF_OPPED -> "You were half-opped in " + channel;
      case YOU_DEHALF_OPPED -> "You were de-half-opped in " + channel;
      case YOU_BANNED -> "You were banned in " + channel;
      default -> "Mode change in " + channel;
    };
  }

  private static String selfTargetedModeBody(
      IrcEventNotificationRule.EventType selfType, String by, String channel) {
    if (selfType == null) {
      return by + " changed your mode in " + channel;
    }
    return switch (selfType) {
      case YOU_OPPED -> by + " gave you operator in " + channel;
      case YOU_DEOPPED -> by + " removed your operator in " + channel;
      case YOU_VOICED -> by + " gave you voice in " + channel;
      case YOU_DEVOICED -> by + " removed your voice in " + channel;
      case YOU_HALF_OPPED -> by + " gave you half-op in " + channel;
      case YOU_DEHALF_OPPED -> by + " removed your half-op in " + channel;
      case YOU_BANNED -> by + " set a ban matching you in " + channel;
      default -> by + " changed your mode in " + channel;
    };
  }

  private List<ModeChangeToken> parseModeChanges(String serverId, String details) {
    String d = Objects.toString(details, "").trim();
    if (d.isEmpty()) {
      return List.of();
    }

    String[] parts = d.split("\\s+");
    if (parts.length == 0) {
      return List.of();
    }

    int modeIdx = -1;
    for (int i = 0; i < parts.length; i++) {
      String token = parts[i];
      if (token.indexOf('+') >= 0 || token.indexOf('-') >= 0) {
        modeIdx = i;
        break;
      }
    }
    if (modeIdx < 0) {
      return List.of();
    }

    String modeSeq = parts[modeIdx];
    List<String> args = new java.util.ArrayList<>();
    for (int i = modeIdx + 1; i < parts.length; i++) {
      args.add(parts[i]);
    }

    boolean add = true;
    int argIdx = 0;
    List<ModeChangeToken> out = new java.util.ArrayList<>();
    ModeVocabulary vocabulary = serverIsupportState.vocabularyForServer(serverId);
    for (int i = 0; i < modeSeq.length(); i++) {
      char c = modeSeq.charAt(i);
      if (c == '+') {
        add = true;
        continue;
      }
      if (c == '-') {
        add = false;
        continue;
      }

      String arg = null;
      if (NegotiatedModeSemantics.takesArgument(vocabulary, c, add) && argIdx < args.size()) {
        arg = args.get(argIdx++);
      }
      out.add(new ModeChangeToken(add, c, arg));
    }
    return out;
  }

  private static NetsplitServers parseNetsplitServers(String reason) {
    String r = Objects.toString(reason, "").trim();
    if (r.isEmpty()) {
      return null;
    }
    String[] parts = r.split("\\s+");
    if (parts.length != 2) {
      return null;
    }
    String left = parts[0].trim();
    String right = parts[1].trim();
    if (!looksLikeIrcServerToken(left) || !looksLikeIrcServerToken(right)) {
      return null;
    }
    return new NetsplitServers(left, right);
  }

  private static boolean looksLikeIrcServerToken(String token) {
    String s = Objects.toString(token, "").trim();
    if (s.length() < 3 || s.length() > 255 || !s.contains(".")) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok = Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ':';
      if (!ok) {
        return false;
      }
    }
    return true;
  }

  private record NetsplitServers(String left, String right) {}

  private record ModeChangeToken(boolean add, char mode, String arg) {}
}
