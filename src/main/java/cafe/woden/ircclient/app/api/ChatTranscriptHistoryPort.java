package cafe.woden.ircclient.app.api;

import java.util.Map;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** App-owned contract for transcript history rendering operations. */
@ApplicationLayer
public interface ChatTranscriptHistoryPort {

  void beginHistoryInsertBatch(TargetRef target);

  int loadOlderInsertOffset(TargetRef target);

  int insertChatFromHistoryAt(
      TargetRef target, int insertAt, String from, String text, boolean outgoingLocalEcho, long at);

  default int insertChatFromHistoryAt(
      TargetRef target,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long at,
      String messageId,
      Map<String, String> ircv3Tags) {
    return insertChatFromHistoryAt(target, insertAt, from, text, outgoingLocalEcho, at);
  }

  int insertActionFromHistoryAt(
      TargetRef target,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long at);

  default int insertActionFromHistoryAt(
      TargetRef target,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long at,
      String messageId,
      Map<String, String> ircv3Tags) {
    return insertActionFromHistoryAt(target, insertAt, from, action, outgoingLocalEcho, at);
  }

  int insertNoticeFromHistoryAt(TargetRef target, int insertAt, String from, String text, long at);

  default int insertNoticeFromHistoryAt(
      TargetRef target,
      int insertAt,
      String from,
      String text,
      long at,
      String messageId,
      Map<String, String> ircv3Tags) {
    return insertNoticeFromHistoryAt(target, insertAt, from, text, at);
  }

  void appendChatFromHistory(
      TargetRef target, String from, String text, boolean outgoingLocalEcho, long at);

  default void appendChatFromHistory(
      TargetRef target,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long at,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendChatFromHistory(target, from, text, outgoingLocalEcho, at);
  }

  void appendActionFromHistory(
      TargetRef target, String from, String action, boolean outgoingLocalEcho, long at);

  default void appendActionFromHistory(
      TargetRef target,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long at,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendActionFromHistory(target, from, action, outgoingLocalEcho, at);
  }

  void appendNoticeFromHistory(TargetRef target, String from, String text, long at);

  default void appendNoticeFromHistory(
      TargetRef target,
      String from,
      String text,
      long at,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendNoticeFromHistory(target, from, text, at);
  }

  void endHistoryInsertBatch(TargetRef target);
}
