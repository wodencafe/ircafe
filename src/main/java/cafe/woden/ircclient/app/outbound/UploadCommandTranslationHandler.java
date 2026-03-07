package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;

/** Backend translator for semantic /upload command arguments into outbound protocol lines. */
interface UploadCommandTranslationHandler {

  IrcProperties.Server.Backend backend();

  String translateUpload(String target, String msgType, String sourcePath, String displayBody);
}
