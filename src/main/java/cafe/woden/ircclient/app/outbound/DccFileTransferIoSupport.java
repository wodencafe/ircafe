package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.model.TargetRef;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Shared DCC file-transfer socket I/O and progress reporting support. */
@ApplicationLayer
@RequiredArgsConstructor
final class DccFileTransferIoSupport {

  private static final String DCC_TAG = "(dcc)";

  @NonNull private final UiPort ui;
  @NonNull private final DccCommandSupport dccCommandSupport;
  private final int connectTimeoutMs;
  private final int ioTimeoutMs;
  private final int bufferSize;

  void sendFileToSocket(
      String sid,
      String nick,
      TargetRef pm,
      Socket socket,
      Path source,
      String displayName,
      long size)
      throws IOException {
    long sent = 0L;
    int lastReportedPercent = -1;
    try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(source));
        BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
      byte[] buf = new byte[bufferSize];
      int n;
      while ((n = in.read(buf)) >= 0) {
        out.write(buf, 0, n);
        sent += n;
        int pct = percent(sent, size);
        if (shouldReportProgress(lastReportedPercent, pct)) {
          lastReportedPercent = pct;
          ui.appendStatus(pm, DCC_TAG, "Sending " + displayName + " … " + pct + "%");
          dccCommandSupport.upsertTransfer(
              sid,
              nick,
              DccCommandSupport.transferEntryId(sid, nick, "send-out"),
              "Send file (outgoing)",
              "Transferring",
              displayName + " (" + DccCommandSupport.formatBytes(size) + ")",
              pct,
              DccTransferStore.ActionHint.NONE);
        }
      }
      out.flush();
    }
  }

  void receiveFileFromOffer(
      String sid,
      String nick,
      TargetRef pm,
      PendingSendOffer offer,
      Path destination,
      String localPath)
      throws IOException {
    long expected = offer.size();
    long received = 0L;
    int lastReportedPercent = -1;

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(offer.host(), offer.port()), connectTimeoutMs);
      socket.setSoTimeout(ioTimeoutMs);

      try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
          DataOutputStream ack =
              new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
          BufferedOutputStream fileOut =
              new BufferedOutputStream(
                  Files.newOutputStream(
                      destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
        byte[] buf = new byte[bufferSize];
        while (expected <= 0L || received < expected) {
          int max =
              (expected > 0L) ? (int) Math.min((long) buf.length, expected - received) : buf.length;
          int n = in.read(buf, 0, max);
          if (n < 0) break;
          fileOut.write(buf, 0, n);
          received += n;

          ack.writeInt((int) (received & 0xFFFF_FFFFL));
          ack.flush();

          int pct = percent(received, expected);
          if (shouldReportProgress(lastReportedPercent, pct)) {
            lastReportedPercent = pct;
            ui.appendStatus(pm, DCC_TAG, "Receiving " + offer.fileName() + " … " + pct + "%");
            dccCommandSupport.upsertTransfer(
                sid,
                nick,
                DccCommandSupport.transferEntryId(sid, nick, "send-in"),
                "Receive file (incoming)",
                "Transferring",
                offer.fileName() + " -> " + destination.getFileName(),
                localPath,
                pct,
                DccTransferStore.ActionHint.NONE);
          }
        }
        fileOut.flush();
      }
    }

    if (expected > 0L && received != expected) {
      throw new IOException("Transfer ended early (" + received + " / " + expected + " bytes)");
    }
  }

  private static boolean shouldReportProgress(int previousPercent, int currentPercent) {
    if (currentPercent < 0) return false;
    if (currentPercent == 100 && previousPercent != 100) return true;
    return currentPercent >= previousPercent + 10;
  }

  private static int percent(long value, long total) {
    if (total <= 0L) return -1;
    if (value <= 0L) return 0;
    long p = (value * 100L) / total;
    if (p < 0L) p = 0L;
    if (p > 100L) p = 100L;
    return (int) p;
  }
}
