package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupPrompt;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuasselCoreSetupSupportTest {
  @Test
  void discoversNestedOptionsWithStableDeduplicationAndCaseInsensitiveKeys() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put(
        "backendinfo",
        List.of(
            Map.of("BackendId", "SQLite"),
            Map.of("Name", "PostgreSQL"),
            Map.of("Backend", "SQLite")));
    fields.put("AuthenticatorInfo", Map.of("Authenticators", List.of("LDAP", "Database")));
    QuasselCoreSetupPrompt prompt =
        QuasselCoreSetupSupport.buildSetupPrompt("core", " setup ", fields);
    assertEquals(List.of("SQLite", "PostgreSQL"), prompt.storageBackends());
    assertEquals(List.of("LDAP", "Database"), prompt.authenticators());
    assertEquals("setup", prompt.detail());
    fields.clear();
    assertEquals(2, prompt.rawSetupFields().size());
  }

  @Test
  void defaultsMissingCoreOptionsAndKeepsPasswordWhitespace() {
    QuasselCoreSetupPrompt prompt = QuasselCoreSetupSupport.buildSetupPrompt("core", "", Map.of());
    assertEquals(List.of("SQLite"), prompt.storageBackends());
    assertEquals(List.of("Database"), prompt.authenticators());
    QuasselCoreSetupRequest normalized =
        QuasselCoreSetupSupport.normalizeSetupRequest(
            prompt, new QuasselCoreSetupRequest(" admin ", " secret ", "", null, null, null));
    assertEquals("admin", normalized.adminUser());
    assertEquals(" secret ", normalized.adminPassword());
    assertEquals("SQLite", normalized.storageBackend());
    assertEquals("Database", normalized.authenticator());
    assertEquals(Map.of(), normalized.authSetupData());
  }

  @Test
  void respectsExplicitOptionsAndNormalizesSetupDataWithoutDroppingNullValues() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(" host ", "db.example.test");
    data.put(" ", "ignored");
    data.put("optional", null);
    QuasselCoreSetupRequest normalized =
        QuasselCoreSetupSupport.normalizeSetupRequest(
            null,
            new QuasselCoreSetupRequest(
                "admin", "secret", " PostgreSQL ", " LDAP ", data, Map.of()));
    assertEquals("PostgreSQL", normalized.storageBackend());
    assertEquals("LDAP", normalized.authenticator());
    assertEquals(2, normalized.storageSetupData().size());
    assertEquals("db.example.test", normalized.storageSetupData().get("host"));
    assertThrows(
        UnsupportedOperationException.class,
        () -> normalized.storageSetupData().put("new", "value"));
  }

  @Test
  void rejectsMissingAdminCredentials() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QuasselCoreSetupSupport.normalizeSetupRequest(
                null, new QuasselCoreSetupRequest(" ", "secret", "", "", null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QuasselCoreSetupSupport.normalizeSetupRequest(
                null, new QuasselCoreSetupRequest("admin", " ", "", "", null, null)));
  }
}
