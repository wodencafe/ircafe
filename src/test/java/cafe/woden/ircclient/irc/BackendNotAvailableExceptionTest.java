package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import org.junit.jupiter.api.Test;

class BackendNotAvailableExceptionTest {

  @Test
  void customBackendIdsAreRetainedAndRendered() {
    BackendNotAvailableException exception =
        new BackendNotAvailableException("plugin-backend", "connect", "plugin", "not installed");

    assertEquals("plugin-backend", exception.backendId());
    assertNull(exception.backend());
    assertEquals("connect", exception.operation());
    assertEquals("plugin", exception.serverId());
    assertEquals(
        "plugin-backend backend is not installed (connect) for server 'plugin'",
        exception.getMessage());
  }

  @Test
  void builtInBackendsStillExposeEnumCompatibility() {
    BackendNotAvailableException exception =
        new BackendNotAvailableException("matrix", "connect", "matrix", "not configured");

    assertEquals("matrix", exception.backendId());
    assertEquals(
        cafe.woden.ircclient.config.IrcProperties.Server.Backend.MATRIX, exception.backend());
    assertEquals(
        "Matrix backend is not configured (connect) for server 'matrix'", exception.getMessage());
  }

  @Test
  void displayMessageUsesPluginAwareDisplayNames() {
    BackendNotAvailableException exception =
        new BackendNotAvailableException("plugin-backend", "connect", "plugin", "not installed");

    assertEquals(
        "Fancy Plugin backend is not installed (connect) for server 'plugin'",
        exception.displayMessage(
            backendId -> "plugin-backend".equals(backendId) ? "Fancy Plugin" : backendId));
  }
}
