package cafe.woden.ircclient.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;

import cafe.woden.ircclient.IrcSwingApp;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class SpringModulithIncrementalAdoptionTest {

  @Test
  void applicationModulesCanBeDiscovered() {
    assertThatCode(() -> ApplicationModules.of(IrcSwingApp.class)).doesNotThrowAnyException();
  }

  @Disabled("Enable after reducing cross-package coupling and declaring module boundaries.")
  @Test
  void moduleVerificationCanBeEnabledAfterBoundaryRefactors() {
    ApplicationModules.of(IrcSwingApp.class).verify();
  }
}
