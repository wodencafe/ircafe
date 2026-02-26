package cafe.woden.ircclient.app.outbound;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.ignore.IgnoreListService;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboundIgnoreCommandServiceTest {

  private final UiPort ui = mock(UiPort.class);
  private final TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
  private final IgnoreListService ignoreListService = mock(IgnoreListService.class);

  private final OutboundIgnoreCommandService service =
      new OutboundIgnoreCommandService(ui, targetCoordinator, ignoreListService);

  @Test
  void ignoreWithoutArgsListsMasksForActiveServer() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListService.listMasks("libera")).thenReturn(List.of("alice!*@*", "*!*@bad.host"));

    service.handleIgnore("   ");

    TargetRef status = new TargetRef("libera", "status");
    verify(ui).appendStatus(status, "(ignore)", "Ignore masks (2): ");
    verify(ui).appendStatus(status, "(ignore)", "  1) alice!*@*");
    verify(ui).appendStatus(status, "(ignore)", "  2) *!*@bad.host");
  }

  @Test
  void ignoreParsesIrssiLevelsAndAddsMask() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListService.addMaskWithLevels(
            "libera", "*!*@bad.host", List.of("MSGS", "NOTICES"), List.of()))
        .thenReturn(IgnoreListService.AddMaskResult.ADDED);

    service.handleIgnore("MSGS NOTICES *!*@bad.host");

    verify(ignoreListService)
        .addMaskWithLevels("libera", "*!*@bad.host", List.of("MSGS", "NOTICES"), List.of());
    verify(ui)
        .appendStatus(new TargetRef("libera", "status"), "(ignore)", "Ignoring: *!*@bad.host");
    verify(ui, org.mockito.Mockito.never())
        .appendStatus(
            eq(new TargetRef("libera", "status")),
            eq("(ignore)"),
            contains("ignore levels were parsed"));
  }

  @Test
  void ignoreWithNetworkOptionCanWorkWithoutActiveTarget() {
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(ignoreListService.addMaskWithLevels("libera", "badnick", List.of(), List.of()))
        .thenReturn(IgnoreListService.AddMaskResult.ADDED);

    service.handleIgnore("-network libera badnick");

    verify(ignoreListService).addMaskWithLevels("libera", "badnick", List.of(), List.of());
    verify(ui).appendStatus(new TargetRef("libera", "status"), "(ignore)", "Ignoring: badnick!*@*");
  }

  @Test
  void ignoreParsesChannelsOptionAndStoresChannelScopedRule() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListService.addMaskWithLevels(
            "libera", "*!*@bad.host", List.of("MSGS"), List.of("#ircafe", "#ops")))
        .thenReturn(IgnoreListService.AddMaskResult.ADDED);

    service.handleIgnore("-channels #ircafe,#ops MSGS *!*@bad.host");

    verify(ignoreListService)
        .addMaskWithLevels("libera", "*!*@bad.host", List.of("MSGS"), List.of("#ircafe", "#ops"));
    verify(ui)
        .appendStatus(new TargetRef("libera", "status"), "(ignore)", "Ignoring: *!*@bad.host");
  }

  @Test
  void unignoreSupportsNumericIndex() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListService.listMasks("libera")).thenReturn(List.of("a!*@*", "b!*@*"));
    when(ignoreListService.removeMask("libera", "b!*@*")).thenReturn(true);

    service.handleUnignore("2");

    verify(ignoreListService).removeMask("libera", "b!*@*");
    verify(ui)
        .appendStatus(new TargetRef("libera", "status"), "(unignore)", "Removed ignore: b!*@*");
  }

  @Test
  void ignoreExceptOptionUsesUnignoreCompatibilityPath() {
    when(targetCoordinator.getActiveTarget()).thenReturn(new TargetRef("libera", "#ircafe"));
    when(ignoreListService.removeMask("libera", "badnick")).thenReturn(true);

    service.handleIgnore("-except badnick");

    verify(ignoreListService).removeMask("libera", "badnick");
    verify(ui)
        .appendStatus(
            new TargetRef("libera", "status"), "(unignore)", "Removed ignore: badnick!*@*");
    verify(ui)
        .appendStatus(
            new TargetRef("libera", "status"),
            "(ignore)",
            "Applied irssi-style -except as /unignore (IRCafe compatibility mode).");
  }
}
