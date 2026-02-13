package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.ignore.IgnoreListService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Handles outbound ignore-related slash commands extracted from {@code IrcMediator}.
 *
 * <p>Includes: /ignore, /unignore, /ignorelist, /softignore, /unsoftignore, /softignorelist.
 *
 * <p>Behavior is intended to be preserved.
 */
@Component
public class OutboundIgnoreCommandService {

  private final UiPort ui;
  private final TargetCoordinator targetCoordinator;
  private final IgnoreListService ignoreListService;

  public OutboundIgnoreCommandService(
      UiPort ui,
      TargetCoordinator targetCoordinator,
      IgnoreListService ignoreListService
  ) {
    this.ui = ui;
    this.targetCoordinator = targetCoordinator;
    this.ignoreListService = ignoreListService;
  }

  public void handleIgnore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }

    String arg = maskOrNick == null ? "" : maskOrNick.trim();
    if (arg.isEmpty()) {
      ui.appendStatus(at, "(ignore)", "Usage: /ignore <maskOrNick>");
      return;
    }

    boolean added = ignoreListService.addMask(at.serverId(), arg);
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
    if (added) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(ignore)", "Ignoring: " + stored);
    } else {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(ignore)", "Already ignored: " + stored);
    }
  }

  public void handleUnignore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(unignore)", "Select a server first.");
      return;
    }

    String arg = maskOrNick == null ? "" : maskOrNick.trim();
    if (arg.isEmpty()) {
      ui.appendStatus(at, "(unignore)", "Usage: /unignore <maskOrNick>");
      return;
    }

    boolean removed = ignoreListService.removeMask(at.serverId(), arg);
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
    if (removed) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(unignore)", "Removed ignore: " + stored);
    } else {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(unignore)", "Not in ignore list: " + stored);
    }
  }

  public void handleIgnoreList() {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(ignore)", "Select a server first.");
      return;
    }

    List<String> masks = ignoreListService.listMasks(at.serverId());
    TargetRef status = new TargetRef(at.serverId(), "status");
    if (masks.isEmpty()) {
      ui.appendStatus(status, "(ignore)", "Ignore list is empty.");
      return;
    }

    ui.appendStatus(status, "(ignore)", "Ignore masks (" + masks.size() + "): ");
    for (String m : masks) {
      ui.appendStatus(status, "(ignore)", "  - " + m);
    }
  }


  public void handleSoftIgnore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(soft-ignore)", "Select a server first.");
      return;
    }

    String arg = maskOrNick == null ? "" : maskOrNick.trim();
    if (arg.isEmpty()) {
      ui.appendStatus(at, "(soft-ignore)", "Usage: /softignore <maskOrNick>");
      return;
    }

    boolean added = ignoreListService.addSoftMask(at.serverId(), arg);
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
    if (added) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(soft-ignore)", "Soft-ignoring: " + stored);
    } else {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(soft-ignore)", "Already soft-ignored: " + stored);
    }
  }

  public void handleUnsoftIgnore(String maskOrNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(unsoftignore)", "Select a server first.");
      return;
    }

    String arg = maskOrNick == null ? "" : maskOrNick.trim();
    if (arg.isEmpty()) {
      ui.appendStatus(at, "(unsoftignore)", "Usage: /unsoftignore <maskOrNick>");
      return;
    }

    boolean removed = ignoreListService.removeSoftMask(at.serverId(), arg);
    String stored = IgnoreListService.normalizeMaskOrNickToHostmask(arg);
    if (removed) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(unsoftignore)", "Removed soft-ignore: " + stored);
    } else {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(unsoftignore)", "Not in soft-ignore list: " + stored);
    }
  }

  public void handleSoftIgnoreList() {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(targetCoordinator.safeStatusTarget(), "(soft-ignore)", "Select a server first.");
      return;
    }

    List<String> masks = ignoreListService.listSoftMasks(at.serverId());
    TargetRef status = new TargetRef(at.serverId(), "status");
    if (masks.isEmpty()) {
      ui.appendStatus(status, "(soft-ignore)", "Soft-ignore list is empty.");
      return;
    }

    ui.appendStatus(status, "(soft-ignore)", "Soft-ignore masks (" + masks.size() + "): ");
    for (String m : masks) {
      ui.appendStatus(status, "(soft-ignore)", "  - " + m);
    }
  }

}