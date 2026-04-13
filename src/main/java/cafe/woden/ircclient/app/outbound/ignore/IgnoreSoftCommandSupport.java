package cafe.woden.ircclient.app.outbound.ignore;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.IgnoreMaskNormalizer;
import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Shared soft-ignore command flow support for outbound ignore commands. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
final class IgnoreSoftCommandSupport {

  @NonNull private final UiPort ui;
  @NonNull private final TargetCoordinator targetCoordinator;
  @NonNull private final IgnoreListQueryPort ignoreListQueryPort;
  @NonNull private final IgnoreListCommandPort ignoreListCommandPort;

  void handleSoftIgnore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(soft-ignore)", "Select a server first.");
      return;
    }

    String arg = Objects.toString(maskOrNick, "").trim();
    if (arg.isEmpty()) {
      ui.appendStatus(at, "(soft-ignore)", "Usage: /softignore <maskOrNick>");
      return;
    }

    boolean added = ignoreListCommandPort.addSoftMask(at.serverId(), arg);
    String stored = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(arg);
    TargetRef status = new TargetRef(at.serverId(), "status");
    if (added) {
      ui.appendStatus(status, "(soft-ignore)", "Soft-ignoring: " + stored);
    } else {
      ui.appendStatus(status, "(soft-ignore)", "Already soft-ignored: " + stored);
    }
  }

  void handleUnsoftIgnore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(unsoftignore)", "Select a server first.");
      return;
    }

    String arg = Objects.toString(maskOrNick, "").trim();
    if (arg.isEmpty()) {
      ui.appendStatus(at, "(unsoftignore)", "Usage: /unsoftignore <maskOrNick>");
      return;
    }

    boolean removed = ignoreListCommandPort.removeSoftMask(at.serverId(), arg);
    String stored = IgnoreMaskNormalizer.normalizeMaskOrNickToHostmask(arg);
    TargetRef status = new TargetRef(at.serverId(), "status");
    if (removed) {
      ui.appendStatus(status, "(unsoftignore)", "Removed soft-ignore: " + stored);
    } else {
      ui.appendStatus(status, "(unsoftignore)", "Not in soft-ignore list: " + stored);
    }
  }

  void handleSoftIgnoreList() {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(
          targetCoordinator.safeStatusTarget(), "(soft-ignore)", "Select a server first.");
      return;
    }

    List<String> masks = ignoreListQueryPort.listSoftMasks(at.serverId());
    TargetRef status = new TargetRef(at.serverId(), "status");
    if (masks.isEmpty()) {
      ui.appendStatus(status, "(soft-ignore)", "Soft-ignore list is empty.");
      return;
    }

    ui.appendStatus(status, "(soft-ignore)", "Soft-ignore masks (" + masks.size() + "): ");
    for (String mask : masks) {
      ui.appendStatus(status, "(soft-ignore)", "  - " + mask);
    }
  }
}
