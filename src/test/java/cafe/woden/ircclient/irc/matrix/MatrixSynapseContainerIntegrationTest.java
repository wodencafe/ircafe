package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Containerized Matrix smoke integration coverage against a local Synapse homeserver.
 *
 * <p>Disabled by default. Enable explicitly with:
 *
 * <pre>
 * ./gradlew integrationTest --tests '*MatrixSynapseContainerIntegrationTest' \
 *   -Dmatrix.it.container.enabled=true
 * </pre>
 *
 * <p>Optional properties/env vars:
 *
 * <ul>
 *   <li>{@code matrix.it.container.image} / {@code MATRIX_IT_CONTAINER_IMAGE}
 *   <li>{@code matrix.it.container.server-name} / {@code MATRIX_IT_CONTAINER_SERVER_NAME}
 *   <li>{@code matrix.it.container.startup-timeout-seconds}
 * </ul>
 */
class MatrixSynapseContainerIntegrationTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private static final String SERVER_ID = "matrix-it";
  private static final String ALICE = "alice";
  private static final String BOB = "bob";
  private static final String ALICE_PASSWORD = "alice-password";
  private static final String BOB_PASSWORD = "bob-password";
  private static final String MESSAGE_TEXT = "hello from matrix synapse testcontainer";
  private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(20);

  @Test
  void containerizedSynapseSupportsProbeWhoamiSendSyncAndHistory() throws Exception {
    ContainerConfig cfg = ContainerConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Matrix Synapse container integration test disabled."
            + " Set -Dmatrix.it.container.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName image = DockerImageName.parse(cfg.image());
    Path dataDir = Files.createTempDirectory("matrix-synapse-it-");
    try {
      generateSynapseConfig(image, cfg, dataDir);
      configureRegistrationSharedSecret(dataDir.resolve("homeserver.yaml"), cfg.registrationSecret());

      try (GenericContainer<?> synapse = newSynapseContainer(image, cfg, dataDir)) {
        synapse.start();

        IrcProperties.Server server =
            serverConfig(SERVER_ID, synapse.getHost(), synapse.getMappedPort(cfg.httpPort()), false);
        ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
        when(proxyResolver.planForServer(SERVER_ID)).thenReturn(directPlan());

        registerUser(synapse, cfg, ALICE, ALICE_PASSWORD);
        registerUser(synapse, cfg, BOB, BOB_PASSWORD);

        LoginResult aliceLogin = login(server, ALICE, ALICE_PASSWORD);
        LoginResult bobLogin = login(server, BOB, BOB_PASSWORD);

        MatrixHomeserverProbe probe = new MatrixHomeserverProbe(proxyResolver);
        MatrixRoomMessageSender sender = new MatrixRoomMessageSender(proxyResolver);
        MatrixSyncClient syncClient = new MatrixSyncClient(proxyResolver);
        MatrixRoomHistoryClient historyClient = new MatrixRoomHistoryClient(proxyResolver);

        MatrixHomeserverProbe.ProbeResult probeResult = probe.probe(SERVER_ID, server);
        assertTrue(probeResult.reachable(), "versions probe should succeed: " + probeResult.detail());

        MatrixHomeserverProbe.WhoamiResult whoami =
            probe.whoami(SERVER_ID, server, aliceLogin.accessToken());
        assertTrue(whoami.authenticated(), "whoami should authenticate: " + whoami.detail());
        assertEquals(aliceLogin.userId(), whoami.userId());

        String roomId = createPrivateRoom(server, aliceLogin.accessToken(), bobLogin.userId());
        joinRoom(server, bobLogin.accessToken(), roomId);

        MatrixRoomMessageSender.SendResult sendResult =
            sender.sendRoomMessage(
                SERVER_ID,
                server,
                aliceLogin.accessToken(),
                roomId,
                "txn-matrix-it-1",
                MESSAGE_TEXT);
        assertTrue(
            sendResult.accepted(), "room send should succeed: " + sendResult.endpoint() + " " + sendResult.detail());

        SyncObservation observation =
            awaitSyncedRoomMessage(syncClient, server, bobLogin.accessToken(), roomId, MESSAGE_TEXT);

        assertFalse(observation.nextBatch().isEmpty(), "sync next_batch should be present");
        MatrixRoomHistoryClient.HistoryResult history =
            historyClient.fetchMessagesBefore(
                SERVER_ID,
                server,
                bobLogin.accessToken(),
                roomId,
                observation.nextBatch(),
                50);
        assertTrue(history.success(), "history fetch should succeed: " + history.detail());
        assertTrue(
            history.events().stream().anyMatch(event -> MESSAGE_TEXT.equals(event.body())),
            "history should contain the sent message");
      }
    } finally {
      deleteRecursively(dataDir);
    }
  }

  private static GenericContainer<?> newSynapseContainer(
      DockerImageName image, ContainerConfig cfg, Path dataDir) {
    return new GenericContainer<>(image)
        .withFileSystemBind(dataDir.toAbsolutePath().toString(), "/data")
        .withEnv("SYNAPSE_SERVER_NAME", cfg.serverName())
        .withEnv("SYNAPSE_REPORT_STATS", "no")
        .withExposedPorts(cfg.httpPort())
        .waitingFor(
            Wait.forHttp("/_matrix/client/versions").forPort(cfg.httpPort()).forStatusCode(200))
        .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()));
  }

  private static void generateSynapseConfig(DockerImageName image, ContainerConfig cfg, Path dataDir) {
    try (GenericContainer<?> generator =
        new GenericContainer<>(image)
            .withFileSystemBind(dataDir.toAbsolutePath().toString(), "/data")
            .withEnv("SYNAPSE_SERVER_NAME", cfg.serverName())
            .withEnv("SYNAPSE_REPORT_STATS", "no")
            .withCommand("generate")
            .withStartupCheckStrategy(new OneShotStartupCheckStrategy())
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()))) {
      generator.start();
    }

    Path homeserverYaml = dataDir.resolve("homeserver.yaml");
    assertTrue(Files.exists(homeserverYaml), "expected generated Synapse config at " + homeserverYaml);
  }

  private static void configureRegistrationSharedSecret(Path homeserverYaml, String sharedSecret)
      throws IOException {
    String secret = normalize(sharedSecret);
    if (secret.isEmpty()) {
      throw new IllegalArgumentException("registration shared secret is blank");
    }

    String line = "registration_shared_secret: \"" + secret + "\"";
    String yaml = Files.readString(homeserverYaml);
    String updated = yaml.replaceAll("(?m)^\\s*#?\\s*registration_shared_secret:.*$", line);
    if (updated.equals(yaml)) {
      updated = yaml + System.lineSeparator() + line + System.lineSeparator();
    }
    Files.writeString(
        homeserverYaml,
        updated,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
  }

  private static void registerUser(
      GenericContainer<?> synapse, ContainerConfig cfg, String username, String password)
      throws Exception {
    List<String> args =
        List.of(
            "-u",
            username,
            "-p",
            password,
            "-a",
            "-c",
            "/data/homeserver.yaml",
            "http://localhost:" + cfg.httpPort());
    Container.ExecResult result = execRegistrationTool(synapse, "register_new_matrix_user", args);
    if (result.getExitCode() == 0) return;

    Container.ExecResult fallback =
        execRegistrationTool(synapse, "synapse_register_new_matrix_user", args);
    if (fallback.getExitCode() == 0) return;

    fail(
        "Matrix user registration failed."
            + " primary stderr="
            + Objects.toString(result.getStderr(), "")
            + " fallback stderr="
            + Objects.toString(fallback.getStderr(), ""));
  }

  private static Container.ExecResult execRegistrationTool(
      GenericContainer<?> synapse, String tool, List<String> args) throws Exception {
    String[] command = new String[args.size() + 1];
    command[0] = tool;
    for (int i = 0; i < args.size(); i++) {
      command[i + 1] = args.get(i);
    }
    return synapse.execInContainer(command);
  }

  private static LoginResult login(IrcProperties.Server server, String username, String password)
      throws Exception {
    ObjectNode body = JSON.createObjectNode();
    body.put("type", "m.login.password");
    ObjectNode identifier = body.putObject("identifier");
    identifier.put("type", "m.id.user");
    identifier.put("user", username);
    body.put("password", password);

    JsonNode root = postJson(appendClientPath(server, "login"), body, "");
    String accessToken = normalize(root.path("access_token").asText(""));
    String userId = normalize(root.path("user_id").asText(""));
    assertFalse(accessToken.isEmpty(), "login access_token should be present");
    assertFalse(userId.isEmpty(), "login user_id should be present");
    return new LoginResult(userId, accessToken);
  }

  private static String createPrivateRoom(
      IrcProperties.Server server, String creatorAccessToken, String inviteeUserId) throws Exception {
    ObjectNode body = JSON.createObjectNode();
    body.put("preset", "private_chat");
    body.put("is_direct", true);
    body.putArray("invite").add(inviteeUserId);

    JsonNode root = postJson(MatrixEndpointResolver.createRoomUri(server), body, creatorAccessToken);
    String roomId = normalize(root.path("room_id").asText(""));
    assertTrue(roomId.startsWith("!"), "createRoom should return a valid room id");
    return roomId;
  }

  private static void joinRoom(IrcProperties.Server server, String accessToken, String roomId)
      throws Exception {
    postJson(MatrixEndpointResolver.joinRoomUri(server, roomId), JSON.createObjectNode(), accessToken);
  }

  private static SyncObservation awaitSyncedRoomMessage(
      MatrixSyncClient syncClient,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String expectedBody)
      throws Exception {
    long deadline = System.nanoTime() + MESSAGE_TIMEOUT.toNanos();
    String since = "";
    while (System.nanoTime() < deadline) {
      MatrixSyncClient.SyncResult result = syncClient.sync(SERVER_ID, server, accessToken, since, 1_000);
      assertTrue(result.success(), "sync request failed: " + result.detail());
      String nextBatch = normalize(result.nextBatch());

      for (MatrixSyncClient.RoomTimelineEvent event : result.events()) {
        if (event == null) continue;
        if (!roomId.equals(normalize(event.roomId()))) continue;
        if (!expectedBody.equals(Objects.toString(event.body(), ""))) continue;
        return new SyncObservation(event, nextBatch);
      }

      if (!nextBatch.isEmpty()) {
        since = nextBatch;
      }
      Thread.sleep(250L);
    }
    throw new AssertionError("did not observe expected room message via sync within timeout");
  }

  private static JsonNode postJson(URI uri, JsonNode payload, String accessToken) throws Exception {
    JsonNode safePayload = payload == null ? JSON.createObjectNode() : payload;
    String body = JSON.writeValueAsString(safePayload);

    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    String token = normalize(accessToken);
    if (!token.isEmpty()) {
      request.header("Authorization", "Bearer " + token);
    }

    HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    int code = response.statusCode();
    String responseBody = Objects.toString(response.body(), "");
    assertTrue(
        code >= 200 && code < 300,
        "HTTP " + code + " from " + uri + " body=" + responseBody);
    return responseBody.isBlank() ? JSON.createObjectNode() : JSON.readTree(responseBody);
  }

  private static URI appendClientPath(IrcProperties.Server server, String pathSuffix) {
    URI apiBase = MatrixEndpointResolver.clientApiBaseUri(server);
    String suffix = normalize(pathSuffix);
    if (suffix.startsWith("/")) suffix = suffix.substring(1);
    return URI.create(apiBase.toString() + "/" + suffix);
  }

  private static ProxyPlan directPlan() {
    IrcProperties.Proxy cfg = new IrcProperties.Proxy(false, "", 0, "", "", true, 3_000, 30_000);
    return new ProxyPlan(cfg, Proxy.NO_PROXY, 3_000, 30_000);
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

  private static void deleteRecursively(Path root) throws IOException {
    if (root == null || !Files.exists(root)) return;
    try (var walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
              });
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }

  private record LoginResult(String userId, String accessToken) {}

  private record SyncObservation(MatrixSyncClient.RoomTimelineEvent event, String nextBatch) {}

  private record ContainerConfig(
      boolean enabled,
      String image,
      String serverName,
      int httpPort,
      int startupTimeoutSeconds,
      String registrationSecret) {
    private static ContainerConfig fromSystem() {
      boolean enabled =
          readBoolean("matrix.it.container.enabled", "MATRIX_IT_CONTAINER_ENABLED", false);
      String image =
          readString(
              "matrix.it.container.image", "MATRIX_IT_CONTAINER_IMAGE", "matrixdotorg/synapse:latest");
      String serverName =
          readString(
              "matrix.it.container.server-name", "MATRIX_IT_CONTAINER_SERVER_NAME", "localhost");
      int httpPort = readInt("matrix.it.container.http-port", "MATRIX_IT_CONTAINER_HTTP_PORT", 8008);
      int startupTimeoutSeconds =
          readInt(
              "matrix.it.container.startup-timeout-seconds",
              "MATRIX_IT_CONTAINER_STARTUP_TIMEOUT_SECONDS",
              180);
      String registrationSecret =
          readString(
              "matrix.it.container.registration-secret",
              "MATRIX_IT_CONTAINER_REGISTRATION_SECRET",
              "matrix-it-registration-secret");
      return new ContainerConfig(
          enabled, image, serverName, httpPort, startupTimeoutSeconds, registrationSecret);
    }

    private static boolean readBoolean(String property, String env, boolean fallback) {
      String raw = readOptional(property, env);
      if (raw == null) return fallback;
      String token = normalize(raw).toLowerCase(Locale.ROOT);
      return switch (token) {
        case "1", "true", "yes", "y", "on" -> true;
        case "0", "false", "no", "n", "off" -> false;
        default -> fallback;
      };
    }

    private static int readInt(String property, String env, int fallback) {
      String raw = readOptional(property, env);
      if (raw == null) return fallback;
      try {
        return Integer.parseInt(normalize(raw));
      } catch (NumberFormatException ignored) {
        return fallback;
      }
    }

    private static String readString(String property, String env, String fallback) {
      String raw = readOptional(property, env);
      String value = normalize(raw);
      return value.isEmpty() ? fallback : value;
    }

    private static String readOptional(String property, String env) {
      String prop = System.getProperty(property);
      if (prop != null && !prop.isBlank()) {
        return prop;
      }
      String envValue = System.getenv(env);
      if (envValue != null && !envValue.isBlank()) {
        return envValue;
      }
      return null;
    }
  }
}
