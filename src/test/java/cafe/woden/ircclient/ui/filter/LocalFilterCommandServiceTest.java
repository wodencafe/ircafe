package cafe.woden.ircclient.ui.filter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.FilterCommand;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import org.junit.jupiter.api.Test;

class LocalFilterCommandServiceTest {

  private static final TargetRef ACTIVE = new TargetRef("libera", "#ircafe");
  private static final TargetRef STATUS = new TargetRef("libera", "status");

  private final UiPort ui = mock(UiPort.class);
  private final ActiveTargetPort targetCoordinator = mock(ActiveTargetPort.class);
  private final FilterSettingsBus filterSettingsBus = mock(FilterSettingsBus.class);
  private final FilterEngine filterEngine = mock(FilterEngine.class);
  private final RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
  private final TranscriptRebuildService rebuildService = mock(TranscriptRebuildService.class);

  private final LocalFilterCommandService service =
      new LocalFilterCommandService(
          ui, targetCoordinator, filterSettingsBus, filterEngine, runtimeConfig, rebuildService);

  @Test
  void addCommandPersistsRulesRebuildsAndPrintsStatus() {
    when(targetCoordinator.getActiveTarget()).thenReturn(ACTIVE);
    when(filterSettingsBus.get()).thenReturn(FilterSettings.defaults());
    when(filterEngine.effectiveResolvedFor(any())).thenReturn(defaultEffective());
    when(rebuildService.rebuild(ACTIVE)).thenReturn(true);

    service.handle(new FilterCommand.Add("noise", FilterCommand.FilterRulePatch.empty()));

    verify(filterSettingsBus)
        .set(
            argThat(
                next ->
                    next != null
                        && next.rules().size() == 1
                        && "noise".equals(next.rules().getFirst().name())));
    verify(runtimeConfig).rememberFilterRules(anyList());
    verify(rebuildService).rebuild(ACTIVE);
    verify(ui).appendStatus(ACTIVE, "(filter)", "Added filter rule: noise");
  }

  @Test
  void defaultsCommandPersistsDefaultsAndTuning() {
    when(targetCoordinator.getActiveTarget()).thenReturn(ACTIVE);
    when(filterSettingsBus.get()).thenReturn(FilterSettings.defaults());
    when(filterEngine.effectiveResolvedFor(any())).thenReturn(defaultEffective());
    when(rebuildService.rebuild(ACTIVE)).thenReturn(true);

    FilterCommand.Defaults defaults =
        new FilterCommand.Defaults(
            false, true, true, true, false, true, 5, true, 400, true, 20, true, 50, true, false,
            true);

    service.handle(defaults);

    verify(runtimeConfig).rememberFiltersEnabledByDefault(false);
    verify(runtimeConfig).rememberFilterPlaceholdersEnabledByDefault(true);
    verify(runtimeConfig).rememberFilterPlaceholdersCollapsedByDefault(false);
    verify(runtimeConfig).rememberFilterPlaceholderMaxPreviewLines(5);
    verify(runtimeConfig).rememberFilterPlaceholderMaxLinesPerRun(400);
    verify(runtimeConfig).rememberFilterPlaceholderTooltipMaxTags(20);
    verify(runtimeConfig).rememberFilterHistoryPlaceholderMaxRunsPerBatch(50);
    verify(runtimeConfig).rememberFilterHistoryPlaceholdersEnabledByDefault(false);
    verify(rebuildService).rebuild(ACTIVE);
  }

  @Test
  void nullCommandPrintsUsageToSafeStatusTarget() {
    when(targetCoordinator.getActiveTarget()).thenReturn(null);
    when(targetCoordinator.safeStatusTarget()).thenReturn(STATUS);

    service.handle(null);

    verify(ui).appendStatus(STATUS, "(filter)", "Usage: /filter help");
  }

  @Test
  void errorCommandRoutesToUiError() {
    when(targetCoordinator.getActiveTarget()).thenReturn(ACTIVE);

    service.handle(new FilterCommand.Error("bad command"));

    verify(ui).appendError(ACTIVE, "(filter)", "bad command");
  }

  private static FilterEngine.EffectiveResolved defaultEffective() {
    FilterEngine.ResolvedBool onByDefault = new FilterEngine.ResolvedBool(true, null);
    return new FilterEngine.EffectiveResolved(
        onByDefault, onByDefault, onByDefault, 3, 250, 12, 10, true);
  }
}
