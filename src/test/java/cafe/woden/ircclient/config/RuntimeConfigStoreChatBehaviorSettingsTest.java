package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreChatBehaviorSettingsTest {

  @TempDir Path tempDir;

  @Test
  void persistsChatBehaviorSettingsUnderUiSection() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberPresenceFoldsEnabled(false);
    store.rememberDefaultQuitMessage("Bye\nnow");
    store.rememberCtcpRequestsInActiveTargetEnabled(false);
    store.rememberNickCompletionCycleWithTabEnabled(true);
    store.rememberNickCompletionAppendAddressSuffixEnabled(false);
    store.rememberTypingIndicatorsEnabled(false);
    store.rememberTypingIndicatorsReceiveEnabled(false);
    store.rememberTypingTreeIndicatorStyle("KBD");
    store.rememberTypingIndicatorsTreeEnabled(false);
    store.rememberTypingIndicatorsUsersListEnabled(false);
    store.rememberMatrixUserListNameDisplayMode("full");
    store.rememberTypingIndicatorsTranscriptEnabled(false);
    store.rememberTypingIndicatorsSendSignalEnabled(false);

    Map<String, Object> ui = RuntimeConfigYamlTestSupport.uiSection(cfg);
    assertEquals(false, ui.get("presenceFoldsEnabled"));
    assertEquals("Bye now", ui.get("defaultQuitMessage"));
    assertEquals(false, ui.get("ctcpRequestsInActiveTargetEnabled"));
    assertEquals(true, ui.get("nickCompletionCycleWithTabEnabled"));
    assertEquals(false, ui.get("nickCompletionAppendAddressSuffixEnabled"));
    assertEquals(false, ui.get("typingIndicatorsEnabled"));
    assertEquals(false, ui.get("typingIndicatorsReceiveEnabled"));
    assertEquals("keyboard", ui.get("typingTreeIndicatorStyle"));
    assertEquals(false, ui.get("typingIndicatorsTreeEnabled"));
    assertEquals(false, ui.get("typingIndicatorsUsersListEnabled"));
    assertEquals("verbose", ui.get("matrixUserListNameDisplayMode"));
    assertEquals(false, ui.get("typingIndicatorsTranscriptEnabled"));
    assertEquals(false, ui.get("typingIndicatorsSendSignalEnabled"));
  }

  @Test
  void nickCompletionSettingsDefaultWhenUnsetAndCanBeReadBack() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));

    assertFalse(store.readNickCompletionCycleWithTabEnabled(false));
    assertTrue(store.readNickCompletionAppendAddressSuffixEnabled(true));

    store.rememberNickCompletionCycleWithTabEnabled(true);
    store.rememberNickCompletionAppendAddressSuffixEnabled(false);

    assertTrue(store.readNickCompletionCycleWithTabEnabled(false));
    assertFalse(store.readNickCompletionAppendAddressSuffixEnabled(true));
  }
}
