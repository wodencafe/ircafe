package cafe.woden.ircclient.app.outbound.ignore;

import static cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures.ignoreRulesPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.properties.IgnoreProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.model.TargetRef;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class IgnoreCommandFlowIntegrationTest {

  @TempDir Path tempDir;

  @Test
  void ignoreAndUnignoreCommandFlowUpdatesStateAndRenderedList() {
    IgnoreListService ignoreListService = newIgnoreListService(tempDir.resolve("command-flow.yml"));
    UiPort ui = mock(UiPort.class);
    TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
    TargetRef active = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);

    IgnoreHardCommandSupport ignoreHardCommandSupport =
        new IgnoreHardCommandSupport(ui, targetCoordinator, ignoreListService, ignoreListService);
    IgnoreSoftCommandSupport ignoreSoftCommandSupport =
        new IgnoreSoftCommandSupport(ui, targetCoordinator, ignoreListService, ignoreListService);
    OutboundIgnoreCommandService service =
        new OutboundIgnoreCommandService(ignoreHardCommandSupport, ignoreSoftCommandSupport);

    service.handleIgnore("-channels #ircafe MSGS badnick");
    assertEquals(List.of("badnick!*@*"), ignoreListService.listMasks("libera"));
    assertEquals(List.of("MSGS"), ignoreListService.levelsForHardMask("libera", "badnick!*@*"));
    assertEquals(
        List.of("#ircafe"), ignoreListService.channelsForHardMask("libera", "badnick!*@*"));

    service.handleIgnore(" ");
    ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
    verify(ui, atLeastOnce()).appendStatus(eq(status), eq("(ignore)"), lines.capture());
    assertTrue(lines.getAllValues().stream().anyMatch(line -> line.contains("levels=MSGS")));
    assertTrue(lines.getAllValues().stream().anyMatch(line -> line.contains("channels=#ircafe")));

    service.handleUnignore("1");
    assertTrue(ignoreListService.listMasks("libera").isEmpty());
  }

  @Test
  void softIgnoreFlowAddsListsAndRemovesRules() {
    IgnoreListService ignoreListService =
        newIgnoreListService(tempDir.resolve("soft-command-flow.yml"));
    UiPort ui = mock(UiPort.class);
    TargetCoordinator targetCoordinator = mock(TargetCoordinator.class);
    TargetRef active = new TargetRef("libera", "#ircafe");
    TargetRef status = new TargetRef("libera", "status");
    when(targetCoordinator.getActiveTarget()).thenReturn(active);
    when(targetCoordinator.safeStatusTarget()).thenReturn(status);

    IgnoreHardCommandSupport ignoreHardCommandSupport =
        new IgnoreHardCommandSupport(ui, targetCoordinator, ignoreListService, ignoreListService);
    IgnoreSoftCommandSupport ignoreSoftCommandSupport =
        new IgnoreSoftCommandSupport(ui, targetCoordinator, ignoreListService, ignoreListService);
    OutboundIgnoreCommandService service =
        new OutboundIgnoreCommandService(ignoreHardCommandSupport, ignoreSoftCommandSupport);

    service.handleSoftIgnore("quietnick");
    assertEquals(List.of("quietnick!*@*"), ignoreListService.listSoftMasks("libera"));

    service.handleSoftIgnoreList();
    ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
    verify(ui, atLeastOnce()).appendStatus(eq(status), eq("(soft-ignore)"), lines.capture());
    assertTrue(lines.getAllValues().stream().anyMatch(line -> line.contains("quietnick!*@*")));

    service.handleUnsoftIgnore("quietnick");
    assertTrue(ignoreListService.listSoftMasks("libera").isEmpty());
  }

  private static IgnoreListService newIgnoreListService(Path path) {
    RuntimeConfigStore runtimeConfig =
        RuntimeConfigStoreTestFixtures.storeWithServers(
            path, IrcPropertiesTestFixtures.server("libera"));
    return new IgnoreListService(
        new IgnoreProperties(true, false, Map.of()), ignoreRulesPort(runtimeConfig));
  }
}
