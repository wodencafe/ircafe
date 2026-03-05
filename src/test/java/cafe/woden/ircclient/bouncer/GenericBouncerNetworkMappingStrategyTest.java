package cafe.woden.ircclient.bouncer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import java.nio.file.Path;
import java.util.List;
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
        "hint-user",
        strategy(runtimeConfig)
            .resolveNetwork(sampleBouncerServer("base-user"), network)
            .loginUser());
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
        strategy(runtimeConfig)
            .resolveNetwork(sampleBouncerServer("base-user"), network)
            .loginUser());
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
        strategy(runtimeConfig)
            .resolveNetwork(sampleBouncerServer("base-user"), network)
            .loginUser());
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
        strategy(runtimeConfig)
            .resolveNetwork(sampleBouncerServer("base-user"), network)
            .loginUser());
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
        strategy(runtimeConfig)
            .resolveNetwork(sampleBouncerServer("base-user"), network)
            .loginUser());
  }

  private RuntimeConfigStore runtimeConfig() {
    return new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));
  }

  private static GenericBouncerNetworkMappingStrategy strategy(RuntimeConfigStore runtimeConfig) {
    return new GenericBouncerNetworkMappingStrategy(runtimeConfig);
  }

  private static IrcProperties.Server sampleBouncerServer(String loginUser) {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, loginUser, "pw", "PLAIN", null);
    return new IrcProperties.Server(
        "bouncer-1",
        "bouncer.example",
        6697,
        true,
        "",
        "nick",
        loginUser,
        "Real Name",
        sasl,
        List.of(),
        List.of(),
        null);
  }
}
