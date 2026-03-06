package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.BackendNotAvailableException;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcEvent;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MatrixIrcClientServiceTest {

  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final MatrixHomeserverProbe homeserverProbe = mock(MatrixHomeserverProbe.class);
  private final MatrixDisplayNameClient displayNameClient = mock(MatrixDisplayNameClient.class);
  private final MatrixUserProfileClient userProfileClient = mock(MatrixUserProfileClient.class);
  private final MatrixPresenceClient presenceClient = mock(MatrixPresenceClient.class);
  private final MatrixReadMarkerClient readMarkerClient = mock(MatrixReadMarkerClient.class);
  private final MatrixRoomMembershipClient roomMembershipClient =
      mock(MatrixRoomMembershipClient.class);
  private final MatrixRoomStateClient roomStateClient = mock(MatrixRoomStateClient.class);
  private final MatrixRoomDirectoryClient roomDirectoryClient =
      mock(MatrixRoomDirectoryClient.class);
  private final MatrixRoomRosterClient roomRosterClient = mock(MatrixRoomRosterClient.class);
  private final MatrixRoomHistoryClient roomHistoryClient = mock(MatrixRoomHistoryClient.class);
  private final MatrixRoomTypingClient roomTypingClient = mock(MatrixRoomTypingClient.class);
  private final MatrixDirectRoomResolver directRoomResolver = mock(MatrixDirectRoomResolver.class);
  private final MatrixMediaUploadClient mediaUploadClient = mock(MatrixMediaUploadClient.class);
  private final MatrixRoomMessageSender roomMessageSender = mock(MatrixRoomMessageSender.class);
  private final MatrixSyncClient syncClient = mock(MatrixSyncClient.class);
  private final MatrixIrcClientService service =
      new MatrixIrcClientService(
          serverCatalog,
          homeserverProbe,
          displayNameClient,
          userProfileClient,
          presenceClient,
          readMarkerClient,
          roomMembershipClient,
          roomStateClient,
          roomDirectoryClient,
          roomRosterClient,
          roomHistoryClient,
          roomTypingClient,
          directRoomResolver,
          mediaUploadClient,
          roomMessageSender,
          syncClient);

  @AfterEach
  void tearDown() {
    service.shutdownNow();
  }

  @Test
  void backendIsMatrix() {
    assertEquals(IrcProperties.Server.Backend.MATRIX, service.backend());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
    assertEquals(Optional.empty(), service.currentNick("matrix"));
    assertFalse(service.isChatHistoryAvailable("matrix"));
    assertFalse(service.isEchoMessageAvailable("matrix"));
    assertFalse(service.isTypingAvailable("matrix"));
    assertEquals("not connected", service.typingAvailabilityReason("matrix"));
    assertFalse(service.isReadMarkerAvailable("matrix"));
    assertFalse(service.isDraftReplyAvailable("matrix"));
    assertFalse(service.isDraftReactAvailable("matrix"));
    assertFalse(service.isDraftUnreactAvailable("matrix"));
    assertFalse(service.isMessageEditAvailable("matrix"));
    assertFalse(service.isMessageRedactionAvailable("matrix"));
    assertFalse(service.isMultilineAvailable("matrix"));
    assertEquals(0, service.negotiatedMultilineMaxLines("matrix"));
    assertEquals(0L, service.negotiatedMultilineMaxBytes("matrix"));
    assertFalse(service.isLabeledResponseAvailable("matrix"));
    assertFalse(service.isStandardRepliesAvailable("matrix"));
    assertFalse(service.isMonitorAvailable("matrix"));
    assertEquals(0, service.negotiatedMonitorLimit("matrix"));
  }

  @Test
  void connectAuthenticatesAndEmitsLifecycleEvents() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 2));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));

    var events = service.events().test();
    assertDoesNotThrow(() -> service.connect("matrix").blockingAwait());
    events.awaitCount(3);

    assertEquals(3, events.values().size());
    assertInstanceOf(IrcEvent.Connecting.class, events.values().get(0).event());
    IrcEvent.Connected connected =
        assertInstanceOf(IrcEvent.Connected.class, events.values().get(1).event());
    assertInstanceOf(IrcEvent.ConnectionReady.class, events.values().get(2).event());

    assertEquals("matrix.example.org", connected.serverHost());
    assertEquals(8448, connected.serverPort());
    assertEquals("@alice:matrix.example.org", connected.nick());
    assertEquals(Optional.of("@alice:matrix.example.org"), service.currentNick("matrix"));
    assertEquals("", service.backendAvailabilityReason("matrix"));
    assertTrue(service.isChatHistoryAvailable("matrix"));
    assertTrue(service.isEchoMessageAvailable("matrix"));
    assertTrue(service.isTypingAvailable("matrix"));
    assertEquals("", service.typingAvailabilityReason("matrix"));
    assertTrue(service.isReadMarkerAvailable("matrix"));
    assertTrue(service.isDraftReplyAvailable("matrix"));
    assertTrue(service.isDraftReactAvailable("matrix"));
    assertTrue(service.isDraftUnreactAvailable("matrix"));
    assertTrue(service.isMessageEditAvailable("matrix"));
    assertTrue(service.isMessageRedactionAvailable("matrix"));
    assertTrue(service.isMultilineAvailable("matrix"));
    assertEquals(0, service.negotiatedMultilineMaxLines("matrix"));
    assertEquals(0L, service.negotiatedMultilineMaxBytes("matrix"));
    assertFalse(service.isLabeledResponseAvailable("matrix"));
    assertFalse(service.isStandardRepliesAvailable("matrix"));
    assertFalse(service.isMonitorAvailable("matrix"));
    assertEquals(0, service.negotiatedMonitorLimit("matrix"));
  }

  @Test
  void connectWithInvalidMatrixHostReportsConfigurationError() {
    IrcProperties.Server server = server("matrix", "  ", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenThrow(new IllegalArgumentException("host is blank"));

    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class, () -> service.connect("matrix").blockingAwait());

    assertEquals(IrcProperties.Server.Backend.MATRIX, err.backend());
    assertEquals("connect", err.operation());
    assertEquals("matrix", err.serverId());
    assertTrue(service.backendAvailabilityReason("matrix").startsWith("invalid Matrix homeserver"));
    assertTrue(err.getMessage().contains("invalid Matrix homeserver configuration"));
  }

  @Test
  void connectWithProbeFailureReportsAvailabilityDetail() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.failed(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"),
                "HTTP 503 from versions endpoint"));

    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class, () -> service.connect("matrix").blockingAwait());

    assertEquals("connect", err.operation());
    assertTrue(
        service
            .backendAvailabilityReason("matrix")
            .contains("homeserver probe failed at https://matrix.example.org:8448"));
    assertTrue(err.getMessage().contains("HTTP 503 from versions endpoint"));
  }

  @Test
  void connectWithWhoamiFailureReportsAuthenticationDetail() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.failed(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "HTTP 401 from whoami endpoint"));

    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class, () -> service.connect("matrix").blockingAwait());

    assertEquals("connect", err.operation());
    assertTrue(
        service
            .backendAvailabilityReason("matrix")
            .contains("authentication failed at https://matrix.example.org:8448"));
    assertTrue(err.getMessage().contains("HTTP 401 from whoami endpoint"));
  }

  @Test
  void connectFailsWhenAccessTokenIsMissing() {
    IrcProperties.Server server = server("matrix", "matrix.example.org", 8448, true, "");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));

    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class, () -> service.connect("matrix").blockingAwait());

    assertEquals("connect", err.operation());
    assertEquals("matrix", err.serverId());
    assertTrue(service.backendAvailabilityReason("matrix").contains("access token is blank"));
    assertTrue(err.getMessage().contains("set server password"));
  }

  @Test
  void connectUsesSaslPasswordAsTokenFallback() {
    IrcProperties.Server server =
        serverWithSaslToken("matrix", "matrix.example.org", 8448, true, "sasl-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "sasl-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                ""));

    assertDoesNotThrow(() -> service.connect("matrix").blockingAwait());
    assertEquals(Optional.of("@alice:matrix.example.org"), service.currentNick("matrix"));
    assertEquals("", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void changeNickUpdatesMatrixDisplayName() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(displayNameClient.setDisplayName(
            "matrix", server, "secret-token", "@alice:matrix.example.org", "Alice"))
        .thenReturn(
            MatrixDisplayNameClient.UpdateResult.updated(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/profile/@alice:matrix.example.org/displayname"),
                "Alice"));
    service.connect("matrix").blockingAwait();

    assertDoesNotThrow(() -> service.changeNick("matrix", "Alice").blockingAwait());
    verify(displayNameClient, times(1))
        .setDisplayName("matrix", server, "secret-token", "@alice:matrix.example.org", "Alice");
  }

  @Test
  void changeNickReportsBackendUnavailableWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () -> service.changeNick("matrix", "Alice").blockingAwait());

    assertEquals(IrcProperties.Server.Backend.MATRIX, err.backend());
    assertEquals("change-nick", err.operation());
    assertEquals("matrix", err.serverId());
    assertTrue(err.getMessage().contains("not connected"));
  }

  @Test
  void changeNickRejectsCrlfNick() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.changeNick("matrix", "Alice\nBob").blockingAwait());

    assertEquals("new nick contains CR/LF", err.getMessage());
  }

  @Test
  void changeNickReportsDisplayNameUpdateFailure() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(displayNameClient.setDisplayName(
            "matrix", server, "secret-token", "@alice:matrix.example.org", "Alice"))
        .thenReturn(
            MatrixDisplayNameClient.UpdateResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/profile/@alice:matrix.example.org/displayname"),
                "HTTP 403 from displayname endpoint"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () -> service.changeNick("matrix", "Alice").blockingAwait());

    assertTrue(err.getMessage().contains("HTTP 403 from displayname endpoint"));
  }

  @Test
  void requestNamesEmitsNickListUpdatedForRoom() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomRosterClient.fetchJoinedMembers(
            "matrix", server, "secret-token", "!room:matrix.example.org"))
        .thenReturn(
            MatrixRoomRosterClient.RosterResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/joined_members"),
                List.of(
                    new MatrixRoomRosterClient.JoinedMember("@zoe:matrix.example.org", "Zoe"),
                    new MatrixRoomRosterClient.JoinedMember(
                        "@alice:matrix.example.org", "Alice"))));

    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);
    assertDoesNotThrow(
        () -> service.requestNames("matrix", "!room:matrix.example.org").blockingAwait());
    events.awaitCount(4);

    IrcEvent.NickListUpdated names =
        assertInstanceOf(IrcEvent.NickListUpdated.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", names.channel());
    assertEquals(2, names.totalUsers());
    assertEquals(0, names.operatorCount());
    assertEquals(
        List.of("@alice:matrix.example.org", "@zoe:matrix.example.org"),
        names.nicks().stream().map(IrcEvent.NickInfo::nick).toList());
    assertEquals("Alice", names.nicks().get(0).realName());
  }

  @Test
  void requestNamesUsesAliasRoomLookupForShortAliasTarget() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomDirectoryClient.resolveRoomAlias(
            "matrix", server, "secret-token", "#ircafe:matrix.example.org"))
        .thenReturn(
            MatrixRoomDirectoryClient.ResolveResult.resolved(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/directory/room/%23ircafe:matrix.example.org"),
                "!room:matrix.example.org"));
    when(roomRosterClient.fetchJoinedMembers(
            "matrix", server, "secret-token", "!room:matrix.example.org"))
        .thenReturn(
            MatrixRoomRosterClient.RosterResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/joined_members"),
                List.of()));
    service.connect("matrix").blockingAwait();

    assertDoesNotThrow(() -> service.requestNames("matrix", "#ircafe").blockingAwait());
    verify(roomDirectoryClient, times(1))
        .resolveRoomAlias("matrix", server, "secret-token", "#ircafe:matrix.example.org");
    verify(roomRosterClient, times(1))
        .fetchJoinedMembers("matrix", server, "secret-token", "!room:matrix.example.org");
  }

  @Test
  void requestNamesReportsBackendUnavailableWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () -> service.requestNames("matrix", "!room:matrix.example.org").blockingAwait());

    assertEquals(IrcProperties.Server.Backend.MATRIX, err.backend());
    assertEquals("names", err.operation());
    assertEquals("matrix", err.serverId());
    assertTrue(err.getMessage().contains("not connected"));
  }

  @Test
  void requestNamesReportsMatrixRosterFailure() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomRosterClient.fetchJoinedMembers(
            "matrix", server, "secret-token", "!room:matrix.example.org"))
        .thenReturn(
            MatrixRoomRosterClient.RosterResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/joined_members"),
                "HTTP 403 from joined members endpoint"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () -> service.requestNames("matrix", "!room:matrix.example.org").blockingAwait());

    assertTrue(err.getMessage().contains("HTTP 403 from joined members endpoint"));
  }

  @Test
  void whoisEmitsWhoisResultAndProbeCompletion() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(userProfileClient.fetchProfile(
            "matrix", server, "secret-token", "@bob:matrix.example.org"))
        .thenReturn(
            MatrixUserProfileClient.ProfileResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/profile/%40bob:matrix.example.org"),
                "@bob:matrix.example.org",
                "Bob",
                "mxc://matrix.example.org/bob-avatar"));

    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);
    assertDoesNotThrow(() -> service.whois("matrix", "@bob:matrix.example.org").blockingAwait());
    events.awaitCount(5);

    IrcEvent.WhoisResult whois =
        assertInstanceOf(IrcEvent.WhoisResult.class, events.values().get(3).event());
    IrcEvent.WhoisProbeCompleted completed =
        assertInstanceOf(IrcEvent.WhoisProbeCompleted.class, events.values().get(4).event());
    assertEquals("@bob:matrix.example.org", whois.nick());
    assertEquals(
        List.of(
            "user id: @bob:matrix.example.org",
            "display name: Bob",
            "avatar: mxc://matrix.example.org/bob-avatar"),
        whois.lines());
    assertEquals("@bob:matrix.example.org", completed.nick());
  }

  @Test
  void whoisRejectsNonMatrixUserId() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class, () -> service.whois("matrix", "bob").blockingAwait());

    assertEquals("nick is not a Matrix user id", err.getMessage());
  }

  @Test
  void whoisReportsBackendUnavailableWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () -> service.whois("matrix", "@bob:matrix.example.org").blockingAwait());

    assertEquals(IrcProperties.Server.Backend.MATRIX, err.backend());
    assertEquals("whois", err.operation());
    assertEquals("matrix", err.serverId());
    assertTrue(err.getMessage().contains("not connected"));
  }

  @Test
  void whoisReportsProfileFetchFailure() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(userProfileClient.fetchProfile(
            "matrix", server, "secret-token", "@bob:matrix.example.org"))
        .thenReturn(
            MatrixUserProfileClient.ProfileResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/profile/%40bob:matrix.example.org"),
                "HTTP 404 from profile endpoint"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () -> service.whois("matrix", "@bob:matrix.example.org").blockingAwait());

    assertTrue(err.getMessage().contains("HTTP 404 from profile endpoint"));
  }

  @Test
  void setAwayEmitsAwayStatusChangedWhenMessageProvided() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(presenceClient.setAwayStatus(
            "matrix", server, "secret-token", "@alice:matrix.example.org", "Stepped out"))
        .thenReturn(
            MatrixPresenceClient.PresenceResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/presence/@alice:matrix.example.org/status"),
                true,
                "Stepped out"));

    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);
    assertDoesNotThrow(() -> service.setAway("matrix", "Stepped out").blockingAwait());
    events.awaitCount(4);

    IrcEvent.AwayStatusChanged away =
        assertInstanceOf(IrcEvent.AwayStatusChanged.class, events.values().get(3).event());
    assertTrue(away.away());
    assertEquals("Stepped out", away.message());
  }

  @Test
  void setAwayClearsAwayWhenMessageIsBlank() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(presenceClient.setAwayStatus(
            "matrix", server, "secret-token", "@alice:matrix.example.org", ""))
        .thenReturn(
            MatrixPresenceClient.PresenceResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/presence/@alice:matrix.example.org/status"),
                false,
                ""));

    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);
    assertDoesNotThrow(() -> service.setAway("matrix", "   ").blockingAwait());
    events.awaitCount(4);

    IrcEvent.AwayStatusChanged away =
        assertInstanceOf(IrcEvent.AwayStatusChanged.class, events.values().get(3).event());
    assertFalse(away.away());
    assertEquals("", away.message());
  }

  @Test
  void setAwayReportsBackendUnavailableWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () -> service.setAway("matrix", "Stepped out").blockingAwait());

    assertEquals(IrcProperties.Server.Backend.MATRIX, err.backend());
    assertEquals("set-away", err.operation());
    assertEquals("matrix", err.serverId());
    assertTrue(err.getMessage().contains("not connected"));
  }

  @Test
  void setAwayReportsPresenceFailure() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(presenceClient.setAwayStatus(
            "matrix", server, "secret-token", "@alice:matrix.example.org", "Stepped out"))
        .thenReturn(
            MatrixPresenceClient.PresenceResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/presence/@alice:matrix.example.org/status"),
                "HTTP 403 from presence endpoint"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () -> service.setAway("matrix", "Stepped out").blockingAwait());

    assertTrue(err.getMessage().contains("HTTP 403 from presence endpoint"));
  }

  @Test
  void joinChannelUsesAliasHostFallbackAndEmitsJoinedChannelEvent() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMembershipClient.joinRoom(
            "matrix", server, "secret-token", "#ircafe:matrix.example.org"))
        .thenReturn(
            MatrixRoomMembershipClient.JoinResult.joined(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/join/%23ircafe%3Amatrix.example.org"),
                "!room:matrix.example.org"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.joinChannel("matrix", "#ircafe").blockingAwait();
    events.awaitCount(4);

    verify(roomMembershipClient)
        .joinRoom("matrix", server, "secret-token", "#ircafe:matrix.example.org");
    IrcEvent.JoinedChannel joined =
        assertInstanceOf(IrcEvent.JoinedChannel.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", joined.channel());
  }

  @Test
  void partChannelByAliasUsesRememberedRoomMappingAndEmitsLeftChannelEvent() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMembershipClient.joinRoom(
            "matrix", server, "secret-token", "#ircafe:matrix.example.org"))
        .thenReturn(
            MatrixRoomMembershipClient.JoinResult.joined(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/join/%23ircafe%3Amatrix.example.org"),
                "!room:matrix.example.org"));
    when(roomMembershipClient.leaveRoom(
            "matrix", server, "secret-token", "!room:matrix.example.org"))
        .thenReturn(
            MatrixRoomMembershipClient.LeaveResult.left(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/leave")));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);
    service.joinChannel("matrix", "#ircafe").blockingAwait();
    events.awaitCount(4);

    service.partChannel("matrix", "#ircafe:matrix.example.org", "bye").blockingAwait();
    events.awaitCount(5);

    verify(roomMembershipClient)
        .leaveRoom("matrix", server, "secret-token", "!room:matrix.example.org");
    IrcEvent.LeftChannel left =
        assertInstanceOf(IrcEvent.LeftChannel.class, events.values().get(4).event());
    assertEquals("!room:matrix.example.org", left.channel());
    assertEquals("bye", left.reason());
  }

  @Test
  void partChannelByAliasUsesDirectoryLookupWhenAliasNotCached() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomDirectoryClient.resolveRoomAlias(
            "matrix", server, "secret-token", "#ircafe:matrix.example.org"))
        .thenReturn(
            MatrixRoomDirectoryClient.ResolveResult.resolved(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/directory/room/%23ircafe:matrix.example.org"),
                "!room:matrix.example.org"));
    when(roomMembershipClient.leaveRoom(
            "matrix", server, "secret-token", "!room:matrix.example.org"))
        .thenReturn(
            MatrixRoomMembershipClient.LeaveResult.left(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/leave")));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.partChannel("matrix", "#ircafe", "bye").blockingAwait();
    events.awaitCount(4);

    verify(roomDirectoryClient)
        .resolveRoomAlias("matrix", server, "secret-token", "#ircafe:matrix.example.org");
    verify(roomMembershipClient)
        .leaveRoom("matrix", server, "secret-token", "!room:matrix.example.org");
    IrcEvent.LeftChannel left =
        assertInstanceOf(IrcEvent.LeftChannel.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", left.channel());
    assertEquals("bye", left.reason());
  }

  @Test
  void partChannelByAliasReportsDirectoryLookupFailure() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomDirectoryClient.resolveRoomAlias(
            "matrix", server, "secret-token", "#ircafe:matrix.example.org"))
        .thenReturn(
            MatrixRoomDirectoryClient.ResolveResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/directory/room/%23ircafe:matrix.example.org"),
                "HTTP 404 from room directory endpoint"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                service.partChannel("matrix", "#ircafe:matrix.example.org", "bye").blockingAwait());

    assertTrue(err.getMessage().contains("HTTP 404 from room directory endpoint"));
  }

  @Test
  void sendToChannelPostsRoomMessageWhenConnected() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("hello matrix")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-1"),
                "$abc"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    assertDoesNotThrow(
        () ->
            service
                .sendToChannel("matrix", "!room:matrix.example.org", "hello matrix")
                .blockingAwait());
    events.awaitCount(4);

    verify(roomMessageSender)
        .sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("hello matrix"));

    IrcEvent.ChannelMessage echoed =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", echoed.channel());
    assertEquals("@alice:matrix.example.org", echoed.from());
    assertEquals("hello matrix", echoed.text());
    assertEquals("$abc", echoed.messageId());
  }

  @Test
  void sendToChannelActionEmitsLocalEchoAction() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("\u0001ACTION waves\u0001")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-2"),
                "$emote"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    assertDoesNotThrow(
        () ->
            service
                .sendToChannel("matrix", "!room:matrix.example.org", "\u0001ACTION waves\u0001")
                .blockingAwait());
    events.awaitCount(4);

    IrcEvent.ChannelAction echoed =
        assertInstanceOf(IrcEvent.ChannelAction.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", echoed.channel());
    assertEquals("@alice:matrix.example.org", echoed.from());
    assertEquals("waves", echoed.action());
    assertEquals("$emote", echoed.messageId());
  }

  @Test
  void sendToChannelResolvesAliasViaDirectoryLookup() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomDirectoryClient.resolveRoomAlias(
            "matrix", server, "secret-token", "#ircafe:matrix.example.org"))
        .thenReturn(
            MatrixRoomDirectoryClient.ResolveResult.resolved(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/directory/room/%23ircafe:matrix.example.org"),
                "!room:matrix.example.org"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("hello alias")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-a1"),
                "$alias1"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendToChannel("matrix", "#ircafe", "hello alias").blockingAwait();
    events.awaitCount(4);

    verify(roomDirectoryClient)
        .resolveRoomAlias("matrix", server, "secret-token", "#ircafe:matrix.example.org");
    verify(roomMessageSender)
        .sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("hello alias"));

    IrcEvent.ChannelMessage echoed =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", echoed.channel());
    assertEquals("hello alias", echoed.text());
  }

  @Test
  void sendNoticeToChannelPostsRoomNoticeWhenConnected() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomNotice(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("notice text")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-n1"),
                "$notice1"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendNoticeToChannel("matrix", "!room:matrix.example.org", "notice text")
        .blockingAwait();
    events.awaitCount(4);

    verify(roomMessageSender)
        .sendRoomNotice(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("notice text"));

    IrcEvent.Notice echoed =
        assertInstanceOf(IrcEvent.Notice.class, events.values().get(3).event());
    assertEquals("@alice:matrix.example.org", echoed.from());
    assertEquals("!room:matrix.example.org", echoed.target());
    assertEquals("notice text", echoed.text());
    assertEquals("$notice1", echoed.messageId());
    assertEquals("m.notice", echoed.ircv3Tags().get("matrix.msgtype"));
  }

  @Test
  void sendNoticeToChannelResolvesAliasViaDirectoryLookup() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomDirectoryClient.resolveRoomAlias(
            "matrix", server, "secret-token", "#ircafe:matrix.example.org"))
        .thenReturn(
            MatrixRoomDirectoryClient.ResolveResult.resolved(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/directory/room/%23ircafe:matrix.example.org"),
                "!room:matrix.example.org"));
    when(roomMessageSender.sendRoomNotice(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("notice alias")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-an1"),
                "$notice-alias"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendNoticeToChannel("matrix", "#ircafe", "notice alias").blockingAwait();
    events.awaitCount(4);

    verify(roomDirectoryClient)
        .resolveRoomAlias("matrix", server, "secret-token", "#ircafe:matrix.example.org");
    verify(roomMessageSender)
        .sendRoomNotice(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("notice alias"));

    IrcEvent.Notice echoed =
        assertInstanceOf(IrcEvent.Notice.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", echoed.target());
    assertEquals("notice alias", echoed.text());
  }

  @Test
  void sendPrivateMessageResolvesDirectRoomCachesItAndEmitsLocalEcho() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(directRoomResolver.resolveDirectRoom(
            "matrix", server, "secret-token", "@bob:matrix.example.org"))
        .thenReturn(
            MatrixDirectRoomResolver.ResolveResult.resolved(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/createRoom"),
                "!dm:matrix.example.org"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!dm:matrix.example.org"),
            anyString(),
            eq("hello bob")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!dm:matrix.example.org/send/m.room.message/txn-1"),
                "$pm1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!dm:matrix.example.org"),
            anyString(),
            eq("hello again")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!dm:matrix.example.org/send/m.room.message/txn-2"),
                "$pm2"));

    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendPrivateMessage("matrix", "@bob:matrix.example.org", "hello bob").blockingAwait();
    service.sendPrivateMessage("matrix", "@bob:matrix.example.org", "hello again").blockingAwait();
    events.awaitCount(5);

    verify(directRoomResolver, times(1))
        .resolveDirectRoom("matrix", server, "secret-token", "@bob:matrix.example.org");
    verify(roomMessageSender)
        .sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!dm:matrix.example.org"),
            anyString(),
            eq("hello bob"));
    verify(roomMessageSender)
        .sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!dm:matrix.example.org"),
            anyString(),
            eq("hello again"));

    IrcEvent.PrivateMessage first =
        assertInstanceOf(IrcEvent.PrivateMessage.class, events.values().get(3).event());
    assertEquals("@alice:matrix.example.org", first.from());
    assertEquals("hello bob", first.text());
    assertEquals("$pm1", first.messageId());
    assertEquals("@bob:matrix.example.org", first.ircv3Tags().get("ircafe/pm-target"));
    assertEquals("!dm:matrix.example.org", first.ircv3Tags().get("matrix.room_id"));

    IrcEvent.PrivateMessage second =
        assertInstanceOf(IrcEvent.PrivateMessage.class, events.values().get(4).event());
    assertEquals("hello again", second.text());
    assertEquals("$pm2", second.messageId());
  }

  @Test
  void sendPrivateMessageActionEmitsLocalEchoAction() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(directRoomResolver.resolveDirectRoom(
            "matrix", server, "secret-token", "@bob:matrix.example.org"))
        .thenReturn(
            MatrixDirectRoomResolver.ResolveResult.resolved(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/createRoom"),
                "!dm:matrix.example.org"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!dm:matrix.example.org"),
            anyString(),
            eq("\u0001ACTION waves\u0001")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!dm:matrix.example.org/send/m.room.message/txn-3"),
                "$pm-action"));

    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendPrivateMessage("matrix", "@bob:matrix.example.org", "\u0001ACTION waves\u0001")
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.PrivateAction echoed =
        assertInstanceOf(IrcEvent.PrivateAction.class, events.values().get(3).event());
    assertEquals("@alice:matrix.example.org", echoed.from());
    assertEquals("waves", echoed.action());
    assertEquals("$pm-action", echoed.messageId());
    assertEquals("@bob:matrix.example.org", echoed.ircv3Tags().get("ircafe/pm-target"));
    assertEquals("!dm:matrix.example.org", echoed.ircv3Tags().get("matrix.room_id"));
  }

  @Test
  void sendNoticePrivateResolvesDirectRoomAndEmitsLocalEchoNotice() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(directRoomResolver.resolveDirectRoom(
            "matrix", server, "secret-token", "@bob:matrix.example.org"))
        .thenReturn(
            MatrixDirectRoomResolver.ResolveResult.resolved(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/createRoom"),
                "!dm:matrix.example.org"));
    when(roomMessageSender.sendRoomNotice(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!dm:matrix.example.org"),
            anyString(),
            eq("private notice")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!dm:matrix.example.org/send/m.room.message/txn-n2"),
                "$notice2"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendNoticePrivate("matrix", "@bob:matrix.example.org", "private notice")
        .blockingAwait();
    events.awaitCount(4);

    verify(directRoomResolver)
        .resolveDirectRoom("matrix", server, "secret-token", "@bob:matrix.example.org");
    verify(roomMessageSender)
        .sendRoomNotice(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!dm:matrix.example.org"),
            anyString(),
            eq("private notice"));

    IrcEvent.Notice echoed =
        assertInstanceOf(IrcEvent.Notice.class, events.values().get(3).event());
    assertEquals("@alice:matrix.example.org", echoed.from());
    assertEquals("@bob:matrix.example.org", echoed.target());
    assertEquals("private notice", echoed.text());
    assertEquals("$notice2", echoed.messageId());
    assertEquals("@bob:matrix.example.org", echoed.ircv3Tags().get("ircafe/pm-target"));
    assertEquals("!dm:matrix.example.org", echoed.ircv3Tags().get("matrix.room_id"));
    assertEquals("m.notice", echoed.ircv3Tags().get("matrix.msgtype"));
  }

  @Test
  void connectEmitsTimelineEventsFromSyncPoll() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$timeline1",
                        "m.text",
                        "hello from sync",
                        1710000000000L))));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChannelMessage message =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", message.channel());
    assertEquals("@bob:matrix.example.org", message.from());
    assertEquals("hello from sync", message.text());
    assertEquals("$timeline1", message.messageId());
  }

  @Test
  void connectEmitsEncryptedTimelinePlaceholderFromSyncPoll() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$timeline-encrypted",
                        "m.room.encrypted",
                        "[encrypted message unavailable]",
                        1710000000500L))));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChannelMessage message =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("$timeline-encrypted", message.messageId());
    assertEquals("[encrypted message unavailable]", message.text());
    assertEquals("m.room.encrypted", message.ircv3Tags().get("matrix.msgtype"));
  }

  @Test
  void connectEmitsMatrixMediaUrlTagOnSyncTimelineMessage() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$timeline-media",
                        "m.image",
                        "photo.png",
                        "",
                        1710000000550L,
                        "mxc://matrix.example.org/media-1"))));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChannelMessage message =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("$timeline-media", message.messageId());
    assertEquals("m.image", message.ircv3Tags().get("matrix.msgtype"));
    assertEquals("mxc://matrix.example.org/media-1", message.ircv3Tags().get("matrix.media_url"));
  }

  @Test
  void connectEmitsDraftReplyTagOnSyncTimelineMessage() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$timeline-reply",
                        "m.text",
                        "reply from sync",
                        "$timeline-root",
                        1710000000000L))));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChannelMessage message =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("$timeline-reply", message.messageId());
    assertEquals("$timeline-root", message.ircv3Tags().get("draft/reply"));
  }

  @Test
  void connectEmitsDraftReplyTagOnSyncNoticeAndActionMessages() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$timeline-notice",
                        "m.notice",
                        "notice from sync",
                        "$notice-root",
                        1710000000000L),
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$timeline-emote",
                        "m.emote",
                        "waves",
                        "$emote-root",
                        1710000001000L))));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(5);

    IrcEvent.Notice notice =
        assertInstanceOf(IrcEvent.Notice.class, events.values().get(3).event());
    assertEquals("$timeline-notice", notice.messageId());
    assertEquals("$notice-root", notice.ircv3Tags().get("draft/reply"));

    IrcEvent.ChannelAction action =
        assertInstanceOf(IrcEvent.ChannelAction.class, events.values().get(4).event());
    assertEquals("$timeline-emote", action.messageId());
    assertEquals("$emote-root", action.ircv3Tags().get("draft/reply"));
  }

  @Test
  void connectEmitsPrivateTimelineEventsForMappedDirectRooms() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!dm:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$timeline-dm",
                        "m.text",
                        "hello direct",
                        1710000002000L)),
                Map.of("!dm:matrix.example.org", "@bob:matrix.example.org")));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(4);

    IrcEvent.PrivateMessage message =
        assertInstanceOf(IrcEvent.PrivateMessage.class, events.values().get(3).event());
    assertEquals("@bob:matrix.example.org", message.from());
    assertEquals("hello direct", message.text());
    assertEquals("$timeline-dm", message.messageId());
    assertEquals("!dm:matrix.example.org", message.ircv3Tags().get("matrix.room_id"));
  }

  @Test
  void connectEmitsMembershipAndDisplayEventsFromSyncPoll() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(),
                List.of(
                    new MatrixSyncClient.RoomMembershipEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$join",
                        "join",
                        "leave",
                        "Bob",
                        "",
                        "",
                        1710000001000L),
                    new MatrixSyncClient.RoomMembershipEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$display",
                        "join",
                        "join",
                        "Bobby",
                        "Bob",
                        "",
                        1710000002000L),
                    new MatrixSyncClient.RoomMembershipEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$part",
                        "leave",
                        "join",
                        "",
                        "Bobby",
                        "left the room",
                        1710000003000L)),
                Map.of(),
                List.of(),
                List.of()));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(7);

    IrcEvent.UserJoinedChannel joined =
        assertInstanceOf(IrcEvent.UserJoinedChannel.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", joined.channel());
    assertEquals("@bob:matrix.example.org", joined.nick());

    IrcEvent.UserSetNameObserved firstSetName =
        assertInstanceOf(IrcEvent.UserSetNameObserved.class, events.values().get(4).event());
    assertEquals("@bob:matrix.example.org", firstSetName.nick());
    assertEquals("Bob", firstSetName.realName());

    IrcEvent.UserSetNameObserved secondSetName =
        assertInstanceOf(IrcEvent.UserSetNameObserved.class, events.values().get(5).event());
    assertEquals("@bob:matrix.example.org", secondSetName.nick());
    assertEquals("Bobby", secondSetName.realName());

    IrcEvent.UserPartedChannel parted =
        assertInstanceOf(IrcEvent.UserPartedChannel.class, events.values().get(6).event());
    assertEquals("!room:matrix.example.org", parted.channel());
    assertEquals("@bob:matrix.example.org", parted.nick());
    assertEquals("left the room", parted.reason());
  }

  @Test
  void connectEmitsLeftChannelWhenSelfMembershipLeavesInSyncPoll() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(),
                List.of(
                    new MatrixSyncClient.RoomMembershipEvent(
                        "!room:matrix.example.org",
                        "@alice:matrix.example.org",
                        "@mod:matrix.example.org",
                        "$self-leave",
                        "leave",
                        "join",
                        "",
                        "",
                        "kicked",
                        1710000009000L)),
                Map.of(),
                List.of(),
                List.of()));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(4);

    IrcEvent.LeftChannel left =
        assertInstanceOf(IrcEvent.LeftChannel.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", left.channel());
    assertEquals("kicked", left.reason());
  }

  @Test
  void connectEmitsEditReactionAndRedactionEventsFromSyncPoll() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(),
                List.of(),
                List.of(
                    new MatrixSyncClient.RoomMessageEditEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$edit1",
                        "$msg1",
                        "m.text",
                        "fixed text",
                        1710000010000L)),
                List.of(
                    new MatrixSyncClient.RoomReactionEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$react1",
                        "$msg1",
                        ":+1:",
                        1710000011000L)),
                List.of(
                    new MatrixSyncClient.RoomRedactionEvent(
                        "!room:matrix.example.org",
                        "@mod:matrix.example.org",
                        "$redact1",
                        "$react1",
                        "",
                        1710000012000L),
                    new MatrixSyncClient.RoomRedactionEvent(
                        "!room:matrix.example.org",
                        "@mod:matrix.example.org",
                        "$redact2",
                        "$msg1",
                        "",
                        1710000013000L)),
                Map.of(),
                List.of(),
                List.of()));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(7);

    IrcEvent.ChannelMessage edit =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", edit.channel());
    assertEquals("@bob:matrix.example.org", edit.from());
    assertEquals("fixed text", edit.text());
    assertEquals("$msg1", edit.ircv3Tags().get("draft/edit"));

    IrcEvent.MessageReactObserved react =
        assertInstanceOf(IrcEvent.MessageReactObserved.class, events.values().get(4).event());
    assertEquals("@bob:matrix.example.org", react.from());
    assertEquals("!room:matrix.example.org", react.target());
    assertEquals(":+1:", react.reaction());
    assertEquals("$msg1", react.messageId());

    IrcEvent.MessageUnreactObserved unreact =
        assertInstanceOf(IrcEvent.MessageUnreactObserved.class, events.values().get(5).event());
    assertEquals("@bob:matrix.example.org", unreact.from());
    assertEquals("!room:matrix.example.org", unreact.target());
    assertEquals(":+1:", unreact.reaction());
    assertEquals("$msg1", unreact.messageId());

    IrcEvent.MessageRedactionObserved redaction =
        assertInstanceOf(IrcEvent.MessageRedactionObserved.class, events.values().get(6).event());
    assertEquals("@mod:matrix.example.org", redaction.from());
    assertEquals("!room:matrix.example.org", redaction.target());
    assertEquals("$msg1", redaction.messageId());
  }

  @Test
  void connectEmitsTypingSignalsFromSyncPoll() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(),
                Map.of("!dm:matrix.example.org", "@bob:matrix.example.org"),
                List.of(
                    new MatrixSyncClient.TypingEvent(
                        "!room:matrix.example.org", List.of("@bob:matrix.example.org")),
                    new MatrixSyncClient.TypingEvent(
                        "!dm:matrix.example.org", List.of("@bob:matrix.example.org"))),
                List.of()));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(5);

    IrcEvent.UserTypingObserved roomTyping =
        assertInstanceOf(IrcEvent.UserTypingObserved.class, events.values().get(3).event());
    assertEquals("@bob:matrix.example.org", roomTyping.from());
    assertEquals("!room:matrix.example.org", roomTyping.target());
    assertEquals("active", roomTyping.state());

    IrcEvent.UserTypingObserved dmTyping =
        assertInstanceOf(IrcEvent.UserTypingObserved.class, events.values().get(4).event());
    assertEquals("@bob:matrix.example.org", dmTyping.from());
    assertEquals("@bob:matrix.example.org", dmTyping.target());
    assertEquals("active", dmTyping.state());
  }

  @Test
  void connectEmitsReadMarkerSignalsFromSelfReceipts() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    long channelTs = 1710000010000L;
    long dmTs = 1710000011000L;
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(),
                Map.of("!dm:matrix.example.org", "@bob:matrix.example.org"),
                List.of(),
                List.of(
                    new MatrixSyncClient.ReadReceiptEvent(
                        "!room:matrix.example.org",
                        "$event-chan",
                        "@alice:matrix.example.org",
                        channelTs),
                    new MatrixSyncClient.ReadReceiptEvent(
                        "!dm:matrix.example.org", "$event-dm", "@alice:matrix.example.org", dmTs),
                    new MatrixSyncClient.ReadReceiptEvent(
                        "!room:matrix.example.org",
                        "$event-ignored",
                        "@bob:matrix.example.org",
                        channelTs))));
    var events = service.events().test();

    service.connect("matrix").blockingAwait();
    events.awaitCount(5);

    IrcEvent.ReadMarkerObserved roomMarker =
        assertInstanceOf(IrcEvent.ReadMarkerObserved.class, events.values().get(3).event());
    assertEquals("@alice:matrix.example.org", roomMarker.from());
    assertEquals("!room:matrix.example.org", roomMarker.target());
    assertEquals("timestamp=" + Instant.ofEpochMilli(channelTs), roomMarker.marker());

    IrcEvent.ReadMarkerObserved dmMarker =
        assertInstanceOf(IrcEvent.ReadMarkerObserved.class, events.values().get(4).event());
    assertEquals("@alice:matrix.example.org", dmMarker.from());
    assertEquals("@bob:matrix.example.org", dmMarker.target());
    assertEquals("timestamp=" + Instant.ofEpochMilli(dmTs), dmMarker.marker());
  }

  @Test
  void requestChatHistoryBeforeEmitsChatHistoryBatchForRoom() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 3))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=3"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h1",
                        "m.text",
                        "older text",
                        "$root-msg",
                        1710000003000L),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h2",
                        "m.notice",
                        "older notice",
                        1710000004000L),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org", "$h3", "m.emote", "waves", 1710000005000L))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .requestChatHistoryBefore("matrix", "!room:matrix.example.org", Instant.now(), 3)
        .blockingAwait();
    events.awaitCount(4);

    verify(roomHistoryClient)
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 3);

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", batch.target());
    assertEquals(3, batch.entries().size());
    assertEquals(ChatHistoryEntry.Kind.PRIVMSG, batch.entries().get(0).kind());
    assertEquals(ChatHistoryEntry.Kind.NOTICE, batch.entries().get(1).kind());
    assertEquals(ChatHistoryEntry.Kind.ACTION, batch.entries().get(2).kind());
    assertEquals("$h1", batch.entries().get(0).messageId());
    assertEquals("$root-msg", batch.entries().get(0).ircv3Tags().get("draft/reply"));
  }

  @Test
  void requestChatHistoryBeforeIncludesEncryptedPlaceholderEntries() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 1))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=1"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-encrypted",
                        "m.room.encrypted",
                        "[encrypted message unavailable]",
                        1710000006000L))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .requestChatHistoryBefore("matrix", "!room:matrix.example.org", Instant.now(), 1)
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals(1, batch.entries().size());
    assertEquals(ChatHistoryEntry.Kind.PRIVMSG, batch.entries().getFirst().kind());
    assertEquals("$h-encrypted", batch.entries().getFirst().messageId());
    assertEquals("[encrypted message unavailable]", batch.entries().getFirst().text());
    assertEquals("m.room.encrypted", batch.entries().getFirst().ircv3Tags().get("matrix.msgtype"));
  }

  @Test
  void requestChatHistoryBeforeIncludesMatrixMediaUrlTag() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 1))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=1"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-image",
                        "m.image",
                        "photo.png",
                        "",
                        1710000006100L,
                        "mxc://matrix.example.org/media-h1"))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .requestChatHistoryBefore("matrix", "!room:matrix.example.org", Instant.now(), 1)
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals(1, batch.entries().size());
    assertEquals("$h-image", batch.entries().getFirst().messageId());
    assertEquals("m.image", batch.entries().getFirst().ircv3Tags().get("matrix.msgtype"));
    assertEquals(
        "mxc://matrix.example.org/media-h1",
        batch.entries().getFirst().ircv3Tags().get("matrix.media_url"));
  }

  @Test
  void requestChatHistoryBeforeSelectorTimestampUsesTimestampAnchor() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 2))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=2"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h1",
                        "m.text",
                        "older text",
                        1710000003000L))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .requestChatHistoryBefore(
            "matrix", "!room:matrix.example.org", "timestamp=2024-03-09T16:00:03Z", 2)
        .blockingAwait();
    events.awaitCount(4);

    verify(roomHistoryClient)
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 2);
    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals(1, batch.entries().size());
  }

  @Test
  void requestChatHistoryBeforeSelectorMsgidUsesKnownMessageAnchor() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("seed")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-seed"),
                "$h1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 10))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=10"),
                "s-next",
                List.of()));
    service.connect("matrix").blockingAwait();
    service.sendToChannel("matrix", "!room:matrix.example.org", "seed").blockingAwait();

    assertDoesNotThrow(
        () ->
            service
                .requestChatHistoryBefore("matrix", "!room:matrix.example.org", "msgid=$h1", 10)
                .blockingAwait());

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 10);
  }

  @Test
  void requestChatHistoryBeforeSelectorMsgidUnknownReportsUnavailable() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () ->
                service
                    .requestChatHistoryBefore(
                        "matrix", "!room:matrix.example.org", "msgid=$unknown", 10)
                    .blockingAwait());

    assertEquals("chat-history-before", err.operation());
    assertTrue(err.getMessage().contains("cannot resolve msgid selector"));
  }

  @Test
  void requestChatHistoryBeforeSelectorMsgidScansHistoryWhenMissingFromIndex() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-scan-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-scan",
                        "m.text",
                        "seeded from scan",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-scan-next", 5))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-scan-next&dir=b&limit=5"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-result",
                        "m.text",
                        "result line",
                        Instant.parse("2024-03-09T15:40:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 5))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=5"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-result",
                        "m.text",
                        "result line",
                        Instant.parse("2024-03-09T15:40:00Z").toEpochMilli()))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    assertDoesNotThrow(
        () ->
            service
                .requestChatHistoryBefore("matrix", "!room:matrix.example.org", "msgid=$h-scan", 5)
                .blockingAwait());
    events.awaitCount(4);

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200);
    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq(5));
    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals(1, batch.entries().size());
    assertEquals("$h-result", batch.entries().getFirst().messageId());
  }

  @Test
  void requestChatHistoryLatestSelectorMsgidUsesKnownMessageAnchor() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("seed")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-seed"),
                "$h1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 3))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=3"),
                "s-next",
                List.of()));
    service.connect("matrix").blockingAwait();
    service.sendToChannel("matrix", "!room:matrix.example.org", "seed").blockingAwait();

    assertDoesNotThrow(
        () ->
            service
                .requestChatHistoryLatest("matrix", "!room:matrix.example.org", "msgid=$h1", 3)
                .blockingAwait());

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 3);
  }

  @Test
  void requestChatHistoryLatestWildcardReturnsRecentHistoryWindow() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 2))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=2"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h1",
                        "m.text",
                        "recent text",
                        1710000003000L))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.requestChatHistoryLatest("matrix", "!room:matrix.example.org", "*", 2).blockingAwait();
    events.awaitCount(4);

    verify(roomHistoryClient)
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 2);
    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals(1, batch.entries().size());
  }

  @Test
  void requestChatHistoryLatestTimestampSelectorUsesBeforePath() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 10))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=10"),
                "s-next",
                List.of()));
    service.connect("matrix").blockingAwait();

    assertDoesNotThrow(
        () ->
            service
                .requestChatHistoryLatest(
                    "matrix", "!room:matrix.example.org", "timestamp=2024-03-09T16:00:03Z", 10)
                .blockingAwait());

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 10);
  }

  @Test
  void requestChatHistoryAroundTimestampSelectorUsesBidirectionalWindowing() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-center",
                        "m.text",
                        "around center",
                        Instant.parse("2024-03-09T16:00:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=40"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-newer",
                        "m.text",
                        "around newer",
                        Instant.parse("2024-03-09T16:01:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=b&limit=40"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-older",
                        "m.text",
                        "around older",
                        Instant.parse("2024-03-09T15:59:03Z").toEpochMilli()))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    assertDoesNotThrow(
        () ->
            service
                .requestChatHistoryAround(
                    "matrix", "!room:matrix.example.org", "timestamp=2024-03-09T16:00:03Z", 10)
                .blockingAwait());
    events.awaitCount(4);

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals(2, batch.entries().size());
    assertEquals("$h-older", batch.entries().get(0).messageId());
    assertEquals("$h-newer", batch.entries().get(1).messageId());

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200);
    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40);
    verify(roomHistoryClient, times(1))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40);
  }

  @Test
  void requestChatHistoryAroundMsgidSelectorUsesKnownMessageTimestamp() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$h-center",
                        "m.text",
                        "seed center",
                        Instant.parse("2024-03-09T16:00:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-center",
                        "m.text",
                        "seed center",
                        Instant.parse("2024-03-09T16:00:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=b&limit=40"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-older",
                        "m.text",
                        "older",
                        Instant.parse("2024-03-09T15:59:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=40"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-newer",
                        "m.text",
                        "newer",
                        Instant.parse("2024-03-09T16:01:03Z").toEpochMilli()))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(4);

    service
        .requestChatHistoryAround("matrix", "!room:matrix.example.org", "msgid=$h-center", 10)
        .blockingAwait();
    events.awaitCount(5);

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(4).event());
    assertEquals(2, batch.entries().size());
    assertEquals("$h-older", batch.entries().get(0).messageId());
    assertEquals("$h-newer", batch.entries().get(1).messageId());
    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200);
    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40);
    verify(roomHistoryClient, times(1))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40);
  }

  @Test
  void requestChatHistoryAroundFailsWhenBackwardHistoryFetchFails() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-center",
                        "m.text",
                        "center",
                        Instant.parse("2024-03-09T16:00:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=b&limit=40"),
                "backward failed"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                service
                    .requestChatHistoryAround(
                        "matrix", "!room:matrix.example.org", "timestamp=2024-03-09T16:00:03Z", 10)
                    .blockingAwait());

    assertTrue(err.getMessage().contains("Matrix history backward fetch failed"));
    verify(roomHistoryClient, times(0))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40);
  }

  @Test
  void requestChatHistoryAroundDedupesDuplicateEntriesWithoutMsgid() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-center",
                        "m.text",
                        "center",
                        Instant.parse("2024-03-09T16:00:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=b&limit=40"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "",
                        "m.text",
                        "duplicate without id",
                        Instant.parse("2024-03-09T16:00:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=40"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "",
                        "m.text",
                        "duplicate without id",
                        Instant.parse("2024-03-09T16:00:03Z").toEpochMilli()))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .requestChatHistoryAround(
            "matrix", "!room:matrix.example.org", "timestamp=2024-03-09T16:00:03Z", 10)
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals(1, batch.entries().size());
    assertEquals("", batch.entries().getFirst().messageId());
  }

  @Test
  void requestChatHistoryBetweenTimestampSelectorsEmitFilteredBatch() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$older",
                        "m.text",
                        "older",
                        Instant.parse("2024-03-09T15:10:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$inside",
                        "m.text",
                        "inside",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$newer",
                        "m.text",
                        "newer",
                        Instant.parse("2024-03-09T16:05:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 10))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=10"),
                "s-forward",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$inside",
                        "m.text",
                        "inside",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$newer",
                        "m.text",
                        "newer",
                        Instant.parse("2024-03-09T16:05:00Z").toEpochMilli()))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .requestChatHistoryBetween(
            "matrix",
            "!room:matrix.example.org",
            "timestamp=2024-03-09T15:30:00Z",
            "timestamp=2024-03-09T16:00:00Z",
            10)
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals(1, batch.entries().size());
    assertEquals("$inside", batch.entries().getFirst().messageId());
    verify(roomHistoryClient, times(1))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 10);
  }

  @Test
  void requestChatHistoryBetweenMsgidSelectorsEmitFilteredBatch() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$h-start",
                        "m.text",
                        "start",
                        Instant.parse("2024-03-09T15:30:00Z").toEpochMilli()),
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$h-end",
                        "m.text",
                        "end",
                        Instant.parse("2024-03-09T16:00:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-start",
                        "m.text",
                        "start",
                        Instant.parse("2024-03-09T15:30:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$inside",
                        "m.text",
                        "inside",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-end",
                        "m.text",
                        "end",
                        Instant.parse("2024-03-09T16:00:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 10))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=10"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$inside",
                        "m.text",
                        "inside",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-end",
                        "m.text",
                        "end",
                        Instant.parse("2024-03-09T16:00:00Z").toEpochMilli()))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(5);

    service
        .requestChatHistoryBetween(
            "matrix", "!room:matrix.example.org", "msgid=$h-start", "msgid=$h-end", 10)
        .blockingAwait();
    events.awaitCount(6);

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(5).event());
    assertEquals(1, batch.entries().size());
    assertEquals("$inside", batch.entries().getFirst().messageId());
    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200);
    verify(roomHistoryClient, times(1))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 10);
  }

  @Test
  void requestChatHistoryBetweenFailsWhenTimestampCursorScanFails() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "scan failed"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                service
                    .requestChatHistoryBetween(
                        "matrix",
                        "!room:matrix.example.org",
                        "timestamp=2024-03-09T15:30:00Z",
                        "timestamp=2024-03-09T16:00:00Z",
                        10)
                    .blockingAwait());

    assertTrue(err.getMessage().contains("Matrix history timestamp scan failed"));
    verify(roomHistoryClient, times(0))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 10);
  }

  @Test
  void requestChatHistoryBetweenFailsWhenForwardHistoryFetchFails() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$inside",
                        "m.text",
                        "inside",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 10))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=10"),
                "forward failed"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                service
                    .requestChatHistoryBetween(
                        "matrix",
                        "!room:matrix.example.org",
                        "timestamp=2024-03-09T15:30:00Z",
                        "timestamp=2024-03-09T16:00:00Z",
                        10)
                    .blockingAwait());

    assertTrue(err.getMessage().contains("Matrix history forward fetch failed"));
  }

  @Test
  void requestChatHistoryBetweenDedupesDuplicateEntriesWithoutMsgidAcrossPages() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$older",
                        "m.text",
                        "older",
                        Instant.parse("2024-03-09T15:10:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$inside",
                        "m.text",
                        "inside",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$newer",
                        "m.text",
                        "newer",
                        Instant.parse("2024-03-09T16:05:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 3))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=3"),
                "s-page2",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "",
                        "m.text",
                        "duplicate without id",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-page2", 2))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-page2&dir=f&limit=2"),
                "s-page2",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "",
                        "m.text",
                        "duplicate without id",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .requestChatHistoryBetween(
            "matrix",
            "!room:matrix.example.org",
            "timestamp=2024-03-09T15:30:00Z",
            "timestamp=2024-03-09T16:00:00Z",
            3)
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals(1, batch.entries().size());
    assertEquals("", batch.entries().getFirst().messageId());
    verify(roomHistoryClient, times(1))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 3);
    verify(roomHistoryClient, times(1))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-page2", 2);
  }

  @Test
  void requestChatHistoryBeforePrivateTargetUsesKnownDirectRoom() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of(),
                Map.of("!dm:matrix.example.org", "@bob:matrix.example.org")));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!dm:matrix.example.org", "s-anchor", 5))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!dm:matrix.example.org/messages?from=s-anchor&dir=b&limit=5"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$dmh1",
                        "m.text",
                        "hello in dm history",
                        1710000006000L))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .requestChatHistoryBefore("matrix", "@bob:matrix.example.org", Instant.now(), 5)
        .blockingAwait();
    events.awaitCount(4);

    verify(roomHistoryClient)
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!dm:matrix.example.org", "s-anchor", 5);
    IrcEvent.ChatHistoryBatchReceived batch =
        assertInstanceOf(IrcEvent.ChatHistoryBatchReceived.class, events.values().get(3).event());
    assertEquals("@bob:matrix.example.org", batch.target());
    assertEquals(1, batch.entries().size());
    assertEquals("@bob:matrix.example.org", batch.entries().getFirst().target());
  }

  @Test
  void requestChatHistoryBeforeFailsForUnknownDirectTarget() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                service
                    .requestChatHistoryBefore(
                        "matrix", "@unknown:matrix.example.org", Instant.now(), 5)
                    .blockingAwait());

    assertTrue(err.getMessage().contains("direct room is unknown"));
  }

  @Test
  void sendToChannelFailsWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () ->
                service
                    .sendToChannel("matrix", "!room:matrix.example.org", "hello")
                    .blockingAwait());

    assertEquals("send-to-channel", err.operation());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void requestChatHistoryFailsWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () ->
                service
                    .requestChatHistoryBefore(
                        "matrix", "!room:matrix.example.org", Instant.now(), 20)
                    .blockingAwait());

    assertEquals("chat-history", err.operation());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void joinFailsWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () -> service.joinChannel("matrix", "#ircafe").blockingAwait());

    assertEquals("join", err.operation());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void partFailsWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () -> service.partChannel("matrix", "!room:matrix.example.org", "bye").blockingAwait());

    assertEquals("part", err.operation());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void sendPrivateMessageFailsWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () ->
                service
                    .sendPrivateMessage("matrix", "@bob:matrix.example.org", "hello")
                    .blockingAwait());

    assertEquals("send-private-message", err.operation());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void sendNoticeToChannelFailsWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () ->
                service
                    .sendNoticeToChannel("matrix", "!room:matrix.example.org", "notice")
                    .blockingAwait());

    assertEquals("send-notice-to-channel", err.operation());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void sendNoticePrivateFailsWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () ->
                service
                    .sendNoticePrivate("matrix", "@bob:matrix.example.org", "notice")
                    .blockingAwait());

    assertEquals("send-notice-private", err.operation());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void sendTypingFailsWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () ->
                service.sendTyping("matrix", "!room:matrix.example.org", "active").blockingAwait());

    assertEquals("send-typing", err.operation());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void sendTypingDelegatesToTypingClientForActiveState() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomTypingClient.setTyping(
            "matrix",
            server,
            "secret-token",
            "!room:matrix.example.org",
            "@alice:matrix.example.org",
            true,
            30_000))
        .thenReturn(
            MatrixRoomTypingClient.TypingResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/typing/@alice:matrix.example.org"),
                true));
    service.connect("matrix").blockingAwait();

    assertDoesNotThrow(
        () ->
            service.sendTyping("matrix", "!room:matrix.example.org", "composing").blockingAwait());

    verify(roomTypingClient, times(1))
        .setTyping(
            "matrix",
            server,
            "secret-token",
            "!room:matrix.example.org",
            "@alice:matrix.example.org",
            true,
            30_000);
  }

  @Test
  void sendTypingIgnoresUnsupportedState() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    assertDoesNotThrow(
        () -> service.sendTyping("matrix", "!room:matrix.example.org", "unknown").blockingAwait());

    verifyNoInteractions(roomTypingClient);
  }

  @Test
  void sendReadMarkerFailsWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () ->
                service
                    .sendReadMarker("matrix", "!room:matrix.example.org", Instant.now())
                    .blockingAwait());

    assertEquals("send-read-marker", err.operation());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
  }

  @Test
  void sendReadMarkerNoopsWhenNoEventIdKnown() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    assertDoesNotThrow(
        () ->
            service
                .sendReadMarker("matrix", "!room:matrix.example.org", Instant.now())
                .blockingAwait());

    verifyNoInteractions(readMarkerClient);
  }

  @Test
  void sendReadMarkerUsesLatestKnownRoomEventId() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("hello")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-1"),
                "$latest-event"));
    when(readMarkerClient.updateReadMarker(
            "matrix", server, "secret-token", "!room:matrix.example.org", "$latest-event"))
        .thenReturn(
            MatrixReadMarkerClient.ReadMarkerResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/read_markers"),
                "!room:matrix.example.org",
                "$latest-event"));
    service.connect("matrix").blockingAwait();
    service.sendToChannel("matrix", "!room:matrix.example.org", "hello").blockingAwait();

    assertDoesNotThrow(
        () ->
            service
                .sendReadMarker("matrix", "!room:matrix.example.org", Instant.now())
                .blockingAwait());

    verify(readMarkerClient, times(1))
        .updateReadMarker(
            "matrix", server, "secret-token", "!room:matrix.example.org", "$latest-event");
  }

  @Test
  void sendPrivateMessageRejectsInvalidTarget() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.sendPrivateMessage("matrix", "bob", "hello").blockingAwait());

    assertEquals("target is not a Matrix user id", err.getMessage());
  }

  @Test
  void sendPrivateMessageReportsDirectRoomResolutionFailure() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(directRoomResolver.resolveDirectRoom(
            "matrix", server, "secret-token", "@bob:matrix.example.org"))
        .thenReturn(
            MatrixDirectRoomResolver.ResolveResult.failed(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/createRoom"),
                "HTTP 403 from createRoom endpoint"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                service
                    .sendPrivateMessage("matrix", "@bob:matrix.example.org", "hello")
                    .blockingAwait());

    assertTrue(err.getMessage().contains("HTTP 403 from createRoom endpoint"));
  }

  @Test
  void sendToChannelRejectsInvalidRoomId() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.sendToChannel("matrix", "irc", "hello").blockingAwait());

    assertEquals("target is not a Matrix room id or alias", err.getMessage());
  }

  @Test
  void sendToChannelReportsAliasLookupFailure() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomDirectoryClient.resolveRoomAlias(
            "matrix", server, "secret-token", "#ircafe:matrix.example.org"))
        .thenReturn(
            MatrixRoomDirectoryClient.ResolveResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/directory/room/%23ircafe:matrix.example.org"),
                "HTTP 404 from room directory endpoint"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () -> service.sendToChannel("matrix", "#ircafe", "hello").blockingAwait());

    assertTrue(err.getMessage().contains("HTTP 404 from room directory endpoint"));
  }

  @Test
  void sendToChannelReportsSenderFailure() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("hello matrix")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/send/m.room.message/txn-2"),
                "HTTP 403 from room send endpoint"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                service
                    .sendToChannel("matrix", "!room:matrix.example.org", "hello matrix")
                    .blockingAwait());

    assertTrue(err.getMessage().contains("HTTP 403 from room send endpoint"));
  }

  @Test
  void rawReportsBackendUnavailableWhenDisconnected() {
    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () -> service.sendRaw("matrix", "PING").blockingAwait());

    assertEquals(IrcProperties.Server.Backend.MATRIX, err.backend());
    assertEquals("raw", err.operation());
    assertEquals("matrix", err.serverId());
    assertTrue(err.getMessage().contains("not connected"));
  }

  @Test
  void rawReportsUnsupportedCommandAfterConnect() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () -> service.sendRaw("matrix", "PING").blockingAwait());

    assertEquals("raw", err.operation());
    assertTrue(err.getMessage().contains("raw command PING is not supported"));
  }

  @Test
  void rawJoinDelegatesToJoinChannel() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMembershipClient.joinRoom(
            "matrix", server, "secret-token", "#ircafe:matrix.example.org"))
        .thenReturn(
            MatrixRoomMembershipClient.JoinResult.joined(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/join/%23ircafe%3Amatrix.example.org"),
                "!room:matrix.example.org"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendRaw("matrix", "JOIN #ircafe").blockingAwait();
    events.awaitCount(4);

    verify(roomMembershipClient, times(1))
        .joinRoom("matrix", server, "secret-token", "#ircafe:matrix.example.org");
    IrcEvent.JoinedChannel joined =
        assertInstanceOf(IrcEvent.JoinedChannel.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", joined.channel());
  }

  @Test
  void rawTopicQueryFetchesRoomTopicAndEmitsTopicEvent() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomStateClient.fetchRoomTopic(
            "matrix", server, "secret-token", "!room:matrix.example.org"))
        .thenReturn(
            MatrixRoomStateClient.TopicResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/state/m.room.topic"),
                "hello matrix topic"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendRaw("matrix", "TOPIC !room:matrix.example.org").blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChannelTopicUpdated topic =
        assertInstanceOf(IrcEvent.ChannelTopicUpdated.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", topic.channel());
    assertEquals("hello matrix topic", topic.topic());
  }

  @Test
  void rawInviteDelegatesToMembershipInviteAndEmitsInviteEvent() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMembershipClient.inviteUser(
            "matrix",
            server,
            "secret-token",
            "!room:matrix.example.org",
            "@bob:matrix.example.org"))
        .thenReturn(
            MatrixRoomMembershipClient.ActionResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/invite")));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendRaw("matrix", "INVITE @bob:matrix.example.org !room:matrix.example.org")
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.InvitedToChannel invite =
        assertInstanceOf(IrcEvent.InvitedToChannel.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", invite.channel());
    assertEquals("@bob:matrix.example.org", invite.invitee());
  }

  @Test
  void rawKickDelegatesToMembershipKickAndEmitsKickEvent() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMembershipClient.kickUser(
            "matrix",
            server,
            "secret-token",
            "!room:matrix.example.org",
            "@bob:matrix.example.org",
            "cleanup"))
        .thenReturn(
            MatrixRoomMembershipClient.ActionResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/kick")));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendRaw("matrix", "KICK !room:matrix.example.org @bob:matrix.example.org :cleanup")
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.UserKickedFromChannel kick =
        assertInstanceOf(IrcEvent.UserKickedFromChannel.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", kick.channel());
    assertEquals("@bob:matrix.example.org", kick.nick());
    assertEquals("@alice:matrix.example.org", kick.by());
    assertEquals("cleanup", kick.reason());
  }

  @Test
  void rawWhoForRoomDelegatesToNames() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomRosterClient.fetchJoinedMembers(
            "matrix", server, "secret-token", "!room:matrix.example.org"))
        .thenReturn(
            MatrixRoomRosterClient.RosterResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/joined_members"),
                List.of(
                    new MatrixRoomRosterClient.JoinedMember("@bob:matrix.example.org", "Bob"))));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendRaw("matrix", "WHO !room:matrix.example.org").blockingAwait();
    events.awaitCount(4);

    IrcEvent.NickListUpdated names =
        assertInstanceOf(IrcEvent.NickListUpdated.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", names.channel());
    assertEquals(1, names.totalUsers());
  }

  @Test
  void rawListEmitsChannelListEventsFromPublicRooms() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomDirectoryClient.fetchPublicRooms("matrix", server, "secret-token", "", "", 100))
        .thenReturn(
            MatrixRoomDirectoryClient.PublicRoomsResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/publicRooms"),
                List.of(
                    new MatrixRoomDirectoryClient.PublicRoom(
                        "!a:matrix.example.org", "#a:matrix.example.org", "Room A", "Topic A", 42),
                    new MatrixRoomDirectoryClient.PublicRoom(
                        "!b:matrix.example.org", "", "Room B", "", 8)),
                "s-page-2"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendRaw("matrix", "LIST").blockingAwait();
    events.awaitCount(7);

    IrcEvent.ChannelListStarted started =
        assertInstanceOf(IrcEvent.ChannelListStarted.class, events.values().get(3).event());
    IrcEvent.ChannelListEntry first =
        assertInstanceOf(IrcEvent.ChannelListEntry.class, events.values().get(4).event());
    IrcEvent.ChannelListEntry second =
        assertInstanceOf(IrcEvent.ChannelListEntry.class, events.values().get(5).event());
    IrcEvent.ChannelListEnded ended =
        assertInstanceOf(IrcEvent.ChannelListEnded.class, events.values().get(6).event());

    assertEquals("Matrix public rooms", started.banner());
    assertEquals("#a:matrix.example.org", first.channel());
    assertEquals(42, first.visibleUsers());
    assertEquals("Topic A", first.topic());
    assertEquals("!b:matrix.example.org", second.channel());
    assertEquals(8, second.visibleUsers());
    assertEquals("Room B", second.topic());
    assertTrue(ended.summary().contains("next_batch=s-page-2"));
  }

  @Test
  void rawListParsesSearchLimitAndSinceOptions() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomDirectoryClient.fetchPublicRooms(
            "matrix", server, "secret-token", "linux", "s-token", 30))
        .thenReturn(
            MatrixRoomDirectoryClient.PublicRoomsResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/publicRooms"),
                List.of(),
                ""));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendRaw("matrix", "LIST search=linux limit=30 since=s-token").blockingAwait();
    events.awaitCount(5);

    verify(roomDirectoryClient)
        .fetchPublicRooms("matrix", server, "secret-token", "linux", "s-token", 30);
  }

  @Test
  void rawModeOpUpdatesPowerLevelsAndEmitsModeObserved() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomStateClient.fetchRoomPowerLevels(
            "matrix", server, "secret-token", "!room:matrix.example.org"))
        .thenReturn(
            MatrixRoomStateClient.PowerLevelsResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/state/m.room.power_levels"),
                Map.of("users_default", 0L, "users", Map.of())));
    when(roomStateClient.updateRoomPowerLevels(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            argThat(
                map -> {
                  if (map == null) return false;
                  Object usersObj = map.get("users");
                  if (!(usersObj instanceof Map<?, ?> usersMap)) return false;
                  return Long.valueOf(50L).equals(usersMap.get("@bob:matrix.example.org"))
                      || Integer.valueOf(50).equals(usersMap.get("@bob:matrix.example.org"));
                })))
        .thenReturn(
            MatrixRoomStateClient.UpdateResult.updated(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/state/m.room.power_levels")));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendRaw("matrix", "MODE !room:matrix.example.org +o @bob:matrix.example.org")
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChannelModeObserved mode =
        assertInstanceOf(IrcEvent.ChannelModeObserved.class, events.values().get(3).event());
    assertEquals("!room:matrix.example.org", mode.channel());
    assertEquals("+o @bob:matrix.example.org", mode.details());
  }

  @Test
  void rawModeOwnerSetsHigherPowerLevel() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomStateClient.fetchRoomPowerLevels(
            "matrix", server, "secret-token", "!room:matrix.example.org"))
        .thenReturn(
            MatrixRoomStateClient.PowerLevelsResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/state/m.room.power_levels"),
                Map.of("users_default", 0L, "users", Map.of())));
    when(roomStateClient.updateRoomPowerLevels(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            argThat(
                map -> {
                  if (map == null) return false;
                  Object usersObj = map.get("users");
                  if (!(usersObj instanceof Map<?, ?> usersMap)) return false;
                  return Long.valueOf(100L).equals(usersMap.get("@bob:matrix.example.org"))
                      || Integer.valueOf(100).equals(usersMap.get("@bob:matrix.example.org"));
                })))
        .thenReturn(
            MatrixRoomStateClient.UpdateResult.updated(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/%21room%3Amatrix.example.org/state/m.room.power_levels")));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendRaw("matrix", "MODE !room:matrix.example.org +q @bob:matrix.example.org")
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChannelModeObserved mode =
        assertInstanceOf(IrcEvent.ChannelModeObserved.class, events.values().get(3).event());
    assertEquals("+q @bob:matrix.example.org", mode.details());
  }

  @Test
  void rawAwayDelegatesToSetAway() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(presenceClient.setAwayStatus(
            "matrix", server, "secret-token", "@alice:matrix.example.org", "Stepped out"))
        .thenReturn(
            MatrixPresenceClient.PresenceResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/presence/@alice:matrix.example.org/status"),
                true,
                "Stepped out"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendRaw("matrix", "AWAY :Stepped out").blockingAwait();
    events.awaitCount(4);

    verify(presenceClient, times(1))
        .setAwayStatus(
            "matrix", server, "secret-token", "@alice:matrix.example.org", "Stepped out");
    IrcEvent.AwayStatusChanged away =
        assertInstanceOf(IrcEvent.AwayStatusChanged.class, events.values().get(3).event());
    assertTrue(away.away());
    assertEquals("Stepped out", away.message());
  }

  @Test
  void rawWhowasDelegatesToWhois() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(userProfileClient.fetchProfile(
            "matrix", server, "secret-token", "@bob:matrix.example.org"))
        .thenReturn(
            MatrixUserProfileClient.ProfileResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/profile/@bob:matrix.example.org"),
                "@bob:matrix.example.org",
                "Bob",
                ""));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service.sendRaw("matrix", "WHOWAS @bob:matrix.example.org 3").blockingAwait();
    events.awaitCount(5);

    verify(userProfileClient, times(1))
        .fetchProfile("matrix", server, "secret-token", "@bob:matrix.example.org");
    IrcEvent.WhoisResult whois =
        assertInstanceOf(IrcEvent.WhoisResult.class, events.values().get(3).event());
    assertEquals("@bob:matrix.example.org", whois.nick());
    assertTrue(whois.lines().stream().anyMatch(line -> line.contains("display name: Bob")));
    assertInstanceOf(IrcEvent.WhoisProbeCompleted.class, events.values().get(4).event());
  }

  @Test
  void rawWhowasRejectsInvalidCount() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.sendRaw("matrix", "WHOWAS @bob:matrix.example.org nope").blockingAwait());

    assertEquals("WHOWAS count must be a positive integer", err.getMessage());
  }

  @Test
  void rawTagmsgDelegatesToSendTyping() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomTypingClient.setTyping(
            "matrix",
            server,
            "secret-token",
            "!room:matrix.example.org",
            "@alice:matrix.example.org",
            true,
            30_000))
        .thenReturn(
            MatrixRoomTypingClient.TypingResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/typing/@alice:matrix.example.org"),
                true));
    service.connect("matrix").blockingAwait();

    service.sendRaw("matrix", "@+typing=active TAGMSG !room:matrix.example.org").blockingAwait();

    verify(roomTypingClient, times(1))
        .setTyping(
            "matrix",
            server,
            "secret-token",
            "!room:matrix.example.org",
            "@alice:matrix.example.org",
            true,
            30_000);
  }

  @Test
  void rawTagmsgRequiresTypingTag() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.sendRaw("matrix", "TAGMSG !room:matrix.example.org").blockingAwait());

    assertEquals("TAGMSG requires +typing=<active|paused|done>", err.getMessage());
  }

  @Test
  void rawPrivmsgWithDraftEditTagDelegatesToMatrixEditSend() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomEdit(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("$msg-1"),
            eq("fixed text")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-e1"),
                "$edit-1"));
    service.connect("matrix").blockingAwait();

    service
        .sendRaw("matrix", "@+draft/edit=$msg-1 PRIVMSG !room:matrix.example.org :fixed text")
        .blockingAwait();

    verify(roomMessageSender, times(1))
        .sendRoomEdit(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("$msg-1"),
            eq("fixed text"));
  }

  @Test
  void rawPrivmsgWithDraftReplyTagDelegatesToMatrixReplySend() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomReply(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("$msg-1"),
            eq("reply text")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-r1"),
                "$reply-1"));
    service.connect("matrix").blockingAwait();

    service
        .sendRaw("matrix", "@+draft/reply=$msg-1 PRIVMSG !room:matrix.example.org :reply text")
        .blockingAwait();

    verify(roomMessageSender, times(1))
        .sendRoomReply(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("$msg-1"),
            eq("reply text"));
  }

  @Test
  void rawPrivmsgWithMatrixMediaTagsDelegatesToMatrixMediaSendAndEmitsEchoTags() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMediaMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("photo.png"),
            eq("m.image"),
            eq("mxc://matrix.example.org/media-1")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-m1"),
                "$media-1"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendRaw(
            "matrix",
            "@+matrix/msgtype=m.image;+matrix/media_url=mxc://matrix.example.org/media-1 PRIVMSG !room:matrix.example.org :photo.png")
        .blockingAwait();
    events.awaitCount(4);

    verify(roomMessageSender, times(1))
        .sendRoomMediaMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("photo.png"),
            eq("m.image"),
            eq("mxc://matrix.example.org/media-1"));
    verifyNoInteractions(mediaUploadClient);

    IrcEvent.ChannelMessage echoed =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("$media-1", echoed.messageId());
    assertEquals("photo.png", echoed.text());
    assertEquals("m.image", echoed.ircv3Tags().get("matrix.msgtype"));
    assertEquals("mxc://matrix.example.org/media-1", echoed.ircv3Tags().get("matrix.media_url"));
  }

  @Test
  void rawPrivmsgWithMatrixMediaTagsAllowsEmptyBodyAndUsesMediaUrlAsEchoText() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMediaMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq(""),
            eq("m.image"),
            eq("mxc://matrix.example.org/media-empty")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-m2"),
                "$media-2"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendRaw(
            "matrix",
            "@+matrix/msgtype=m.image;+matrix/media_url=mxc://matrix.example.org/media-empty PRIVMSG !room:matrix.example.org")
        .blockingAwait();
    events.awaitCount(4);

    IrcEvent.ChannelMessage echoed =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("mxc://matrix.example.org/media-empty", echoed.text());
    assertEquals("m.image", echoed.ircv3Tags().get("matrix.msgtype"));
    assertEquals(
        "mxc://matrix.example.org/media-empty", echoed.ircv3Tags().get("matrix.media_url"));
    verifyNoInteractions(mediaUploadClient);
  }

  @Test
  void rawPrivmsgWithMatrixUploadPathUploadsThenSendsMediaEvent() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(mediaUploadClient.uploadFile(
            eq("matrix"), eq(server), eq("secret-token"), eq("/tmp/photo.png")))
        .thenReturn(
            MatrixMediaUploadClient.UploadResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/media/v3/upload?filename=photo.png"),
                "mxc://matrix.example.org/upload-1",
                "photo.png",
                "image/png",
                42L));
    when(roomMessageSender.sendRoomMediaMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("photo.png"),
            eq("m.image"),
            eq("mxc://matrix.example.org/upload-1")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-m3"),
                "$media-3"));

    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    service
        .sendRaw(
            "matrix",
            "@+matrix/msgtype=m.image;+matrix/upload_path=/tmp/photo.png PRIVMSG !room:matrix.example.org :photo.png")
        .blockingAwait();
    events.awaitCount(4);

    verify(mediaUploadClient, times(1))
        .uploadFile(eq("matrix"), eq(server), eq("secret-token"), eq("/tmp/photo.png"));
    verify(roomMessageSender, times(1))
        .sendRoomMediaMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("photo.png"),
            eq("m.image"),
            eq("mxc://matrix.example.org/upload-1"));

    IrcEvent.ChannelMessage echoed =
        assertInstanceOf(IrcEvent.ChannelMessage.class, events.values().get(3).event());
    assertEquals("$media-3", echoed.messageId());
    assertEquals("photo.png", echoed.text());
    assertEquals("m.image", echoed.ircv3Tags().get("matrix.msgtype"));
    assertEquals("mxc://matrix.example.org/upload-1", echoed.ircv3Tags().get("matrix.media_url"));
  }

  @Test
  void rawPrivmsgWithMatrixUploadPathFailsWhenUploadFails() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(mediaUploadClient.uploadFile(
            eq("matrix"), eq(server), eq("secret-token"), eq("/tmp/missing.png")))
        .thenReturn(
            MatrixMediaUploadClient.UploadResult.failed(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/media/v3/upload?filename=missing.png"),
                "upload path is not a readable file"));
    service.connect("matrix").blockingAwait();

    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                service
                    .sendRaw(
                        "matrix",
                        "@+matrix/msgtype=m.image;+matrix/upload_path=/tmp/missing.png PRIVMSG !room:matrix.example.org :missing.png")
                    .blockingAwait());

    assertTrue(err.getMessage().contains("Matrix media upload failed"));
  }

  @Test
  void rawPrivmsgWithMatrixMediaTagsRequiresMediaUrlTag() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service
                    .sendRaw(
                        "matrix",
                        "@+matrix/msgtype=m.image PRIVMSG !room:matrix.example.org :photo.png")
                    .blockingAwait());

    assertEquals("matrix media url tag is blank", err.getMessage());
  }

  @Test
  void rawTagmsgDraftReactDelegatesToMatrixReactionSend() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomReaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("$msg-1"),
            eq(":+1:")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.reaction/txn-r1"),
                "$react-1"));
    service.connect("matrix").blockingAwait();

    service
        .sendRaw("matrix", "@+draft/react=:+1:;+draft/reply=$msg-1 TAGMSG !room:matrix.example.org")
        .blockingAwait();

    verify(roomMessageSender, times(1))
        .sendRoomReaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("$msg-1"),
            eq(":+1:"));
  }

  @Test
  void rawTagmsgDraftUnreactDelegatesToMatrixReactionRedaction() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "next-sync-token",
                List.of(),
                List.of(),
                List.of(),
                List.of(
                    new MatrixSyncClient.RoomReactionEvent(
                        "!room:matrix.example.org",
                        "@alice:matrix.example.org",
                        "$react-local",
                        "$msg-1",
                        ":+1:",
                        1710000001000L)),
                List.of(),
                Map.of(),
                List.of(),
                List.of()));
    when(roomMessageSender.sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$react-local"),
            anyString(),
            eq("")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/redact/$react-local/txn-u1"),
                "$unreact-1"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(4);

    service
        .sendRaw(
            "matrix", "@+draft/unreact=:+1:;+draft/reply=$msg-1 TAGMSG !room:matrix.example.org")
        .blockingAwait();

    verify(roomMessageSender, times(1))
        .sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$react-local"),
            anyString(),
            eq(""));
  }

  @Test
  void rawTagmsgDraftUnreactHydratesReactionIndexFromHistoryWhenSessionLookupMisses() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "anchor-sync-1",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("anchor-sync-1"),
            eq(200)))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages"),
                "anchor-sync-2",
                List.of(),
                List.of(
                    new MatrixRoomHistoryClient.RoomReactionEvent(
                        "@alice:matrix.example.org",
                        "$react-history",
                        "$msg-1",
                        ":+1:",
                        1710000002000L)),
                List.of()));
    when(roomMessageSender.sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$react-history"),
            anyString(),
            eq("")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/redact/$react-history/txn-u2"),
                "$unreact-history"));
    service.connect("matrix").blockingAwait();

    service
        .sendRaw(
            "matrix", "@+draft/unreact=:+1:;+draft/reply=$msg-1 TAGMSG !room:matrix.example.org")
        .blockingAwait();

    verify(roomMessageSender, times(1))
        .sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$react-history"),
            anyString(),
            eq(""));
  }

  @Test
  void rawTagmsgDraftUnreactHistoryFallbackIgnoresPreviouslyRedactedReactionIds() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "anchor-sync-1",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("anchor-sync-1"),
            eq(200)))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages"),
                "anchor-sync-2",
                List.of(),
                List.of(),
                List.of(
                    new MatrixRoomHistoryClient.RoomRedactionEvent(
                        "@alice:matrix.example.org",
                        "$redact-1",
                        "$react-stale",
                        "cleanup",
                        1710000001000L))));
    when(roomHistoryClient.fetchMessagesBefore(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("anchor-sync-2"),
            eq(200)))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages"),
                "",
                List.of(),
                List.of(
                    new MatrixRoomHistoryClient.RoomReactionEvent(
                        "@alice:matrix.example.org",
                        "$react-stale",
                        "$msg-1",
                        ":+1:",
                        1710000000000L),
                    new MatrixRoomHistoryClient.RoomReactionEvent(
                        "@alice:matrix.example.org",
                        "$react-active",
                        "$msg-1",
                        ":+1:",
                        1710000000001L)),
                List.of()));
    when(roomMessageSender.sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$react-active"),
            anyString(),
            eq("")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/redact/$react-active/txn-u3"),
                "$unreact-active"));
    service.connect("matrix").blockingAwait();

    service
        .sendRaw(
            "matrix", "@+draft/unreact=:+1:;+draft/reply=$msg-1 TAGMSG !room:matrix.example.org")
        .blockingAwait();

    verify(roomMessageSender, times(1))
        .sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$react-active"),
            anyString(),
            eq(""));
  }

  @Test
  void rawTagmsgDraftUnreactFallsBackToTimestampAnchoredForwardHistoryScan() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "anchor-sync-1",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$msg-1",
                        "m.text",
                        "target",
                        1710000000000L)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of(),
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("anchor-sync-1"),
            eq(200)))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages"),
                "",
                List.of(),
                List.of(),
                List.of()),
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages"),
                "cursor-near-target",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org", "$msg-1", "m.text", "target", 1710000000000L)),
                List.of(),
                List.of()));
    when(roomHistoryClient.fetchMessagesAfter(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("cursor-near-target"),
            eq(200)))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages"),
                "",
                List.of(),
                List.of(
                    new MatrixRoomHistoryClient.RoomReactionEvent(
                        "@alice:matrix.example.org",
                        "$react-forward",
                        "$msg-1",
                        ":+1:",
                        1710000002000L)),
                List.of()));
    when(roomMessageSender.sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$react-forward"),
            anyString(),
            eq("")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/redact/$react-forward/txn-u4"),
                "$unreact-forward"));
    service.connect("matrix").blockingAwait();

    service
        .sendRaw(
            "matrix", "@+draft/unreact=:+1:;+draft/reply=$msg-1 TAGMSG !room:matrix.example.org")
        .blockingAwait();

    verify(roomMessageSender, times(1))
        .sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$react-forward"),
            anyString(),
            eq(""));
  }

  @Test
  void rawRedactDelegatesToMatrixRoomRedactionSend() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$msg-1"),
            anyString(),
            eq("cleanup")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/redact/$msg-1/txn-rd1"),
                "$redact-1"));
    service.connect("matrix").blockingAwait();

    service.sendRaw("matrix", "REDACT !room:matrix.example.org $msg-1 :cleanup").blockingAwait();

    verify(roomMessageSender, times(1))
        .sendRoomRedaction(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            eq("$msg-1"),
            anyString(),
            eq("cleanup"));
  }

  @Test
  void rawTagmsgDraftReactRequiresDraftReplyTag() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service
                    .sendRaw("matrix", "@+draft/react=:+1: TAGMSG !room:matrix.example.org")
                    .blockingAwait());

    assertEquals("TAGMSG draft reactions require +draft/reply=<msgid>", err.getMessage());
  }

  @Test
  void rawMarkReadDelegatesToSendReadMarker() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("hello")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-1"),
                "$event-1"));
    when(readMarkerClient.updateReadMarker(
            "matrix", server, "secret-token", "!room:matrix.example.org", "$event-1"))
        .thenReturn(
            MatrixReadMarkerClient.ReadMarkerResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/read_markers"),
                "!room:matrix.example.org",
                "$event-1"));
    service.connect("matrix").blockingAwait();
    service.sendToChannel("matrix", "!room:matrix.example.org", "hello").blockingAwait();

    service
        .sendRaw("matrix", "MARKREAD !room:matrix.example.org timestamp=2026-02-16T12:34:56.000Z")
        .blockingAwait();

    verify(readMarkerClient, times(1))
        .updateReadMarker("matrix", server, "secret-token", "!room:matrix.example.org", "$event-1");
  }

  @Test
  void rawMarkReadMsgidSelectorDelegatesToReadMarkerForMessageEventId() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("seed")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-seed"),
                "$seed-1"));
    when(readMarkerClient.updateReadMarker(
            "matrix", server, "secret-token", "!room:matrix.example.org", "$seed-1"))
        .thenReturn(
            MatrixReadMarkerClient.ReadMarkerResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/read_markers"),
                "!room:matrix.example.org",
                "$seed-1"));
    service.connect("matrix").blockingAwait();
    service.sendToChannel("matrix", "!room:matrix.example.org", "seed").blockingAwait();

    service.sendRaw("matrix", "MARKREAD !room:matrix.example.org msgid=$seed-1").blockingAwait();

    verify(readMarkerClient, times(1))
        .updateReadMarker("matrix", server, "secret-token", "!room:matrix.example.org", "$seed-1");
  }

  @Test
  void rawMarkReadMsgidSelectorFailsWhenMessageCannotBeResolved() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    BackendNotAvailableException err =
        assertThrows(
            BackendNotAvailableException.class,
            () ->
                service
                    .sendRaw("matrix", "MARKREAD !room:matrix.example.org msgid=$missing")
                    .blockingAwait());

    assertEquals("markread", err.operation());
    assertTrue(err.getMessage().contains("cannot resolve msgid selector"));
  }

  @Test
  void rawMarkReadRejectsUnsupportedSelectorType() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service
                    .sendRaw("matrix", "MARKREAD !room:matrix.example.org selector=abc")
                    .blockingAwait());

    assertEquals("MARKREAD selector must be timestamp=... or msgid=...", err.getMessage());
  }

  @Test
  void rawChatHistoryBeforeDelegatesToSelectorRequest() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 2))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=2"),
                "s-next",
                List.of()));
    service.connect("matrix").blockingAwait();

    service
        .sendRaw(
            "matrix",
            "CHATHISTORY BEFORE !room:matrix.example.org timestamp=2024-03-09T16:00:03Z 2")
        .blockingAwait();

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 2);
  }

  @Test
  void rawChatHistoryBeforeMsgidUsesIndexedAnchor() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(roomMessageSender.sendRoomMessage(
            eq("matrix"),
            eq(server),
            eq("secret-token"),
            eq("!room:matrix.example.org"),
            anyString(),
            eq("seed")))
        .thenReturn(
            MatrixRoomMessageSender.SendResult.accepted(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-seed"),
                "$h1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 2))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=2"),
                "s-next",
                List.of()));
    service.connect("matrix").blockingAwait();
    service.sendToChannel("matrix", "!room:matrix.example.org", "seed").blockingAwait();

    assertDoesNotThrow(
        () ->
            service
                .sendRaw("matrix", "CHATHISTORY BEFORE !room:matrix.example.org msgid=$h1 2")
                .blockingAwait());

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 2);
  }

  @Test
  void rawChatHistoryLatestWithLimitOnlyUsesWildcardSelector() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 3))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=3"),
                "s-next",
                List.of()));
    service.connect("matrix").blockingAwait();

    service.sendRaw("matrix", "CHATHISTORY LATEST !room:matrix.example.org 3").blockingAwait();

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 3);
  }

  @Test
  void rawChatHistoryBetweenDelegatesToHistoryFetch() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 200))
        .thenReturn(null);
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 10))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=10"),
                "s-forward",
                List.of()));
    service.connect("matrix").blockingAwait();

    assertDoesNotThrow(
        () ->
            service
                .sendRaw(
                    "matrix",
                    "CHATHISTORY BETWEEN !room:matrix.example.org "
                        + "timestamp=2024-03-09T16:00:03Z timestamp=2024-03-09T16:05:03Z 10")
                .blockingAwait());

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200);
    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 200);
    verify(roomHistoryClient, times(1))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 10);
  }

  @Test
  void rawChatHistoryAroundDelegatesToBidirectionalHistoryFetch() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of()));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-center",
                        "m.text",
                        "center",
                        Instant.parse("2024-03-09T16:00:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=b&limit=40"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-older",
                        "m.text",
                        "older",
                        Instant.parse("2024-03-09T15:59:03Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=40"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-newer",
                        "m.text",
                        "newer",
                        Instant.parse("2024-03-09T16:01:03Z").toEpochMilli()))));
    service.connect("matrix").blockingAwait();

    assertDoesNotThrow(
        () ->
            service
                .sendRaw(
                    "matrix",
                    "CHATHISTORY AROUND !room:matrix.example.org timestamp=2024-03-09T16:00:03Z 10")
                .blockingAwait());

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200);
    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40);
    verify(roomHistoryClient, times(1))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 40);
  }

  @Test
  void rawChatHistoryBetweenMsgidSelectorsUseIndexedAnchors() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    when(syncClient.sync(eq("matrix"), eq(server), eq("secret-token"), anyString(), eq(0)))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/sync?timeout=0"),
                "s-anchor",
                List.of(
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$h-start",
                        "m.text",
                        "start",
                        Instant.parse("2024-03-09T15:30:00Z").toEpochMilli()),
                    new MatrixSyncClient.RoomTimelineEvent(
                        "!room:matrix.example.org",
                        "@bob:matrix.example.org",
                        "$h-end",
                        "m.text",
                        "end",
                        Instant.parse("2024-03-09T16:00:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-anchor&dir=b&limit=200"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-start",
                        "m.text",
                        "start",
                        Instant.parse("2024-03-09T15:30:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$inside",
                        "m.text",
                        "inside",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-end",
                        "m.text",
                        "end",
                        Instant.parse("2024-03-09T16:00:00Z").toEpochMilli()))));
    when(roomHistoryClient.fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 10))
        .thenReturn(
            MatrixRoomHistoryClient.HistoryResult.success(
                URI.create(
                    "https://matrix.example.org:8448/_matrix/client/v3/rooms/!room:matrix.example.org/messages?from=s-next&dir=f&limit=10"),
                "s-next",
                List.of(
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$inside",
                        "m.text",
                        "inside",
                        Instant.parse("2024-03-09T15:45:00Z").toEpochMilli()),
                    new MatrixRoomHistoryClient.RoomHistoryEvent(
                        "@bob:matrix.example.org",
                        "$h-end",
                        "m.text",
                        "end",
                        Instant.parse("2024-03-09T16:00:00Z").toEpochMilli()))));
    service.connect("matrix").blockingAwait();

    assertDoesNotThrow(
        () ->
            service
                .sendRaw(
                    "matrix",
                    "CHATHISTORY BETWEEN !room:matrix.example.org msgid=$h-start msgid=$h-end 10")
                .blockingAwait());

    verify(roomHistoryClient, times(1))
        .fetchMessagesBefore(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-anchor", 200);
    verify(roomHistoryClient, times(1))
        .fetchMessagesAfter(
            "matrix", server, "secret-token", "!room:matrix.example.org", "s-next", 10);
  }

  @Test
  void rawChatHistoryRejectsUnknownMode() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    service.connect("matrix").blockingAwait();

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service
                    .sendRaw("matrix", "CHATHISTORY FUTURE !room:matrix.example.org")
                    .blockingAwait());

    assertEquals("CHATHISTORY mode must be BEFORE, LATEST, BETWEEN, or AROUND", err.getMessage());
  }

  @Test
  void disconnectClearsStoredAvailabilityReason() {
    IrcProperties.Server server =
        server("matrix", "matrix.example.org", 8448, true, "secret-token");
    when(serverCatalog.require("matrix")).thenReturn(server);
    when(homeserverProbe.probe("matrix", server))
        .thenReturn(
            MatrixHomeserverProbe.ProbeResult.reachable(
                URI.create("https://matrix.example.org:8448/_matrix/client/versions"), 1));
    when(homeserverProbe.whoami("matrix", server, "secret-token"))
        .thenReturn(
            MatrixHomeserverProbe.WhoamiResult.authenticated(
                URI.create("https://matrix.example.org:8448/_matrix/client/v3/account/whoami"),
                "@alice:matrix.example.org",
                "DEV1"));
    var events = service.events().test();
    service.connect("matrix").blockingAwait();
    events.awaitCount(3);

    assertDoesNotThrow(() -> service.disconnect("matrix", "bye").blockingAwait());
    events.awaitCount(4);

    IrcEvent.Disconnected disconnected =
        assertInstanceOf(IrcEvent.Disconnected.class, events.values().get(3).event());
    assertEquals("bye", disconnected.reason());
    assertEquals(Optional.empty(), service.currentNick("matrix"));
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
    assertDoesNotThrow(() -> service.disconnect("matrix", "later").blockingAwait());
  }

  @Test
  void disconnectWithoutSessionIsNoop() {
    assertDoesNotThrow(() -> service.disconnect("matrix").blockingAwait());
    assertEquals("not connected", service.backendAvailabilityReason("matrix"));
    assertFalse(service.currentNick("matrix").isPresent());
  }

  private static IrcProperties.Server server(
      String id, String host, int port, boolean tls, String token) {
    return new IrcProperties.Server(
        id,
        host,
        port,
        tls,
        token,
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

  private static IrcProperties.Server serverWithSaslToken(
      String id, String host, int port, boolean tls, String saslToken) {
    return new IrcProperties.Server(
        id,
        host,
        port,
        tls,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        new IrcProperties.Server.Sasl(true, "alice", saslToken, "PLAIN", true),
        null,
        List.of(),
        List.of(),
        null,
        IrcProperties.Server.Backend.MATRIX);
  }
}
