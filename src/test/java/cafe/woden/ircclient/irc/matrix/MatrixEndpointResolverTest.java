package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.config.IrcProperties;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatrixEndpointResolverTest {

  @Test
  void versionsUriFromHostAndPortUsesServerTls() {
    IrcProperties.Server server = server("matrix", "matrix.example.org", 8448, true);

    URI uri = MatrixEndpointResolver.versionsUri(server);

    assertEquals("https://matrix.example.org:8448/_matrix/client/versions", uri.toString());
  }

  @Test
  void whoamiUriUsesSameClientApiPrefix() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.whoamiUri(server);

    assertEquals("https://example.org:443/matrix/_matrix/client/v3/account/whoami", uri.toString());
  }

  @Test
  void userProfileUriBuildsProfilePath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.userProfileUri(server, "@alice:example.org");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/profile/@alice:example.org",
        uri.toString());
  }

  @Test
  void userDisplayNameUriBuildsDisplayNamePath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.userDisplayNameUri(server, "@alice:example.org");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/profile/@alice:example.org/displayname",
        uri.toString());
  }

  @Test
  void roomSendMessageUriIncludesRoomIdAndTxnInPath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri =
        MatrixEndpointResolver.roomSendMessageUri(server, "!room:matrix.example.org", "txn-1");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.room.message/txn-1",
        uri.toString());
  }

  @Test
  void roomSendEventUriSupportsCustomEventTypePath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri =
        MatrixEndpointResolver.roomSendEventUri(
            server, "!room:matrix.example.org", "m.reaction", "txn-r1");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/rooms/!room:matrix.example.org/send/m.reaction/txn-r1",
        uri.toString());
  }

  @Test
  void roomRedactEventUriBuildsRedactionPath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri =
        MatrixEndpointResolver.roomRedactEventUri(
            server, "!room:matrix.example.org", "$event-1", "txn-x1");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/rooms/!room:matrix.example.org/redact/$event-1/txn-x1",
        uri.toString());
  }

  @Test
  void syncUriIncludesTimeoutAndSinceQuery() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.syncUri(server, "s72595_4483_1934", 1500);

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/sync?timeout=1500&since=s72595_4483_1934",
        uri.toString());
  }

  @Test
  void createRoomUriUsesSameClientApiPrefix() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.createRoomUri(server);

    assertEquals("https://example.org:443/matrix/_matrix/client/v3/createRoom", uri.toString());
  }

  @Test
  void joinRoomUriEncodesRoomAliasInPath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.joinRoomUri(server, "#ircafe:example.org");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/join/%23ircafe:example.org",
        uri.toString());
  }

  @Test
  void leaveRoomUriBuildsRoomsLeavePath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.leaveRoomUri(server, "!room:example.org");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/rooms/!room:example.org/leave",
        uri.toString());
  }

  @Test
  void roomJoinedMembersUriBuildsRoomsJoinedMembersPath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.roomJoinedMembersUri(server, "!room:example.org");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/rooms/!room:example.org/joined_members",
        uri.toString());
  }

  @Test
  void userPresenceStatusUriBuildsPresenceStatusPath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.userPresenceStatusUri(server, "@alice:example.org");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/presence/@alice:example.org/status",
        uri.toString());
  }

  @Test
  void roomTypingUriBuildsTypingPath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri =
        MatrixEndpointResolver.roomTypingUri(server, "!room:example.org", "@alice:example.org");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/rooms/!room:example.org/typing/@alice:example.org",
        uri.toString());
  }

  @Test
  void roomReadMarkersUriBuildsReadMarkersPath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.roomReadMarkersUri(server, "!room:example.org");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/rooms/!room:example.org/read_markers",
        uri.toString());
  }

  @Test
  void mediaUploadUriBuildsMediaPathAndFilenameQuery() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.mediaUploadUri(server, "photo one.png");

    assertEquals(
        "https://example.org:443/matrix/_matrix/media/v3/upload?filename=photo%20one.png",
        uri.toString());
  }

  @Test
  void mediaUploadUriOmitsFilenameQueryWhenBlank() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.mediaUploadUri(server, " ");

    assertEquals("https://example.org:443/matrix/_matrix/media/v3/upload", uri.toString());
  }

  @Test
  void roomAliasDirectoryUriBuildsDirectoryPath() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.roomAliasDirectoryUri(server, "#ircafe:example.org");

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/directory/room/%23ircafe:example.org",
        uri.toString());
  }

  @Test
  void roomMessagesUriBuildsBackPaginationQuery() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri =
        MatrixEndpointResolver.roomMessagesUri(
            server, "!room:example.org", "s72595_4483_1934", "", 50);

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/rooms/!room:example.org/messages?from=s72595_4483_1934&dir=b&limit=50",
        uri.toString());
  }

  @Test
  void roomMessagesUriBuildsForwardPaginationQuery() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri =
        MatrixEndpointResolver.roomMessagesUri(
            server, "!room:example.org", "s72595_4483_1934", "", "f", 20);

    assertEquals(
        "https://example.org:443/matrix/_matrix/client/v3/rooms/!room:example.org/messages?from=s72595_4483_1934&dir=f&limit=20",
        uri.toString());
  }

  @Test
  void roomMessagesUriRejectsInvalidDirection() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                MatrixEndpointResolver.roomMessagesUri(
                    server, "!room:example.org", "s72595_4483_1934", "", "x", 20));

    assertEquals("direction must be 'b' or 'f'", err.getMessage());
  }

  @Test
  void versionsUriFromAbsoluteBaseUrlKeepsPathPrefix() {
    IrcProperties.Server server = server("matrix", "https://example.org/matrix", 0, true);

    URI uri = MatrixEndpointResolver.versionsUri(server);

    assertEquals("https://example.org:443/matrix/_matrix/client/versions", uri.toString());
  }

  @Test
  void versionsUriFromHostWithPathUsesConfiguredPort() {
    IrcProperties.Server server = server("matrix", "example.org/custom/base", 8008, false);

    URI uri = MatrixEndpointResolver.versionsUri(server);

    assertEquals("http://example.org:8008/custom/base/_matrix/client/versions", uri.toString());
  }

  @Test
  void blankHostIsRejected() {
    IrcProperties.Server server = server("matrix", "  ", 8448, true);

    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class, () -> MatrixEndpointResolver.versionsUri(server));

    assertEquals("host is blank", err.getMessage());
  }

  private static IrcProperties.Server server(String id, String host, int port, boolean tls) {
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
}
