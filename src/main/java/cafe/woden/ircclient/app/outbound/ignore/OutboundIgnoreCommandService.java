package cafe.woden.ircclient.app.outbound.ignore;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
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

  private final IgnoreHardCommandSupport ignoreHardCommandSupport;
  private final IgnoreSoftCommandSupport ignoreSoftCommandSupport;

  public OutboundIgnoreCommandService(
      UiPort ui,
      TargetCoordinator targetCoordinator,
      IgnoreListQueryPort ignoreListQueryPort,
      IgnoreListCommandPort ignoreListCommandPort) {
    this.ignoreHardCommandSupport =
        new IgnoreHardCommandSupport(
            ui, targetCoordinator, ignoreListQueryPort, ignoreListCommandPort);
    this.ignoreSoftCommandSupport =
        new IgnoreSoftCommandSupport(
            ui, targetCoordinator, ignoreListQueryPort, ignoreListCommandPort);
  }

  public void handleIgnore(String maskOrNick) {
    ignoreHardCommandSupport.handleIgnore(maskOrNick);
  }

  public void handleUnignore(String maskOrNick) {
    ignoreHardCommandSupport.handleUnignore(maskOrNick);
  }

  public void handleIgnoreList() {
    ignoreHardCommandSupport.handleIgnoreList();
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
}
