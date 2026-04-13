package cafe.woden.ircclient.app.outbound.dcc;

import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.model.TargetRef;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared inbound DCC offer and control-message handling support. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class DccInboundOfferSupport {

  private static final String DCC_TAG = "(dcc)";

  @NonNull private final DccCommandSupport dccCommandSupport;
  @NonNull private final DccRuntimeRegistry dccRuntimeRegistry;

  boolean handleInboundDccOffer(
      Instant at, String serverId, String fromNick, String dccArgument, boolean spoiler) {
    String sid = DccCommandSupport.normalizeToken(serverId);
    String nick = DccCommandSupport.normalizeNick(fromNick);
    if (sid.isEmpty() || nick.isEmpty()) return false;

    List<String> tokens = splitDccTokens(dccArgument);
    if (tokens.isEmpty()) return false;

    String verb = tokens.getFirst().toUpperCase(Locale.ROOT);
    return switch (verb) {
      case "CHAT" -> consumeInboundChatOffer(at, sid, nick, tokens, spoiler);
      case "SEND" -> consumeInboundSendOffer(at, sid, nick, tokens, spoiler);
      case "RESUME" -> consumeInboundResumeControl(at, sid, nick, tokens, spoiler);
      case "ACCEPT" -> consumeInboundAcceptControl(at, sid, nick, tokens, spoiler);
      default -> {
        postInboundDccStatus(at, sid, nick, "Received unsupported DCC command: " + verb, spoiler);
        yield true;
      }
    };
  }

  private boolean consumeInboundResumeControl(
      Instant at, String sid, String fromNick, List<String> tokens, boolean spoiler) {
    if (tokens.size() < 4) {
      postInboundDccStatus(at, sid, fromNick, "Malformed DCC RESUME control message.", spoiler);
      return true;
    }

    String fileName = DccCommandSupport.sanitizeOfferFileName(tokens.get(1));
    Integer port = parsePort(tokens.get(2));
    Long offset = parseLong(tokens.get(3));
    if (port == null || offset == null || offset < 0L) {
      postInboundDccStatus(at, sid, fromNick, "Malformed DCC RESUME control message.", spoiler);
      return true;
    }

    dccCommandSupport.upsertTransfer(
        sid,
        fromNick,
        DccCommandSupport.transferEntryId(sid, fromNick, "control-resume"),
        "Control",
        "RESUME received",
        fileName + " (port " + port + ", offset " + DccCommandSupport.formatBytes(offset) + ")",
        null,
        DccTransferStore.ActionHint.NONE);
    postInboundDccStatus(
        at,
        sid,
        fromNick,
        "DCC RESUME control from "
            + fromNick
            + " for "
            + fileName
            + " at byte "
            + offset
            + " (port "
            + port
            + ").",
        spoiler);
    return true;
  }

  private boolean consumeInboundAcceptControl(
      Instant at, String sid, String fromNick, List<String> tokens, boolean spoiler) {
    if (tokens.size() < 4) {
      postInboundDccStatus(at, sid, fromNick, "Malformed DCC ACCEPT control message.", spoiler);
      return true;
    }

    String fileName = DccCommandSupport.sanitizeOfferFileName(tokens.get(1));
    Integer port = parsePort(tokens.get(2));
    Long offset = parseLong(tokens.get(3));
    if (port == null || offset == null || offset < 0L) {
      postInboundDccStatus(at, sid, fromNick, "Malformed DCC ACCEPT control message.", spoiler);
      return true;
    }

    dccCommandSupport.upsertTransfer(
        sid,
        fromNick,
        DccCommandSupport.transferEntryId(sid, fromNick, "control-accept"),
        "Control",
        "ACCEPT received",
        fileName + " (port " + port + ", offset " + DccCommandSupport.formatBytes(offset) + ")",
        null,
        DccTransferStore.ActionHint.NONE);
    postInboundDccStatus(
        at,
        sid,
        fromNick,
        "DCC ACCEPT control from "
            + fromNick
            + " for "
            + fileName
            + " at byte "
            + offset
            + " (port "
            + port
            + ").",
        spoiler);
    return true;
  }

  private boolean consumeInboundChatOffer(
      Instant at, String sid, String fromNick, List<String> tokens, boolean spoiler) {
    if (tokens.size() < 3) {
      postInboundDccStatus(at, sid, fromNick, "Malformed DCC CHAT offer.", spoiler);
      return true;
    }

    int hostIdx;
    int portIdx;
    String protocol = DccCommandSupport.normalizeToken(tokens.get(1)).toLowerCase(Locale.ROOT);
    if ("chat".equals(protocol)) {
      if (tokens.size() < 4) {
        postInboundDccStatus(at, sid, fromNick, "Malformed DCC CHAT offer.", spoiler);
        return true;
      }
      hostIdx = 2;
      portIdx = 3;
    } else {
      hostIdx = 1;
      portIdx = 2;
    }

    InetAddress host = parseDccHost(tokens.get(hostIdx));
    Integer port = parsePort(tokens.get(portIdx));
    if (host == null || port == null) {
      postInboundDccStatus(at, sid, fromNick, "Malformed DCC CHAT address/port.", spoiler);
      return true;
    }

    pendingChatOffers()
        .put(
            DccCommandSupport.peerKey(sid, fromNick),
            new PendingChatOffer(sid, fromNick, host, port, atOrNow(at)));
    dccCommandSupport.upsertTransfer(
        sid,
        fromNick,
        DccCommandSupport.transferEntryId(sid, fromNick, "chat-in"),
        "Chat (incoming)",
        "Offer received",
        host.getHostAddress() + ":" + port,
        null,
        DccTransferStore.ActionHint.ACCEPT_CHAT);
    postInboundDccStatus(
        at,
        sid,
        fromNick,
        "DCC CHAT offer from "
            + fromNick
            + " at "
            + host.getHostAddress()
            + ":"
            + port
            + ". Accept with /dcc accept "
            + fromNick
            + " or right-click nick -> DCC -> Accept Chat Offer.",
        spoiler);
    return true;
  }

  private boolean consumeInboundSendOffer(
      Instant at, String sid, String fromNick, List<String> tokens, boolean spoiler) {
    if (tokens.size() < 5) {
      postInboundDccStatus(at, sid, fromNick, "Malformed DCC SEND offer.", spoiler);
      return true;
    }

    String fileName = DccCommandSupport.sanitizeOfferFileName(tokens.get(1));
    InetAddress host = parseDccHost(tokens.get(2));
    Integer port = parsePort(tokens.get(3));
    Long size = parseLong(tokens.get(4));
    if (host == null || port == null || size == null || size < 0L) {
      postInboundDccStatus(at, sid, fromNick, "Malformed DCC SEND offer.", spoiler);
      return true;
    }

    pendingSendOffers()
        .put(
            DccCommandSupport.peerKey(sid, fromNick),
            new PendingSendOffer(sid, fromNick, fileName, host, port, size, atOrNow(at)));
    dccCommandSupport.upsertTransfer(
        sid,
        fromNick,
        DccCommandSupport.transferEntryId(sid, fromNick, "send-in"),
        "Receive file (incoming)",
        "Offer received",
        fileName + " (" + DccCommandSupport.formatBytes(size) + ")",
        0,
        DccTransferStore.ActionHint.GET_FILE);

    postInboundDccStatus(
        at,
        sid,
        fromNick,
        "DCC SEND offer from "
            + fromNick
            + ": "
            + fileName
            + " ("
            + DccCommandSupport.formatBytes(size)
            + "). Accept with /dcc get "
            + fromNick
            + " or right-click nick -> DCC -> Get Pending File.",
        spoiler);
    return true;
  }

  private void postInboundDccStatus(
      Instant at, String sid, String fromNick, String text, boolean spoiler) {
    TargetRef pm = dccCommandSupport.ensurePmTarget(sid, fromNick);
    if (spoiler) {
      dccCommandSupport.appendSpoilerChatAt(pm, atOrNow(at), DCC_TAG, text);
    } else {
      dccCommandSupport.appendStatusAt(pm, atOrNow(at), DCC_TAG, text);
    }
    dccCommandSupport.markUnreadIfInactive(pm);
  }

  private java.util.concurrent.ConcurrentMap<String, PendingChatOffer> pendingChatOffers() {
    return dccRuntimeRegistry.pendingChatOffers();
  }

  private java.util.concurrent.ConcurrentMap<String, PendingSendOffer> pendingSendOffers() {
    return dccRuntimeRegistry.pendingSendOffers();
  }

  private static List<String> splitDccTokens(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return List.of();

    ArrayList<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (ch == '"') {
        inQuotes = !inQuotes;
        continue;
      }
      if (!inQuotes && Character.isWhitespace(ch)) {
        if (!current.isEmpty()) {
          out.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(ch);
    }
    if (!current.isEmpty()) out.add(current.toString());
    return out;
  }

  private static Instant atOrNow(Instant at) {
    return (at == null) ? Instant.now() : at;
  }

  private static InetAddress parseDccHost(String token) {
    String s = DccCommandSupport.normalizeToken(token);
    if (s.isEmpty()) return null;
    try {
      if (s.indexOf('.') >= 0 || s.indexOf(':') >= 0) {
        return InetAddress.getByName(s);
      }
      long packed = Long.parseLong(s);
      long u = packed & 0xFFFF_FFFFL;
      byte[] bytes =
          new byte[] {
            (byte) ((u >>> 24) & 0xFF),
            (byte) ((u >>> 16) & 0xFF),
            (byte) ((u >>> 8) & 0xFF),
            (byte) (u & 0xFF)
          };
      return InetAddress.getByAddress(bytes);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Integer parsePort(String token) {
    Long v = parseLong(token);
    if (v == null) return null;
    if (v <= 0L || v > 65535L) return null;
    return v.intValue();
  }

  private static Long parseLong(String token) {
    try {
      return Long.parseLong(DccCommandSupport.normalizeToken(token));
    } catch (Exception ignored) {
      return null;
    }
  }
}

record PendingChatOffer(
    String serverId, String fromNick, InetAddress host, int port, Instant offeredAt) {}

record PendingSendOffer(
    String serverId,
    String fromNick,
    String fileName,
    InetAddress host,
    int port,
    long size,
    Instant offeredAt) {}
