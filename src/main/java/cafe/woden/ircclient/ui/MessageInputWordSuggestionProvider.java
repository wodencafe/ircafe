package cafe.woden.ircclient.ui;

import java.util.List;

/** Supplies word suggestions for the message input completion popup. */
interface MessageInputWordSuggestionProvider {

  /**
   * Suggest replacement/completion candidates for the provided token.
   *
   * @param token current token under the caret
   * @param maxSuggestions maximum number of suggestions to return
   */
  List<String> suggestWords(String token, int maxSuggestions);
}
