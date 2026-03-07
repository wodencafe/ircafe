package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MatrixMediaUploadClientTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixMediaUploadClient uploadClient = new MatrixMediaUploadClient(proxyResolver);

  @Test
  void uploadFilePostsBinaryPayloadAndParsesContentUri() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    Path tmp = Files.createTempFile("matrix-upload-", ".bin");
    byte[] payload = "binary-payload".getBytes(StandardCharsets.UTF_8);
    Files.write(tmp, payload);

    try (TestServer server =
        TestServer.start(200, "{\"content_uri\":\"mxc://matrix.example.org/upload-1\"}")) {
      MatrixMediaUploadClient.UploadResult result =
          uploadClient.uploadFile(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              tmp.toString());

      assertTrue(result.success());
      assertEquals("mxc://matrix.example.org/upload-1", result.contentUri());
      assertEquals("POST", server.lastMethod().get());
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertEquals("/_matrix/media/v3/upload", server.lastPath().get());
      assertTrue(server.lastQuery().get().startsWith("filename="));
      assertFalse(server.lastContentType().get().isBlank());
      assertArrayEquals(payload, server.lastRequestBody().get());
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  @Test
  void uploadFileAcceptsFileUriSourcePath() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    Path tmp = Files.createTempFile("matrix-upload-uri-", ".txt");
    Files.writeString(tmp, "hello upload");

    try (TestServer server =
        TestServer.start(200, "{\"content_uri\":\"mxc://matrix.example.org/upload-uri\"}")) {
      MatrixMediaUploadClient.UploadResult result =
          uploadClient.uploadFile(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              tmp.toUri().toString());

      assertTrue(result.success());
      assertEquals("mxc://matrix.example.org/upload-uri", result.contentUri());
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  @Test
  void uploadFileRejectsBlankAccessToken() {
    MatrixMediaUploadClient.UploadResult result =
        uploadClient.uploadFile(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            " ",
            "/tmp/file.bin");

    assertFalse(result.success());
    assertEquals("access token is blank", result.detail());
  }

  @Test
  void uploadFileRejectsUnreadableFilePath() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixMediaUploadClient.UploadResult result =
        uploadClient.uploadFile(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            "secret-token",
            "/tmp/does-not-exist-matrix-upload.bin");

    assertFalse(result.success());
    assertEquals("upload path is not a readable file", result.detail());
  }

  @Test
  void uploadFileReportsHttpErrors() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    Path tmp = Files.createTempFile("matrix-upload-http-", ".bin");
    Files.writeString(tmp, "hello");

    try (TestServer server = TestServer.start(413, "{\"errcode\":\"M_TOO_LARGE\"}")) {
      MatrixMediaUploadClient.UploadResult result =
          uploadClient.uploadFile(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              tmp.toString());

      assertFalse(result.success());
      assertEquals("HTTP 413 from media upload endpoint", result.detail());
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  private static ProxyPlan directPlan() {
    IrcProperties.Proxy cfg = new IrcProperties.Proxy(false, "", 0, "", "", true, 3_000, 3_000);
    return new ProxyPlan(cfg, Proxy.NO_PROXY, 3_000, 3_000);
  }

  private static IrcProperties.Server serverConfig(String id, String host, int port, boolean tls) {
    return new IrcProperties.Server(
        id,
        host,
        port,
        tls,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        null,
        List.of(),
        List.of(),
        null,
        IrcProperties.Server.Backend.MATRIX);
  }

  private record TestServer(
      HttpServer server,
      int port,
      AtomicReference<String> lastMethod,
      AtomicReference<String> lastPath,
      AtomicReference<String> lastQuery,
      AtomicReference<String> lastAuthorizationHeader,
      AtomicReference<String> lastContentType,
      AtomicReference<byte[]> lastRequestBody)
      implements AutoCloseable {

    static TestServer start(int statusCode, String body) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> method = new AtomicReference<>("");
      AtomicReference<String> path = new AtomicReference<>("");
      AtomicReference<String> query = new AtomicReference<>("");
      AtomicReference<String> auth = new AtomicReference<>("");
      AtomicReference<String> contentType = new AtomicReference<>("");
      AtomicReference<byte[]> requestBody = new AtomicReference<>(new byte[0]);
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/_matrix/media/v3/upload",
          exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getRawPath());
            query.set(String.valueOf(exchange.getRequestURI().getRawQuery()));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(exchange.getRequestBody().readAllBytes());
            reply(exchange, statusCode, payload);
          });
      server.start();
      return new TestServer(
          server,
          server.getAddress().getPort(),
          method,
          path,
          query,
          auth,
          contentType,
          requestBody);
    }

    @Override
    public void close() {
      server.stop(0);
    }

    private static void reply(HttpExchange exchange, int statusCode, byte[] payload)
        throws IOException {
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(statusCode, payload.length);
      exchange.getResponseBody().write(payload);
      exchange.close();
    }
  }
}
