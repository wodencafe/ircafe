package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerEditorProxyValidationPolicyTest {

  @Test
  void disabledOverrideHasNoApplicableValidation() {
    ServerEditorProxyValidationPolicy.ProxyValidation validation =
        ServerEditorProxyValidationPolicy.validate(false, true, "", "", "", "", "", "");

    assertFalse(validation.applicable());
    assertFalse(validation.proxyDetailsApplicable());
    assertFalse(validation.connectTimeoutWarning());
    assertFalse(validation.readTimeoutWarning());
  }

  @Test
  void overrideWithoutProxyEnabledOnlyValidatesTimeoutWarnings() {
    ServerEditorProxyValidationPolicy.ProxyValidation validation =
        ServerEditorProxyValidationPolicy.validate(true, false, "", "", "", "", "-1", "abc");

    assertTrue(validation.applicable());
    assertFalse(validation.proxyDetailsApplicable());
    assertTrue(validation.connectTimeoutWarning());
    assertTrue(validation.readTimeoutWarning());
    assertFalse(validation.hostBad());
    assertFalse(validation.portBad());
    assertFalse(validation.authMismatch());
  }

  @Test
  void enabledProxyValidatesHostPortAndAuthMismatch() {
    ServerEditorProxyValidationPolicy.ProxyValidation validation =
        ServerEditorProxyValidationPolicy.validate(
            true, true, "", "70000", "alice", "", "20000", "30000");

    assertTrue(validation.applicable());
    assertTrue(validation.proxyDetailsApplicable());
    assertTrue(validation.hostBad());
    assertTrue(validation.portBad());
    assertFalse(validation.connectTimeoutWarning());
    assertFalse(validation.readTimeoutWarning());
    assertTrue(validation.authMismatch());
  }
}
