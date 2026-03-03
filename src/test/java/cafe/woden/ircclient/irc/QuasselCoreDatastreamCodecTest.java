package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();

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
    assertArrayEquals(
        "2sendInput(BufferInfo,QString)".getBytes(StandardCharsets.UTF_8),
        slotBytes);
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
        assertInstanceOf(
            QuasselCoreDatastreamCodec.QtDateTimeValue.class, decoded.params().get(0));
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
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
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
        assertInstanceOf(
            QuasselCoreDatastreamCodec.QtDateTimeValue.class, decoded.params().get(0));
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
}
