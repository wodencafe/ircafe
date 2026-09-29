package cafe.woden.ircclient.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeConfigClientTranslationAdapterTest {

  @Test
  void writesTranslationSettingsToRuntimeStore() {
    RuntimeConfigStore runtimeConfig = mock(RuntimeConfigStore.class);
    RuntimeConfigClientTranslationAdapter adapter =
        new RuntimeConfigClientTranslationAdapter(runtimeConfig);
    IrcProperties.Client.Translation translation =
        new IrcProperties.Client.Translation(
            true,
            IrcProperties.Client.Translation.Mode.MANUAL,
            "deepl",
            "https://api.deepl.com/v2/translate",
            "secret",
            "en",
            "fr",
            false,
            false,
            List.of("en", "fr"),
            IrcProperties.Client.Translation.DisplayMode.BELOW_ORIGINAL,
            12_000,
            4_000,
            2);

    adapter.rememberClientTranslation(translation);

    verify(runtimeConfig).rememberClientTranslation(translation);
  }
}
