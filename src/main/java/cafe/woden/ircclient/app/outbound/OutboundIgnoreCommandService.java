package cafe.woden.ircclient.app.outbound;

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
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/**
 * Handles outbound ignore-related slash commands extracted from {@code IrcMediator}.
 *
 * <p>Includes: /ignore, /unignore, /ignorelist, /softignore, /unsoftignore, /softignorelist.
 *
 * <p>Behavior is intended to be preserved.
 */
@Component
@ApplicationLayer
public class OutboundIgnoreCommandService {

  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final IgnoreListQueryPort ignoreListQueryPort;
  private final IgnoreListCommandPort ignoreListCommandPort;
  private final IgnoreSoftCommandSupport ignoreSoftCommandSupport;

  public OutboundIgnoreCommandService(
      UiPort ui,
      TargetCoordinator targetCoordinator,
      IgnoreListQueryPort ignoreListQueryPort,
      IgnoreListCommandPort ignoreListCommandPort) {
    this.ui = ui;
    this.targetCoordinator = targetCoordinator;
    this.ignoreListQueryPort = ignoreListQueryPort;
    this.ignoreListCommandPort = ignoreListCommandPort;
    this.ignoreSoftCommandSupport =
        new IgnoreSoftCommandSupport(
            ui, targetCoordinator, ignoreListQueryPort, ignoreListCommandPort);
  }

  public void handleIgnore(String maskOrNick) {
    TargetRef active = targetCoordinator.getActiveTarget();
    IgnoreCommandParsingSupport.IrssiIgnoreSpec spec =
        IgnoreCommandParsingSupport.parseIrssiIgnoreSpec(maskOrNick);
    String sid = resolveServerIdForIgnore(active, spec.network());
    if (sid.isEmpty()) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }

    if (spec.listRequested() && !spec.except()) {
      handleIgnoreListForServer(sid, "(ignore)");
      appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
      return;
    }

    if (spec.mask().isEmpty()) {
      ui.appendStatus(
          new TargetRef(sid, "status"),
          "(ignore)",
          "Usage: /ignore [-options] [levels] <maskOrNick>");
      return;
    }

    if (spec.except()) {
      handleUnignoreForServer(sid, spec.mask(), true);
      appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
      return;
    }

    Long expiresAtEpochMs =
        IgnoreCommandParsingSupport.parseExpiryEpochMs(spec.time(), System.currentTimeMillis());
    if (!spec.time().isBlank() && expiresAtEpochMs == null) {
      ui.appendStatus(
          new TargetRef(sid, "status"),
          "(ignore)",
          "Invalid -time value: \"" + spec.time() + "\" (use values like 10min, 2h, 1d, 1w).");
      return;
    }

    IgnoreTextPatternMode patternMode =
        spec.patternText().isBlank() ? IgnoreTextPatternMode.GLOB : spec.patternMode();
    if (!spec.patternText().isBlank() && patternMode == IgnoreTextPatternMode.REGEXP) {
      if (!IgnoreCommandParsingSupport.isValidRegexPattern(spec.patternText())) {
        ui.appendStatus(
            new TargetRef(sid, "status"),
            "(ignore)",
            "Invalid -pattern regexp: \"" + spec.patternText() + "\"");
        return;
      }
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
      ui.appendStatus(new TargetRef(sid, "status"), "(ignore)", "Ignoring: " + stored);
    } else if (addResult == IgnoreAddMaskResult.UPDATED) {
      ui.appendStatus(new TargetRef(sid, "status"), "(ignore)", "Updated ignore: " + stored);
    } else {
      ui.appendStatus(new TargetRef(sid, "status"), "(ignore)", "Already ignored: " + stored);
    }
    appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
  }

  public void handleUnignore(String maskOrNick) {
    TargetRef active = targetCoordinator.getActiveTarget();
    IgnoreCommandParsingSupport.IrssiIgnoreSpec spec =
        IgnoreCommandParsingSupport.parseIrssiIgnoreSpec(maskOrNick);
    String sid = resolveServerIdForIgnore(active, spec.network());
    if (sid.isEmpty()) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(unignore)", "Select a server first.");
      return;
    }

    if (spec.mask().isEmpty()) {
      ui.appendStatus(
          new TargetRef(sid, "status"), "(unignore)", "Usage: /unignore <maskOrNick|index>");
      return;
    }

    String arg = spec.mask();
    if (isPositiveInteger(arg)) {
      int idx = Integer.parseInt(arg);
      List<String> masks = ignoreListQueryPort.listMasks(sid);
      if (idx < 1 || idx > masks.size()) {
        ui.appendStatus(
            new TargetRef(sid, "status"),
            "(unignore)",
            "Ignore index out of range: " + idx + " (1.." + masks.size() + ")");
        return;
      }
      String byIndex = masks.get(idx - 1);
      handleUnignoreForServer(sid, byIndex, false);
      appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
      return;
    }

    handleUnignoreForServer(sid, arg, false);
    appendCompatibilityNotes(new TargetRef(sid, "status"), spec);
  }

  private void handleUnignoreForServer(
      String serverId, String maskOrNick, boolean exceptCompatibility) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    String arg = Objects.toString(maskOrNick, "").trim();
    if (arg.isEmpty()) return;

    boolean removed = ignoreListCommandPort.removeMask(sid, arg);
    String stored = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(arg);
    if (removed) {
      ui.appendStatus(new TargetRef(sid, "status"), "(unignore)", "Removed ignore: " + stored);
    } else {
      ui.appendStatus(new TargetRef(sid, "status"), "(unignore)", "Not in ignore list: " + stored);
    }

    if (exceptCompatibility) {
      ui.appendStatus(
          new TargetRef(sid, "status"),
          "(ignore)",
          "Applied irssi-style -except as /unignore (IRCafe compatibility mode).");
    }
  }

  public void handleIgnoreList() {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }

    handleIgnoreListForServer(at.serverId(), "(ignore)");
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
      String m = masks.get(i);
      List<String> levels =
          IgnoreLevels.normalizeConfigured(ignoreListQueryPort.levelsForHardMask(sid, m));
      List<String> channels = ignoreListQueryPort.channelsForHardMask(sid, m);
      long expiresAtEpochMs = ignoreListQueryPort.expiresAtEpochMsForHardMask(sid, m);
      String pattern = Objects.toString(ignoreListQueryPort.patternForHardMask(sid, m), "").trim();
      IgnoreTextPatternMode patternMode = ignoreListQueryPort.patternModeForHardMask(sid, m);
      boolean replies = ignoreListQueryPort.repliesForHardMask(sid, m);
      String display =
          IgnoreListRenderingSupport.formatHardMaskDisplay(
              m, levels, channels, expiresAtEpochMs, pattern, patternMode, replies);
      ui.appendStatus(status, tag, "  " + (i + 1) + ") " + display);
    }
  }

  public void handleSoftIgnore(String maskOrNick) {
    ignoreSoftCommandSupport.handleSoftIgnore(maskOrNick);
  }

  public void handleUnsoftIgnore(String maskOrNick) {
    ignoreSoftCommandSupport.handleUnsoftIgnore(maskOrNick);
  }

  public void handleSoftIgnoreList() {
    ignoreSoftCommandSupport.handleSoftIgnoreList();
  }

  private static String resolveServerIdForIgnore(TargetRef active, String network) {
    String net = Objects.toString(network, "").trim();
    if (!net.isEmpty()) return net;
    return active == null ? "" : Objects.toString(active.serverId(), "").trim();
  }

  private static boolean isPositiveInteger(String s) {
    String v = Objects.toString(s, "").trim();
    if (v.isEmpty()) return false;
    for (int i = 0; i < v.length(); i++) {
      if (!Character.isDigit(v.charAt(i))) return false;
    }
    try {
      return Integer.parseInt(v) > 0;
    } catch (Exception ignored) {
      return false;
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
}
