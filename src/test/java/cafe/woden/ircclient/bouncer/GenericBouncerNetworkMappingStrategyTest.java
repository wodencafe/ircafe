package cafe.woden.ircclient.bouncer;

import static cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures.bouncerDiscoveryPort;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.bouncer.spi.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.spi.BouncerNetworkMappingContext;
import cafe.woden.ircclient.bouncer.spi.BouncerServerProfile;
import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.api.BouncerDiscoveryConfigPort;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericBouncerNetworkMappingStrategyTest {

  @TempDir Path tempDir;

  @Test
  void loginUserHintOverridesDerivedLoginWhenEnabled() {
    RuntimeConfigStore runtimeConfig = runtimeConfig();
    runtimeConfig.rememberGenericBouncerPreferLoginHint(true);

    BouncerDiscoveredNetwork network =
        new BouncerDiscoveredNetwork(
            "generic", "bouncer-1", "net1", "Libera", "Libera", "hint-user", Set.of(), Map.of());

    assertEquals(
        "hint-user", resolve(runtimeConfig, sampleBouncerServer("base-user"), network).loginUser());
  }

  @Test
  void loginUserHintCanBeIgnoredWhenDisabled() {
    RuntimeConfigStore runtimeConfig = runtimeConfig();
    runtimeConfig.rememberGenericBouncerPreferLoginHint(false);

    BouncerDiscoveredNetwork network =
        new BouncerDiscoveredNetwork(
            "generic", "bouncer-1", "net1", "Libera", "Libera", "hint-user", Set.of(), Map.of());

    assertEquals(
        "base-user/Libera",
        resolve(runtimeConfig, sampleBouncerServer("base-user"), network).loginUser());
  }

  @Test
  void runtimeTemplateCanShapeDerivedLogin() {
    RuntimeConfigStore runtimeConfig = runtimeConfig();
    runtimeConfig.rememberGenericBouncerLoginTemplate("{base}|{network}");

    BouncerDiscoveredNetwork network =
        new BouncerDiscoveredNetwork(
            "generic", "bouncer-1", "net2", "Lib Era", "Lib Era", null, Set.of(), Map.of());

    assertEquals(
        "base-user|Lib_Era",
        resolve(runtimeConfig, sampleBouncerServer("base-user"), network).loginUser());
  }

  @Test
  void perNetworkTemplateOverridesRuntimeTemplate() {
    RuntimeConfigStore runtimeConfig = runtimeConfig();
    runtimeConfig.rememberGenericBouncerLoginTemplate("{base}|{network}");

    BouncerDiscoveredNetwork network =
        new BouncerDiscoveredNetwork(
            "generic",
            "bouncer-1",
            "net2",
            "Lib Era",
            "Lib Era",
            null,
            Set.of(),
            Map.of("loginTemplate", "{network}:{base}"));

    assertEquals(
        "Lib_Era:base-user",
        resolve(runtimeConfig, sampleBouncerServer("base-user"), network).loginUser());
  }

  @Test
  void explicitLoginUserOverridesHintAndTemplate() {
    RuntimeConfigStore runtimeConfig = runtimeConfig();
    runtimeConfig.rememberGenericBouncerLoginTemplate("{base}|{network}");
    runtimeConfig.rememberGenericBouncerPreferLoginHint(true);

    BouncerDiscoveredNetwork network =
        new BouncerDiscoveredNetwork(
            "generic",
            "bouncer-1",
            "net3",
            "Lib Era",
            "Lib Era",
            "hint-user",
            Set.of(),
            Map.of("loginUser", "explicit-user"));

    assertEquals(
        "explicit-user",
        resolve(runtimeConfig, sampleBouncerServer("base-user"), network).loginUser());
  }

  private RuntimeConfigStore runtimeConfig() {
    return RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));
  }

  private static cafe.woden.ircclient.bouncer.spi.ResolvedBouncerNetwork resolve(
      RuntimeConfigStore runtimeConfig,
      BouncerServerProfile bouncer,
      BouncerDiscoveredNetwork network) {
    BouncerDiscoveryConfigPort config = bouncerDiscoveryPort(runtimeConfig);
    BouncerNetworkMappingContext context =
        new BouncerNetworkMappingContext(
            config.readGenericBouncerLoginTemplate(
                BouncerNetworkMappingContext.DEFAULT_GENERIC_LOGIN_TEMPLATE),
            config.readGenericBouncerPreferLoginHint(
                BouncerNetworkMappingContext.DEFAULT_PREFER_LOGIN_HINT));
    return new GenericBouncerNetworkMappingStrategy().resolveNetwork(bouncer, network, context);
  }

  private static BouncerServerProfile sampleBouncerServer(String loginUser) {
    return new BouncerServerProfile("bouncer-1", loginUser, loginUser);
  }
}
