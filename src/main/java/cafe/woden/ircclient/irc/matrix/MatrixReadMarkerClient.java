package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Updates Matrix read markers via {@code /_matrix/client/v3/rooms/{roomId}/read_markers}. */
@Component
@InfrastructureLayer
final class MatrixReadMarkerClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-read-marker/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip",
          "Content-Type", "application/json");

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ServerProxyResolver proxyResolver;

  MatrixReadMarkerClient(ServerProxyResolver proxyResolver) {
    this.proxyResolver = Objects.requireNonNull(proxyResolver, "proxyResolver");
  }

  ReadMarkerResult updateReadMarker(
      String serverId,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String eventId) {
    URI endpoint = MatrixEndpointResolver.roomReadMarkersUri(server, roomId);
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return ReadMarkerResult.failed(endpoint, "access token is blank");
    }
    String eid = normalize(eventId);
    if (eid.isEmpty()) {
      return ReadMarkerResult.failed(endpoint, "event id is blank");
    }

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);

    ObjectNode payload = JSON.createObjectNode();
    payload.put("m.read", eid);
    payload.put("m.fully_read", eid);

    try {
      HttpLite.Response<String> response =
          HttpLite.postString(
              endpoint,
              headers,
              payload.toString(),
              plan.proxy(),
              plan.connectTimeoutMs(),
              plan.readTimeoutMs());
      int code = response.statusCode();
      if (code < 200 || code >= 300) {
        return ReadMarkerResult.failed(endpoint, "HTTP " + code + " from read marker endpoint");
      }
      return ReadMarkerResult.success(endpoint, roomId, eid);
    } catch (IOException ex) {
      String message = normalize(ex.getMessage());
      if (message.isEmpty()) {
        message = ex.getClass().getSimpleName();
      }
      return ReadMarkerResult.failed(endpoint, message);
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record ReadMarkerResult(
      boolean success, URI endpoint, String roomId, String eventId, String detail) {
    static ReadMarkerResult success(URI endpoint, String roomId, String eventId) {
      return new ReadMarkerResult(
          true,
          Objects.requireNonNull(endpoint, "endpoint"),
          normalize(roomId),
          normalize(eventId),
          "");
    }

    static ReadMarkerResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "read marker update failed";
      }
      return new ReadMarkerResult(
          false, Objects.requireNonNull(endpoint, "endpoint"), "", "", message);
    }
  }
}
