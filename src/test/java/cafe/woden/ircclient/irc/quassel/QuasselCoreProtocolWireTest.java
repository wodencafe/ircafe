package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuasselCoreProtocolWireTest {

  @Test
  void decodesClientLoginHandshakeFromWireFixture() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();

    QuasselCoreDatastreamCodec.HandshakeMessage decoded =
        codec.readHandshakeMessage(new ByteArrayInputStream(clientLoginHandshakeFrameFixture()));

    assertEquals("ClientLogin", decoded.messageType());
    assertEquals("alice", decoded.fields().get("User"));
    assertEquals("secret", decoded.fields().get("Password"));
  }

  @Test
  void encodesClientLoginHandshakeToWireFixture() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    codec.writeHandshakeMessage(out, clientLoginFields());

    assertArrayEquals(clientLoginHandshakeFrameFixture(), out.toByteArray());
  }

  @Test
  void decodesDisplayMsgRpcFromWireFixture() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        codec.readSignalProxyMessage(new ByteArrayInputStream(displayMsgRpcFrameFixture()));

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL, decoded.requestType());
    assertEquals("2displayMsg(Message)", decoded.slotName());
    QuasselCoreDatastreamCodec.MessageValue message =
        assertInstanceOf(QuasselCoreDatastreamCodec.MessageValue.class, decoded.params().get(0));
    assertEquals(77L, message.messageId());
    assertEquals(1_700_000_001L, message.timestampEpochSeconds());
    assertEquals("#chat", message.bufferInfo().bufferName());
    assertEquals("alice!u@example.net", message.sender());
    assertEquals("hello", message.content());
  }

  @Test
  void decodesRpcFrameWithNullSlotNameWithoutThrowing() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        codec.readSignalProxyMessage(new ByteArrayInputStream(rpcNullSlotFrameFixture()));

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL, decoded.requestType());
    assertEquals("", decoded.slotName());
    assertEquals(List.of(), decoded.params());
  }

  @Test
  void decodesNetworkInfoSyncFromWireFixture() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        codec.readSignalProxyMessage(new ByteArrayInputStream(networkInfoSyncFrameFixture()));

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC, decoded.requestType());
    assertEquals("NetworkInfo", decoded.className());
    assertEquals("1", decoded.objectName());
    assertEquals("sync()", decoded.slotName());
    @SuppressWarnings("unchecked")
    Map<String, Object> state = assertInstanceOf(Map.class, decoded.params().get(0));
    assertEquals("libera", state.get("networkName"));
    assertEquals("bot", state.get("myNick"));
  }

  @Test
  void decodesBufferSyncerMarkerSyncFromWireFixture() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();

    QuasselCoreDatastreamCodec.SignalProxyMessage decoded =
        codec.readSignalProxyMessage(
            new ByteArrayInputStream(bufferSyncerMarkerSyncFrameFixture()));

    assertEquals(QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC, decoded.requestType());
    assertEquals("BufferSyncer", decoded.className());
    assertEquals("global", decoded.objectName());
    assertEquals("setMarkerLine(BufferId,MsgId)", decoded.slotName());
    assertEquals(11, ((Number) decoded.params().get(0)).intValue());
    assertEquals(4242, ((Number) decoded.params().get(1)).intValue());
  }

  @Test
  void encodesBufferSyncerMarkerSyncToWireFixture() throws Exception {
    QuasselCoreDatastreamCodec codec = new QuasselCoreDatastreamCodec();
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    codec.writeSignalProxySync(
        out,
        "BufferSyncer",
        "global",
        "setMarkerLine(BufferId,MsgId)",
        List.of(
            new QuasselCoreDatastreamCodec.UserTypeValue("BufferId", 11),
            new QuasselCoreDatastreamCodec.UserTypeValue("MsgId", 4242)));

    assertArrayEquals(bufferSyncerMarkerSyncFrameFixture(), out.toByteArray());
  }

  @Test
  void encodesBacklogRequestSyncToWireFixture() throws Exception {
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

    assertArrayEquals(backlogRequestSyncFrameFixture(), out.toByteArray());
  }

  private static LinkedHashMap<String, Object> clientLoginFields() {
    LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
    fields.put("MsgType", "ClientLogin");
    fields.put("User", "alice");
    fields.put("Password", "secret");
    return fields;
  }

  private static byte[] clientLoginHandshakeFrameFixture() throws IOException {
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    writeInt32(payload, 6);
    writeVariantQByteArray(payload, "MsgType");
    writeVariantQString(payload, "ClientLogin");
    writeVariantQByteArray(payload, "User");
    writeVariantQString(payload, "alice");
    writeVariantQByteArray(payload, "Password");
    writeVariantQString(payload, "secret");
    return frame(payload.toByteArray());
  }

  private static byte[] displayMsgRpcFrameFixture() throws IOException {
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    writeInt32(payload, 3);
    writeVariantInt(payload, QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL);
    writeVariantQByteArray(payload, "2displayMsg(Message)");
    writeVariantUserTypeMessage(
        payload,
        new QuasselCoreDatastreamCodec.MessageValue(
            77L,
            1_700_000_001L,
            0x0001,
            0,
            new QuasselCoreDatastreamCodec.BufferInfoValue(5, 3, 0x02, -1, "#chat"),
            "alice!u@example.net",
            "hello"));
    return frame(payload.toByteArray());
  }

  private static byte[] networkInfoSyncFrameFixture() throws IOException {
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    writeInt32(payload, 5);
    writeVariantInt(payload, QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC);
    writeVariantQByteArray(payload, "NetworkInfo");
    writeVariantQByteArray(payload, "1");
    writeVariantQByteArray(payload, "sync()");
    writeVariantMap(payload, Map.of("networkName", "libera", "myNick", "bot"));
    return frame(payload.toByteArray());
  }

  private static byte[] rpcNullSlotFrameFixture() throws IOException {
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    writeInt32(payload, 2);
    writeVariantInt(payload, QuasselCoreDatastreamCodec.SIGNAL_PROXY_RPC_CALL);
    writeVariantNull(payload, 12);
    return frame(payload.toByteArray());
  }

  private static byte[] backlogRequestSyncFrameFixture() throws IOException {
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    writeInt32(payload, 9);
    writeVariantInt(payload, QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC);
    writeVariantQByteArray(payload, "BacklogManager");
    writeVariantQByteArray(payload, "global");
    writeVariantQByteArray(payload, "requestBacklog(BufferId,MsgId,MsgId,int,int)");
    writeVariantUserTypeId(payload, "BufferId", 11);
    writeVariantUserTypeId(payload, "MsgId", -1);
    writeVariantUserTypeId(payload, "MsgId", 42);
    writeVariantInt(payload, 50);
    writeVariantInt(payload, 0);
    return frame(payload.toByteArray());
  }

  private static byte[] bufferSyncerMarkerSyncFrameFixture() throws IOException {
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    writeInt32(payload, 6);
    writeVariantInt(payload, QuasselCoreDatastreamCodec.SIGNAL_PROXY_SYNC);
    writeVariantQByteArray(payload, "BufferSyncer");
    writeVariantQByteArray(payload, "global");
    writeVariantQByteArray(payload, "setMarkerLine(BufferId,MsgId)");
    writeVariantUserTypeId(payload, "BufferId", 11);
    writeVariantUserTypeId(payload, "MsgId", 4242);
    return frame(payload.toByteArray());
  }

  private static byte[] frame(byte[] payload) throws IOException {
    ByteArrayOutputStream frame = new ByteArrayOutputStream();
    writeInt32(frame, payload.length);
    frame.write(payload);
    return frame.toByteArray();
  }

  private static void writeVariantInt(ByteArrayOutputStream out, int value) throws IOException {
    writeInt32(out, 2);
    out.write(0);
    writeInt32(out, value);
  }

  private static void writeVariantNull(ByteArrayOutputStream out, int type) {
    writeInt32(out, type);
    out.write(1);
  }

  private static void writeVariantQByteArray(ByteArrayOutputStream out, String value)
      throws IOException {
    writeInt32(out, 12);
    out.write(0);
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    writeInt32(out, utf8.length);
    out.write(utf8);
  }

  private static void writeVariantQString(ByteArrayOutputStream out, String value)
      throws IOException {
    writeInt32(out, 10);
    out.write(0);
    byte[] utf16 = value.getBytes(StandardCharsets.UTF_16BE);
    writeInt32(out, utf16.length);
    out.write(utf16);
  }

  private static void writeVariantMap(ByteArrayOutputStream out, Map<String, String> values)
      throws IOException {
    writeInt32(out, 8);
    out.write(0);
    writeInt32(out, values.size());
    for (Map.Entry<String, String> entry : values.entrySet()) {
      writeQString(out, entry.getKey());
      writeVariantQString(out, entry.getValue());
    }
  }

  private static void writeVariantUserTypeId(ByteArrayOutputStream out, String typeName, int value)
      throws IOException {
    writeInt32(out, 127);
    out.write(0);
    writeCString(out, typeName);
    writeInt32(out, value);
  }

  private static void writeVariantUserTypeMessage(
      ByteArrayOutputStream out, QuasselCoreDatastreamCodec.MessageValue message)
      throws IOException {
    writeInt32(out, 127);
    out.write(0);
    writeCString(out, "Message");
    writeInt32(out, (int) message.messageId());
    writeInt32(out, (int) message.timestampEpochSeconds());
    writeInt32(out, message.typeBits());
    out.write(message.flags() & 0xff);
    writeBufferInfo(out, message.bufferInfo());
    writeUtf8(out, message.sender());
    writeUtf8(out, message.content());
  }

  private static void writeBufferInfo(
      ByteArrayOutputStream out, QuasselCoreDatastreamCodec.BufferInfoValue buffer)
      throws IOException {
    writeInt32(out, buffer.bufferId());
    writeInt32(out, buffer.networkId());
    writeInt16(out, (short) buffer.typeBits());
    writeInt32(out, buffer.groupId());
    writeUtf8(out, buffer.bufferName());
  }

  private static void writeCString(ByteArrayOutputStream out, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
    writeInt32(out, bytes.length + 1);
    out.write(bytes);
    out.write(0);
  }

  private static void writeQString(ByteArrayOutputStream out, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_16BE);
    writeInt32(out, bytes.length);
    out.write(bytes);
  }

  private static void writeUtf8(ByteArrayOutputStream out, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    writeInt32(out, bytes.length);
    out.write(bytes);
  }

  private static void writeInt16(ByteArrayOutputStream out, short value) {
    out.write((value >>> 8) & 0xff);
    out.write(value & 0xff);
  }

  private static void writeInt32(ByteArrayOutputStream out, int value) {
    out.write((value >>> 24) & 0xff);
    out.write((value >>> 16) & 0xff);
    out.write((value >>> 8) & 0xff);
    out.write(value & 0xff);
  }
}
