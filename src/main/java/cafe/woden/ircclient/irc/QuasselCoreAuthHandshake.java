package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
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
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Performs Quassel datastream handshake/authentication (ClientInit + ClientLogin). */
@Component
@InfrastructureLayer
public class QuasselCoreAuthHandshake {
  private static final int MAX_HANDSHAKE_MESSAGES = 16;

  private final QuasselCoreDatastreamCodec datastreamCodec;

  public QuasselCoreAuthHandshake(QuasselCoreDatastreamCodec datastreamCodec) {
    this.datastreamCodec = Objects.requireNonNull(datastreamCodec, "datastreamCodec");
  }

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

      if ("ClientInitReject".equals(type)) {
        throw new IllegalStateException(
            renderHandshakeError(fields, "ClientInit rejected by core"));
      }

      if ("ClientInitAck".equals(type)) {
        if (!isCoreConfigured(fields)) {
          throw coreSetupRequired(fields, "core is not configured for client logins");
        }
        sendClientLogin(out, authUser, authPassword);
        loginSent = true;
        continue;
      }

      if ("ClientLoginReject".equals(type)) {
        throw new IllegalStateException(
            renderHandshakeError(fields, "ClientLogin rejected by core"));
      }

      if ("ClientLoginAck".equals(type)) {
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
        return new AuthResult(authUser, primaryNetworkId, networkIds, initialBuffers);
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

  private static boolean isCoreConfigured(Map<String, Object> fields) {
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
      Map<Integer, QuasselCoreDatastreamCodec.BufferInfoValue> initialBuffers) {}

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
