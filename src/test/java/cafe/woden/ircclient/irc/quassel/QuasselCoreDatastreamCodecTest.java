package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuasselCoreDatastreamCodecTest {

  @Test
  void writeAndReadHandshakeFrameRoundTripsClientInitFields() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();

    LinkedHashMap<String, Object> outbound = new LinkedHashMap<>();
    outbound.put("MsgType", "ClientInit");
    outbound.put("ClientVersion", "IRCafe");
    outbound.put("ClientDate", "1700000000");
    outbound.put("Features", 0L);
    outbound.put("FeatureList", List.of("EchoMessage", "ExtendedFeatures"));

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    codec.writeHandshakeMessage(bytes, outbound);

    QuasselCoreDatastreamCodec.HandshakeMessage decoded =
        codec.readHandshakeMessage(new ByteArrayInputStream(bytes.toByteArray()));

    assertEquals("ClientInit", decoded.messageType());
    assertEquals("IRCafe", decoded.fields().get("ClientVersion"));
    assertEquals("1700000000", decoded.fields().get("ClientDate"));
    assertEquals(0L, decoded.fields().get("Features"));
    assertEquals(List.of("EchoMessage", "ExtendedFeatures"), decoded.fields().get("FeatureList"));
  }

  @Test
  void decodesSessionInitWithNetworkIdsAndBufferInfos() throws Exception {
    QuasselCoreDatastreamCodec.BufferInfoValue statusBuffer =
        new QuasselCoreDatastreamCodec.BufferInfoValue(1, 7, 0x01, 0, "freenode");

    LinkedHashMap<String, Object> outbound = new LinkedHashMap<>();
    outbound.put("MsgType", "SessionInit");
    outbound.put(
        "SessionState",
        new LinkedHashMap<>(
            Map.of(
                "BufferInfos", List.of(statusBuffer),
                "NetworkIds", List.of(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", 7)),
                "Identities", List.of())));

    QuasselCoreDatastreamCodec.HandshakeMessage decoded =
        QuasselCoreDatastreamCodec.decodeHandshakePayload(
            QuasselCoreDatastreamCodec.encodeHandshakePayload(outbound));

    assertEquals("SessionInit", decoded.messageType());

    @SuppressWarnings("unchecked")
    Map<String, Object> sessionState = (Map<String, Object>) decoded.fields().get("SessionState");

    @SuppressWarnings("unchecked")
    List<Object> networkIds = (List<Object>) sessionState.get("NetworkIds");
    assertEquals(7, ((Number) networkIds.get(0)).intValue());

    @SuppressWarnings("unchecked")
    List<Object> bufferInfos = (List<Object>) sessionState.get("BufferInfos");
    QuasselCoreDatastreamCodec.BufferInfoValue parsedBuffer =
        assertInstanceOf(QuasselCoreDatastreamCodec.BufferInfoValue.class, bufferInfos.get(0));
    assertEquals(7, parsedBuffer.networkId());
    assertEquals("freenode", parsedBuffer.bufferName());
  }

  @Test
  void writesSignalProxyRpcCallFrame() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    codec.writeSignalProxyRpcCall(
        out,
        "2sendInput(BufferInfo,QString)",
        List.of(
            new QuasselCoreDatastreamCodec.BufferInfoValue(-1, 3, 0x01, -1, ""),
            "/QUOTE WHOIS alice"));

    byte[] frame = out.toByteArray();
    int payloadLen =
        ((frame[0] & 0xff) << 24)
            | ((frame[1] & 0xff) << 16)
            | ((frame[2] & 0xff) << 8)
            | (frame[3] & 0xff);
    assertEquals(frame.length - 4, payloadLen);

    ByteBuffer payload = ByteBuffer.wrap(frame, 4, payloadLen).order(ByteOrder.BIG_ENDIAN);
    int listSize = payload.getInt();
    assertEquals(4, listSize);

    // First list item is QVariant(int) request type = RpcCall (2)
    assertEquals(2, payload.getInt());
    assertEquals(0, payload.get());
    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL, payload.getInt());

    // Second list item is QVariant(QByteArray) slot-name bytes.
    assertEquals(12, payload.getInt());
    assertEquals(0, payload.get());
    int slotLen = payload.getInt();
    byte[] slotBytes = new byte[slotLen];
    payload.get(slotBytes);
    assertArrayEquals("2sendInput(BufferInfo,QString)".getBytes(StandardCharsets.UTF_8), slotBytes);
  }

  @Test
  void writesSignalProxySyncFrame() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    codec.writeSignalProxySync(
        out,
        "BacklogManager",
        "global",
        "requestBacklog(BufferId,MsgId,MsgId,int,int)",
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", -1),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 42),
            50,
            0));

    byte[] frame = out.toByteArray();
    byte[] payload = java.util.Arrays.copyOfRange(frame, 4, frame.length);
    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC, decoded.requestType());
    assertEquals("BacklogManager", decoded.className());
    assertEquals("global", decoded.objectName());
    assertEquals("requestBacklog(BufferId,MsgId,MsgId,int,int)", decoded.slotName());
  }

  @Test
  void writesNetworkInfoIdentityAsIntVariant() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    codec.writeSignalProxySync(
        out,
        "Network",
        "5",
        "requestSetNetworkInfo",
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue(
                "NetworkInfo", Map.of("NetworkId", 5, "IdentityId", 1, "NetworkName", "libera"))));

    byte[] frame = out.toByteArray();
    byte[] payload = java.util.Arrays.copyOfRange(frame, 4, frame.length);
    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);
    assertEquals("requestSetNetworkInfo", decoded.slotName());
    assertEquals(1, decoded.params().size());
    Map<?, ?> networkInfo = assertInstanceOf(Map.class, decoded.params().get(0));
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) networkInfo;
    assertEquals(1, ((Number) map.get("IdentityId")).intValue());
  }

  @Test
  void writesNetworkInfoIdentityAsIdentityUserTypeVariant() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    codec.writeSignalProxySync(
        out,
        "Network",
        "5",
        "requestSetNetworkInfo",
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue(
                "NetworkInfo",
                Map.of(
                    "NetworkId", new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", 5),
                    "Identity", new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", 1),
                    "NetworkName", "libera"))));

    byte[] frame = out.toByteArray();
    byte[] payload = java.util.Arrays.copyOfRange(frame, 4, frame.length);
    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);
    assertEquals("requestSetNetworkInfo", decoded.slotName());
    assertEquals(1, decoded.params().size());
    Map<?, ?> networkInfo = assertInstanceOf(Map.class, decoded.params().get(0));
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) networkInfo;
    assertEquals(1, ((Number) map.get("Identity")).intValue());
  }

  @Test
  void writesSignalProxyInitRequestFrame() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    codec.writeSignalProxyInitRequest(out, "Network", "5", List.of());

    byte[] frame = out.toByteArray();
    byte[] payload = java.util.Arrays.copyOfRange(frame, 4, frame.length);
    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_INIT_REQUEST, decoded.requestType());
    assertEquals("Network", decoded.className());
    assertEquals("5", decoded.objectName());
    assertTrue(decoded.slotName().isEmpty());
    assertTrue(decoded.params().isEmpty());
  }

  @Test
  void writesSignalProxyHeartbeatProbeFrame() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    QuasselCoreDatastreamCodec.QtDateTimeValue dt =
        QuasselCoreDatastreamCodec.utcDateTimeFromEpochMs(1_700_000_123_456L);

    codec.writeSignalProxyHeartBeat(out, dt);

    byte[] frame = out.toByteArray();
    byte[] payload = java.util.Arrays.copyOfRange(frame, 4, frame.length);
    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_HEARTBEAT, decoded.requestType());
    QuasselCoreDatastreamCodec.QtDateTimeValue parsed =
        assertInstanceOf(QuasselCoreDatastreamCodec.QtDateTimeValue.class, decoded.params().get(0));
    assertEquals(dt, parsed);
    assertEquals(1_700_000_123_456L, QuasselCoreDatastreamCodec.epochMsFromQtDateTime(parsed));
  }

  @Test
  void decodesSignalProxyDisplayMsgRpcWithMessagePayload() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    QuasselCoreDatastreamCodec.MessageValue outboundMessage =
        new QuasselCoreDatastreamCodec.MessageValue(
            77L,
            1_700_000_001L,
            0x0001,
            0,
            new QuasselCoreDatastreamCodec.BufferInfoValue(5, 3, 0x02, -1, "#chat"),
            "alice!u@example.net",
            "hello");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    codec.writeSignalProxyRpcCall(out, "2displayMsg(Message)", List.of(outboundMessage));
    byte[] frame = out.toByteArray();
    byte[] payload = java.util.Arrays.copyOfRange(frame, 4, frame.length);

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL, decoded.requestType());
    assertEquals("2displayMsg(Message)", decoded.slotName());
    QuasselCoreDatastreamCodec.MessageValue msg =
        assertInstanceOf(QuasselCoreDatastreamCodec.MessageValue.class, decoded.params().get(0));
    assertEquals(77L, msg.messageId());
    assertEquals(1_700_000_001L, msg.timestampEpochSeconds());
    assertEquals("#chat", msg.bufferInfo().bufferName());
    assertEquals("alice!u@example.net", msg.sender());
    assertEquals("hello", msg.content());
  }

  @Test
  void decodesSignalProxyHeartbeatMessage() throws Exception {
    byte[] payload =
        QuasselCoreDatastreamCodec.encodeSignalProxyPayload(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_HEARTBEAT,
                new QuasselCoreDatastreamCodec.QtDateTimeValue(2_460_000, 1234, 1)));

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_HEARTBEAT, decoded.requestType());
    assertTrue(decoded.slotName().isEmpty());
    QuasselCoreDatastreamCodec.QtDateTimeValue dt =
        assertInstanceOf(QuasselCoreDatastreamCodec.QtDateTimeValue.class, decoded.params().get(0));
    assertEquals(2_460_000, dt.julianDay());
    assertEquals(1234, dt.msecsOfDay());
    assertEquals(1, dt.timeSpec());
  }

  @Test
  void decodesSignalProxySyncEnvelopeMetadata() throws Exception {
    byte[] payload =
        QuasselCoreDatastreamCodec.encodeSignalProxyPayload(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "BacklogManager".getBytes(StandardCharsets.UTF_8),
                "global".getBytes(StandardCharsets.UTF_8),
                "receiveBacklog(BufferId,QVariantList)".getBytes(StandardCharsets.UTF_8),
                11,
                List.of()));

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC, decoded.requestType());
    assertEquals("BacklogManager", decoded.className());
    assertEquals("global", decoded.objectName());
    assertEquals("receiveBacklog(BufferId,QVariantList)", decoded.slotName());
  }

  @Test
  void decodesNetworkInfoSyncParams() throws Exception {
    byte[] payload =
        QuasselCoreDatastreamCodec.encodeSignalProxyPayload(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "NetworkInfo".getBytes(StandardCharsets.UTF_8),
                "1".getBytes(StandardCharsets.UTF_8),
                "sync()".getBytes(StandardCharsets.UTF_8),
                Map.of("networkName", "libera")));

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC, decoded.requestType());
    assertEquals("NetworkInfo", decoded.className());
    assertEquals("1", decoded.objectName());
    assertEquals("sync()", decoded.slotName());
    @SuppressWarnings("unchecked")
    Map<String, Object> state = (Map<String, Object>) decoded.params().get(0);
    assertEquals("libera", state.get("networkName"));
  }

  @Test
  void decodesUnknownSyncClassParams() throws Exception {
    byte[] payload =
        QuasselCoreDatastreamCodec.encodeSignalProxyPayload(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC,
                "NetworkConfig".getBytes(StandardCharsets.UTF_8),
                "global".getBytes(StandardCharsets.UTF_8),
                "sync()".getBytes(StandardCharsets.UTF_8),
                Map.of("networkId", 11, "networkName", "libera")));

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC, decoded.requestType());
    assertEquals("NetworkConfig", decoded.className());
    assertEquals("global", decoded.objectName());
    assertEquals("sync()", decoded.slotName());
    assertEquals(1, decoded.params().size());
    @SuppressWarnings("unchecked")
    Map<String, Object> state = (Map<String, Object>) decoded.params().get(0);
    assertEquals(11, ((Number) state.get("networkId")).intValue());
    assertEquals("libera", state.get("networkName"));
  }

  @Test
  void decodesUnknownNetworkRpcSlotParams() throws Exception {
    byte[] payload =
        QuasselCoreDatastreamCodec.encodeSignalProxyPayload(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL,
                "2networkCreated(NetworkId)".getBytes(StandardCharsets.UTF_8),
                new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", 9)));

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL, decoded.requestType());
    assertEquals("2networkCreated(NetworkId)", decoded.slotName());
    assertEquals(1, decoded.params().size());
    assertEquals(9, ((Number) decoded.params().get(0)).intValue());
  }

  @Test
  void decodesUnknownIdentityRpcSlotParams() throws Exception {
    byte[] payload =
        QuasselCoreDatastreamCodec.encodeSignalProxyPayload(
            List.of(
                QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL,
                "2identityCreated(Identity)".getBytes(StandardCharsets.UTF_8),
                new QuasselCoreDatastreamCodec.UserTypeValue(
                    "Identity",
                    Map.of(
                        "identityId",
                        new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", 3),
                        "identityName",
                        "ircafe"))));

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload);

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL, decoded.requestType());
    assertEquals("2identityCreated(Identity)", decoded.slotName());
    assertEquals(1, decoded.params().size());
    Object first = decoded.params().get(0);
    @SuppressWarnings("unchecked")
    Map<String, Object> identityMap =
        first instanceof QuasselCoreDatastreamCodec.UserTypeValue userType
            ? (Map<String, Object>) userType.value()
            : (Map<String, Object>) assertInstanceOf(Map.class, first);
    assertEquals("ircafe", identityMap.get("identityName"));
  }

  @Test
  void decodesSyncPayloadWhenVariantParamIsNull() throws Exception {
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    writeInt32(payload, 5);
    writeVariantInt(payload, QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC);
    writeVariantQByteArray(payload, "Network");
    writeVariantQByteArray(payload, "5");
    writeVariantQByteArray(payload, "sync()");
    writeVariantNullQString(payload);

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        QuasselCoreDatastreamCodec.decodeSignalProxyPayload(payload.toByteArray());

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC, decoded.requestType());
    assertEquals("Network", decoded.className());
    assertEquals("5", decoded.objectName());
    assertEquals("sync()", decoded.slotName());
    assertEquals(1, decoded.params().size());
    assertEquals(null, decoded.params().get(0));
  }

  private static void writeVariantInt(ByteArrayOutputStream out, int value) {
    writeInt32(out, 2);
    out.write(0);
    writeInt32(out, value);
  }

  private static void writeVariantQByteArray(ByteArrayOutputStream out, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    writeInt32(out, 12);
    out.write(0);
    writeInt32(out, bytes.length);
    out.writeBytes(bytes);
  }

  private static void writeVariantNullQString(ByteArrayOutputStream out) {
    writeInt32(out, 10);
    out.write(1);
  }

  private static void writeInt32(ByteArrayOutputStream out, int value) {
    out.write((value >>> 24) & 0xFF);
    out.write((value >>> 16) & 0xFF);
    out.write((value >>> 8) & 0xFF);
    out.write(value & 0xFF);
  }
}
