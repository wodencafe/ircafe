package cafe.woden.ircclient.app.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned contract for transcript history rendering operations. */
@ApplicationLayer
public interface ChatTranscriptHistoryPort {

  void beginHistoryInsertBatch(TargetRef target);

  int loadOlderInsertOffset(TargetRef target);

  int insertChatFromHistoryAt(
      TargetRef target, int insertAt, String from, String text, boolean outgoingLocalEcho, long at);

  int insertActionFromHistoryAt(
      TargetRef target,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long at);

  int insertNoticeFromHistoryAt(TargetRef target, int insertAt, String from, String text, long at);

  void appendChatFromHistory(
      TargetRef target, String from, String text, boolean outgoingLocalEcho, long at);

  void appendActionFromHistory(
      TargetRef target, String from, String action, boolean outgoingLocalEcho, long at);

  void appendNoticeFromHistory(TargetRef target, String from, String text, long at);

  void endHistoryInsertBatch(TargetRef target);
}
