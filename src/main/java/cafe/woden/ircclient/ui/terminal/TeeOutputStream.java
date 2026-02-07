package cafe.woden.ircclient.ui.terminal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * OutputStream that writes to a delegate OutputStream and also forwards the bytes
 * (decoded as text) to a consumer.
 */
final class TeeOutputStream extends OutputStream {

  private final OutputStream delegate;
  private final Consumer<String> sink;
  private final Charset charset;

  TeeOutputStream(OutputStream delegate, Consumer<String> sink) {
    this(delegate, sink, Charset.defaultCharset());
  }

  TeeOutputStream(OutputStream delegate, Consumer<String> sink, Charset charset) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.sink = Objects.requireNonNull(sink, "sink");
    this.charset = Objects.requireNonNull(charset, "charset");
  }

  @Override
  public void write(int b) throws IOException {
    delegate.write(b);
    try {
      sink.accept(new String(new byte[] {(byte) b}, charset));
    } catch (Exception ignored) {
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    delegate.write(b, off, len);
    if (len <= 0) return;
    try {
      sink.accept(new String(b, off, len, charset));
    } catch (Exception ignored) {
    }
  }

  @Override
  public void flush() throws IOException {
    delegate.flush();
  }

  @Override
  public void close() throws IOException {
    // Do not close the delegate (it is the original System.out/err).
    flush();
  }
}
