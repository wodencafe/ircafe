package cafe.woden.ircclient.config;

import static org.assertj.core.api.Assertions.assertThat;

import cafe.woden.ircclient.config.api.FirstRunSetupConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.config.servers.ServerRegistry;
import cafe.woden.ircclient.ui.FirstRunSetupCoordinator;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FirstRunSetupWiringIntegrationTest {
  @TempDir Path temp;

  @Test
  void wiresTheWizardToTheLiveRegistryAndRuntimeConfig() {
    new ApplicationContextRunner()
        .withBean(IrcProperties.class, IrcPropertiesTestFixtures::properties)
        .withBean(
            RuntimeConfigStore.class,
            () -> RuntimeConfigStoreTestFixtures.store(temp.resolve("ircafe.yml")))
        .withUserConfiguration(
            RuntimeConfigServerRegistryAdapter.class,
            ServerRegistry.class,
            RuntimeConfigFirstRunSetupAdapter.class,
            FirstRunSetupCoordinator.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(FirstRunSetupCoordinator.class);
              assertThat(context).hasSingleBean(FirstRunSetupConfigPort.class);
              var setup = context.getBean(FirstRunSetupConfigPort.class);
              assertThat(setup.shouldOfferSetup()).isTrue();
              setup.finishSetup(IrcPropertiesTestFixtures.server("new-network"));
              assertThat(context.getBean(ServerRegistry.class).containsId("new-network")).isTrue();
              assertThat(setup.shouldOfferSetup()).isFalse();
            });
  }
}
