package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettingsTestFixtures;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandHistoryStoreTest {

  private CommandHistoryStore store;

  @BeforeEach
  void setUp() {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(UiSettingsTestFixtures.defaultSettings());
    store = new CommandHistoryStore(settingsBus);
  }

  @AfterEach
  void tearDown() {
    store.shutdown();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/ns IDENTIFY secret",
        "/nickserv identify Alice secret",
        "/NS ID secret",
        "/NickServ\tIDENTIFY\tsecret",
        "/ns\tID\tsecret",
        "/msg NickServ IDENTIFY secret",
        "/msg\tNickServ\tID\tsecret",
        "/ns SET token=secret"
      })
  void omitsNickServIdentificationAndTokensFromHistory(String line) {
    store.add(line);

    assertTrue(store.snapshot().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/ns", "/ns HELP", "/nickserv INFO Alice", "/ns IDENTITY", "/msg Alice hello"})
  void retainsOrdinaryCommands(String line) {
    store.add(line);

    assertEquals(List.of(line), store.snapshot());
  }
}
