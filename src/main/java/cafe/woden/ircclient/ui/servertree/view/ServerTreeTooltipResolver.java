package cafe.woden.ircclient.ui.servertree.view;

import java.awt.event.MouseEvent;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Resolves tree tooltip text by prioritizing overlay action-button tooltips over node tooltips. */
@Component
public final class ServerTreeTooltipResolver {

  public String toolTipForEvent(
      ServerTreeServerActionOverlay serverActionOverlay,
      ServerTreeTooltipProvider tooltipProvider,
      MouseEvent event) {
    String overlayTip =
        Objects.requireNonNull(serverActionOverlay, "serverActionOverlay").toolTipForEvent(event);
    if (overlayTip != null && !overlayTip.isBlank()) {
      return overlayTip;
    }
    return Objects.requireNonNull(tooltipProvider, "tooltipProvider").toolTipForEvent(event);
  }
}
