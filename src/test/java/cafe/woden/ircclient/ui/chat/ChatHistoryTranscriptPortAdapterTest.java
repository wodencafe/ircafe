package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.api.UiSettingsRuntimeConfigPort;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import org.junit.jupiter.api.Test;

class ChatHistoryTranscriptPortAdapterTest {

  @Test
  void defaultsViewportLockToTrueWhenRuntimeConfigPortIsAbsent() {
    ChatHistoryTranscriptPortAdapter adapter =
        new ChatHistoryTranscriptPortAdapter(
            mock(ChatTranscriptStore.class), mock(UiSettingsBus.class), null);

    assertTrue(adapter.chatHistoryLockViewportDuringLoadOlder());
  }

  @Test
  void delegatesViewportLockSettingToUiSettingsRuntimeConfigPort() {
    UiSettingsRuntimeConfigPort runtimeConfig = mock(UiSettingsRuntimeConfigPort.class);
    when(runtimeConfig.readChatHistoryLockViewportDuringLoadOlder(true)).thenReturn(false);

    ChatHistoryTranscriptPortAdapter adapter =
        new ChatHistoryTranscriptPortAdapter(
            mock(ChatTranscriptStore.class), mock(UiSettingsBus.class), runtimeConfig);

    assertFalse(adapter.chatHistoryLockViewportDuringLoadOlder());
  }
}
