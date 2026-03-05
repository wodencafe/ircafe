package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelDisconnectStateManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelInteractionApi;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetSelectionCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTypingActivityManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUnreadStateCoordinator;
import javax.swing.Timer;

/** Container for channel interaction collaborators wired during server tree construction. */
public record ServerTreeChannelInteractionCollaborators(
    Timer typingActivityTimer,
    ServerTreeTypingActivityManager typingActivityManager,
    ServerTreeChannelDisconnectStateManager channelDisconnectStateManager,
    ServerTreeTargetSelectionCoordinator targetSelectionCoordinator,
    ServerTreeUnreadStateCoordinator unreadStateCoordinator,
    ServerTreeChannelInteractionApi channelInteractionApi) {}
