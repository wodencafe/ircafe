package cafe.woden.ircclient.bouncer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BouncerBackendRegistryTest {

  @Test
  void buildsDescriptorsFromMappingStrategies() {
    BouncerBackendRegistry registry =
        new BouncerBackendRegistry(
            List.of(
                new FakeStrategy("znc", "znc:", "ZNC Networks", Set.of("znc.in/playback"), "net-z"),
                new FakeStrategy(
                    "soju", "soju:", "Soju Networks", Set.of("soju.im/bouncer-networks"), "net-s"),
                new FakeStrategy("generic", "bouncer:", "Bouncer Networks", Set.of(), "net-g")));

    assertEquals(Set.of("generic", "soju", "znc"), registry.backendIds());
    assertEquals(3, registry.descriptors().size());

    BouncerBackendDescriptor generic = registry.find("GENERIC").orElseThrow();
    assertEquals("bouncer:", generic.ephemeralIdPrefix());
    assertEquals("Bouncer Networks", generic.networksGroupLabel());

    BouncerBackendDescriptor soju = registry.find("soju").orElseThrow();
    assertEquals(Set.of("soju.im/bouncer-networks"), soju.capabilityHints());
  }

  @Test
  void ignoresDuplicateBackendIdsAfterNormalization() {
    BouncerBackendRegistry registry =
        new BouncerBackendRegistry(
            List.of(
                new FakeStrategy("soju", "soju:", "Soju Networks", Set.of(), "one"),
                new FakeStrategy(" SOJU ", "soju:", "Soju Networks 2", Set.of(), "two")));

    assertEquals(1, registry.descriptors().size());
    assertTrue(registry.find("soju").isPresent());
  }

  private record FakeStrategy(
      String backendId,
      String ephemeralIdPrefix,
      String networksGroupLabel,
      Set<String> capabilityHints,
      String idSuffix)
      implements BouncerNetworkMappingStrategy {

    @Override
    public ResolvedBouncerNetwork resolveNetwork(
        IrcProperties.Server bouncer, BouncerDiscoveredNetwork network) {
      return new ResolvedBouncerNetwork(
          ephemeralIdPrefix + "origin:" + idSuffix, "user/" + idSuffix, "display", "display");
    }

    @Override
    public IrcProperties.Server buildEphemeralServer(
        IrcProperties.Server bouncer,
        ResolvedBouncerNetwork resolved,
        List<String> autoJoinChannels) {
      return bouncer;
    }
  }
}
