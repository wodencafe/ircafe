package cafe.woden.ircclient.ui.servertree.view;

import java.awt.event.MouseEvent;
import java.util.Objects;

/** Resolves tree tooltip text by prioritizing overlay action-button tooltips over node tooltips. */
public final class ServerTreeTooltipResolver {

  private final ServerTreeServerActionOverlay serverActionOverlay;
  private final ServerTreeTooltipProvider tooltipProvider;

  public ServerTreeTooltipResolver(
      ServerTreeServerActionOverlay serverActionOverlay,
      ServerTreeTooltipProvider tooltipProvider) {
    this.serverActionOverlay = Objects.requireNonNull(serverActionOverlay, "serverActionOverlay");
    this.tooltipProvider = Objects.requireNonNull(tooltipProvider, "tooltipProvider");
  }

  public String toolTipForEvent(MouseEvent event) {
    String overlayTip = serverActionOverlay.toolTipForEvent(event);
    if (overlayTip != null && !overlayTip.isBlank()) {
      return overlayTip;
    }
    return tooltipProvider.toolTipForEvent(event);
  }
}
