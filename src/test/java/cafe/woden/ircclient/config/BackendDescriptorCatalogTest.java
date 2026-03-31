package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BackendDescriptorCatalogTest {

  @Test
  void builtInsResolveByBackendAndId() {
    BackendDescriptorCatalog catalog = BackendDescriptorCatalog.builtIns();

    assertEquals("irc", catalog.idFor(IrcProperties.Server.Backend.IRC));
    assertEquals("Quassel Core", catalog.displayNameFor(IrcProperties.Server.Backend.QUASSEL_CORE));
    assertEquals(IrcProperties.Server.Backend.MATRIX, catalog.backendForId("matrix").orElseThrow());
    assertTrue(catalog.descriptorForId("unknown").isEmpty());
  }
}
