package cafe.woden.ircclient.app.outbound.ignore;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.ignore.api.IgnoreAddMaskResult;
import cafe.woden.ircclient.ignore.api.IgnoreLevels;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.IgnoreMaskNormalizer;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared hard-ignore command flow support for outbound ignore commands. */
@ApplicationLayer
@RequiredArgsConstructor
final class IgnoreHardCommandSupport {

  @NonNull private final UiPort ui;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final IgnoreListQueryPort ignoreListQueryPort;
  @NonNull private final IgnoreListCommandPort ignoreListCommandPort;

  void handleIgnore(String maskOrNick) {
    TargetRef active = targetCoordinator.getActiveTarget();
    IgnoreCommandParsingSupport.IrssiIgnoreSpec spec =
        IgnoreCommandParsingSupport.parseIrssiIgnoreSpec(maskOrNick);
    String sid = resolveServerIdForIgnore(active, spec.network());
    if (sid.isEmpty()) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    if (spec.listRequested() && !spec.except()) {
      handleIgnoreListForServer(sid, "(ignore)");
      appendCompatibilityNotes(status, spec);
      return;
    }

    if (spec.mask().isEmpty()) {
      ui.appendStatus(status, "(ignore)", "Usage: /ignore [-options] [levels] <maskOrNick>");
      return;
    }

    if (spec.except()) {
      handleUnignoreForServer(sid, spec.mask(), true);
      appendCompatibilityNotes(status, spec);
      return;
    }

    Long expiresAtEpochMs =
        IgnoreCommandParsingSupport.parseExpiryEpochMs(spec.time(), System.currentTimeMillis());
    if (!spec.time().isBlank() && expiresAtEpochMs == null) {
      ui.appendStatus(
          status,
          "(ignore)",
          "Invalid -time value: \"" + spec.time() + "\" (use values like 10min, 2h, 1d, 1w).");
      return;
    }

    IgnoreTextPatternMode patternMode =
        spec.patternText().isBlank() ? IgnoreTextPatternMode.GLOB : spec.patternMode();
    if (!spec.patternText().isBlank()
        && patternMode == IgnoreTextPatternMode.REGEXP
        && !IgnoreCommandParsingSupport.isValidRegexPattern(spec.patternText())) {
      ui.appendStatus(
          status, "(ignore)", "Invalid -pattern regexp: \"" + spec.patternText() + "\"");
      return;
    }

    IgnoreAddMaskResult addResult =
        ignoreListCommandPort.addMaskWithLevels(
            sid,
            spec.mask(),
            spec.levels(),
            spec.channels(),
            expiresAtEpochMs,
            spec.patternText(),
            patternMode,
            spec.replies());
    String stored = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(spec.mask());
    if (addResult == IgnoreAddMaskResult.ADDED) {
      ui.appendStatus(status, "(ignore)", "Ignoring: " + stored);
    } else if (addResult == IgnoreAddMaskResult.UPDATED) {
      ui.appendStatus(status, "(ignore)", "Updated ignore: " + stored);
    } else {
      ui.appendStatus(status, "(ignore)", "Already ignored: " + stored);
    }
    appendCompatibilityNotes(status, spec);
  }

  void handleUnignore(String maskOrNick) {
    TargetRef active = targetCoordinator.getActiveTarget();
    IgnoreCommandParsingSupport.IrssiIgnoreSpec spec =
        IgnoreCommandParsingSupport.parseIrssiIgnoreSpec(maskOrNick);
    String sid = resolveServerIdForIgnore(active, spec.network());
    if (sid.isEmpty()) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(unignore)", "Select a server first.");
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    if (spec.mask().isEmpty()) {
      ui.appendStatus(status, "(unignore)", "Usage: /unignore <maskOrNick|index>");
      return;
    }

    String arg = spec.mask();
    if (isPositiveInteger(arg)) {
      int idx = Integer.parseInt(arg);
      List<String> masks = ignoreListQueryPort.listMasks(sid);
      if (idx < 1 || idx > masks.size()) {
        ui.appendStatus(
            status,
            "(unignore)",
            "Ignore index out of range: " + idx + " (1.." + masks.size() + ")");
        return;
      }
      handleUnignoreForServer(sid, masks.get(idx - 1), false);
      appendCompatibilityNotes(status, spec);
      return;
    }

    handleUnignoreForServer(sid, arg, false);
    appendCompatibilityNotes(status, spec);
  }

  void handleIgnoreList() {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }
    handleIgnoreListForServer(at.serverId(), "(ignore)");
  }

  private void handleUnignoreForServer(
      String serverId, String maskOrNick, boolean exceptCompatibility) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    String arg = Objects.toString(maskOrNick, "").trim();
    if (arg.isEmpty()) return;

    boolean removed = ignoreListCommandPort.removeMask(sid, arg);
    String stored = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(arg);
    TargetRef status = new TargetRef(sid, "status");
    if (removed) {
      ui.appendStatus(status, "(unignore)", "Removed ignore: " + stored);
    } else {
      ui.appendStatus(status, "(unignore)", "Not in ignore list: " + stored);
    }

    if (exceptCompatibility) {
      ui.appendStatus(
          status,
          "(ignore)",
          "Applied irssi-style -except as /unignore (IRCafe compatibility mode).");
    }
  }

  private void handleIgnoreListForServer(String serverId, String tag) {
    String sid = Objects.toString(serverId, "").trim();
    ignoreListCommandPort.pruneExpiredHardMasks(sid, System.currentTimeMillis());
    TargetRef status = new TargetRef(sid, "status");
    List<String> masks = ignoreListQueryPort.listMasks(sid);
    if (masks.isEmpty()) {
      ui.appendStatus(status, tag, "Ignore list is empty.");
      return;
    }

    ui.appendStatus(status, tag, "Ignore masks (" + masks.size() + "): ");
    for (int i = 0; i < masks.size(); i++) {
      String mask = masks.get(i);
      List<String> levels =
          IgnoreLevels.normalizeConfigured(ignoreListQueryPort.levelsForHardMask(sid, mask));
      List<String> channels = ignoreListQueryPort.channelsForHardMask(sid, mask);
      long expiresAtEpochMs = ignoreListQueryPort.expiresAtEpochMsForHardMask(sid, mask);
      String pattern =
          Objects.toString(ignoreListQueryPort.patternForHardMask(sid, mask), "").trim();
      IgnoreTextPatternMode patternMode = ignoreListQueryPort.patternModeForHardMask(sid, mask);
      boolean replies = ignoreListQueryPort.repliesForHardMask(sid, mask);
      String display =
          IgnoreListRenderingSupport.formatHardMaskDisplay(
              mask, levels, channels, expiresAtEpochMs, pattern, patternMode, replies);
      ui.appendStatus(status, tag, "  " + (i + 1) + ") " + display);
    }
  }

  private void appendCompatibilityNotes(
      TargetRef out, IgnoreCommandParsingSupport.IrssiIgnoreSpec spec) {
    if (spec == null || out == null) return;

    if (spec.patternMode() != IgnoreTextPatternMode.GLOB && spec.patternText().isBlank()) {
      ui.appendStatus(
          out,
          "(ignore)",
          "Compatibility: -regexp/-full provided without -pattern; modifier ignored.");
    }
    if (!spec.reason().isEmpty()) {
      ui.appendStatus(
          out,
          "(ignore)",
          "Compatibility: trailing reason text parsed but not persisted: \""
              + spec.reason()
              + "\"");
    }
  }

  private static String resolveServerIdForIgnore(TargetRef active, String network) {
    String net = Objects.toString(network, "").trim();
    if (!net.isEmpty()) return net;
    return active == null ? "" : Objects.toString(active.serverId(), "").trim();
  }

  private static boolean isPositiveInteger(String input) {
    String value = Objects.toString(input, "").trim();
    if (value.isEmpty()) return false;
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) return false;
    }
    try {
      return Integer.parseInt(value) > 0;
    } catch (Exception ignored) {
      return false;
    }
  }
}
