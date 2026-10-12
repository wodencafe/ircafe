package cafe.woden.ircclient.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.StandardEnvironment;
import org.yaml.snakeyaml.Yaml;

class LegacyTlsStartupPolicyTest {
  private static final String OPTION = "--ircafe.tls.allow-legacy-rsa";

  @TempDir Path temp;

  @Test
  void defaultAndExplicitFalseLeaveSecurityPolicyUntouched() {
    String before = Security.getProperty("jdk.tls.disabledAlgorithms");
    assertFalse(LegacyTlsStartupPolicy.configureFromArguments(new String[] {}));
    assertFalse(LegacyTlsStartupPolicy.configureFromArguments(new String[] {OPTION + "=false"}));
    assertEquals(before, Security.getProperty("jdk.tls.disabledAlgorithms"));
  }

  @Test
  void requiresExplicitOptInAndUsesLastOccurrence() {
    assertTrue(LegacyTlsStartupPolicy.isRequested(new String[] {"--other=true", OPTION + "=true"}));
    assertFalse(
        LegacyTlsStartupPolicy.isRequested(new String[] {OPTION + "=true", OPTION + "=false"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> LegacyTlsStartupPolicy.isRequested(new String[] {OPTION}));
    assertThrows(
        IllegalArgumentException.class,
        () -> LegacyTlsStartupPolicy.isRequested(new String[] {OPTION + "=yes"}));
  }

  @Test
  void launchArgumentOverridesSavedPreferenceInBothDirections() {
    assertTrue(LegacyTlsStartupPolicy.isRequested(new String[] {}, true));
    assertFalse(LegacyTlsStartupPolicy.isRequested(new String[] {OPTION + "=false"}, true));
    assertTrue(LegacyTlsStartupPolicy.isRequested(new String[] {OPTION + "=true"}, false));
  }

  @Test
  void removesOnlyWildcardRsaCipherRestriction() {
    assertEquals(
        "SSLv3, RSA keySize < 2048, TLS_RSA_WITH_RC4_128_SHA, rsa_pkcs1_sha1 usage HandshakeSignature",
        LegacyTlsStartupPolicy.withoutLegacyRsaRestriction(
            "SSLv3, TLS_RSA_*, RSA keySize < 2048, TLS_RSA_WITH_RC4_128_SHA, "
                + "rsa_pkcs1_sha1 usage HandshakeSignature"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"default", "argument", "saved", "override"})
  void freshJvmAppliesPolicyBeforeTlsAndKeepsCertificateValidation(String mode) throws Exception {
    String mainClasses =
        Path.of(
                LegacyTlsStartupPolicy.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .toString();
    String testClasses =
        Path.of(
                LegacyTlsStartupProbe.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .toString();
    String resources =
        Path.of(
                Objects.requireNonNull(getClass().getResource("/tls/self-signed-server-cert.pem"))
                    .toURI())
            .getParent()
            .getParent()
            .toString();
    List<String> command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                String.join(
                    File.pathSeparator,
                    mainClasses,
                    testClasses,
                    resources,
                    classLocation(StandardEnvironment.class),
                    classLocation(LogFactory.class),
                    classLocation(Yaml.class)),
                LegacyTlsStartupProbe.class.getName()));
    Path config = temp.resolve("ircafe.yml");
    if (mode.equals("saved") || mode.equals("override")) {
      Files.writeString(config, "irc:\n  client:\n    tls:\n      allowLegacyRsa: true\n");
    }
    command.add("--ircafe.runtime-config=" + config);
    if (mode.equals("argument")) command.add(OPTION + "=true");
    if (mode.equals("override")) command.add(OPTION + "=false");
    Path output = temp.resolve("probe.log");
    Process process =
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertTrue(process.waitFor(30, TimeUnit.SECONDS), "TLS startup probe timed out");
      assertEquals(0, process.exitValue(), () -> readOutput(output));
    } finally {
      process.destroyForcibly();
    }
  }

  private static String classLocation(Class<?> type) throws Exception {
    return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
  }

  private static String readOutput(Path output) {
    try {
      return Files.readString(output);
    } catch (Exception ex) {
      return ex.toString();
    }
  }
}
