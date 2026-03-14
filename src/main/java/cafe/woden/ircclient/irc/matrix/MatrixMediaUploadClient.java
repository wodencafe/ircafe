package cafe.woden.ircclient.irc.matrix;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Uploads local media files to Matrix via {@code /_matrix/media/v3/upload}. */
@Component
@InfrastructureLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixMediaUploadClient {

  private static final Map<String, String> REQUEST_HEADERS =
      Map.of(
          "User-Agent", "ircafe-matrix-upload/1.0",
          "Accept", "application/json",
          "Accept-Encoding", "gzip");

  private static final ObjectMapper JSON = new ObjectMapper();

  @NonNull private final ServerProxyResolver proxyResolver;

  UploadResult uploadFile(
      String serverId, IrcProperties.Server server, String accessToken, String uploadPath) {
    URI fallbackEndpoint = MatrixEndpointResolver.mediaUploadUri(server, "");
    String token = normalize(accessToken);
    if (token.isEmpty()) {
      return UploadResult.failed(fallbackEndpoint, "access token is blank");
    }

    Path path;
    try {
      path = resolveUploadPath(uploadPath);
    } catch (IllegalArgumentException ex) {
      return UploadResult.failed(fallbackEndpoint, ex.getMessage());
    }
    String fileName = safeFileName(path);
    URI endpoint = MatrixEndpointResolver.mediaUploadUri(server, fileName);

    if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
      return UploadResult.failed(endpoint, "upload path is not a readable file");
    }

    byte[] payload;
    try {
      payload = Files.readAllBytes(path);
    } catch (IOException ex) {
      String msg = normalize(ex.getMessage());
      if (msg.isEmpty()) {
        msg = ex.getClass().getSimpleName();
      }
      return UploadResult.failed(endpoint, msg);
    }
    String contentType = detectContentType(path, fileName);

    Map<String, String> headers = new HashMap<>(REQUEST_HEADERS);
    headers.put("Authorization", "Bearer " + token);
    headers.put("Content-Type", contentType);

    ProxyPlan plan = proxyResolver.planForServer(serverId);
    try {
      HttpLite.Response<String> response =
          HttpLite.postBytes(
              endpoint,
              headers,
              payload,
              plan.proxy(),
              plan.connectTimeoutMs(),
              plan.readTimeoutMs());
      int code = response.statusCode();
      String body = Objects.toString(response.body(), "");
      if (code < 200 || code >= 300) {
        return UploadResult.failed(endpoint, "HTTP " + code + " from media upload endpoint");
      }

      String contentUri = parseContentUri(body);
      if (contentUri.isEmpty()) {
        return UploadResult.failed(endpoint, "upload response did not include content_uri");
      }
      return UploadResult.success(endpoint, contentUri, fileName, contentType, payload.length);
    } catch (IOException ex) {
      String msg = normalize(ex.getMessage());
      if (msg.isEmpty()) {
        msg = ex.getClass().getSimpleName();
      }
      return UploadResult.failed(endpoint, msg);
    }
  }

  private static Path resolveUploadPath(String uploadPath) {
    String raw = normalize(uploadPath);
    if (raw.isEmpty()) {
      throw new IllegalArgumentException("upload path is blank");
    }
    try {
      if (raw.regionMatches(true, 0, "file:", 0, 5)) {
        return Path.of(URI.create(raw));
      }
      return Path.of(raw);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("invalid upload path", ex);
    }
  }

  private static String safeFileName(Path path) {
    if (path == null) {
      return "upload.bin";
    }
    Path leaf = path.getFileName();
    String name = leaf == null ? "" : normalize(leaf.toString());
    return name.isEmpty() ? "upload.bin" : name;
  }

  private static String detectContentType(Path path, String fileName) {
    try {
      String detected = normalize(Files.probeContentType(path));
      if (!detected.isEmpty()) {
        return detected;
      }
    } catch (IOException ignored) {
      // Best effort only; fall back below.
    }
    String guessed = normalize(URLConnection.guessContentTypeFromName(fileName));
    return guessed.isEmpty() ? "application/octet-stream" : guessed;
  }

  private static String parseContentUri(String responseBody) {
    String json = normalize(responseBody);
    if (json.isEmpty()) {
      return "";
    }
    try {
      JsonNode root = JSON.readTree(json);
      return normalize(root.path("content_uri").asText(""));
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  record UploadResult(
      boolean success,
      URI endpoint,
      String contentUri,
      String fileName,
      String contentType,
      long contentLength,
      String detail) {
    static UploadResult success(
        URI endpoint, String contentUri, String fileName, String contentType, long contentLength) {
      return new UploadResult(
          true,
          Objects.requireNonNull(endpoint, "endpoint"),
          normalize(contentUri),
          normalize(fileName),
          normalize(contentType),
          Math.max(0L, contentLength),
          "");
    }

    static UploadResult failed(URI endpoint, String detail) {
      String message = normalize(detail);
      if (message.isEmpty()) {
        message = "media upload failed";
      }
      return new UploadResult(
          false, Objects.requireNonNull(endpoint, "endpoint"), "", "", "", 0L, message);
    }
  }
}
