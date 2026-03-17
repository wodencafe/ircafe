package cafe.woden.ircclient.state.api;

import org.jmolecules.architecture.layered.ApplicationLayer;

/** Public state-module contract for server-negotiated RPL_ISUPPORT token and mode semantics. */
@ApplicationLayer
public interface ServerIsupportStatePort {

  void applyIsupportToken(String serverId, String tokenName, String tokenValue);

  ModeVocabulary vocabularyForServer(String serverId);

  void clearServer(String serverId);
}
