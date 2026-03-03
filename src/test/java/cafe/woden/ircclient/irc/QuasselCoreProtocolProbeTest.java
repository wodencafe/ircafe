package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class QuasselCoreProtocolProbeTest {

  @Test
  void buildsDefaultProbeRequestWordsInProtocolPreferenceOrder() {
    byte[] actual = QuasselCoreProtocolProbe.buildProbeRequestWords(false, false);

    byte[] expected =
        new byte[] {
          0x42, (byte) 0xb3, 0x3f, 0x00, // magic
          0x00, 0x00, 0x00, 0x02, // datastream
          (byte) 0x80, 0x00, 0x00, 0x01 // legacy + end-of-list
        };
    assertArrayEquals(expected, actual);
  }

  @Test
  void parseSelectionExtractsTypeAndFeatureBitfields() {
    QuasselCoreProtocolProbe.ProbeSelection selection =
        QuasselCoreProtocolProbe.parseSelection(0x030a1202);

    assertEquals(QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, selection.protocolType());
    assertEquals(0x0a12, selection.protocolFeatures());
    assertEquals(0x03, selection.connectionFeatures());
  }

  @Test
  void negotiateWritesProbeAndParsesReply() throws Exception {
    byte[] reply = new byte[] {0x01, 0x00, 0x00, 0x02};
    DuplexTestSocket socket = new DuplexTestSocket(new ByteArrayInputStream(reply));
    QuasselCoreProtocolProbe probe = new QuasselCoreProtocolProbe();

    QuasselCoreProtocolProbe.ProbeSelection selection = probe.negotiate(socket);

    assertEquals(QuasselCoreProtocolProbe.PROTOCOL_DATASTREAM, selection.protocolType());
    assertArrayEquals(
        QuasselCoreProtocolProbe.buildProbeRequestWords(false, false), socket.writtenBytes());
  }

  private static final class DuplexTestSocket extends Socket {
    private final InputStream input;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private DuplexTestSocket(InputStream input) {
      this.input = input;
    }

    @Override
    public InputStream getInputStream() {
      return input;
    }

    @Override
    public OutputStream getOutputStream() {
      return output;
    }

    private byte[] writtenBytes() {
      return output.toByteArray();
    }
  }
}
