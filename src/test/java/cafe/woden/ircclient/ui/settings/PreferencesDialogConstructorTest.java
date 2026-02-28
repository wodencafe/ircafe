package cafe.woden.ircclient.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import cafe.woden.ircclient.notifications.IrcEventNotificationRulesBus;
import cafe.woden.ircclient.notify.pushy.PushyNotificationService;
import cafe.woden.ircclient.notify.pushy.PushySettingsBus;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.notify.sound.NotificationSoundSettingsBus;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.chat.TranscriptRebuildService;
import cafe.woden.ircclient.ui.chat.embed.EmbedLoadPolicyBus;
import cafe.woden.ircclient.ui.filter.FilterSettingsBus;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.ui.tray.TrayService;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;

class PreferencesDialogConstructorTest {

  @Test
  void acceptsActiveExecutors() {
    ExecutorService pushyExec = mock(ExecutorService.class);
    ExecutorService ruleExec = mock(ExecutorService.class);
    when(pushyExec.isShutdown()).thenReturn(false);
    when(ruleExec.isShutdown()).thenReturn(false);

    PreferencesDialog dialog = newDialog(pushyExec, ruleExec);
    assertNotNull(dialog);
  }

  @Test
  void rejectsShutdownPushyExecutor() {
    ExecutorService pushyExec = mock(ExecutorService.class);
    ExecutorService ruleExec = mock(ExecutorService.class);
    when(pushyExec.isShutdown()).thenReturn(true);
    when(ruleExec.isShutdown()).thenReturn(false);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> newDialog(pushyExec, ruleExec));
    assertEquals("pushyTestExecutor must be active", ex.getMessage());
  }

  @Test
  void rejectsShutdownRuleExecutor() {
    ExecutorService pushyExec = mock(ExecutorService.class);
    ExecutorService ruleExec = mock(ExecutorService.class);
    when(pushyExec.isShutdown()).thenReturn(false);
    when(ruleExec.isShutdown()).thenReturn(true);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> newDialog(pushyExec, ruleExec));
    assertEquals("notificationRuleTestExecutor must be active", ex.getMessage());
  }

  private static PreferencesDialog newDialog(
      ExecutorService pushyTestExecutor, ExecutorService notificationRuleTestExecutor) {
    return new PreferencesDialog(
        mock(UiSettingsBus.class),
        mock(EmbedCardStyleBus.class),
        mock(ThemeManager.class),
        mock(ThemeAccentSettingsBus.class),
        mock(ThemeTweakSettingsBus.class),
        mock(ChatThemeSettingsBus.class),
        mock(SpellcheckSettingsBus.class),
        mock(RuntimeConfigStore.class),
        mock(LogProperties.class),
        mock(NickColorSettingsBus.class),
        mock(NickColorService.class),
        mock(NickColorOverridesDialog.class),
        mock(EmbedLoadPolicyDialog.class),
        mock(EmbedLoadPolicyBus.class),
        mock(PircbotxIrcClientService.class),
        mock(FilterSettingsBus.class),
        mock(TranscriptRebuildService.class),
        mock(ActiveTargetPort.class),
        mock(TrayService.class),
        mock(TrayNotificationService.class),
        mock(GnomeDbusNotificationBackend.class),
        mock(NotificationSoundSettingsBus.class),
        mock(PushySettingsBus.class),
        mock(PushyNotificationService.class),
        mock(IrcEventNotificationRulesBus.class),
        mock(UserCommandAliasesBus.class),
        mock(NotificationSoundService.class),
        mock(ServerDialogs.class),
        pushyTestExecutor,
        notificationRuleTestExecutor);
  }
}
