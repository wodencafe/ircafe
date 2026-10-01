package cafe.woden.ircclient.config;

import static org.assertj.core.api.Assertions.assertThat;

import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RuntimeConfigPersistenceIntegrationTest {
  @TempDir Path tempDir;

  @Test
  void springLifecycleStartsPersistenceAndFlushesPendingChangesOnClose() throws Exception {
    Path config = tempDir.resolve("ircafe.yml");
    new ApplicationContextRunner()
        .withBean(
            RuntimeConfigStore.class,
            () ->
                RuntimeConfigStoreTestFixtures.storeWithServers(
                    config, IrcPropertiesTestFixtures.server("libera")))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              RuntimeConfigStore store = context.getBean(RuntimeConfigStore.class);
              assertThat(store.readServerIds()).containsExactly("libera");
              store.startPersistence();
              store.rememberStartupThemePending("nimbus-dark-orange");
              assertThat(Files.readString(config))
                  .contains("startupThemePending: nimbus-dark-orange");
              SwingUtilities.invokeAndWait(
                  () -> {
                    store.runMutationBatch(
                        () -> {
                          store.rememberJoinedChannel("libera", "#first");
                          store.rememberJoinedChannel("libera", "#second");
                          store.forgetJoinedChannel("libera", "#first");
                          store.rememberUiSettings("nimbus-dark-orange", "Noto Sans Display", 16);
                          store.clearStartupThemePending();
                        });
                    assertThat(store.readJoinedChannels("libera")).containsExactly("#second");
                  });
            });
    try (RuntimeConfigStore reopened = RuntimeConfigStoreTestFixtures.store(config)) {
      assertThat(reopened.readJoinedChannels("libera")).isEqualTo(List.of("#second"));
      assertThat(reopened.readStartupThemePending()).isEmpty();
      assertThat(Files.readString(config)).contains("chatFontSize: 16");
    }
  }
}
