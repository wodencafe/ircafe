package cafe.woden.ircclient.app.outbound.ignore;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public final class OutboundIgnoreCommandService {

  @NonNull private final IgnoreHardCommandSupport ignoreHardCommandSupport;
  @NonNull private final IgnoreSoftCommandSupport ignoreSoftCommandSupport;

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
