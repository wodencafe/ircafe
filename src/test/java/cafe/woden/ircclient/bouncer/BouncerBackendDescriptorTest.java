package cafe.woden.ircclient.bouncer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BouncerBackendDescriptorTest {

  @Test
  void normalizesFieldsAndCapabilityHints() {
    BouncerBackendDescriptor descriptor =
        new BouncerBackendDescriptor(
            "  SoJu  ", " soju ", " Soju Networks ", Set.of(" Soju.IM/Bouncer-Networks ", " "));

    assertEquals("soju", descriptor.backendId());
    assertEquals("soju:", descriptor.ephemeralIdPrefix());
    assertEquals("Soju Networks", descriptor.networksGroupLabel());
    assertEquals(Set.of("soju.im/bouncer-networks"), descriptor.capabilityHints());
  }

  @Test
  void defaultsPrefixLabelAndHintsWhenMissing() {
    BouncerBackendDescriptor descriptor = new BouncerBackendDescriptor("generic", null, null, null);

    assertEquals("generic", descriptor.backendId());
    assertEquals("generic:", descriptor.ephemeralIdPrefix());
    assertEquals("generic Networks", descriptor.networksGroupLabel());
    assertTrue(descriptor.capabilityHints().isEmpty());
  }
}
