package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.sun.security.auth.module.UnixSystem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
  private static final String CURRENT_UID = detectCurrentUid();
  private static final String CURRENT_GID = detectCurrentGid();

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
      Path homeserverYaml = generateSynapseConfig(image, cfg, dataDir);
      configureRegistrationSharedSecret(homeserverYaml, cfg.registrationSecret());

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
        MatrixMediaUploadClient mediaUploadClient = new MatrixMediaUploadClient(proxyResolver);
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

        Path uploadFile = dataDir.resolve("matrix-it-upload.bin");
        Files.writeString(uploadFile, "matrix upload payload", StandardCharsets.UTF_8);

        MatrixMediaUploadClient.UploadResult uploadResult =
            mediaUploadClient.uploadFile(
                SERVER_ID, server, aliceLogin.accessToken(), uploadFile.toString());
        assertTrue(uploadResult.success(), "media upload should succeed: " + uploadResult.detail());
        String mediaUrl = normalize(uploadResult.contentUri());
        assertTrue(mediaUrl.startsWith("mxc://"), "upload should return an mxc:// content URI");

        MatrixRoomMessageSender.SendResult mediaSendResult =
            sender.sendRoomMediaMessage(
                SERVER_ID,
                server,
                aliceLogin.accessToken(),
                roomId,
                "txn-matrix-it-media-1",
                "matrix-it-upload.bin",
                "m.file",
                mediaUrl);
        assertTrue(
            mediaSendResult.accepted(),
            "room media send should succeed: "
                + mediaSendResult.endpoint()
                + " "
                + mediaSendResult.detail());

        SyncObservation mediaObservation =
            awaitSyncedRoomMediaMessage(
                syncClient,
                server,
                bobLogin.accessToken(),
                roomId,
                "m.file",
                mediaUrl,
                observation.nextBatch());
        MatrixRoomHistoryClient.HistoryResult mediaHistory =
            historyClient.fetchMessagesBefore(
                SERVER_ID,
                server,
                bobLogin.accessToken(),
                roomId,
                mediaObservation.nextBatch(),
                50);
        assertTrue(mediaHistory.success(), "media history fetch should succeed: " + mediaHistory.detail());
        assertTrue(
            mediaHistory.events().stream()
                .anyMatch(
                    event ->
                        "m.file".equals(normalize(event.msgType()))
                            && mediaUrl.equals(normalize(event.mediaUrl()))),
            "history should contain uploaded media event");
      }
    } finally {
      deleteRecursively(dataDir);
    }
  }

  @Test
  void containerizedSynapseSupportsPublicListAndModeAdminFlows() throws Exception {
    ContainerConfig cfg = ContainerConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Matrix Synapse container integration test disabled."
            + " Set -Dmatrix.it.container.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName image = DockerImageName.parse(cfg.image());
    Path dataDir = Files.createTempDirectory("matrix-synapse-it-admin-");
    try {
      Path homeserverYaml = generateSynapseConfig(image, cfg, dataDir);
      configureRegistrationSharedSecret(homeserverYaml, cfg.registrationSecret());

      try (GenericContainer<?> synapse = newSynapseContainer(image, cfg, dataDir)) {
        synapse.start();

        IrcProperties.Server server =
            serverConfig(SERVER_ID, synapse.getHost(), synapse.getMappedPort(cfg.httpPort()), false);
        ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
        when(proxyResolver.planForServer(SERVER_ID)).thenReturn(directPlan());

        registerUser(synapse, cfg, ALICE, ALICE_PASSWORD);
        registerUser(synapse, cfg, BOB, BOB_PASSWORD);
        String charlie = "charlie";
        String charliePassword = "charlie-password";
        registerUser(synapse, cfg, charlie, charliePassword);

        LoginResult aliceLogin = login(server, ALICE, ALICE_PASSWORD);
        LoginResult bobLogin = login(server, BOB, BOB_PASSWORD);
        LoginResult charlieLogin = login(server, charlie, charliePassword);

        MatrixRoomDirectoryClient roomDirectoryClient = new MatrixRoomDirectoryClient(proxyResolver);
        MatrixRoomStateClient roomStateClient = new MatrixRoomStateClient(proxyResolver);
        MatrixRoomMembershipClient roomMembershipClient =
            new MatrixRoomMembershipClient(proxyResolver);
        MatrixRoomRosterClient roomRosterClient = new MatrixRoomRosterClient(proxyResolver);

        String aliasLocalPart = "matrixit" + System.currentTimeMillis();
        String roomId = createPublicRoom(server, aliceLogin.accessToken(), aliasLocalPart);
        boolean roomPublished = tryPublishRoomToDirectory(server, aliceLogin.accessToken(), roomId);
        joinRoom(server, bobLogin.accessToken(), roomId);

        if (roomPublished) {
          awaitPublicRoomListed(
              roomDirectoryClient, server, aliceLogin.accessToken(), roomId, aliasLocalPart);
        } else {
          MatrixRoomDirectoryClient.PublicRoomsResult listResult =
              roomDirectoryClient.fetchPublicRooms(
                  SERVER_ID, server, aliceLogin.accessToken(), aliasLocalPart, "", 100);
          assertTrue(
              listResult.success(),
              "public room directory lookup should succeed even when publication is forbidden: "
                  + listResult.detail());
        }

        MatrixRoomStateClient.PowerLevelsResult baselineState =
            roomStateClient.fetchRoomPowerLevels(SERVER_ID, server, aliceLogin.accessToken(), roomId);
        assertTrue(
            baselineState.success(),
            "power-level state fetch should succeed: " + baselineState.detail());
        long usersDefault = usersDefaultLevel(baselineState.content());
        Map<String, Object> nextState =
            withUserPowerLevel(baselineState.content(), bobLogin.userId(), 50L);

        MatrixRoomStateClient.UpdateResult powerLevelUpdate =
            roomStateClient.updateRoomPowerLevels(
                SERVER_ID, server, aliceLogin.accessToken(), roomId, nextState);
        assertTrue(
            powerLevelUpdate.updated(),
            "power-level state update should succeed: " + powerLevelUpdate.detail());

        MatrixRoomStateClient.PowerLevelsResult updatedState =
            roomStateClient.fetchRoomPowerLevels(SERVER_ID, server, aliceLogin.accessToken(), roomId);
        assertTrue(
            updatedState.success(),
            "updated power-level state fetch should succeed: " + updatedState.detail());
        assertEquals(
            50L,
            userPowerLevel(updatedState.content(), bobLogin.userId(), usersDefault),
            "bob should be promoted to +o-equivalent power level");

        String topic = "matrix-it-admin-topic-" + System.currentTimeMillis();
        MatrixRoomStateClient.UpdateResult topicUpdate =
            roomStateClient.updateRoomTopic(
                SERVER_ID, server, aliceLogin.accessToken(), roomId, topic);
        assertTrue(topicUpdate.updated(), "topic update should succeed: " + topicUpdate.detail());
        MatrixRoomStateClient.TopicResult topicState =
            roomStateClient.fetchRoomTopic(SERVER_ID, server, aliceLogin.accessToken(), roomId);
        assertTrue(topicState.success(), "topic fetch should succeed: " + topicState.detail());
        assertEquals(topic, topicState.topic(), "room topic should match updated text");

        MatrixRoomMembershipClient.ActionResult invite =
            roomMembershipClient.inviteUser(
                SERVER_ID, server, aliceLogin.accessToken(), roomId, charlieLogin.userId());
        assertTrue(invite.success(), "invite request should succeed: " + invite.detail());
        joinRoom(server, charlieLogin.accessToken(), roomId);

        MatrixRoomRosterClient.RosterResult rosterAfterInvite =
            roomRosterClient.fetchJoinedMembers(
                SERVER_ID, server, aliceLogin.accessToken(), roomId);
        assertTrue(
            rosterAfterInvite.success(),
            "joined members fetch should succeed: " + rosterAfterInvite.detail());
        assertTrue(
            rosterHasUser(rosterAfterInvite.members(), charlieLogin.userId()),
            "invited user should appear in joined roster");

        MatrixRoomMembershipClient.ActionResult kick =
            roomMembershipClient.kickUser(
                SERVER_ID,
                server,
                aliceLogin.accessToken(),
                roomId,
                charlieLogin.userId(),
                "matrix-it-kick");
        assertTrue(kick.success(), "kick request should succeed: " + kick.detail());

        MatrixRoomRosterClient.RosterResult rosterAfterKick =
            roomRosterClient.fetchJoinedMembers(
                SERVER_ID, server, aliceLogin.accessToken(), roomId);
        assertTrue(
            rosterAfterKick.success(),
            "joined members fetch should succeed after kick: " + rosterAfterKick.detail());
        assertFalse(
            rosterHasUser(rosterAfterKick.members(), charlieLogin.userId()),
            "kicked user should not remain in joined roster");

        MatrixRoomMembershipClient.ActionResult ban =
            roomMembershipClient.banUser(
                SERVER_ID,
                server,
                aliceLogin.accessToken(),
                roomId,
                bobLogin.userId(),
                "matrix-it-ban");
        assertTrue(ban.success(), "ban request should succeed: " + ban.detail());

        MatrixRoomMembershipClient.ActionResult unban =
            roomMembershipClient.unbanUser(
                SERVER_ID, server, aliceLogin.accessToken(), roomId, bobLogin.userId());
        assertTrue(unban.success(), "unban request should succeed: " + unban.detail());
      }
    } finally {
      deleteRecursively(dataDir);
    }
  }

  @Test
  void containerizedSynapseMatrixServiceReportsMonitorUnavailableWhenConnected() throws Exception {
    ContainerConfig cfg = ContainerConfig.fromSystem();
    Assumptions.assumeTrue(
        cfg.enabled(),
        "Matrix Synapse container integration test disabled."
            + " Set -Dmatrix.it.container.enabled=true.");
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is not available on this machine.");

    DockerImageName image = DockerImageName.parse(cfg.image());
    Path dataDir = Files.createTempDirectory("matrix-synapse-it-monitor-");
    try {
      Path homeserverYaml = generateSynapseConfig(image, cfg, dataDir);
      configureRegistrationSharedSecret(homeserverYaml, cfg.registrationSecret());

      try (GenericContainer<?> synapse = newSynapseContainer(image, cfg, dataDir)) {
        synapse.start();

        IrcProperties.Server probeServer =
            serverConfig(SERVER_ID, synapse.getHost(), synapse.getMappedPort(cfg.httpPort()), false);
        ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
        when(proxyResolver.planForServer(SERVER_ID)).thenReturn(directPlan());

        registerUser(synapse, cfg, ALICE, ALICE_PASSWORD);
        LoginResult aliceLogin = login(probeServer, ALICE, ALICE_PASSWORD);

        IrcProperties.Server serviceServer =
            serverConfigWithAccessToken(
                SERVER_ID,
                synapse.getHost(),
                synapse.getMappedPort(cfg.httpPort()),
                false,
                aliceLogin.accessToken());
        ServerCatalog serverCatalog = mock(ServerCatalog.class);
        when(serverCatalog.require(SERVER_ID)).thenReturn(serviceServer);
        when(serverCatalog.find(SERVER_ID)).thenReturn(Optional.of(serviceServer));

        MatrixIrcClientService service = buildMatrixService(serverCatalog, proxyResolver);
        try {
          service.connect(SERVER_ID).blockingAwait();
          assertTrue(service.isMultilineAvailable(SERVER_ID));
          assertFalse(service.isMonitorAvailable(SERVER_ID));
          assertEquals(0, service.negotiatedMonitorLimit(SERVER_ID));
        } finally {
          service.shutdownNow();
        }
      }
    } finally {
      deleteRecursively(dataDir);
    }
  }

  private static GenericContainer<?> newSynapseContainer(
      DockerImageName image, ContainerConfig cfg, Path dataDir) {
    GenericContainer<?> container =
        new GenericContainer<>(image)
            .withFileSystemBind(dataDir.toAbsolutePath().toString(), "/data")
            .withEnv("SYNAPSE_SERVER_NAME", cfg.serverName())
            .withEnv("SYNAPSE_REPORT_STATS", "no")
            .withExposedPorts(cfg.httpPort())
            .waitingFor(
                Wait.forHttp("/_matrix/client/versions").forPort(cfg.httpPort()).forStatusCode(200))
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()));
    applyUidGidEnv(container);
    return container;
  }

  private static Path generateSynapseConfig(DockerImageName image, ContainerConfig cfg, Path dataDir)
      throws IOException {
    GenericContainer<?> generator =
        new GenericContainer<>(image)
            .withFileSystemBind(dataDir.toAbsolutePath().toString(), "/data")
            .withEnv("SYNAPSE_SERVER_NAME", cfg.serverName())
            .withEnv("SYNAPSE_REPORT_STATS", "no")
            .withCommand("generate")
            .withStartupCheckStrategy(new OneShotStartupCheckStrategy())
            .withStartupTimeout(Duration.ofSeconds(cfg.startupTimeoutSeconds()));
    applyUidGidEnv(generator);
    try (generator) {
      generator.start();
    }

    Path homeserverYaml = dataDir.resolve("homeserver.yaml");
    if (Files.exists(homeserverYaml)) {
      return homeserverYaml;
    }

    Path discovered = discoverGeneratedHomeserverYaml(dataDir);
    assertTrue(discovered != null, "expected generated Synapse config under " + dataDir);
    if (!discovered.equals(homeserverYaml)) {
      Files.copy(discovered, homeserverYaml, StandardCopyOption.REPLACE_EXISTING);
    }
    return homeserverYaml;
  }

  private static Path discoverGeneratedHomeserverYaml(Path dataDir) {
    if (dataDir == null || !Files.exists(dataDir)) {
      return null;
    }
    try (var walk = Files.walk(dataDir)) {
      return walk
          .filter(Files::isRegularFile)
          .filter(path -> normalize(path.getFileName().toString()).endsWith("homeserver.yaml"))
          .findFirst()
          .orElse(null);
    } catch (IOException ignored) {
      return null;
    }
  }

  private static void applyUidGidEnv(GenericContainer<?> container) {
    if (container == null) {
      return;
    }
    if (!CURRENT_UID.isEmpty()) {
      container.withEnv("UID", CURRENT_UID);
    }
    if (!CURRENT_GID.isEmpty()) {
      container.withEnv("GID", CURRENT_GID);
    }
  }

  private static String detectCurrentUid() {
    String envUid = normalize(System.getenv("UID"));
    if (!envUid.isEmpty()) {
      return envUid;
    }
    try {
      return Long.toString(new UnixSystem().getUid());
    } catch (Throwable ignored) {
      return "";
    }
  }

  private static String detectCurrentGid() {
    String envGid = normalize(System.getenv("GID"));
    if (!envGid.isEmpty()) {
      return envGid;
    }
    try {
      return Long.toString(new UnixSystem().getGid());
    } catch (Throwable ignored) {
      return "";
    }
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

  private static String createPublicRoom(
      IrcProperties.Server server, String creatorAccessToken, String aliasLocalPart) throws Exception {
    ObjectNode body = JSON.createObjectNode();
    body.put("preset", "public_chat");
    body.put("visibility", "public");
    body.put("room_alias_name", normalize(aliasLocalPart));
    body.put("name", "Matrix IT Public Room");
    body.put("topic", "Matrix IT public room for directory/mode coverage");

    JsonNode root = postJson(MatrixEndpointResolver.createRoomUri(server), body, creatorAccessToken);
    String roomId = normalize(root.path("room_id").asText(""));
    assertTrue(roomId.startsWith("!"), "createRoom should return a valid room id");
    return roomId;
  }

  private static boolean tryPublishRoomToDirectory(
      IrcProperties.Server server, String accessToken, String roomId) throws Exception {
    String rid = normalize(roomId);
    String encodedRoomId = URLEncoder.encode(rid, StandardCharsets.UTF_8);
    URI endpoint = appendClientPath(server, "directory/list/room/" + encodedRoomId);
    ObjectNode payload = JSON.createObjectNode();
    payload.put("visibility", "public");
    JsonNode safePayload = payload;
    String body = JSON.writeValueAsString(safePayload);

    HttpRequest.Builder request =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body));
    String token = normalize(accessToken);
    if (!token.isEmpty()) {
      request.header("Authorization", "Bearer " + token);
    }

    HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    int code = response.statusCode();
    String responseBody = Objects.toString(response.body(), "");
    if (code >= 200 && code < 300) {
      return true;
    }
    if (code == 403) {
      return false;
    }
    fail("HTTP " + code + " from " + endpoint + " body=" + responseBody);
    return false;
  }

  private static String aliasLocalPartFromAlias(String alias) {
    String value = normalize(alias);
    if (!value.startsWith("#")) {
      return "";
    }
    int colon = value.indexOf(':');
    if (colon <= 1) {
      return "";
    }
    return normalize(value.substring(1, colon));
  }

  private static void awaitPublicRoomListed(
      MatrixRoomDirectoryClient roomDirectoryClient,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String aliasLocalPart)
      throws Exception {
    long deadline = System.nanoTime() + MESSAGE_TIMEOUT.toNanos();
    String expectedRoomId = normalize(roomId);
    String expectedAliasPart = normalize(aliasLocalPart);

    while (System.nanoTime() < deadline) {
      String since = "";
      for (int page = 0; page < 20; page++) {
        MatrixRoomDirectoryClient.PublicRoomsResult result =
            roomDirectoryClient.fetchPublicRooms(SERVER_ID, server, accessToken, "", since, 100);
        assertTrue(
            result.success(),
            "public room directory lookup should succeed: " + result.detail());
        if (publicRoomsContain(result.rooms(), expectedRoomId, expectedAliasPart)) {
          return;
        }

        since = normalize(result.nextBatch());
        if (since.isEmpty()) {
          break;
        }
      }
      Thread.sleep(250L);
    }

    fail(
        "public room directory did not include expected room within timeout."
            + " roomId="
            + expectedRoomId
            + " aliasLocalPart="
            + expectedAliasPart);
  }

  private static boolean publicRoomsContain(
      List<MatrixRoomDirectoryClient.PublicRoom> rooms, String roomId, String aliasLocalPart) {
    if (rooms == null || rooms.isEmpty()) {
      return false;
    }
    String expectedRoomId = normalize(roomId);
    String expectedAliasPart = normalize(aliasLocalPart);
    for (MatrixRoomDirectoryClient.PublicRoom room : rooms) {
      if (room == null) continue;
      if (!expectedRoomId.isEmpty() && expectedRoomId.equals(normalize(room.roomId()))) {
        return true;
      }
      if (!expectedAliasPart.isEmpty()
          && expectedAliasPart.equals(aliasLocalPartFromAlias(room.canonicalAlias()))) {
        return true;
      }
    }
    return false;
  }

  private static boolean rosterHasUser(
      List<MatrixRoomRosterClient.JoinedMember> members, String userId) {
    if (members == null || members.isEmpty()) {
      return false;
    }
    String expected = normalize(userId);
    for (MatrixRoomRosterClient.JoinedMember member : members) {
      if (member == null) continue;
      if (expected.equals(normalize(member.userId()))) {
        return true;
      }
    }
    return false;
  }

  private static long usersDefaultLevel(Map<String, Object> state) {
    if (state == null || state.isEmpty()) {
      return 0L;
    }
    return toLongValue(state.get("users_default"), 0L);
  }

  private static Map<String, Object> withUserPowerLevel(
      Map<String, Object> state, String userId, long level) {
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
    if (state != null && !state.isEmpty()) {
      for (Map.Entry<String, Object> entry : state.entrySet()) {
        if (entry == null) continue;
        String key = normalize(Objects.toString(entry.getKey(), ""));
        if (key.isEmpty()) continue;
        copy.put(key, entry.getValue());
      }
    }

    LinkedHashMap<String, Object> users = new LinkedHashMap<>();
    Object existingUsers = copy.get("users");
    if (existingUsers instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry == null) continue;
        String key = normalize(Objects.toString(entry.getKey(), ""));
        if (key.isEmpty()) continue;
        users.put(key, entry.getValue());
      }
    }
    users.put(normalize(userId), Long.valueOf(level));
    copy.put("users", users);
    if (!copy.containsKey("users_default")) {
      copy.put("users_default", Long.valueOf(0L));
    }
    return copy;
  }

  private static long userPowerLevel(Map<String, Object> state, String userId, long fallback) {
    if (state == null || state.isEmpty()) {
      return fallback;
    }
    Object usersObj = state.get("users");
    if (!(usersObj instanceof Map<?, ?> users)) {
      return fallback;
    }
    String uid = normalize(userId);
    if (uid.isEmpty()) {
      return fallback;
    }
    return toLongValue(users.get(uid), fallback);
  }

  private static long toLongValue(Object raw, long fallback) {
    if (raw instanceof Number n) {
      return n.longValue();
    }
    String value = normalize(Objects.toString(raw, ""));
    if (value.isEmpty()) {
      return fallback;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
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

  private static SyncObservation awaitSyncedRoomMediaMessage(
      MatrixSyncClient syncClient,
      IrcProperties.Server server,
      String accessToken,
      String roomId,
      String expectedMsgType,
      String expectedMediaUrl,
      String sinceToken)
      throws Exception {
    long deadline = System.nanoTime() + MESSAGE_TIMEOUT.toNanos();
    String since = normalize(sinceToken);
    String msgType = normalize(expectedMsgType);
    String mediaUrl = normalize(expectedMediaUrl);

    while (System.nanoTime() < deadline) {
      MatrixSyncClient.SyncResult result = syncClient.sync(SERVER_ID, server, accessToken, since, 1_000);
      assertTrue(result.success(), "sync request failed: " + result.detail());
      String nextBatch = normalize(result.nextBatch());

      for (MatrixSyncClient.RoomTimelineEvent event : result.events()) {
        if (event == null) continue;
        if (!roomId.equals(normalize(event.roomId()))) continue;
        if (!msgType.equals(normalize(event.msgType()))) continue;
        if (!mediaUrl.equals(normalize(event.mediaUrl()))) continue;
        return new SyncObservation(event, nextBatch);
      }

      if (!nextBatch.isEmpty()) {
        since = nextBatch;
      }
      Thread.sleep(250L);
    }
    throw new AssertionError("did not observe uploaded media message via sync within timeout");
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

  private static IrcProperties.Server serverConfigWithAccessToken(
      String id, String host, int port, boolean tls, String accessToken) {
    return new IrcProperties.Server(
        id,
        host,
        port,
        tls,
        normalize(accessToken),
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

  private static MatrixIrcClientService buildMatrixService(
      ServerCatalog serverCatalog, ServerProxyResolver proxyResolver) {
    return new MatrixIrcClientService(
        serverCatalog,
        new MatrixHomeserverProbe(proxyResolver),
        new MatrixDisplayNameClient(proxyResolver),
        new MatrixUserProfileClient(proxyResolver),
        new MatrixPresenceClient(proxyResolver),
        new MatrixReadMarkerClient(proxyResolver),
        new MatrixRoomMembershipClient(proxyResolver),
        new MatrixRoomStateClient(proxyResolver),
        new MatrixRoomDirectoryClient(proxyResolver),
        new MatrixRoomRosterClient(proxyResolver),
        new MatrixRoomHistoryClient(proxyResolver),
        new MatrixRoomTypingClient(proxyResolver),
        new MatrixDirectRoomResolver(proxyResolver),
        new MatrixMediaUploadClient(proxyResolver),
        new MatrixRoomMessageSender(proxyResolver),
        new MatrixSyncClient(proxyResolver));
  }

  private static void deleteRecursively(Path root) {
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
    } catch (IOException ignored) {
      // Best-effort cleanup. Container-created files may be unreadable by the test user.
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
