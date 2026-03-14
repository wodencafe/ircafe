package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Performs Quassel datastream handshake/authentication (ClientInit + ClientLogin). */
@Component
@InfrastructureLayer
@RequiredArgsConstructor
public class QuasselCoreAuthHandshake {
  private static final Logger log = LoggerFactory.getLogger(QuasselCoreAuthHandshake.class);
  private static final int MAX_HANDSHAKE_MESSAGES = 16;

  @NonNull private final QuasselCoreDatastreamCodec datastreamCodec;

  public AuthResult authenticate(Socket socket, IrcProperties.Server server) throws IOException {
    Socket s = Objects.requireNonNull(socket, "socket");
    IrcProperties.Server cfg = Objects.requireNonNull(server, "server");

    OutputStream out = s.getOutputStream();
    InputStream in = s.getInputStream();

    String authUser = configuredAuthUser(cfg);
    String authPassword = configuredAuthPassword(cfg);

    sendClientInit(out);

    boolean loginSent = false;
    for (int i = 0; i < MAX_HANDSHAKE_MESSAGES; i++) {
      QuasselCoreDatastreamCodec.HandshakeMessage message =
          datastreamCodec.readHandshakeMessage(in);
      String type = Objects.toString(message.messageType(), "").trim();
      Map<String, Object> fields = message.fields();
      log.info("Quassel handshake message: type={}, fieldKeys={}", type, fields.keySet());

      if ("ClientInitReject".equals(type)) {
        throw new IllegalStateException(
            renderHandshakeError(fields, "ClientInit rejected by core"));
      }

      if ("ClientInitAck".equals(type)) {
        log.info(
            "Quassel ClientInitAck: coreConfigured={}, fields={}",
            isCoreConfigured(fields),
            fields);
        if (!isCoreConfigured(fields)) {
          throw coreSetupRequired(fields, "core is not configured for client logins");
        }
        sendClientLogin(out, authUser, authPassword);
        loginSent = true;
        continue;
      }

      if ("ClientLoginReject".equals(type)) {
        log.info("Quassel ClientLoginReject fields={}", fields);
        throw new IllegalStateException(
            renderHandshakeError(fields, "ClientLogin rejected by core"));
      }

      if ("ClientLoginAck".equals(type)) {
        log.info("Quassel ClientLoginAck fields={}", fields);
        continue;
      }

      if ("SessionInit".equals(type)) {
        if (!loginSent) {
          throw new IllegalStateException("received SessionInit before login completed");
        }
        List<Integer> networkIds = extractNetworkIds(fields);
        int primaryNetworkId = networkIds.isEmpty() ? -1 : networkIds.get(0);
        Map<Integer, QuasselCoreDatastreamCodec.BufferInfoValue> initialBuffers =
            extractBufferInfos(fields);
        Map<Integer, Map<String, Object>> initialIdentities = extractIdentityStates(fields);
        log.info(
            "Quassel SessionInit summary: authUser={}, primaryNetworkId={}, networkIds={}, initialBufferCount={}, initialIdentityCount={}, sessionStateKeys={}, fields={}",
            authUser,
            primaryNetworkId,
            networkIds,
            initialBuffers.size(),
            initialIdentities.size(),
            extractSessionStateKeys(fields),
            fields);
        return new AuthResult(
            authUser, primaryNetworkId, networkIds, initialBuffers, initialIdentities);
      }

      if ("CoreSetupAck".equals(type)
          || "CoreSetupReject".equals(type)
          || "CoreSetupData".equals(type)) {
        String fallback =
            switch (type) {
              case "CoreSetupAck" ->
                  "Quassel Core setup acknowledged; reconnect after setup completes";
              case "CoreSetupReject" -> "Quassel Core setup rejected";
              case "CoreSetupData" -> "Quassel Core requires initial setup before login";
              default -> "Quassel Core setup is required before login";
            };
        throw coreSetupRequired(fields, fallback);
      }

      throw new IllegalStateException("unexpected Quassel handshake message type: " + type);
    }

    throw new IOException(
        "Quassel handshake did not reach SessionInit within expected message limit");
  }

  /**
   * Perform Quassel Core initial setup exchange for an unconfigured core.
   *
   * <p>Sequence: ClientInit -> ClientInitAck/CoreSetupData -> CoreSetupData -> CoreSetupAck.
   */
  public void performCoreSetup(Socket socket, CoreSetupRequest request) throws IOException {
    Socket s = Objects.requireNonNull(socket, "socket");
    CoreSetupRequest req = Objects.requireNonNull(request, "request");

    String adminUser = Objects.toString(req.adminUser(), "").trim();
    if (adminUser.isEmpty()) {
      throw new IllegalArgumentException("admin user is required");
    }
    String adminPassword = Objects.toString(req.adminPassword(), "");
    if (adminPassword.isBlank()) {
      throw new IllegalArgumentException("admin password is required");
    }

    String storageBackend = Objects.toString(req.storageBackend(), "").trim();
    if (storageBackend.isEmpty()) {
      throw new IllegalArgumentException("storage backend is required");
    }
    String authenticator = Objects.toString(req.authenticator(), "").trim();
    if (authenticator.isEmpty()) {
      throw new IllegalArgumentException("authenticator is required");
    }

    Map<String, Object> storageSetupData = normalizeSetupMap(req.storageSetupData());
    Map<String, Object> authSetupData = normalizeSetupMap(req.authSetupData());

    OutputStream out = s.getOutputStream();
    InputStream in = s.getInputStream();
    sendClientInit(out);

    boolean setupSent = false;
    for (int i = 0; i < MAX_HANDSHAKE_MESSAGES; i++) {
      QuasselCoreDatastreamCodec.HandshakeMessage message =
          datastreamCodec.readHandshakeMessage(in);
      String type = Objects.toString(message.messageType(), "").trim();
      Map<String, Object> fields = message.fields();
      log.info("Quassel setup handshake message: type={}, fieldKeys={}", type, fields.keySet());

      if ("ClientInitReject".equals(type)) {
        throw new IllegalStateException(
            renderHandshakeError(fields, "ClientInit rejected by core"));
      }

      if ("ClientInitAck".equals(type)) {
        log.info(
            "Quassel setup ClientInitAck: coreConfigured={}, fields={}",
            isCoreConfigured(fields),
            fields);
        if (isCoreConfigured(fields)) {
          throw new IllegalStateException("Quassel Core is already configured");
        }
        sendCoreSetupData(
            out,
            adminUser,
            adminPassword,
            storageBackend,
            storageSetupData,
            authenticator,
            authSetupData);
        setupSent = true;
        continue;
      }

      if ("CoreSetupData".equals(type)) {
        log.info("Quassel setup CoreSetupData fields={}", fields);
        // Some cores explicitly send CoreSetupData after ClientInitAck.
        if (!setupSent) {
          sendCoreSetupData(
              out,
              adminUser,
              adminPassword,
              storageBackend,
              storageSetupData,
              authenticator,
              authSetupData);
          setupSent = true;
        }
        continue;
      }

      if ("CoreSetupReject".equals(type)) {
        log.info("Quassel setup CoreSetupReject fields={}", fields);
        throw new IllegalStateException(
            renderHandshakeError(fields, "Quassel Core setup rejected"));
      }

      if ("CoreSetupAck".equals(type)) {
        log.info("Quassel setup CoreSetupAck fields={}", fields);
        return;
      }

      throw new IllegalStateException("unexpected Quassel setup message type: " + type);
    }

    throw new IOException("Quassel setup did not reach CoreSetupAck within expected message limit");
  }

  private void sendClientInit(OutputStream out) throws IOException {
    LinkedHashMap<String, Object> msg = new LinkedHashMap<>();
    msg.put("MsgType", "ClientInit");
    msg.put("ClientVersion", "IRCafe");
    msg.put("ClientDate", Long.toString(Instant.now().getEpochSecond()));
    msg.put("Features", 0L);
    msg.put("FeatureList", List.of());
    datastreamCodec.writeHandshakeMessage(out, msg);
  }

  private void sendClientLogin(OutputStream out, String user, String password) throws IOException {
    LinkedHashMap<String, Object> msg = new LinkedHashMap<>();
    msg.put("MsgType", "ClientLogin");
    msg.put("User", user);
    msg.put("Password", password);
    datastreamCodec.writeHandshakeMessage(out, msg);
  }

  private void sendCoreSetupData(
      OutputStream out,
      String adminUser,
      String adminPassword,
      String storageBackend,
      Map<String, Object> storageSetupData,
      String authenticator,
      Map<String, Object> authSetupData)
      throws IOException {
    LinkedHashMap<String, Object> setup = new LinkedHashMap<>();
    setup.put("AdminUser", adminUser);
    // Quassel core expects AdminPasswd (legacy spelling) inside SetupData.
    setup.put("AdminPasswd", adminPassword);
    setup.put("Backend", storageBackend);
    setup.put("SetupData", storageSetupData);
    setup.put("Authenticator", authenticator);
    setup.put("AuthProperties", authSetupData);

    LinkedHashMap<String, Object> msg = new LinkedHashMap<>();
    msg.put("MsgType", "CoreSetupData");
    msg.put("SetupData", setup);
    datastreamCodec.writeHandshakeMessage(out, msg);
  }

  private static Map<String, Object> normalizeSetupMap(Map<String, Object> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      if (entry == null) continue;
      String key = Objects.toString(entry.getKey(), "").trim();
      if (key.isEmpty()) continue;
      out.put(key, entry.getValue());
    }
    if (out.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(out);
  }

  private static boolean isCoreConfigured(Map<String, Object> fields) {
    Object coreConfigured = fields.get("CoreConfigured");
    if (coreConfigured instanceof Boolean value) {
      return value;
    }

    Object configured = fields.get("Configured");
    if (configured instanceof Boolean value) {
      return value;
    }

    Object loginEnabled = fields.get("LoginEnabled");
    if (loginEnabled instanceof Boolean value) {
      return value;
    }
    return true;
  }

  private static String renderHandshakeError(Map<String, Object> fields, String fallback) {
    String base = Objects.toString(fallback, "").trim();
    Object err = fields.get("Error");
    String detail = Objects.toString(err, "").trim();
    if (detail.isEmpty()) {
      return base;
    }
    if (base.isEmpty()) {
      return detail;
    }
    return base + ": " + detail;
  }

  private static CoreSetupRequiredException coreSetupRequired(
      Map<String, Object> fields, String fallback) {
    String detail = Objects.toString(fallback, "").trim();
    if (detail.isEmpty()) {
      detail = "Quassel Core setup is required before login";
    }
    String rendered = renderHandshakeError(fields, detail);
    return new CoreSetupRequiredException(
        rendered,
        fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields)));
  }

  @SuppressWarnings("unchecked")
  private static List<Integer> extractNetworkIds(Map<String, Object> fields) {
    Object stateRaw = fields.get("SessionState");
    if (!(stateRaw instanceof Map<?, ?> stateMap)) {
      return List.of();
    }
    Object networkIdsRaw = stateMap.get("NetworkIds");
    if (!(networkIdsRaw instanceof List<?> networkIdsList)) {
      return List.of();
    }

    ArrayList<Integer> out = new ArrayList<>(networkIdsList.size());
    for (Object value : networkIdsList) {
      if (value instanceof Number n) {
        out.add(n.intValue());
      }
    }
    if (out.isEmpty()) return List.of();
    return Collections.unmodifiableList(out);
  }

  @SuppressWarnings("unchecked")
  private static List<String> extractSessionStateKeys(Map<String, Object> fields) {
    if (fields == null || fields.isEmpty()) return List.of();
    Object stateRaw = fields.get("SessionState");
    if (!(stateRaw instanceof Map<?, ?> stateMap)) {
      return List.of();
    }
    ArrayList<String> keys = new ArrayList<>(stateMap.size());
    for (Object rawKey : stateMap.keySet()) {
      String key = Objects.toString(rawKey, "").trim();
      if (!key.isEmpty()) keys.add(key);
    }
    if (keys.isEmpty()) return List.of();
    return Collections.unmodifiableList(keys);
  }

  @SuppressWarnings("unchecked")
  private static Map<Integer, QuasselCoreDatastreamCodec.BufferInfoValue> extractBufferInfos(
      Map<String, Object> fields) {
    Object stateRaw = fields.get("SessionState");
    if (!(stateRaw instanceof Map<?, ?> stateMap)) {
      return Map.of();
    }
    Object bufferInfosRaw = stateMap.get("BufferInfos");
    if (!(bufferInfosRaw instanceof List<?> bufferInfosList)) {
      return Map.of();
    }

    LinkedHashMap<Integer, QuasselCoreDatastreamCodec.BufferInfoValue> out = new LinkedHashMap<>();
    for (Object value : bufferInfosList) {
      if (!(value instanceof QuasselCoreDatastreamCodec.BufferInfoValue info)) {
        continue;
      }
      out.put(info.bufferId(), info);
    }
    if (out.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(out);
  }

  @SuppressWarnings("unchecked")
  private static Map<Integer, Map<String, Object>> extractIdentityStates(
      Map<String, Object> fields) {
    Object stateRaw = fields.get("SessionState");
    if (!(stateRaw instanceof Map<?, ?> stateMap)) {
      return Map.of();
    }
    Object identitiesRaw = stateMap.get("Identities");
    if (!(identitiesRaw instanceof List<?> identitiesList)) {
      return Map.of();
    }

    LinkedHashMap<Integer, Map<String, Object>> out = new LinkedHashMap<>();
    for (Object value : identitiesList) {
      if (!(value instanceof Map<?, ?> identityMap) || identityMap.isEmpty()) continue;
      int identityId = identityIdFromMap(identityMap);
      if (identityId < 0) continue;
      LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : identityMap.entrySet()) {
        if (entry == null) continue;
        String key = Objects.toString(entry.getKey(), "").trim();
        if (key.isEmpty()) continue;
        normalized.put(key, entry.getValue());
      }
      out.put(identityId, Collections.unmodifiableMap(normalized));
    }
    if (out.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(out);
  }

  private static int identityIdFromMap(Map<?, ?> map) {
    if (map == null || map.isEmpty()) return -1;
    Object raw =
        firstNonNull(
            map.get("identityId"),
            map.get("identityid"),
            map.get("IdentityId"),
            map.get("IDENTITYID"));
    if (raw instanceof Number n) return n.intValue();
    String text = Objects.toString(raw, "").trim();
    if (text.isEmpty()) return -1;
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private static Object firstNonNull(Object... values) {
    if (values == null || values.length == 0) return null;
    for (Object value : values) {
      if (value != null) return value;
    }
    return null;
  }

  static String configuredAuthUser(IrcProperties.Server server) {
    String login = Objects.toString(server.login(), "").trim();
    if (!login.isEmpty()) return login;
    String nick = Objects.toString(server.nick(), "").trim();
    if (!nick.isEmpty()) return nick;
    return "quassel-user";
  }

  static String configuredAuthPassword(IrcProperties.Server server) {
    String pass = Objects.toString(server.serverPassword(), "").trim();
    if (!pass.isEmpty()) return pass;
    IrcProperties.Server.Sasl sasl = server.sasl();
    if (sasl != null && sasl.enabled()) {
      pass = Objects.toString(sasl.password(), "").trim();
      if (!pass.isEmpty()) return pass;
    }
    return "";
  }

  /** Successful completion of ClientInit/ClientLogin/SessionInit handshake. */
  public record AuthResult(
      String authUser,
      int primaryNetworkId,
      List<Integer> networkIds,
      Map<Integer, QuasselCoreDatastreamCodec.BufferInfoValue> initialBuffers,
      Map<Integer, Map<String, Object>> initialIdentities) {
    public AuthResult {
      networkIds = networkIds == null || networkIds.isEmpty() ? List.of() : List.copyOf(networkIds);
      initialBuffers =
          initialBuffers == null || initialBuffers.isEmpty()
              ? Map.of()
              : Collections.unmodifiableMap(new LinkedHashMap<>(initialBuffers));
      initialIdentities =
          initialIdentities == null || initialIdentities.isEmpty()
              ? Map.of()
              : Collections.unmodifiableMap(new LinkedHashMap<>(initialIdentities));
    }

    public AuthResult(
        String authUser,
        int primaryNetworkId,
        List<Integer> networkIds,
        Map<Integer, QuasselCoreDatastreamCodec.BufferInfoValue> initialBuffers) {
      this(authUser, primaryNetworkId, networkIds, initialBuffers, Map.of());
    }
  }

  /** User-provided parameters for Quassel Core initial setup. */
  public record CoreSetupRequest(
      String adminUser,
      String adminPassword,
      String storageBackend,
      String authenticator,
      Map<String, Object> storageSetupData,
      Map<String, Object> authSetupData) {}

  /** Indicates the core requires initial setup before an authenticated session can start. */
  public static final class CoreSetupRequiredException extends IllegalStateException {
    private final Map<String, Object> setupFields;

    CoreSetupRequiredException(String message, Map<String, Object> setupFields) {
      super(Objects.toString(message, "Quassel Core setup is required"));
      this.setupFields =
          setupFields == null
              ? Map.of()
              : Collections.unmodifiableMap(new LinkedHashMap<>(setupFields));
    }

    public Map<String, Object> setupFields() {
      return setupFields;
    }
  }
}
