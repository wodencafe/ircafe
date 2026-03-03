package cafe.woden.ircclient.irc;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.springframework.stereotype.Component;

/** Performs Quassel Core protocol probing on a connected socket. */
@Component
@InfrastructureLayer
public class QuasselCoreProtocolProbe {
  static final int MAGIC_BASE = 0x42b33f00;
  static final int FEATURE_ENCRYPTION = 0x01;
  static final int FEATURE_COMPRESSION = 0x02;

  static final int PROTOCOL_LEGACY = 0x01;
  static final int PROTOCOL_DATASTREAM = 0x02;
  private static final int END_OF_PROTOCOL_LIST = 0x80000000;

  /**
   * Sends a probe request and parses the first protocol-selection reply word.
   *
   * <p>Based on Quassel's upstream client probe flow:
   *
   * <ul>
   *   <li>magic word ({@code 0x42b33f00 | featureFlags})
   *   <li>ordered protocol descriptors ({@code low8=type, mid16=features, highbit=end-of-list})
   *   <li>one 32-bit server selection reply
   * </ul>
   */
  public ProbeSelection negotiate(Socket socket) throws IOException {
    Socket s = Objects.requireNonNull(socket, "socket");

    OutputStream out = s.getOutputStream();
    out.write(buildProbeRequestWords(false, false));
    out.flush();

    InputStream in = s.getInputStream();
    int reply = readInt32(in);
    return parseSelection(reply);
  }

  static byte[] buildProbeRequestWords(boolean advertiseEncryption, boolean advertiseCompression) {
    int magic = MAGIC_BASE;
    if (advertiseEncryption) magic |= FEATURE_ENCRYPTION;
    if (advertiseCompression) magic |= FEATURE_COMPRESSION;

    int datastream = PROTOCOL_DATASTREAM;
    int legacy = END_OF_PROTOCOL_LIST | PROTOCOL_LEGACY;

    byte[] out = new byte[12];
    writeInt32(out, 0, magic);
    writeInt32(out, 4, datastream);
    writeInt32(out, 8, legacy);
    return out;
  }

  static ProbeSelection parseSelection(int replyWord) {
    int protocolType = replyWord & 0xff;
    int protocolFeatures = (replyWord >>> 8) & 0xffff;
    int connectionFeatures = (replyWord >>> 24) & 0xff;
    return new ProbeSelection(replyWord, protocolType, protocolFeatures, connectionFeatures);
  }

  static String protocolLabel(int protocolType) {
    return switch (protocolType) {
      case PROTOCOL_DATASTREAM -> "datastream";
      case PROTOCOL_LEGACY -> "legacy";
      default -> "unknown(" + protocolType + ")";
    };
  }

  static String hex8(int value) {
    return String.format("0x%02x", value & 0xff);
  }

  static String hex16(int value) {
    return String.format("0x%04x", value & 0xffff);
  }

  private static int readInt32(InputStream in) throws IOException {
    byte[] bytes = readExactly(in, 4);
    return ((bytes[0] & 0xff) << 24)
        | ((bytes[1] & 0xff) << 16)
        | ((bytes[2] & 0xff) << 8)
        | (bytes[3] & 0xff);
  }

  private static byte[] readExactly(InputStream in, int size) throws IOException {
    byte[] out = new byte[size];
    int offset = 0;
    while (offset < size) {
      int read = in.read(out, offset, size - offset);
      if (read < 0) {
        throw new EOFException("unexpected EOF while reading protocol probe reply");
      }
      offset += read;
    }
    return out;
  }

  private static void writeInt32(byte[] out, int offset, int value) {
    out[offset] = (byte) ((value >>> 24) & 0xff);
    out[offset + 1] = (byte) ((value >>> 16) & 0xff);
    out[offset + 2] = (byte) ((value >>> 8) & 0xff);
    out[offset + 3] = (byte) (value & 0xff);
  }

  /** Parsed 32-bit protocol selection returned by Quassel Core. */
  public record ProbeSelection(
      int rawWord, int protocolType, int protocolFeatures, int connectionFeatures) {}
}
