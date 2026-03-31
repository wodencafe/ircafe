package cafe.woden.ircclient.ui.servers;

import java.util.Objects;

/** Pure validation rules for the server-editor proxy override fields. */
final class ServerEditorProxyValidationPolicy {
  private ServerEditorProxyValidationPolicy() {}

  static ProxyValidation validate(
      boolean overrideSelected,
      boolean proxyEnabled,
      String host,
      String port,
      String user,
      String password,
      String connectTimeoutMs,
      String readTimeoutMs) {
    if (!overrideSelected) {
      return new ProxyValidation(false, false, false, false, false, false, false);
    }

    boolean connectTimeoutWarning =
        !trim(connectTimeoutMs).isEmpty() && !isPositiveLong(connectTimeoutMs);
    boolean readTimeoutWarning = !trim(readTimeoutMs).isEmpty() && !isPositiveLong(readTimeoutMs);

    if (!proxyEnabled) {
      return new ProxyValidation(
          true, false, false, false, connectTimeoutWarning, readTimeoutWarning, false);
    }

    boolean hostBad = trim(host).isEmpty();
    boolean portBad = !isValidPort(port);
    boolean hasUser = !trim(user).isEmpty();
    boolean hasPassword = !Objects.toString(password, "").trim().isEmpty();
    boolean authMismatch = hasUser ^ hasPassword;

    return new ProxyValidation(
        true, true, hostBad, portBad, connectTimeoutWarning, readTimeoutWarning, authMismatch);
  }

  private static boolean isPositiveLong(String value) {
    try {
      long parsed = Long.parseLong(trim(value));
      return parsed > 0;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isValidPort(String value) {
    try {
      int parsed = Integer.parseInt(trim(value));
      return parsed > 0 && parsed <= 65_535;
    } catch (Exception e) {
      return false;
    }
  }

  private static String trim(String value) {
    return Objects.toString(value, "").trim();
  }

  record ProxyValidation(
      boolean applicable,
      boolean proxyDetailsApplicable,
      boolean hostBad,
      boolean portBad,
      boolean connectTimeoutWarning,
      boolean readTimeoutWarning,
      boolean authMismatch) {}
}
