package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.model.TargetRef;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared DCC target, transfer-tracking, and formatting support. */
@ApplicationLayer
@RequiredArgsConstructor
final class DccCommandSupport {

  @NonNull private final UiPort ui;
  @NonNull private final TargetCoordinator targetCoordinator;
  private final DccTransferStore dccTransferStore;

  TargetRef ensurePmTarget(String sid, String nick) {
    TargetRef pm = new TargetRef(sid, nick);
    ui.ensureTargetExists(pm);
    return pm;
  }

  void markUnreadIfInactive(TargetRef target) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (!target.equals(active)) {
      ui.markUnread(target);
    }
  }

  void appendStatusAt(TargetRef target, Instant at, String tag, String text) {
    ui.appendStatusAt(target, at, tag, text);
  }

  void appendSpoilerChatAt(TargetRef target, Instant at, String tag, String text) {
    ui.appendSpoilerChatAt(target, at, tag, text);
  }

  void upsertTransfer(
      String sid,
      String nick,
      String entryId,
      String kind,
      String status,
      String detail,
      Integer progressPercent,
      DccTransferStore.ActionHint actionHint) {
    upsertTransfer(sid, nick, entryId, kind, status, detail, "", progressPercent, actionHint);
  }

  void upsertTransfer(
      String sid,
      String nick,
      String entryId,
      String kind,
      String status,
      String detail,
      String localPath,
      Integer progressPercent,
      DccTransferStore.ActionHint actionHint) {
    if (dccTransferStore == null) return;
    dccTransferStore.upsert(
        sid, entryId, nick, kind, status, detail, localPath, progressPercent, actionHint);
  }

  static String transferEntryId(String sid, String nick, String suffix) {
    String server = normalizeToken(sid).toLowerCase(Locale.ROOT);
    String peer = normalizeNick(nick).toLowerCase(Locale.ROOT);
    String sfx = normalizeToken(suffix).toLowerCase(Locale.ROOT);
    return server + "|" + peer + "|" + sfx;
  }

  static String peerKey(String sid, String nick) {
    return normalizeToken(sid).toLowerCase(Locale.ROOT)
        + "\u0000"
        + normalizeToken(nick).toLowerCase(Locale.ROOT);
  }

  static String sanitizeOfferFileName(String fileName) {
    String name = Objects.toString(fileName, "").trim();
    if (name.isEmpty()) return "download.bin";
    name = name.replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0 && slash + 1 < name.length()) {
      name = name.substring(slash + 1);
    }
    name = name.replace("\r", "_").replace("\n", "_").replace("\u0000", "_");
    if (name.isBlank()) return "download.bin";
    return name;
  }

  static String normalizeToken(String raw) {
    return Objects.toString(raw, "").trim();
  }

  static String normalizeNick(String raw) {
    String nick = normalizeToken(raw);
    if (nick.indexOf(' ') >= 0) return "";
    return nick;
  }

  static String formatBytes(long bytes) {
    if (bytes < 0L) return "?";
    if (bytes < 1024L) return bytes + " B";
    double kb = bytes / 1024.0;
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KiB", kb);
    double mb = kb / 1024.0;
    if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MiB", mb);
    double gb = mb / 1024.0;
    return String.format(Locale.ROOT, "%.2f GiB", gb);
  }
}
