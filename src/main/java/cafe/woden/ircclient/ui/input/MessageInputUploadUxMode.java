package cafe.woden.ircclient.ui.input;

import java.io.File;
import java.util.List;
import javax.swing.JComponent;

interface MessageInputUploadUxMode {

  record ActionPresentation(
      boolean attachVisible, String attachTooltip, String attachAccessibleDescription) {}

  interface Context {
    JComponent ownerComponent();

    boolean isInputEditable();

    List<File> normalizeUploadFiles(List<File> files);

    String consumeDraftCaptionForUpload();

    void emitOutboundLine(String line);
  }

  ActionPresentation presentation();

  void runAttachAction(Context context);

  boolean canImportFileDrop(Context context);

  boolean importFileDrop(Context context, List<File> files);
}
