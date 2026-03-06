package cafe.woden.ircclient.ui.input;

import java.io.File;
import java.util.List;

final class IrcMessageInputUploadUxMode implements MessageInputUploadUxMode {
  private static final ActionPresentation PRESENTATION =
      new ActionPresentation(
          false,
          "File upload is available on Matrix-backed servers",
          "File upload is available on Matrix-backed servers");

  @Override
  public ActionPresentation presentation() {
    return PRESENTATION;
  }

  @Override
  public void runAttachAction(Context context) {}

  @Override
  public boolean canImportFileDrop(Context context) {
    return false;
  }

  @Override
  public boolean importFileDrop(Context context, List<File> files) {
    return false;
  }
}
