package cafe.woden.ircclient.ui.servertree.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.config.api.UiSettingsRuntimeConfigPort;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTypingIndicatorStyle;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ServerTreeSettingsSynchronizerContextTest {

  @Test
  void contextDelegatesSettingsSynchronizerOperations() {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    UiSettingsRuntimeConfigPort runtimeConfig = mock(UiSettingsRuntimeConfigPort.class);
    JfrRuntimeEventsService jfrRuntimeEventsService = mock(JfrRuntimeEventsService.class);
    AtomicBoolean typingEnabled = new AtomicBoolean(true);
    AtomicBoolean typingCleared = new AtomicBoolean(false);
    AtomicReference<ServerTreeTypingIndicatorStyle> typingStyle = new AtomicReference<>();
    AtomicBoolean badgesEnabled = new AtomicBoolean(false);
    AtomicInteger unreadScale = new AtomicInteger();
    AtomicReference<Color> unreadColor = new AtomicReference<>();
    AtomicReference<Color> highlightColor = new AtomicReference<>();
    AtomicBoolean refreshedLayout = new AtomicBoolean(false);
    AtomicBoolean refreshedJfr = new AtomicBoolean(false);

    ServerTreeSettingsSynchronizer.Context context =
        ServerTreeSettingsSynchronizer.context(
            settingsBus,
            jfrRuntimeEventsService,
            runtimeConfig,
            typingEnabled::get,
            typingEnabled::set,
            () -> typingCleared.set(true),
            typingStyle::set,
            badgesEnabled::set,
            unreadScale::set,
            unreadColor::set,
            highlightColor::set,
            () -> refreshedLayout.set(true),
            () -> refreshedJfr.set(true));

    assertSame(settingsBus, context.settingsBus());
    assertSame(jfrRuntimeEventsService, context.jfrRuntimeEventsService());
    assertSame(runtimeConfig, context.runtimeConfig());
    assertTrue(context.typingIndicatorsTreeEnabled());

    context.setTypingIndicatorsTreeEnabled(false);
    context.clearTypingIndicatorsFromTree();
    context.setTypingIndicatorStyle(ServerTreeTypingIndicatorStyle.DOTS);
    context.setServerTreeNotificationBadgesEnabled(true);
    context.setUnreadBadgeScalePercent(123);
    context.setUnreadChannelTextColor(Color.GREEN);
    context.setHighlightChannelTextColor(Color.ORANGE);
    context.refreshTreeLayoutAfterUiChange();
    context.refreshApplicationJfrNode();

    assertFalse(typingEnabled.get());
    assertTrue(typingCleared.get());
    assertEquals(ServerTreeTypingIndicatorStyle.DOTS, typingStyle.get());
    assertTrue(badgesEnabled.get());
    assertEquals(123, unreadScale.get());
    assertEquals(Color.GREEN, unreadColor.get());
    assertEquals(Color.ORANGE, highlightColor.get());
    assertTrue(refreshedLayout.get());
    assertTrue(refreshedJfr.get());
  }
}
