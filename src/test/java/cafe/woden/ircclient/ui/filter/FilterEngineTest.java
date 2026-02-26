package cafe.woden.ircclient.ui.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.model.FilterAction;
import cafe.woden.ircclient.model.FilterDirection;
import cafe.woden.ircclient.model.FilterRule;
import cafe.woden.ircclient.model.FilterScopeOverride;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.RegexFlag;
import cafe.woden.ircclient.model.RegexSpec;
import cafe.woden.ircclient.model.TagSpec;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FilterEngineTest {

  @Test
  void shouldHideAndFirstMatchFollowRuleDefinitions() {
    FilterSettingsBus bus = mock(FilterSettingsBus.class);
    when(bus.get())
        .thenReturn(
            new FilterSettings(
                true,
                true,
                true,
                3,
                250,
                12,
                10,
                true,
                List.of(
                    new FilterRule(
                        null,
                        "hide-spam",
                        true,
                        "libera/#ircafe",
                        FilterAction.HIDE,
                        FilterDirection.ANY,
                        EnumSet.of(LogKind.CHAT),
                        List.of("spammer"),
                        new RegexSpec("buy now", EnumSet.of(RegexFlag.I)),
                        TagSpec.empty())),
                List.of()));

    FilterEngine engine = new FilterEngine(bus);
    TargetRef target = new TargetRef("libera", "#ircafe");
    FilterContext ctx =
        new FilterContext(
            target, LogKind.CHAT, LogDirection.IN, "Spammer", "BUY NOW and save", Set.of());

    assertTrue(engine.shouldHide(ctx));
    FilterEngine.Match match = engine.firstMatch(ctx);
    assertNotNull(match);
    assertEquals("hide-spam", match.ruleName());
    assertTrue(match.isHide());
  }

  @Test
  void moreSpecificScopeOverrideWinsOverGlobalOverride() {
    FilterSettingsBus bus = mock(FilterSettingsBus.class);
    when(bus.get())
        .thenReturn(
            new FilterSettings(
                true,
                true,
                true,
                3,
                250,
                12,
                10,
                true,
                List.of(),
                List.of(
                    new FilterScopeOverride("*/*", false, null, null),
                    new FilterScopeOverride("libera/#ircafe", true, null, null))));

    FilterEngine engine = new FilterEngine(bus);

    TargetRef specific = new TargetRef("libera", "#ircafe");
    TargetRef other = new TargetRef("libera", "#other");

    assertTrue(engine.filtersEnabledFor(specific));
    assertFalse(engine.filtersEnabledFor(other));
    assertEquals(
        "libera/#ircafe",
        engine.effectiveResolvedFor(specific).filtersEnabled().sourceScopePattern());
  }

  @Test
  void disabledFiltersAndNullInputFallbackBehaveSafely() {
    FilterSettingsBus bus = mock(FilterSettingsBus.class);
    when(bus.get())
        .thenReturn(
            new FilterSettings(false, true, true, 0, 250, 12, 10, true, List.of(), List.of()));

    FilterEngine engine = new FilterEngine(bus);
    FilterContext ctx =
        new FilterContext(
            new TargetRef("libera", "#ircafe"),
            LogKind.CHAT,
            LogDirection.IN,
            "nick",
            "hello",
            Set.of());

    assertFalse(engine.shouldHide(ctx));
    assertNull(engine.firstMatch(ctx));
    assertFalse(engine.shouldHide(null));
    assertNotNull(engine.effectiveFor(null));
  }
}
