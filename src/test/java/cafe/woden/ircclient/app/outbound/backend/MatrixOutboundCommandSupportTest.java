package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.model.TargetRef;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MatrixOutboundCommandSupportTest {

  private final MatrixOutboundCommandSupport support = new MatrixOutboundCommandSupport();

  @Test
  void normalizeUploadMsgTypeSupportsShortcutsAndCanonicalTokens() {
    assertEquals("m.image", support.normalizeUploadMsgType("image"));
    assertEquals("m.file", support.normalizeUploadMsgType("FILE"));
    assertEquals("m.video", support.normalizeUploadMsgType("m.video"));
    assertEquals("m.audio", support.normalizeUploadMsgType("m.audio"));
  }

  @Test
  void normalizeUploadMsgTypeRejectsUnsupportedTokens() {
    assertEquals("", support.normalizeUploadMsgType(""));
    assertEquals("", support.normalizeUploadMsgType("m.bad"));
    assertEquals("", support.normalizeUploadMsgType("sticker"));
  }

  @Test
  void defaultUploadCaptionFallsBackToFileName() {
    assertEquals("My File.txt", support.defaultUploadCaption("/tmp/My File.txt"));
    assertEquals("photo.png", support.defaultUploadCaption("photo.png"));
    assertEquals("", support.defaultUploadCaption("   "));
  }

  @Test
  void buildUploadPrivmsgEscapesIrcv3TagValues() {
    String line =
        support.buildUploadPrivmsg(
            "!room:example.org", "image", "/tmp/My File;A\\B.png", "matrix upload");

    assertEquals(
        "@+matrix/msgtype=m.image;+matrix/upload_path=/tmp/My\\sFile\\:A\\\\B.png PRIVMSG !room:example.org :matrix upload",
        line);
  }

  @Test
  void buildUploadPrivmsgReturnsBlankWhenRequiredPartsMissing() {
    assertEquals("", support.buildUploadPrivmsg("", "image", "/tmp/photo.png", "caption"));
    assertEquals("", support.buildUploadPrivmsg("!room:example.org", "", "/tmp/photo.png", "x"));
    assertEquals("", support.buildUploadPrivmsg("!room:example.org", "image", "   ", "x"));
  }

  @Test
  void appendUploadHelpAndUsageWriteExpectedUiStatusLines() {
    UiPort ui = Mockito.mock(UiPort.class);
    TargetRef out = new TargetRef("matrix", "!room:example.org");

    support.appendUploadHelp(ui, out);
    support.appendUploadUsage(ui, out);

    Mockito.verify(ui)
        .appendStatus(
            out,
            "(help)",
            "/upload <m.image|m.file|m.video|m.audio> <path> [caption]  (msgtype shortcuts: image|file|video|audio)");
    Mockito.verify(ui).appendStatus(out, "(upload)", "Usage: /upload <msgtype> <path> [caption]");
    Mockito.verify(ui)
        .appendStatus(
            out,
            "(upload)",
            "msgtype: m.image | m.file | m.video | m.audio (shortcuts: image|file|video|audio)");
  }
}
