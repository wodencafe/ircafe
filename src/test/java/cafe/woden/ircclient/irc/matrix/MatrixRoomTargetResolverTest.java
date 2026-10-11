package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MatrixRoomTargetResolverTest {
  private static final String SID = "matrix";
  private static final String ROOM = "!room:example.org";
  private static final String ALIAS = "#room:example.org";
  private static final String PEER = "@peer:example.org";
  private static final String TOKEN = "access-token";
  private static final URI ENDPOINT = URI.create("https://example.org/_matrix/client/v3/rooms");
  private final IrcProperties.Server server = mock(IrcProperties.Server.class);
  private final MatrixRoomDirectoryClient directory = mock(MatrixRoomDirectoryClient.class);
  private final MatrixDirectRoomResolver directRooms = mock(MatrixDirectRoomResolver.class);
  private final MatrixSyncClient sync = mock(MatrixSyncClient.class);
  private final MatrixSession session = new MatrixSession("@me:example.org", TOKEN);
  private String host = "example.org";
  private final MatrixRoomTargetResolver resolver =
      new MatrixRoomTargetResolver(
          directory,
          directRooms,
          sync,
          observed -> {
            assertSame(server, observed);
            return host;
          });

  @ParameterizedTest
  @CsvSource({
    "!room:example.org, !room:example.org",
    "#room:elsewhere.org, #room:elsewhere.org",
    "#room, #room:example.org"
  })
  void normalizesJoinTargetsWithoutRemoteRequests(String target, String expected) {
    assertEquals(expected, resolver.normalizeJoinTarget(" " + target + " ", server));
    verifyNoInteractions(directory, directRooms, sync);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "room", "@peer:example.org", "!room:", "#room:"})
  void rejectsBlankOrInvalidJoinTarget(String target) {
    var error =
        assertThrows(
            IllegalArgumentException.class, () -> resolver.normalizeJoinTarget(target, server));
    assertEquals(
        target.isBlank() ? "channel is blank" : "target is not a Matrix room id or alias",
        error.getMessage());
    verifyNoInteractions(directory, directRooms, sync);
  }

  @Test
  void localAliasRequiresHomeserverHost() {
    host = "";
    var error =
        assertThrows(
            IllegalArgumentException.class, () -> resolver.normalizeJoinTarget("#room", server));
    assertEquals("target is not a Matrix room id or alias", error.getMessage());
    verifyNoInteractions(directory, directRooms, sync);
  }

  @Test
  void roomIdResolutionNeedsNeitherSessionNorDirectoryLookup() {
    assertEquals(ROOM, resolver.resolveAliasOrRoomId(SID, " " + ROOM + " ", server, null));
    verifyNoInteractions(directory, directRooms, sync);
  }

  @Test
  void localAliasUsesSessionCacheBeforeDirectoryLookup() {
    session.rememberJoinedAlias(ALIAS, ROOM);
    assertEquals(ROOM, resolver.resolveAliasOrRoomId(SID, " #room ", server, session));
    verifyNoInteractions(directory, directRooms, sync);
  }

  @Test
  void aliasLookupUsesCallerThreadAndRemembersBothDirectionsBeforeReturning() {
    Thread caller = Thread.currentThread();
    when(directory.resolveRoomAlias(SID, server, TOKEN, ALIAS))
        .thenAnswer(
            ignored -> {
              assertSame(caller, Thread.currentThread());
              return MatrixRoomDirectoryClient.ResolveResult.resolved(ENDPOINT, " " + ROOM + " ");
            });

    assertEquals(ROOM, resolver.resolveAliasOrRoomId(SID, " #room ", server, session));
    assertEquals(ROOM, session.roomForAlias(ALIAS));
    assertEquals(ALIAS, session.targetForRoom(ROOM));
    assertEquals(ROOM, resolver.resolveAliasOrRoomId(SID, ALIAS, server, session));
    verify(directory).resolveRoomAlias(SID, server, TOKEN, ALIAS);
    verifyNoMoreInteractions(directory);
    verifyNoInteractions(directRooms, sync);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedOrInvalidAliasLookupDoesNotPopulateSessionCache(boolean invalid) {
    when(directory.resolveRoomAlias(SID, server, TOKEN, ALIAS))
        .thenReturn(
            invalid
                ? MatrixRoomDirectoryClient.ResolveResult.resolved(ENDPOINT, "not-a-room")
                : MatrixRoomDirectoryClient.ResolveResult.failed(ENDPOINT, "denied"));
    var error =
        assertThrows(
            IllegalStateException.class,
            () -> resolver.resolveAliasOrRoomId(SID, ALIAS, server, session));
    assertEquals(
        invalid
            ? "Matrix room alias lookup returned invalid room id"
            : "Matrix room alias lookup failed at " + ENDPOINT + ": denied",
        error.getMessage());
    assertEquals("", session.roomForAlias(ALIAS));
  }

  @Test
  void sendingToPeerResolvesAndCachesDirectRoom() {
    when(directRooms.resolveDirectRoom(SID, server, TOKEN, PEER))
        .thenReturn(MatrixDirectRoomResolver.ResolveResult.resolved(ENDPOINT, " " + ROOM + " "));
    assertEquals(ROOM, resolver.resolveDirectRoomId(SID, server, session, " " + PEER + " "));
    assertEquals(PEER, session.targetForRoom(ROOM));
    assertEquals(ROOM, resolver.resolveDirectRoomId(SID, server, session, PEER));
    verify(directRooms).resolveDirectRoom(SID, server, TOKEN, PEER);
    verifyNoMoreInteractions(directRooms);
    verifyNoInteractions(directory, sync);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedOrInvalidDirectRoomResolutionDoesNotPopulateSessionCache(boolean invalid) {
    when(directRooms.resolveDirectRoom(SID, server, TOKEN, PEER))
        .thenReturn(
            invalid
                ? MatrixDirectRoomResolver.ResolveResult.resolved(ENDPOINT, "!room:")
                : MatrixDirectRoomResolver.ResolveResult.failed(ENDPOINT, "denied"));
    var error =
        assertThrows(
            IllegalStateException.class,
            () -> resolver.resolveDirectRoomId(SID, server, session, PEER));
    assertEquals(
        invalid
            ? "Matrix direct room resolution returned invalid room id"
            : "Matrix direct room resolution failed at " + ENDPOINT + ": denied",
        error.getMessage());
    assertEquals("", session.roomForPeer(PEER));
  }

  @Test
  void historyForKnownPeerUsesCacheWithoutSyncOrRoomCreation() {
    session.rememberDirectRoom(PEER, ROOM);
    assertEquals(ROOM, resolver.resolveHistoryRoomId(SID, " " + PEER + " ", server, session));
    verifyNoInteractions(directory, directRooms, sync);
  }

  @ParameterizedTest
  @ValueSource(strings = {"next", " "})
  void historyForUnknownPeerRefreshesMappingsAndOnlyNonblankSyncCursor(String next) {
    session.sinceToken.set("prior");
    when(sync.sync(SID, server, TOKEN, "", 0))
        .thenReturn(
            MatrixSyncClient.SyncResult.success(
                ENDPOINT,
                next,
                List.of(),
                Map.of(ROOM, PEER, "!other:example.org", "@other:example.org")));
    assertEquals(ROOM, resolver.resolveHistoryRoomId(SID, PEER, server, session));
    assertEquals(next.isBlank() ? "prior" : "next", session.sinceToken.get());
    assertEquals("!other:example.org", session.roomForPeer("@other:example.org"));
    verify(sync).sync(SID, server, TOKEN, "", 0);
    verifyNoInteractions(directory, directRooms);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void missingFailedOrUnmappedSyncLeavesHistoryPeerUnknownWithoutRoomCreation(int outcome) {
    session.sinceToken.set("prior");
    when(sync.sync(SID, server, TOKEN, "", 0))
        .thenReturn(
            switch (outcome) {
              case 0 -> null;
              case 1 -> MatrixSyncClient.SyncResult.failed(ENDPOINT, "offline");
              default -> MatrixSyncClient.SyncResult.success(ENDPOINT, "next", List.of());
            });
    var error =
        assertThrows(
            IllegalStateException.class,
            () -> resolver.resolveHistoryRoomId(SID, PEER, server, session));
    assertEquals("Matrix direct room is unknown for target", error.getMessage());
    assertEquals(outcome == 2 ? "next" : "prior", session.sinceToken.get());
    assertEquals("", session.roomForPeer(PEER));
    verifyNoInteractions(directory, directRooms);
  }

  @Test
  void historyPeerWithoutSessionDoesNotFetch() {
    var error =
        assertThrows(
            IllegalStateException.class,
            () -> resolver.resolveHistoryRoomId(SID, PEER, server, null));
    assertEquals("Matrix direct room is unknown for target", error.getMessage());
    verifyNoInteractions(directory, directRooms, sync);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void realtimeSignalsUseKnownTargetsAndNeverFetchKnownMappings(boolean lookupAllowed) {
    session.rememberDirectRoom(PEER, ROOM);
    session.rememberJoinedAlias(ALIAS, ROOM);
    for (String target : List.of(ROOM, PEER, ALIAS, " #room ")) {
      assertEquals(
          ROOM, resolver.resolveRealtimeSignalRoomId(SID, target, server, session, lookupAllowed));
    }
    verifyNoInteractions(directory, directRooms, sync);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void realtimeSignalForUnknownPeerDoesNotRefreshOrCreateRoom(boolean lookupAllowed) {
    assertEquals(
        "", resolver.resolveRealtimeSignalRoomId(SID, PEER, server, session, lookupAllowed));
    verifyNoInteractions(directory, directRooms, sync);
  }

  @Test
  void typingPolicyLeavesUnknownAliasUnresolvedWithoutLookup() {
    assertEquals("", resolver.resolveRealtimeSignalRoomId(SID, "#room", server, session, false));
    assertEquals("", session.roomForAlias(ALIAS));
    verifyNoInteractions(directory, directRooms, sync);
  }

  @Test
  void readMarkerPolicyAllowsDirectoryLookupForUnknownAlias() {
    when(directory.resolveRoomAlias(SID, server, TOKEN, ALIAS))
        .thenReturn(MatrixRoomDirectoryClient.ResolveResult.resolved(ENDPOINT, ROOM));
    assertEquals(ROOM, resolver.resolveRealtimeSignalRoomId(SID, "#room", server, session, true));
    assertEquals(ROOM, session.roomForAlias(ALIAS));
    verify(directory).resolveRoomAlias(SID, server, TOKEN, ALIAS);
    verifyNoInteractions(directRooms, sync);
  }

  @Test
  void mutationRequiresKnownDirectRoomWithoutRefreshOrCreation() {
    var error =
        assertThrows(
            IllegalStateException.class,
            () -> resolver.resolveMutationRoomId(SID, PEER, server, session));
    assertEquals("Matrix direct room is unknown for target", error.getMessage());
    session.rememberDirectRoom(PEER, ROOM);
    assertEquals(ROOM, resolver.resolveMutationRoomId(SID, " " + PEER + " ", server, session));
    verifyNoInteractions(directory, directRooms, sync);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void historyAndMutationRoomAliasesUseDirectoryResolution(boolean history) {
    when(directory.resolveRoomAlias(SID, server, TOKEN, ALIAS))
        .thenReturn(MatrixRoomDirectoryClient.ResolveResult.resolved(ENDPOINT, ROOM));
    String room =
        history
            ? resolver.resolveHistoryRoomId(SID, "#room", server, session)
            : resolver.resolveMutationRoomId(SID, "#room", server, session);
    assertEquals(ROOM, room);
    verify(directory).resolveRoomAlias(SID, server, TOKEN, ALIAS);
    verifyNoInteractions(directRooms, sync);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void mutationAndRealtimeSignalsRejectBlankTargetsBeforeLookup(int operation) {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              if (operation == 0) {
                resolver.resolveMutationRoomId(SID, " ", server, session);
              } else {
                resolver.resolveRealtimeSignalRoomId(SID, " ", server, session, operation == 2);
              }
            });
    assertEquals("target is blank", error.getMessage());
    verifyNoInteractions(directory, directRooms, sync);
  }

  @Test
  void resolverUsesTheSuppliedSessionsMappingsAndSessionCleanupRemovesThem() {
    var other = new MatrixSession("@other:example.org", "other-token");
    session.rememberJoinedAlias(ALIAS, ROOM);
    other.rememberJoinedAlias(ALIAS, "!other:example.org");
    assertEquals(ROOM, resolver.resolveAliasOrRoomId(SID, ALIAS, server, session));
    assertEquals("!other:example.org", resolver.resolveAliasOrRoomId(SID, ALIAS, server, other));
    session.forgetJoinedRoom(ROOM);
    assertEquals("", resolver.resolveRealtimeSignalRoomId(SID, ALIAS, server, session, false));
    assertEquals("!other:example.org", resolver.resolveAliasOrRoomId(SID, ALIAS, server, other));
    verifyNoInteractions(directory, directRooms, sync);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void remoteFailuresPropagateWithoutRememberingMappings(int operation) {
    var failure = new IllegalStateException("transport failed");
    if (operation == 0) {
      when(directory.resolveRoomAlias(SID, server, TOKEN, ALIAS)).thenThrow(failure);
    } else if (operation == 1) {
      when(directRooms.resolveDirectRoom(SID, server, TOKEN, PEER)).thenThrow(failure);
    } else {
      when(sync.sync(SID, server, TOKEN, "", 0)).thenThrow(failure);
    }
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () -> {
              switch (operation) {
                case 0 -> resolver.resolveAliasOrRoomId(SID, ALIAS, server, session);
                case 1 -> resolver.resolveDirectRoomId(SID, server, session, PEER);
                default -> resolver.resolveHistoryRoomId(SID, PEER, server, session);
              }
            }));
    assertEquals("", session.roomForAlias(ALIAS));
    assertEquals("", session.roomForPeer(PEER));
  }
}
