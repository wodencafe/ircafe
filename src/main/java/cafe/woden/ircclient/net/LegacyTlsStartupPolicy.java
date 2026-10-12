package cafe.woden.ircclient.net;

import java.security.Security;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Explicit, process-wide compatibility opt-in, applied before JSSE caches its security policy. */
public final class LegacyTlsStartupPolicy {
  private static final String OPTION = "--ircafe.tls.allow-legacy-rsa";
  private static final String DISABLED_ALGORITHMS = "jdk.tls.disabledAlgorithms";

  private LegacyTlsStartupPolicy() {}

  /**
   * Call at the start of main, before initializing TLS or Spring. This allows static RSA key
   * exchange; it does not disable certificate validation or other algorithm restrictions.
   *
   * @return whether compatibility was explicitly requested
   */
  public static boolean configureFromArguments(String[] args) {
    return configureFromArguments(args, false);
  }

  /** Explicit launch arguments override the saved preference, including an explicit false. */
  public static boolean configureFromArguments(String[] args, boolean savedPreference) {
    if (!isRequested(args, savedPreference)) return false;
    String policy = Security.getProperty(DISABLED_ALGORITHMS);
    if (policy != null) {
      Security.setProperty(DISABLED_ALGORITHMS, withoutLegacyRsaRestriction(policy));
    }
    return true;
  }

  static boolean isRequested(String[] args) {
    return isRequested(args, false);
  }

  static boolean isRequested(String[] args, boolean savedPreference) {
    boolean requested = savedPreference;
    for (String arg : args) {
      if (OPTION.equals(arg)) {
        throw new IllegalArgumentException(OPTION + " requires =true or =false");
      }
      if (arg.startsWith(OPTION + "=")) {
        requested =
            switch (arg.substring(OPTION.length() + 1)) {
              case "true" -> true;
              case "false" -> false;
              default -> throw new IllegalArgumentException(OPTION + " requires =true or =false");
            };
      }
    }
    return requested;
  }

  static String withoutLegacyRsaRestriction(String policy) {
    return Arrays.stream(policy.split(","))
        .map(String::trim)
        .filter(algorithm -> !"TLS_RSA_*".equalsIgnoreCase(algorithm))
        .collect(Collectors.joining(", "));
  }
}
