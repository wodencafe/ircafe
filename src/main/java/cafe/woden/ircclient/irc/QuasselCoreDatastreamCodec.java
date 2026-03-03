package cafe.woden.ircclient.irc;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/**
 * Minimal Qt DataStream codec for Quassel datastream and SignalProxy payloads.
 *
 * <p>Supports handshake/auth messages, outbound rpc calls, and the subset of inbound SignalProxy
 * messages needed for IRCafe's Quassel bridge.
 */
@Component
@InfrastructureLayer
public class QuasselCoreDatastreamCodec {
  static final int MAX_FRAME_BYTES = 64 * 1024 * 1024;

  static final int SIGNAL_PROXY_SYNC = 1;
  static final int SIGNAL_PROXY_RPC_CALL = 2;
  static final int SIGNAL_PROXY_INIT_REQUEST = 3;
  static final int SIGNAL_PROXY_INIT_DATA = 4;
  static final int SIGNAL_PROXY_HEARTBEAT = 5;
  static final int SIGNAL_PROXY_HEARTBEAT_REPLY = 6;

  private static final Charset QT_CSTRING_CHARSET = StandardCharsets.ISO_8859_1;
  private static final long MILLIS_PER_DAY = 86_400_000L;
  private static final long JULIAN_DAY_UNIX_EPOCH = 2_440_588L;

  private static final int QT_LONG_LONG = 4;
  private static final int QT_ULONG_LONG = 5;
  private static final int QT_BOOL = 1;
  private static final int QT_INT = 2;
  private static final int QT_UINT = 3;
  private static final int QT_VARIANT_MAP = 8;
  private static final int QT_VARIANT_LIST = 9;
  private static final int QT_QSTRING = 10;
  private static final int QT_QSTRING_LIST = 11;
  private static final int QT_QBYTE_ARRAY = 12;
  private static final int QT_QDATETIME = 16;
  private static final int QT_USER_TYPE = 127;
  private static final int QT_LONG = 129;
  private static final int QT_SHORT = 130;
  private static final int QT_CHAR = 131;
  private static final int QT_ULONG = 132;
  private static final int QT_USHORT = 133;
  private static final int QT_UCHAR = 134;
  private static final int QT_VARIANT = 138;
  private static final int QT_VARIANT_ALT = 144;
  private static final int QT_USER_TYPE_ALT = 256;

  public void writeHandshakeMessage(OutputStream output, Map<String, Object> fields)
      throws IOException {
    OutputStream out = Objects.requireNonNull(output, "output");
    Map<String, Object> map = Objects.requireNonNull(fields, "fields");

    byte[] payload = encodeHandshakePayload(map);
    writeInt32(out, payload.length);
    out.write(payload);
    out.flush();
  }

  public HandshakeMessage readHandshakeMessage(InputStream input) throws IOException {
    byte[] payload = readFramePayload(input);
    return decodeHandshakePayload(payload);
  }

  public byte[] readFramePayload(InputStream input) throws IOException {
    InputStream in = Objects.requireNonNull(input, "input");
    int frameSize = readInt32(in);
    if (frameSize <= 0 || frameSize > MAX_FRAME_BYTES) {
      throw new IOException("invalid Quassel datastream frame size: " + frameSize);
    }
    return readExactly(in, frameSize);
  }

  public void writeSignalProxyRpcCall(OutputStream output, String slotName, List<Object> params)
      throws IOException {
    OutputStream out = Objects.requireNonNull(output, "output");
    String slot = Objects.toString(slotName, "").trim();
    if (slot.isEmpty()) {
      throw new IllegalArgumentException("slotName is blank");
    }

    ArrayList<Object> items = new ArrayList<>();
    items.add(SIGNAL_PROXY_RPC_CALL);
    items.add(slot.getBytes(StandardCharsets.UTF_8));
    if (params != null && !params.isEmpty()) {
      items.addAll(params);
    }
    writeSignalProxyPayloadFrame(out, items);
  }

  public void writeSignalProxySync(
      OutputStream output,
      String className,
      String objectName,
      String slotName,
      List<Object> params)
      throws IOException {
    OutputStream out = Objects.requireNonNull(output, "output");
    String clazz = Objects.toString(className, "").trim();
    String object = Objects.toString(objectName, "").trim();
    String slot = Objects.toString(slotName, "").trim();
    if (clazz.isEmpty()) {
      throw new IllegalArgumentException("className is blank");
    }
    if (object.isEmpty()) {
      throw new IllegalArgumentException("objectName is blank");
    }
    if (slot.isEmpty()) {
      throw new IllegalArgumentException("slotName is blank");
    }

    ArrayList<Object> items = new ArrayList<>();
    items.add(SIGNAL_PROXY_SYNC);
    items.add(clazz.getBytes(StandardCharsets.UTF_8));
    items.add(object.getBytes(StandardCharsets.UTF_8));
    items.add(slot.getBytes(StandardCharsets.UTF_8));
    if (params != null && !params.isEmpty()) {
      items.addAll(params);
    }
    writeSignalProxyPayloadFrame(out, items);
  }

  public void writeSignalProxyHeartBeatReply(OutputStream output, QtDateTimeValue serverTime)
      throws IOException {
    OutputStream out = Objects.requireNonNull(output, "output");
    QtDateTimeValue time = Objects.requireNonNull(serverTime, "serverTime");
    writeSignalProxyPayloadFrame(out, List.of(SIGNAL_PROXY_HEARTBEAT_REPLY, time));
  }

  public void writeSignalProxyHeartBeat(OutputStream output, QtDateTimeValue clientTime)
      throws IOException {
    OutputStream out = Objects.requireNonNull(output, "output");
    QtDateTimeValue time = Objects.requireNonNull(clientTime, "clientTime");
    writeSignalProxyPayloadFrame(out, List.of(SIGNAL_PROXY_HEARTBEAT, time));
  }

  private static void writeSignalProxyPayloadFrame(OutputStream out, List<Object> payloadItems)
      throws IOException {
    byte[] payload = encodeSignalProxyPayload(payloadItems);
    writeInt32(out, payload.length);
    out.write(payload);
    out.flush();
  }

  public SignalProxyMessage readSignalProxyMessage(InputStream input) throws IOException {
    return decodeSignalProxyPayload(readFramePayload(input));
  }

  public static SignalProxyMessage decodeSignalProxyPayload(byte[] payloadBytes)
      throws IOException {
    Objects.requireNonNull(payloadBytes, "payloadBytes");
    ByteBuffer payload = ByteBuffer.wrap(payloadBytes).order(ByteOrder.BIG_ENDIAN);

    ensureRemaining(payload, 4, "SignalProxy payload item count");
    int itemCount = payload.getInt();
    if (itemCount <= 0 || itemCount > 1_000_000) {
      throw new IOException("invalid SignalProxy payload item count: " + itemCount);
    }

    Object requestTypeRaw = readVariant(payload, 0);
    int requestType = asInt(requestTypeRaw, "SignalProxy request type");

    String className = "";
    String objectName = "";
    String slotName = "";
    List<Object> params = List.of();

    if (requestType == SIGNAL_PROXY_RPC_CALL && itemCount >= 2) {
      Object slotRaw = readVariant(payload, 0);
      slotName = decodeSlotName(slotRaw);
      int remainingParamCount = Math.max(0, itemCount - 2);
      params = decodeKnownRpcParams(slotName, remainingParamCount, payload);
    } else if ((requestType == SIGNAL_PROXY_SYNC
            || requestType == SIGNAL_PROXY_INIT_REQUEST
            || requestType == SIGNAL_PROXY_INIT_DATA)
        && itemCount >= 3) {
      className = decodeSlotName(readVariant(payload, 0));
      objectName = decodeSlotName(readVariant(payload, 0));
      int consumed = 3;
      if (requestType == SIGNAL_PROXY_SYNC && itemCount >= 4) {
        slotName = decodeSlotName(readVariant(payload, 0));
        consumed = 4;
      }
      int remainingParamCount = Math.max(0, itemCount - consumed);
      params = decodeKnownSyncParams(className, slotName, remainingParamCount, payload);
    } else if ((requestType == SIGNAL_PROXY_HEARTBEAT
            || requestType == SIGNAL_PROXY_HEARTBEAT_REPLY)
        && itemCount >= 2) {
      Object raw = readVariant(payload, 0);
      if (raw instanceof QtDateTimeValue dt) {
        params = List.of(dt);
      }
    }

    return new SignalProxyMessage(requestType, className, objectName, slotName, params);
  }

  public static QtDateTimeValue utcDateTimeFromEpochMs(long epochMs) {
    long daysSinceEpoch = Math.floorDiv(epochMs, MILLIS_PER_DAY);
    int msecsOfDay = (int) Math.floorMod(epochMs, MILLIS_PER_DAY);
    long julianDay = JULIAN_DAY_UNIX_EPOCH + daysSinceEpoch;
    return new QtDateTimeValue((int) julianDay, msecsOfDay, 1);
  }

  public static long epochMsFromQtDateTime(QtDateTimeValue value) {
    QtDateTimeValue dt = Objects.requireNonNull(value, "value");
    long julianDay = dt.julianDay();
    long msecsOfDay = dt.msecsOfDay();
    if (msecsOfDay < 0L) msecsOfDay = 0L;
    if (msecsOfDay >= MILLIS_PER_DAY) msecsOfDay = MILLIS_PER_DAY - 1L;
    long daysSinceEpoch = julianDay - JULIAN_DAY_UNIX_EPOCH;
    return (daysSinceEpoch * MILLIS_PER_DAY) + msecsOfDay;
  }

  private static List<Object> decodeKnownRpcParams(
      String slotName, int remainingParamCount, ByteBuffer payload) throws IOException {
    if (remainingParamCount <= 0) {
      return List.of();
    }
    if ("2displayMsg(Message)".equals(slotName)) {
      return List.of(readVariant(payload, 0));
    }
    if ("2displayStatusMsg(QString,QString)".equals(slotName)) {
      String first = Objects.toString(readVariant(payload, 0), "");
      String second = remainingParamCount > 1 ? Objects.toString(readVariant(payload, 0), "") : "";
      return List.of(first, second);
    }
    if ("2bufferInfoUpdated(BufferInfo)".equals(slotName)
        || "2bufferInfoRemoved(BufferInfo)".equals(slotName)) {
      return List.of(readVariant(payload, 0));
    }
    // Unknown rpc calls are intentionally ignored for now; we keep frame handling resilient.
    return List.of();
  }

  private static List<Object> decodeKnownSyncParams(
      String className, String slotName, int remainingParamCount, ByteBuffer payload)
      throws IOException {
    if (remainingParamCount <= 0) {
      return List.of();
    }

    String classToken = Objects.toString(className, "").trim();
    String slotToken = Objects.toString(slotName, "").trim();

    if ("BacklogManager".equals(classToken) && slotToken.contains("receiveBacklog")) {
      return readRemainingVariants(remainingParamCount, payload);
    }
    if ("BufferSyncer".equals(classToken) || "BufferViewConfig".equals(classToken)) {
      return readRemainingVariants(remainingParamCount, payload);
    }
    if ("Network".equals(classToken)
        || "NetworkInfo".equals(classToken)
        || "IrcChannel".equals(classToken)
        || "IrcUser".equals(classToken)) {
      return readRemainingVariants(remainingParamCount, payload);
    }

    // Unknown sync/init payload is intentionally skipped.
    return List.of();
  }

  private static List<Object> readRemainingVariants(int count, ByteBuffer payload)
      throws IOException {
    if (count <= 0) return List.of();
    ArrayList<Object> out = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      out.add(readVariant(payload, 0));
    }
    return List.copyOf(out);
  }

  private static int asInt(Object value, String context) throws IOException {
    if (value instanceof Number n) {
      return n.intValue();
    }
    throw new IOException("unexpected " + context + " value type: " + value);
  }

  private static String decodeSlotName(Object raw) throws IOException {
    if (raw instanceof byte[] bytes) {
      return new String(bytes, StandardCharsets.UTF_8);
    }
    if (raw instanceof String s) {
      return s;
    }
    throw new IOException("unexpected SignalProxy rpc slot variant value: " + raw);
  }

  static byte[] encodeHandshakePayload(Map<String, Object> fields) throws IOException {
    ByteArrayOutputStream payload = new ByteArrayOutputStream(256);
    writeInt32(payload, fields.size() * 2);
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      writeQByteArrayVariant(payload, entry.getKey().getBytes(StandardCharsets.UTF_8));
      writeVariant(payload, entry.getValue());
    }
    return payload.toByteArray();
  }

  static byte[] encodeSignalProxyPayload(List<Object> values) throws IOException {
    List<Object> list = (values == null) ? List.of() : values;
    ByteArrayOutputStream payload = new ByteArrayOutputStream(256);
    writeInt32(payload, list.size());
    for (Object value : list) {
      writeVariant(payload, value);
    }
    return payload.toByteArray();
  }

  static HandshakeMessage decodeHandshakePayload(byte[] payloadBytes) throws IOException {
    Objects.requireNonNull(payloadBytes, "payloadBytes");
    ByteBuffer payload = ByteBuffer.wrap(payloadBytes).order(ByteOrder.BIG_ENDIAN);

    int itemCount = payload.getInt();
    if (itemCount < 0 || (itemCount & 1) != 0) {
      throw new IOException("invalid Quassel handshake item count: " + itemCount);
    }

    LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
    String messageType = "";
    int pairs = itemCount / 2;
    for (int i = 0; i < pairs; i++) {
      String key = decodeKey(readVariant(payload, 0));
      Object value = readVariant(payload, 0);
      fields.put(key, value);

      if ("MsgType".equals(key) && value instanceof String s) {
        messageType = s;
      }
    }

    return new HandshakeMessage(
        messageType, Collections.unmodifiableMap(new LinkedHashMap<>(fields)));
  }

  private static String decodeKey(Object raw) throws IOException {
    if (raw instanceof byte[] bytes) {
      return new String(bytes, StandardCharsets.UTF_8);
    }
    if (raw instanceof String s) {
      return s;
    }
    throw new IOException("unexpected handshake key variant value: " + raw);
  }

  private static void writeVariant(OutputStream out, Object value) throws IOException {
    if (value instanceof String s) {
      writeQStringVariant(out, s);
      return;
    }
    if (value instanceof Boolean b) {
      writeVariantHeader(out, QT_BOOL, false);
      writeBool(out, b);
      return;
    }
    if (value instanceof Integer i) {
      writeVariantHeader(out, QT_INT, false);
      writeInt32(out, i);
      return;
    }
    if (value instanceof QtDateTimeValue dt) {
      writeVariantHeader(out, QT_QDATETIME, false);
      writeQtDateTime(out, dt);
      return;
    }
    if (value instanceof Long u) {
      if (u < 0 || u > 0xffff_ffffL) {
        writeVariantHeader(out, QT_ULONG_LONG, false);
        writeInt64(out, u);
        return;
      }
      writeVariantHeader(out, QT_UINT, false);
      writeInt32(out, (int) (u & 0xffff_ffffL));
      return;
    }
    if (value instanceof Short s) {
      writeVariantHeader(out, QT_SHORT, false);
      writeInt16(out, s);
      return;
    }
    if (value instanceof byte[] bytes) {
      writeQByteArrayVariant(out, bytes);
      return;
    }
    if (value instanceof BufferInfoValue bufferInfo) {
      writeUserTypeVariant(out, "BufferInfo", bufferInfo);
      return;
    }
    if (value instanceof MessageValue message) {
      writeUserTypeVariant(out, "Message", message);
      return;
    }
    if (value instanceof UserTypeValue userType) {
      writeUserTypeVariant(out, userType.typeName(), userType.value());
      return;
    }
    if (value instanceof Map<?, ?> map) {
      writeVariantHeader(out, QT_VARIANT_MAP, false);
      writeInt32(out, map.size());
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = Objects.toString(entry.getKey(), "");
        writeQString(out, key);
        writeVariant(out, entry.getValue());
      }
      return;
    }
    if (value instanceof List<?> list) {
      if (isStringList(list)) {
        writeVariantHeader(out, QT_QSTRING_LIST, false);
        writeInt32(out, list.size());
        for (Object item : list) {
          writeQString(out, item == null ? null : item.toString());
        }
        return;
      }

      writeVariantHeader(out, QT_VARIANT_LIST, false);
      writeInt32(out, list.size());
      for (Object item : list) {
        writeVariant(out, item);
      }
      return;
    }

    throw new IOException(
        "unsupported QVariant payload type: "
            + (value == null ? "null" : value.getClass().getName()));
  }

  private static void writeUserTypeVariant(OutputStream out, String typeName, Object value)
      throws IOException {
    String name = Objects.toString(typeName, "").trim();
    if (name.isEmpty()) {
      throw new IOException("user type name is blank");
    }

    writeVariantHeader(out, QT_USER_TYPE, false);
    writeCString(out, name);

    switch (name) {
      case "BufferInfo" -> {
        if (!(value instanceof BufferInfoValue info)) {
          throw new IOException("BufferInfo user type expects BufferInfoValue payload");
        }
        writeBufferInfo(out, info);
      }
      case "NetworkId", "BufferId", "IdentityId", "MsgId" -> {
        if (!(value instanceof Number n)) {
          throw new IOException(name + " user type expects numeric payload");
        }
        writeInt32(out, n.intValue());
      }
      case "PeerPtr" -> {
        if (!(value instanceof Number n)) {
          throw new IOException("PeerPtr user type expects numeric payload");
        }
        writeInt64(out, n.longValue());
      }
      case "Message" -> {
        if (!(value instanceof MessageValue message)) {
          throw new IOException("Message user type expects MessageValue payload");
        }
        writeMessage(out, message);
      }
      case "Identity", "NetworkInfo", "IrcUser", "IrcChannel", "Network::Server" -> {
        if (!(value instanceof Map<?, ?> map)) {
          throw new IOException(name + " user type expects QVariantMap payload");
        }
        writeInt32(out, map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          String key = Objects.toString(entry.getKey(), "");
          writeQString(out, key);
          writeVariant(out, entry.getValue());
        }
      }
      default -> throw new IOException("unsupported user type for QVariant serialization: " + name);
    }
  }

  private static Object readVariant(ByteBuffer in, int depth) throws IOException {
    if (depth > 48) {
      throw new IOException("maximum QVariant nesting exceeded");
    }
    ensureRemaining(in, 5, "variant header");
    int type = in.getInt();
    boolean isNull = in.get() != 0;
    if (isNull) {
      return null;
    }

    return switch (type) {
      case QT_BOOL -> {
        ensureRemaining(in, 1, "bool");
        yield in.get() != 0;
      }
      case QT_INT -> {
        ensureRemaining(in, 4, "int");
        yield in.getInt();
      }
      case QT_LONG -> {
        ensureRemaining(in, 8, "long");
        yield in.getLong();
      }
      case QT_SHORT -> {
        ensureRemaining(in, 2, "short");
        yield in.getShort();
      }
      case QT_CHAR -> {
        ensureRemaining(in, 1, "char");
        yield (int) in.get();
      }
      case QT_USHORT -> {
        ensureRemaining(in, 2, "ushort");
        yield Short.toUnsignedInt(in.getShort());
      }
      case QT_UCHAR -> {
        ensureRemaining(in, 1, "uchar");
        yield Byte.toUnsignedInt(in.get());
      }
      case QT_UINT -> {
        ensureRemaining(in, 4, "uint");
        yield Integer.toUnsignedLong(in.getInt());
      }
      case QT_ULONG -> {
        ensureRemaining(in, 8, "ulong");
        yield in.getLong();
      }
      case QT_LONG_LONG, QT_ULONG_LONG -> {
        ensureRemaining(in, 8, "longlong");
        yield in.getLong();
      }
      case QT_QSTRING -> readQString(in);
      case QT_QSTRING_LIST -> readQStringList(in);
      case QT_QBYTE_ARRAY -> readQByteArray(in);
      case QT_QDATETIME -> readQtDateTime(in);
      case QT_VARIANT_LIST -> readVariantList(in, depth + 1);
      case QT_VARIANT_MAP -> readVariantMap(in, depth + 1);
      case QT_VARIANT, QT_VARIANT_ALT -> readVariant(in, depth + 1);
      case QT_USER_TYPE, QT_USER_TYPE_ALT -> readUserType(in, depth + 1);
      default -> throw new IOException("unsupported Qt QVariant type id: " + type);
    };
  }

  private static Object readUserType(ByteBuffer in, int depth) throws IOException {
    String typeName = readCString(in);
    if (typeName == null || typeName.isBlank()) {
      throw new IOException("received blank Qt user type name");
    }

    return switch (typeName) {
      case "BufferInfo" -> readBufferInfo(in);
      case "BufferId", "NetworkId", "IdentityId", "MsgId" -> {
        ensureRemaining(in, 4, typeName);
        yield in.getInt();
      }
      case "PeerPtr" -> {
        ensureRemaining(in, 8, typeName);
        yield in.getLong();
      }
      case "Message" -> readMessage(in);
      case "Identity", "NetworkInfo", "IrcUser", "IrcChannel", "Network::Server" ->
          readVariantMap(in, depth + 1);
      default -> throw new IOException("unsupported Qt user type id: " + typeName);
    };
  }

  private static BufferInfoValue readBufferInfo(ByteBuffer in) throws IOException {
    ensureRemaining(in, 4 + 4 + 2 + 4, "BufferInfo");
    int bufferId = in.getInt();
    int networkId = in.getInt();
    int typeBits = Short.toUnsignedInt(in.getShort());
    int groupId = in.getInt();
    String bufferName = readUtf8String(in);
    return new BufferInfoValue(bufferId, networkId, typeBits, groupId, bufferName);
  }

  private static MessageValue readMessage(ByteBuffer in) throws IOException {
    ensureRemaining(in, 4 + 4 + 4 + 1, "Message");
    long messageId = in.getInt();
    long timestampEpochSeconds = in.getInt();
    int typeBits = in.getInt();
    int flags = Byte.toUnsignedInt(in.get());
    BufferInfoValue bufferInfo = readBufferInfo(in);
    String sender = readUtf8String(in);
    String content = readUtf8String(in);
    return new MessageValue(
        messageId, timestampEpochSeconds, typeBits, flags, bufferInfo, sender, content);
  }

  private static QtDateTimeValue readQtDateTime(ByteBuffer in) throws IOException {
    ensureRemaining(in, 9, "QDateTime");
    int julianDay = in.getInt();
    int msecsOfDay = in.getInt();
    int timeSpec = Byte.toUnsignedInt(in.get());
    return new QtDateTimeValue(julianDay, msecsOfDay, timeSpec);
  }

  private static Map<String, Object> readVariantMap(ByteBuffer in, int depth) throws IOException {
    int size = readCollectionSize(in, "QVariantMap");
    LinkedHashMap<String, Object> map = new LinkedHashMap<>(size);
    for (int i = 0; i < size; i++) {
      String key = readQString(in);
      map.put(key == null ? "" : key, readVariant(in, depth));
    }
    return map;
  }

  private static List<Object> readVariantList(ByteBuffer in, int depth) throws IOException {
    int size = readCollectionSize(in, "QVariantList");
    ArrayList<Object> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(readVariant(in, depth));
    }
    return list;
  }

  private static List<String> readQStringList(ByteBuffer in) throws IOException {
    int size = readCollectionSize(in, "QStringList");
    ArrayList<String> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(readQString(in));
    }
    return list;
  }

  private static byte[] readQByteArray(ByteBuffer in) throws IOException {
    ensureRemaining(in, 4, "QByteArray length");
    int len = in.getInt();
    if (len == -1) {
      return null;
    }
    ensureRemaining(in, len, "QByteArray data");
    byte[] out = new byte[len];
    in.get(out);
    return out;
  }

  private static String readQString(ByteBuffer in) throws IOException {
    ensureRemaining(in, 4, "QString length");
    int len = in.getInt();
    if (len == -1) {
      return null;
    }
    if ((len & 1) != 0) {
      throw new IOException("invalid QString byte-length (must be even): " + len);
    }
    ensureRemaining(in, len, "QString data");
    byte[] bytes = new byte[len];
    in.get(bytes);
    return new String(bytes, StandardCharsets.UTF_16BE);
  }

  private static String readUtf8String(ByteBuffer in) throws IOException {
    ensureRemaining(in, 4, "UTF-8 string length");
    int len = in.getInt();
    if (len == -1) {
      return null;
    }
    ensureRemaining(in, len, "UTF-8 string data");
    byte[] bytes = new byte[len];
    in.get(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static String readCString(ByteBuffer in) throws IOException {
    ensureRemaining(in, 4, "C-string length");
    int len = in.getInt();
    if (len == -1) {
      return null;
    }
    if (len <= 0) {
      return "";
    }
    ensureRemaining(in, len, "C-string data");
    byte[] bytes = new byte[len];
    in.get(bytes);
    int usable = len;
    if (usable > 0 && bytes[usable - 1] == 0) {
      usable--;
    }
    return new String(bytes, 0, usable, QT_CSTRING_CHARSET);
  }

  private static int readCollectionSize(ByteBuffer in, String typeName) throws IOException {
    ensureRemaining(in, 4, typeName + " size");
    int size = in.getInt();
    if (size < 0 || size > 1_000_000) {
      throw new IOException("invalid " + typeName + " size: " + size);
    }
    return size;
  }

  private static void ensureRemaining(ByteBuffer in, int needed, String context)
      throws IOException {
    if (needed < 0 || in.remaining() < needed) {
      throw new IOException(
          "unexpected EOF while decoding "
              + context
              + " (need="
              + needed
              + ", have="
              + in.remaining()
              + ")");
    }
  }

  private static boolean isStringList(List<?> list) {
    for (Object item : list) {
      if (item != null && !(item instanceof String)) {
        return false;
      }
    }
    return true;
  }

  private static void writeVariantHeader(OutputStream out, int type, boolean isNull)
      throws IOException {
    writeInt32(out, type);
    writeBool(out, isNull);
  }

  private static void writeQByteArrayVariant(OutputStream out, byte[] bytes) throws IOException {
    writeVariantHeader(out, QT_QBYTE_ARRAY, false);
    writeQByteArray(out, bytes);
  }

  private static void writeQStringVariant(OutputStream out, String value) throws IOException {
    writeVariantHeader(out, QT_QSTRING, false);
    writeQString(out, value);
  }

  private static void writeQByteArray(OutputStream out, byte[] bytes) throws IOException {
    if (bytes == null) {
      writeInt32(out, -1);
      return;
    }
    writeInt32(out, bytes.length);
    out.write(bytes);
  }

  private static void writeQString(OutputStream out, String value) throws IOException {
    if (value == null) {
      writeInt32(out, -1);
      return;
    }
    byte[] utf16 = value.getBytes(StandardCharsets.UTF_16BE);
    writeInt32(out, utf16.length);
    out.write(utf16);
  }

  private static void writeUtf8String(OutputStream out, String value) throws IOException {
    if (value == null) {
      writeInt32(out, -1);
      return;
    }
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    writeInt32(out, utf8.length);
    out.write(utf8);
  }

  private static void writeCString(OutputStream out, String value) throws IOException {
    if (value == null) {
      writeInt32(out, -1);
      return;
    }
    byte[] raw = value.getBytes(QT_CSTRING_CHARSET);
    writeInt32(out, raw.length + 1);
    out.write(raw);
    out.write(0);
  }

  private static void writeBufferInfo(OutputStream out, BufferInfoValue info) throws IOException {
    writeInt32(out, info.bufferId());
    writeInt32(out, info.networkId());
    writeInt16(out, (short) info.typeBits());
    writeInt32(out, info.groupId());
    writeUtf8String(out, info.bufferName());
  }

  private static void writeMessage(OutputStream out, MessageValue message) throws IOException {
    writeInt32(out, (int) message.messageId());
    writeInt32(out, (int) message.timestampEpochSeconds());
    writeInt32(out, message.typeBits());
    out.write(message.flags() & 0xff);
    writeBufferInfo(out, message.bufferInfo());
    writeUtf8String(out, message.sender());
    writeUtf8String(out, message.content());
  }

  private static void writeQtDateTime(OutputStream out, QtDateTimeValue value) throws IOException {
    writeInt32(out, value.julianDay());
    writeInt32(out, value.msecsOfDay());
    out.write(value.timeSpec() & 0xff);
  }

  private static void writeBool(OutputStream out, boolean value) throws IOException {
    out.write(value ? 1 : 0);
  }

  private static int readInt32(InputStream in) throws IOException {
    byte[] bytes = readExactly(in, 4);
    return ((bytes[0] & 0xff) << 24)
        | ((bytes[1] & 0xff) << 16)
        | ((bytes[2] & 0xff) << 8)
        | (bytes[3] & 0xff);
  }

  private static void writeInt16(OutputStream out, short value) throws IOException {
    out.write((value >>> 8) & 0xff);
    out.write(value & 0xff);
  }

  private static void writeInt32(OutputStream out, int value) throws IOException {
    out.write((value >>> 24) & 0xff);
    out.write((value >>> 16) & 0xff);
    out.write((value >>> 8) & 0xff);
    out.write(value & 0xff);
  }

  private static void writeInt64(OutputStream out, long value) throws IOException {
    out.write((int) ((value >>> 56) & 0xff));
    out.write((int) ((value >>> 48) & 0xff));
    out.write((int) ((value >>> 40) & 0xff));
    out.write((int) ((value >>> 32) & 0xff));
    out.write((int) ((value >>> 24) & 0xff));
    out.write((int) ((value >>> 16) & 0xff));
    out.write((int) ((value >>> 8) & 0xff));
    out.write((int) (value & 0xff));
  }

  private static byte[] readExactly(InputStream in, int size) throws IOException {
    byte[] out = new byte[size];
    int offset = 0;
    while (offset < size) {
      int read = in.read(out, offset, size - offset);
      if (read < 0) {
        throw new EOFException("unexpected EOF while reading datastream frame");
      }
      offset += read;
    }
    return out;
  }

  /** Decoded top-level handshake message fields from a datastream frame. */
  public record HandshakeMessage(String messageType, Map<String, Object> fields) {}

  /** Parsed subset of SignalProxy message metadata used by Quassel backend read loop. */
  public record SignalProxyMessage(
      int requestType, String className, String objectName, String slotName, List<Object> params) {}

  /** Raw Qt datetime payload as sent in SignalProxy heartbeat messages. */
  public record QtDateTimeValue(int julianDay, int msecsOfDay, int timeSpec) {}

  /** Simplified Quassel user-type wrapper for outbound variant encoding. */
  public record UserTypeValue(String typeName, Object value) {}

  /** Quassel BufferInfo payload used by {@code RpcHandler::sendInput}. */
  public record BufferInfoValue(
      int bufferId, int networkId, int typeBits, int groupId, String bufferName) {}

  /** Quassel Message payload used by {@code MessageEvent::displayMsg}. */
  public record MessageValue(
      long messageId,
      long timestampEpochSeconds,
      int typeBits,
      int flags,
      BufferInfoValue bufferInfo,
      String sender,
      String content) {}
}
