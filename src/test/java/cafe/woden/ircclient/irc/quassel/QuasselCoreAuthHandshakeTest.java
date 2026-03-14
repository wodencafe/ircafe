package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuasselCoreAuthHandshakeTest {

  @Test
  void authenticateSendsClientInitThenLoginAndWaitsForSessionInit() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake handshake = new QuasselCoreAuthHandshake(codec);
    QuasselCoreDatastreamCodec.BufferInfoValue initialStatusBuffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(5, 7, 0x01, -1, "status");
    Map<String, Object> initialIdentity =
        Map.of("identityId", 1, "identityName", "quassel-user", "nicks", List.of("quassel-user"));

    byte[] inbound =
        encodeFrames(
            codec,
            message(
                "ClientInitAck",
                field("Configured", true),
                field("StorageBackends", List.of()),
                field("FeatureList", List.of())),
            message("ClientLoginAck"),
            message(
                "SessionInit",
                field(
                    "SessionState",
                    new LinkedHashMap<>(
                        java.util.Map.of(
                            "BufferInfos", List.of(initialStatusBuffer),
                            "NetworkIds", List.of(7),
                            "Identities", List.of(initialIdentity))))));

    ScriptedSocket socket = new ScriptedSocket(inbound);
    IrcProperties.Server server = server("alice", "secret");

    QuasselCoreAuthHandshake.AuthResult result = handshake.authenticate(socket, server);

    assertEquals("alice", result.authUser());
    assertEquals(7, result.primaryNetworkId());
    assertEquals(List.of(7), result.networkIds());
    assertEquals(java.util.Map.of(5, initialStatusBuffer), result.initialBuffers());
    assertEquals("quassel-user", result.initialIdentities().get(1).get("identityName"));

    ByteArrayInputStream outbound = new ByteArrayInputStream(socket.writtenBytes());
    QuasselCoreDatastreamCodec.HandshakeMessage first = codec.readHandshakeMessage(outbound);
    QuasselCoreDatastreamCodec.HandshakeMessage second = codec.readHandshakeMessage(outbound);

    assertEquals("ClientInit", first.messageType());
    assertEquals("ClientLogin", second.messageType());
    assertEquals("alice", second.fields().get("User"));
    assertEquals("secret", second.fields().get("Password"));
    assertEquals(0, outbound.available());
  }

  @Test
  void authenticateFailsWhenCoreIsNotConfigured() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake handshake = new QuasselCoreAuthHandshake(codec);

    byte[] inbound = encodeFrames(codec, message("ClientInitAck", field("Configured", false)));
    ScriptedSocket socket = new ScriptedSocket(inbound);

    QuasselCoreAuthHandshake.CoreSetupRequiredException err =
        assertThrows(
            QuasselCoreAuthHandshake.CoreSetupRequiredException.class,
            () -> handshake.authenticate(socket, server("alice", "secret")));

    assertTrue(err.getMessage().toLowerCase().contains("configured"));
  }

  @Test
  void authenticateFailsWhenCoreRequiresSetupDataExchange() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake handshake = new QuasselCoreAuthHandshake(codec);

    byte[] inbound =
        encodeFrames(
            codec,
            message("ClientInitAck", field("Configured", true)),
            message("CoreSetupData", field("StorageBackends", List.of("SQLite"))));
    ScriptedSocket socket = new ScriptedSocket(inbound);

    QuasselCoreAuthHandshake.CoreSetupRequiredException err =
        assertThrows(
            QuasselCoreAuthHandshake.CoreSetupRequiredException.class,
            () -> handshake.authenticate(socket, server("alice", "secret")));

    assertTrue(err.getMessage().toLowerCase().contains("setup"));
    assertTrue(err.setupFields().containsKey("StorageBackends"));
  }

  @Test
  void authenticateFailsWhenLoginIsRejected() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake handshake = new QuasselCoreAuthHandshake(codec);

    byte[] inbound =
        encodeFrames(
            codec,
            message("ClientInitAck", field("Configured", true)),
            message("ClientLoginReject", field("Error", "invalid credentials")));
    ScriptedSocket socket = new ScriptedSocket(inbound);

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () -> handshake.authenticate(socket, server("alice", "secret")));

    assertTrue(err.getMessage().contains("invalid credentials"));
  }

  @Test
  void authenticateFailsWhenClientInitIsRejected() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake handshake = new QuasselCoreAuthHandshake(codec);

    byte[] inbound =
        encodeFrames(
            codec, message("ClientInitReject", field("Error", "core policy rejected client init")));
    ScriptedSocket socket = new ScriptedSocket(inbound);

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () -> handshake.authenticate(socket, server("alice", "secret")));

    assertTrue(err.getMessage().contains("ClientInit rejected by core"));
    assertTrue(err.getMessage().contains("core policy rejected client init"));
  }

  @Test
  void authenticateFailsWhenSessionInitArrivesBeforeClientInitAck() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake handshake = new QuasselCoreAuthHandshake(codec);
    byte[] inbound = encodeFrames(codec, message("SessionInit", field("SessionState", Map.of())));
    ScriptedSocket socket = new ScriptedSocket(inbound);

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () -> handshake.authenticate(socket, server("alice", "secret")));

    assertTrue(err.getMessage().contains("before login completed"));
  }

  @Test
  void authenticateTreatsLoginEnabledFalseAsSetupRequired() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake handshake = new QuasselCoreAuthHandshake(codec);
    byte[] inbound = encodeFrames(codec, message("ClientInitAck", field("LoginEnabled", false)));
    ScriptedSocket socket = new ScriptedSocket(inbound);

    QuasselCoreAuthHandshake.CoreSetupRequiredException err =
        assertThrows(
            QuasselCoreAuthHandshake.CoreSetupRequiredException.class,
            () -> handshake.authenticate(socket, server("alice", "secret")));

    assertTrue(err.getMessage().toLowerCase().contains("configured"));
  }

  @Test
  void configuredAuthUserFallsBackToNickThenDefault() {
    IrcProperties.Server nickOnly = server("", "");
    IrcProperties.Server fullyBlank =
        new IrcProperties.Server(
            "quassel",
            "irc.example.net",
            4242,
            false,
            "",
            "",
            "",
            "Quassel Test",
            null,
            null,
            List.of(),
            List.of(),
            null,
            IrcProperties.Server.Backend.QUASSEL_CORE);

    assertEquals("nick", QuasselCoreAuthHandshake.configuredAuthUser(nickOnly));
    assertEquals("quassel-user", QuasselCoreAuthHandshake.configuredAuthUser(fullyBlank));
  }

  @Test
  void configuredAuthPasswordFallsBackToEnabledSasl() {
    IrcProperties.Server withSasl =
        new IrcProperties.Server(
            "quassel",
            "irc.example.net",
            4242,
            false,
            "",
            "nick",
            "alice",
            "Quassel Test",
            new IrcProperties.Server.Sasl(true, "alice", "sasl-secret", "PLAIN", null),
            new IrcProperties.Server.Nickserv(false, "", "NickServ", null),
            List.of(),
            List.of(),
            null,
            IrcProperties.Server.Backend.QUASSEL_CORE);

    assertEquals("sasl-secret", QuasselCoreAuthHandshake.configuredAuthPassword(withSasl));
  }

  @Test
  void performCoreSetupSendsCoreSetupDataAndAcceptsAck() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake handshake = new QuasselCoreAuthHandshake(codec);

    byte[] inbound =
        encodeFrames(
            codec,
            message(
                "ClientInitAck",
                field("CoreConfigured", false),
                field(
                    "BackendInfo",
                    List.of(Map.of("BackendId", "SQLite"), Map.of("BackendId", "PostgreSQL"))),
                field("AuthenticatorInfo", List.of(Map.of("AuthenticatorId", "Database")))),
            message("CoreSetupAck"));
    ScriptedSocket socket = new ScriptedSocket(inbound);

    handshake.performCoreSetup(
        socket,
        new QuasselCoreAuthHandshake.CoreSetupRequest(
            "admin",
            "secret",
            "SQLite",
            "Database",
            Map.of("DatabaseName", "quassel-storage"),
            Map.of()));

    ByteArrayInputStream outbound = new ByteArrayInputStream(socket.writtenBytes());
    QuasselCoreDatastreamCodec.HandshakeMessage first = codec.readHandshakeMessage(outbound);
    QuasselCoreDatastreamCodec.HandshakeMessage second = codec.readHandshakeMessage(outbound);

    assertEquals("ClientInit", first.messageType());
    assertEquals("CoreSetupData", second.messageType());
    @SuppressWarnings("unchecked")
    Map<String, Object> setupData = (Map<String, Object>) second.fields().get("SetupData");
    assertEquals("admin", setupData.get("AdminUser"));
    assertEquals("secret", setupData.get("AdminPasswd"));
    assertEquals("SQLite", setupData.get("Backend"));
    assertEquals("Database", setupData.get("Authenticator"));
  }

  @Test
  void performCoreSetupFailsWhenCoreRejectsSetup() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreAuthHandshake handshake = new QuasselCoreAuthHandshake(codec);
    byte[] inbound =
        encodeFrames(
            codec,
            message("ClientInitAck", field("CoreConfigured", false)),
            message("CoreSetupReject", field("Error", "admin user already exists")));
    ScriptedSocket socket = new ScriptedSocket(inbound);

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                handshake.performCoreSetup(
                    socket,
                    new QuasselCoreAuthHandshake.CoreSetupRequest(
                        "admin", "secret", "SQLite", "Database", Map.of(), Map.of())));

    assertTrue(err.getMessage().contains("already exists"));
  }

  @SafeVarargs
  private static LinkedHashMap<String, Object> message(
      String msgType, java.util.Map.Entry<String, Object>... additionalFields) {
    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
    map.put("MsgType", msgType);
    for (java.util.Map.Entry<String, Object> field : additionalFields) {
      map.put(field.getKey(), field.getValue());
    }
    return map;
  }

  private static java.util.Map.Entry<String, Object> field(String key, Object value) {
    return java.util.Map.entry(key, value);
  }

  @SafeVarargs
  private static byte[] encodeFrames(
      QuasselCoreDatastreamCodec codec, LinkedHashMap<String, Object>... frames)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (LinkedHashMap<String, Object> frame : frames) {
      codec.writeHandshakeMessage(out, frame);
    }
    return out.toByteArray();
  }

  private static IrcProperties.Server server(String login, String password) {
    return new IrcProperties.Server(
        "quassel",
        "irc.example.net",
        4242,
        false,
        password,
        "nick",
        login,
        "Quassel Test",
        new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null),
        new IrcProperties.Server.Nickserv(false, "", "NickServ", null),
        List.of(),
        List.of(),
        null,
        IrcProperties.Server.Backend.QUASSEL_CORE);
  }

  private static final class ScriptedSocket extends Socket {
    private final ByteArrayInputStream input;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private volatile boolean closed;

    private ScriptedSocket(byte[] inbound) {
      this.input = new ByteArrayInputStream(inbound);
    }

    @Override
    public InputStream getInputStream() {
      return input;
    }

    @Override
    public OutputStream getOutputStream() {
      return output;
    }

    @Override
    public synchronized void close() {
      closed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }

    private byte[] writtenBytes() {
      return output.toByteArray();
    }
  }
}
