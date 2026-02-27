package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.ignore.api.IgnoreAddMaskResult;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.IgnoreTextPatternMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboundIgnoreCommandServicePortMockVerifyTest {

  private final UiPort ui = mock(UiPort.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final IgnoreListQueryPort queryPort = mock(IgnoreListQueryPort.class);
  private final IgnoreListCommandPort commandPort = mock(IgnoreListCommandPort.class);

  private final OutboundIgnoreCommandService service =
      new OutboundIgnoreCommandService(ui, targetCoordinator, queryPort, commandPort);

  @Test
  void ignoreListPathUsesCommandForPruneAndQueryForRenderingOnly() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(queryPort.listMasks("libera")).thenReturn(List.of());

    service.handleIgnore(" ");

    verify(commandPort).pruneExpiredHardMasks(eq("libera"), anyLong());
    verify(queryPort).listMasks("libera");
    verify(commandPort, never())
        .addMaskWithLevels(
            "libera", "badnick", List.of(), List.of(), null, "", IgnoreTextPatternMode.GLOB, false);
  }

  @Test
  void addPathUsesCommandPortOnlyForMutation() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(commandPort.addMaskWithLevels(
            "libera", "badnick", List.of(), List.of(), null, "", IgnoreTextPatternMode.GLOB, false))
        .thenReturn(IgnoreAddMaskResult.ADDED);

    service.handleIgnore("badnick");

    verify(commandPort)
        .addMaskWithLevels(
            "libera", "badnick", List.of(), List.of(), null, "", IgnoreTextPatternMode.GLOB, false);
    verify(queryPort, never()).listMasks("libera");
    verifyNoMoreInteractions(queryPort);
  }

  @Test
  void unignoreByIndexUsesQueryResolutionThenCommandRemoval() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(queryPort.listMasks("libera")).thenReturn(List.of("a!*@*", "b!*@*"));
    when(commandPort.removeMask("libera", "b!*@*")).thenReturn(true);

    service.handleUnignore("2");

    verify(queryPort).listMasks("libera");
    verify(commandPort).removeMask("libera", "b!*@*");
  }
}
